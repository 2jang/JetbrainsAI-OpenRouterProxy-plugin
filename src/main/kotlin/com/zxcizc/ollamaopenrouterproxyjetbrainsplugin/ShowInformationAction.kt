package com.zxcizc.ollamaopenrouterproxyjetbrainsplugin

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.Messages

class ShowInformationAction : AnAction(), DumbAware {

    override fun actionPerformed(e: AnActionEvent) {
        val proxyServer = PluginStartupActivity.getProxyServerInstance()
        val settings = PluginSettingsState.getInstance()
        
        val infoMessage = """
            <html>
            <h3>🔧 Ollama OpenRouter Proxy Information</h3>
            <br>
            <b>📋 Setup Instructions:</b><br>
            1. Go to <b>Tools → AI Assistant → Models</b><br>
            2. Set Ollama URL to: <b>http://localhost:11444</b><br>
            3. Configure OpenRouter API key in plugin settings<br>
            <br>
            <b>🎯 Hybrid Feature:</b><br>
            Access both local Ollama and OpenRouter models in one unified list!<br>
            • <b>(local)</b> models run on your machine<br>
            • <b>OpenRouter</b> models run in the cloud<br>
            <br>
            <b>🌐 Current Status:</b><br>
            ${proxyServer.getDetailedStatus()}<br>
            <br>
            <b>⚙️ Configuration:</b><br>
            • Proxy Server Port: <b>11444</b><br>
            • Local Ollama Port: <b>11434</b><br>
            • Proxy Mode: <b>${if (settings.isProxyEnabled) "Enabled" else "Disabled"}</b><br>
            • API Key: <b>${if (settings.openRouterApiKey.isBlank()) "Not configured" else "Configured"}</b><br>
            • Whitelisted Models: <b>${if (settings.selectedModels.isEmpty()) "All models" else "${settings.selectedModels.size} models"}</b><br>
            <br>
            <b>📖 How it works:</b><br>
            • <b>Proxy Mode:</b> Hybrid list with local + OpenRouter models<br>
            • <b>Bypass Mode:</b> Direct connection to local Ollama only<br>
            </html>
        """.trimIndent()
        
        Messages.showInfoMessage(e.project, infoMessage, "Ollama OpenRouter Proxy Information")
    }

    override fun update(e: AnActionEvent) {
        // 액션이 항상 활성화되도록 설정
        e.presentation.isEnabledAndVisible = true
    }
}
