package com.zxcizc.ollamaopenrouterproxyjetbrainsplugin

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import com.intellij.openapi.diagnostic.Logger
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.Serializable
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.URI
import java.net.URL
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

class ProxyServer {
    private var server: HttpServer? = null
    private val log: Logger = Logger.getInstance(ProxyServer::class.java)

    private companion object {
        const val PROXY_PORT = 11444
        val OPENROUTER_CHAT_URL: URL = URI("https://openrouter.ai/api/v1/chat/completions").toURL()
        val OPENROUTER_MODELS_URL: URL = URI("https://openrouter.ai/api/v1/models").toURL()
        const val OLLAMA_IS_RUNNING = "Ollama is running"
        // 모델 목록 캐시 및 유효 시간(5분)
        val modelCache = ConcurrentHashMap<String, Any>()
        const val CACHE_KEY = "models"
        const val CACHE_DURATION_MS = 5 * 60 * 1000
    }

    fun start() {
        try {
            if (server?.address != null) {
                log.info("Proxy server is already running on port $PROXY_PORT")
                return
            }
            server = HttpServer.create(InetSocketAddress("localhost", PROXY_PORT), 0).apply {
                createContext("/", UniversalHandler())
                executor = null
                start()
            }
            log.info("Ollama-OpenRouter Proxy Server started on http://localhost:$PROXY_PORT")
        } catch (e: IOException) {
            log.error("Failed to start proxy server on port $PROXY_PORT", e)
        }
    }

    fun stop() {
        server?.stop(0)
        log.info("Proxy Server stopped.")
        server = null
    }

    private fun sendJsonResponse(exchange: HttpExchange, statusCode: Int, jsonResponse: String) {
        try {
            exchange.responseHeaders.set("Content-Type", "application/json; charset=utf-8")
            val responseBytes = jsonResponse.toByteArray(StandardCharsets.UTF_8)
            exchange.sendResponseHeaders(statusCode, responseBytes.size.toLong())
            exchange.responseBody.use { it.write(responseBytes) }
        } catch (e: IOException) {
            log.error("Failed to send JSON response", e)
        } finally {
            exchange.close()
        }
    }

    private fun sendPlainTextResponse(exchange: HttpExchange, statusCode: Int, textResponse: String) {
        try {
            exchange.responseHeaders.set("Content-Type", "text/plain; charset=utf-8")
            val responseBytes = textResponse.toByteArray(StandardCharsets.UTF_8)
            exchange.sendResponseHeaders(statusCode, responseBytes.size.toLong())
            exchange.responseBody.use { it.write(responseBytes) }
        } catch (e: IOException) {
            log.error("Failed to send plain text response", e)
        } finally {
            exchange.close()
        }
    }

    private inner class UniversalHandler : HttpHandler {
        private val log: Logger = Logger.getInstance(UniversalHandler::class.java)
        private val chatProxyHandler = ChatProxyHandler()

        override fun handle(exchange: HttpExchange) {
            val path = exchange.requestURI.path
            val method = exchange.requestMethod
            if (PluginSettingsState.getInstance().enableDebugLogging) {
                log.info(">>>> [DEBUG] Received request: $method $path")
                }

            when {
                (method == "POST" && (path == "/api/chat" || path == "/v1/chat/completions")) -> chatProxyHandler.handle(exchange)
                (method == "GET" && path == "/api/tags") -> handleModelsList(exchange)
                (method == "GET" && path == "/") -> handleRootCheck(exchange)
                else -> {
                    log.warn("Unhandled path: $method $path. Returning 404.")
                    sendJsonResponse(exchange, 404, "{\"error\": \"Not Found\"}")
                }
            }
        }

        private fun handleRootCheck(exchange: HttpExchange) {
            sendPlainTextResponse(exchange, 200, OLLAMA_IS_RUNNING)
        }

        @Suppress("UNCHECKED_CAST")
        private fun handleModelsList(exchange: HttpExchange) {
            val cached = modelCache[CACHE_KEY] as? Pair<Long, String>
            if (cached != null && (System.currentTimeMillis() - cached.first) < CACHE_DURATION_MS) {
                log.info("Returning cached model list.")
                sendJsonResponse(exchange, 200, cached.second)
                return
            }

            log.info("Fetching model list from OpenRouter...")
            try {
                val connection = OPENROUTER_MODELS_URL.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"

                if (connection.responseCode == 200) {
                    val jsonResponse = connection.inputStream.bufferedReader().use { it.readText() }
                    val ollamaFormattedJson = convertOpenRouterToOllamaFormat(jsonResponse)
                    log.info("Successfully fetched and converted model list. Caching result.")
                    modelCache[CACHE_KEY] = Pair(System.currentTimeMillis(), ollamaFormattedJson)
                    sendJsonResponse(exchange, 200, ollamaFormattedJson)
                } else {
                    log.error("Failed to fetch models from OpenRouter. Status: ${connection.responseCode}")
                    sendJsonResponse(exchange, 502, "{\"error\": \"Failed to fetch models from OpenRouter\"}")
                }
            } catch (e: Exception) {
                log.error("Exception while fetching model list", e)
                sendJsonResponse(exchange, 500, "{\"error\": \"Internal Proxy Error\"}")
            }
        }

        private fun convertOpenRouterToOllamaFormat(openRouterJson: String): String {
            val gson = Gson()
            val type = object : TypeToken<Map<String, List<Map<String, Any>>>>() {}.type
            val openRouterResponse: Map<String, List<Map<String, Any>>> = gson.fromJson(openRouterJson, type)

            val ollamaModels = (openRouterResponse["data"] ?: emptyList()).map { openRouterModel ->
                val modelId = openRouterModel["id"] as? String ?: "unknown"
                val detailsMap = mapOf(
                    "format" to "gguf",
                    "family" to (modelId.split("/").firstOrNull() ?: "unknown"),
                    "parameter_size" to "N/A",
                    "quantization_level" to "N/A"
                )
                mapOf<String, Serializable>(
                    "name" to modelId, // ':latest' 제거
                    "model" to modelId, // ':latest' 제거
                    "modified_at" to Instant.now().toString(),
                    "size" to 0L,
                    "digest" to modelId,
                    "details" to (detailsMap as Serializable)
                )
            }
            return gson.toJson(mapOf("models" to ollamaModels))
        }
    }


    private inner class ChatProxyHandler {
        private val log: Logger = Logger.getInstance(ChatProxyHandler::class.java)
        private val gson = Gson()

        fun handle(exchange: HttpExchange) {
            var headersSent = false
            try {
                val requestBody = readRequestBody(exchange.requestURI.path, exchange.requestBody)
                val apiKey = PluginSettingsState.getInstance().openRouterApiKey
                if (apiKey.isBlank()) {
                    log.warn("OpenRouter API Key is not set.")
                    sendJsonResponse(exchange, 401, "{\"error\": \"OpenRouter API Key is not set in IDE Settings.\"}")
                    return
                }

                val modifiedBody = removeLatestTagFromModel(requestBody)

                val connection = OPENROUTER_CHAT_URL.openConnection() as HttpURLConnection
                connection.apply {
                    requestMethod = "POST"
                    doOutput = true
                    readTimeout = 60 * 1000
                    connectTimeout = 15 * 1000
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Authorization", "Bearer $apiKey")
                    setRequestProperty("HTTP-Referer", "https://github.com/zxcizc/OllamaOpenRouterProxy-jetbrains-plugin")
                    setRequestProperty("X-Title", "Ollama OpenRouter Proxy")
                }
                connection.outputStream.use { it.write(modifiedBody.toByteArray(StandardCharsets.UTF_8)) }

                val responseCode = connection.responseCode
                val responseStream = if (responseCode in 200..299) connection.inputStream else connection.errorStream

                connection.headerFields.forEach { (key, values) -> if (key != null) exchange.responseHeaders.put(key, values) }
                exchange.sendResponseHeaders(responseCode, 0)
                headersSent = true

                exchange.responseBody.use { clientResponseStream ->
                    responseStream?.use { serverResponseStream ->
                        val reader = BufferedReader(InputStreamReader(serverResponseStream, StandardCharsets.UTF_8))
                        reader.forEachLine { line ->
                            if (PluginSettingsState.getInstance().enableDebugLogging) {
                                log.info("<<<< [DEBUG] Received from OpenRouter: $line")
                            }

                            if (line.startsWith("data:")) {
                                val jsonData = line.substring(5).trim()
                                if (jsonData == "[DONE]") {
                                    val finalOllamaChunk = createFinalOllamaChunk(modifiedBody)
                                    if (PluginSettingsState.getInstance().enableDebugLogging) {
                                        log.info(">>>> [DEBUG] Forwarding final chunk: $finalOllamaChunk")
                                    }
                                    clientResponseStream.write((finalOllamaChunk + "\n").toByteArray(StandardCharsets.UTF_8))
                                    clientResponseStream.flush()
                                    log.info("Stream finished, sent final Ollama chunk.")
                                } else {
                                    val ollamaChunk = convertOpenAiToOllamaChunk(jsonData, modifiedBody)
                                    if (ollamaChunk != null) {
                                        if (PluginSettingsState.getInstance().enableDebugLogging) {
                                            log.info(">>>> [DEBUG] Forwarding to client: $ollamaChunk")
                                        }
                                        clientResponseStream.write((ollamaChunk + "\n").toByteArray(StandardCharsets.UTF_8))
                                        clientResponseStream.flush()
                                    }
                                }
                            } else if (line.trim().startsWith(":")) {
                                log.info("Filtered out comment line: $line")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                log.error("Error during proxying request", e)
                if (!headersSent) {
                    sendJsonResponse(exchange, 500, "{\"error\": \"Internal Proxy Error: ${e.message}\"}")
                }
            } finally {
                if (!headersSent) {
                    exchange.close()
                }
            }
        }

        @Suppress("UNCHECKED_CAST")
        private fun convertOpenAiToOllamaChunk(openAiJson: String, originalRequestBody: String): String? {
            try {
                val type = object : TypeToken<Map<String, Any>>() {}.type
                val openAiMap: Map<String, Any> = gson.fromJson(openAiJson, type)

                val choices = openAiMap["choices"] as? List<Map<String, Any>>
                val delta = choices?.firstOrNull()?.get("delta") as? Map<String, Any>
                val role = delta?.get("role") as? String ?: "assistant"
                val content = delta?.get("content")

                // content가 null이고 다른 유의미한 정보(예: tool_calls)도 없으면 청크를 보내지 않을 수 있음.
                // 하지만 안정성을 위해 빈 content라도 message 객체를 만들어 보내는 것이 좋음.
                if (content == null && delta?.containsKey("tool_calls") != true) {
                    // role 정보만 있는 첫 청크 등은 보낼 필요가 없음
                    if(delta?.size == 1 && delta.containsKey("role")) return null
                }

                val ollamaMessage = mapOf("role" to role, "content" to (content ?: ""))

                val originalRequestMap: Map<String, Any> = gson.fromJson(originalRequestBody, type)

                val ollamaChunk = mapOf(
                    "model" to originalRequestMap["model"],
                    "created_at" to Instant.now().toString(),
                    "message" to ollamaMessage,
                    "done" to false
                )
                return gson.toJson(ollamaChunk)
            } catch (e: Exception) {
                log.error("Failed to convert OpenAI chunk to Ollama format: $openAiJson", e)
                return null
            }
        }

        // --- 이 메서드를 최종 수정합니다 ---
        private fun createFinalOllamaChunk(originalRequestBody: String): String {
            val type = object : TypeToken<Map<String, Any>>() {}.type
            val originalRequestMap: Map<String, Any> = gson.fromJson(originalRequestBody, type)

            // Ollama API 문서의 Final Response 형식을 정확히 모방합니다.
            val finalChunk = mapOf(
                "model" to originalRequestMap["model"],
                "created_at" to Instant.now().toString(),
                "message" to mapOf("role" to "assistant", "content" to ""), // 비어있는 message 객체 포함
                "done" to true,
                // 통계 정보는 OpenRouter 응답에서 얻을 수 없으므로 더미 값으로 채웁니다.
                "total_duration" to 0,
                "load_duration" to 0,
                "prompt_eval_count" to 0,
                "prompt_eval_duration" to 0,
                "eval_count" to 0,
                "eval_duration" to 0
            )
            return gson.toJson(finalChunk)
        }

        private fun readRequestBody(path: String, inputStream: InputStream): String {
            return inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }.also { body ->
                if (PluginSettingsState.getInstance().enableDebugLogging) {
                    log.info(">>>> [DEBUG] Request Body for $path: $body")
                }
            }
        }

        private fun removeLatestTagFromModel(body: String): String {
            val gson = Gson()
            try {
                val type = object : TypeToken<MutableMap<String, Any>>() {}.type
                val requestMap: MutableMap<String, Any> = gson.fromJson(body, type)
                val originalModel = requestMap["model"] as? String
                if (originalModel != null && originalModel.endsWith(":latest")) {
                    val newModel = originalModel.removeSuffix(":latest")
                    requestMap["model"] = newModel
                    log.info("Model tag removed: '$originalModel' -> '$newModel'")
                    return gson.toJson(requestMap)
                }
            } catch (e: Exception) {
                log.error("Failed to parse or modify request body for tag removal.", e)
            }
            return body
        }
    }
}