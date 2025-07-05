# 🚀 JetbrainsAI-OpenRouterProxy-plugin: AI Assistant를 위한 OpenRouter 프록시

[![JetBrains Plugin](https://img.shields.io/badge/JetBrains-Plugin-000000?style=for-the-badge&logo=jetbrains&logoColor=white)](https://plugins.jetbrains.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.1.0-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![IntelliJ Platform](https://img.shields.io/badge/IntelliJ%20Platform-2025.1.3-000000?style=for-the-badge&logo=intellijidea&logoColor=white)](https://plugins.jetbrains.com/)
[![Gradle](https://img.shields.io/badge/Gradle-8.13-02303A?style=for-the-badge&logo=gradle&logoColor=white)](https://gradle.org/)
[![License](https://img.shields.io/badge/License-GPL--3.0-blue?style=for-the-badge)](LICENSE)

**JetbrainsAI-OpenRouterProxy-plugin**은 JetBrains IDE의 **AI Assistant**가 강력한 **OpenRouter.ai**의 클라우드 AI 모델들을 직접 사용할 수 있도록 연결해주는 스마트 프록시 플러그인입니다. 이 플러그인을 통해 개발자는 코딩 중 AI Assistant에서 GPT, Claude, Gemini 등 100개 이상의 최신 모델을 자유롭게 선택하고 활용할 수 있습니다.

<p align="center">
    <br>
    <img src="https://github.com/user-attachments/assets/d16313a3-b31e-4698-814a-81646a3589d9">
    <br>
</p>

## ✨ 주요 기능

- **OpenRouter 모델 통합**:
    - AI Assistant의 모델 목록에 OpenRouter.ai가 제공하는 100개 이상의 클라우드 모델을 완벽하게 통합합니다.
    - 실시간 모델 목록 동기화 및 캐싱을 지원합니다.
- **실시간 파라미터 제어**:
    - 코딩 중에도 Temperature, Top-P 등 AI 응답 스타일을 즉시 변경할 수 있습니다.
    - 시스템 프롬프트를 주입하여 AI의 역할을 지정할 수 있습니다.
- **프리셋 시스템**:
    - 창작, 코딩, 분석 등 작업별 최적화된 AI 설정을 미리 저장하고 쉽게 전환할 수 있습니다.
- **편리한 관리 기능**:
    - 사용할 모델만 선택적으로 노출하는 화이트리스트 기능을 제공합니다.
    - IDE 우측 도구 창에서 모든 기능을 직관적으로 제어할 수 있습니다.
- **로컬 모델 지원 (선택 사항)**:
    - 로컬 Ollama 서버가 있는 경우, 클라우드 모델과 로컬 모델을 원클릭으로 전환하며 사용할 수 있습니다.

## 🏗️ 프로젝트 구조

~~~
JetbrainsAI-OpenRouterProxy-plugin/
├── src/main/kotlin/com/zxcizc/ollamaopenrouterproxyjetbrainsplugin/
│   ├── ProxyServer.kt                    # 핵심 프록시 서버 구현
│   ├── PluginSettingsState.kt           # 설정 상태 관리
│   ├── PluginSettingsComponent.kt       # 설정 UI 컴포넌트
│   ├── PluginSettingsConfigurable.kt    # 설정 페이지 통합
│   ├── ProxyControlToolWindowFactory.kt # 도구 창 팩토리
│   └── ... (기타 컴포넌트)
├── src/main/resources/
│   └── META-INF/
│       └── plugin.xml                   # 플러그인 설정
├── build.gradle.kts                     # Gradle 빌드 설정
└── README.md
~~~

## 🚀 설치 및 실행 방법

### 준비물
- JetBrains IDE (IntelliJ IDEA, PyCharm 등 2024.1+)
- Java 21+
- OpenRouter.ai API 키

### 설치 단계
1.  **플러그인 빌드**
    ~~~bash
    git clone https://github.com/zxcizc/JetbrainsAI-OpenRouterProxy-plugin.git
    cd JetbrainsAI-OpenRouterProxy-plugin
    ./gradlew buildPlugin
    ~~~

2.  **플러그인 설치**
    IDE에서 `File → Settings → Plugins → Install Plugin from Disk`를 선택하고 `build/libs/` 폴더에 생성된 `.jar` 파일을 선택합니다.

3.  **기본 설정**
    - **Tools → JetbrainsAI OpenRouter Proxy Settings**로 이동합니다.
    - **OpenRouter API 키**를 입력합니다.
    - (선택) 사용하고 싶은 모델들을 화이트리스트에 추가합니다.

4.  **AI Assistant 연동**
   **Tools → AI Assistant → Models**에서 **Ollama URL**을 `http://localhost:11444`로 설정합니다. (이 플러그인이 해당 주소에서 프록시 서버 역할을 합니다.)

## 🛠️ 사용된 주요 기술

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

## 🤝 기여하기

언제든지 버그 리포트, 기능 제안, Pull Request를 환영합니다!

1.  저장소 포크하기
2.  기능 브랜치 생성하기 (`git checkout -b feature/AmazingFeature`)
3.  변경사항 커밋하기 (`git commit -m 'Add some AmazingFeature'`)
4.  브랜치에 푸시하기 (`git push origin feature/AmazingFeature`)
5.  Pull Request 열기

## 📝 라이선스

이 프로젝트는 [GPL-3.0 라이선스](LICENSE) 하에 배포됩니다.

---

⭐ 이 플러그인이 개발 워크플로우를 개선하는 데 도움이 되셨다면 Star를 눌러주세요! ⭐