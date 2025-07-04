package com.zxcizc.ollamaopenrouterproxyjetbrainsplugin

import com.intellij.util.xmlb.annotations.Tag

/**
 * Represents a set of parameters for an API request.
 * Properties are nullable to indicate that a parameter might not be set.
 */
@Tag("ParameterPreset")
data class ParameterPreset(
    // Sampling
    var temperature: Double? = null,
    var topP: Double? = null,
    var topK: Int? = null,
    var minP: Double? = null,
    var topA: Double? = null,
    var seed: Int? = null,

    // Repetition Control
    var frequencyPenalty: Double? = null,
    var presencePenalty: Double? = null,
    var repetitionPenalty: Double? = null,

    // Output Control
    var maxTokens: Int? = null,
    var stop: MutableList<String>? = null,
    var responseFormatType: String? = null, // "json_object"

    // Tools
    @Tag("toolsJson") // XML 태그 이름을 명확하게 지정
    var toolsJson: String? = null,
    @Tag("toolChoiceJson")
    var toolChoiceJson: String? = null,

    // Advanced
    @Tag("logitBiasJson")
    var logitBiasJson: String? = null,
    var logprobs: Boolean? = null,
    var topLogprobs: Int? = null
) {
    // A no-arg constructor is required for XML serialization by IntelliJ platform
    constructor() : this(temperature = null)
}