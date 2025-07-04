package com.zxcizc.ollamaopenrouterproxyjetbrainsplugin

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.Messages

class ShowInformationAction : AnAction(), DumbAware {

    override fun actionPerformed(e: AnActionEvent) {
        val proxyServer = PluginStartupActivity.getProxyServerInstance()
        val settings = PluginSettingsState.getInstance()
        val proxyPort = settings.proxyPort

        val infoMessage = """
            <html>
            <h3>🔧 Ollama OpenRouter Proxy Information</h3>
            <br>
            <b>📋 Setup Instructions:</b><br>
            1. Go to <b>Tools → AI Assistant → Models</b><br>
            2. Set Ollama URL to: <b>http://localhost:$proxyPort</b><br>
            3. Configure OpenRouter API key in plugin settings<br>
            <br>
            <b>🌐 Server Status:</b><br>
            ${proxyServer.getDetailedStatus()}<br>
            <br>
            <b>⚙️ Configuration:</b><br>
            • Proxy Server Port: <b>$proxyPort</b><br>
            • Local Ollama Port (for bypass mode): <b>${settings.ollamaBaseUrl}</b><br>
            • Proxy Mode: <b>${if (settings.isProxyEnabled) "Enabled" else "Disabled"}</b><br>  
            • API Key: <b>${if (settings.openRouterApiKey.isBlank()) "Not configured" else "Configured"}</b><br>
            • Whitelisted Models: <b>${if (settings.selectedModels.isEmpty()) "All models" else "${settings.selectedModels.size} models"}</b><br>
            <br>
            <b>📖 How it works:</b><br>
            • <b>Proxy Mode:</b> Requests to localhost:$proxyPort → OpenRouter.ai<br>
            • <b>Bypass Mode:</b> Requests to localhost:$proxyPort → ${settings.ollamaBaseUrl}<br>
            </html>
        """.trimIndent()

        Messages.showInfoMessage(e.project, infoMessage, "Ollama OpenRouter Proxy Information")
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = true
    }
}