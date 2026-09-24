# agents.md — AI Agent Context for "QuickChat"

> This file is written **for AI coding agents and LLMs** to understand the project's architecture, conventions, and extension points. It is not a user-facing document.

---

## Project Overview

A single-activity Android AI chat app (Kotlin + Jetpack Compose + Room) with multi-provider support — cloud (Gemini, OpenAI, Claude, OpenRouter, DeepSeek, custom OpenAI-compatible) and on-device (Gemini Nano via AICore). Streaming replies, local tool calling, and persistent user-created skills. The app uses **manual dependency injection** via the `Application` subclass (no DI framework), keeping the dependency graph explicit and easy to follow for static analysis.

---

## Architecture

### Clean Architecture (3 layers + AI transport)

```
┌──────────────────────────────────────────────────────────┐
│  ui/ (Compose screens, ViewModels)                        │
│  └── ViewModel → emits UiEvent → UiState (StateFlow)      │
├──────────────────────────────────────────────────────────┤
│  domain/ (models, repository interface)                   │
│  └── Pure Kotlin — no Android dependencies                │
├──────────────────────────────────────────────────────────┤
│  data/ai/ (providers, streaming SDK, tools, skills)       │
│  └── SdkProvider / AiCoreProvider implement AiProvider    │
├──────────────────────────────────────────────────────────┤
│  data/ (Room, DAOs, entities, repository impl, prefs)     │
│  └── ChatRepositoryImpl bridges domain ↔ Room             │
└──────────────────────────────────────────────────────────┘
```

### Key data flows

1. **Chat send:** `ChatViewModel.send()` → append USER message → `resolver.resolve(instance).streamReply(...)` → collect `AiStreamEvent.Text` into `streamingText` (+ live tok/s) and `AiStreamEvent.ToolStarted/Tool` into persisted `tool_calls` → on completion append MODEL message with generation stats. Stop cancels `streamJob`; partial text is kept.
2. **Tool loop (inside `SdkProvider`):** stream round → reassemble tool calls → `runTool()` executes locally → append assistant + tool-result messages → repeat (max 5 rounds). Only text is emitted; tool activity is emitted as events the ViewModel persists.
3. **Skills:** `RepoSkillToolSource` loads tested + enabled skills per request; `SkillTool` wraps a DB row; `SkillExecutor` runs code via the shared `JsSandbox` with `args`/`secrets` globals. `create_skill` saves drafts (never active until the Settings test gate passes).
4. **Room DAO** emits `Flow<List<Entity>>` → **RepositoryImpl** maps entity↔domain → **ViewModel** collects into `StateFlow<UiState>` → **Composable** reads state and renders. Timeline merges `messages` + `toolCalls` by timestamp in `MessageList`.

### Dependency injection

All wiring lives in `QuickChatApp.onCreate()`:

```kotlin
database = AppDatabase.getInstance(this)
preferences = UserPreferencesDataStore(this)
repository = ChatRepositoryImpl(sessionDao, messageDao, favoriteDao, providerDao, toolCallDao, skillDao, skillSecretDao)
llmSdk = LlmSdk()
skillExecutor = SkillExecutor(repository)
staticTools = AiTools.defaults() + CreateSkillTool(...) + ListSkillsTool(...)
providerResolver = ProviderResolver(llmSdk, staticTools, RepoSkillToolSource(repository, skillExecutor))
```

ViewModels receive dependencies via `ViewModelProvider.Factory` lambdas in `AppNavHost.kt`. Each factory reads from `app.*`.

---

## Package Layout (app/src/main/java/.../quickchat/)

```
data/
├── ai/
│   ├── AiProvider.kt         # AiProvider iface + AiStreamEvent (Text/ToolStarted/Tool)
│   ├── LlmSdk.kt             # Shared OkHttp SSE client (postSse/get)
│   ├── SdkProvider.kt        # OpenAI-protocol + Anthropic streaming tool loops
│   ├── AiCoreProvider.kt     # On-device Nano (ML Kit GenAI Prompt API)
│   ├── Providers.kt          # ProviderConfig presets (no hardcoded models)
│   ├── ProviderResolver.kt   # Builds live provider per user-added instance
│   ├── tools/
│   │   ├── AiTool.kt         # Tool contract + AiTools.defaults()
│   │   ├── JsSandbox.kt      # Shared QuickJS sandbox (helpers, timeouts, caps)
│   │   ├── BuiltInTools.kt   # GetTimeTool, CalculatorTool (+ ExprEvaluator)
│   │   └── JsTool.kt         # run_javascript (thin wrapper over JsSandbox)
│   └── skills/
│       ├── SkillExecutor.kt  # Runs skills with runtime secret injection
│       ├── SkillTool.kt      # Persisted skill as AiTool
│       ├── CreateSkillTool.kt# Meta-tool: AI creates draft skills
│       ├── ListSkillsTool.kt # Inventory incl. draft status (gate visibility)
│       └── SkillValidation.kt# Pure validation (tool names, schemas, secrets)
├── local/
│   ├── AppDatabase.kt        # Room @Database v5 (+ MIGRATION_1_2 … 4_5)
│   ├── dao/ChatDao.kt        # Session/message/favorite/provider/tool/skill DAOs
│   ├── entity/ChatEntities.kt# All @Entity classes
│   └── preferences/
│       └── UserPreferencesDataStore.kt  # Appearance + selection + generation prefs
└── repository/
    └── ChatRepositoryImpl.kt # Maps entity↔domain

domain/
├── model/
│   ├── ChatModels.kt         # ChatSession/Message/Role, AiModelInfo, FavoriteModel,
│   │                         # ProviderInstance, ToolCallRecord, ChatTurn
│   └── SkillModels.kt        # Skill, SecretDecl
└── repository/
    └── ChatRepository.kt     # Interface: reactive and suspend functions

ui/
├── chat/
│   ├── ChatUiState.kt        # UiState + ChatUiEvent + RunningTool
│   ├── ChatViewModel.kt      # Send/stream/stop, model select, session CRUD
│   ├── ChatScreen.kt         # Drawer/history, timeline, bubbles, input, model sheet
│   ├── MessageSegments.kt    # <thought> split/strip (pure, tested)
│   └── GenerationStats.kt    # Token estimate + tok/s helpers (pure, tested)
├── components/
│   └── MarkdownText.kt       # M3 markdown wrapper (headings capped to bubble scale)
├── settings/
│   ├── SettingsUiState.kt / SettingsViewModel.kt / SettingsScreen.kt
│   └── pages/                # Appearance, Providers, Models, Skills, General, About
├── navigation/
│   ├── AppNavigation.kt      # Route sealed interface (@Serializable)
│   └── AppNavHost.kt         # NavHost wiring + ViewModel factories
└── theme/
    ├── Color.kt              # Light + Dark fallback palettes (+ presets in Theme.kt)
    ├── Shape.kt              # QuickShapes with expressive rounding
    ├── Type.kt               # Quicksand/system typography
    ├── Spacing.kt            # QuickSpacing 8dp system
    └── Theme.kt              # QuickChatTheme (dynamic color, presets, AMOLED)
```

---

## Conventions

### Naming
- **Package**: `dev.lciszewski27.quickchat`
- **Entities** (`data/`): `*Entity` suffix
- **Domain models**: no suffix
- **UiState classes**: `*UiState` suffix, one per screen
- **Events**: `*UiEvent` sealed interface, one per screen
- **ViewModels**: `*ViewModel` suffix
- **Screens**: `*Screen` composable function
- **Theme**: `QuickChatTheme`, `QuickSpacing`, `QuickShapes`

### State management
- Each screen has a **`data class UiState`** (immutable, single source of truth).
- Each ViewModel exposes `val uiState: StateFlow<UiState>` and private `_uiState: MutableStateFlow<UiState>`.
- Events are defined as a **sealed interface** `UiEvent`; ViewModels route `onEvent(event)` via `when`.
- Streaming is per-session (`streamingSessionId`); the stream survives navigation and only renders in its session.

### Streaming & tools
- `streamReply` returns `Flow<AiStreamEvent>`; text deltas render live as plain text (markdown re-parse per chunk flickers — full markdown applies on persist).
- Scroll follows the stream with instant snaps + bottom-edge pinning; never restart an animated scroll per chunk.
- Tool definitions come from `AiTool` (built-ins) + tested/enabled `Skill`s; `generateReply` collects text only.
- Secrets: declarations live on the skill row, values in `skill_secrets`; tool definitions and listings must never include values (covered by `SkillsTest`).
- Gemini 3 tool calls require echoing `extra_content.google.thought_signature` verbatim on the follow-up turn — `SdkProvider` round-trips it; other providers ignore the field.

### JavaScript sandbox
- One shared `JsSandbox` (QuickJS, fresh engine per call): no fetch/timers/modules/filesystem; helpers are `base64Encode/Decode`, `request`, `httpRequest` (all synchronous — strip redundant `await` before them); `args`/`secrets` globals; single explicit `return` wrapper (bare completions don't cross the binding reliably); `evaluationTimeoutMillis` + coroutine `withTimeout`; result caps.

### Database
- Real migrations for every schema change (`MIGRATION_1_2` … `MIGRATION_4_5`) — Room validates migrated schemas exactly (lesson learned: a stray index in a migration crashes startup with "Migration didn't properly handle").
- New message stats columns use `@ColumnInfo(defaultValue = "0")` so migration SQL matches entity expectations.
- Cascades are manual in `ChatRepositoryImpl` (sessions → messages + tool calls; providers → sessions + favorites; skills → secrets).

### Navigation
- Type-safe routes: `Route.Chat`, `Route.Settings`.
- Model picker is a `ModalBottomSheet` showing **favorites only**, grouped by provider.

### Material 3
- Consume `MaterialTheme` roles; `surfaceContainer*` for hierarchy, `tertiaryContainer` for tool cards; no decorative outlines/shadows/gradients.
- Touch targets stay at 48dp defaults; edge-to-edge with `imePadding()` on scroll containers.

---

## Testing

### Current status
- `AiToolsTest` — evaluator, JS sandbox (timeout, top-level return, await-tolerance, no-fetch proof), 45s anti-hang rule for engine tests
- `SkillsTest` — validation, create flow, secret injection, test gate, inventory (fake repository)
- `GenerationStatsTest`, `MessageSegmentsTest`, `AiCoreTranscriptTest` — pure-logic suites
- `ExampleUnitTest` / `ExampleInstrumentedTest` — placeholders

### Unit test patterns to follow
- **Pure logic first:** parsers, validators, formatters, transcript builders — no Android needed.
- **Tools:** real `JsSandbox` on JVM via the `quickjs-kt-jvm` test substitution in `app/build.gradle.kts`; engine tests carry the `Timeout` rule.
- **Repository-adjacent logic:** small fakes of `ChatRepository` (see `SkillsTest.FakeSkillRepo`); never `TODO()` a method the test path touches.
- Room migrations are validated by the crash-on-mismatch behavior — double-check new `ALTER`/`CREATE` statements against the entity (columns, defaults, indices).

---

## Extension Points

### Add a new provider backend
1. Append a `ProviderConfig` in `Providers.kt` (OpenAI-protocol: base URL + paths; Anthropic: base + version). No hardcoded models — catalogs are fetched live.
2. For a non-HTTP backend (like AICore), implement `AiProvider` directly and branch it in `ProviderResolver.resolve()`; add the protocol to `LlmProtocol` and fail-loud branches in `SdkProvider`.
3. No UI changes needed — providers, models, favorites, and chat resolve instances dynamically. Add `requiresApiKey = false` for keyless backends and handle the Settings card accordingly.

### Add a new built-in tool
1. Implement `AiTool` in `data/ai/tools/` (name, description, JSON-Schema params, `execute` returning result text or `"Error: …"` — never throw for bad input).
2. Append it to `AiTools.defaults()`. Both wire protocols pick it up automatically.
3. Add unit tests (pure logic + tool behavior); engine-touching tests get the `Timeout` rule.

### Add a new screen
1. Define a new `Route` in `AppNavigation.kt`.
2. Create the package: `ui/yourscreen/` with `*UiState.kt`, `*ViewModel.kt`, `*Screen.kt`.
3. Add a `composable<Route.YourRoute>` block in `AppNavHost.kt`.
4. Wire the ViewModel factory using `app.*` dependencies.

### Add a new preference
1. Add a key to `UserPreferencesDataStore.Keys`.
2. Add the `Flow` accessor and `suspend` setter.
3. Surface it in `SettingsUiState` / `SettingsViewModel` (+ page UI).
