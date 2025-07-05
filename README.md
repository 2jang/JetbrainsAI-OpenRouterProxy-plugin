# 🚀 JetbrainsAI-OpenRouterProxy-plugin

[![JetBrains Plugin](https://img.shields.io/badge/JetBrains-Plugin-000000?style=for-the-badge&logo=jetbrains&logoColor=white)](https://plugins.jetbrains.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.1.0-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![IntelliJ Platform](https://img.shields.io/badge/IntelliJ%20Platform-2025.1.3-000000?style=for-the-badge&logo=intellijidea&logoColor=white)](https://plugins.jetbrains.com/)
[![Gradle](https://img.shields.io/badge/Gradle-8.13-02303A?style=for-the-badge&logo=gradle&logoColor=white)](https://gradle.org/)

<p>:earth_americas: <a href="https://github.com/2jang/ollama-chutesai-proxy/blob/main/README-ko.md">한국어</a> | <a href="https://github.com/2jang/ollama-chutesai-proxy">English</a></p>

**JetbrainsAI-OpenRouterProxy-plugin** is a proxy plugin that connects the **AI Assistant** in JetBrains IDEs to the cloud-based AI models of **OpenRouter.ai**. This allows developers to freely choose and utilize over 100 of the latest models, such as GPT, Claude, and Gemini, directly within the Jetbrains AI Assistant during their coding workflow.

## ✨ Key Features

- **OpenRouter Model Integration**:
    - Seamlessly integrates over 100 cloud models from OpenRouter.ai into the AI Assistant's model list.
- **Real-time Parameter Control**:
    - Adjust AI response parameters like Temperature and Top-P in real-time from the IDE's tool window.
    - Customizable system prompts.
- **Preset System**:
    - Save and easily switch between optimized AI settings for different tasks like creative writing, coding, and analysis.
- **Convenient Management**:
    - Whitelist feature to selectively expose only the models you want to use.
    - Check the status of your OpenRouter API key.
- **Local Model Support**:
    - If you have a local Ollama server, you can use both cloud and local models simultaneously.

## 🏗️ Project Structure

~~~
JetbrainsAI-OpenRouterProxy-plugin/
├── src/main/kotlin/com/zxcizc/ollamaopenrouterproxyjetbrainsplugin/
│   ├── ProxyServer.kt                    # Core proxy server implementation
│   ├── PluginSettingsState.kt           # Manages plugin settings
│   ├── PluginSettingsComponent.kt       # UI components for settings
│   ├── PluginSettingsConfigurable.kt    # Integrates with the settings page
│   └── ProxyControlToolWindowFactory.kt # Factory for the tool window
├── src/main/resources/
│   └── META-INF/
│       └── plugin.xml                   # Plugin configuration
└── build.gradle.kts                     # Gradle build configuration
~~~

## 🚀 Installation and Setup

### Prerequisites
- JetBrains IDE (IntelliJ IDEA, PyCharm, etc. 2024.1+)
- Java 21+
- OpenRouter.ai API Key

### Installation Steps
1.  **Build the Plugin**
    ~~~bash
    git clone https://github.com/zxcizc/JetbrainsAI-OpenRouterProxy-plugin.git
    cd JetbrainsAI-OpenRouterProxy-plugin
    ./gradlew buildPlugin
    ~~~

2.  **Install the Plugin**
    In your IDE, go to `File → Settings → Plugins → Install Plugin from Disk` and select the `.jar` file from the `build/libs/` directory.

3.  **Initial Setup**
    - Navigate to **Tools → JetbrainsAI OpenRouter Proxy Settings**.
    - Enter your **OpenRouter API Key**.
    - (Optional) Add your desired models to the whitelist.

4.  **AI Assistant Integration**
   Go to **Tools → AI Assistant → Models** and set the **Ollama URL** to `http://localhost:11444`. (This plugin acts as a proxy server at this address).

## 🛠️ Key Technologies Used

-   **Core**:
    -   Kotlin (v2.1.0)
    -   Java HttpServer
-   **Plugin Framework**:
    -   IntelliJ Platform SDK (v2025.1.3)
-   **Libraries**:
    -   Gson (v2.10.1)
    -   Kotlin Coroutines
-   **Build Tool**:
    -   Gradle (v8.13)

## 🤝 Contributing

Bug reports, feature suggestions, and pull requests are always welcome!

1.  Fork the repository
2.  Create your feature branch (`git checkout -b feature/AmazingFeature`)
3.  Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4.  Push to the branch (`git push origin feature/AmazingFeature`)
5.  Open a Pull Request

## 📝 License

This project is distributed under the [GPL-3.0 License](LICENSE).

---

⭐ If you find this plugin helpful for your development workflow, please give it a star! ⭐