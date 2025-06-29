package com.zxcizc.ollamaopenrouterproxyjetbrainsplugin

/**
 * 파라미터 UI 컴포넌트 타입
 */
enum class ParameterType {
    FLOAT_SLIDER,        // 실수 슬라이더 + 텍스트 필드
    INTEGER_SPINNER,     // 정수 스피너
    BOOLEAN_CHECKBOX,    // 불린 체크박스
    STRING_FIELD,        // 문자열 입력 필드
    JSON_TEXTAREA,       // JSON 텍스트 에어리어
    TAG_INPUT,          // 태그 입력 (여러 문자열)
    COMBO_BOX           // 콤보박스 (선택 옵션)
}

/**
 * 파라미터 정의 데이터 클래스
 */
data class ParameterDefinition(
    val key: String,                    // API 키 이름
    val displayName: String,            // UI 표시명
    val description: String,            // 상세 설명
    val type: ParameterType,           // UI 컴포넌트 타입
    val defaultValue: Any?,            // 기본값
    val range: String? = null,         // 허용 범위 (표시용)
    val hint: String? = null,          // 힌트/예시 텍스트
    val explainerVideo: String? = null, // 설명 영상 URL
    val options: List<String>? = null,  // 콤보박스 옵션들
    val validation: ((Any?) -> String?)? = null // 유효성 검증 함수
)

/**
 * 모든 파라미터 정의를 담은 싱글톤 객체
 */
object ParameterDefinitions {
    
    /**
     * 핵심 파라미터들 (기본 섹션에 표시)
     */
    val CORE_PARAMETERS = listOf(
        ParameterDefinition(
            key = "temperature",
            displayName = "Temperature",
            description = "Influences variety in responses. Lower values = more predictable, higher values = more diverse. At 0, always gives same response for given input.",
            type = ParameterType.FLOAT_SLIDER,
            defaultValue = 1.0,
            range = "0.0 - 2.0",
            explainerVideo = "https://youtu.be/ezgqHnWvua8",
            validation = { value ->
                val temp = (value as? Double) ?: return@ParameterDefinition "Invalid number"
                if (temp < 0.0 || temp > 2.0) "Temperature must be between 0.0 and 2.0" else null
            }
        ),
        ParameterDefinition(
            key = "max_tokens",
            displayName = "Max Tokens",
            description = "Upper limit for tokens the model can generate. Won't produce more than this limit. Maximum is context length minus prompt length.",
            type = ParameterType.INTEGER_SPINNER,
            defaultValue = null,
            range = "1 or above",
            hint = "Leave empty for unlimited (up to model's context)",
            validation = { value ->
                val tokens = value as? Int
                if (tokens != null && tokens < 1) "Max tokens must be 1 or above" else null
            }
        ),
        ParameterDefinition(
            key = "top_p",
            displayName = "Top P",
            description = "Limits model's choices to percentage of likely tokens. Lower = more predictable, default allows full range. Like dynamic Top-K.",
            type = ParameterType.FLOAT_SLIDER,
            defaultValue = 1.0,
            range = "0.0 - 1.0",
            explainerVideo = "https://youtu.be/wQP-im_HInk",
            validation = { value ->
                val topP = (value as? Double) ?: return@ParameterDefinition "Invalid number"
                if (topP < 0.0 || topP > 1.0) "Top P must be between 0.0 and 1.0" else null
            }
        ),
        ParameterDefinition(
            key = "top_k",
            displayName = "Top K",
            description = "Limits model's token choices at each step. Value of 1 = always most likely token. 0 = considers all choices (disabled).",
            type = ParameterType.INTEGER_SPINNER,
            defaultValue = 0,
            range = "0 or above",
            hint = "0 = disabled (consider all tokens)",
            explainerVideo = "https://youtu.be/EbZv6-N8Xlk",
            validation = { value ->
                val topK = (value as? Int) ?: return@ParameterDefinition "Invalid number"
                if (topK < 0) "Top K must be 0 or above" else null
            }
        )
    )
    
    /**
     * 고급 샘플링 파라미터들 (접기/펼치기 섹션)
     */
    val ADVANCED_SAMPLING_PARAMETERS = listOf(
        ParameterDefinition(
            key = "frequency_penalty",
            displayName = "Frequency Penalty",
            description = "Controls repetition based on frequency in input. Higher = less repetition of frequent tokens. Negative = encourage reuse. Scales with occurrences.",
            type = ParameterType.FLOAT_SLIDER,
            defaultValue = 0.0,
            range = "-2.0 - 2.0",
            explainerVideo = "https://youtu.be/p4gl6fqI0_w",
            validation = { value ->
                val penalty = (value as? Double) ?: return@ParameterDefinition "Invalid number"
                if (penalty < -2.0 || penalty > 2.0) "Frequency penalty must be between -2.0 and 2.0" else null
            }
        ),
        ParameterDefinition(
            key = "presence_penalty",
            displayName = "Presence Penalty",
            description = "Adjusts repetition of tokens already used. Higher = less repetition. Doesn't scale with occurrences. Negative = encourage reuse.",
            type = ParameterType.FLOAT_SLIDER,
            defaultValue = 0.0,
            range = "-2.0 - 2.0",
            explainerVideo = "https://youtu.be/MwHG5HL-P74",
            validation = { value ->
                val penalty = (value as? Double) ?: return@ParameterDefinition "Invalid number"
                if (penalty < -2.0 || penalty > 2.0) "Presence penalty must be between -2.0 and 2.0" else null
            }
        ),
        ParameterDefinition(
            key = "repetition_penalty",
            displayName = "Repetition Penalty",
            description = "Reduces repetition from input. Higher = less repetition, but too high can reduce coherence. Scales based on original token probability.",
            type = ParameterType.FLOAT_SLIDER,
            defaultValue = 1.0,
            range = "0.0 - 2.0",
            explainerVideo = "https://youtu.be/LHjGAnLm3DM",
            validation = { value ->
                val penalty = (value as? Double) ?: return@ParameterDefinition "Invalid number"
                if (penalty < 0.0 || penalty > 2.0) "Repetition penalty must be between 0.0 and 2.0" else null
            }
        ),
        ParameterDefinition(
            key = "min_p",
            displayName = "Min P",
            description = "Minimum probability for a token relative to most likely token. If set to 0.1, only allows tokens at least 1/10th as probable as best option.",
            type = ParameterType.FLOAT_SLIDER,
            defaultValue = 0.0,
            range = "0.0 - 1.0",
            validation = { value ->
                val minP = (value as? Double) ?: return@ParameterDefinition "Invalid number"
                if (minP < 0.0 || minP > 1.0) "Min P must be between 0.0 and 1.0" else null
            }
        ),
        ParameterDefinition(
            key = "top_a",
            displayName = "Top A",
            description = "Consider only tokens with 'sufficiently high' probabilities based on most likely token. Like dynamic Top-P with narrower scope.",
            type = ParameterType.FLOAT_SLIDER,
            defaultValue = 0.0,
            range = "0.0 - 1.0",
            validation = { value ->
                val topA = (value as? Double) ?: return@ParameterDefinition "Invalid number"
                if (topA < 0.0 || topA > 1.0) "Top A must be between 0.0 and 1.0" else null
            }
        ),
        ParameterDefinition(
            key = "seed",
            displayName = "Seed",
            description = "Makes inference deterministic. Same seed + parameters should return same result. Not guaranteed for all models.",
            type = ParameterType.INTEGER_SPINNER,
            defaultValue = null,
            hint = "Leave empty for random, or enter integer for reproducible results",
            validation = { value ->
                // 정수면 OK, null이면 OK, 그 외는 에러
                if (value != null && value !is Int) "Seed must be an integer" else null
            }
        )
    )
    
    /**
     * 출력 제어 파라미터들 (접기/펼치기 섹션)
     */
    val OUTPUT_CONTROL_PARAMETERS = listOf(
        ParameterDefinition(
            key = "stop",
            displayName = "Stop Sequences",
            description = "Stop generation immediately if model encounters any of these tokens.",
            type = ParameterType.TAG_INPUT,
            defaultValue = emptyList<String>(),
            hint = "Examples: \"\\n\", \".\", \"###\", \"Human:\", \"Assistant:\""
        ),
        ParameterDefinition(
            key = "response_format",
            displayName = "Response Format",
            description = "Forces model to produce specific output format. JSON mode guarantees valid JSON output. Remember to instruct model in your prompt too.",
            type = ParameterType.COMBO_BOX,
            defaultValue = null,
            options = listOf("Auto (Default)", "JSON Object", "Custom JSON Schema"),
            hint = "When using JSON mode, also instruct the model via prompt"
        ),
        ParameterDefinition(
            key = "logprobs",
            displayName = "Log Probabilities",
            description = "Whether to return log probabilities of output tokens. Useful for analysis and debugging.",
            type = ParameterType.BOOLEAN_CHECKBOX,
            defaultValue = false
        ),
        ParameterDefinition(
            key = "top_logprobs",
            displayName = "Top Log Probabilities",
            description = "Number of most likely tokens to return at each position with log probabilities. Only works when Log Probabilities is enabled.",
            type = ParameterType.INTEGER_SPINNER,
            defaultValue = null,
            range = "0 - 20",
            hint = "Requires Log Probabilities to be enabled",
            validation = { value ->
                val topLogprobs = value as? Int
                if (topLogprobs != null && (topLogprobs < 0 || topLogprobs > 20)) {
                    "Top log probabilities must be between 0 and 20"
                } else null
            }
        ),
        ParameterDefinition(
            key = "structured_outputs",
            displayName = "Structured Outputs",
            description = "Enable structured outputs using response_format json_schema (if model supports it).",
            type = ParameterType.BOOLEAN_CHECKBOX,
            defaultValue = false
        )
    )
    
    /**
     * 고급 옵션 파라미터들 (접기/펼치기 섹션)
     */
    val ADVANCED_OPTIONS_PARAMETERS = listOf(
        ParameterDefinition(
            key = "logit_bias",
            displayName = "Logit Bias",
            description = "Maps token IDs to bias values (-100 to 100). Added to logits before sampling. Values -1 to 1 adjust likelihood, -100/100 ban/force tokens.",
            type = ParameterType.JSON_TEXTAREA,
            defaultValue = emptyMap<String, Double>(),
            hint = "Example: {\"50256\": -100, \"1234\": 50}",
            validation = { value ->
                try {
                    val map = value as? Map<*, *> ?: return@ParameterDefinition null
                    map.values.forEach { bias ->
                        val biasValue = (bias as? Number)?.toDouble() ?: return@ParameterDefinition "All values must be numbers"
                        if (biasValue < -100 || biasValue > 100) return@ParameterDefinition "Bias values must be between -100 and 100"
                    }
                    null
                } catch (e: Exception) {
                    "Invalid JSON format"
                }
            }
        ),
        ParameterDefinition(
            key = "tools",
            displayName = "Tools",
            description = "Tool calling parameter array, following OpenAI's tool calling format. Transformed for non-OpenAI providers automatically.",
            type = ParameterType.JSON_TEXTAREA,
            defaultValue = emptyList<Map<String, Any>>(),
            hint = "Example: [{\"type\": \"function\", \"function\": {\"name\": \"get_weather\"}}]"
        ),
        ParameterDefinition(
            key = "tool_choice",
            displayName = "Tool Choice",
            description = "Controls which tool is called. 'none' = no tools, 'auto' = model decides, 'required' = must use tool, or specify particular function.",
            type = ParameterType.COMBO_BOX,
            defaultValue = null,
            options = listOf("Auto", "None", "Required", "Custom Function"),
            hint = "Custom Function allows specifying exact tool to use"
        )
    )
    
    /**
     * 모든 파라미터 정의를 하나의 리스트로 합침
     */
    val ALL_PARAMETERS = CORE_PARAMETERS + ADVANCED_SAMPLING_PARAMETERS + OUTPUT_CONTROL_PARAMETERS + ADVANCED_OPTIONS_PARAMETERS
    
    /**
     * 키로 파라미터 정의 찾기
     */
    fun getParameterDefinition(key: String): ParameterDefinition? {
        return ALL_PARAMETERS.find { it.key == key }
    }
    
    /**
     * 카테고리별 파라미터 그룹 정보
     */
    data class ParameterGroup(
        val title: String,
        val icon: String,
        val description: String,
        val parameters: List<ParameterDefinition>,
        val collapsible: Boolean = false
    )
    
    /**
     * UI에서 사용할 파라미터 그룹들
     */
    val PARAMETER_GROUPS = listOf(
        ParameterGroup(
            title = "Core Generation",
            icon = "🎯",
            description = "Essential parameters for controlling model behavior",
            parameters = CORE_PARAMETERS,
            collapsible = false
        ),
        ParameterGroup(
            title = "Advanced Sampling",
            icon = "🔧",
            description = "Fine-tune token selection and repetition control",
            parameters = ADVANCED_SAMPLING_PARAMETERS,
            collapsible = true
        ),
        ParameterGroup(
            title = "Output Control",
            icon = "📋",
            description = "Control output format and debugging options",
            parameters = OUTPUT_CONTROL_PARAMETERS,
            collapsible = true
        ),
        ParameterGroup(
            title = "Advanced Options",
            icon = "⚙️",
            description = "Expert-level controls and tool integration",
            parameters = ADVANCED_OPTIONS_PARAMETERS,
            collapsible = true
        )
    )
}
