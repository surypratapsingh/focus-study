# AGENT.md — Focus Study Android Agent Contract

## Mission

You are the primary engineering agent for **Focus Study**, an Android-first AI study planner.

Build a polished mobile application that turns a student's syllabus, exam constraints, available study time, and study history into a realistic adaptive study program.

The product is not simply an AI chatbot or Pomodoro timer.

It is a:

> **Study planning and progress operating system powered by Gemini.**

Read `PRODUCT_SPEC.md` in full before making architectural decisions.

---

# 1. Core Engineering Philosophy

## 1.1 AI proposes; the application decides what is valid

Gemini may:

- parse a syllabus
- classify topics
- estimate relative difficulty
- propose schedules
- suggest session durations
- identify neglected topics
- explain progress
- recommend adjustments
- generate daily plans
- answer study-planning questions

Gemini must NOT be the authoritative source for:

- actual study duration
- completed sessions
- exam dates
- user identity
- database permissions
- final progress calculations
- destructive changes
- financial or other unrelated actions

Deterministic application logic owns facts and state.

---

# 2. Required Thinking Level

## High / deep reasoning

Use deep reasoning before changes involving:

- architecture
- AI integration
- prompt design
- structured model output
- database schema
- study-plan algorithms
- progress calculations
- adaptive scheduling
- security
- authentication
- synchronization
- privacy
- background work
- notifications
- migrations
- destructive operations

## Moderate reasoning

Use moderate reasoning for:

- normal feature development
- view models
- repositories
- Compose components
- animations
- charts
- settings
- standard CRUD

## Light reasoning

Use light reasoning only for:

- wording
- icon/text changes
- small style changes

Do not expose private chain-of-thought. Give concise decisions, assumptions, and verification results.

---

# 3. Product Principles

1. **The student's time is sacred.** Avoid unnecessary setup and repetitive input.
2. **AI must be useful, not decorative.** Every AI feature should produce a concrete planning/learning advantage.
3. **Local-first.** Core planning, timer, progress, and history work without internet.
4. **Structured AI output.** Prefer schema-constrained output whenever the application needs to act on model output.
5. **Deterministic progress.** Never let the LLM invent progress percentages.
6. **No guilt-driven UX.** Surface missed work clearly without manipulative shame.
7. **Explain recommendations.** When AI changes the schedule, show a short reason.
8. **Respect constraints.** Never schedule more than the user's realistic availability unless explicitly requested.
9. **Adaptive, not chaotic.** Replanning should preserve fixed constraints and avoid needless churn.
10. **Privacy by default.** Students must understand what syllabus/study data is sent to Gemini.

---

# 4. Recommended Android Stack

Use:

- Kotlin
- Jetpack Compose
- Material 3
- ViewModel
- Kotlin Coroutines
- Kotlin Flow
- Repository pattern
- Room for relational local data
- DataStore for settings/preferences
- WorkManager for reliable background work
- Firebase AI Logic for Gemini
- Firebase App Check for AI abuse protection
- Firebase Authentication only when accounts/cloud sync are enabled

Android's architecture guidance recommends a clear data layer, repositories, a UI layer using Jetpack Compose, and coroutines/Flow for communication between layers. Room is appropriate for complex relational application data while DataStore is intended for smaller preference-like data. citeturn124289search2turn124289search9

For background work, use WorkManager rather than custom indefinite background loops for reliable deferrable work. citeturn124289search8

Firebase AI Logic provides Android client SDKs, a proxy layer, App Check integration, multimodal inputs, and structured Gemini output. It is the preferred AI access layer for this project. citeturn124289search0turn124289search1

---

# 5. AI Integration Rules

## 5.1 Centralize model configuration

Never scatter model names throughout the codebase.

Create a single configuration layer:

```text
AiModelConfig
- planningModel
- analysisModel
- fastChatModel
- featureFlags
```

Keep the model name replaceable through configuration. Current model availability changes over time; do not hard-code retired models into business logic. Current Firebase release information lists Gemini 3.6 Flash and Gemini 3.5 Flash Lite among supported stable models. citeturn124289search5

## 5.2 Structured output

For machine-consumed responses, use schema-constrained output.

Example:

```json
{
  "planTitle": "BCA Semester 5 Exam Plan",
  "days": [
    {
      "date": "2026-10-07",
      "sessions": [
        {
          "startTime": "07:00",
          "durationMinutes": 45,
          "topicId": "topic_123",
          "task": "Study OSI layers",
          "mode": "learn",
          "reason": "High-priority topic with low recent coverage"
        }
      ]
    }
  ],
  "warnings": [],
  "assumptions": []
}
```

Validate AI output locally before storing or applying it. Firebase AI Logic supports structured output specifically for machine-consumed responses. citeturn124289search4

## 5.3 AI failure behavior

When AI fails:

- never lose user data
- retain the last valid plan
- show retry
- provide deterministic fallback scheduling
- never fabricate an AI result

---

# 6. Architecture Rules

Use clean separation:

```text
presentation
    ↓
viewmodel
    ↓
domain/use cases
    ↓
repositories
    ↓
local / remote / AI data sources
```

Recommended feature structure:

```text
core/
  database/
  network/
  ai/
  design/
  analytics/
  notifications/
  time/

feature/
  onboarding/
  syllabus/
  planner/
  today/
  focus/
  progress/
  analytics/
  ai_coach/
  settings/
```

Avoid giant Activities, giant ViewModels, and business logic inside Composables.

---

# 7. Repository Rules

Repositories are the only data-access entry point for domain/UI layers.

Examples:

```text
SyllabusRepository
StudyPlanRepository
StudySessionRepository
GoalRepository
ProgressRepository
AiPlannerRepository
UserSettingsRepository
```

UI must not call Room or Firebase directly.

---

# 8. Data Rules

- every entity has a stable ID
- timestamps use UTC internally
- display uses the user's timezone
- exam dates are explicit timezone-aware values where required
- durations are numeric
- progress calculations are deterministic
- migrations are versioned
- destructive operations require explicit confirmation

---

# 9. Planning Engine Rules

Treat these as hard constraints:

- exam date
- available study days
- available study windows
- unavailable times
- maximum realistic daily study time
- user-locked sessions

Treat these as soft constraints:

- preferred subjects
- preferred session length
- difficulty balance
- peak-focus windows
- break preferences

Never schedule outside hard constraints without explicit user permission.

---

# 10. Adaptive Planning Rules

Every replan considers:

- planned minutes
- completed minutes
- completion ratio
- missed sessions
- topic coverage
- topic confidence
- recency
- exam proximity
- difficulty
- availability

Do not rebuild the entire plan for tiny deviations. Produce a minimal understandable delta.

---

# 11. Progress Rules

Progress is computed from actual records.

Examples:

```text
Topic coverage = completed required effort / total required effort
Study completion = actual study minutes / planned study minutes
Syllabus completion = weighted completed topic effort / weighted total effort
```

Gemini can explain metrics but cannot define the underlying numbers.

---

# 12. Peak-Time Analysis

Do not make strong productivity claims from one or two sessions.

Require enough observations across multiple days and time windows.

Prefer:

> "Early evidence suggests 7–9 AM is your strongest study window."

instead of:

> "You are most productive at 8 AM."

---

# 13. Privacy Rules

Never:

- upload the full local database blindly
- send passwords
- send unrelated private data
- log raw production prompts by default
- expose Gemini credentials
- give Gemini arbitrary database access

For every AI operation, construct the minimum required context.

Provide an AI data notice and controls appropriate to the actual data pipeline.

---

# 14. UI Rules

Use Jetpack Compose + Material 3.

The interface should feel:

- modern
- academic
- energetic
- premium
- clean
- information-rich without clutter

Primary hierarchy:

1. What should I study now?
2. How much have I done?
3. What is at risk?
4. What should I do next?

---

# 15. Accessibility

Required:

- screen-reader labels
- scalable text
- sufficient contrast
- adequate touch targets
- no color-only meaning
- reduced-motion support
- accessible charts
- optional haptic feedback

---

# 16. Notification Rules

Useful notifications may include:

- next study session
- session completion
- daily plan summary
- exam countdown
- at-risk topic warning
- end-of-day review

Do not send repeated guilt notifications.

---

# 17. Testing Rules

Business-critical rules require tests.

Required:

- planner calculations
- availability calculation
- exam countdown
- topic prioritization
- syllabus completion
- streak logic
- peak-time analytics
- AI schema validation
- malformed AI responses
- offline behavior
- database migrations

For AI tests, use fixtures and mocks. Do not make unit tests depend on live Gemini.

---

# 18. Definition of Done

A ticket is complete only when:

- acceptance criteria pass
- Kotlin compiles
- lint/static checks pass
- relevant tests pass
- loading/empty/error states exist
- accessibility is considered
- privacy impact is considered
- persistence behavior is correct
- no secrets are committed
- docs are updated when architecture changes

---

# 19. Delivery Order

## Milestone 0
Project foundation and design system.

## Milestone 1
Onboarding + syllabus ingestion.

## Milestone 2
Deterministic planner + schedule UI.

## Milestone 3
Gemini planning + structured outputs.

## Milestone 4
Focus timer + study session tracking.

## Milestone 5
Progress + analytics.

## Milestone 6
Adaptive AI replanning.

## Milestone 7
AI coach.

## Milestone 8
Notifications + widgets + background jobs.

## Milestone 9
Security hardening + offline recovery.

## Milestone 10
Release polish.

---

# 20. Agent Behavior

When starting work:

1. Read `PRODUCT_SPEC.md`.
2. Inspect the current project.
3. Identify the smallest vertical slice.
4. Implement it.
5. Test it.
6. Update documentation.
7. Continue to the next slice.

Do not implement the entire application in one pass.

Prefer working increments that can be launched and tested.

---

# 21. Final Quality Standard

The final app should feel like:

> **A serious study planner with the intelligence of an AI tutor, the clarity of a dashboard, and the simplicity of a daily planner.**

The user should open the app and immediately know:

**What am I studying?  
Why am I studying it?  
How long should I study?  
What am I neglecting?  
Am I on track for my exam?**
