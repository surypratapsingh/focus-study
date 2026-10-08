# Focus Study — Google Play Store Metadata & Release Package

## 1. App Title & Basic Details

- **App Name:** Focus Study: AI Exam Planner
- **Short Description (max 80 chars):**  
  Adaptive AI study planner turning syllabus & exams into focused daily programs.
- **Category:** Education / Productivity
- **Content Rating:** PEGI 3 / Everyone
- **Package Name:** `com.focusstudy.app`
- **Current Version:** `1.0.0` (Version Code: `1`)

---

## 2. Full Description (max 4,000 chars)

Master your exams without stress or burnout. **Focus Study** is the intelligent, local-first study operating system that transforms your syllabus, exam dates, and daily availability into an adaptive, guaranteed study roadmap.

Whether preparing for university finals, board examinations, competitive entrance tests, or professional certifications, Focus Study eliminates the guesswork of "what should I study today?"

### 🚀 Key Features

#### 1. Smart Syllabus Ingestion & Structuring
- Import study materials via PDF documents, camera syllabus photos, or quick text outline.
- Instant topic hierarchy extraction: Subjects → Units → Chapters → Key Topics.
- Difficulty and priority scoring ensures high-weightage topics receive prime focus.

#### 2. Deterministic Planning & Macro Roadmap
- Mathematical scheduling engine guarantees full syllabus coverage before your exam day.
- Spaced revision intervals automatically interspersed to lock knowledge into long-term memory.
- Pre-exam buffer protection reserves dedicated time for full mock exams and final reviews.

#### 3. Focused Pomodoro Timer & Session Tracking
- Distraction-free countdown timer tailored to your custom interval preferences (25/5, 45/10, or 60/10).
- Real-time XP rewards and session attempt logging.
- Instant home screen widget updates keep your daily momentum front and center.

#### 4. Adaptive AI Replanning (Powered by Gemini)
- Fall behind on a busy day? Never worry.
- Focus Study detects missed sessions and recalculates optimal, minimal schedule adjustments.
- Pinned sessions remain locked, while flexible blocks adaptively rebalance.

#### 5. Progress, Heatmaps & Scholar Progression
- 7x5 time-of-day study density heatmap reveals your peak cognitive productivity windows.
- Planned vs. actual adherence analytics keep you accountable.
- Tasteful Scholar Progression levels and academic milestones reward consistent effort.

#### 6. Privacy & Local-First Philosophy
- **Your data belongs to you:** All study sessions, syllabi, notes, and progress records reside strictly on your device.
- Full offline capability: plan, track, and study anywhere without an internet connection.
- One-tap complete JSON backup export and restore.

---

## 3. Permissions Justification

| Permission | Purpose |
|---|---|
| `android.permission.POST_NOTIFICATIONS` | Scheduled study block alarms, morning daily focus briefs, and countdown milestones. |
| `android.permission.RECORD_AUDIO` | Hands-free AI Coach speech recognition and natural voice study session logging. |
| `android.permission.VIBRATE` | Gentle tactile haptic pulses when Pomodoro focus and rest intervals complete. |
| `android.permission.CAMERA` *(Optional)* | Ingest printed syllabus handouts or study guides directly into topic lists. |
| `android.permission.INTERNET` | Optional Gemini AI strategic explanations and cloud sync attestation via Firebase App Check. |

---

## 4. Release Notes (v1.0.0)

- Initial production release of Focus Study Operating System.
- Complete 6-phase onboarding and intelligent syllabus parser (PDF, camera, manual outline).
- Deterministic mathematical planner with spaced revision intervals and pre-exam buffer protection.
- Focus Pomodoro timer with pause/resume, custom intervals, study logging, and XP rewards.
- Analytics dashboard with study heatmaps, peak window detection, and Scholar tier progression.
- Adaptive Gemini replanning and conversational hands-free AI Study Coach with STT & TTS.
- Advanced AI Personalization: Circadian Chronotype profiling (Early Bird, Daylight Achiever, Night Owl, Adaptive Explorer) & Cognitive Fatigue Guard.
- Social Study Buddy accountability cards with native Android share sheet and peer stat comparisons.
- Responsive large-screen & tablet workstation layout with adaptive NavigationRail.
- Premium visual polish: Midnight OLED pure black mode, Paper Light mode, and standard proportional typography.
- Interactive in-app guide and voice command syntax cheatsheet.
- Home screen widget for one-tap focus sessions and exam countdown.
- Local-first Room SQLite storage with validated JSON backup and restore.
