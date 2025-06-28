package com.zxcizc.ollamaopenrouterproxyjetbrainsplugin

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

@State(
    name = "com.zxcizc.ollamaopenrouterproxyjetbrainsplugin.PluginSettingsState",
    storages = [Storage("OllamaOpenRouterProxySettings.xml")]
)
class PluginSettingsState : PersistentStateComponent<PluginSettingsState> {

    var openRouterApiKey: String = ""
    var enableDebugLogging: Boolean = false // --- 이 줄을 추가합니다 ---

    override fun getState(): PluginSettingsState {
        return this
    }

    override fun loadState(state: PluginSettingsState) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        @JvmStatic
        fun getInstance(): PluginSettingsState {
            return ApplicationManager.getApplication().getService(PluginSettingsState::class.java)
        }
    }
}