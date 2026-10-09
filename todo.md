# Focus Study — Todo List

> Tick off items as they are completed. Keep this in sync with `progress.md`.

---

## 🔥 P0 — Must ship (MVP)

### M0 · Foundation

- [x] **FSTUDY-001-01** — Create Android project (Kotlin, Compose, min SDK 26+, target SDK 35)
- [x] **FSTUDY-001-02** — Set up Material 3 design system (theme, colors, typography, shape)
- [x] **FSTUDY-001-03** — Set up Room (DB class, entities, DAOs, type converters)
- [x] **FSTUDY-001-04** — Set up DataStore (preferences proto or typed)
- [x] **FSTUDY-001-05** — Set up ViewModel + Repository + UseCases skeleton
- [x] **FSTUDY-001-06** — Set up unit + instrumentation test structure (JUnit, MockK, Coroutines test)

### M1 · Onboarding

- [x] **FSTUDY-002-01** — Welcome screen (headline, CTA)
- [x] **FSTUDY-002-02** — Exam type selector (university / school / competitive / cert / custom)
- [x] **FSTUDY-002-03** — Exam date selector (single + multi-date, tentative toggle)
- [x] **FSTUDY-002-04** — Available-time wizard (per-day presets, custom, weekday/weekend split)
- [x] **FSTUDY-002-05** — Study-window selector (presets + custom time ranges)
- [x] **FSTUDY-002-06** — Session-length selector (25/5, 45/10, 60/10, custom)
- [x] **FSTUDY-002-07** — Current knowledge selector (per subject: not started → revision only)
- [x] **FSTUDY-002-08** — Study intensity selector (balanced / exam-first / low-burnout / etc.)

### M1 · Syllabus

- [x] **FSTUDY-003-01** — Manual syllabus entry (subject → unit → topic text input)
- [x] **FSTUDY-003-02** — PDF import (file picker, extract text)
- [x] **FSTUDY-003-03** — Image import (camera / gallery, multimodal Gemini)
- [x] **FSTUDY-003-04** — Text extraction / local pre-processing (SyllabusParser)
- [x] **FSTUDY-003-05** — Gemini syllabus extraction (schema-constrained, validated output)
- [x] **FSTUDY-003-06** — Syllabus review/edit screen (edit, merge, delete, mark completed)
- [x] **FSTUDY-003-07** — Topic hierarchy display (Subject → Unit → Topic → Subtopic)

### M2 · Deterministic Planner

- [x] **FSTUDY-004-01** — `calculateAvailableMinutes()` — maps windows to usable time slots
- [x] **FSTUDY-004-02** — `rankTopics()` — priority by exam proximity, difficulty, coverage
- [x] **FSTUDY-004-03** — `allocateStudyMinutes()` — assigns minutes per topic per day
- [x] **FSTUDY-004-04** — `reserveRevision()` — spaced revision windows
- [x] **FSTUDY-004-05** — `reserveBuffer()` — buffer days near exam
- [x] **FSTUDY-004-06** — Plan preview screen (phases, week view, day view)
- [x] **FSTUDY-004-07** — Plan editing (drag session, lock session, delete, add)

### M4 · Today + Focus

- [x] **FSTUDY-005-01** — Today dashboard (exam countdown, progress bar, next session)
- [x] **FSTUDY-005-02** — Next-session recommendation widget
- [x] **FSTUDY-005-03** — Today timeline (list of sessions, ✓ / → / ✗ state)
- [x] **FSTUDY-005-04** — Daily progress bar (actual / planned, percentage)
- [x] **FSTUDY-005-05** — At-risk topics card
- [x] **FSTUDY-006-01** — Focus timer (countdown, progress ring)
- [x] **FSTUDY-006-02** — Pause / resume
- [x] **FSTUDY-006-03** — Session completion screen (+XP, progress update)
- [x] **FSTUDY-006-05** — Session history list

---

## 🟡 P1 — Ship after core works

### M3 · Gemini Planning

- [x] **FSTUDY-004-08** — Gemini plan generation (AiPlannerService, structured output)
- [x] **FSTUDY-004-09** — AI plan explanation ("why did Gemini schedule this?")
- [x] **FSTUDY-004-10** — Plan versioning (store AiPlanVersion, diff UI)

### M4 · AI Daily Brief

- [x] **FSTUDY-005-06** — AI daily brief card (generated on launch/demand in Today tab)

### M5 · Progress + Analytics

- [x] **FSTUDY-007-01** — Total-study metric
- [x] **FSTUDY-007-02** — Daily progress bars
- [x] **FSTUDY-007-03** — Subject progress (per-subject breakdown)
- [x] **FSTUDY-007-04** — Topic coverage (% per topic)
- [x] **FSTUDY-007-05** — Revision coverage
- [x] **FSTUDY-007-06** — Planned vs actual chart
- [x] **FSTUDY-007-07** — Streaks (current + longest)
- [x] **FSTUDY-008-01** — Daily trend chart
- [x] **FSTUDY-008-02** — Weekly trend chart
- [x] **FSTUDY-008-03** — Subject distribution (pie / bar)
- [x] **FSTUDY-008-04** — Study heatmap (week × time-of-day grid)
- [x] **FSTUDY-008-05** — Peak-time detection (require sufficient data before surfacing)
- [x] **FSTUDY-008-06** — Low-time detection
- [x] **FSTUDY-008-07** — Consistency analytics

### M6 · Adaptive Planning

- [x] **FSTUDY-009-01** — Progress summarizer (AI explanation of metrics)
- [x] **FSTUDY-009-02** — Neglected-topic detector
- [x] **FSTUDY-009-03** — At-risk exam detector
- [x] **FSTUDY-009-04** — Peak-time explainer
- [x] **FSTUDY-009-05** — AI daily brief (full generation)
- [x] **FSTUDY-009-06** — Weekly review generation
- [x] **FSTUDY-010-01** — Missed-session detection
- [x] **FSTUDY-010-02** — Schedule risk calculation (deterministic)
- [x] **FSTUDY-010-03** — AI replan generation (AiReplanService)
- [x] **FSTUDY-010-04** — Replan preview (diff old vs new)
- [x] **FSTUDY-010-05** — Accept / reject replan
- [x] **FSTUDY-010-06** — Replan history

### M8 · Notifications

- [x] **FSTUDY-012-01** — Session reminders
- [x] **FSTUDY-012-02** — Exam countdown notification
- [x] **FSTUDY-012-03** — Daily plan reminder (morning)
- [x] **FSTUDY-012-04** — Risk notification (at-risk topic)
- [x] **FSTUDY-012-05** — End-of-day review notification

### M9 · Security (baseline)

- [x] **FSTUDY-014-01** — Firebase App Check (production)
- [x] **FSTUDY-014-04** — AI prompt-injection protections
- [x] **FSTUDY-014-05** — Imported-file validation (MIME, size, sanitize)
- [x] **FSTUDY-014-06** — Sensitive-data logging review

---

## 🔵 P2 — Nice to have

### M7 · AI Coach

- [x] **FSTUDY-011-01** — AI chat UI (conversational screen)
- [x] **FSTUDY-011-02** — Context builder (minimal context per request)
- [x] **FSTUDY-011-03** — Quick prompts ("Why am I behind?", "Rebuild my week")
- [x] **FSTUDY-011-04** — Action intents (typed AI → app actions)
- [x] **FSTUDY-011-05** — Action confirmation UI
- [x] **FSTUDY-011-06** — Voice input (STT mic via native `RecognizerIntent`)
- [x] **FSTUDY-011-07** — Voice audio reader (TTS engine with auto-read and speaker buttons)
- [x] **FSTUDY-011-08** — Natural language study session logger & auto-committer (time range parser, XP reward, Room DB commit)

### M8 · Widgets

- [x] **FSTUDY-013-01** — Today widget
- [x] **FSTUDY-013-02** — Next-session widget
- [x] **FSTUDY-013-03** — Progress widget

### M9 · Auth + Cloud Sync (optional)

- [x] **FSTUDY-014-02** — Firebase Authentication (when cloud enabled)
- [x] **FSTUDY-014-03** — Cloud authorization rules (Firestore security rules)
- [x] **FSTUDY-015-01** — Data export
- [x] **FSTUDY-015-02** — Data import (validate before commit)
- [x] **FSTUDY-015-03** — Backup / restore
- [x] **FSTUDY-015-04** — Offline recovery
- [x] **FSTUDY-015-05** — Database migration tests

### M6 · Focus extras

- [x] **FSTUDY-006-04** — Sound / haptic settings

---

## 🟣 P3 — Future / post-launch

- [x] **FSTUDY-016-01** — Gamification (XP system, academic milestones & Scholar Progression levels)
- [x] **FSTUDY-016-02** — Advanced AI personalization (Chronotype energy curve profiling, Learning Techniques, Burnout Guard, AI Coach integration)
- [x] **FSTUDY-016-03** — Social / study buddy features (Shareable accountability progress card via native share sheet, buddy stats token parser & duel comparison)
- [x] **FSTUDY-016-04** — Tablet & large-screen layout (Adaptive NavigationRail for >=600dp, two-pane responsive layouts for Today, Planner, and Progress)

---

## 🎨 UI & Theme Polish (DEC-024)

- [x] **FSTUDY-017-01** — Typography overhaul & descender overlap elimination (`Typography.kt` standard line heights)
- [x] **FSTUDY-017-02** — `StatusPill` custom component replacing deformed M3 Badge components across all screens
- [x] **FSTUDY-017-03** — Theme & Display mode structure (Dark Slate, Midnight OLED pure black, Paper Light, System Default)
- [x] **FSTUDY-017-04** — Interactive app tutorial dialog (`AppTutorialDialog.kt`) with 5 tabs and voice syntax cheatsheet
- [x] **FSTUDY-017-05** — Tutorial triggers across TodayScreen (`?` icon), AiCoachScreen ("💡 Voice Guide" chip), and SettingsScreen

---

## ✅ Release Polish Completed (M10)

- [x] **Performance Audit** — Database indexes on all FKs and query columns; lambda state readers in Compose progress indicators
- [x] **Accessibility Audit** — Semantic content descriptions on all interactive and status icons; >= 48dp touch targets
- [x] **UX Edge-case Review** — Graceful empty states for 0 exams, 0 sessions, 0 topics, and offline operation
- [x] **Privacy & Security Review** — Local-first Room storage; SafeLogger PII redaction; App Check attestation; path-based Firestore security rules
- [x] **Google Play Release Package** — Complete store listing metadata, permissions justification, and release notes in `distribution/PLAY_STORE_METADATA.md`

---

## 🛡️ Anti-Bill & BYOK Architecture (DEC-028)

- [x] **FSTUDY-018-01** — 100% Anti-Bill Guarantee & zero hardcoded developer keys in APK/AAB build configuration
- [x] **FSTUDY-018-02** — Bring-Your-Own-Key (BYOK) DataStore persistence and Settings UI management
- [x] **FSTUDY-018-03** — Lightweight native REST client for Gemini 1.5 Flash with automatic fallback to offline heuristics
- [x] **FSTUDY-018-04** — 1-tap Google AI Studio link for free tier keys with masked visibility and local-only persistence
- [x] **FSTUDY-018-05** — Comprehensive test coverage across all 12 test suites passing 100%

---

## 🎨 Adaptive Launcher Icons & Lint Hardening (DEC-029)

- [x] **FSTUDY-019-01** — Modern adaptive launcher icon with Slate 900 base & illuminated study/beacon vector (`ic_launcher.xml`, `ic_launcher_round.xml`, `ic_launcher_background.xml`, `ic_launcher_foreground.xml`)
- [x] **FSTUDY-019-02** — Android 13+ Material You monochrome themed launcher icon support (`<monochrome>`)
- [x] **FSTUDY-019-03** — Extracted widget layout strings to `strings.xml` and eliminated typography lint warnings
- [x] **FSTUDY-019-04** — Fresh verified production packages generated in `distribution/` (debug APK 18.9 MB, release APK 12.3 MB, Google Play AAB 11.9 MB)

---

## 📑 Ingest Hardening & Calendar Timetable Interoperability (DEC-030)

- [x] **FSTUDY-020-01** — Native PDF stream text extractor (`PdfTextExtractor`) with standard JDK `Inflater` and `BT ... ET` text operator parsing
- [x] **FSTUDY-020-02** — Hybrid `AiSyllabusParser` supporting offline deterministic syllabus extraction + BYOK Gemini 1.5 Flash multimodal vision (OQ-005 resolved)
- [x] **FSTUDY-020-03** — RFC 5545 iCalendar (`.ics`) generator (`CalendarExportManager`) with exam milestone and study block event mapping
- [x] **FSTUDY-020-04** — Android `FileProvider` (`file_paths.xml`) and 1-tap calendar export in `PlannerScreen` (Week view) and `SettingsScreen` (Data Management)
- [x] **FSTUDY-020-05** — 14 unit test suites passing 100% with fresh verified production packages (debug APK 18.9 MB, release APK 12.3 MB, Google Play AAB 11.9 MB)




