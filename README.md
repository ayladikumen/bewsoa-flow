# ⚡ Bewsoa Flow

**An AI-adaptive weekly operating system — not another calendar.**

[![CI](https://github.com/ayladikumen/bewsoa-flow/actions/workflows/ci.yml/badge.svg)](https://github.com/ayladikumen/bewsoa-flow/actions/workflows/ci.yml)

Calendar apps store what you *say* you'll do. Bewsoa Flow closes the loop: it **measures** what you actually did, **explains** the patterns it finds, and an **AI assistant renegotiates the plan with you** — in chat, in plain language, with schema-constrained LLM output and human-in-the-loop approval on every single change.

Version 3.0 is a full redesign: warm paper, big rounded type, pastel checklists, and one idea everywhere — **the goal is the week; any single day is allowed to flex.**

Built with Kotlin + Jetpack Compose.

---

## The loop that makes it different

```
   plan ──▶ track ──▶ measure ──▶ explain ──▶ renegotiate ──▶ plan …
  (blocks)  (check-  (streak,     (insights   (assistant + Sunday coach
             offs)    XP, pace)    engine)     draft; you hit Apply)
```

1. **Track** — the day is a checklist, not a timetable. Blocks read *"~2h · evening"*; a pace bar keeps you honest about the clock (knob = the day passing, fill = the plan getting done) without drawing a single hour line.
2. **Measure** — streaks ("never miss twice"), per-track hours, XP that speaks human: your level as a badge, the runway to the next one, the week as seven dots, a chest you open by hand.
3. **Explain** — an on-device **insights engine** mines the last month: *"'Gym' on Thursdays is your weak spot — skipped 4 of the last 5."*
4. **Renegotiate** — talk to the **Assistant**, or let the Sunday coach draft next week from your real adherence. Every AI change arrives as a **draft card** that names its scope loudly and waits for your Apply. Nothing is ever applied silently.

## The assistant's contract: once, unless you say forever

The 3.0 headline feature. The assistant reads your plan, tasks and streak, and understands *scope*:

- *"Tonight I'm at a wedding"* → drafts a change to **that day only**. A per-date override layer sits in front of the standing program, so alarms, widgets, streak and XP follow it automatically — and the override evaporates once the day is over.
- *"Move gym to 18:00 **every week**"* → only an explicitly permanent request rewrites the standing weekly program.
- *"Add: 40 soru paragraf yarına"* → becomes a sized, scheduled, Eisenhower-classified task.

Ambiguous wording always resolves to *once*. Draft cards are labelled **TODAY ONLY / EVERY WEEK / NEW TASK** so scope is never a surprise.

## Features

- 🏠 **Home** — greeting dashboard: one honest completion ring, routine/task tiles, XP daily goal, deep-focus launcher, a peek at the week
- ✅ **Today** — pastel checklist grouped by daypart, springy round checks, hold-&-drag reorder, excused skips (3/week), catch-up for unlogged blocks, tasks with AI splitting and spaced-repetition reviews
- 🗓 **Week — the centre button** — week ring with pace copy (*"ahead of pace — Sunday will be light ✨"*), streak dots, level badge, weekly chest, and a browsable plan for any day of any week
- ✨ **Assistant** — Claude or Gemini chat grounded in your actual plan; one-time day edits, permanent program edits, task capture — all via draft cards
- 🧠 **Deep Focus** — commit to one thing and a length; live countdown notification; only confirmed time is logged
- 🎮 **XP economy** — blocks pay by length and mission-weight, daily goal at 80% of a perfect day, chest at 5 kept days, streak freezes, milestone bonuses, full-screen confetti moments
- 📱 **Home-screen widgets** (Glance) — current block, today's progress, streak; they recolor with your theme
- 🔔 **Task-end reminders** & a goal-tied **motivation engine** — exact alarms, boot-safe, three intensities
- 📝 **Weekly review** — writes itself from your logs; your three notes feed the Sunday coach
- 🎨 **Themes** — Sunrise (the 3.0 cream-and-indigo default) plus the original dark palettes

## Tech stack

| Layer | Choice |
| --- | --- |
| UI | Jetpack Compose, Material 3, Glance (widgets), bundled Baloo 2 + Nunito, spring-based motion system |
| State | ViewModel + Kotlin Flows, reactive program/override switching |
| Storage | Room + KSP (completions, tasks, focus, XP ledger), DataStore (settings, day overrides, chat history, pending drafts) |
| AI | Anthropic Messages API & Gemini generateContent, both schema-constrained (one structured call covers chat reply + action + payload); prompt + local time-parsing hardening |
| Scheduling | AlarmManager exact alarms, WorkManager (motivation chain, alarm re-sync, Sunday coach) |
| Quality | 94 JUnit unit tests, GitHub Actions CI (test + lint + assemble on every PR), R8 minified release |

## Project structure

```
app/src/main/java/ai/bewsoa/flow/
├── BewsoaFlowApp.kt          # bootstrap: program, overrides, theme, workers
├── data/
│   ├── WeeklyProgram.kt      # standing program (single source of truth)
│   ├── DayOverrides.kt       # per-date one-time edits — the "once" layer
│   ├── CustomProgram.kt      # AI-built standing override
│   ├── AiAssistant.kt        # chat: reply + edit_today/edit_week/add_task
│   ├── AiProgramUpdater.kt   # markdown → schedule + Sunday coach
│   ├── AiTaskParser.kt       # sentence → structured task, task → steps
│   ├── Xp.kt / XpRepository.kt  # the economy (pure Kotlin, unit-tested)
│   ├── Insights.kt / ProgramDiff.kt / BlockCodec.kt / Streak.kt
│   └── db/                   # Room entities, DAOs, database (v5)
├── notifications/            # alarms, receivers, motivation, CoachWorker
├── widget/                   # Glance home-screen widgets
└── ui/
    ├── home/ · day/ · progress/ (Week) · chat/ · settings/ (Profile)
    ├── focus/ · review/ · alerts/ · guide/
    ├── components/           # PastelRow, RoundCheck, DraftCard, pill nav,
    │                         # FlamePill, WeekDots, DayPaceBar, motion kit
    └── theme/                # Sunrise + dark palettes, bundled fonts
```

## Building

**Requirements:** Android Studio (or JDK 17 + Android SDK 34). Min Android 8.0 (API 26).

```bash
./gradlew assembleDebug          # gradlew.bat on Windows
./gradlew testDebugUnitTest      # unit tests
```

The APK lands in `app/build/outputs/apk/debug/`. To wake the AI features, add a free [Gemini](https://aistudio.google.com) or [Anthropic](https://console.anthropic.com) API key in Profile — keys and chat history never leave the device.

## The program it runs

The default weekly program lives in [docs/weekly_program.md](docs/weekly_program.md): YKS weekday mornings, TYT Saturdays, SAT evenings, project blocks at night, gym in between — and the one rule that protects everything: **never miss twice.** From there, the assistant, the coach and your own change requests evolve it week by week.

## License

[MIT](LICENSE) © 2026 Bewsoa AI

---

*Part of the Bewsoa AI ecosystem — alongside Exact Hour and Bewsoa AI Clock.*
