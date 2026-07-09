# ⚡ Bewsoa Flow

**An AI-adaptive weekly operating system — not another calendar.**

[![CI](https://github.com/ayladikumen/bewsoa-flow/actions/workflows/ci.yml/badge.svg)](https://github.com/ayladikumen/bewsoa-flow/actions/workflows/ci.yml)

Calendar apps store what you *say* you'll do. Bewsoa Flow closes the loop: it **measures** what you actually did, **explains** the patterns it finds, and every Sunday an **AI coach renegotiates next week's plan with you** — schema-constrained LLM output, human-in-the-loop approval, and stable block ids so your history survives every revision.

Built with Kotlin + Jetpack Compose. Dark, glowing, and fast.

---

## The loop that makes it different

```
   plan ──▶ track ──▶ measure ──▶ explain ──▶ renegotiate ──▶ plan …
  (blocks)  (check-  (streaks,    (insights   (AI coach drafts
             offs)    stats)       engine)     next week; you accept)
```

1. **Track** — the day's blocks come from one weekly program; you check them off (app, notification action, or home-screen widget).
2. **Measure** — streaks ("never miss twice"), per-track hours, 7-day charts.
3. **Explain** — an on-device **insights engine** mines the last month: *"'Gym' on Thursdays is your weak spot — skipped 4 of the last 5."*, *"The blocks you skip average 2.5h — much longer than the ones you finish."*
4. **Renegotiate** — Sunday evening a WorkManager job sends the current schedule + insights + your weekly review to **Claude or Gemini**, which returns next week's program as validated JSON with a coach note. You see a short diff of what would change and accept or dismiss. Nothing is ever applied without you.

You can also change the program any time in plain language — *"move gym to 18:00, add a Sunday run"* — and the AI applies exactly that, showing a grouped diff (`Gym: 17:00–18:45 → 18:00–19:45 · Mon–Fri`).

## Features

- 📋 **Today** — live schedule with a second-by-second countdown, progress ring, per-block check-off, and the coach's pending proposal when there is one
- 🧠 **AI program editing & weekly coach** — dual provider (Anthropic Claude / Google Gemini), structured outputs (JSON schema on both APIs), local diffing, stable ids that preserve completion history
- 📱 **Home-screen widget** (Glance) — the block running right now, minutes left, one-tap Done
- 🔍 **Insights** — adherence trend, weak spots, best/worst weekdays, overload detection; pure Kotlin, unit-tested
- 🔔 **Task-end reminders** — exact alarms with a "Mark as done" action, boot-safe, 6h re-sync
- ⚡ **Motivation engine** — goal-tied notifications at unpredictable times, three intensities
- 📊 **Progress** — streak counter, weekly charts, per-track hours vs. plan
- 🗓 **Weekly review** — Sunday template, partially auto-filled from tracking, feeds the coach

## Tech stack

| Layer | Choice |
| --- | --- |
| UI | Jetpack Compose, Material 3, Glance (widget), custom dark theme |
| State | ViewModel + Kotlin Flows, reactive program switching |
| Storage | Room + KSP (completions, reviews, notification log), DataStore (settings, pending proposals) |
| AI | Anthropic Messages API & Gemini generateContent, both schema-constrained; prompt + local time-parsing hardening |
| Scheduling | AlarmManager exact alarms, WorkManager (motivation chain, alarm re-sync, Sunday coach) |
| Quality | JUnit unit tests, GitHub Actions CI (test + lint + assemble on every PR), R8 minified release |

## Project structure

```
app/src/main/java/ai/bewsoa/flow/
├── BewsoaFlowApp.kt          # bootstrap: program, channels, workers, alarms
├── data/
│   ├── WeeklyProgram.kt      # built-in program (single source of truth)
│   ├── CustomProgram.kt      # AI-built override, reactive version flow
│   ├── AiProgramUpdater.kt   # Claude/Gemini calls: rebuild + weekly coach
│   ├── Insights.kt           # history → findings (unit-tested)
│   ├── ProgramDiff.kt        # old vs new schedule → short human diff
│   ├── Stats.kt / ProgramRepository.kt / SettingsRepository.kt
│   └── db/                   # Room entities, DAOs, database
├── notifications/            # alarms, receivers, motivation, CoachWorker
├── widget/                   # Glance home-screen widget
└── ui/                       # today / progress / review / alerts / settings
```

## Building

**Requirements:** Android Studio (or JDK 17 + Android SDK 34). Min Android 8.0 (API 26).

```bash
./gradlew assembleDebug          # gradlew.bat on Windows
./gradlew testDebugUnitTest      # unit tests
```

The APK lands in `app/build/outputs/apk/debug/`. To use the AI features, add a free [Gemini](https://aistudio.google.com) or [Anthropic](https://console.anthropic.com) API key in Settings — keys never leave the device.

## The program it runs

The default weekly program lives in [docs/weekly_program.md](docs/weekly_program.md): YKS weekday mornings, TYT Saturdays, SAT evenings, project blocks at night, gym in between — and the one rule that protects everything: **never miss twice.** From there, the coach and your change requests evolve it week by week.

## License

[MIT](LICENSE) © 2026 Bewsoa AI

---

*Part of the Bewsoa AI ecosystem — alongside Exact Hour and Bewsoa AI Clock.*
