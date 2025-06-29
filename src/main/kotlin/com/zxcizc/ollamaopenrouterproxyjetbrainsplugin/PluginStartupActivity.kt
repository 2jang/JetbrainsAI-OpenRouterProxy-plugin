package com.zxcizc.ollamaopenrouterproxyjetbrainsplugin

import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer

class PluginStartupActivity : ProjectActivity {

    companion object {
        private val proxyServer = ProxyServer()
        
        // ProxyServer 인스턴스에 안전하게 접근할 수 있는 메서드
        @JvmStatic
        fun getProxyServerInstance(): ProxyServer = proxyServer
    }

    override suspend fun execute(project: Project) {
        // 앱이 시작될 때 한 번만 서버를 시작하도록 보장
        if (ApplicationManager.getApplication().isUnitTestMode || ApplicationManager.getApplication().isHeadlessEnvironment) {
            return
        }

        proxyServer.start()

        // 초기 설정 안내 (처음 사용자에게만)
        showInitialSetupGuidance(project)

        // IDE 종료 시 서버를 중지하기 위한 훅
        val parentDisposable = Disposable {}
        Disposer.register(ApplicationManager.getApplication(), parentDisposable)
        Disposer.register(parentDisposable, Disposable {
            proxyServer.stop()
        })
    }
    
    private fun showInitialSetupGuidance(project: Project) {
        val settings = PluginSettingsState.getInstance()
        
        // 설정이 비어있으면 초기 사용자로 간주
        if (settings.openRouterApiKey.isBlank()) {
            val notification = Notification(
                "OllamaOpenRouterProxy",
                "🚀 Ollama OpenRouter Proxy Setup",
                """
                <html>
                <b>Setup Required:</b><br>
                1. Go to <b>Tools → AI Assistant → Models</b><br>
                2. Set Ollama URL to: <b>http://localhost:11444</b><br>
                3. Configure your OpenRouter API key in plugin settings<br>
                <br>
                <i>The proxy server is now running on port 11444</i>
                </html>
                """.trimIndent(),
                NotificationType.INFORMATION
            )
            
            notification.addAction(object : NotificationAction("Open Plugin Settings") {
                override fun actionPerformed(e: AnActionEvent, notification: Notification) {
                    ShowSettingsUtil.getInstance().showSettingsDialog(project, "Ollama OpenRouter Proxy")
                    notification.expire()
                }
            })
            
            notification.addAction(object : NotificationAction("Dismiss") {
                override fun actionPerformed(e: AnActionEvent, notification: Notification) {
                    notification.expire()
                }
            })
            
            Notifications.Bus.notify(notification, project)
        }
    }
}
