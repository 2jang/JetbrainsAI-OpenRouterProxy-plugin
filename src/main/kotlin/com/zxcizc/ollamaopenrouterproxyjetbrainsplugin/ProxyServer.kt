package com.zxcizc.ollamaopenrouterproxyjetbrainsplugin

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.intellij.openapi.application.ApplicationManager
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

// --- Data classes (기존과 동일) ---
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
data class OpenRouterKeyResponse(val data: OpenRouterKeyData)
data class OpenRouterKeyData(
    val label: String?,
    val usage: Double?,
    val is_free_tier: Boolean?,
    val is_provisioning_key: Boolean?,
    val limit: Double?,
    val limit_remaining: Double?
)

class ProxyServer {
    private var server: HttpServer? = null
    private val log: Logger = Logger.getInstance(ProxyServer::class.java)

    companion object {
        const val OLLAMA_PORT = 11434
        val OPENROUTER_CHAT_URL: URL = URI("https://openrouter.ai/api/v1/chat/completions").toURL()
        val OPENROUTER_MODELS_URL: URL = URI("https://openrouter.ai/api/v1/models").toURL()
        val OPENROUTER_KEY_URL: URL = URI("https://openrouter.ai/api/v1/key").toURL()
        const val OLLAMA_IS_RUNNING = "Ollama is running"
        val modelCache = ConcurrentHashMap<String, Any>()
        const val CACHE_KEY = "models"
        const val CACHE_DURATION_MS = 30 * 1000
        private val gson = Gson()

        fun invalidateModelsCache() {
            modelCache.remove(CACHE_KEY)
        }

        fun validateApiKey(apiKey: String): OpenRouterKeyData? {
            if (apiKey.isBlank()) return null
            return try {
                val connection = OPENROUTER_KEY_URL.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Authorization", "Bearer $apiKey")
                connection.connectTimeout = 10000
                connection.readTimeout = 15000
                if (connection.responseCode == 200) {
                    val jsonResponse = connection.inputStream.bufferedReader().use { it.readText() }
                    gson.fromJson(jsonResponse, OpenRouterKeyResponse::class.java).data
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    fun isRunning(): Boolean = server?.address != null

    fun getStatus(): String {
        val settings = PluginSettingsState.getInstance()
        return when {
            settings.isProxyEnabled -> "Running (Proxy Mode)"
            else -> "Running (Bypass Mode)"
        }
    }

    fun getDetailedStatus(): String {
        val settings = PluginSettingsState.getInstance()
        return when {
            !settings.isProxyEnabled -> "Mode: Bypass (Direct to Ollama at localhost:11434)"
            settings.openRouterApiKey.isBlank() -> "Mode: Proxy (OpenRouter API Key Required)"
            else -> "Mode: Proxy (OpenRouter + Ollama)"
        }
    }

    fun start() {
        if (server != null) {
            log.warn("Proxy server might be already running. Stopping it first to release the port.")
            stop()
        }
        val port = PluginSettingsState.getInstance().proxyPort // <<< 설정에서 포트 가져오기
        try {
            server = HttpServer.create(InetSocketAddress("localhost", port), 0).apply {
                createContext("/", UniversalHandler())
                executor = Executors.newCachedThreadPool()
                start()
            }
            log.info("Ollama-OpenRouter Proxy Server started on http://localhost:$port")
        } catch (e: IOException) {
            log.error("Failed to start proxy server on port $port. It might be in use by another application.", e)
        }
    }

    fun stop() {
        server?.stop(0)
        log.info("Proxy Server stopped.")
        server = null
    }

    fun restart() {
        ApplicationManager.getApplication().executeOnPooledThread {
            stop()
            try { Thread.sleep(500) } catch (e: InterruptedException) { Thread.currentThread().interrupt() }
            start()
        }
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
            if (PluginSettingsState.getInstance().isProxyEnabled) {
                handleOpenRouterProxy(exchange)
            } else {
                bypassToOllama(exchange)
            }
        }

        private fun handleOpenRouterProxy(exchange: HttpExchange) {
            val path = exchange.requestURI.path
            val method = exchange.requestMethod

            when {
                (method == "POST" && (path == "/api/chat" || path == "/v1/chat/completions")) ->
                    chatProxyHandler.handle(exchange)
                (method == "GET" && path == "/api/tags") ->
                    handleModelsList(exchange)
                (method == "GET" && path == "/") ->
                    sendPlainTextResponse(exchange, 200, OLLAMA_IS_RUNNING)
                else ->
                    sendJsonResponse(exchange, 404, "{\"error\": \"Not Found\"}")
            }
        }

        private fun bypassToOllama(exchange: HttpExchange) {
            val path = exchange.requestURI.path
            val method = exchange.requestMethod

            when {
                (method == "GET" && path == "/api/tags") ->
                    handleModelsList(exchange)
                (method == "POST" && (path == "/api/chat" || path == "/v1/chat/completions")) ->
                    chatProxyHandler.handle(exchange)
                (method == "GET" && path == "/") ->
                    sendPlainTextResponse(exchange, 200, OLLAMA_IS_RUNNING)
                else ->
                    forwardToLocalOllama(exchange, path, method)
            }
        }

        private fun forwardToLocalOllama(exchange: HttpExchange, path: String, method: String) {
            // ... (기존과 동일)
        }

        @Suppress("UNCHECKED_CAST")
        private fun handleModelsList(exchange: HttpExchange) {
            // ... (기존과 동일)
        }

        // ... (handleModelsList의 헬퍼 함수들은 기존과 동일)
    }

    private inner class ChatProxyHandler {
        private val log: Logger = Logger.getInstance(ChatProxyHandler::class.java)
        private val gson = Gson()

        fun handle(exchange: HttpExchange) {
            var headersSent = false
            try {
                val requestBody = readRequestBody(exchange.requestURI.path, exchange.requestBody)

                // 1. 요청 본문을 유연한 Map으로 파싱
                val requestType = object : TypeToken<MutableMap<String, Any>>() {}.type
                val requestMap: MutableMap<String, Any> = gson.fromJson(requestBody, requestType)

                val modelName = requestMap["model"] as? String ?: ""

                if (modelName.startsWith("ℹ️") || modelName.startsWith("═══")) {
                    val errorResponse = "{\"error\": {\"message\": \"This is an informational entry, not a usable model. Please select an actual model from the list.\", \"type\": \"invalid_model\", \"code\": \"dummy_model_request\"}}"
                    sendJsonResponse(exchange, 400, errorResponse)
                    return
                }

                if (modelName.startsWith("(local)")) {
                    val localModelName = modelName.removePrefix("(local) ")
                    requestMap["model"] = localModelName
                    // 로컬 요청에도 파라미터 오버라이드 적용
                    val settings = PluginSettingsState.getInstance()
                    if (settings.overrideParameters) {
                        applyParameterOverrides(requestMap, settings.activeParameters)
                    }
                    handleLocalModelRequest(exchange, requestMap)
                    return
                }

                // 2. 파라미터 오버라이드 로직
                val settings = PluginSettingsState.getInstance()
                if (settings.overrideParameters) {
                    applyParameterOverrides(requestMap, settings.activeParameters)
                    log.info("Overriding parameters for model '$modelName'")
                }

                val apiKey = settings.openRouterApiKey
                if (apiKey.isBlank()) {
                    val errorResponse = "{\"error\": {\"message\": \"OpenRouter API Key is not configured in IDE Settings.\", \"type\": \"authentication_error\", \"code\": \"api_key_required\"}}"
                    sendJsonResponse(exchange, 401, errorResponse)
                    return
                }

                if (settings.selectedModels.isNotEmpty() && !settings.selectedModels.contains(modelName)) {
                    val errorResponse = "{\"error\": {\"message\": \"Model '$modelName' is not available. Please check your model whitelist.\", \"type\": \"model_not_found\", \"code\": \"model_not_whitelisted\"}}"
                    sendJsonResponse(exchange, 404, errorResponse)
                    return
                }

                if (modelName.endsWith(":latest")) {
                    val newModel = modelName.removeSuffix(":latest")
                    requestMap["model"] = newModel
                    log.info("Model tag removed: '$modelName' -> '$newModel'")
                }

                val modifiedBody = gson.toJson(requestMap)

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

                connection.headerFields.forEach { (key, values) ->
                    if (key != null) exchange.responseHeaders.put(key, values)
                }
                exchange.sendResponseHeaders(responseCode, 0)
                headersSent = true

                exchange.responseBody.use { clientResponseStream ->
                    responseStream?.use { serverResponseStream ->
                        val reader = BufferedReader(InputStreamReader(serverResponseStream, StandardCharsets.UTF_8))
                        reader.forEachLine { line ->
                            if (line.startsWith("data:")) {
                                val jsonData = line.substring(5).trim()
                                if (jsonData == "[DONE]") {
                                    val finalChunk = createFinalOllamaChunk(modelName)
                                    clientResponseStream.write((finalChunk + "\n").toByteArray(StandardCharsets.UTF_8))
                                    clientResponseStream.flush()
                                } else {
                                    val ollamaChunk = convertOpenAiToOllamaChunk(jsonData, modelName)
                                    if (ollamaChunk != null) {
                                        clientResponseStream.write((ollamaChunk + "\n").toByteArray(StandardCharsets.UTF_8))
                                        clientResponseStream.flush()
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                log.error("Error during proxying request", e)
                if (!headersSent) {
                    val errorResponse = "{\"error\": {\"message\": \"Internal proxy error: ${e.message}\", \"type\": \"proxy_error\", \"code\": \"internal_error\"}}"
                    sendJsonResponse(exchange, 500, errorResponse)
                }
            } finally {
                if (!headersSent) {
                    exchange.close()
                }
            }
        }

        private fun applyParameterOverrides(requestMap: MutableMap<String, Any>, params: ParameterPreset) {
            // Simple Types - Add only if the value is not null AND not the API default
            params.temperature?.let { if (it != 1.0) requestMap["temperature"] = it }
            params.topP?.let { if (it != 1.0) requestMap["top_p"] = it }
            params.topK?.let { if (it != 0) requestMap["top_k"] = it }
            params.minP?.let { if (it != 0.0) requestMap["min_p"] = it }
            params.topA?.let { if (it != 0.0) requestMap["top_a"] = it }
            params.seed?.let { requestMap["seed"] = it } // Seed has no default, add if not null
            params.frequencyPenalty?.let { if (it != 0.0) requestMap["frequency_penalty"] = it }
            params.presencePenalty?.let { if (it != 0.0) requestMap["presence_penalty"] = it }
            params.repetitionPenalty?.let { if (it != 1.0) requestMap["repetition_penalty"] = it }
            params.maxTokens?.let { requestMap["max_tokens"] = it } // No default, add if not null
            params.logprobs?.let { requestMap["logprobs"] = it } // No default, add if not null
            params.topLogprobs?.let { if (it != 0) requestMap["top_logprobs"] = it }

            // List Type - Add only if not null and not empty
            params.stop?.let { if (it.isNotEmpty()) requestMap["stop"] = it }

            // Map Type from String
            params.responseFormatType?.let {
                if (it.isNotBlank()) {
                    requestMap["response_format"] = mapOf("type" to it)
                }
            }

            // Complex JSON String Types
            fun addJsonParameter(key: String, jsonString: String?) {
                jsonString?.let {
                    if (it.isNotBlank()) {
                        try {
                            val paramObject = gson.fromJson<Any>(it, Any::class.java)
                            requestMap[key] = paramObject
                        } catch (e: Exception) {
                            log.error("Failed to parse JSON for parameter '$key': $it", e)
                        }
                    }
                }
            }

            addJsonParameter("tools", params.toolsJson)
            addJsonParameter("tool_choice", params.toolChoiceJson)
            addJsonParameter("logit_bias", params.logitBiasJson)
        }

        private fun handleLocalModelRequest(exchange: HttpExchange, requestMap: Map<String, Any>) {
            val modifiedBody = gson.toJson(requestMap)
            var ollamaConnection: HttpURLConnection? = null
            try {
                val ollamaUrl = URI("http://localhost:$OLLAMA_PORT/api/chat").toURL()
                ollamaConnection = ollamaUrl.openConnection() as HttpURLConnection
                ollamaConnection.requestMethod = "POST"
                ollamaConnection.doOutput = true
                ollamaConnection.setRequestProperty("Content-Type", "application/json")

                ollamaConnection.outputStream.use {
                    it.write(modifiedBody.toByteArray(StandardCharsets.UTF_8))
                }

                val responseCode = ollamaConnection.responseCode
                exchange.responseHeaders.clear()
                ollamaConnection.headerFields.forEach { key, values ->
                    if (key != null) {
                        exchange.responseHeaders.put(key, values)
                    }
                }
                exchange.sendResponseHeaders(responseCode, 0)

                val inputStream = if (responseCode in 200..299) ollamaConnection.inputStream else ollamaConnection.errorStream
                inputStream?.use { input ->
                    exchange.responseBody.use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (e: IOException) {
                log.error("Failed to forward local model request to Ollama", e)
                val errorResponse = "{\"error\": {\"message\": \"Failed to connect to local Ollama at localhost:$OLLAMA_PORT. Is it running?\", \"type\": \"connection_error\", \"code\": \"local_ollama_unavailable\"}}"
                sendJsonResponse(exchange, 502, errorResponse)
            } finally {
                ollamaConnection?.disconnect()
                exchange.close()
            }
        }

        private fun convertOpenAiToOllamaChunk(openAiJson: String, modelName: String): String? {
            return try {
                val openAiChunk = gson.fromJson(openAiJson, OpenAIChatChunk::class.java)
                val delta = openAiChunk.choices.firstOrNull()?.delta ?: return null

                if (delta.content == null && delta.role != null) return null

                val ollamaMessage = OllamaMessage(role = delta.role ?: "assistant", content = delta.content ?: "")
                val ollamaChunk = OllamaChatChunk(
                    model = modelName,
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

        private fun createFinalOllamaChunk(modelName: String): String {
            val finalChunk = OllamaFinalChunk(
                model = modelName,
                created_at = Instant.now().toString(),
                message = OllamaMessage(role = "assistant", content = ""),
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
            return inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        }
    }
}