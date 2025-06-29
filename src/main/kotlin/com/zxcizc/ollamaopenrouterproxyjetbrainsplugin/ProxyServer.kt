package com.zxcizc.ollamaopenrouterproxyjetbrainsplugin

import com.google.gson.Gson
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import com.intellij.openapi.diagnostic.Logger
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.URI
import java.net.URL
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

// --- 개선: 타입 안정성을 위한 데이터 클래스 정의 ---
data class OllamaChatRequest(var model: String, val messages: List<OllamaMessage>, val stream: Boolean?)
data class OllamaMessage(val role: String, val content: String)
data class OllamaChatChunk(val model: String, val created_at: String, val message: OllamaMessage, val done: Boolean)
data class OllamaFinalChunk(
    val model: String, val created_at: String, val message: OllamaMessage, val done: Boolean,
    val total_duration: Long, val load_duration: Long, val prompt_eval_count: Int,
    val prompt_eval_duration: Long, val eval_count: Int, val eval_duration: Long
)
data class OpenAIChatChunk(val choices: List<Choice>) {
    data class Choice(val delta: Delta)
    data class Delta(val role: String?, val content: String?)
}
data class OllamaTagResponse(val models: List<OllamaModel>)
data class OllamaModel(
    val name: String, val model: String, val modified_at: String, val size: Long,
    val digest: String, val details: ModelDetails
)
data class ModelDetails(
    val format: String, val family: String, val parameter_size: String, val quantization_level: String
)
data class OpenRouterModelsResponse(val data: List<OpenRouterModel>)
data class OpenRouterModel(val id: String)


class ProxyServer {
    private var server: HttpServer? = null
    private val log: Logger = Logger.getInstance(ProxyServer::class.java)

    private companion object {
        const val PROXY_PORT = 11444
        val OPENROUTER_CHAT_URL: URL = URI("https://openrouter.ai/api/v1/chat/completions").toURL()
        val OPENROUTER_MODELS_URL: URL = URI("https://openrouter.ai/api/v1/models").toURL()
        const val OLLAMA_IS_RUNNING = "Ollama is running"
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
                // --- 개선: 여러 요청을 동시에 처리하기 위한 스레드 풀 설정 ---
                executor = Executors.newCachedThreadPool()
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
        private val gson = Gson()

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
            val openRouterResponse = gson.fromJson(openRouterJson, OpenRouterModelsResponse::class.java)
            val ollamaModels = openRouterResponse.data.map { openRouterModel ->
                val modelId = openRouterModel.id
                OllamaModel(
                    name = modelId,
                    model = modelId,
                    modified_at = Instant.now().toString(),
                    size = 0L,
                    digest = modelId,
                    details = ModelDetails(
                        format = "gguf",
                        family = modelId.split("/").firstOrNull() ?: "unknown",
                        parameter_size = "N/A",
                        quantization_level = "N/A"
                    )
                )
            }
            return gson.toJson(OllamaTagResponse(models = ollamaModels))
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

                val originalRequest = gson.fromJson(requestBody, OllamaChatRequest::class.java)
                removeLatestTagFromModel(originalRequest)
                val modifiedBody = gson.toJson(originalRequest)

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
                                    val finalOllamaChunk = createFinalOllamaChunk(originalRequest)
                                    if (PluginSettingsState.getInstance().enableDebugLogging) {
                                        log.info(">>>> [DEBUG] Forwarding final chunk: $finalOllamaChunk")
                                    }
                                    clientResponseStream.write((finalOllamaChunk + "\n").toByteArray(StandardCharsets.UTF_8))
                                    clientResponseStream.flush()
                                    log.info("Stream finished, sent final Ollama chunk.")
                                } else {
                                    val ollamaChunk = convertOpenAiToOllamaChunk(jsonData, originalRequest)
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

        private fun convertOpenAiToOllamaChunk(openAiJson: String, originalRequest: OllamaChatRequest): String? {
            return try {
                val openAiChunk = gson.fromJson(openAiJson, OpenAIChatChunk::class.java)
                val delta = openAiChunk.choices.firstOrNull()?.delta ?: return null

                // content가 null이고 role 정보만 있는 첫 청크(메타데이터)는 무시
                if (delta.content == null && delta.role != null) return null

                val ollamaMessage = OllamaMessage(role = delta.role ?: "assistant", content = delta.content ?: "")
                val ollamaChunk = OllamaChatChunk(
                    model = originalRequest.model,
                    created_at = Instant.now().toString(),
                    message = ollamaMessage,
                    done = false
                )
                gson.toJson(ollamaChunk)
            } catch (e: Exception) {
                log.error("Failed to convert OpenAI chunk to Ollama format: $openAiJson", e)
                null
            }
        }

        private fun createFinalOllamaChunk(originalRequest: OllamaChatRequest): String {
            val finalChunk = OllamaFinalChunk(
                model = originalRequest.model,
                created_at = Instant.now().toString(),
                message = OllamaMessage(role = "assistant", content = ""), // 비어있는 message 객체 포함
                done = true,
                total_duration = 0,
                load_duration = 0,
                prompt_eval_count = 0,
                prompt_eval_duration = 0,
                eval_count = 0,
                eval_duration = 0
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

        private fun removeLatestTagFromModel(request: OllamaChatRequest) {
            if (request.model.endsWith(":latest")) {
                val newModel = request.model.removeSuffix(":latest")
                log.info("Model tag removed: '${request.model}' -> '$newModel'")
                request.model = newModel
            }
        }
    }
}