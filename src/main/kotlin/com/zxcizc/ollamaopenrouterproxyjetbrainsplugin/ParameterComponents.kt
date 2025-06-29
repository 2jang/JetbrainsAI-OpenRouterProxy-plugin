package com.zxcizc.ollamaopenrouterproxyjetbrainsplugin

import com.intellij.ui.components.*
import com.intellij.ui.DocumentAdapter
import com.intellij.util.ui.JBUI
import com.intellij.ui.JBColor
import java.awt.*
import java.awt.event.ActionListener
import javax.swing.*
import javax.swing.event.ChangeEvent
import javax.swing.event.ChangeListener
import javax.swing.event.DocumentEvent

/**
 * 슬라이더 + 텍스트 필드 조합 컴포넌트
 * 실수 값 입력을 위한 UI
 */
class FloatSliderComponent(
    private val definition: ParameterDefinition,
    private val onValueChanged: (Double) -> Unit
) : JPanel(BorderLayout()) {
    
    private val slider = JSlider(0, 1000)
    private val textField = JBTextField(8)
    private val resetButton = JButton("↺")
    
    private val range = ParameterUtils.parseDoubleRange(definition.range ?: "0.0 - 1.0")
    private var isUpdating = false
    
    init {
        setupComponents()
        setupLayout()
        setupListeners()
        
        // 기본값 설정
        val defaultValue = (definition.defaultValue as? Double) ?: range.first
        setValue(defaultValue)
    }
    
    private fun setupComponents() {
        // 슬라이더 설정
        slider.paintTicks = true
        slider.paintLabels = false
        slider.majorTickSpacing = 250
        slider.minorTickSpacing = 100
        
        // 텍스트 필드 설정
        textField.preferredSize = Dimension(80, textField.preferredSize.height)
        textField.horizontalAlignment = JTextField.CENTER
        definition.hint?.let { textField.toolTipText = "💡 $it" }
        
        // 리셋 버튼 설정
        resetButton.preferredSize = Dimension(30, 25)
        resetButton.toolTipText = "Reset to default (${definition.defaultValue})"
        resetButton.font = resetButton.font.deriveFont(Font.PLAIN, 12f)
    }
    
    private fun setupLayout() {
        val controlPanel = JPanel(BorderLayout())
        controlPanel.add(textField, BorderLayout.CENTER)
        controlPanel.add(resetButton, BorderLayout.EAST)
        
        add(slider, BorderLayout.CENTER)
        add(controlPanel, BorderLayout.EAST)
        
        border = JBUI.Borders.empty(2)
    }
    
    private fun setupListeners() {
        // 슬라이더 변경 리스너
        slider.addChangeListener { 
            if (!isUpdating) {
                val value = sliderToValue(slider.value)
                updateTextField(value)
                notifyValueChanged(value)
            }
        }
        
        // 텍스트 필드 변경 리스너
        textField.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                if (!isUpdating) {
                    val text = textField.text
                    val value = parseTextValue(text)
                    if (value != null) {
                        updateSlider(value)
                        notifyValueChanged(value)
                        updateFieldAppearance(true)
                    } else {
                        updateFieldAppearance(false)
                    }
                }
            }
        })
        
        // 리셋 버튼 리스너
        resetButton.addActionListener {
            val defaultValue = (definition.defaultValue as? Double) ?: range.first
            setValue(defaultValue)
        }
    }
    
    private fun sliderToValue(sliderPos: Int): Double {
        return ParameterUtils.sliderToValue(sliderPos, range)
    }
    
    private fun valueToSlider(value: Double): Int {
        return ParameterUtils.valueToSlider(value, range)
    }
    
    private fun parseTextValue(text: String): Double? {
        return try {
            val value = text.toDouble()
            if (value in range.first..range.second) value else null
        } catch (e: NumberFormatException) {
            null
        }
    }
    
    private fun updateSlider(value: Double) {
        isUpdating = true
        slider.value = valueToSlider(value)
        isUpdating = false
    }
    
    private fun updateTextField(value: Double) {
        isUpdating = true
        textField.text = String.format("%.2f", value)
        isUpdating = false
    }
    
    private fun updateFieldAppearance(valid: Boolean) {
        textField.background = if (valid) {
            UIManager.getColor("TextField.background")
        } else {
            JBColor(0xFFE6E6, 0x5D2B2B) // 라이트: 연한 빨강, 다크: 어두운 빨강
        }
    }
    
    private fun notifyValueChanged(value: Double) {
        // 기본값과 다른지 확인해서 UI 스타일 변경
        val isDefault = (definition.defaultValue as? Double) == value
        updateNonDefaultStyle(!isDefault)
        
        onValueChanged(value)
    }
    
    private fun updateNonDefaultStyle(isNonDefault: Boolean) {
        val bgColor = if (isNonDefault) {
            JBColor(0xFFF3E0, 0x5D4037) // 주황빛 배경
        } else {
            UIManager.getColor("Panel.background")
        }
        background = bgColor
        slider.background = bgColor
    }
    
    fun setValue(value: Double) {
        isUpdating = true
        val clampedValue = value.coerceIn(range.first, range.second)
        updateSlider(clampedValue)
        updateTextField(clampedValue)
        updateFieldAppearance(true)
        updateNonDefaultStyle((definition.defaultValue as? Double) != clampedValue)
        isUpdating = false
    }
    
    fun getValue(): Double {
        return parseTextValue(textField.text) ?: (definition.defaultValue as? Double) ?: range.first
    }
}

/**
 * 정수 스피너 컴포넌트
 * 정수 값 입력을 위한 UI
 */
class IntegerSpinnerComponent(
    private val definition: ParameterDefinition,
    private val onValueChanged: (Int?) -> Unit
) : JPanel(BorderLayout()) {
    
    private val spinner: JSpinner
    private val checkBox = JCheckBox("Enable")
    private val resetButton = JButton("↺")
    
    private val range = if (definition.range != null) {
        ParameterUtils.parseIntRange(definition.range!!)
    } else {
        Pair(0, 10000)
    }
    
    private var isUpdating = false
    
    init {
        // 스피너 모델 생성
        val model = if (definition.defaultValue == null) {
            // nullable 정수용 (max_tokens, seed 등)
            SpinnerNumberModel(1, range.first, range.second, 1)
        } else {
            // 기본값이 있는 정수용 (top_k 등)
            SpinnerNumberModel(definition.defaultValue as Int, range.first, range.second, 1)
        }
        
        spinner = JSpinner(model)
        
        setupComponents()
        setupLayout()
        setupListeners()
        
        // 초기 상태 설정
        if (definition.defaultValue == null) {
            checkBox.isSelected = false
            spinner.isEnabled = false
        } else {
            checkBox.isVisible = false
        }
    }
    
    private fun setupComponents() {
        // 스피너 설정
        (spinner.editor as JSpinner.DefaultEditor).textField.preferredSize = 
            Dimension(80, spinner.preferredSize.height)
        (spinner.editor as JSpinner.DefaultEditor).textField.horizontalAlignment = JTextField.CENTER
        
        // 체크박스 설정 (nullable 값용)
        if (definition.defaultValue == null) {
            checkBox.text = "Enable ${definition.displayName}"
            definition.hint?.let { checkBox.toolTipText = "💡 $it" }
        }
        
        // 리셋 버튼 설정
        resetButton.preferredSize = Dimension(30, 25)
        resetButton.toolTipText = "Reset to default"
        resetButton.font = resetButton.font.deriveFont(Font.PLAIN, 12f)
    }
    
    private fun setupLayout() {
        val controlPanel = JPanel(BorderLayout())
        
        if (definition.defaultValue == null) {
            // nullable 값: 체크박스 + 스피너 + 리셋
            controlPanel.add(checkBox, BorderLayout.WEST)
            controlPanel.add(spinner, BorderLayout.CENTER)
            controlPanel.add(resetButton, BorderLayout.EAST)
        } else {
            // 기본값이 있는 값: 스피너 + 리셋
            controlPanel.add(spinner, BorderLayout.CENTER)
            controlPanel.add(resetButton, BorderLayout.EAST)
        }
        
        add(controlPanel, BorderLayout.CENTER)
        border = JBUI.Borders.empty(2)
    }
    
    private fun setupListeners() {
        // 스피너 변경 리스너
        spinner.addChangeListener {
            if (!isUpdating) {
                notifyValueChanged()
            }
        }
        
        // 체크박스 리스너 (nullable 값용)
        if (definition.defaultValue == null) {
            checkBox.addActionListener {
                spinner.isEnabled = checkBox.isSelected
                if (!checkBox.isSelected) {
                    // 비활성화시 기본값으로 설정
                    isUpdating = true
                    spinner.value = 1
                    isUpdating = false
                }
                notifyValueChanged()
            }
        }
        
        // 리셋 버튼 리스너
        resetButton.addActionListener {
            setValue(definition.defaultValue as? Int)
        }
    }
    
    private fun notifyValueChanged() {
        val value = getCurrentValue()
        
        // 기본값과 다른지 확인해서 UI 스타일 변경
        val isDefault = value == definition.defaultValue
        updateNonDefaultStyle(!isDefault)
        
        onValueChanged(value)
    }
    
    private fun updateNonDefaultStyle(isNonDefault: Boolean) {
        val bgColor = if (isNonDefault) {
            JBColor(0xFFF3E0, 0x5D4037) // 주황빛 배경
        } else {
            UIManager.getColor("Panel.background")
        }
        background = bgColor
    }
    
    private fun getCurrentValue(): Int? {
        return if (definition.defaultValue == null) {
            // nullable 값: 체크박스가 선택되어야 함
            if (checkBox.isSelected) spinner.value as Int else null
        } else {
            // 기본값이 있는 값: 항상 유효
            spinner.value as Int
        }
    }
    
    fun setValue(value: Int?) {
        isUpdating = true
        
        if (definition.defaultValue == null) {
            // nullable 값 처리
            if (value == null) {
                checkBox.isSelected = false
                spinner.isEnabled = false
                spinner.value = 1
            } else {
                checkBox.isSelected = true
                spinner.isEnabled = true
                spinner.value = value.coerceIn(range.first, range.second)
            }
        } else {
            // 기본값이 있는 값 처리
            spinner.value = (value ?: (definition.defaultValue as Int)).coerceIn(range.first, range.second)
        }
        
        updateNonDefaultStyle(value != definition.defaultValue)
        isUpdating = false
    }
    
    fun getValue(): Int? {
        return getCurrentValue()
    }
}

/**
 * 파라미터 레이블 컴포넌트 (툴팁 포함)
 */
class ParameterLabelComponent(private val definition: ParameterDefinition) : JBLabel() {
    
    init {
        setupLabel()
        setupTooltip()
    }
    
    private fun setupLabel() {
        val labelText = buildString {
            append("<html><b>${definition.displayName}</b>")
            definition.range?.let { range ->
                append("<br><small><i>Range: $range</i></small>")
            }
            definition.defaultValue?.let { default ->
                append("<br><small>Default: $default</small>")
            }
            append("</html>")
        }
        text = labelText
    }
    
    private fun setupTooltip() {
        val tooltipText = buildString {
            append("<html><div style='width:350px'>")
            append("<b>${definition.displayName}</b><br><br>")
            append("${definition.description}<br><br>")
            
            definition.range?.let { append("<b>Range:</b> $it<br>") }
            definition.defaultValue?.let { append("<b>Default:</b> $it<br>") }
            definition.hint?.let { append("<br><i>💡 Hint: $it</i><br>") }
            definition.explainerVideo?.let { 
                append("<br>📺 <a href='$it'>Watch explainer video</a>") 
            }
            
            append("</div></html>")
        }
        toolTipText = tooltipText
    }
}
