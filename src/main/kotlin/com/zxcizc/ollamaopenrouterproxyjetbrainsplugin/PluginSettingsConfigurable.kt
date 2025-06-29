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
                mySettingsComponent.selectedModels != settings.selectedModels
    }

    override fun apply() {
        val settings = PluginSettingsState.getInstance()
        settings.openRouterApiKey = mySettingsComponent.openRouterApiKey
        settings.ollamaBaseUrl = mySettingsComponent.ollamaBaseUrl
        settings.enableDebugLogging = mySettingsComponent.enableDebugLogging
        settings.isProxyEnabled = mySettingsComponent.isProxyEnabled
        settings.selectedModels = mySettingsComponent.selectedModels.toMutableSet()
        
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
    }

    override fun disposeUIResources() {
        if (::mySettingsComponent.isInitialized) {
            mySettingsComponent.dispose()
        }
    }
}