package com.zxcizc.ollamaopenrouterproxyjetbrainsplugin

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.JBColor
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import java.awt.Color
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JCheckBox
import javax.swing.JTextField

class ProxyControlToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val proxyControlPanel = ProxyControlPanel()
        val content = ContentFactory.getInstance().createContent(proxyControlPanel.component, "", false)
        toolWindow.contentManager.addContent(content)
    }

    private class ProxyControlPanel : PluginSettingsState.SettingsChangeListener, Disposable {
        private val settings = PluginSettingsState.getInstance()
        private lateinit var proxyEnabledCheckBox: JCheckBox
        private lateinit var statusLabel: JLabel
        
        val component: JComponent

        init {
            // 설정 변경 리스너 등록
            settings.addListener(this)
            
            component = panel {
                group("Proxy Control") {
                    row {
                        proxyEnabledCheckBox = checkBox("Enable OpenRouter Proxy")
                            .applyToComponent {
                                isSelected = settings.isProxyEnabled
                                addActionListener { 
                                    settings.isProxyEnabled = isSelected
                                    updateStatus()
                                }
                            }
                            .comment("Toggle hybrid mode: local Ollama + OpenRouter models vs local only")
                            .component
                    }
                    
                    row("Status:") {
                        statusLabel = label(getStatusText())
                            .applyToComponent {
                                // 상태에 따라 색상 변경
                                foreground = if (settings.isProxyEnabled) {
                                    JBColor(Color.BLUE, Color(135, 206, 235)) // 라이트: 파랑, 다크: 스카이블루
                                } else {
                                    JBColor.GRAY
                                }
                            }
                            .component
                    }
                    
                    row {
                        comment("💡 Access Settings and Information via Tools menu")
                            .applyToComponent {
                                foreground = JBColor(Color(0, 102, 204), Color(135, 206, 235))
                            }
                    }
                }
            }
            
            // 초기 상태 업데이트
            updateStatus()
        }

        private fun getStatusText(): String {
            return try {
                val proxyServer = getProxyServerInstance()
                proxyServer.getDetailedStatus()
            } catch (e: Exception) {
                "Status: Server Starting..."
            }
        }
        
        private fun getProxyServerInstance(): ProxyServer {
            return PluginStartupActivity.getProxyServerInstance()
        }
        
        private fun getApiKeyStatusText(): String {
            val apiKey = settings.openRouterApiKey
            return when {
                apiKey.isBlank() -> "Not configured"
                apiKey.length < 10 -> "Invalid format"
                else -> {
                    // 간단한 검증 (실시간으로는 부담스러우므로 기본적인 체크만)
                    val keyData = ProxyServer.validateApiKey(apiKey)
                    if (keyData != null) {
                        "Valid (${keyData.label ?: "Unknown"})"
                    } else {
                        "Check settings for details"
                    }
                }
            }
        }
        
        private fun getApiKeyStatusColor(): Color {
            val apiKey = settings.openRouterApiKey
            return when {
                apiKey.isBlank() -> JBColor.GRAY
                apiKey.length < 10 -> JBColor.RED
                else -> JBColor(Color(0, 128, 0), Color(144, 238, 144)) // 라이트: 녹색, 다크: 라이트그린
            }
        }

        private fun updateStatus() {
            ApplicationManager.getApplication().invokeLater {
                val newStatusText = getStatusText()
                statusLabel.text = newStatusText
                
                // 상태에 따라 색상 변경 (Stopped 제거)
                statusLabel.foreground = when {
                    newStatusText.contains("Bypass") -> JBColor.GRAY
                    newStatusText.contains("API Key Required") -> JBColor.ORANGE
                    newStatusText.contains("Proxy") -> JBColor(Color.BLUE, Color(135, 206, 235)) // 라이트: 파랑, 다크: 스카이블루
                    else -> JBColor.foreground()
                }
                
                // 체크박스 상태 동기화 (다른 곳에서 변경된 경우)
                if (proxyEnabledCheckBox.isSelected != settings.isProxyEnabled) {
                    proxyEnabledCheckBox.isSelected = settings.isProxyEnabled
                }
            }
        }

        // SettingsChangeListener 구현
        override fun onProxyEnabledChanged(enabled: Boolean) {
            updateStatus()
        }
        
        override fun onSettingsChanged() {
            updateStatus()
        }

        // Disposable 구현
        override fun dispose() {
            settings.removeListener(this)
        }
    }
}
