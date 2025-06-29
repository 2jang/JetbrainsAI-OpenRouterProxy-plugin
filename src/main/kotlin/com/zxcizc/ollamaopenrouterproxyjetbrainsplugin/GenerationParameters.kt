package com.zxcizc.ollamaopenrouterproxyjetbrainsplugin

/**
 * 생성 파라미터를 담는 데이터 클래스
 * 기본값이 있는 파라미터들은 기본값과 같으면 JSON에 포함하지 않음
 * null이나 empty인 파라미터들도 JSON에 포함하지 않음
 */
data class GenerationParameters(
    // === 기본값이 있는 파라미터들 (기본값이면 JSON에서 생략) ===
    
    /**
     * 응답의 다양성을 조절. 낮은 값 = 예측 가능, 높은 값 = 다양함
     * 0에서는 동일한 입력에 항상 같은 응답
     */
    var temperature: Double = 1.0,
    
    /**
     * 모델의 토큰 선택을 확률 상위 P%로 제한. 동적 Top-K와 유사
     */
    var topP: Double = 1.0,
    
    /**
     * 각 단계에서 모델의 토큰 선택을 제한. 0 = 비활성화
     */
    var topK: Int = 0,
    
    /**
     * 입력에서 자주 나타나는 토큰의 반복을 제어. 발생 횟수에 비례
     */
    var frequencyPenalty: Double = 0.0,
    
    /**
     * 이미 사용된 특정 토큰의 반복을 조절. 발생 횟수와 무관
     */
    var presencePenalty: Double = 0.0,
    
    /**
     * 입력에서 토큰 반복을 줄임. 원래 토큰 확률 기반으로 패널티 적용
     */
    var repetitionPenalty: Double = 1.0,
    
    /**
     * 가장 가능성이 높은 토큰에 상대적인 최소 확률
     */
    var minP: Double = 0.0,
    
    /**
     * 최고 확률 토큰 기반으로 "충분히 높은" 확률을 가진 토큰만 고려
     */
    var topA: Double = 0.0,
    
    /**
     * 출력 토큰의 로그 확률 반환 여부
     */
    var logprobs: Boolean = false,
    
    /**
     * JSON 스키마를 사용한 구조화된 출력 지원 여부
     */
    var structuredOutputs: Boolean = false,
    
    // === 기본값이 없는 파라미터들 (null/empty면 JSON에서 생략) ===
    
    /**
     * 모델이 생성할 수 있는 토큰의 상한. null이면 무제한(컨텍스트 길이까지)
     */
    var maxTokens: Int? = null,
    
    /**
     * 결정론적 추론을 위한 시드. 같은 시드+파라미터면 같은 결과
     */
    var seed: Int? = null,
    
    /**
     * 생성을 즉시 중단할 토큰들
     */
    var stop: List<String> = emptyList(),
    
    /**
     * 특정 출력 형식 강제. "json_object" 등
     */
    var responseFormat: String? = null,
    
    /**
     * 각 토큰 위치에서 반환할 상위 토큰 수 (0-20)
     * logprobs가 true일 때만 의미 있음
     */
    var topLogprobs: Int? = null,
    
    /**
     * 토큰 ID를 바이어스 값(-100~100)에 매핑. 샘플링 전 로짓에 추가
     */
    var logitBias: Map<String, Double> = emptyMap(),
    
    /**
     * 도구 호출 파라미터 배열
     */
    var tools: List<Map<String, Any>> = emptyList(),
    
    /**
     * 모델이 호출할 도구 제어. "none", "auto", "required" 또는 특정 함수
     */
    var toolChoice: String? = null
) {
    
    /**
     * JSON 전송용 맵으로 변환
     * 기본값과 같거나 null/empty인 값들은 제외
     */
    fun toJsonMap(): Map<String, Any> {
        val params = mutableMapOf<String, Any>()
        
        // 기본값이 아닌 경우에만 추가
        if (temperature != 1.0) params["temperature"] = temperature
        if (topP != 1.0) params["top_p"] = topP
        if (topK != 0) params["top_k"] = topK
        if (frequencyPenalty != 0.0) params["frequency_penalty"] = frequencyPenalty
        if (presencePenalty != 0.0) params["presence_penalty"] = presencePenalty
        if (repetitionPenalty != 1.0) params["repetition_penalty"] = repetitionPenalty
        if (minP != 0.0) params["min_p"] = minP
        if (topA != 0.0) params["top_a"] = topA
        if (logprobs) params["logprobs"] = logprobs
        if (structuredOutputs) params["structured_outputs"] = structuredOutputs
        
        // null이 아니거나 비어있지 않은 경우에만 추가
        maxTokens?.let { params["max_tokens"] = it }
        seed?.let { params["seed"] = it }
        if (stop.isNotEmpty()) params["stop"] = stop
        responseFormat?.let { 
            params["response_format"] = mapOf("type" to it)
        }
        if (logprobs && topLogprobs != null && topLogprobs!! > 0) {
            params["top_logprobs"] = topLogprobs!!
        }
        if (logitBias.isNotEmpty()) params["logit_bias"] = logitBias
        if (tools.isNotEmpty()) params["tools"] = tools
        toolChoice?.let { params["tool_choice"] = it }
        
        return params
    }
    
    /**
     * 기본값이 아닌 파라미터가 있는지 확인
     */
    fun hasNonDefaultParameters(): Boolean {
        return toJsonMap().isNotEmpty()
    }
    
    /**
     * 모든 파라미터를 기본값으로 리셋
     */
    fun resetToDefaults() {
        temperature = 1.0
        topP = 1.0
        topK = 0
        frequencyPenalty = 0.0
        presencePenalty = 0.0
        repetitionPenalty = 1.0
        minP = 0.0
        topA = 0.0
        logprobs = false
        structuredOutputs = false
        maxTokens = null
        seed = null
        stop = emptyList()
        responseFormat = null
        topLogprobs = null
        logitBias = emptyMap()
        tools = emptyList()
        toolChoice = null
    }
    
    /**
     * 다른 GenerationParameters 객체의 값을 복사
     */
    fun copyFrom(other: GenerationParameters) {
        temperature = other.temperature
        topP = other.topP
        topK = other.topK
        frequencyPenalty = other.frequencyPenalty
        presencePenalty = other.presencePenalty
        repetitionPenalty = other.repetitionPenalty
        minP = other.minP
        topA = other.topA
        logprobs = other.logprobs
        structuredOutputs = other.structuredOutputs
        maxTokens = other.maxTokens
        seed = other.seed
        stop = other.stop.toList()
        responseFormat = other.responseFormat
        topLogprobs = other.topLogprobs
        logitBias = other.logitBias.toMap()
        tools = other.tools.toList()
        toolChoice = other.toolChoice
    }
}
