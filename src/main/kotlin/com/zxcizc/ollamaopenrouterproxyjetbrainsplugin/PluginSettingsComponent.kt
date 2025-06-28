package com.zxcizc.ollamaopenrouterproxyjetbrainsplugin

import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPasswordField
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JPanel

class PluginSettingsComponent {

    val panel: JPanel
    private val openRouterApiKeyField = JBPasswordField()
    private val enableDebugLoggingCheckBox = JBCheckBox("Enable detailed request/response logging") // --- 이 줄을 추가합니다 ---

    init {
        panel = FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("Enter your OpenRouter API Key:"), openRouterApiKeyField, 1, false)
            .addComponent(enableDebugLoggingCheckBox, 1) // --- 이 줄을 추가합니다 ---
            .addComponentFillVertically(JPanel(), 0)
            .panel
    }

    val preferredFocusedComponent: JComponent
        get() = openRouterApiKeyField

    var openRouterApiKey: String
        get() = String(openRouterApiKeyField.password)
        set(value) {
            openRouterApiKeyField.text = value
        }

    // --- 이 프로퍼티를 추가합니다 ---
    var enableDebugLogging: Boolean
        get() = enableDebugLoggingCheckBox.isSelected
        set(value) {
            enableDebugLoggingCheckBox.isSelected = value
        }
}