# Focus Study

An Android study planner. You give it your exams, syllabus and available time; it builds a day-by-day study plan, runs a focus timer, tracks progress, and (optionally) uses Google Gemini for syllabus parsing, plan generation, re-planning and a study coach.

Package `com.focusstudy.app` · version 1.0.2 · minSdk 26 · compileSdk 35 · Kotlin + Jetpack Compose (Material 3).

## Features

Drawn from the code under `app/src/main/java/com/focusstudy/app/`:

- **Onboarding and syllabus import** (`feature/onboarding`, `feature/syllabus`): enter exams and syllabus as text or import a PDF (on-device text extraction in `PdfTextExtractor`); imports are validated (size, MIME type, injection checks) in `core/security/FileImportValidator`.
- **Deterministic planner** (`feature/planner/PlannerEngine`): schedules topics into study sessions inside your declared availability; re-plan dialog for missed work.
- **Today and Focus** (`feature/today`, `feature/focus`): next session, focus timer, session tracking, optional ambient sounds generated on-device (`core/audio`).
- **Progress and analytics** (`feature/progress`): deterministic progress and readiness calculations, streaks, gamification tiers and badges, chronotype-style personalisation.
- **Active recall** (`feature/recall`): flashcard / quiz engine for spaced practice.
- **AI coach and planning** (`feature/ai`, `core/ai`): Gemini-backed coach, syllabus parser, plan and re-plan services with schema validation of responses. The API key is read from `local.properties` / the `GEMINI_API_KEY` environment variable at build time, or supplied by the user in-app (see `decision.md`, DEC-028). Without a key, offline fallbacks are used where the code provides them.
- **Notifications, widget, background work** (`core/notification`, `core/widget`, `core/worker`).
- **Calendar export** to iCalendar (`core/calendar`) and **backup/restore** (`core/backup`).
- **Local-first storage**: Room database and DataStore preferences (`core/database`, `core/datastore`).

## Build and test

Requirements: JDK 17 or newer, Android SDK (platform 35). Put the SDK path in `local.properties` (`sdk.dir=...`, git-ignored) or set `ANDROID_HOME`.

```bash
./gradlew assembleDebug          # debug APK
./gradlew testDebugUnitTest      # JVM unit tests (also run by CI)
```

Optional: add `GEMINI_API_KEY=...` to `local.properties` to bake in a default key. Never commit it.

CI: `.github/workflows/ci.yml` runs the unit tests on every push and pull request.

## Project layout

```
app/src/main/java/com/focusstudy/app/
  core/      ai, audio, auth, backup, calendar, database, datastore, design, di,
             notification, repository, security, widget, worker
  feature/   ai, common, focus, main, onboarding, planner, progress, recall,
             settings, syllabus, today
app/src/test/java/com/focusstudy/app/   JVM unit tests
web/                                     landing page and APK downloads
distribution/                            Play Store listing metadata
firestore.rules                          Firestore security rules
```

## Project docs

- `project.md`: overview, architecture and milestones
- `PRODUCT_SPEC (1).md`: full product specification
- `AGENT (1).md`: contract for AI coding agents working on the repo
- `decision.md`: decision log (DEC-NNN) · `progress.md`: change log · `todo.md`: task list
