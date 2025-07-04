package com.zxcizc.ollamaopenrouterproxyjetbrainsplugin

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil
import com.intellij.util.xmlb.annotations.Transient
import java.util.concurrent.CopyOnWriteArrayList

@State(
    name = "com.zxcizc.ollamaopenrouterproxyjetbrainsplugin.PluginSettingsState",
    storages = [Storage("OllamaOpenRouterProxySettings.xml")]
)
class PluginSettingsState : PersistentStateComponent<PluginSettingsState> {

    var openRouterApiKey: String = ""
    var enableDebugLogging: Boolean = false
    var ollamaBaseUrl: String = "http://localhost:11434"
    var proxyPort: Int = 11444
    var selectedModels: MutableSet<String> = mutableSetOf()

    private var _isProxyEnabled: Boolean = true

    var overrideParameters: Boolean = false
    var activeParameters: ParameterPreset = ParameterPreset()
    var savedPresets: MutableMap<String, ParameterPreset> = mutableMapOf()

    @Transient
    private val listeners = CopyOnWriteArrayList<SettingsChangeListener>()

    var isProxyEnabled: Boolean
        get() = _isProxyEnabled
        set(value) {
            if (_isProxyEnabled != value) {
                _isProxyEnabled = value
                notifyListeners()
                try {
                    ProxyServer.invalidateModelsCache()
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }

    override fun getState(): PluginSettingsState = this

    override fun loadState(state: PluginSettingsState) {
        XmlSerializerUtil.copyBean(state, this)
    }

    override fun initializeComponent() {
        if (savedPresets.isEmpty()) {
            // *** FIX: Only "Default" preset remains ***
            savedPresets["Default"] = ParameterPreset()
        }
    }

    interface SettingsChangeListener {
        fun onProxyEnabledChanged(enabled: Boolean)
        fun onSettingsChanged()
    }

    fun addListener(listener: SettingsChangeListener) { listeners.add(listener) }
    fun removeListener(listener: SettingsChangeListener) { listeners.remove(listener) }
    private fun notifyListeners() { listeners.forEach { it.onProxyEnabledChanged(_isProxyEnabled); it.onSettingsChanged() } }
    fun notifySettingsChanged() { listeners.forEach { it.onSettingsChanged() } }

    companion object {
        @JvmStatic
        fun getInstance(): PluginSettingsState {
            return ApplicationManager.getApplication().getService(PluginSettingsState::class.java)
        }
    }
}