package com.zxcizc.ollamaopenrouterproxyjetbrainsplugin

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.openapi.util.NlsActions

class ToggleProxyAction : DumbAwareToggleAction() {

    override fun isSelected(e: AnActionEvent): Boolean {
        return PluginSettingsState.getInstance().isProxyEnabled
    }

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        val settings = PluginSettingsState.getInstance()
        settings.isProxyEnabled = state

        val title = "Ollama OpenRouter Proxy"
        val message = if (state) {
            "Proxy enabled. Requests will be sent to OpenRouter."
        } else {
            "Proxy disabled. Requests will bypass to localhost:11434."
        }

        Notifications.Bus.notify(Notification("OllamaOpenRouterProxy", title, message, NotificationType.INFORMATION))
    }

    override fun update(e: AnActionEvent) {
        super.update(e)
        val selected = isSelected(e)
        val presentation = e.presentation
        if (selected) {
            presentation.text = "Proxy: OpenRouter"
            presentation.description = "Click to disable proxy and bypass to local Ollama"
        } else {
            presentation.text = "Proxy: Bypass"
            presentation.description = "Click to enable OpenRouter proxy"
        }
    }
}