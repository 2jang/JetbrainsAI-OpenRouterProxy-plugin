package com.zxcizc.ollamaopenrouterproxyjetbrainsplugin

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.JBColor
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import java.awt.Color
import javax.swing.*
import javax.swing.event.ChangeEvent
import javax.swing.event.ChangeListener

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
        private lateinit var useCustomParametersCheckBox: JCheckBox
        
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
                            .comment("Toggle between OpenRouter proxy and direct Ollama connection")
                            .component
                    }
                    
                    row("Status:") {
                        statusLabel = label(getStatusText())
                            .applyToComponent {
                                foreground = if (settings.isProxyEnabled) {
                                    JBColor(Color.BLUE, Color(135, 206, 235))
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
                
                group("Parameters") {
                    row {
                        useCustomParametersCheckBox = checkBox("Use Custom Parameters")
                            .applyToComponent {
                                isSelected = settings.useCustomParameters
                                addActionListener {
                                    settings.useCustomParameters = isSelected
                                }
                            }
                            .comment("Override default OpenRouter parameters")
                            .component
                    }
                    
                    row {
                        comment("Temperature: ${String.format("%.2f", settings.temperature)}")
                    }
                    
                    row {
                        comment("Top P: ${String.format("%.2f", settings.topP)}")
                    }
                    
                    row {
                        comment("Top K: ${settings.topK}")
                    }
                    
                    row {
                        comment("Max Tokens: ${settings.maxTokens}")
                    }
                    
                    row {
                        button("Configure Parameters") {
                            // Settings 페이지에서 설정하도록 안내
                            ShowSettingsUtil.getInstance().showSettingsDialog(null, "Ollama OpenRouter Proxy")
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

        private fun updateStatus() {
            ApplicationManager.getApplication().invokeLater {
                val newStatusText = getStatusText()
                statusLabel.text = newStatusText
                
                // 상태에 따라 색상 변경
                statusLabel.foreground = when {
                    newStatusText.contains("Bypass") -> JBColor.GRAY
                    newStatusText.contains("API Key Required") -> JBColor.ORANGE
                    newStatusText.contains("Proxy") -> JBColor(Color.BLUE, Color(135, 206, 235))
                    else -> JBColor.foreground()
                }
                
                // 체크박스 상태 동기화
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
