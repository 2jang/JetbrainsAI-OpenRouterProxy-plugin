package com.zxcizc.ollamaopenrouterproxyjetbrainsplugin

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.Messages
import org.jetbrains.annotations.Nls
import javax.swing.JComponent

class PluginSettingsConfigurable : Configurable {

    private lateinit var mySettingsComponent: PluginSettingsComponent
    private val log: Logger = Logger.getInstance(PluginSettingsConfigurable::class.java)

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
                mySettingsComponent.proxyPort != settings.proxyPort ||
                mySettingsComponent.enableDebugLogging != settings.enableDebugLogging ||
                mySettingsComponent.isProxyEnabled != settings.isProxyEnabled ||
                mySettingsComponent.selectedModels != settings.selectedModels
    }

    override fun apply() {
        val settings = PluginSettingsState.getInstance()
        val oldPort = settings.proxyPort
        val oldBaseUrl = settings.ollamaBaseUrl

        // --- 로깅을 위해 이전 상태 저장 ---
        val changedItems = mutableListOf<String>()
        if (mySettingsComponent.openRouterApiKey != settings.openRouterApiKey) changedItems.add("API Key")
        if (mySettingsComponent.ollamaBaseUrl != settings.ollamaBaseUrl) changedItems.add("Ollama URL changed to '${mySettingsComponent.ollamaBaseUrl}'")
        if (mySettingsComponent.proxyPort != settings.proxyPort) changedItems.add("Proxy Port changed to ${mySettingsComponent.proxyPort}")
        if (mySettingsComponent.enableDebugLogging != settings.enableDebugLogging) changedItems.add("Debug Logging ${if (mySettingsComponent.enableDebugLogging) "enabled" else "disabled"}")
        if (mySettingsComponent.isProxyEnabled != settings.isProxyEnabled) changedItems.add("Proxy ${if (mySettingsComponent.isProxyEnabled) "enabled" else "disabled"}")
        if (mySettingsComponent.selectedModels != settings.selectedModels) changedItems.add("Model Whitelist updated (${mySettingsComponent.selectedModels.size} models)")

        // --- 설정 적용 ---
        settings.openRouterApiKey = mySettingsComponent.openRouterApiKey
        settings.ollamaBaseUrl = mySettingsComponent.ollamaBaseUrl
        settings.proxyPort = mySettingsComponent.proxyPort
        settings.enableDebugLogging = mySettingsComponent.enableDebugLogging
        settings.isProxyEnabled = mySettingsComponent.isProxyEnabled
        settings.selectedModels = mySettingsComponent.selectedModels.toMutableSet()

        // --- 로그 기록 ---
        if (changedItems.isNotEmpty()) {
            log.info("Plugin settings updated: ${changedItems.joinToString(", ")}")
        }

        // --- 기능적 처리 ---
        if (oldPort != settings.proxyPort || oldBaseUrl != settings.ollamaBaseUrl) {
            PluginStartupActivity.getProxyServerInstance().restart()
            Messages.showInfoMessage(
                "Proxy server port or Ollama URL has been changed. The server is restarting.",
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
        mySettingsComponent.proxyPort = settings.proxyPort
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