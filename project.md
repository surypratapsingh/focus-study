# Focus Study — Project Overview

> **"A serious study planner with the intelligence of an AI tutor, the clarity of a dashboard, and the simplicity of a daily planner."**

---

## What Is This?

**Focus Study** is an Android-first AI study planner that turns a student's syllabus, exam deadline, and available time into a realistic adaptive study program powered by Gemini.

It is **not** a Pomodoro timer or an AI chatbot. It is a **study planning and progress operating system**.

---

## Core Value Proposition

| Question | Answer the app gives |
|---|---|
| What should I study? | Prioritized next session |
| When should I study it? | Scheduled time window |
| Why is it important? | AI explanation with data |
| How long? | Session duration from plan |
| Am I progressing? | Deterministic progress bars |
| What am I neglecting? | Neglected topic cards |
| Am I on track? | Exam readiness score |
| What should change? | AI adaptive replan |

---

## Platform & Stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| State | ViewModel + Kotlin Flow |
| Local DB | Room |
| Preferences | DataStore |
| Background | WorkManager |
| AI | Gemini via Firebase AI Logic |
| AI Protection | Firebase App Check |
| Auth (optional) | Firebase Authentication |

---

## Architecture

```
Android UI (Jetpack Compose)
        ↓
   ViewModels
        ↓
   Use Cases
        ↓
  Repositories
        ↓
┌──────────────┬─────────────────┬────────────────┐
│  Room DB     │  Firebase AI    │  Firebase      │
│  (local)     │  Logic (Gemini) │  (cloud opt.)  │
└──────────────┴─────────────────┴────────────────┘
```

### Feature Module Structure

```
app/
├── core/
│   ├── database/          # Room DB, DAOs, entities, migrations
│   ├── network/           # Network utilities, connectivity
│   ├── ai/                # AiModelConfig, AiPromptRepository, AiResponseValidator
│   ├── design/            # Theme, colors, typography, shared components
│   ├── analytics/         # Deterministic metrics engine
│   ├── notifications/     # Notification channels, builders
│   └── time/              # UTC utilities, timezone helpers
│
└── feature/
    ├── onboarding/        # Welcome → wizard → plan setup
    ├── syllabus/          # Input, Gemini parser, review/edit
    ├── planner/           # Deterministic planner + Gemini plan gen
    ├── today/             # Home screen, next session, timeline
    ├── focus/             # Timer, pause/resume, session record
    ├── progress/          # Progress bars, streaks, planned vs actual
    ├── analytics/         # Charts, heatmap, peak-time
    ├── ai_coach/          # Chat UI, context builder, action intents
    └── settings/          # Theme, notifications, AI consent, DataStore
```

---

## AI Services

```
AiPlannerService       — generates macro plan + daily sessions
AiSyllabusParser       — converts raw syllabus to structured topics
AiInsightService       — progress summaries, risk alerts, weekly review
AiCoachService         — conversational study coaching
AiReplanService        — adaptive schedule adjustments
AiPromptRepository     — versioned prompts (planner.v1, coach.v1…)
AiResponseValidator    — validates schema before applying output
```

---

## Room Entities

```
StudentProfile, Exam, Subject, Unit, Topic, Subtopic
StudyPlan, StudyPlanDay, StudySession, StudyAttempt
Goal, AvailabilityWindow, StudyPreference
ProgressSnapshot, ActivityEvent
AiInsight, AiPlanVersion
Achievement
```

---

## Key Repositories

```
SyllabusRepository
StudyPlanRepository
StudySessionRepository
GoalRepository
ProgressRepository
AiPlannerRepository
UserSettingsRepository
```

---

## Milestones

| # | Name | Status |
|---|---|---|
| M0 | Project foundation + design system | 🔲 Not started |
| M1 | Onboarding + syllabus ingestion | 🔲 Not started |
| M2 | Deterministic planner + schedule UI | 🔲 Not started |
| M3 | Gemini planning + structured outputs | 🔲 Not started |
| M4 | Focus timer + session tracking | 🔲 Not started |
| M5 | Progress + analytics | 🔲 Not started |
| M6 | Adaptive AI replanning | 🔲 Not started |
| M7 | AI coach | 🔲 Not started |
| M8 | Notifications + widgets + background jobs | 🔲 Not started |
| M9 | Security hardening + offline recovery | 🔲 Not started |
| M10 | Release polish | 🔲 Not started |

---

## Engineering Principles

1. **AI proposes; app decides.** Gemini never owns facts or state.
2. **Deterministic progress.** LLM cannot invent numbers.
3. **Local-first.** Timer, plan, history work offline.
4. **Structured AI output.** Schema-constrained + validated before use.
5. **Minimal context.** Send only what Gemini needs per request.
6. **No guilt UX.** Surface missed work without shame.
7. **Explain AI changes.** Always show reason when plan adjusts.
8. **Respect constraints.** Never exceed declared availability.
9. **Privacy by default.** User controls what data goes to Gemini.
10. **Ponytail mindset.** Best code is code never written — no over-engineering.

---

## Priority Tiers

| Tier | Features |
|---|---|
| **P0** | Onboarding, syllabus input, deterministic planner, daily plan, focus timer, session tracking, progress, Room, core UI |
| **P1** | Gemini syllabus parser, Gemini plan gen, adaptive replan, AI insights, analytics, peak-time, notifications |
| **P2** | AI coach, widgets, cloud sync, advanced recommendations |
| **P3** | Gamification, social, advanced AI personalization |

---

## Definition of Done (per ticket)

- [ ] Acceptance criteria pass
- [ ] Kotlin compiles, lint passes
- [ ] Relevant tests pass
- [ ] Loading / empty / error states handled
- [ ] Accessibility considered
- [ ] Privacy impact considered
- [ ] Persistence behavior correct
- [ ] No secrets committed
- [ ] Docs updated if architecture changed
