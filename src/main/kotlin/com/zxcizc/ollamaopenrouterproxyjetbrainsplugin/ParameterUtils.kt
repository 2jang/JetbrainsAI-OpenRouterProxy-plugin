package com.zxcizc.ollamaopenrouterproxyjetbrainsplugin

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import javax.swing.JComponent

/**
 * 파라미터 관련 유틸리티 함수들
 */
object ParameterUtils {
    
    private val gson = Gson()
    
    // ===== 자료형 변환 함수들 =====
    
    /**
     * 문자열을 Double로 변환하고 범위 제한
     */
    fun parseDouble(input: String, min: Double, max: Double, defaultValue: Double): Double {
        return try {
            val value = input.toDouble()
            value.coerceIn(min, max)
        } catch (e: NumberFormatException) {
            defaultValue
        }
    }
    
    /**
     * 문자열을 Int로 변환하고 범위 제한
     */
    fun parseInt(input: String, min: Int, max: Int): Int? {
        return try {
            if (input.isBlank()) null
            else {
                val value = input.toInt()
                if (value in min..max) value else null
            }
        } catch (e: NumberFormatException) {
            null
        }
    }
    
    /**
     * 문자열을 Int로 변환 (범위 제한 없음)
     */
    fun parseIntOrNull(input: String): Int? {
        return try {
            if (input.isBlank()) null else input.toInt()
        } catch (e: NumberFormatException) {
            null
        }
    }
    
    /**
     * Stop sequences 파싱 (쉼표, 줄바꿈, 세미콜론으로 구분)
     */
    fun parseStopSequences(input: String): List<String> {
        return if (input.isBlank()) {
            emptyList()
        } else {
            input.split(",", "\n", ";")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
        }
    }
    
    /**
     * JSON 문자열을 Map으로 파싱
     */
    fun parseJsonMap(input: String): Map<String, Double>? {
        return try {
            if (input.isBlank()) {
                emptyMap()
            } else {
                val type = object : TypeToken<Map<String, Double>>() {}.type
                gson.fromJson<Map<String, Double>>(input, type)
            }
        } catch (e: JsonSyntaxException) {
            null
        }
    }
    
    /**
     * JSON 문자열을 List<Map>으로 파싱 (tools용)
     */
    fun parseJsonList(input: String): List<Map<String, Any>>? {
        return try {
            if (input.isBlank()) {
                emptyList()
            } else {
                val type = object : TypeToken<List<Map<String, Any>>>() {}.type
                gson.fromJson<List<Map<String, Any>>>(input, type)
            }
        } catch (e: JsonSyntaxException) {
            null
        }
    }
    
    // ===== 값 검증 함수들 =====
    
    /**
     * 파라미터 값이 유효한지 검증
     */
    fun validateParameter(definition: ParameterDefinition, value: Any?): String? {
        return definition.validation?.invoke(value)
    }
    
    /**
     * 모든 파라미터 검증
     */
    fun validateAllParameters(parameters: GenerationParameters): Map<String, String> {
        val errors = mutableMapOf<String, String>()
        
        ParameterDefinitions.ALL_PARAMETERS.forEach { definition ->
            val value = when (definition.key) {
                "temperature" -> parameters.temperature
                "max_tokens" -> parameters.maxTokens
                "top_p" -> parameters.topP
                "top_k" -> parameters.topK
                "frequency_penalty" -> parameters.frequencyPenalty
                "presence_penalty" -> parameters.presencePenalty
                "repetition_penalty" -> parameters.repetitionPenalty
                "min_p" -> parameters.minP
                "top_a" -> parameters.topA
                "seed" -> parameters.seed
                "stop" -> parameters.stop
                "response_format" -> parameters.responseFormat
                "logprobs" -> parameters.logprobs
                "top_logprobs" -> parameters.topLogprobs
                "structured_outputs" -> parameters.structuredOutputs
                "logit_bias" -> parameters.logitBias
                "tools" -> parameters.tools
                "tool_choice" -> parameters.toolChoice
                else -> null
            }
            
            validateParameter(definition, value)?.let { error ->
                errors[definition.key] = error
            }
        }
        
        return errors
    }
    
    // ===== 범위 파싱 함수들 =====
    
    /**
     * "0.0 - 2.0" 형태의 범위 문자열을 Pair<Double, Double>로 파싱
     */
    fun parseDoubleRange(rangeStr: String): Pair<Double, Double> {
        return try {
            val parts = rangeStr.split("-").map { it.trim() }
            if (parts.size == 2) {
                Pair(parts[0].toDouble(), parts[1].toDouble())
            } else {
                Pair(0.0, 1.0) // 기본값
            }
        } catch (e: Exception) {
            Pair(0.0, 1.0) // 기본값
        }
    }
    
    /**
     * "0 - 100" 형태의 범위 문자열을 Pair<Int, Int>로 파싱
     */
    fun parseIntRange(rangeStr: String): Pair<Int, Int> {
        return try {
            val parts = rangeStr.split("-").map { it.trim() }
            if (parts.size == 2) {
                Pair(parts[0].toInt(), parts[1].toInt())
            } else {
                Pair(0, 100) // 기본값
            }
        } catch (e: Exception) {
            Pair(0, 100) // 기본값
        }
    }
    
    // ===== 슬라이더 변환 함수들 =====
    
    /**
     * 슬라이더 값(0-1000)을 실제 값으로 변환
     */
    fun sliderToValue(sliderValue: Int, range: Pair<Double, Double>): Double {
        val ratio = sliderValue / 1000.0
        return range.first + ratio * (range.second - range.first)
    }
    
    /**
     * 실제 값을 슬라이더 값(0-1000)으로 변환
     */
    fun valueToSlider(value: Double, range: Pair<Double, Double>): Int {
        val ratio = (value - range.first) / (range.second - range.first)
        return (ratio * 1000).toInt().coerceIn(0, 1000)
    }
    
    // ===== JSON 변환 함수들 =====
    
    /**
     * Map을 예쁜 JSON 문자열로 변환
     */
    fun mapToJsonString(map: Map<String, Any>): String {
        return if (map.isEmpty()) {
            ""
        } else {
            gson.toJson(map)
        }
    }
    
    /**
     * List를 예쁜 JSON 문자열로 변환
     */
    fun listToJsonString(list: List<Any>): String {
        return if (list.isEmpty()) {
            ""
        } else {
            gson.toJson(list)
        }
    }
    
    /**
     * Stop sequences를 문자열로 변환 (UI 표시용)
     */
    fun stopSequencesToString(sequences: List<String>): String {
        return sequences.joinToString(", ")
    }
    
    // ===== 프리셋 관련 함수들 =====
    
    /**
     * 미리 정의된 파라미터 프리셋들
     */
    object Presets {
        
        fun creative(): GenerationParameters {
            return GenerationParameters().apply {
                temperature = 1.3
                topP = 0.9
                topK = 40
                frequencyPenalty = 0.3
                presencePenalty = 0.3
            }
        }
        
        fun balanced(): GenerationParameters {
            return GenerationParameters().apply {
                temperature = 1.0
                topP = 1.0
                topK = 0
                frequencyPenalty = 0.0
                presencePenalty = 0.0
            }
        }
        
        fun precise(): GenerationParameters {
            return GenerationParameters().apply {
                temperature = 0.3
                topP = 0.7
                topK = 10
                frequencyPenalty = 0.0
                presencePenalty = 0.0
            }
        }
        
        fun deterministic(): GenerationParameters {
            return GenerationParameters().apply {
                temperature = 0.0
                topP = 1.0
                topK = 1
                seed = 42
            }
        }
        
        fun coding(): GenerationParameters {
            return GenerationParameters().apply {
                temperature = 0.2
                topP = 0.95
                topK = 0
                frequencyPenalty = 0.1
                presencePenalty = 0.1
                stop = listOf("```", "\n\n\n")
            }
        }
        
        fun getAllPresets(): Map<String, GenerationParameters> {
            return mapOf(
                "🎨 Creative" to creative(),
                "⚖️ Balanced" to balanced(), 
                "🎯 Precise" to precise(),
                "🔒 Deterministic" to deterministic(),
                "💻 Coding" to coding()
            )
        }
    }
    
    // ===== UI 헬퍼 함수들 =====
    
    /**
     * 값이 기본값인지 확인
     */
    fun isDefaultValue(definition: ParameterDefinition, value: Any?): Boolean {
        return when (definition.defaultValue) {
            null -> value == null
            is Double -> (value as? Double) == definition.defaultValue
            is Int -> (value as? Int) == definition.defaultValue
            is Boolean -> (value as? Boolean) == definition.defaultValue
            is List<*> -> (value as? List<*>)?.isEmpty() == true
            is Map<*, *> -> (value as? Map<*, *>)?.isEmpty() == true
            else -> value == definition.defaultValue
        }
    }
    
    /**
     * 파라미터 그룹에서 기본값이 아닌 파라미터 개수 계산
     */
    fun countNonDefaultParameters(group: ParameterDefinitions.ParameterGroup, parameters: GenerationParameters): Int {
        return group.parameters.count { definition ->
            val value = extractValueFromParameters(parameters, definition.key)
            !isDefaultValue(definition, value)
        }
    }
    
    /**
     * GenerationParameters에서 특정 키의 값 추출
     */
    private fun extractValueFromParameters(parameters: GenerationParameters, key: String): Any? {
        return when (key) {
            "temperature" -> parameters.temperature
            "max_tokens" -> parameters.maxTokens
            "top_p" -> parameters.topP
            "top_k" -> parameters.topK
            "frequency_penalty" -> parameters.frequencyPenalty
            "presence_penalty" -> parameters.presencePenalty
            "repetition_penalty" -> parameters.repetitionPenalty
            "min_p" -> parameters.minP
            "top_a" -> parameters.topA
            "seed" -> parameters.seed
            "stop" -> parameters.stop
            "response_format" -> parameters.responseFormat
            "logprobs" -> parameters.logprobs
            "top_logprobs" -> parameters.topLogprobs
            "structured_outputs" -> parameters.structuredOutputs
            "logit_bias" -> parameters.logitBias
            "tools" -> parameters.tools
            "tool_choice" -> parameters.toolChoice
            else -> null
        }
    }
}
