# QuickChat

**A private, Material 3 Expressive Android chat app for multiple AI providers — cloud and on-device — with streaming, local tools, and user-created skills.**

![Kotlin](https://img.shields.io/badge/kotlin-%230095D5.svg?style=flat-square&logo=kotlin&logoColor=white)
![Android API](https://img.shields.io/badge/API-31%2B-brightgreen.svg?style=flat-square&logo=android)
![Material Design 3](https://img.shields.io/badge/UI-Material_3_Expressive-blueviolet.svg?style=flat-square)
![License](https://img.shields.io/github/license/lciszewski27/quick-chat?style=flat-square)

Chat with Gemini, OpenAI, Claude, OpenRouter, DeepSeek, any OpenAI-compatible endpoint, or fully offline with on-device Gemini Nano. History, favorites, API keys, and skills stay on your device; only your messages go to the selected provider.

> [!CAUTION]
> QuickChat is in **beta** and can use **more tokens than expected**: agentic tool loops (up to 5 rounds per reply), retries, skills, and growing chat history all add up — keep an eye on usage if your API key is billed per token. On-device Gemini Nano uses no cloud tokens at all.

---

## Features

*   **Many providers, one client:** Google AI Studio (Gemini via its OpenAI-compatible endpoint), OpenAI, Claude (native Anthropic Messages API), OpenRouter, DeepSeek, and any number of custom OpenAI-compatible endpoints (Ollama, LM Studio, proxies). One shared OkHttp-based streaming SDK speaks OpenAI-protocol SSE plus Anthropic SSE, so adding a backend is one `ProviderConfig`.
*   **On-device Gemini Nano:** where supported (AICore devices), add the on-device provider — no API key, private, works offline. Model download with progress is managed in Settings; availability is checked per device.
*   **Streaming replies:** token-by-token rendering with a Stop button, live tok/s, and average tok/s persisted per message (token counts are estimated at ~4 chars/token — providers don't report per-chunk usage).
*   **Markdown replies:** bold, lists, code, tables via a Material 3 markdown renderer, with headings capped to bubble scale. `<thought>`/`<think>` blocks render collapsed and expandable; the live stream stays plain text to avoid re-parse flicker.
*   **History sidebar:** modal drawer on compact windows, permanent pane on wide windows. Search, rename, and delete conversations.
*   **Favorites-only model picker:** catalogs like OpenRouter's are too big to scroll in chat — star favorites in Settings and pick from those, grouped by provider. Model catalogs are always fetched live; nothing is hardcoded.
*   **Local tools the assistant can call:** current time, calculator (safe built-in evaluator), and sandboxed JavaScript (QuickJS, no fetch/timers/filesystem — plus `base64` and a controlled `request()` helper). Tool calls stream through an agentic loop (up to 5 rounds, both protocols), appear as expandable timeline cards with arguments, results, and durations, and Gemini 3 `thought_signature` values round-trip verbatim so multi-turn tool use validates.
*   **User skills:** ask the assistant to "make a skill that …" and it writes a persistent JavaScript skill via `create_skill`. Skills appear for every provider, can declare secret parameters whose values are set only in Settings and injected at runtime (the model only ever sees secret *names*), and stay hidden until they pass a test run (Settings → Skills: list, code editor, test gate, secrets editor).
*   **Modern UI/UX:** Material 3 Expressive with Material You dynamic colors, light/dark/system themes, AMOLED black mode, 4 color presets, Quicksand/system font choice, and an animations toggle. Multiline input, selectable message text, copy buttons on model replies.
*   **Settings:** Appearance (copied from the same design system as Where Is My Money), Providers & Keys, Models & Favorites (fetch + search), Skills, General (temperature, system prompt), About.

## Privacy

* No accounts, no cloud sync, no analytics or crash reporting.
* Chats, favorites, API keys, skills, and secrets never leave your device unless **you** send a message — and then only that message goes to the selected provider.
* The manifest declares `INTERNET` because cloud providers, model catalog fetches, and skill `request()` calls need network. On-device Nano inference is fully offline.

## Tech Stack & Architecture

Built with modern Android development principles, focusing on traceable dependencies.

*   **Architecture:** Clean Architecture (`data`, `domain`, `ui`) & single-activity.
*   **Dependency injection:** manual DI via the `Application` class (`QuickChatApp`) — no Dagger/Hilt/Koin.
*   **UI:** Jetpack Compose (Material 3 Expressive).
*   **Navigation:** Navigation Compose with type-safe `@Serializable` routes (`Chat`, `Settings`). Model picker and dialogs are sheets/dialogs hosted by their screens, not routes.
*   **State:** per-screen immutable `UiState` + `StateFlow`, `UiEvent` sealed interfaces. Streaming state (`streamingText`, running-tool indicator, live tok/s) lives in `ChatUiState`; persisted history comes from Room flows.
*   **AI layer (`data/ai/`):** one `AiProvider` interface (`listModels`, `streamReply: Flow<AiStreamEvent>`); `SdkProvider` implements the OpenAI-protocol and Anthropic tool loops over a single `LlmSdk` (OkHttp SSE); `AiCoreProvider` wraps ML Kit's GenAI Prompt API; `ProviderResolver` builds the right provider per user-added instance. Tools (`data/ai/tools/`: `AiTool`, time, calculator, sandboxed JS via shared `JsSandbox`) plus user skills (`data/ai/skills/`: `SkillExecutor`, `SkillTool`, `CreateSkillTool`, `ListSkillsTool`) are shared by every provider.
*   **Database:** Room (KSP) with reactive `Flow`-based DAOs and real migrations (v1→v5). Tables: `chat_sessions`, `chat_messages` (with generation stats), `favorite_models`, `provider_instances`, `tool_calls`, `skills`, `skill_secrets`.
*   **Storage & prefs:** Jetpack DataStore Preferences (appearance, selected provider/model, temperature, system prompt).
*   **Unit tests:** `AiToolsTest` (evaluator, JS sandbox incl. timeout/await-tolerance/sandbox proof), `SkillsTest` (validation, create flow, secret injection, test gate), `GenerationStatsTest`, `MessageSegmentsTest`, `AiCoreTranscriptTest`.

| Property | Value |
|---|---|
| `applicationId` | `dev.lciszewski27.quickchat` |
| `minSdk` / `targetSdk` / `compileSdk` | 31 / 37 / 37 |
| Kotlin / JVM target | 2.4.20 / 17 (AGP 9.4.1) |
| Key deps | Compose BOM + Material 3 (`1.5.0-alpha28`), Room, DataStore, Navigation Compose, kotlinx.serialization, OkHttp, multiplatform-markdown-renderer-m3 (`0.40.0`), quickjs-kt (`1.0.3`), ML Kit genai-prompt (`1.0.0-beta2`) |

### Project structure

```
app/src/main/java/.../quickchat/
├── data/
│   ├── ai/          # AiProvider, LlmSdk (SSE), SdkProvider (tool loops),
│   │                # AiCoreProvider (Nano), Providers (configs), ProviderResolver,
│   │                # tools/ (AiTool, time, calculator, JsSandbox, run_javascript),
│   │                # skills/ (SkillExecutor, SkillTool, CreateSkillTool, ListSkillsTool)
│   ├── local/       # AppDatabase (+ migrations), DAOs, entities, preferences
│   └── repository/  # ChatRepositoryImpl
├── domain/          # Pure-Kotlin models (chat, providers, skills, favorites),
│                    # ChatRepository interface
└── ui/              # Compose: chat/ (screen, ViewModel, timeline, markdown),
                     # settings/ (+ pages/), components/, navigation/, theme/
```

## Build & Run

### Prerequisites

*   A recent Android Studio version (AGP 9.x is required)
*   JDK 17
*   An Android device/emulator running API 31+ (AICore/Nano needs a supported device with Android 14+)

### Quick start

```bash
# 1. Clone the repository
git clone https://github.com/lciszewski27/quickchat.git
cd quickchat

# 2. Build the debug APK
./gradlew assembleDebug

# 3. Run unit tests
./gradlew :app:testDebugUnitTest

# 4. Run on a connected device
./gradlew installDebug
```

To use cloud providers, add your API keys in Settings → Providers & Keys (keys stay on-device). For on-device chat, add the Gemini Nano provider on a supported device and download the model.

---

## Contributing

Issues and pull requests are welcome. Please keep the manual-DI, local-first design: don't add DI frameworks, and be careful changing Room entity fields — they affect the database schema and need a tested migration (see `MIGRATION_4_5` and the `tool_calls` index lesson: Room validates migrated schemas exactly).

## License

This project is licensed under the **GNU General Public License v3.0** — see [LICENSE](./LICENSE).
