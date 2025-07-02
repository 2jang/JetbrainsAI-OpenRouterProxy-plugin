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

// --- 타입 안정성을 위한 데이터 클래스 정의 ---
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

// OpenRouter Key API 응답 데이터 클래스들
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
        const val PROXY_PORT = 11444
        const val OLLAMA_PORT = 11434
        val OPENROUTER_CHAT_URL: URL = URI("https://openrouter.ai/api/v1/chat/completions").toURL()
        val OPENROUTER_MODELS_URL: URL = URI("https://openrouter.ai/api/v1/models").toURL()
        val OPENROUTER_KEY_URL: URL = URI("https://openrouter.ai/api/v1/key").toURL()
        const val OLLAMA_IS_RUNNING = "Ollama is running"
        val modelCache = ConcurrentHashMap<String, Any>()
        const val CACHE_KEY = "models"
        const val CACHE_DURATION_MS = 30 * 1000 // 30초로 단축
        private val gson = Gson()
        
        // 모델 캐시 무효화
        fun invalidateModelsCache() {
            modelCache.remove(CACHE_KEY)
        }
        
        // API 키 검증 함수 (UI에서 호출 가능)
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
                    val keyResponse = gson.fromJson(jsonResponse, OpenRouterKeyResponse::class.java)
                    keyResponse.data
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }
    }
    
    // 서버 상태 조회 메서드 (더 안정적인 체크)
    fun isRunning(): Boolean {
        return try {
            server?.address != null
        } catch (e: Exception) {
            false
        }
    }
    
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
        try {
            if (server?.address != null) {
                log.info("Proxy server is already running on port $PROXY_PORT")
                return
            }
            server = HttpServer.create(InetSocketAddress("localhost", PROXY_PORT), 0).apply {
                createContext("/", UniversalHandler())
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
            
            // 하이브리드 모드: 모든 요청을 처리
            when {
                (method == "GET" && path == "/api/tags") -> 
                    handleModelsList(exchange) // 동일한 하이브리드 모델 리스트 반환
                (method == "POST" && (path == "/api/chat" || path == "/v1/chat/completions")) -> 
                    chatProxyHandler.handle(exchange) // 동일한 하이브리드 처리
                (method == "GET" && path == "/") -> 
                    sendPlainTextResponse(exchange, 200, OLLAMA_IS_RUNNING)
                else -> 
                    forwardToLocalOllama(exchange, path, method)
            }
        }
        
        private fun forwardToLocalOllama(exchange: HttpExchange, path: String, method: String) {
            var ollamaConnection: HttpURLConnection? = null
            try {
                val ollamaUrl = URI("http://localhost:$OLLAMA_PORT$path").toURL()
                ollamaConnection = ollamaUrl.openConnection() as HttpURLConnection
                ollamaConnection.requestMethod = method
                ollamaConnection.connectTimeout = 5000
                ollamaConnection.readTimeout = 30000

                // Request headers 복사
                exchange.requestHeaders.forEach { key, values ->
                    if (!key.equals("Host", ignoreCase = true)) {
                        ollamaConnection.setRequestProperty(key, values.joinToString(", "))
                    }
                }

                // Request body 복사 (POST/PUT의 경우)
                if (method == "POST" || method == "PUT") {
                    ollamaConnection.doOutput = true
                    exchange.requestBody.use { input ->
                        ollamaConnection.outputStream.use { output ->
                            input.copyTo(output)
                        }
                    }
                }

                // Response 복사
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
                log.error("Failed to forward request to local Ollama", e)
                val errorMsg = "{\"error\": \"Failed to connect to local Ollama at localhost:$OLLAMA_PORT\"}"
                sendJsonResponse(exchange, 502, errorMsg)
            } finally {
                ollamaConnection?.disconnect()
                exchange.close()
            }
        }

        @Suppress("UNCHECKED_CAST")
        private fun handleModelsList(exchange: HttpExchange) {
            // 캐시 확인
            val cached = modelCache[CACHE_KEY] as? Pair<Long, String>
            if (cached != null && (System.currentTimeMillis() - cached.first) < CACHE_DURATION_MS) {
                sendJsonResponse(exchange, 200, cached.second)
                return
            }

            // 하이브리드 모델 리스트 생성
            val localModels = fetchLocalOllamaModels()
            val openRouterModels = fetchOpenRouterModels()
            
            val combinedModels = mutableListOf<OllamaModel>()
            
            // 안내 메시지 더미 모델 추가
            combinedModels.add(createDummyModel("ℹ️ If models don't appear, try Alt+Tab twice in AI Assistant"))
            
            // 로컬 모델 추가
            if (localModels.isNotEmpty()) {
                combinedModels.addAll(localModels)
            }
            
            // 구분선 추가 (둘 다 있을 때만)
            if (localModels.isNotEmpty() && openRouterModels.isNotEmpty()) {
                combinedModels.add(createDummyModel("═══════════════════════════════"))
            }
            
            // OpenRouter 모델 추가  
            if (openRouterModels.isNotEmpty()) {
                combinedModels.addAll(openRouterModels)
            }
            
            // 화이트리스트 필터링 (더미 모델 제외)
            val filteredModels = applyWhitelistFilter(combinedModels)
            
            val finalJson = gson.toJson(OllamaTagResponse(models = filteredModels))
            modelCache[CACHE_KEY] = Pair(System.currentTimeMillis(), finalJson)
            sendJsonResponse(exchange, 200, finalJson)
        }
        
        private fun fetchLocalOllamaModels(): List<OllamaModel> {
            return try {
                val ollamaUrl = URI("http://localhost:$OLLAMA_PORT/api/tags").toURL()
                val ollamaConnection = ollamaUrl.openConnection() as HttpURLConnection
                ollamaConnection.requestMethod = "GET"
                ollamaConnection.connectTimeout = 3000
                ollamaConnection.readTimeout = 10000
                
                if (ollamaConnection.responseCode == 200) {
                    val ollamaResponse = ollamaConnection.inputStream.bufferedReader().use { it.readText() }
                    val ollamaTagResponse = gson.fromJson(ollamaResponse, OllamaTagResponse::class.java)
                    
                    // 로컬 모델들에 (local) 접두사 추가
                    ollamaTagResponse.models.map { model ->
                        model.copy(
                            name = "(local) ${model.name}",
                            model = "(local) ${model.model}"
                        )
                    }
                } else {
                    emptyList()
                }
            } catch (e: Exception) {
                emptyList()
            }
        }
        
        private fun fetchOpenRouterModels(): List<OllamaModel> {
            val settings = PluginSettingsState.getInstance()
            val apiKey = settings.openRouterApiKey
            
            if (apiKey.isBlank()) return emptyList()
            
            return try {
                val connection = OPENROUTER_MODELS_URL.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Authorization", "Bearer $apiKey")
                connection.connectTimeout = 10000
                connection.readTimeout = 30000
                
                if (connection.responseCode == 200) {
                    val jsonResponse = connection.inputStream.bufferedReader().use { it.readText() }
                    val openRouterResponse = gson.fromJson(jsonResponse, OpenRouterModelsResponse::class.java)
                    
                    // OpenRouter 모델들을 Ollama 형식으로 변환 (원래 ID 유지)
                    openRouterResponse.data.map { openRouterModel ->
                        createOllamaModelEntry(openRouterModel.id)
                    }
                } else {
                    emptyList()
                }
            } catch (e: Exception) {
                emptyList()
            }
        }
        
        private fun createDummyModel(name: String): OllamaModel {
            val timestamp = Instant.now().toString()
            return OllamaModel(
                name = name,
                model = name,
                modified_at = timestamp,
                size = 0L,
                digest = "dummy:${name.hashCode()}",
                details = ModelDetails(
                    format = "dummy",
                    family = "info",
                    parameter_size = "0B",
                    quantization_level = "N/A"
                )
            )
        }
        
        private fun applyWhitelistFilter(models: List<OllamaModel>): List<OllamaModel> {
            val settings = PluginSettingsState.getInstance()
            val selectedModels = settings.selectedModels
            
            return if (selectedModels.isEmpty()) {
                models
            } else {
                models.filter { model ->
                    // 더미 모델(안내 메시지, 구분선)은 항상 포함
                    model.details.format == "dummy" || selectedModels.contains(model.name)
                }
            }
        }
        
        private fun createOllamaModelEntry(modelId: String): OllamaModel {
            val familyName = modelId.split("/").firstOrNull() ?: "unknown"
            val timestamp = Instant.now().toString()
            
            return OllamaModel(
                name = modelId, // OpenRouter 모델은 원래 이름 그대로
                model = modelId,
                modified_at = timestamp,
                size = estimateModelSize(modelId),
                digest = generateModelDigest(modelId),
                details = ModelDetails(
                    format = "gguf",
                    family = familyName,
                    parameter_size = extractParameterSize(modelId),
                    quantization_level = "Q4_0"
                )
            )
        }
        
        private fun extractParameterSize(modelId: String): String {
            return when {
                modelId.contains("8b", ignoreCase = true) -> "8B"
                modelId.contains("7b", ignoreCase = true) -> "7B"
                modelId.contains("13b", ignoreCase = true) -> "13B"
                modelId.contains("70b", ignoreCase = true) -> "70B"
                modelId.contains("405b", ignoreCase = true) -> "405B"
                modelId.contains("gpt-4") -> "~1.7T"
                modelId.contains("gpt-3.5") -> "~175B"
                modelId.contains("claude") -> "~200B"
                modelId.contains("gemini") -> "~175B"
                else -> "Unknown"
            }
        }
        
        private fun estimateModelSize(modelId: String): Long {
            return when {
                modelId.contains("405b", ignoreCase = true) -> 234_000_000_000L
                modelId.contains("70b", ignoreCase = true) -> 40_000_000_000L
                modelId.contains("8b", ignoreCase = true) -> 4_700_000_000L
                modelId.contains("7b", ignoreCase = true) -> 3_800_000_000L
                modelId.contains("gpt-4") -> 80_000_000_000L
                modelId.contains("gpt-3.5") -> 20_000_000_000L
                modelId.contains("claude") -> 25_000_000_000L
                modelId.contains("gemini") -> 22_000_000_000L
                else -> 5_000_000_000L
            }
        }
        
        private fun generateModelDigest(modelId: String): String {
            val hash = modelId.hashCode().toString(16).padStart(8, '0')
            return "sha256:${hash}${"a".repeat(56)}"
        }
    }

    private inner class ChatProxyHandler {
        private val log: Logger = Logger.getInstance(ChatProxyHandler::class.java)
        private val gson = Gson()

        fun handle(exchange: HttpExchange) {
            var headersSent = false
            try {
                val requestBody = readRequestBody(exchange.requestURI.path, exchange.requestBody)
                val originalRequest = gson.fromJson(requestBody, OllamaChatRequest::class.java)
                
                // 더미 모델 요청 차단
                if (originalRequest.model.startsWith("ℹ️") || originalRequest.model.startsWith("═══")) {
                    val errorResponse = """
                    {
                        "error": {
                            "message": "This is an informational entry, not a usable model. Please select an actual model from the list.",
                            "type": "invalid_model",
                            "code": "dummy_model_request"
                        }
                    }
                    """.trimIndent()
                    sendJsonResponse(exchange, 400, errorResponse)
                    return
                }
                
                // (local) 접두사가 있는 모델은 로컬 Ollama로 전송
                if (originalRequest.model.startsWith("(local)")) {
                    handleLocalModelRequest(exchange, originalRequest)
                    return
                }
                
                // OpenRouter 모델 처리
                val apiKey = PluginSettingsState.getInstance().openRouterApiKey
                if (apiKey.isBlank()) {
                    val errorResponse = """
                    {
                        "error": {
                            "message": "OpenRouter API Key is not configured in IDE Settings.",
                            "type": "authentication_error",
                            "code": "api_key_required"
                        }
                    }
                    """.trimIndent()
                    sendJsonResponse(exchange, 401, errorResponse)
                    return
                }

                // 화이트리스트 확인
                val settings = PluginSettingsState.getInstance()
                if (settings.selectedModels.isNotEmpty() && !settings.selectedModels.contains(originalRequest.model)) {
                    val errorResponse = """
                    {
                        "error": {
                            "message": "Model '${originalRequest.model}' is not available. Please check your model whitelist.",
                            "type": "model_not_found",
                            "code": "model_not_whitelisted"
                        }
                    }
                    """.trimIndent()
                    sendJsonResponse(exchange, 404, errorResponse)
                    return
                }
                
                // OpenRouter로 요청 전송
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

                connection.headerFields.forEach { (key, values) -> 
                    if (key != null) exchange.responseHeaders.put(key, values) 
                }
                exchange.sendResponseHeaders(responseCode, 0)
                headersSent = true

                // OpenAI 응답을 Ollama 형식으로 변환
                exchange.responseBody.use { clientResponseStream ->
                    responseStream?.use { serverResponseStream ->
                        val reader = BufferedReader(InputStreamReader(serverResponseStream, StandardCharsets.UTF_8))
                        reader.forEachLine { line ->
                            if (line.startsWith("data:")) {
                                val jsonData = line.substring(5).trim()
                                if (jsonData == "[DONE]") {
                                    val finalChunk = createFinalOllamaChunk(originalRequest)
                                    clientResponseStream.write((finalChunk + "\n").toByteArray(StandardCharsets.UTF_8))
                                    clientResponseStream.flush()
                                } else {
                                    val ollamaChunk = convertOpenAiToOllamaChunk(jsonData, originalRequest)
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
                    val errorResponse = """
                    {
                        "error": {
                            "message": "Internal proxy error: ${e.message}",
                            "type": "proxy_error",
                            "code": "internal_error"
                        }
                    }
                    """.trimIndent()
                    sendJsonResponse(exchange, 500, errorResponse)
                }
            } finally {
                if (!headersSent) {
                    exchange.close()
                }
            }
        }
        
        private fun handleLocalModelRequest(exchange: HttpExchange, originalRequest: OllamaChatRequest) {
            // (local) 접두사 제거
            val localModelName = originalRequest.model.removePrefix("(local) ")
            originalRequest.model = localModelName
            val modifiedBody = gson.toJson(originalRequest)
            
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
                val errorResponse = """
                {
                    "error": {
                        "message": "Failed to connect to local Ollama at localhost:$OLLAMA_PORT. Is it running?",
                        "type": "connection_error",
                        "code": "local_ollama_unavailable"
                    }
                }
                """.trimIndent()
                sendJsonResponse(exchange, 502, errorResponse)
            } finally {
                ollamaConnection?.disconnect()
                exchange.close()
            }
        }

        private fun convertOpenAiToOllamaChunk(openAiJson: String, originalRequest: OllamaChatRequest): String? {
            return try {
                val openAiChunk = gson.fromJson(openAiJson, OpenAIChatChunk::class.java)
                val delta = openAiChunk.choices.firstOrNull()?.delta ?: return null

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

        private fun removeLatestTagFromModel(request: OllamaChatRequest) {
            if (request.model.endsWith(":latest")) {
                val newModel = request.model.removeSuffix(":latest")
                log.info("Model tag removed: '${request.model}' -> '$newModel'")
                request.model = newModel
            }
        }
    }
}
