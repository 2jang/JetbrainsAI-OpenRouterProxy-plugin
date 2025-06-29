package com.zxcizc.ollamaopenrouterproxyjetbrainsplugin

import com.google.gson.Gson
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.Disposable
import com.intellij.ui.components.*
import com.intellij.ui.SearchTextField
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.CollectionListModel
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import com.intellij.ui.JBColor
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
    private val tabbedPane = JTabbedPane()
    
    // 기존 모델 관리 관련 컴포넌트들
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
    
    // 파라미터 관련 컴포넌트들
    private val parametersEnabledCheckBox = JCheckBox("Enable Custom Parameters")
    private val temperatureComponent: FloatSliderComponent
    private val maxTokensComponent: IntegerSpinnerComponent
    private val topPComponent: FloatSliderComponent
    private val topKComponent: IntegerSpinnerComponent
    
    // 전체 모델 목록 (필터링용)
    private val allAvailableModels = mutableListOf<String>()
    private val settings = PluginSettingsState.getInstance()

    init {
        // 설정 변경 리스너 등록
        settings.addListener(this)
        
        // 파라미터 컴포넌트들 초기화
        temperatureComponent = FloatSliderComponent(
            ParameterDefinitions.CORE_PARAMETERS.find { it.key == "temperature" }!!
        ) { value -> 
            if (settings.parametersEnabled) {
                settings.paramTemperature = value
                settings.notifyParametersChanged()
            }
        }
        
        maxTokensComponent = IntegerSpinnerComponent(
            ParameterDefinitions.CORE_PARAMETERS.find { it.key == "max_tokens" }!!
        ) { value ->
            if (settings.parametersEnabled) {
                settings.paramMaxTokens = value
                settings.notifyParametersChanged()
            }
        }
        
        topPComponent = FloatSliderComponent(
            ParameterDefinitions.CORE_PARAMETERS.find { it.key == "top_p" }!!
        ) { value ->
            if (settings.parametersEnabled) {
                settings.paramTopP = value
                settings.notifyParametersChanged()
            }
        }
        
        topKComponent = IntegerSpinnerComponent(
            ParameterDefinitions.CORE_PARAMETERS.find { it.key == "top_k" }!!
        ) { value ->
            if (settings.parametersEnabled) {
                settings.paramTopK = value ?: 0
                settings.notifyParametersChanged()
            }
        }
        
        // 기존 설정들
        setupExistingComponents()
        setupModelLists()
        setupButtons()
        setupSearchFields()
        
        // 파라미터 컴포넌트 설정
        setupParameterComponents()
        
        // 탭 패널 생성
        panel = createTabbedPanel()
        
        // 초기 상태 설정
        loadInitialSettings()
    }
    
    private fun setupExistingComponents() {
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
    }
    
    private fun setupParameterComponents() {
        // 파라미터 활성화 체크박스
        parametersEnabledCheckBox.addActionListener {
            val enabled = parametersEnabledCheckBox.isSelected
            settings.parametersEnabled = enabled
            updateParameterComponentsEnabled(enabled)
            if (enabled) {
                loadParametersFromSettings()
            }
        }
        
        // 초기 활성화 상태 설정
        parametersEnabledCheckBox.isSelected = settings.parametersEnabled
        updateParameterComponentsEnabled(settings.parametersEnabled)
    }
    
    private fun updateParameterComponentsEnabled(enabled: Boolean) {
        temperatureComponent.isEnabled = enabled
        maxTokensComponent.isEnabled = enabled
        topPComponent.isEnabled = enabled
        topKComponent.isEnabled = enabled
        
        // 비활성화시 컴포넌트 색상 변경
        val components = listOf(temperatureComponent, maxTokensComponent, topPComponent, topKComponent)
        components.forEach { component ->
            component.background = if (enabled) {
                UIManager.getColor("Panel.background")
            } else {
                JBColor.LIGHT_GRAY
            }
        }
    }
    
    private fun createTabbedPanel(): JPanel {
        val mainPanel = JPanel(BorderLayout())
        
        // 공통 설정 (탭 외부)
        val commonPanel = createCommonSettingsPanel()
        mainPanel.add(commonPanel, BorderLayout.NORTH)
        
        // 탭 패널
        tabbedPane.addTab("🔧 Models", createModelsPanel())
        tabbedPane.addTab("⚙️ Parameters", createParametersPanel())
        
        mainPanel.add(tabbedPane, BorderLayout.CENTER)
        
        return mainPanel
    }
    
    private fun createCommonSettingsPanel(): JPanel {
        // 설정 안내 패널
        val guidancePanel = JPanel(BorderLayout())
        guidancePanel.border = JBUI.Borders.compound(
            JBUI.Borders.empty(5, 10),
            JBUI.Borders.customLine(JBColor(0xB3D7FF, 0x4A90E2), 1)
        )
        guidancePanel.background = JBColor(0xE8F4FD, 0x2D3748)
        guidancePanel.isOpaque = true
        
        val guidanceLabel = JBLabel("""
            <html>
            <b>📋 Setup Guide:</b><br>
            1. Go to <b>Tools → AI Assistant → Models</b><br>
            2. Set Ollama URL to: <b>http://localhost:11444</b><br>
            3. Configure your OpenRouter API key below<br>
            4. The proxy will route requests between local Ollama and OpenRouter
            </html>
        """.trimIndent())
        guidanceLabel.border = JBUI.Borders.empty(10)
        guidancePanel.add(guidanceLabel, BorderLayout.CENTER)
        
        // API 키 입력 패널 생성
        val apiKeyPanel = JPanel(BorderLayout())
        apiKeyPanel.add(openRouterApiKeyField, BorderLayout.CENTER)
        apiKeyPanel.add(apiKeyStatusLabel, BorderLayout.SOUTH)
        
        val commonPanel = FormBuilder.createFormBuilder()
            .addComponent(guidancePanel, 1)
            .addVerticalGap(10)
            .addLabeledComponent(JBLabel("Enter your OpenRouter API Key:"), apiKeyPanel, 1, false)
            .addLabeledComponent(JBLabel("Ollama Base URL:"), ollamaBaseUrlField, 1, false)
            .addComponent(enableProxyCheckBox, 1)
            .addComponent(enableDebugLoggingCheckBox, 1)
            .panel
            
        return commonPanel
    }
    
    private fun createModelsPanel(): JPanel {
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
    
    private fun createParametersPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        
        // 상단 컨트롤
        val topPanel = JPanel(FlowLayout(FlowLayout.LEFT))
        topPanel.add(parametersEnabledCheckBox)
        
        val saveButton = JButton("💾 Save")
        val resetButton = JButton("🔄 Reset")
        val presetButton = JButton("📋 Presets")
        
        saveButton.addActionListener { saveParametersToSettings() }
        resetButton.addActionListener { resetParametersToDefaults() }
        presetButton.addActionListener { showPresetMenu(presetButton) }
        
        topPanel.add(Box.createHorizontalStrut(20))
        topPanel.add(saveButton)
        topPanel.add(resetButton)
        topPanel.add(presetButton)
        
        // 파라미터 폼
        val formPanel = createParameterForm()
        
        panel.add(topPanel, BorderLayout.NORTH)
        panel.add(formPanel, BorderLayout.CENTER)
        
        return panel
    }
    
    private fun createParameterForm(): JPanel {
        val formBuilder = FormBuilder.createFormBuilder()
        
        // 섹션 제목
        formBuilder.addComponent(JBLabel("<html><h3>🎯 Core Generation Parameters</h3></html>"))
        formBuilder.addVerticalGap(10)
        
        // Temperature
        val tempDef = ParameterDefinitions.CORE_PARAMETERS.find { it.key == "temperature" }!!
        formBuilder.addLabeledComponent(
            ParameterLabelComponent(tempDef),
            temperatureComponent,
            1, false
        )
        
        // Max Tokens
        val maxTokensDef = ParameterDefinitions.CORE_PARAMETERS.find { it.key == "max_tokens" }!!
        formBuilder.addLabeledComponent(
            ParameterLabelComponent(maxTokensDef),
            maxTokensComponent,
            1, false
        )
        
        // Top P
        val topPDef = ParameterDefinitions.CORE_PARAMETERS.find { it.key == "top_p" }!!
        formBuilder.addLabeledComponent(
            ParameterLabelComponent(topPDef),
            topPComponent,
            1, false
        )
        
        // Top K
        val topKDef = ParameterDefinitions.CORE_PARAMETERS.find { it.key == "top_k" }!!
        formBuilder.addLabeledComponent(
            ParameterLabelComponent(topKDef),
            topKComponent,
            1, false
        )
        
        formBuilder.addVerticalGap(20)
        
        // 정보 패널
        val infoPanel = JPanel(BorderLayout())
        infoPanel.border = JBUI.Borders.compound(
            JBUI.Borders.empty(10),
            JBUI.Borders.customLine(JBColor(0xD1C4E9, 0x512DA8), 1)
        )
        infoPanel.background = JBColor(0xF3E5F5, 0x4A148C)
        infoPanel.isOpaque = true
        
        val infoLabel = JBLabel("""
            <html>
            <b>ℹ️ Parameter Information:</b><br><br>
            • Hover over parameter labels for detailed explanations<br>
            • Orange background indicates non-default values<br>
            • Parameters only apply when enabled<br>
            • More advanced parameters coming in future updates
            </html>
        """.trimIndent())
        infoLabel.border = JBUI.Borders.empty(10)
        infoPanel.add(infoLabel, BorderLayout.CENTER)
        
        formBuilder.addComponent(infoPanel)
        formBuilder.addComponentFillVertically(JPanel(), 0)
        
        return formBuilder.panel
    }
    
    private fun loadInitialSettings() {
        enableProxyCheckBox.isSelected = settings.isProxyEnabled
        ollamaBaseUrlField.text = settings.ollamaBaseUrl
        
        // API 키 상태 초기화
        updateApiKeyStatus(String(openRouterApiKeyField.password))
        
        // 파라미터 로드
        loadParametersFromSettings()
    }
    
    private fun loadParametersFromSettings() {
        temperatureComponent.setValue(settings.paramTemperature)
        maxTokensComponent.setValue(settings.paramMaxTokens)
        topPComponent.setValue(settings.paramTopP)
        topKComponent.setValue(settings.paramTopK)
    }
    
    private fun saveParametersToSettings() {
        settings.paramTemperature = temperatureComponent.getValue()
        settings.paramMaxTokens = maxTokensComponent.getValue()
        settings.paramTopP = topPComponent.getValue()
        settings.paramTopK = topKComponent.getValue() ?: 0
        
        settings.notifyParametersChanged()
        Messages.showInfoMessage("Parameters saved successfully!", "Saved")
    }
    
    private fun resetParametersToDefaults() {
        val result = Messages.showYesNoDialog(
            "Reset all parameters to their default values?",
            "Reset Parameters",
            Messages.getQuestionIcon()
        )
        
        if (result == Messages.YES) {
            settings.resetParametersToDefaults()
            loadParametersFromSettings()
            Messages.showInfoMessage("Parameters reset to defaults!", "Reset")
        }
    }
    
    private fun showPresetMenu(anchor: JComponent) {
        val popup = JBPopupFactory.getInstance().createComponentPopupBuilder(
            createPresetMenu(), null
        ).setTitle("Choose Preset")
            .setMovable(true)
            .setResizable(false)
            .createPopup()
        
        popup.showUnderneathOf(anchor)
    }
    
    private fun createPresetMenu(): JComponent {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        panel.border = JBUI.Borders.empty(5)
        
        ParameterUtils.Presets.getAllPresets().forEach { (name, preset) ->
            val button = JButton(name)
            button.addActionListener {
                settings.setGenerationParameters(preset)
                settings.lastUsedPreset = name
                loadParametersFromSettings()
                
                // 팝업이 있다면 닫기
                SwingUtilities.getWindowAncestor(button)?.let { window ->
                    if (window is JDialog) window.dispose()
                }
            }
            panel.add(button)
        }
        
        return panel
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

    private fun createDualListPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        
        // 왼쪽 패널 (사용 가능한 모델들)
        val leftPanel = JPanel(BorderLayout())
        leftPanel.border = JBUI.Borders.empty(5)
        
        val leftTopPanel = JPanel(BorderLayout())
        leftTopPanel.add(JBLabel("Available Models:"), BorderLayout.NORTH)
        leftTopPanel.add(availableModelsSearchField, BorderLayout.SOUTH)
        leftPanel.add(leftTopPanel, BorderLayout.NORTH)
        leftPanel.add(JBScrollPane(availableModelsList), BorderLayout.CENTER)
        
        // 오른쪽 패널 (선택된 모델들)
        val rightPanel = JPanel(BorderLayout())
        rightPanel.border = JBUI.Borders.empty(5)
        
        val rightTopPanel = JPanel(BorderLayout())
        rightTopPanel.add(JBLabel("Whitelisted Models:"), BorderLayout.NORTH)
        rightTopPanel.add(selectedModelsSearchField, BorderLayout.SOUTH)
        rightPanel.add(rightTopPanel, BorderLayout.NORTH)
        rightPanel.add(JBScrollPane(selectedModelsList), BorderLayout.CENTER)
        
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
        if (currentSelected.isEmpty()) {
            selectedModelsListModel.add("ℹ️ No models whitelisted - all models are available")
        } else {
            selectedModelsListModel.addAll(0, currentSelected.sorted())
        }
        
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
    
    override fun onParametersChanged() {
        // 파라미터 변경 시 UI 업데이트
        ApplicationManager.getApplication().invokeLater {
            loadParametersFromSettings()
        }
    }
    
    // Disposable 구현
    override fun dispose() {
        settings.removeListener(this)
    }
}
