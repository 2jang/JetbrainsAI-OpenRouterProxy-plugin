package com.zxcizc.ollamaopenrouterproxyjetbrainsplugin

import com.intellij.openapi.options.Configurable
import org.jetbrains.annotations.Nls
import javax.swing.JComponent

class PluginSettingsConfigurable : Configurable {

    private lateinit var mySettingsComponent: PluginSettingsComponent

    @Nls(capitalization = Nls.Capitalization.Title)
    override fun getDisplayName(): String = "Ollama OpenRouter Proxy"

    override fun getPreferredFocusedComponent(): JComponent = mySettingsComponent.preferredFocusedComponent

    override fun createComponent(): JComponent {
        mySettingsComponent = PluginSettingsComponent()
        return mySettingsComponent.panel
    }

    override fun isModified(): Boolean {
        val settings = PluginSettingsState.getInstance()
        return mySettingsComponent.openRouterApiKey != settings.openRouterApiKey ||
                mySettingsComponent.ollamaBaseUrl != settings.ollamaBaseUrl ||
                mySettingsComponent.enableDebugLogging != settings.enableDebugLogging ||
                mySettingsComponent.isProxyEnabled != settings.isProxyEnabled ||
                mySettingsComponent.selectedModels != settings.selectedModels ||
                mySettingsComponent.useCustomParameters != settings.useCustomParameters ||
                mySettingsComponent.temperature != settings.temperature ||
                mySettingsComponent.topP != settings.topP ||
                mySettingsComponent.topK != settings.topK ||
                mySettingsComponent.maxTokens != settings.maxTokens ||
                mySettingsComponent.frequencyPenalty != settings.frequencyPenalty ||
                mySettingsComponent.presencePenalty != settings.presencePenalty ||
                mySettingsComponent.repetitionPenalty != settings.repetitionPenalty ||
                mySettingsComponent.seed != settings.seed
    }

    override fun apply() {
        val settings = PluginSettingsState.getInstance()
        settings.openRouterApiKey = mySettingsComponent.openRouterApiKey
        settings.ollamaBaseUrl = mySettingsComponent.ollamaBaseUrl
        settings.enableDebugLogging = mySettingsComponent.enableDebugLogging
        settings.isProxyEnabled = mySettingsComponent.isProxyEnabled
        settings.selectedModels = mySettingsComponent.selectedModels.toMutableSet()
        settings.useCustomParameters = mySettingsComponent.useCustomParameters
        settings.temperature = mySettingsComponent.temperature
        settings.topP = mySettingsComponent.topP
        settings.topK = mySettingsComponent.topK
        settings.maxTokens = mySettingsComponent.maxTokens
        settings.frequencyPenalty = mySettingsComponent.frequencyPenalty
        settings.presencePenalty = mySettingsComponent.presencePenalty
        settings.repetitionPenalty = mySettingsComponent.repetitionPenalty
        settings.seed = mySettingsComponent.seed
        
        // 설정 변경 시 모델 캐시 무효화 및 리스너 알림
        ProxyServer.invalidateModelsCache()
        settings.notifySettingsChanged()
    }

    override fun reset() {
        val settings = PluginSettingsState.getInstance()
        mySettingsComponent.openRouterApiKey = settings.openRouterApiKey
        mySettingsComponent.ollamaBaseUrl = settings.ollamaBaseUrl
        mySettingsComponent.enableDebugLogging = settings.enableDebugLogging
        mySettingsComponent.isProxyEnabled = settings.isProxyEnabled
        mySettingsComponent.selectedModels = settings.selectedModels
        mySettingsComponent.useCustomParameters = settings.useCustomParameters
        mySettingsComponent.temperature = settings.temperature
        mySettingsComponent.topP = settings.topP
        mySettingsComponent.topK = settings.topK
        mySettingsComponent.maxTokens = settings.maxTokens
        mySettingsComponent.frequencyPenalty = settings.frequencyPenalty
        mySettingsComponent.presencePenalty = settings.presencePenalty
        mySettingsComponent.repetitionPenalty = settings.repetitionPenalty
        mySettingsComponent.seed = settings.seed
    }

    override fun disposeUIResources() {
        if (::mySettingsComponent.isInitialized) {
            mySettingsComponent.dispose()
        }
    }
}