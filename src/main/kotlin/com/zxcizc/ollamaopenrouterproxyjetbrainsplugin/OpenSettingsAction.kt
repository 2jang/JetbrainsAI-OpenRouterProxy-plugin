package com.zxcizc.ollamaopenrouterproxyjetbrainsplugin

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbAware

class OpenSettingsAction : AnAction(), DumbAware {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project
        ShowSettingsUtil.getInstance().showSettingsDialog(
            project,
            "Ollama OpenRouter Proxy"
        )
    }

    override fun update(e: AnActionEvent) {
        // 액션이 항상 활성화되도록 설정
        e.presentation.isEnabledAndVisible = true
    }
}
