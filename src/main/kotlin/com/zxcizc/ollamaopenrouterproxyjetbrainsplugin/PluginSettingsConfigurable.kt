package com.zxcizc.ollamaopenrouterproxyjetbrainsplugin

import com.intellij.openapi.options.Configurable
import org.jetbrains.annotations.Nls
import javax.swing.JComponent

class PluginSettingsConfigurable : Configurable {

    // --- 개선: lateinit으로 불필요한 nullable 체크 제거 ---
    private lateinit var mySettingsComponent: PluginSettingsComponent

    @Nls(capitalization = Nls.Capitalization.Title)
    override fun getDisplayName(): String {
        return "Ollama OpenRouter Proxy"
    }

    override fun getPreferredFocusedComponent(): JComponent {
        return mySettingsComponent.preferredFocusedComponent
    }

    override fun createComponent(): JComponent {
        mySettingsComponent = PluginSettingsComponent()
        return mySettingsComponent.panel
    }

    override fun isModified(): Boolean {
        val settings = PluginSettingsState.getInstance()
        return mySettingsComponent.openRouterApiKey != settings.openRouterApiKey ||
                mySettingsComponent.enableDebugLogging != settings.enableDebugLogging
    }

    override fun apply() {
        val settings = PluginSettingsState.getInstance()
        settings.openRouterApiKey = mySettingsComponent.openRouterApiKey
        settings.enableDebugLogging = mySettingsComponent.enableDebugLogging
    }

    override fun reset() {
        val settings = PluginSettingsState.getInstance()
        mySettingsComponent.openRouterApiKey = settings.openRouterApiKey
        mySettingsComponent.enableDebugLogging = settings.enableDebugLogging
    }

    override fun disposeUIResources() {
        // lateinit 변수는 null로 만들 수 없으므로, UI 리소스 해제가 필요하다면
        // mySettingsComponent 내부에 별도의 dispose 메서드를 만들어 호출해야 합니다.
        // 현재는 특별한 해제 로직이 필요하지 않습니다.
    }
}