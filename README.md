# ⚡ Bewsoa Flow

**The daily engine behind Bewsoa AI.**

A personal Android app that turns one weekly program — YKS mornings, TYT Saturdays, SAT evenings, Exact Hour nights, gym in between — into a daily checklist with smart reminders, streak tracking, and motivation that actually talks about *your* goals: Bewsoa AI, Exact Hour, and a university abroad.

Built with Kotlin + Jetpack Compose. Dark, glowing, and fast.

---

## Features

### 📋 Today — the daily checklist
- The full day's schedule (weekday / TYT Saturday / Reset Sunday variants), generated from [the weekly program](docs/weekly_program.md)
- Live **"NOW" card** with a second-by-second countdown to the end of the current block
- Animated progress ring for the day, per-block check-off, "ended but not logged" nudges
- SAT focus alternates automatically (R&W / Math by weekday) and each evening shows its project focus (Exact Hour firmware, OpenSCAD, Bewsoa AI Clock…)

### 🔔 Task-end reminders
- **20 minutes after each block ends** (configurable 0–60 min), a notification asks if it got done — with a one-tap **"Mark as done"** action
- Already checked it off? No notification. The app doesn't nag about finished work
- Exact alarms (with a graceful fallback when the permission isn't granted), rescheduled on boot and refreshed by a 6-hour background sync

### ⚡ Motivation engine
- Random notifications at unpredictable times, 09:00–23:00
- Every line is tied to a real goal — no empty "you can do it" filler:
  > *"Bewsoa AI grows one commit at a time. Tonight: one small task, done fully."*
  >
  > *"Every net you add today is a door opening at a university abroad. Sit down and collect."*
- Time-aware pools: YKS talk in the morning, gym talk at five, project talk at night, streak talk on Sunday evenings
- Three intensities: Chill (~2/day), Normal (~4/day), Beast (~7/day)

### 📊 Progress — how you're actually doing
- 🔥 **Streak counter** built on the program's one rule: **never miss twice** (a day counts when ≥60% of its blocks are done)
- 7-day completion chart + this-week overview
- Per-track weekly stats: YKS, TYT, SAT, Projects hours vs. plan; gym as sessions

### 🗓 Weekly Review
- The Sunday review template from the program, as a form — TYT score, SAT hours, project notes, sleep, energy, next week's single most important task
- YKS blocks kept and gym sessions **auto-filled from your tracking**
- Past weeks stored and listed, so trends are visible

---

## Screens

| Today | Progress | Review | Settings |
| --- | --- | --- | --- |
| Live schedule + countdown | Streak, chart, track stats | Sunday template form | Reminders, motivation, permissions |

*(screenshots coming after first device install)*

## Tech stack

| Layer | Choice |
| --- | --- |
| UI | Jetpack Compose, Material 3, custom dark theme |
| State | ViewModel + Kotlin Flows (`StateFlow`, Room-backed) |
| Storage | Room (completions, weekly reviews) + DataStore (settings) |
| Scheduling | `AlarmManager` exact alarms (task reminders) + WorkManager (motivation chain, 6h alarm re-sync) |
| Language | Kotlin 2.0, coroutines everywhere |

## Project structure

```
app/src/main/java/ai/bewsoa/flow/
├── BewsoaFlowApp.kt          # channels + workers + alarms bootstrap
├── MainActivity.kt           # edge-to-edge shell, notification permission
├── data/
│   ├── WeeklyProgram.kt      # the weekly program as data (single source of truth)
│   ├── TaskBlock.kt, Track.kt
│   ├── Stats.kt              # weekly stats computation
│   ├── ProgramRepository.kt  # completions, streak logic ("never miss twice")
│   ├── SettingsRepository.kt # DataStore-backed preferences
│   └── db/                   # Room: entities, DAOs, database
├── notifications/
│   ├── TaskAlarmScheduler.kt # end-of-block alarms (today + tomorrow, idempotent)
│   ├── TaskAlarmReceiver.kt  # fires the reminder (skips finished blocks)
│   ├── MarkDoneReceiver.kt   # "Mark as done" notification action
│   ├── MotivationWorker.kt   # self-rechaining random motivation
│   ├── ScheduleSyncWorker.kt # 6h safety net for alarms
│   ├── BootReceiver.kt       # reschedule after reboot/update
│   ├── NotificationHelper.kt # channels + builders
│   └── Motivator.kt          # the goal-tied quote pools
└── ui/
    ├── today/  progress/  review/  settings/   # one package per screen
    ├── components/           # GlowCard, ProgressRing, StatBar…
    └── theme/                # colors, typography, dark-first theme
```

## Building

**Requirements:** Android Studio (or JDK 17 + Android SDK 34). Min Android 8.0 (API 26).

```bash
# Android Studio: File → Open → this folder, then Run ▶
# Or from the command line:
./gradlew assembleDebug        # gradlew.bat on Windows
```

The APK lands in `app/build/outputs/apk/debug/`.

> On Android 13+ the app asks for notification permission on first launch.
> For minute-precise reminders, also allow **Alarms & reminders** — Settings tab → "Needs attention" card (only shows if it's missing).

## The program it runs

The full weekly program lives in [docs/weekly_program.md](docs/weekly_program.md). The short version:

| Track | Weekly shape |
| --- | --- |
| 📚 YKS | Weekday mornings → 17:00, fixed + evening review session |
| 📝 TYT | Saturday exam → done by 14:00, reviewed same evening |
| 🇺🇸 SAT | 45 min weekday evenings (R&W/Math alternating) + Sunday deep session |
| 💻 Projects | Exact Hour & Bewsoa AI Clock — ~1h evenings + weekend blocks |
| 🏋️ Gym | 4–5 long sessions, right after YKS ends |

And the one rule that protects everything: **never miss twice.**

## Roadmap

- [ ] Home-screen widget with the current block + countdown
- [ ] TYT/SAT score history charts from weekly reviews
- [ ] Mistake-log quick capture (`yks-mistakes.md` / `sat-mistakes.md` style)
- [ ] Export weekly reviews as markdown

## License

[MIT](LICENSE) © 2026 Bewsoa AI

---

*Part of the Bewsoa AI ecosystem — alongside Exact Hour and Bewsoa AI Clock.*
