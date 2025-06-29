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

    // 리스너 인터페이스
    interface SettingsChangeListener {
        fun onProxyEnabledChanged(enabled: Boolean)
        fun onSettingsChanged() // 새로운 메서드 추가
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
