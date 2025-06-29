package com.zxcizc.ollamaopenrouterproxyjetbrainsplugin

import com.google.gson.Gson
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.Disposable
import com.intellij.ui.components.*
import com.intellij.ui.SearchTextField
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.CollectionListModel
import com.intellij.ui.JBColor
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.*
import java.awt.event.ActionListener
import java.net.HttpURLConnection
import java.net.URI
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.ListSelectionEvent
import javax.swing.event.ListSelectionListener

// OpenRouter API 응답을 위한 데이터 클래스들 (설정 컴포넌트용)
private data class SettingsOpenRouterModelsResponse(val data: List<SettingsOpenRouterModelInfo>)
private data class SettingsOpenRouterModelInfo(val id: String)

class PluginSettingsComponent : PluginSettingsState.SettingsChangeListener, Disposable {

    val panel: JPanel
    private val openRouterApiKeyField = JBPasswordField()
    private val apiKeyStatusLabel = JBLabel()
    private val ollamaBaseUrlField = JBTextField()
    private val enableDebugLoggingCheckBox = JBCheckBox("Enable detailed request/response logging")
    private val enableProxyCheckBox = JBCheckBox("Enable OpenRouter Proxy (if disabled, bypasses to localhost:11434)")
    
    // 모델 리스트 관련 컴포넌트들
    private val availableModelsListModel = CollectionListModel<String>()
    private val selectedModelsListModel = CollectionListModel<String>()
    private val availableModelsList = JBList(availableModelsListModel)
    private val selectedModelsList = JBList(selectedModelsListModel)
    private val availableModelsSearchField = SearchTextField()
    private val selectedModelsSearchField = SearchTextField()
    
    // 이동 버튼들
    private val addButton = JButton(">")
    private val addAllButton = JButton(">>")
    private val removeButton = JButton("<")
    private val removeAllButton = JButton("<<")
    private val refreshButton = JButton("Refresh Models")
    
    // 전체 모델 목록 (필터링용)
    private val allAvailableModels = mutableListOf<String>()
    private val settings = PluginSettingsState.getInstance()

    init {
        // 설정 변경 리스너 등록
        settings.addListener(this)
        
        // 프록시 활성화 체크박스에 액션 리스너 추가
        enableProxyCheckBox.addActionListener {
            settings.isProxyEnabled = enableProxyCheckBox.isSelected
        }
        
        // API 키 필드에 자동 새로고침 및 검증 기능 추가
        openRouterApiKeyField.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                val apiKey = String(openRouterApiKeyField.password)
                updateApiKeyStatus(apiKey)
                
                if (apiKey.length > 10) { // API 키가 어느 정도 입력되면 자동 새로고침
                    SwingUtilities.invokeLater {
                        if (allAvailableModels.isEmpty()) {
                            fetchModels()
                        }
                    }
                }
            }
        })
        
        setupModelLists()
        setupButtons()
        setupSearchFields()
        
        panel = createMainPanel()
        
        // 초기 상태 설정
        enableProxyCheckBox.isSelected = settings.isProxyEnabled
        ollamaBaseUrlField.text = settings.ollamaBaseUrl
        
        // API 키 상태 초기화
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
        
        // 백그라운드에서 API 키 검증
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
                        if (keyData.is_free_tier == true) {
                            append(" [Free Tier]")
                        }
                        if (keyData.is_provisioning_key == true) {
                            append(" [Provisioning]")
                        }
                    }
                    apiKeyStatusLabel.text = statusText
                    apiKeyStatusLabel.foreground = JBColor(Color(0, 128, 0), Color(144, 238, 144)) // 라이트: 녹색, 다크: 라이트그린
                } else {
                    apiKeyStatusLabel.text = "❌ Invalid API key or network error"
                    apiKeyStatusLabel.foreground = JBColor.RED
                }
            }
        }.start()
    }

    private fun createMainPanel(): JPanel {
        // 설정 안내 패널
        val guidancePanel = JPanel(BorderLayout())
        guidancePanel.border = JBUI.Borders.empty(5, 10)
        val guidanceLabel = JBLabel("""
            <html>
            <div style='background-color: #E8F4FD; padding: 10px; border: 1px solid #B3D7FF; border-radius: 4px;'>
            <b>📋 Setup Guide:</b><br>
            1. Go to <b>Tools → AI Assistant → Models</b><br>
            2. Set Ollama URL to: <b>http://localhost:11444</b><br>
            3. Configure your OpenRouter API key below<br>
            4. The proxy will route requests between local Ollama and OpenRouter
            </div>
            </html>
        """.trimIndent())
        guidancePanel.add(guidanceLabel, BorderLayout.CENTER)
        
        // API 키 입력 패널 생성
        val apiKeyPanel = JPanel(BorderLayout())
        apiKeyPanel.add(openRouterApiKeyField, BorderLayout.CENTER)
        apiKeyPanel.add(apiKeyStatusLabel, BorderLayout.SOUTH)
        
        val mainPanel = FormBuilder.createFormBuilder()
            .addComponent(guidancePanel, 1)
            .addVerticalGap(10)
            .addLabeledComponent(JBLabel("Enter your OpenRouter API Key:"), apiKeyPanel, 1, false)
            .addLabeledComponent(JBLabel("Ollama Base URL:"), ollamaBaseUrlField, 1, false)
            .addComponent(enableProxyCheckBox, 1)
            .addComponent(enableDebugLoggingCheckBox, 1)
            .addComponent(createParametersPanel())
            .addComponent(createModelWhitelistPanel())
            .addComponentFillVertically(JPanel(), 0)
            .panel
        
        return mainPanel
    }
    
    private fun createParametersPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.border = JBUI.Borders.emptyTop(10)
        
        // 제목과 체크박스
        val titlePanel = JPanel(FlowLayout(FlowLayout.LEFT))
        val useCustomParamsCheckBox = JBCheckBox("Use Custom OpenRouter Parameters", settings.useCustomParameters)
        useCustomParamsCheckBox.addActionListener {
            settings.useCustomParameters = useCustomParamsCheckBox.isSelected
            updateParameterFieldsState()
        }
        titlePanel.add(useCustomParamsCheckBox)
        
        // 파라미터 입력 패널
        val parametersPanel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints()
        gbc.insets = JBUI.insets(2, 5)
        gbc.anchor = GridBagConstraints.WEST
        
        // Temperature
        gbc.gridx = 0; gbc.gridy = 0
        parametersPanel.add(JBLabel("Temperature (0.0-2.0):"), gbc)
        gbc.gridx = 1
        val temperatureSpinner = JSpinner(SpinnerNumberModel(settings.temperature, 0.0, 2.0, 0.1))
        temperatureSpinner.addChangeListener { settings.temperature = temperatureSpinner.value as Double }
        parametersPanel.add(temperatureSpinner, gbc)
        
        // Top P
        gbc.gridx = 0; gbc.gridy = 1
        parametersPanel.add(JBLabel("Top P (0.0-1.0):"), gbc)
        gbc.gridx = 1
        val topPSpinner = JSpinner(SpinnerNumberModel(settings.topP, 0.0, 1.0, 0.1))
        topPSpinner.addChangeListener { settings.topP = topPSpinner.value as Double }
        parametersPanel.add(topPSpinner, gbc)
        
        // Top K
        gbc.gridx = 0; gbc.gridy = 2
        parametersPanel.add(JBLabel("Top K:"), gbc)
        gbc.gridx = 1
        val topKSpinner = JSpinner(SpinnerNumberModel(settings.topK, 0, 100, 1))
        topKSpinner.addChangeListener { settings.topK = topKSpinner.value as Int }
        parametersPanel.add(topKSpinner, gbc)
        
        // Max Tokens
        gbc.gridx = 0; gbc.gridy = 3
        parametersPanel.add(JBLabel("Max Tokens:"), gbc)
        gbc.gridx = 1
        val maxTokensSpinner = JSpinner(SpinnerNumberModel(settings.maxTokens, 1, 4096, 1))
        maxTokensSpinner.addChangeListener { settings.maxTokens = maxTokensSpinner.value as Int }
        parametersPanel.add(maxTokensSpinner, gbc)
        
        // Frequency Penalty
        gbc.gridx = 0; gbc.gridy = 4
        parametersPanel.add(JBLabel("Frequency Penalty (-2.0-2.0):"), gbc)
        gbc.gridx = 1
        val frequencyPenaltySpinner = JSpinner(SpinnerNumberModel(settings.frequencyPenalty, -2.0, 2.0, 0.1))
        frequencyPenaltySpinner.addChangeListener { settings.frequencyPenalty = frequencyPenaltySpinner.value as Double }
        parametersPanel.add(frequencyPenaltySpinner, gbc)
        
        // Presence Penalty
        gbc.gridx = 0; gbc.gridy = 5
        parametersPanel.add(JBLabel("Presence Penalty (-2.0-2.0):"), gbc)
        gbc.gridx = 1
        val presencePenaltySpinner = JSpinner(SpinnerNumberModel(settings.presencePenalty, -2.0, 2.0, 0.1))
        presencePenaltySpinner.addChangeListener { settings.presencePenalty = presencePenaltySpinner.value as Double }
        parametersPanel.add(presencePenaltySpinner, gbc)
        
        // Repetition Penalty
        gbc.gridx = 0; gbc.gridy = 6
        parametersPanel.add(JBLabel("Repetition Penalty (0.0-2.0):"), gbc)
        gbc.gridx = 1
        val repetitionPenaltySpinner = JSpinner(SpinnerNumberModel(settings.repetitionPenalty, 0.0, 2.0, 0.1))
        repetitionPenaltySpinner.addChangeListener { settings.repetitionPenalty = repetitionPenaltySpinner.value as Double }
        parametersPanel.add(repetitionPenaltySpinner, gbc)
        
        // Seed
        gbc.gridx = 0; gbc.gridy = 7
        parametersPanel.add(JBLabel("Seed (optional):"), gbc)
        gbc.gridx = 1
        val seedField = JBTextField(settings.seed?.toString() ?: "")
        seedField.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                settings.seed = seedField.text.toIntOrNull()
            }
        })
        parametersPanel.add(seedField, gbc)
        
        // 리셋 버튼
        gbc.gridx = 0; gbc.gridy = 8; gbc.gridwidth = 2
        val resetButton = JButton("Reset to Defaults")
        resetButton.addActionListener {
            settings.temperature = 1.0
            settings.topP = 1.0
            settings.topK = 0
            settings.maxTokens = 1000
            settings.frequencyPenalty = 0.0
            settings.presencePenalty = 0.0
            settings.repetitionPenalty = 1.0
            settings.seed = null
            
            // UI 업데이트
            temperatureSpinner.value = 1.0
            topPSpinner.value = 1.0
            topKSpinner.value = 0
            maxTokensSpinner.value = 1000
            frequencyPenaltySpinner.value = 0.0
            presencePenaltySpinner.value = 0.0
            repetitionPenaltySpinner.value = 1.0
            seedField.text = ""
        }
        parametersPanel.add(resetButton, gbc)
        
        // 파라미터 필드들을 멤버 변수로 저장 (상태 업데이트용)
        parameterComponents = listOf(
            temperatureSpinner, topPSpinner, topKSpinner, maxTokensSpinner,
            frequencyPenaltySpinner, presencePenaltySpinner, repetitionPenaltySpinner,
            seedField, resetButton
        )
        
        panel.add(titlePanel, BorderLayout.NORTH)
        panel.add(parametersPanel, BorderLayout.CENTER)
        
        // 초기 상태 설정
        updateParameterFieldsState()
        
        return panel
    }
    
    private lateinit var parameterComponents: List<JComponent>
    
    private fun updateParameterFieldsState() {
        val enabled = settings.useCustomParameters
        if (::parameterComponents.isInitialized) {
            parameterComponents.forEach { it.isEnabled = enabled }
        }
    }

    private fun createModelWhitelistPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.border = JBUI.Borders.emptyTop(10)
        
        // 제목
        val titlePanel = JPanel(FlowLayout(FlowLayout.LEFT))
        titlePanel.add(JBLabel("Model Whitelist (if none selected, all models are available):"))
        titlePanel.add(refreshButton)
        
        // 메인 컨텐츠
        val contentPanel = JPanel(BorderLayout())
        contentPanel.add(createDualListPanel(), BorderLayout.CENTER)
        
        panel.add(titlePanel, BorderLayout.NORTH)
        panel.add(contentPanel, BorderLayout.CENTER)
        
        return panel
    }

    private fun createDualListPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        
        // 왼쪽 패널 (사용 가능한 모델들)
        val leftPanel = JPanel(BorderLayout())
        leftPanel.border = JBUI.Borders.empty(5)
        leftPanel.add(JBLabel("Available Models:"), BorderLayout.NORTH)
        leftPanel.add(availableModelsSearchField, BorderLayout.NORTH)
        
        val leftListPanel = JPanel(BorderLayout())
        leftListPanel.add(availableModelsSearchField, BorderLayout.NORTH)
        leftListPanel.add(JBScrollPane(availableModelsList), BorderLayout.CENTER)
        leftPanel.add(leftListPanel, BorderLayout.CENTER)
        
        // 오른쪽 패널 (선택된 모델들)
        val rightPanel = JPanel(BorderLayout())
        rightPanel.border = JBUI.Borders.empty(5)
        rightPanel.add(JBLabel("Whitelisted Models:"), BorderLayout.NORTH)
        
        val rightListPanel = JPanel(BorderLayout())
        rightListPanel.add(selectedModelsSearchField, BorderLayout.NORTH)
        rightListPanel.add(JBScrollPane(selectedModelsList), BorderLayout.CENTER)
        rightPanel.add(rightListPanel, BorderLayout.CENTER)
        
        // 중간 버튼 패널
        val buttonPanel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints()
        gbc.insets = JBUI.insets(2)
        gbc.fill = GridBagConstraints.HORIZONTAL
        
        gbc.gridy = 0
        buttonPanel.add(addButton, gbc)
        gbc.gridy = 1
        buttonPanel.add(addAllButton, gbc)
        gbc.gridy = 2
        buttonPanel.add(removeButton, gbc)
        gbc.gridy = 3
        buttonPanel.add(removeAllButton, gbc)
        
        // 전체 레이아웃
        panel.add(leftPanel, BorderLayout.WEST)
        panel.add(buttonPanel, BorderLayout.CENTER)
        panel.add(rightPanel, BorderLayout.EAST)
        
        // 패널 크기 설정
        leftPanel.preferredSize = Dimension(300, 400)
        rightPanel.preferredSize = Dimension(300, 400)
        buttonPanel.preferredSize = Dimension(80, 400)
        
        return panel
    }

    private fun setupModelLists() {
        // 리스트 설정
        availableModelsList.selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
        selectedModelsList.selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
        
        // 더블클릭으로 이동
        availableModelsList.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                if (e.clickCount == 2) {
                    moveSelectedToWhitelist()
                }
            }
        })
        
        selectedModelsList.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                if (e.clickCount == 2) {
                    removeSelectedFromWhitelist()
                }
            }
        })
        
        // 선택 변경 리스너
        availableModelsList.addListSelectionListener { updateButtonStates() }
        selectedModelsList.addListSelectionListener { updateButtonStates() }
    }

    private fun setupButtons() {
        // 버튼 크기 설정
        val buttonSize = Dimension(60, 30)
        arrayOf(addButton, addAllButton, removeButton, removeAllButton).forEach {
            it.preferredSize = buttonSize
            it.minimumSize = buttonSize
            it.maximumSize = buttonSize
        }
        
        // 버튼 액션 설정
        addButton.addActionListener { moveSelectedToWhitelist() }
        addAllButton.addActionListener { moveAllToWhitelist() }
        removeButton.addActionListener { removeSelectedFromWhitelist() }
        removeAllButton.addActionListener { removeAllFromWhitelist() }
        refreshButton.addActionListener { fetchModels() }
        
        updateButtonStates()
    }

    private fun setupSearchFields() {
        // 사용 가능한 모델 검색
        availableModelsSearchField.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                filterAvailableModels()
            }
        })
        
        // 선택된 모델 검색
        selectedModelsSearchField.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                filterSelectedModels()
            }
        })
    }

    private fun filterAvailableModels() {
        val searchText = availableModelsSearchField.text.lowercase()
        val currentSelected = selectedModelsListModel.toList().toSet()
        
        val filteredModels = allAvailableModels
            .filter { !currentSelected.contains(it) }
            .filter { searchText.isEmpty() || it.lowercase().contains(searchText) }
            .sorted()
        
        availableModelsListModel.removeAll()
        availableModelsListModel.addAll(0, filteredModels)
    }

    private fun filterSelectedModels() {
        val searchText = selectedModelsSearchField.text.lowercase()
        val allSelected = settings.selectedModels.toList()
        
        val filteredModels = allSelected
            .filter { searchText.isEmpty() || it.lowercase().contains(searchText) }
            .sorted()
        
        selectedModelsListModel.removeAll()
        selectedModelsListModel.addAll(0, filteredModels)
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
                if (allAvailableModels.contains(model)) {
                    availableModelsListModel.add(model)
                }
            }
            updateModelsInSettings()
            filterAvailableModels() // 검색 필터 재적용
            updateButtonStates()
        }
    }

    private fun removeAllFromWhitelist() {
        val allSelected = selectedModelsListModel.toList()
        allSelected.forEach { model ->
            selectedModelsListModel.remove(model)
            if (allAvailableModels.contains(model)) {
                availableModelsListModel.add(model)
            }
        }
        updateModelsInSettings()
        filterAvailableModels() // 검색 필터 재적용
        updateButtonStates()
    }

    private fun updateModelsInSettings() {
        settings.selectedModels = selectedModelsListModel.toList().toMutableSet()
        // 화이트리스트 변경 시 모델 캐시 무효화
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

        // 캐시 무효화
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
                            refreshButton.isEnabled = true
                            refreshButton.text = "Refresh Models"
                        }
                    } else {
                        throw Exception("Failed to fetch models, status: ${connection.responseCode}")
                    }
                } catch (e: Exception) {
                    ApplicationManager.getApplication().invokeLater {
                        Messages.showErrorDialog("Error fetching models: ${e.message}", "Error")
                        refreshButton.isEnabled = true
                        refreshButton.text = "Refresh Models"
                    }
                }
            }
        })
    }

    private fun updateModelLists() {
        val currentSelected = settings.selectedModels
        
        // 선택된 모델 리스트 업데이트
        selectedModelsListModel.removeAll()
        selectedModelsListModel.addAll(0, currentSelected.sorted())
        
        // 사용 가능한 모델 리스트 업데이트 (선택된 것 제외)
        filterAvailableModels()
        updateButtonStates()
    }

    val preferredFocusedComponent: JComponent get() = openRouterApiKeyField

    // Getter/Setter methods
    var openRouterApiKey: String
        get() = String(openRouterApiKeyField.password)
        set(value) {
            openRouterApiKeyField.text = value
        }

    var ollamaBaseUrl: String
        get() = ollamaBaseUrlField.text
        set(value) {
            ollamaBaseUrlField.text = value
        }

    var enableDebugLogging: Boolean
        get() = enableDebugLoggingCheckBox.isSelected
        set(value) {
            enableDebugLoggingCheckBox.isSelected = value
        }

    var isProxyEnabled: Boolean
        get() = enableProxyCheckBox.isSelected
        set(value) {
            enableProxyCheckBox.isSelected = value
        }

    var selectedModels: Set<String>
        get() = selectedModelsListModel.toList().toSet()
        set(value) {
            settings.selectedModels = value.toMutableSet()
            if (allAvailableModels.isNotEmpty()) {
                updateModelLists()
            }
        }
    
    // 파라미터 getter/setter (설정에서 직접 관리되므로 별도 UI 없음)
    var useCustomParameters: Boolean
        get() = settings.useCustomParameters
        set(value) {
            settings.useCustomParameters = value
        }
    
    var temperature: Double
        get() = settings.temperature
        set(value) {
            settings.temperature = value
        }
    
    var topP: Double
        get() = settings.topP
        set(value) {
            settings.topP = value
        }
        
    var topK: Int
        get() = settings.topK
        set(value) {
            settings.topK = value
        }
        
    var maxTokens: Int
        get() = settings.maxTokens
        set(value) {
            settings.maxTokens = value
        }
        
    var frequencyPenalty: Double
        get() = settings.frequencyPenalty
        set(value) {
            settings.frequencyPenalty = value
        }
        
    var presencePenalty: Double
        get() = settings.presencePenalty
        set(value) {
            settings.presencePenalty = value
        }
        
    var repetitionPenalty: Double
        get() = settings.repetitionPenalty
        set(value) {
            settings.repetitionPenalty = value
        }
        
    var seed: Int?
        get() = settings.seed
        set(value) {
            settings.seed = value
        }
    
    // SettingsChangeListener 구현
    override fun onProxyEnabledChanged(enabled: Boolean) {
        ApplicationManager.getApplication().invokeLater {
            if (enableProxyCheckBox.isSelected != enabled) {
                enableProxyCheckBox.isSelected = enabled
            }
        }
    }
    
    override fun onSettingsChanged() {
        // 설정 변경 시 추가 작업 (필요시)
    }
    
    // Disposable 구현
    override fun dispose() {
        settings.removeListener(this)
    }
}
