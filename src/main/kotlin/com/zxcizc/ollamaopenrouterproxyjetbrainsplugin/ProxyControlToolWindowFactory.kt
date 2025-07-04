package com.zxcizc.ollamaopenrouterproxyjetbrainsplugin

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.dsl.builder.*
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import java.awt.Dimension
import java.util.*
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.text.AbstractDocument
import javax.swing.text.AttributeSet
import javax.swing.text.DocumentFilter

class ProxyControlToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val proxyControlPanel = ProxyControlPanel()
        val content = ContentFactory.getInstance().createContent(proxyControlPanel.createPanel(), "", false)
        toolWindow.contentManager.addContent(content)
        Disposer.register(toolWindow.disposable, proxyControlPanel)
    }

    private class ProxyControlPanel : PluginSettingsState.SettingsChangeListener, Disposable {
        private val settings = PluginSettingsState.getInstance()

        // --- UI Component Member Variables ---
        private lateinit var statusLabel: JBLabel
        private lateinit var overrideCheckBox: JCheckBox

        private lateinit var temperatureSlider: JSlider
        private lateinit var temperatureLabel: JBLabel
        private lateinit var topPSlider: JSlider
        private lateinit var topPLabel: JBLabel
        private lateinit var topKSpinner: JSpinner
        private lateinit var minPSlider: JSlider
        private lateinit var minPLabel: JBLabel
        private lateinit var topASlider: JSlider
        private lateinit var topALabel: JBLabel
        private lateinit var seedTextField: JTextField
        private lateinit var frequencyPenaltySlider: JSlider
        private lateinit var frequencyPenaltyLabel: JBLabel
        private lateinit var presencePenaltySlider: JSlider
        private lateinit var presencePenaltyLabel: JBLabel
        private lateinit var repetitionPenaltySlider: JSlider
        private lateinit var repetitionPenaltyLabel: JBLabel
        private lateinit var maxTokensTextField: JTextField
        private lateinit var stopTextField: JTextField
        private lateinit var responseFormatComboBox: JComboBox<String>
        private lateinit var toolsButton: JButton
        private lateinit var toolChoiceButton: JButton
        private lateinit var logitBiasButton: JButton
        private lateinit var logprobsCheckbox: JCheckBox
        private lateinit var topLogprobsSpinner: JSpinner

        private val componentsToToggle = mutableListOf<JComponent>()

        override fun dispose() {
            settings.removeListener(this)
        }

        fun createPanel(): JComponent {
            settings.addListener(this)

            val mainPanel = panel {
                group("Proxy Control") {
                    row {
                        checkBox("Enable OpenRouter Proxy").bindSelected(settings::isProxyEnabled)
                    }
                    row("Status:") {
                        statusLabel = JBLabel("Initializing...")
                        statusLabel.foreground = JBColor.GRAY
                        cell(statusLabel)
                    }
                }
                group("Parameters") {
                    row {
                        overrideCheckBox = checkBox("Override model parameters")
                            .bindSelected(settings::overrideParameters)
                            .onChanged {
                                toggleAllComponents(it.isSelected)
                                settings.notifySettingsChanged()
                            }.component
                    }
                    row {
                        val parameterScrollPane = JBScrollPane(createParameterPanel()).apply { border = JBUI.Borders.empty() }
                        cell(parameterScrollPane).align(Align.FILL)
                    }.resizableRow()
                }
            }
            toggleAllComponents(overrideCheckBox.isSelected)
            updateStatus()
            return mainPanel
        }

        private fun createParameterPanel(): JComponent {
            return panel {
                group("Sampling") {
                    addSliderRow(this, "🌡 Temperature", 0..200, 1.0, { settings.activeParameters.temperature }, { v -> settings.activeParameters.temperature = v }).also { (s, l) -> temperatureSlider = s; temperatureLabel = l }
                    addSliderRow(this, "   Top P", 0..100, 1.0, { settings.activeParameters.topP }, { v -> settings.activeParameters.topP = v }).also { (s, l) -> topPSlider = s; topPLabel = l }
                    addSpinnerRow(this, "   Top K", 0..1000, 0, { settings.activeParameters.topK }, { v -> settings.activeParameters.topK = v }).also { s -> topKSpinner = s }
                    addSliderRow(this, "   Min P", 0..100, 0.0, { settings.activeParameters.minP }, { v -> settings.activeParameters.minP = v }).also { (s, l) -> minPSlider = s; minPLabel = l }
                    addSliderRow(this, "   Top A", 0..100, 0.0, { settings.activeParameters.topA }, { v -> settings.activeParameters.topA = v }).also { (s, l) -> topASlider = s; topALabel = l }
                    addSeedRow(this).also { tf -> seedTextField = tf }
                }

                group("Repetition Control") {
                    addSliderRow(this, "🔁 Frequency Penalty", -200..200, 0.0, { settings.activeParameters.frequencyPenalty }, { v -> settings.activeParameters.frequencyPenalty = v }).also { (s, l) -> frequencyPenaltySlider = s; frequencyPenaltyLabel = l }
                    addSliderRow(this, "   Presence Penalty", -200..200, 0.0, { settings.activeParameters.presencePenalty }, { v -> settings.activeParameters.presencePenalty = v }).also { (s, l) -> presencePenaltySlider = s; presencePenaltyLabel = l }
                    addSliderRow(this, "   Repetition Penalty", 0..200, 1.0, { settings.activeParameters.repetitionPenalty }, { v -> settings.activeParameters.repetitionPenalty = v }).also { (s, l) -> repetitionPenaltySlider = s; repetitionPenaltyLabel = l }
                }

                group("Output Control") {
                    addIntTextFieldRow(this, "📦 Max Tokens", { settings.activeParameters.maxTokens }, { v -> settings.activeParameters.maxTokens = v }).also { tf -> maxTokensTextField = tf }
                    addStopSequenceRow(this).also { tf -> stopTextField = tf }
                    addResponseFormatRow(this).also { cb -> responseFormatComboBox = cb }
                }

                group("Tools") {
                    addJsonEditRow(this, "🔧 Tools", { settings.activeParameters.toolsJson }, { v -> settings.activeParameters.toolsJson = v }).also { b -> toolsButton = b }
                    addJsonEditRow(this, "   Tool Choice", { settings.activeParameters.toolChoiceJson }, { v -> settings.activeParameters.toolChoiceJson = v }).also { b -> toolChoiceButton = b }
                }

                group("Advanced") {
                    addJsonEditRow(this, "⚙️ Logit Bias", { settings.activeParameters.logitBiasJson }, { v -> settings.activeParameters.logitBiasJson = v }).also { b -> logitBiasButton = b }
                    addLogprobsRow(this).also { (cb, s) -> logprobsCheckbox = cb; topLogprobsSpinner = s }
                }
            }
        }

        private fun toggleAllComponents(isEnabled: Boolean) {
            componentsToToggle.forEach { it.isEnabled = isEnabled }
        }

        private fun Panel.addSliderRow(panel: Panel, label: @Nls String, range: IntRange, defaultVal: Double, getter: () -> Double?, setter: (Double?) -> Unit): Pair<JSlider, JBLabel> {
            val slider = JSlider(range.first, range.last, (getter()?.times(100))?.toInt() ?: (defaultVal * 100).toInt())
            val valueLabel = JBLabel(String.format("%.2f", getter() ?: defaultVal))

            slider.addChangeListener {
                if (!slider.valueIsAdjusting) {
                    val newValue = slider.value / 100.0
                    setter(if (newValue == defaultVal) null else newValue)
                    valueLabel.text = String.format("%.2f", newValue)
                    settings.notifySettingsChanged()
                }
            }
            val resetButton = JButton(AllIcons.General.Reset)
            resetButton.addActionListener {
                setter(null)
                slider.value = (defaultVal * 100).toInt()
                valueLabel.text = String.format("%.2f", defaultVal)
                settings.notifySettingsChanged()
            }
            panel.row(label) { cell(valueLabel); cell(slider).align(AlignX.FILL); cell(resetButton) }
            componentsToToggle.addAll(listOf(valueLabel, slider, resetButton))
            return slider to valueLabel
        }

        private fun Panel.addSpinnerRow(panel: Panel, label: @Nls String, range: IntRange, defaultVal: Int, getter: () -> Int?, setter: (Int?) -> Unit): JSpinner {
            val spinner = JSpinner(SpinnerNumberModel(getter() ?: defaultVal, range.first, range.last, 1))
            spinner.addChangeListener {
                val value = spinner.value as Int
                setter(if (value == defaultVal) null else value)
                settings.notifySettingsChanged()
            }
            val resetButton = JButton(AllIcons.General.Reset)
            resetButton.addActionListener {
                setter(null)
                spinner.value = defaultVal
                settings.notifySettingsChanged()
            }
            panel.row(label) { cell(spinner); cell(resetButton) }
            componentsToToggle.addAll(listOf(spinner, resetButton))
            return spinner
        }

        private fun Panel.addIntTextFieldRow(panel: Panel, label: @Nls String, getter: () -> Int?, setter: (Int?) -> Unit): JTextField {
            val textField = JTextField(getter()?.toString() ?: "", 8)
            (textField.document as AbstractDocument).documentFilter = IntDocumentFilter()
            textField.document.addDocumentListener(object : DocumentListener {
                override fun insertUpdate(e: DocumentEvent?) = update()
                override fun removeUpdate(e: DocumentEvent?) = update()
                override fun changedUpdate(e: DocumentEvent?) = update()
                private fun update() {
                    setter(textField.text.toIntOrNull())
                    settings.notifySettingsChanged()
                }
            })
            panel.row(label) { cell(textField) }
            componentsToToggle.add(textField)
            return textField
        }

        private fun Panel.addSeedRow(panel: Panel): JTextField {
            val textField = JTextField(settings.activeParameters.seed?.toString() ?: "", 10)
            (textField.document as AbstractDocument).documentFilter = IntDocumentFilter()
            textField.document.addDocumentListener(object : DocumentListener {
                override fun insertUpdate(e: DocumentEvent?) = update()
                override fun removeUpdate(e: DocumentEvent?) = update()
                override fun changedUpdate(e: DocumentEvent?) = update()
                private fun update() {
                    settings.activeParameters.seed = textField.text.toIntOrNull()
                    settings.notifySettingsChanged()
                }
            })
            val randomButton = JButton(AllIcons.Actions.Refresh)
            randomButton.addActionListener { textField.text = Random().nextInt(Int.MAX_VALUE).toString() }
            panel.row("🎲 Seed") { cell(textField); cell(randomButton) }
            componentsToToggle.addAll(listOf(textField, randomButton))
            return textField
        }

        private fun Panel.addStopSequenceRow(panel: Panel): JTextField {
            val textField = JTextField(settings.activeParameters.stop?.joinToString(",") ?: "")
            textField.document.addDocumentListener(object : DocumentListener {
                override fun insertUpdate(e: DocumentEvent?) = update()
                override fun removeUpdate(e: DocumentEvent?) = update()
                override fun changedUpdate(e: DocumentEvent?) = update()
                private fun update() {
                    val list = textField.text.split(",").map(String::trim).filter(String::isNotBlank)
                    settings.activeParameters.stop = if (list.isEmpty()) null else list.toMutableList()
                    settings.notifySettingsChanged()
                }
            })
            panel.row("🛑 Stop") { cell(textField).align(AlignX.FILL) }
            componentsToToggle.add(textField)
            return textField
        }

        private fun Panel.addResponseFormatRow(panel: Panel): JComboBox<String> {
            val items = listOf("default", "json_object")
            val comboBox = JComboBox(items.toTypedArray())
            comboBox.selectedItem = settings.activeParameters.responseFormatType ?: "default"
            comboBox.addActionListener {
                val selected = comboBox.selectedItem as String
                settings.activeParameters.responseFormatType = if (selected == "default") null else selected
                settings.notifySettingsChanged()
            }
            panel.row("Format") { cell(comboBox) }
            componentsToToggle.add(comboBox)
            return comboBox
        }

        private fun Panel.addJsonEditRow(panel: Panel, label: @Nls String, getter: () -> String?, setter: (String?) -> Unit): JButton {
            val button = JButton("Edit...")
            button.addActionListener {
                val dialog = JsonEditorDialog(getter() ?: "", label)
                if (dialog.showAndGet()) {
                    val newText = dialog.getJsonText()
                    setter(if (newText.isBlank()) null else newText)
                    settings.notifySettingsChanged()
                }
            }
            panel.row(label) { cell(button) }
            componentsToToggle.add(button)
            return button
        }

        private fun Panel.addLogprobsRow(panel: Panel): Pair<JCheckBox, JSpinner> {
            val spinner = addSpinnerRow(panel, "   Top Logprobs", 0..20, 0, { settings.activeParameters.topLogprobs }, { v -> settings.activeParameters.topLogprobs = v })
            val checkbox = JCheckBox("Return log probabilities")
            checkbox.isSelected = settings.activeParameters.logprobs ?: false

            checkbox.addActionListener {
                val isSelected = (it.source as JCheckBox).isSelected
                settings.activeParameters.logprobs = if (isSelected) true else null
                spinner.isEnabled = isSelected
                settings.notifySettingsChanged()
            }

            panel.row { cell(checkbox) }

            spinner.isEnabled = checkbox.isSelected
            componentsToToggle.add(checkbox)
            return checkbox to spinner
        }

        override fun onProxyEnabledChanged(enabled: Boolean) { updateStatus() }
        override fun onSettingsChanged() { updateStatus() }

        private fun updateStatus() {
            ApplicationManager.getApplication().invokeLater {
                if (this::statusLabel.isInitialized) {
                    statusLabel.text = getDetailedStatus()
                    statusLabel.foreground = when {
                        statusLabel.text.contains("Bypass") -> UIUtil.getLabelDisabledForeground()
                        statusLabel.text.contains("API Key Required") -> JBColor.ORANGE
                        statusLabel.text.contains("Proxy") -> JBColor.BLUE
                        else -> UIUtil.getLabelForeground()
                    }
                }
            }
        }

        private fun getDetailedStatus(): String = try { PluginStartupActivity.getProxyServerInstance().getDetailedStatus() } catch (e: Exception) { "Status: Server Starting..." }

        private class IntDocumentFilter : DocumentFilter() {
            private val regex = "\\d*".toRegex()
            override fun insertString(fb: FilterBypass, offset: Int, string: String, attr: AttributeSet?) { if (string.matches(regex)) super.insertString(fb, offset, string, attr) }
            override fun replace(fb: FilterBypass, offset: Int, length: Int, text: String, attrs: AttributeSet?) { if (text.matches(regex)) super.replace(fb, offset, length, text, attrs) }
        }

        private class JsonEditorDialog(@Nls initialText: String, @Nls title: String) : DialogWrapper(true) {
            private val textArea = JBTextArea(initialText, 15, 60)
            init { this.title = "Edit $title"; init() }
            override fun createCenterPanel(): JComponent {
                val panel = JPanel(BorderLayout())
                panel.add(JBScrollPane(textArea), BorderLayout.CENTER)
                panel.minimumSize = Dimension(400, 300)
                return panel
            }
            override fun getPreferredFocusedComponent(): JComponent = textArea
            fun getJsonText(): String = textArea.text
        }
    }
}