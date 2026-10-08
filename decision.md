# Focus Study — Decision Log

> Architectural and product decisions with rationale. Add a new entry every time a non-trivial decision is made.

---

## Decision Format

```
### DEC-NNN — Title
Date: YYYY-MM-DD
Status: Decided | Superseded | Revisit
Context: Why this decision was needed
Decision: What was decided
Rationale: Why this option over alternatives
Consequences: What this implies for the codebase
```

---

## Decisions

---

### DEC-001 — Android-only, no cross-platform framework
**Date:** 2026-10-06  
**Status:** Decided

**Context:** Need to choose between native Android, Flutter, React Native, or KMP.

**Decision:** Build Android-native with Kotlin + Jetpack Compose only.

**Rationale:**
- Firebase AI Logic Android SDK is first-class; Flutter/RN integration is secondary and lagged.
- Jetpack Compose + Material 3 provide the premium, information-dense UI required by the spec.
- WorkManager, Room, DataStore are all native JVM libraries — no bridging overhead.
- The spec explicitly requires these libraries; fighting a cross-platform framework is unnecessary complexity.

**Consequences:**
- iOS is not in scope for V1.
- All agents must write Kotlin.

---

### DEC-002 — Firebase AI Logic over direct Gemini Developer API
**Date:** 2026-10-06  
**Status:** Decided

**Context:** How to integrate Gemini safely in an Android app without exposing API keys in the APK.

**Decision:** Use Firebase AI Logic (formerly Vertex AI in Firebase) as the AI access layer.

**Rationale:**
- Firebase AI Logic provides a proxy architecture — no raw Gemini API key in the APK.
- Built-in Firebase App Check integration prevents unauthorized AI access.
- Supports structured output, multimodal input (PDF/image), and streaming natively on Android.
- Current stable models include Gemini 2.5 Flash; model name is configured centrally (see DEC-005).

**Consequences:**
- Firebase project is required even for local-only builds.
- Debug builds need `FirebaseAppCheck.installAppCheckProviderFactory(DebugAppCheckProviderFactory())`.
- Credentials are managed through `google-services.json`, never committed to source.

---

### DEC-003 — Local-first architecture with optional cloud sync
**Date:** 2026-10-06  
**Status:** Decided

**Context:** Decide whether the app requires a backend or can work standalone.

**Decision:** Room is the primary data store. Cloud sync is optional and gated behind authentication.

**Rationale:**
- Core study planning, timer, progress, and history must work without internet.
- Students in exam prep cannot rely on constant connectivity.
- Firebase Authentication + Firestore sync is additive — it doesn't change the local data model.
- Reduces initial complexity; P2 feature.

**Consequences:**
- All Room entities must have stable local IDs.
- Timestamps stored in UTC; displayed in user's local timezone.
- Sync queue pattern when cloud is enabled: local write first, queue sync, reconcile on connectivity.
- Conflict policy: plan metadata = versioned; sessions = append-only; settings = last-write-wins.

---

### DEC-004 — Deterministic progress; Gemini explains, never defines
**Date:** 2026-10-06  
**Status:** Decided

**Context:** The AI could compute progress percentages directly, which risks hallucinated numbers.

**Decision:** All progress metrics (coverage %, planned vs actual, streaks, adherence) are computed from Room data by deterministic Kotlin functions. Gemini receives computed values and generates natural-language explanations only.

**Rationale:**
- Students make exam decisions based on progress data — fabricated numbers are harmful.
- Deterministic logic is testable; LLM output is not.
- Matches spec principle: "Never let the LLM invent progress percentages."

**Consequences:**
- `ProgressRepository` owns all metric calculations.
- AI insight prompts receive pre-computed context, not raw DB access.
- Every displayed metric must trace to a stored record (see spec §50).

---

### DEC-005 — Centralized AI model configuration
**Date:** 2026-10-06  
**Status:** Decided

**Context:** Model names change over Firebase AI Logic releases. Hard-coding breaks upgrades.

**Decision:** Create `AiModelConfig` with named constants: `planningModel`, `analysisModel`, `fastChatModel`. Never scatter model name strings in business logic.

**Rationale:**
- Firebase AI Logic model availability changes (e.g., Gemini 2.5 Flash is current stable).
- Single change point to upgrade all AI features simultaneously.
- Feature flags allow A/B testing different models per use case.

**Consequences:**
- `AiModelConfig` must be the only place model name strings live.
- Unit tests mock this config; they do not call live Gemini.

---

### DEC-006 — Schema-constrained AI output with local validation
**Date:** 2026-10-06  
**Status:** Decided

**Context:** Unstructured Gemini output is fragile to parse and risky to apply.

**Decision:** All machine-consumed AI responses (plan generation, syllabus extraction, replan, action intents) use Firebase AI Logic's structured output (JSON schema). Output is validated by `AiResponseValidator` before any mutation to local state.

**Rationale:**
- Firebase AI Logic supports constrained JSON generation; use it.
- Validation catches hallucinated field types, missing required fields, out-of-range values before they reach the DB.
- AI failure path: retain last valid plan, show retry, never lose user data.

**Consequences:**
- Every AI service has a companion Kotlin data class with `@Serializable` for schema definition.
- Prompt versions are tracked (e.g., `planner.v1`, `syllabus-parser.v1`).
- Tests use fixtures (mock AI response JSON), never live network calls.

---

### DEC-007 — Prompt injection defense via strict delimiters
**Date:** 2026-10-06  
**Status:** Decided

**Context:** Syllabus content is untrusted user input (including PDF/image uploads). A malicious document could contain text designed to override system instructions.

**Decision:** Syllabus text is always injected into prompts as explicitly-delimited content, never as system-level instructions. System prompt clearly establishes that the content section is untrusted user data.

**Rationale:**
- Spec §44 explicitly calls this out: "Ignore previous instructions" is a real attack vector.
- Firebase AI Logic does not eliminate prompt injection risk at the content level.
- Strong delimiters + explicit system instructions reduce but cannot eliminate risk.

**Consequences:**
- `AiPromptRepository` handles all prompt construction — no free-form prompt building in UI or ViewModels.
- System instructions are static and not derived from user input.
- Prompt injection is a test case in FSTUDY-014-04.

---

### DEC-008 — Adaptive replanning: minimal delta, not full rebuild
**Date:** 2026-10-06  
**Status:** Decided

**Context:** When a student falls behind, the app could rebuild the entire plan or adjust minimally.

**Decision:** Replanning produces a minimal delta — move or reschedule affected sessions without rebuilding confirmed future sessions. Hard constraints (exam date, locked sessions, max availability) are always preserved.

**Rationale:**
- Full rebuilds destroy student-approved session structures and cause confusion.
- Spec §12: "Do not rebuild the entire plan for tiny deviations."
- Minimal deltas are explainable: "I moved 2 Java sessions to Sunday."
- Students must preview and accept/reject major replans.

**Consequences:**
- `AiReplanService` receives current plan + missed sessions + risk calculation (deterministic).
- Output is a typed diff (MOVE_SESSION, ADD_SESSION, REMOVE_SESSION) — not a new plan object.
- Replan history is stored in `AiPlanVersion`.

---

### DEC-009 — Feature module structure (core + feature packages)
**Date:** 2026-10-06  
**Status:** Decided

**Context:** Need a scalable package structure that supports multiple agents working in parallel.

**Decision:** Separate `core/` (shared infrastructure) from `feature/` (vertical slices). Each feature is self-contained with its own ViewModel, UseCases, and UI.

**Rationale:**
- Multiple agents can work on different features without collision.
- Core modules (database, AI, design) are shared dependencies only.
- Avoids giant Activities and business logic in Composables.

**Consequences:**
- `core/database/` owns all Room entities and DAOs.
- `feature/X/` owns the ViewModel, UseCases, and Compose screens for feature X.
- Repositories sit at the core level or at the feature boundary, accessed only through use cases.

---

### DEC-010 — WorkManager for all background work; no permanent loops
**Date:** 2026-10-06  
**Status:** Decided

**Context:** Need reliable background jobs (daily plan refresh, notifications, AI insight generation) without draining battery.

**Decision:** Use WorkManager for all deferrable background work. No permanent foreground services or background loops unless the focus timer is actively running.

**Rationale:**
- WorkManager is the Android-recommended solution for reliable deferrable work.
- Permanent loops drain battery and are killed by Doze.
- Focus timer uses a foreground service only while the timer is actively counting.

**Consequences:**
- `core/notifications/` schedules WorkManager jobs for reminders.
- AI insight refresh is a periodic WorkManager task.
- Daily summary generation runs via WorkManager at a scheduled time.

---

### DEC-011 — Minimum viable UX: four primary questions answered on home screen
**Date:** 2026-10-06  
**Status:** Decided

**Context:** The home screen could surface many things. Need a clear hierarchy.

**Decision:** Home screen hierarchy:
1. What should I study now? (next session card)
2. How much have I done today? (progress bar)
3. What is at risk? (at-risk topics card)
4. What should I do next? (today's timeline)

**Rationale:**
- Spec §14 and §26 define this hierarchy explicitly.
- Students open the app to answer one question; give it to them immediately.
- Avoid dashboard overload — secondary analytics live on the Progress/Analytics tabs.

**Consequences:**
- Home screen ViewModel aggregates data from multiple repositories.
- Charts and detailed analytics are on the Progress screen, not the home screen.
- AI daily brief is a collapsible card, not the primary UI element.

---

### DEC-012 — Native Jetpack Compose Canvas & declarative components for charts
**Date:** 2026-10-06  
**Status:** Decided

**Context:** The app requires Planned vs Actual comparison bars, Subject distribution bars, and a 7x5 Study Heatmap. Need to decide whether to add third-party charting libraries (MPAndroidChart, Vico) or build native Compose components.

**Decision:** Build native Jetpack Compose Canvas and Box/Column grid components without third-party chart dependencies.

**Rationale:**
- Directly aligns with Ponytail skill rung 4: *Native platform feature — The best code is the code you never wrote.*
- Zero external dependencies to maintain or update.
- Completely reactive with Material 3 theming, dark/light modes, and dynamic sizes.
- Deterministic and fast with zero UI lag.

**Consequences:**
- No extra dependencies added to `libs.versions.toml`.
- Chart layout is 100% testable and lightweight.

### DEC-013 — Dual-engine architecture: Deterministic planner baseline with structured delta replanning
**Date:** 2026-10-06  
**Status:** Decided

**Context:** Full LLM plan generation can introduce timeline drifts, hallucinated topics, or invalidate student constraints.

**Decision:** The application core uses `PlannerEngine` to establish deterministic schedule foundations and calculate mathematical schedule risk. Gemini/AI services propose minimal structured deltas (`MOVE_SESSION`, `ADD_SESSION`, `REMOVE_SESSION`) strictly validated by `AiResponseValidator`. Pinned (`isLocked = true`) sessions are immutable and strictly protected from modification.

**Rationale:**
- Strict alignment with the core project principle: "AI proposes; the application decides what is valid."
- Eliminates the risk of hallucinated dates, out-of-range study session lengths (<15m or >180m), or timeline disruption past the exam date.
- Offline and fallback scenarios operate seamlessly without internet connectivity.

**Consequences:**
- All AI proposals pass through `AiResponseValidator` before presentation or DB execution.
- User retains full control via the `ReplanDialog` diff inspection before committing changes.

---

### DEC-014 — AI Plan Versioning and Audit Trail
**Date:** 2026-10-06  
**Status:** Decided

**Context:** Users need transparency on why their schedule changed and how many revisions occurred.

**Decision:** Store an immutable `AiPlanVersion` record in Room upon every plan generation or replan application, containing the summary of changes, prompt version, JSON delta diff, and UTC timestamp. Expose this history in the Exam Roadmap tab.

**Rationale:**
- Builds user trust by explaining why schedule changes were made.
- Enables debugging and telemetry on AI planning efficiency.
- Supports future undo/rollback functionality.

**Consequences:**
- Added `AiPlanVersion` entity and DAO queries in `studyPlanDao()`.
- Planner UI displays the version history strip with prompt schema identifiers.

### DEC-015 — AI Coach with typed action intents and user confirmation
**Date:** 2026-10-06  
**Status:** Decided

**Context:** Spec §22 & §23 specify that the AI Study Coach must provide recommendations with supporting data and can propose actions (`LIGHTEN_TODAY`, `REBUILD_SCHEDULE`, `FOCUS_TOPIC`).

**Decision:** The AI Coach generates conversational responses with optional typed `CoachAction` intents. Any mutation action is rendered as an embedded interactive confirmation card in the chat bubble. Mutating Room state is only executed when the student explicitly taps "Execute Action", accompanied by validation (e.g. verifying non-locked sessions) and writing a plan version audit.

**Rationale:**
- Prevents accidental, unexpected, or hallucinated schedule modifications.
- Fulfills the golden rule: "AI proposes; the application decides what is valid."
- Provides a delightful, natural conversational experience with tangible agency.

**Consequences:**
- Built `AiCoachService` and `AiCoachViewModel`.
- Embedded `ActionProposalCard` inside `AiCoachScreen`.

---

### DEC-016 — WorkManager background scheduling and tiered notification channels
**Date:** 2026-10-06  
**Status:** Decided

**Context:** The application needs reliable morning plan reminders, exam countdown alerts, missed session alerts, and end-of-day reviews without spamming the user or draining battery.

**Decision:** Use Android `WorkManager` for asynchronous, battery-efficient periodic checks (`DailyPlanWorker` every 24h, `ScheduleRiskWorker` every 12h). Partition notifications into three distinct Android O+ channels: `channel_study_sessions` (High priority), `channel_daily_plan` (Default priority), and `channel_schedule_risks` (Default priority). Honor user preferences stored in `UserPreferencesManager`.

**Rationale:**
- Conforms to Android platform best practices for background work (spec §36).
- Tiered channels allow users to mute specific alert types without disabling critical session reminders.
- `ExistingPeriodicWorkPolicy.KEEP` prevents duplicate job registrations on app relaunch.

**Consequences:**
- `NotificationHelper` and `StudyWorkScheduler` initialized in `FocusStudyApp.onCreate()`.
- Added unit tests verifying notification and background worker logic.

---

### DEC-017 — Native Android AppWidget via AppWidgetProvider and RemoteViews
**Date:** 2026-10-06  
**Status:** Decided

**Context:** The app requires home screen widgets displaying exam countdown, today target progress bar, and next priority session launcher (FSTUDY-013-01, 02, 03).

**Decision:** Implement standard Android platform `AppWidgetProvider` (`TodayStudyWidgetProvider`) with XML layout (`widget_today_study.xml`) and reactive broadcast update mechanism (`TodayStudyWidgetProvider.triggerUpdate(context)`).

**Rationale:**
- Native Android `RemoteViews` requires zero additional external dependencies (Ponytail rung 4).
- Provides immediate home screen accessibility for students to glance at exam days and one-tap launch study sessions.
- Automatically refreshes on timer completion, study attempt logging, and every 30 minutes in background.

**Consequences:**
- Declared receiver in `AndroidManifest.xml` with `@xml/today_study_widget_info`.
- `FocusScreen` triggers real-time widget refresh upon session completion.

---

### DEC-018 — Local-First Settings & Data Backup with Round-Trip Validation
**Date:** 2026-10-06  
**Status:** Decided

**Context:** Users need control over timer sounds, haptics, notifications, and the ability to export and import complete local database state without relying on cloud infrastructure (FSTUDY-006-04, FSTUDY-015).

**Decision:** Implement `SettingsScreen` and `SettingsViewModel` exposing toggle controls stored via `UserPreferencesManager` (DataStore) and full JSON backup serialization/deserialization via `DataBackupManager` with checksum and schema validation before committing to Room.

**Rationale:**
- Fulfills local-first privacy commitment (DEC-003): user data remains strictly on-device unless manually exported.
- Protects users from data corruption or migration failures by validating payloads prior to DB mutation.
- Native Compose `LocalHapticFeedback` provides haptic feedback without requiring low-level hardware vibration managers.

**Consequences:**
- Added `SettingsScreen` accessible from header gear icon in `TodayScreen`.
- DataStore tracks sound, haptic, and notification toggles.
- Backup export copies formatted JSON directly to Android system clipboard.

---

### DEC-019 — Multi-environment Firebase App Check attestation policy
**Date:** 2026-10-07  
**Status:** Decided

**Context:** The app requires backend/Gemini request authentication to prevent unauthorized APK usage and scraping without requiring forced user logins (FSTUDY-014-01).

**Decision:** Create `AppCheckConfig` initializing Play Integrity attestation for production builds and debug attestation for development. Check attestation status before delegating requests to Firebase AI Logic.

**Rationale:**
- Conforms with Google Play integrity best practices for protecting LLM API quotas.
- Keeps onboarding frictionless (zero mandatory signup) while securing API boundaries.

**Consequences:**
- `AppCheckConfig.initialize` called in `FocusStudyApp.onCreate()`.
- Validated via unit test suite in `DatabaseIntegrityTest`.

---

### DEC-020 — Sensitive data log sanitization and path-based Firestore security rules
**Date:** 2026-10-07  
**Status:** Decided

**Context:** Student study notes, emails, and Gemini auth tokens must never leak to system logcat or unauthorized cloud paths (FSTUDY-014-03, FSTUDY-014-06).

**Decision:** Enforce regex sanitization via `SafeLogger` for emails, bearer tokens, and credentials with JVM fallback. Deploy path-based Cloud Firestore ownership rules (`/users/{userId}/*` owned exclusively by `request.auth.uid`).

**Rationale:**
- Meets strict student privacy guarantees (spec §51 & §52).
- Zero chance of credential or PII leaks in production bug reports.

**Consequences:**
- `SafeLogger` replaces raw Logcat calls in critical security modules.
- Added root `firestore.rules` file ready for Firebase deployment.

---

### DEC-021 — Tasteful Academic Gamification and Scholar Progression System
**Date:** 2026-10-07  
**Status:** Decided

**Context:** Spec P3 proposes gamification (XP and achievements) to maintain student consistency without introducing distracting casino mechanics or vanity badges.

**Decision:** Implement `GamificationEngine` with a 5-tier academic hierarchy ("Novice Scholar", "Apprentice Scholar", "Practitioner", "Senior Scholar", "Master Strategist") and 6 foundational milestones tied to actual study effort (first block, 3-day streak, 5h focus, 25%/50%/80% syllabus mastery). XP is earned deterministically (2 XP per focused study minute + 50 XP per unlocked milestone).

**Rationale:**
- Maintains intrinsic motivation and academic gravitas without distracting game elements.
- Uses native Material 3 Compose cards, standard Unicode emoji badges, and Room persistence.
- Zero external libraries or complex game engines (Ponytail mindset).

**Consequences:**
- Integrated into `ProgressViewModel` and `ProgressScreen`.
- Fully covered by unit tests in `GamificationEngineTest`.

---

### DEC-022 — Release Polish and Semantic Accessibility Standards
**Date:** 2026-10-07  
**Status:** Decided

**Context:** The application is preparing for production release. It requires accessibility standards, performance guardrails, and graceful empty states across all screens.

**Decision:** 
1. Enforce explicit semantic `contentDescription` on all interactive or status icons. Decorative icons adjacent to text labels retain null `contentDescription` to prevent screen reader redundancy.
2. Maintain touch targets >= 48dp on all interactive elements.
3. Use lambda-based state progress getters in Compose progress indicators (`progress = { state.progressPercent }`) to eliminate full recomposition passes.
4. Provide clear empty states and guidance on every tab when zero exams or sessions are present.

**Rationale:**
- Ensures compliance with Google Play accessibility and performance guidelines.
- Guarantees seamless experience for first-time users and screen-reader users alike.

**Consequences:**
- Cleaned up icon accessibility labels in `TodayScreen`.
- Compiled release documentation in `distribution/PLAY_STORE_METADATA.md`.

---

### DEC-023 — Voice Input (STT), Audio Reader (TTS), and Natural Language Auto-Logging
**Date:** 2026-10-07  
**Status:** Decided

**Context:** Users require hands-free interaction in the AI Coach tab to speak commands ("mic option"), have schedules and responses read aloud ("read things"), and verbally command the AI to log sessions directly into the app database (e.g. "Suppose right now I'm doing coding from 11:30 to 2:00 p.m., so it should enter this in data") without manual typing or form entry.

**Decision:**
1. **Speech-to-Text (STT):** Use Android native `RecognizerIntent.ACTION_RECOGNIZE_SPEECH` via `rememberLauncherForActivityResult`. Avoid any external speech SDKs (Ponytail aligned). Add `RECORD_AUDIO` permission to AndroidManifest.
2. **Text-to-Speech (TTS):** Integrate `android.speech.tts.TextToSpeech` into `AiCoachScreen` with clean lifecycle cleanup (`DisposableEffect`). Provide a speaker button (`Icons.AutoMirrored.Filled.VolumeUp`) on coach messages, and automatically speak schedule summaries when queried.
3. **Natural Language Study Session Parser:** Implement pattern recognition in `AiCoachService` for time ranges (e.g., `11:30 to 2:00 p.m.`), duration calculations (150 mins), and topic extraction (e.g., "Coding"). Propose typed `LOG_STUDY_SESSION` actions with +300 XP.
4. **Autonomous Data Committer:** In `AiCoachViewModel`, when the user specifies phrases like "enter this in data" or "save in data", auto-commit the proposed session directly into Room SQLite (`Topic`, `StudySession`, `StudyAttempt`, `StudyPlanDay`, and `GamificationEngine` XP) without requiring secondary manual confirmation taps.
5. **Voice Scheduling & Readout:** Support "read things / read my schedule" to summarize today's agenda with audio playback, and "arrange a schedule" to trigger schedule rebalancing.

**Rationale:**
- Delivers frictionless, zero-effort logging for busy students.
- Respects strict local-first architecture and eliminates external SDK dependencies.
- Retains safety with typed intent modeling while offering instant auto-commit when explicitly commanded.

**Consequences:**
- Added `updatePlanDay` in `StudyPlanDao`.
- Enhanced `AiCoachService` with `parseStudyCommand` and `parseTimeStringToMinutes`.
- Added unit test suite in `AiCoachAndNotificationTest` verifying 150m calculation, XP rewards, and auto-logging.
- Validated on connected physical Samsung Galaxy S20 FE device.

---

### DEC-024 — Premium Typography Standardization, StatusPill Component, Midnight OLED Theme, and In-App Tutorial Guide
**Date:** 2026-10-07  
**Status:** Decided

**Context:** User requested an overhaul of the app UI to eliminate "AI slop" (clipping badges, deformed oval badges, overlapping text descenders, alignment inconsistencies), introduce dark menu structure themes (Dark Slate, Midnight OLED pure black, Paper Light, System Default), and build interactive in-app tutorials and cheat sheets for effortless usability.

**Decision:**
1. **Typography Standardization (`Typography.kt`):** Standardize all 15 Material 3 typography definitions (`display*`, `headline*`, `title*`, `body*`, `label*`) with proportional line heights (e.g. `labelSmall`: 10sp / 14sp, `bodySmall`: 12sp / 17sp, `titleMedium`: 15sp / 22sp) ensuring zero descender clipping and eliminating text overlap.
2. **`StatusPill` Native Component (`StatusPill.kt`):** Replace all deformed M3 `Badge { Text(...) }` implementations across feature screens (`TodayScreen`, `PlannerScreen`, `ProgressScreen`, `AiCoachScreen`, `ReplanDialog`) with a bespoke, native `StatusPill` surface having rounded corners (`8.dp`), proportional padding, and optional leading icons.
3. **Multi-Mode Theme System & Midnight OLED (`Theme.kt`, `Color.kt`):**
   - Implemented 4 selectable appearance modes: "System Default", "Dark Slate Mode", "Midnight OLED (Pure Black)", and "Paper Light Mode".
   - Engineered Midnight OLED palette with pure black (`#000000`) scaffolds, deep midnight surfaces (`#0B0F15`), and crisp borders (`#21262D`).
   - Reactively wired `UserPreferences.themeMode` through `MainActivity.kt` and `SettingsScreen.kt`.
4. **Interactive App Tutorial & Voice Cheatsheet (`AppTutorialDialog.kt`):**
   - Built a comprehensive 5-tab interactive walkthrough: Voice Coach, Daily Flow, Planner, Focus Timer, and Dark Themes.
   - Includes real-world voice command syntax examples ("Coding from 11:30 to 2:00, enter this in data", "Read my schedule").
   - Integrated access triggers into `TodayScreen` (`?` Help icon), `AiCoachScreen` ("💡 Voice Guide" prompt chip), and `SettingsScreen` ("Open Interactive Guide & Cheatsheet" button).

**Rationale:**
- Creates a distinct, information-dense, and highly legible visual hierarchy.
- Pure black AMOLED mode significantly reduces battery consumption on OLED displays during long study sessions.
- In-app tutorials eliminate learning friction without bloating the APK with third-party walkthrough libraries (Ponytail aligned).

**Consequences:**
- Added `StatusPill.kt` to `core.design.theme`.
- Updated `SettingsViewModel` and `SettingsScreen` with dedicated Theme & Appearance and Guides sections.
- Validated with live screenshots on physical Samsung Galaxy S20 FE 5G hardware.

---

### DEC-025 — Advanced AI Personalization, Social Study Buddy Accountability, and Adaptive Tablet/Foldable Workstations
**Date:** 2026-10-07  
**Status:** Decided

**Context:** Fulfilling remaining P3 requirements from the Product Specification (§53, §54):
1. Students have different natural circadian peaks (chronotypes) and study preferences, requiring personalized scheduling and cognitive fatigue warnings to avoid burnout before exams.
2. Students maintain higher study consistency when paired with an accountability partner ("study buddy"), needing a lightweight, privacy-preserving way to share progress and compare stats without mandatory cloud logins.
3. Tablets, foldables (unfolded), and landscape mode currently stretch compact phone layouts across wide displays, wasting screen real estate.

**Decision:**
1. **PersonalizationEngine (`PersonalizationEngine.kt`):**
   - **Chronotype Profiling:** Analyzes attempt timestamps to categorize students into 4 circadian profiles: Early Bird 🌅, Daylight Achiever ☀️, Night Owl 🦉, or Adaptive Explorer ⚖️. Highlights their optimal focus window.
   - **Evidence-Based Learning Techniques:** Recommends specific methodologies (Active Recall & Testing, Feynman Technique, Pomodoro Classic, Spaced Blurting) based on topic difficulty and study phase.
   - **Cognitive Fatigue Guard:** Analyzes 7-day focus load, consecutive heavy days (>=4h), and late-night study to compute burnout risk (Optimal, Moderate, High) with restorative recovery suggestions.
   - **AI Coach Integration:** Users can query their chronotype, study techniques, and burnout check via conversation or quick prompt chips.
2. **Social Study Buddy & Accountability Sharing:**
   - **Native Android Share Sheet:** Formats verified study milestone cards with hashtags and an importable buddy token (`[BUDDY-STATS:NAME=...:XP=...:STREAK=...:MINS=...]`) using Android's native `Intent.ACTION_SEND` (zero third-party SDKs, Ponytail aligned).
   - **Peer Comparison Duel:** Dialog allows importing or entering a buddy's stats to display an "Accountability Duo" side-by-side comparison on `ProgressScreen` with positive encouragement messages. Data persists locally in DataStore.
3. **Adaptive Tablet & Large-Screen Workstations (`MainScreen.kt`, `TodayScreen.kt`, `PlannerScreen.kt`, `ProgressScreen.kt`):**
   - Screen width breakpoint `screenWidthDp >= 600` automatically transitions the navigation to a Material 3 `NavigationRail` on the left.
   - **TodayScreen:** 2-pane workstation displaying Header Countdown, Daily Target, and Next Session on the left; AI Daily Brief, At-Risk Topics, and Sessions Timeline on the right.
   - **PlannerScreen:** 2-pane workstation displaying Exam Roadmap macro phases, AI strategy insight, and replan alerts on the left; tabbed Day Schedule and 7-day strip on the right.
   - **ProgressScreen:** 2-pane workstation displaying Scholar Tier, Study Buddy Duo, Personalization Chronotype, and Streaks on the left; Syllabus Coverage, Adherence charts, Subject breakdown, Heatmap, and Peak Window on the right.

**Rationale:**
- Maintains strict local-first architecture and zero external SDK dependencies.
- Delivers an empowering, supportive study experience on both mobile and large-screen foldable/tablet devices.

**Consequences:**
- Added `PersonalizationEngine.kt` to `feature.progress`.
- Updated `UserPreferencesManager`, `UserSettingsRepository`, `AiCoachService`, `AiCoachScreen`, `SettingsScreen`, and `SettingsViewModel`.
- Added `PersonalizationAndSocialTest` unit suite (all 12 test suites passing 100%).
- Verified `assembleDebug` APK build (18.9 MB).

---

### DEC-026 — Production Release Packaging, Google Play AAB, and Toolchain JDK Configuration
**Date:** 2026-10-07  
**Status:** Decided

**Context:** Project requires verified production artifacts for release, distribution via Google Play Store (`.aab` format) and standalone APK (`.apk` format), with seamless developer onboarding across systems without manual environment variable tweaking.

**Decision:**
1. **JDK 21 Toolchain Auto-Detection:** Configured `gradle.properties` (`org.gradle.java.home=C:/Program Files/Android/Android Studio/jbr`) and `gradlew.bat` with automated fallback detection to the bundled Android Studio JetBrains Runtime (JBR 21), allowing zero-config execution of `./gradlew` from any terminal or PowerShell instance.
2. **Release Artifact Generation:**
   - Compiled production Android App Bundle (`FocusStudy-v1.0.0.aab`, 11.9 MB) via `bundleRelease`.
   - Compiled optimized production release APK (`FocusStudy-v1.0.0-release.apk`, 12.3 MB) via `assembleRelease` passing `lintVitalRelease`.
   - Verified debug APK (`FocusStudy-v1.0.0-debug.apk`, 18.9 MB) via `assembleDebug`.
3. **Distribution Repository Package:** Consolidated all compiled release packages and updated Play Store store listing specifications, disclosure declarations, and permissions (`RECORD_AUDIO`, `POST_NOTIFICATIONS`, `VIBRATE`) in `distribution/PLAY_STORE_METADATA.md`.

**Rationale:**
- Fulfills Google Play Store target SDK 35 and App Bundle format requirements.
- Guarantees immediate zero-setup reproducible builds across workstations.

**Consequences:**
- Updated `gradlew.bat` and `gradle.properties`.
- Added release binary outputs to `distribution/`.
- Verified all 12 test suites passing 100%.

---

### DEC-027 — Decoupled Coroutine Flow Observers & Deterministic Schedule Re-Generation
**Date:** 2026-10-08  
**Status:** Decided

**Context:** When advancing across calendar dates or initiating manual plan generation, nested infinite Flow `.collect { }` blocks in `PlannerViewModel` led to coroutine suspension deadlock, preventing subsequent date selections and plan regeneration from updating the UI. Additionally, regenerating a plan required superseding previous active plans in Room to prevent primary plan collisions.

**Decision:**
1. **Decoupled Flow Observers:** Replaced 5 levels of nested Flow collection in `PlannerViewModel` with decoupled `viewModelScope.launch` jobs using `collectLatest` on `_selectedDate` and `examDao().getPrimaryExam()`.
2. **StateFlow Atomic Updates:** Standardized state modifications using `_uiState.update { ... }`.
3. **Plan Superseding in Room (`StudyPlanDao`):** Added `archiveActivePlans(examId)` to set `status = 'superseded'` on existing active plans before writing a newly generated plan, eliminating stale plan collisions.
4. **Resilient Exam Extraction:** In `regeneratePlan()`, fallback directly to `db.examDao().getPrimaryExam().firstOrNull()` if `_uiState.value.exam` has not arrived yet.

**Rationale:**
- Prevents coroutine suspension traps where infinite Room Flow emissions block outer collectors.
- Guarantees immediate zero-latency schedule generation and seamless date transitions on hardware.

**Consequences:**
- Updated `Daos.kt` (`StudyPlanDao.archiveActivePlans`).
- Refactored `PlannerViewModel.kt` (`loadPlannerData`, `regeneratePlan`).
- Verified live on connected Samsung Galaxy S20 FE 5G device with full 71-day schedule generated.

---

### DEC-028 — Zero-Cost Anti-Bill Architecture & Bring-Your-Own-Key (BYOK) Gemini Integration
**Date:** 2026-10-08  
**Status:** Decided

**Context:** The user asked whether they must provide their personal API key, expressing valid concern about getting a hefty cloud bill if someone extracts a bundled developer API key from the APK or misuses it. The app must guarantee $0 developer expense forever, operate 100% offline and free by default, and provide an optional Bring-Your-Own-Key (BYOK) mechanism for cloud generative features without third-party SDK bloat.

**Decision:**
1. **Zero Developer Cost & Anti-Bill Guarantee:** NEVER bundle or hardcode any developer API key or credit card into the APK/AAB or build configuration. Anyone decompiling the APK with jadx will find 0 API keys.
2. **100% Local-First Baseline:** The entire app (Pomodoro timer, 6-phase adaptive schedule engine `PlannerEngine`, SQLite Room database, Scholar XP/streak gamification, analytics heatmaps, sound/vibration cues, Android speech-to-text, Android Text-to-Speech reader, and coaching heuristic rules) operates 100% locally and offline for $0 cost.
3. **Optional BYOK (Bring Your Own Key):** Users who want open-ended conversational coaching via Gemini 1.5 Flash can input their own free Gemini API key from Google AI Studio (`https://aistudio.google.com/app/apikey`). Google AI Studio provides 15 RPM / 1M TPM for free with zero billing required.
4. **Local Encrypted Storage:** The personal key is stored solely on the user's physical device inside Android encrypted private DataStore (`UserPreferencesManager`), never transmitted to any developer server.
5. **Native JVM REST Client:** Querying Gemini uses native `java.net.HttpURLConnection` and Android `org.json.JSONObject` (zero third-party AI SDKs or OkHttp bloat, strictly Ponytail-aligned).
6. **Graceful Fallback:** If no key is set, the device is offline, or the network times out, `AiCoachService` automatically falls back to deterministic pedagogical heuristics with an informative status message.

**Rationale:**
- Complete financial safety for the developer ($0.00 hosting/API bill forever).
- Total privacy and transparency for the user.
- Complies with open-source and Google Play Store policies.

**Consequences:**
- Updated `UserPreferencesManager.kt`, `UserSettingsRepository.kt`, `SettingsViewModel.kt`.
- Updated `SettingsScreen.kt` with a prominent "100% Anti-Bill Guarantee" banner, masked key input, and 1-tap Google AI Studio link.
- Updated `AiCoachService.kt` and `AiCoachViewModel.kt`.
- Added unit tests in `SettingsAndBackupTest.kt` and `AiCoachAndNotificationTest.kt` (all 12 test suites passing 100%).

---

### DEC-029 — Adaptive Launcher Icons & Lint Hardening
**Date:** 2026-10-08  
**Status:** Decided

**Context:** The application previously lacked an explicit launcher icon declaration in `AndroidManifest.xml`, causing Android to default to the system generic icon and triggering a lint warning `MissingApplicationIcon`. Additionally, widget layout files contained hardcoded text strings and lacked Android 13+ Material You themed icon support (`<monochrome>`).

**Decision:**
1. **Adaptive Launcher Icon Design:** Created an adaptive vector icon (`ic_launcher.xml` and `ic_launcher_round.xml`) with a deep Slate 900 base (`ic_launcher_background.xml`) and an illuminated academic beacon with open wisdom wings in cyan `#38BDF8` and indigo `#818CF8` (`ic_launcher_foreground.xml`).
2. **Android 13+ Material You Monochrome Dynamic Theming:** Added `<monochrome>` elements to both round and standard adaptive icon definitions, enabling adaptive wallpaper tinting on Android 13+ (API 33+).
3. **Legacy Fallback:** Provided layer-list fallback `ic_launcher.xml` in `res/drawable/`.
4. **Layout String Extraction:** Extracted hardcoded strings from `widget_today_study.xml` into `strings.xml`, resolving hardcoded string and typography dash lint warnings.
5. **Verified Release Outputs:** Built and validated debug APK, release APK, and Google Play Bundle with 100% clean test execution.

**Rationale:**
- Ensures the app has a premium, distinct visual identity on student home screens and app drawers.
- Conforms fully to Android 8.0+ adaptive icon and Android 13+ themed icon platform standards.
- Eliminates Android Lint warnings and maintains zero release blockers.

**Consequences:**
- Added `ic_launcher_background.xml`, `ic_launcher_foreground.xml`, `ic_launcher.xml`, `ic_launcher_round.xml`.
- Updated `AndroidManifest.xml` (`android:icon` and `android:roundIcon`).
- Updated `strings.xml` and `widget_today_study.xml`.
- Re-verified all 12 test suites passing and packaged fresh release binaries in `distribution/`.

---

## Open Questions

| # | Question | Owner | Status / Decision |
|---|---|---|---|
| OQ-001 | What is the minimum Android SDK version? | Architect | Decided: minSdk 26 (Android 8.0), compileSdk 35, targetSdk 35 |
| OQ-002 | Will Hilt be used for DI, or manual DI for simplicity? | Senior Dev | Decided: AppContainer container pattern (minimal native DI, zero kapt/ksp overhead, Ponytail aligned) |
| OQ-003 | Chart library choice: Vico, MPAndroidChart, or custom Canvas? | Senior Dev | Decided: DEC-012 Custom native Compose components (zero extra dependencies, Ponytail aligned) |
| OQ-004 | Will the app launch with Firebase Auth disabled (local-only)? | Product | Decided: Yes, local-only by default, cloud sync is optional P2 |
| OQ-005 | PDF text extraction: local ML Kit or pass raw PDF to Gemini multimodal? | M1 agent | Open (M1) |

---

## Superseded Decisions

_(None yet)_
