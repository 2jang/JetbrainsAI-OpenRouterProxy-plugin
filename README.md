# 🚀 JetbrainsAI-OpenRouterProxy-plugin: AI 모델 프록시 플러그인 🤖

[![JetBrains Plugin](https://img.shields.io/badge/JetBrains-Plugin-000000?style=for-the-badge&logo=jetbrains&logoColor=white)](https://plugins.jetbrains.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.1.0-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![IntelliJ Platform](https://img.shields.io/badge/IntelliJ%20Platform-2025.1.3-000000?style=for-the-badge&logo=intellijidea&logoColor=white)](https://plugins.jetbrains.com/)
[![Gradle](https://img.shields.io/badge/Gradle-8.13-02303A?style=for-the-badge&logo=gradle&logoColor=white)](https://gradle.org/)
[![License](https://img.shields.io/badge/License-GPL--3.0-blue?style=for-the-badge)](LICENSE)

로컬 Ollama와 클라우드 OpenRouter.ai를 하나로! **JetbrainsAI-OpenRouterProxy-plugin**은 JetBrains IDE의 AI Assistant에서 로컬 Ollama 모델과 OpenRouter.ai의 다양한 클라우드 AI 모델을 통합하여 사용할 수 있게 해주는 스마트 프록시 플러그인입니다. 개발자들이 코딩 중에 최적의 AI 모델을 자유롭게 선택하고 활용할 수 있도록 돕습니다.

<p align="center">
    <br>
    <img src="https://github.com/user-attachments/assets/d16313a3-b31e-4698-814a-81646a3589d9">
    <br>
</p>

## ✨ 주요 기능

- **통합 모델 접근**:
    - 로컬 Ollama 모델과 OpenRouter.ai 클라우드 모델을 하나의 인터페이스로 통합
    - 실시간 모델 목록 동기화 및 캐싱
    - 프록시 모드와 바이패스 모드 간 간편한 전환
- **스마트 라우팅**:
    - 모델명 기반 자동 라우팅 (로컬 vs 클라우드)
    - OpenAI API와 Ollama API 형식 간 자동 변환
    - 스트리밍 응답 처리 지원
- **실시간 파라미터 오버라이드**:
    - Temperature, Top-P 등 샘플링 파라미터 조정
    - Frequency/Presence Penalty로 반복 제어
    - Max Tokens, Stop Sequences 등 출력 제어
- **프리셋 시스템**:
    - 자주 사용하는 파라미터 조합을 프리셋으로 저장 및 관리
    - 시스템 프롬프트 주입 기능
- **편리한 관리 기능**:
    - 사용할 모델만 선택적으로 노출하는 화이트리스트
    - IDE 우측 도구 창에서 모든 기능 제어
    - 실시간 프록시 상태 모니터링 및 원클릭 토글

## 🏗️ 프로젝트 구조

~~~
JetbrainsAI-OpenRouterProxy-plugin/
├── src/main/kotlin/com/zxcizc/ollamaopenrouterproxyjetbrainsplugin/
│   ├── ProxyServer.kt                    # 핵심 프록시 ���버 구현
│   ├── PluginSettingsState.kt           # 설정 상태 관리
│   ├── PluginSettingsComponent.kt       # 설정 UI 컴포넌트
│   ├── PluginSettingsConfigurable.kt    # 설정 페이지 통합
│   ├── ProxyControlToolWindowFactory.kt # 도구 창 팩토리
│   ├── ParameterPreset.kt               # 파라미터 프리셋 모델
│   ├── PluginStartupActivity.kt         # 플러그인 시작 활동
│   └── ... (Actions)                    # 메뉴 액션 클래스들
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
- OpenRouter.ai API 키 (선택사항)
- 로컬 Ollama 서버 (선택사항)

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
    - OpenRouter API 키를 입력합니다 (클라우드 모델 사용 시).
    - 필요한 모델들을 화이트리스트에 추가합니다.

4.  **AI Assistant 연동**
   **Tools → AI Assistant → Models**에서 Ollama URL을 `http://localhost:11444`로 설정합니다. (포트를 변경했다면 해당 포트로 설정)

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
