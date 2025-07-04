package com.zxcizc.ollamaopenrouterproxyjetbrainsplugin

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.Messages
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
                mySettingsComponent.proxyPort != settings.proxyPort || // <<< 포트 변경 감지
                mySettingsComponent.enableDebugLogging != settings.enableDebugLogging ||
                mySettingsComponent.isProxyEnabled != settings.isProxyEnabled ||
                mySettingsComponent.selectedModels != settings.selectedModels
    }

    override fun apply() {
        val settings = PluginSettingsState.getInstance()
        val oldPort = settings.proxyPort

        settings.openRouterApiKey = mySettingsComponent.openRouterApiKey
        settings.ollamaBaseUrl = mySettingsComponent.ollamaBaseUrl
        settings.proxyPort = mySettingsComponent.proxyPort // <<< 포트 설정 저장
        settings.enableDebugLogging = mySettingsComponent.enableDebugLogging
        settings.isProxyEnabled = mySettingsComponent.isProxyEnabled
        settings.selectedModels = mySettingsComponent.selectedModels.toMutableSet()

        if (oldPort != settings.proxyPort) {
            PluginStartupActivity.getProxyServerInstance().restart()
            Messages.showInfoMessage(
                "Proxy server port has been changed to ${settings.proxyPort}. The server is restarting.",
                "Server Restarted"
            )
        }

        ProxyServer.invalidateModelsCache()
        settings.notifySettingsChanged()
    }

    override fun reset() {
        val settings = PluginSettingsState.getInstance()
        mySettingsComponent.openRouterApiKey = settings.openRouterApiKey
        mySettingsComponent.ollamaBaseUrl = settings.ollamaBaseUrl
        mySettingsComponent.proxyPort = settings.proxyPort // <<< 포트 설정 리셋
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