package com.zxcizc.ollamaopenrouterproxyjetbrainsplugin

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.intellij.openapi.application.ApplicationManager
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import com.intellij.openapi.diagnostic.Logger
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
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

// --- Data classes (No changes) ---
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
        val OPENROUTER_CHAT_URL: URL = URI("https://openrouter.ai/api/v1/chat/completions").toURL()
        val OPENROUTER_MODELS_URL: URL = URI("https://openrouter.ai/api/v1/models").toURL()
        val OPENROUTER_KEY_URL: URL = URI("https://openrouter.ai/api/v1/key").toURL()
        const val OLLAMA_IS_RUNNING = "Ollama is running"
        val modelCache = ConcurrentHashMap<String, Any>()
        const val CACHE_KEY = "models"
        const val CACHE_DURATION_MS = 30 * 1000
        private val gson = Gson()

        fun invalidateModelsCache() { modelCache.remove(CACHE_KEY) }

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
                } else { null }
            } catch (e: Exception) { null }
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
            !settings.isProxyEnabled -> "Mode: Bypass (Direct to ${settings.ollamaBaseUrl})"
            settings.openRouterApiKey.isBlank() -> "Mode: Proxy (OpenRouter API Key Required)"
            else -> "Mode: Proxy (OpenRouter + Ollama)"
        }
    }

    fun start() {
        if (server != null) {
            log.warn("Proxy server might be already running. Stopping it first to release the port.")
            stop()
        }
        val port = PluginSettingsState.getInstance().proxyPort
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
            val settings = PluginSettingsState.getInstance()
            val path = exchange.requestURI.path
            val method = exchange.requestMethod

            if (method == "GET" && path == "/api/tags") {
                handleModelsList(exchange)
                return
            }
            if (method == "GET" && path == "/") {
                sendPlainTextResponse(exchange, 200, OLLAMA_IS_RUNNING)
                return
            }

            if (method == "POST" && (path == "/api/chat" || path == "/v1/chat/completions")) {
                chatProxyHandler.handle(exchange)
                return
            }

            if (!settings.isProxyEnabled) {
                forwardToLocalOllama(exchange, path, method)
            } else {
                sendJsonResponse(exchange, 404, "{\"error\": \"Not Found\"}")
            }
        }

        private fun forwardToLocalOllama(exchange: HttpExchange, path: String, method: String) {
            var ollamaConnection: HttpURLConnection? = null
            val baseUrl = PluginSettingsState.getInstance().ollamaBaseUrl.removeSuffix("/")
            try {
                val ollamaUrl = URI.create("$baseUrl$path").toURL()
                ollamaConnection = ollamaUrl.openConnection() as HttpURLConnection
                ollamaConnection.requestMethod = method
                ollamaConnection.connectTimeout = 5000
                ollamaConnection.readTimeout = 30000

                exchange.requestHeaders.forEach { key, values ->
                    if (!key.equals("Host", ignoreCase = true)) {
                        ollamaConnection.setRequestProperty(key, values.joinToString(", "))
                    }
                }

                if (method == "POST" || method == "PUT") {
                    ollamaConnection.doOutput = true
                    exchange.requestBody.use { input -> ollamaConnection.outputStream.use { output -> input.copyTo(output) } }
                }

                val responseCode = ollamaConnection.responseCode
                exchange.responseHeaders.clear()
                ollamaConnection.headerFields.forEach { key, values ->
                    if (key != null) exchange.responseHeaders.put(key, values)
                }
                exchange.sendResponseHeaders(responseCode, 0)

                val inputStream = if (responseCode in 200..299) ollamaConnection.inputStream else ollamaConnection.errorStream
                inputStream?.use { input -> exchange.responseBody.use { output -> input.copyTo(output) } }
            } catch (e: IOException) {
                log.error("Failed to forward request to local Ollama at $baseUrl", e)
                val errorMsg = "{\"error\": \"Failed to connect to local Ollama at $baseUrl\"}"
                sendJsonResponse(exchange, 502, errorMsg)
            } finally {
                ollamaConnection?.disconnect()
                exchange.close()
            }
        }

        @Suppress("UNCHECKED_CAST")
        private fun handleModelsList(exchange: HttpExchange) {
            val cached = modelCache[CACHE_KEY] as? Pair<Long, String>
            if (cached != null && (System.currentTimeMillis() - cached.first) < CACHE_DURATION_MS) {
                sendJsonResponse(exchange, 200, cached.second)
                return
            }

            val localModels = fetchLocalOllamaModels()
            val openRouterModels = fetchOpenRouterModels()

            val combinedModels = mutableListOf<OllamaModel>()
            if (localModels.isNotEmpty() || openRouterModels.isNotEmpty()) {
                combinedModels.add(createDummyModel("ℹ️ If models don't appear, try Alt+Tab in AI Assistant"))
            }
            if (localModels.isNotEmpty()) { combinedModels.addAll(localModels) }
            if (localModels.isNotEmpty() && openRouterModels.isNotEmpty()) {
                combinedModels.add(createDummyModel("═══════════════════════════════"))
            }
            if (openRouterModels.isNotEmpty()) { combinedModels.addAll(openRouterModels) }

            val filteredModels = applyWhitelistFilter(combinedModels)

            val finalJson = gson.toJson(OllamaTagResponse(models = filteredModels))
            modelCache[CACHE_KEY] = Pair(System.currentTimeMillis(), finalJson)
            sendJsonResponse(exchange, 200, finalJson)
        }

        private fun fetchLocalOllamaModels(): List<OllamaModel> {
            return try {
                val settings = PluginSettingsState.getInstance()
                val baseUrl = settings.ollamaBaseUrl.removeSuffix("/")
                val ollamaUrl = URI.create("$baseUrl/api/tags").toURL()

                val ollamaConnection = ollamaUrl.openConnection() as HttpURLConnection
                ollamaConnection.requestMethod = "GET"
                ollamaConnection.connectTimeout = 3000
                ollamaConnection.readTimeout = 10000

                if (ollamaConnection.responseCode == 200) {
                    val ollamaResponse = ollamaConnection.inputStream.bufferedReader().use { it.readText() }
                    val ollamaTagResponse = gson.fromJson(ollamaResponse, OllamaTagResponse::class.java)

                    ollamaTagResponse.models.map { model ->
                        model.copy(name = "(local) ${model.name}", model = "(local) ${model.model}")
                    }
                } else {
                    log.warn("Failed to fetch local Ollama models. Status: ${ollamaConnection.responseCode}. URL: $ollamaUrl")
                    emptyList()
                }
            } catch (e: Exception) {
                log.warn("Error fetching local Ollama models. Check if Ollama is running and the URL is correct in settings.", e)
                emptyList()
            }
        }

        private fun fetchOpenRouterModels(): List<OllamaModel> {
            val settings = PluginSettingsState.getInstance()
            if (settings.openRouterApiKey.isBlank()) return emptyList()

            return try {
                val connection = OPENROUTER_MODELS_URL.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Authorization", "Bearer ${settings.openRouterApiKey}")
                connection.connectTimeout = 10000
                connection.readTimeout = 30000

                if (connection.responseCode == 200) {
                    val jsonResponse = connection.inputStream.bufferedReader().use { it.readText() }
                    val openRouterResponse = gson.fromJson(jsonResponse, OpenRouterModelsResponse::class.java)

                    openRouterResponse.data.map { openRouterModel -> createOllamaModelEntry(openRouterModel.id) }
                } else {
                    emptyList()
                }
            } catch (e: Exception) {
                emptyList()
            }
        }

        private fun createDummyModel(name: String): OllamaModel {
            val timestamp = Instant.now().toString()
            return OllamaModel(name, name, timestamp, 0L, "dummy:${name.hashCode()}", ModelDetails("dummy", "info", "0B", "N/A"))
        }

        private fun applyWhitelistFilter(models: List<OllamaModel>): List<OllamaModel> {
            val selectedModels = PluginSettingsState.getInstance().selectedModels
            return if (selectedModels.isEmpty()) {
                models
            } else {
                models.filter { model -> model.details.format == "dummy" || selectedModels.contains(model.name) }
            }
        }

        private fun createOllamaModelEntry(modelId: String): OllamaModel {
            val familyName = modelId.split("/").firstOrNull() ?: "unknown"
            return OllamaModel(modelId, modelId, Instant.now().toString(), estimateModelSize(modelId), generateModelDigest(modelId),
                ModelDetails("gguf", familyName, extractParameterSize(modelId), "Q4_0")
            )
        }

        private fun extractParameterSize(modelId: String): String = when {
            "8b" in modelId -> "8B"; "7b" in modelId -> "7B"; "13b" in modelId -> "13B"
            "70b" in modelId -> "70B"; "405b" in modelId -> "405B"; "gpt-4" in modelId -> "~1.7T"
            "gpt-3.5" in modelId -> "~175B"; "claude" in modelId -> "~200B"; "gemini" in modelId -> "~175B"
            else -> "Unknown"
        }

        private fun estimateModelSize(modelId: String): Long = when {
            "405b" in modelId -> 234_000_000_000L; "70b" in modelId -> 40_000_000_000L
            "8b" in modelId -> 4_700_000_000L; "7b" in modelId -> 3_800_000_000L
            else -> 5_000_000_000L
        }

        private fun generateModelDigest(modelId: String): String = "sha256:${modelId.hashCode().toString(16).padStart(8, '0')}${"a".repeat(56)}"
    }

    private inner class ChatProxyHandler {
        private val log: Logger = Logger.getInstance(ChatProxyHandler::class.java)
        private val gson = Gson()

        fun handle(exchange: HttpExchange) {
            var headersSent = false
            var requestBodyForLogging: String? = null
            try {
                val requestBody = readRequestBody(exchange)
                requestBodyForLogging = requestBody // For logging in case of error

                val settings = PluginSettingsState.getInstance()
                if (settings.enableDebugLogging) {
                    log.info("--- REQUEST from Client ---\nPath: ${exchange.requestURI.path}\nHeaders: ${exchange.requestHeaders.entries}\nBody: $requestBody")
                }

                val requestType = object : TypeToken<MutableMap<String, Any>>() {}.type
                val requestMap: MutableMap<String, Any> = gson.fromJson(requestBody, requestType)
                val modelName = requestMap["model"] as? String ?: ""

                if (modelName.startsWith("ℹ️") || modelName.startsWith("═══")) {
                    sendJsonResponse(exchange, 400, "{\"error\": \"This is an informational entry, not a usable model.\"}")
                    return
                }

                // *** CENTRALIZED PARAMETER OVERRIDE LOGIC ***
                if (settings.overrideParameters) {
                    log.info("Parameter override is ENABLED. Applying overrides...")
                    applyParameterOverrides(requestMap, settings.activeParameters)
                } else {
                    log.info("Parameter override is DISABLED.")
                }

                val isLocalModel = modelName.startsWith("(local)")

                if (settings.systemPrompt.isNotBlank()) {
                    val originalMessages = requestMap["messages"] as? List<Map<String, Any>>
                    if (originalMessages != null) {
                        val newMessages = mutableListOf<Map<String, Any>>()
                        val systemMessage = mapOf("role" to "system", "content" to settings.systemPrompt)
                        newMessages.add(systemMessage)
                        newMessages.addAll(originalMessages)
                        requestMap["messages"] = newMessages
                    }
                }

                if (settings.isProxyEnabled && !isLocalModel) {
                    // OpenRouter
                    handleOpenRouterRequest(exchange, requestMap, settings)
                } else {
                    // Local Ollama
                    if (isLocalModel) {
                        requestMap["model"] = modelName.removePrefix("(local) ")
                    }
                    forwardToLocalOllama(exchange, requestMap)
                }
            } catch (e: Exception) {
                log.error("Error during proxying request. Initial request body: $requestBodyForLogging", e)
                if (!headersSent) {
                    sendJsonResponse(exchange, 500, "{\"error\": \"Internal proxy error: ${e.message?.replace("\"", "'")}\"}")
                }
            }
        }

        // *** FIX: Simplified signature, it already has the final requestMap ***
        private fun handleOpenRouterRequest(exchange: HttpExchange, requestMap: MutableMap<String, Any>, settings: PluginSettingsState) {
            val modelName = requestMap["model"] as? String ?: ""
            if (settings.openRouterApiKey.isBlank()) {
                sendJsonResponse(exchange, 401, "{\"error\": \"OpenRouter API Key is not configured.\"}")
                return
            }
            if (settings.selectedModels.isNotEmpty() && !settings.selectedModels.contains(modelName)) {
                sendJsonResponse(exchange, 404, "{\"error\": \"Model '$modelName' is not in your whitelist.\"}")
                return
            }

            if (modelName.endsWith(":latest")) {
                requestMap["model"] = modelName.removeSuffix(":latest")
            }

            val modifiedBody = gson.toJson(requestMap)
            if (settings.enableDebugLogging) {
                log.info("--- REQUEST to OpenRouter ---\nURL: ${OPENROUTER_CHAT_URL}\nBody: $modifiedBody")
            }

            val connection = OPENROUTER_CHAT_URL.openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = "POST"
                doOutput = true
                readTimeout = 60000
                connectTimeout = 15000
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer ${settings.openRouterApiKey}")
                setRequestProperty("HTTP-Referer", "https://github.com/zxcizc/OllamaOpenRouterProxy-jetbrains-plugin")
                setRequestProperty("X-Title", "Ollama OpenRouter Proxy")
            }
            connection.outputStream.use { it.write(modifiedBody.toByteArray(StandardCharsets.UTF_8)) }

            val responseCode = connection.responseCode

            connection.headerFields.forEach { key, values -> if (key != null) exchange.responseHeaders.put(key, values) }
            exchange.sendResponseHeaders(responseCode, 0)

            val responseStream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
            val (loggingStream, originalStream) = teeInputStream(responseStream)

            Thread {
                if (settings.enableDebugLogging) {
                    val responseBodyForLogging = loggingStream.bufferedReader().readText()
                    log.info("--- RESPONSE from OpenRouter ---\nStatus: $responseCode\nHeaders: ${connection.headerFields}\nBody: $responseBodyForLogging")
                } else {
                    loggingStream.readBytes()
                }
            }.start()

            exchange.responseBody.use { clientResponseStream ->
                originalStream.use { serverResponseStream ->
                    val reader = BufferedReader(InputStreamReader(serverResponseStream, StandardCharsets.UTF_8))
                    reader.forEachLine { line ->
                        if (line.startsWith("data:")) {
                            val jsonData = line.substring(5).trim()
                            if (jsonData == "[DONE]") {
                                clientResponseStream.write((createFinalOllamaChunk(modelName) + "\n").toByteArray(StandardCharsets.UTF_8))
                            } else {
                                convertOpenAiToOllamaChunk(jsonData, modelName)?.let {
                                    clientResponseStream.write((it + "\n").toByteArray(StandardCharsets.UTF_8))
                                }
                            }
                        }
                    }
                }
            }
        }

        private fun forwardToLocalOllama(exchange: HttpExchange, requestMap: Map<String, Any>) {
            val modifiedBody = gson.toJson(requestMap)
            if (PluginSettingsState.getInstance().enableDebugLogging) {
                log.info("--- REQUEST to Local Ollama ---\nURL: ${PluginSettingsState.getInstance().ollamaBaseUrl}/api/chat\nBody: $modifiedBody")
            }
            var ollamaConnection: HttpURLConnection? = null
            val baseUrl = PluginSettingsState.getInstance().ollamaBaseUrl.removeSuffix("/")
            try {
                val ollamaUrl = URI.create("$baseUrl/api/chat").toURL()
                ollamaConnection = ollamaUrl.openConnection() as HttpURLConnection
                ollamaConnection.requestMethod = "POST"
                ollamaConnection.doOutput = true
                ollamaConnection.setRequestProperty("Content-Type", "application/json")

                ollamaConnection.outputStream.use { it.write(modifiedBody.toByteArray(StandardCharsets.UTF_8)) }

                val responseCode = ollamaConnection.responseCode
                ollamaConnection.headerFields.forEach { key, values ->
                    if (key != null) exchange.responseHeaders.put(key, values)
                }
                exchange.sendResponseHeaders(responseCode, 0)

                val inputStream = if (responseCode in 200..299) ollamaConnection.inputStream else ollamaConnection.errorStream
                inputStream?.use { input -> exchange.responseBody.use { output -> input.copyTo(output) } }
            } catch (e: IOException) {
                log.error("Failed to forward local model request to Ollama at $baseUrl", e)
                sendJsonResponse(exchange, 502, "{\"error\": \"Failed to connect to local Ollama at $baseUrl\"}")
            } finally {
                ollamaConnection?.disconnect()
                exchange.close()
            }
        }

        private fun applyParameterOverrides(requestMap: MutableMap<String, Any>, params: ParameterPreset) {
            // *** FIX: Always add parameters if they exist in the object (no default value check) ***
            params.temperature?.let { requestMap["temperature"] = it }
            params.topP?.let { requestMap["top_p"] = it }
            params.topK?.let { requestMap["top_k"] = it }
            params.minP?.let { requestMap["min_p"] = it }
            params.topA?.let { requestMap["top_a"] = it }
            params.seed?.let { requestMap["seed"] = it }
            params.frequencyPenalty?.let { requestMap["frequency_penalty"] = it }
            params.presencePenalty?.let { requestMap["presence_penalty"] = it }
            params.repetitionPenalty?.let { requestMap["repetition_penalty"] = it }
            params.maxTokens?.let { requestMap["max_tokens"] = it }
            params.logprobs?.let { requestMap["logprobs"] = it }
            params.topLogprobs?.let { requestMap["top_logprobs"] = it }
            params.stop?.let { if (it.isNotEmpty()) requestMap["stop"] = it }
            params.responseFormatType?.let { if (it.isNotBlank()) requestMap["response_format"] = mapOf("type" to it) }

            fun addJsonParameter(key: String, jsonString: String?) {
                jsonString?.takeIf { it.isNotBlank() }?.let {
                    try {
                        requestMap[key] = gson.fromJson<Any>(it, Any::class.java)
                    } catch (e: Exception) {
                        log.error("Failed to parse JSON for parameter '$key': $it", e)
                    }
                }
            }
            addJsonParameter("tools", params.toolsJson)
            addJsonParameter("tool_choice", params.toolChoiceJson)
            addJsonParameter("logit_bias", params.logitBiasJson)
        }

        private fun convertOpenAiToOllamaChunk(openAiJson: String, modelName: String): String? {
            return try {
                val openAiChunk = gson.fromJson(openAiJson, OpenAIChatChunk::class.java)
                val delta = openAiChunk.choices.firstOrNull()?.delta ?: return null
                if (delta.content == null && delta.role != null) return null
                val ollamaMessage = OllamaMessage(role = delta.role ?: "assistant", content = delta.content ?: "")
                val ollamaChunk = OllamaChatChunk(modelName, Instant.now().toString(), ollamaMessage, false)
                gson.toJson(ollamaChunk)
            } catch (e: Exception) {
                log.error("Failed to convert OpenAI chunk to Ollama format: $openAiJson", e)
                null
            }
        }

        private fun createFinalOllamaChunk(modelName: String): String {
            val finalChunk = OllamaFinalChunk(modelName, Instant.now().toString(), OllamaMessage("assistant", ""), true, 0, 0, 0, 0, 0, 0)
            return gson.toJson(finalChunk)
        }

        private fun readRequestBody(exchange: HttpExchange): String {
            return exchange.requestBody.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        }
        // 스트림을 복제하는 헬퍼 함수
        private fun teeInputStream(input: InputStream?): Pair<InputStream, InputStream> {
            if (input == null) return ByteArrayInputStream(byteArrayOf()) to ByteArrayInputStream(byteArrayOf())
            val baos = ByteArrayOutputStream()
            input.copyTo(baos)
            val bytes = baos.toByteArray()
            return ByteArrayInputStream(bytes) to ByteArrayInputStream(bytes)
        }
    }
}