package com.zxcizc.ollamaopenrouterproxyjetbrainsplugin

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil
import java.util.concurrent.CopyOnWriteArrayList

@State(
    name = "com.zxcizc.ollamaopenrouterproxyjetbrainsplugin.PluginSettingsState",
    storages = [Storage("OllamaOpenRouterProxySettings.xml")]
)
class PluginSettingsState : PersistentStateComponent<PluginSettingsState> {

    var openRouterApiKey: String = ""
    var enableDebugLogging: Boolean = false
    var ollamaBaseUrl: String = "http://localhost:11434"
    var selectedModels: MutableSet<String> = mutableSetOf()
    
    // === 생성 파라미터 설정 ===
    
    /**
     * 생성 파라미터들. XML 직렬화를 위해 각 필드를 개별적으로 저장
     */
    // Core Parameters
    var paramTemperature: Double = 1.0
    var paramMaxTokens: Int? = null
    var paramTopP: Double = 1.0
    var paramTopK: Int = 0
    
    // Advanced Sampling Parameters
    var paramFrequencyPenalty: Double = 0.0
    var paramPresencePenalty: Double = 0.0
    var paramRepetitionPenalty: Double = 1.0
    var paramMinP: Double = 0.0
    var paramTopA: Double = 0.0
    var paramSeed: Int? = null
    
    // Output Control Parameters
    var paramStop: MutableList<String> = mutableListOf()
    var paramResponseFormat: String? = null
    var paramLogprobs: Boolean = false
    var paramTopLogprobs: Int? = null
    var paramStructuredOutputs: Boolean = false
    
    // Advanced Options Parameters  
    var paramLogitBias: MutableMap<String, Double> = mutableMapOf()
    var paramTools: MutableList<Map<String, Any>> = mutableListOf()
    var paramToolChoice: String? = null
    
    /**
     * 파라미터 기능 활성화 여부
     */
    var parametersEnabled: Boolean = false
    
    /**
     * 마지막으로 사용한 파라미터 프리셋 이름
     */
    var lastUsedPreset: String? = null
    
    // 프록시 활성화 상태를 위한 백킹 필드 (기본값: true)
    private var _isProxyEnabled: Boolean = true
    
    // 설정 변경 리스너 목록
    private val listeners = CopyOnWriteArrayList<SettingsChangeListener>()
    
    // 프록시 활성화 상태 프로퍼티 (리스너 호출)
    var isProxyEnabled: Boolean
        get() = _isProxyEnabled
        set(value) {
            if (_isProxyEnabled != value) {
                _isProxyEnabled = value
                notifyListeners()
                // 프록시 모드 변경 시 모델 캐시 무효화
                try {
                    ProxyServer.invalidateModelsCache()
                } catch (e: Exception) {
                    // 클래스 로딩 순서로 인해 예외가 발생할 수 있으므로 무시
                }
            }
        }

    override fun getState(): PluginSettingsState {
        return this
    }

    override fun loadState(state: PluginSettingsState) {
        XmlSerializerUtil.copyBean(state, this)
    }

    // === 파라미터 관련 메서드들 ===
    
    /**
     * 설정에서 GenerationParameters 객체로 변환
     */
    fun getGenerationParameters(): GenerationParameters {
        return GenerationParameters(
            temperature = paramTemperature,
            maxTokens = paramMaxTokens,
            topP = paramTopP,
            topK = paramTopK,
            frequencyPenalty = paramFrequencyPenalty,
            presencePenalty = paramPresencePenalty,
            repetitionPenalty = paramRepetitionPenalty,
            minP = paramMinP,
            topA = paramTopA,
            seed = paramSeed,
            stop = paramStop.toList(),
            responseFormat = paramResponseFormat,
            logprobs = paramLogprobs,
            topLogprobs = paramTopLogprobs,
            structuredOutputs = paramStructuredOutputs,
            logitBias = paramLogitBias.toMap(),
            tools = paramTools.toList(),
            toolChoice = paramToolChoice
        )
    }
    
    /**
     * GenerationParameters 객체를 설정에 저장
     */
    fun setGenerationParameters(params: GenerationParameters) {
        paramTemperature = params.temperature
        paramMaxTokens = params.maxTokens
        paramTopP = params.topP
        paramTopK = params.topK
        paramFrequencyPenalty = params.frequencyPenalty
        paramPresencePenalty = params.presencePenalty
        paramRepetitionPenalty = params.repetitionPenalty
        paramMinP = params.minP
        paramTopA = params.topA
        paramSeed = params.seed
        paramStop = params.stop.toMutableList()
        paramResponseFormat = params.responseFormat
        paramLogprobs = params.logprobs
        paramTopLogprobs = params.topLogprobs
        paramStructuredOutputs = params.structuredOutputs
        paramLogitBias = params.logitBias.toMutableMap()
        paramTools = params.tools.toMutableList()
        paramToolChoice = params.toolChoice
        
        // 변경 알림
        notifyParametersChanged()
    }
    
    /**
     * 파라미터를 기본값으로 리셋
     */
    fun resetParametersToDefaults() {
        setGenerationParameters(GenerationParameters())
        lastUsedPreset = null
    }
    
    /**
     * 프리셋 적용
     */
    fun applyPreset(presetName: String) {
        val preset = ParameterUtils.Presets.getAllPresets()[presetName]
        if (preset != null) {
            setGenerationParameters(preset)
            lastUsedPreset = presetName
        }
    }
    
    /**
     * 현재 파라미터가 기본값인지 확인
     */
    fun hasNonDefaultParameters(): Boolean {
        return getGenerationParameters().hasNonDefaultParameters()
    }
    
    /**
     * 파라미터 변경 알림
     */
    fun notifyParametersChanged() {
        listeners.forEach { it.onParametersChanged() }
        notifySettingsChanged()
        
        // 파라미터 변경 시 모델 캐시 무효화
        try {
            ProxyServer.invalidateModelsCache()
        } catch (e: Exception) {
            // 클래스 로딩 순서로 인해 예외가 발생할 수 있으므로 무시
        }
    }

    // 리스너 인터페이스
    interface SettingsChangeListener {
        fun onProxyEnabledChanged(enabled: Boolean)
        fun onSettingsChanged() // 기존 메서드
        fun onParametersChanged() // 파라미터 변경 알림
    }
    
    // 리스너 관리 메서드
    fun addListener(listener: SettingsChangeListener) {
        listeners.add(listener)
    }
    
    fun removeListener(listener: SettingsChangeListener) {
        listeners.remove(listener)
    }
    
    private fun notifyListeners() {
        listeners.forEach { 
            it.onProxyEnabledChanged(_isProxyEnabled)
            it.onSettingsChanged()
        }
    }
    
    // 설정 변경 알림
    fun notifySettingsChanged() {
        listeners.forEach { it.onSettingsChanged() }
    }

    companion object {
        @JvmStatic
        fun getInstance(): PluginSettingsState {
            return ApplicationManager.getApplication().getService(PluginSettingsState::class.java)
        }
    }
}
