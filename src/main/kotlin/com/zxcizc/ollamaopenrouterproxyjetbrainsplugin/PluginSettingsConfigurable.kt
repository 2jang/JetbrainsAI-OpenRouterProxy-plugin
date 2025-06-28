package com.zxcizc.ollamaopenrouterproxyjetbrainsplugin

import com.intellij.openapi.options.Configurable
import org.jetbrains.annotations.Nls
import javax.swing.JComponent

class PluginSettingsConfigurable : Configurable {

    private var mySettingsComponent: PluginSettingsComponent? = null

    @Nls(capitalization = Nls.Capitalization.Title)
    override fun getDisplayName(): String {
        return "Ollama OpenRouter Proxy"
    }

    override fun getPreferredFocusedComponent(): JComponent? {
        return mySettingsComponent?.preferredFocusedComponent
    }

    override fun createComponent(): JComponent? {
        mySettingsComponent = PluginSettingsComponent()
        return mySettingsComponent?.panel
    }

    override fun isModified(): Boolean {
        val settings = PluginSettingsState.getInstance()
        return mySettingsComponent?.openRouterApiKey != settings.openRouterApiKey ||
                mySettingsComponent?.enableDebugLogging != settings.enableDebugLogging // --- 이 줄을 추가합니다 ---
    }

    override fun apply() {
        val settings = PluginSettingsState.getInstance()
        settings.openRouterApiKey = mySettingsComponent?.openRouterApiKey ?: ""
        settings.enableDebugLogging = mySettingsComponent?.enableDebugLogging ?: false // --- 이 줄을 추가합니다 ---
    }

    override fun reset() {
        val settings = PluginSettingsState.getInstance()
        mySettingsComponent?.openRouterApiKey = settings.openRouterApiKey
        mySettingsComponent?.enableDebugLogging = settings.enableDebugLogging // --- 이 줄을 추가합니다 ---
    }

    override fun disposeUIResources() {
        mySettingsComponent = null
    }
}