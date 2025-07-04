package com.zxcizc.ollamaopenrouterproxyjetbrainsplugin

import com.google.gson.Gson
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.Messages
import com.intellij.ui.CollectionListModel
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.*
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import com.intellij.ui.JBColor
import com.intellij.util.ui.UIUtil
import java.awt.*
import java.net.HttpURLConnection
import java.net.URI
import javax.swing.*
import javax.swing.event.DocumentEvent

private data class SettingsOpenRouterModelsResponse(val data: List<SettingsOpenRouterModelInfo>)
private data class SettingsOpenRouterModelInfo(val id: String)

class PluginSettingsComponent : PluginSettingsState.SettingsChangeListener, Disposable {

    val panel: JPanel
    private val openRouterApiKeyField = JBPasswordField()
    private val apiKeyStatusLabel = JBLabel()
    private val ollamaBaseUrlField = JBTextField()
    private val proxyPortSpinner = JSpinner(SpinnerNumberModel(11444, 1024, 65535, 1))
    private val enableDebugLoggingCheckBox = JBCheckBox("Enable detailed request/response logging")
    private val enableProxyCheckBox = JBCheckBox("Enable OpenRouter Proxy (if disabled, bypasses to localhost:11434)")

    private val availableModelsListModel = CollectionListModel<String>()
    private val selectedModelsListModel = CollectionListModel<String>()
    private val availableModelsList = JBList(availableModelsListModel)
    private val selectedModelsList = JBList(selectedModelsListModel)
    private val availableModelsSearchField = SearchTextField()
    private val selectedModelsSearchField = SearchTextField()

    private val addButton = JButton(">")
    private val addAllButton = JButton(">>")
    private val removeButton = JButton("<")
    private val removeAllButton = JButton("<<")
    private val refreshButton = JButton("Refresh Models")

    private val allAvailableModels = mutableListOf<String>()
    private val settings = PluginSettingsState.getInstance()

    init {
        settings.addListener(this)
        enableProxyCheckBox.addActionListener { settings.isProxyEnabled = enableProxyCheckBox.isSelected }

        openRouterApiKeyField.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                val apiKey = String(openRouterApiKeyField.password)
                updateApiKeyStatus(apiKey)
                if (apiKey.length > 10 && allAvailableModels.isEmpty()) {
                    SwingUtilities.invokeLater { fetchModels() }
                }
            }
        })

        setupModelLists()
        setupButtons()
        setupSearchFields()
        panel = createMainPanel()

        enableProxyCheckBox.isSelected = settings.isProxyEnabled
        ollamaBaseUrlField.text = settings.ollamaBaseUrl
        proxyPortSpinner.value = settings.proxyPort

        updateApiKeyStatus(String(openRouterApiKeyField.password))
    }

    private fun updateApiKeyStatus(apiKey: String) {
        if (apiKey.isBlank()) {
            apiKeyStatusLabel.text = ""
            return
        }

        if (apiKey.length < 10) {
            apiKeyStatusLabel.text = "⚠️ API key seems too short"
            apiKeyStatusLabel.foreground = JBColor.ORANGE
            return
        }

        SwingUtilities.invokeLater {
            apiKeyStatusLabel.text = "🔍 Validating API key..."
            apiKeyStatusLabel.foreground = JBColor.BLUE
        }

        Thread {
            val keyData = ProxyServer.validateApiKey(apiKey)
            SwingUtilities.invokeLater {
                if (keyData != null) {
                    val statusText = buildString {
                        append("✅ Valid")
                        keyData.label?.let { append(" - $it") }
                        keyData.limit_remaining?.let { remaining ->
                            keyData.limit?.let { limit ->
                                append(" ($${String.format("%.2f", remaining)}/$${String.format("%.2f", limit)})")
                            }
                        }
                        if (keyData.is_free_tier == true) { append(" [Free Tier]") }
                        if (keyData.is_provisioning_key == true) { append(" [Provisioning]") }
                    }
                    apiKeyStatusLabel.text = statusText
                    apiKeyStatusLabel.foreground = JBColor(Color(0, 128, 0), Color(144, 238, 144))
                } else {
                    apiKeyStatusLabel.text = "❌ Invalid API key or network error"
                    apiKeyStatusLabel.foreground = JBColor.RED
                }
            }
        }.start()
    }

    private fun createMainPanel(): JPanel {
        val guidancePanel = JPanel(BorderLayout())
        // *** FIX: Use compound border for compatibility ***
        guidancePanel.border = JBUI.Borders.compound(
            JBUI.Borders.customLine(JBColor.border(), 1),
            JBUI.Borders.empty(5)
        )
        guidancePanel.background = UIUtil.getPanelBackground()

        val proxyPort = PluginSettingsState.getInstance().proxyPort
        val guidanceLabel = JBLabel("""
            <html>
            <b>Setup Guide:</b><br>
            1. Go to <b>Tools → AI Assistant → Models</b><br>
            2. Set Ollama URL to: <b>http://localhost:$proxyPort</b><br>
            3. Configure your OpenRouter API key below.
            </html>
        """.trimIndent()).apply { border = JBUI.Borders.empty(10) }
        guidancePanel.add(guidanceLabel, BorderLayout.CENTER)

        val apiKeyPanel = JPanel(BorderLayout()).apply { add(openRouterApiKeyField, BorderLayout.CENTER); add(apiKeyStatusLabel, BorderLayout.SOUTH) }

        val portEditor = proxyPortSpinner.editor.apply { if (this is JSpinner.DefaultEditor) this.textField.columns = 5 }

        return FormBuilder.createFormBuilder()
            .addComponent(guidancePanel)
            .addLabeledComponent(JBLabel("OpenRouter API Key:"), apiKeyPanel, true)
            .addLabeledComponent(JBLabel("Ollama URL (for bypass mode):"), ollamaBaseUrlField, true)
            .addLabeledComponent(JBLabel("Proxy Server Port:"), portEditor, true)
            .addComponent(enableProxyCheckBox)
            .addComponent(enableDebugLoggingCheckBox)
            .addComponent(createModelWhitelistPanel())
            .addComponentFillVertically(JPanel(), 0)
            .panel
    }

    private fun createModelWhitelistPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.border = JBUI.Borders.emptyTop(10)

        val titlePanel = JPanel(FlowLayout(FlowLayout.LEFT))
        titlePanel.add(JBLabel("Model Whitelist (if none selected, all models are available):"))
        titlePanel.add(refreshButton)

        val contentPanel = JPanel(BorderLayout())
        contentPanel.add(createDualListPanel(), BorderLayout.CENTER)

        panel.add(titlePanel, BorderLayout.NORTH)
        panel.add(contentPanel, BorderLayout.CENTER)

        return panel
    }

    private fun createDualListPanel(): JPanel {
        val panel = JPanel(BorderLayout())

        val leftPanel = JPanel(BorderLayout())
        leftPanel.border = JBUI.Borders.empty(5)

        val leftTopPanel = JPanel(BorderLayout())
        leftTopPanel.add(JBLabel("Available Models:"), BorderLayout.NORTH)
        leftTopPanel.add(availableModelsSearchField, BorderLayout.SOUTH)
        leftPanel.add(leftTopPanel, BorderLayout.NORTH)
        leftPanel.add(JBScrollPane(availableModelsList), BorderLayout.CENTER)

        val rightPanel = JPanel(BorderLayout())
        rightPanel.border = JBUI.Borders.empty(5)

        val rightTopPanel = JPanel(BorderLayout())
        rightTopPanel.add(JBLabel("Whitelisted Models:"), BorderLayout.NORTH)
        rightTopPanel.add(selectedModelsSearchField, BorderLayout.SOUTH)
        rightPanel.add(rightTopPanel, BorderLayout.NORTH)
        rightPanel.add(JBScrollPane(selectedModelsList), BorderLayout.CENTER)

        val buttonPanel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints()
        gbc.insets = JBUI.insets(2)
        gbc.fill = GridBagConstraints.HORIZONTAL

        gbc.gridy = 0; buttonPanel.add(addButton, gbc)
        gbc.gridy = 1; buttonPanel.add(addAllButton, gbc)
        gbc.gridy = 2; buttonPanel.add(removeButton, gbc)
        gbc.gridy = 3; buttonPanel.add(removeAllButton, gbc)

        panel.add(leftPanel, BorderLayout.WEST)
        panel.add(buttonPanel, BorderLayout.CENTER)
        panel.add(rightPanel, BorderLayout.EAST)

        leftPanel.preferredSize = Dimension(300, 400)
        rightPanel.preferredSize = Dimension(300, 400)
        buttonPanel.preferredSize = Dimension(80, 400)

        return panel
    }

    private fun setupModelLists() {
        availableModelsList.selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
        selectedModelsList.selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION

        availableModelsList.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) { if (e.clickCount == 2) moveSelectedToWhitelist() }
        })

        selectedModelsList.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) { if (e.clickCount == 2) removeSelectedFromWhitelist() }
        })

        availableModelsList.addListSelectionListener { updateButtonStates() }
        selectedModelsList.addListSelectionListener { updateButtonStates() }
    }

    private fun setupButtons() {
        val buttonSize = Dimension(60, 30)
        arrayOf(addButton, addAllButton, removeButton, removeAllButton).forEach {
            it.preferredSize = buttonSize
            it.minimumSize = buttonSize
            it.maximumSize = buttonSize
        }

        addButton.addActionListener { moveSelectedToWhitelist() }
        addAllButton.addActionListener { moveAllToWhitelist() }
        removeButton.addActionListener { removeSelectedFromWhitelist() }
        removeAllButton.addActionListener { removeAllFromWhitelist() }
        refreshButton.addActionListener { fetchModels() }

        updateButtonStates()
    }

    private fun setupSearchFields() {
        availableModelsSearchField.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) { filterAvailableModels() }
        })

        selectedModelsSearchField.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) { filterSelectedModels() }
        })
    }

    private fun filterAvailableModels() {
        val searchText = availableModelsSearchField.text.lowercase()
        val currentSelected = selectedModelsListModel.toList().toSet()
        val filteredModels = allAvailableModels.filter { !currentSelected.contains(it) && (searchText.isEmpty() || it.lowercase().contains(searchText)) }.sorted()
        availableModelsListModel.replaceAll(filteredModels)
    }

    private fun filterSelectedModels() {
        val searchText = selectedModelsSearchField.text.lowercase()
        val allSelected = settings.selectedModels.toList()
        val filteredModels = allSelected.filter { searchText.isEmpty() || it.lowercase().contains(searchText) }.sorted()
        selectedModelsListModel.replaceAll(filteredModels)
    }

    private fun moveSelectedToWhitelist() {
        val selected = availableModelsList.selectedValuesList
        if (selected.isNotEmpty()) {
            selected.forEach { model ->
                availableModelsListModel.remove(model)
                selectedModelsListModel.add(model)
            }
            updateModelsInSettings()
            updateButtonStates()
        }
    }

    private fun moveAllToWhitelist() {
        val allAvailable = availableModelsListModel.toList()
        allAvailable.forEach { model ->
            availableModelsListModel.remove(model)
            selectedModelsListModel.add(model)
        }
        updateModelsInSettings()
        updateButtonStates()
    }

    private fun removeSelectedFromWhitelist() {
        val selected = selectedModelsList.selectedValuesList
        if (selected.isNotEmpty()) {
            selected.forEach { model ->
                selectedModelsListModel.remove(model)
                if (allAvailableModels.contains(model)) availableModelsListModel.add(model)
            }
            updateModelsInSettings()
            filterAvailableModels()
            updateButtonStates()
        }
    }

    private fun removeAllFromWhitelist() {
        val allSelected = selectedModelsListModel.toList()
        allSelected.forEach { model ->
            selectedModelsListModel.remove(model)
            if (allAvailableModels.contains(model)) availableModelsListModel.add(model)
        }
        updateModelsInSettings()
        filterAvailableModels()
        updateButtonStates()
    }

    private fun updateModelsInSettings() {
        settings.selectedModels = selectedModelsListModel.toList().toMutableSet()
        ProxyServer.invalidateModelsCache()
    }

    private fun updateButtonStates() {
        addButton.isEnabled = availableModelsList.selectedIndices.isNotEmpty()
        addAllButton.isEnabled = availableModelsListModel.size > 0
        removeButton.isEnabled = selectedModelsList.selectedIndices.isNotEmpty()
        removeAllButton.isEnabled = selectedModelsListModel.size > 0
    }

    private fun fetchModels() {
        val apiKey = String(openRouterApiKeyField.password)
        if (apiKey.isBlank()) {
            Messages.showWarningDialog("Please enter an OpenRouter API key first.", "API Key Required")
            return
        }

        refreshButton.isEnabled = false
        refreshButton.text = "Loading..."
        ProxyServer.invalidateModelsCache()

        ProgressManager.getInstance().run(object : Task.Backgroundable(null, "Fetching Models from OpenRouter...", false) {
            override fun run(indicator: com.intellij.openapi.progress.ProgressIndicator) {
                try {
                    val url = URI("https://openrouter.ai/api/v1/models").toURL()
                    val connection = url.openConnection() as HttpURLConnection
                    connection.setRequestProperty("Authorization", "Bearer $apiKey")

                    if (connection.responseCode == 200) {
                        val jsonResponse = connection.inputStream.bufferedReader().use { it.readText() }
                        val openRouterResponse = Gson().fromJson(jsonResponse, SettingsOpenRouterModelsResponse::class.java)

                        ApplicationManager.getApplication().invokeLater {
                            allAvailableModels.clear()
                            allAvailableModels.addAll(openRouterResponse.data.map { it.id }.sorted())
                            updateModelLists()
                        }
                    } else {
                        throw Exception("Failed to fetch models, status: ${connection.responseCode}")
                    }
                } catch (e: Exception) {
                    ApplicationManager.getApplication().invokeLater {
                        Messages.showErrorDialog("Error fetching models: ${e.message}", "Error")
                    }
                } finally {
                    ApplicationManager.getApplication().invokeLater {
                        refreshButton.isEnabled = true
                        refreshButton.text = "Refresh Models"
                    }
                }
            }
        })
    }

    private fun updateModelLists() {
        val currentSelected = settings.selectedModels
        selectedModelsListModel.replaceAll(currentSelected.sorted())
        filterAvailableModels()
        updateButtonStates()
    }

    val preferredFocusedComponent: JComponent get() = openRouterApiKeyField

    var openRouterApiKey: String
        get() = String(openRouterApiKeyField.password)
        set(value) { openRouterApiKeyField.text = value }

    var ollamaBaseUrl: String
        get() = ollamaBaseUrlField.text
        set(value) { ollamaBaseUrlField.text = value }

    var proxyPort: Int
        get() = proxyPortSpinner.value as Int
        set(value) { proxyPortSpinner.value = value }

    var enableDebugLogging: Boolean
        get() = enableDebugLoggingCheckBox.isSelected
        set(value) { enableDebugLoggingCheckBox.isSelected = value }

    var isProxyEnabled: Boolean
        get() = enableProxyCheckBox.isSelected
        set(value) { enableProxyCheckBox.isSelected = value }

    var selectedModels: Set<String>
        get() = selectedModelsListModel.toList().toSet()
        set(value) {
            settings.selectedModels = value.toMutableSet()
            if (allAvailableModels.isNotEmpty()) { updateModelLists() }
        }

    override fun onProxyEnabledChanged(enabled: Boolean) {
        ApplicationManager.getApplication().invokeLater { if (enableProxyCheckBox.isSelected != enabled) { enableProxyCheckBox.isSelected = enabled } }
    }

    override fun onSettingsChanged() {}
    override fun dispose() { settings.removeListener(this) }
}