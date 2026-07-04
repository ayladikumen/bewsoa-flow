# Weekly Program — Study + Build

> Same format as before: works in Obsidian, VS Code, any editor.
> Times are **anchors, not handcuffs**. "~19:30" means "roughly then." What matters is the order of blocks and the weekly totals.

---

## What this is

YKS is the backbone: every weekday, morning to 5pm, no exceptions. TYT exam every Saturday until 2pm. Everything else — gym, SAT, and coding the main projects (Exact Hour first) — is built around that skeleton so nothing collides and nothing gets dropped.

### The four tracks

| Track | What it means weekly |
| --- | --- |
| 📚 YKS | Weekday mornings → 17:00. Fixed. This is the priority. |
| 📝 TYT | Saturday exam (done by 14:00) + same-day mistake review |
| 🇺🇸 SAT | Short weekday evening sessions + one deep Sunday session |
| 💻 Projects | Exact Hour and other main repos, via Claude Code — evenings + weekend blocks |
| 🏋️ Gym | 4–5 sessions/week, slotted where it fits best each day |

---

## Version A — Time-based (flexible anchors)

### Weekdays (Mon–Fri)

| Rough time | Block | Notes |
| --- | --- | --- |
| Morning → 17:00 | 📚 **YKS** | Breaks and lunch happen inside this block however works for you |
| ~17:00–18:45 | 🏋️ **Gym (long session)** | ~1.5h+. Proper warm-up, full program, no rushing out |
| ~19:00–19:30 | 🍽 Dinner | Quick — dinner lands around 7/7:30. Eat, don't linger |
| ~19:30–21:15 | 📚 **Study session 2** | ~1.75h of YKS: review the morning (active recall, mistake log) + question drills on the day's topics |
| ~21:15–22:00 | 🇺🇸 SAT | ~45 focused min. Reading & Writing one day, Math the next — alternate |
| ~22:00–23:00 | 💻 Project block | Exact Hour, main repos. ~1h, one small task per evening — a commit, a solved problem, an OpenSCAD tweak |
| **23:00 →** | 🔋 **Free time** | Yours. Guilt-free |

> If YKS drained you completely on a given day: drop **one** of the three evening blocks (study 2, SAT, or project) — never two. Study session 2 is the last one you should sacrifice; it's what makes the morning stick.

### Saturday

| Rough time | Block | Notes |
| --- | --- | --- |
| Morning → 14:00 | 📝 **TYT exam** | Fixed |
| ~14:00–15:00 | 🍽 Lunch + decompress | You earned it |
| ~15:00–17:00 | 🏋️ **Gym (2h)** | Longest session of the week — no time pressure |
| ~17:15–19:00 | 💻 **Project session** | Best slot for hardware work on Exact Hour (rocker pad, wiring, testing) — table space and daylight |
| ~19:00–19:15 | 🍽 Quick dinner | — |
| ~19:15–21:45 | 📚 **YKS (2.5h)** | Go over the TYT while it's fresh, then drill weak topics |
| **21:45 →** | 🔋 **Free time** | — |

### Sunday

| Rough time | Block | Notes |
| --- | --- | --- |
| Morning (sleep in) | — | Recovery matters. Start when you start |
| ~10:30–13:00 | 🇺🇸 **SAT deep session** | Timed section or full practice set, then mark and review immediately |
| ~13:00–14:30 | 🍽 Lunch + break | — |
| ~14:30–17:30 | 💻 Project block | Finish whatever Saturday's block left open. Push to GitHub so the week ends clean |
| ~17:30–18:30 | 🏋️ Gym (optional/light) | Only if you're at 4 sessions or fewer this week |
| ~18:30–19:00 | 🗓 Weekly review | 20 min, plain markdown note in the repo or a notebook. Template below |
| **21:45 →** | 🔋 **Free time** | — |

---

## Version B — Non-timed (quota-based)

Use this when the day gets scrambled (appointments, family stuff, bad sleep). The clock doesn't matter — hitting the quotas does.

### Fixed, non-negotiable
- **YKS:** every weekday, morning until 5pm
- **TYT:** every Saturday, done by 2pm — go over it during Saturday evening's YKS block while it's fresh
- **Free time floor:** after 11pm weekdays, after 9:45pm weekends — protected in both directions (you get it, and you stop working at it)

### Weekly quotas (fill them wherever they fit)

| Track | Weekly target | Natural home |
| --- | --- | --- |
| 📚 YKS evening session | ~8–9h (~1.75h/weekday) | After dinner — review + drills |
| 🇺🇸 SAT | ~5–6h | 5 × 45min weekday evenings + Sunday deep session |
| 💻 Projects | ~9–10h | ~1h weekday evenings + Sat/Sun blocks |
| 🏋️ Gym | 4–5 long sessions (~1.5h) | Right after YKS ends at 5pm |
| 🗓 Review | 20 min | Sunday evening |

### Daily priority order (when time is short)
1. YKS block (fixed — happens no matter what)
2. Gym — the long session is the point; don't shrink it two days in a row
3. Study session 2 (YKS review) — this is what converts the morning into retention
4. SAT hour
5. Project work — even 30 min of real progress (one commit, one solved problem) keeps the streak alive

### Rules that make Version B work
- **One task per project session.** "Work on Exact Hour" is not a task. "Get the rocker pad OpenSCAD file rendering" is.
- **End every project session with a commit.** Even a WIP commit. Claude Code + the repo is your memory — not your head.
- **SAT alternates:** R&W, Math, R&W, Math across the week. Sunday session targets whatever the week showed is weakest.
- **Wrong answers get written down** — a plain markdown file per subject (`yks-mistakes.md`, `sat-mistakes.md`) in your notes or repo. Reread it before the next TYT/practice set. No apps, no cards — just a running list.

---

## Project track — main repos

Priority order right now:

| Priority | Project | Current focus |
| --- | --- | --- |
| 1 | 🕐 **Exact Hour** | Rocker pad input redesign: cross-trigger prevention, roll stability, switch geometry → parametric OpenSCAD file → print/test → wire into the Python firmware |
| 2 | 🕐 Bewsoa AI Clock (web) | Keep alive: small evening tasks — reminders, settings, polish. One weekday evening per week is enough |
| 3 | Anything new | Only after Exact Hour's input redesign ships |

**Workflow:** everything through Claude Code on the GitHub repo. Weekday evenings = software (firmware, OpenSCAD iterations, web app). Saturday deep block = hardware (printing, assembly, physical testing) since it needs table space and daylight energy.

Suggested weekday rhythm (loose):

| Day | Evening project focus |
| --- | --- |
| Mon | Exact Hour — firmware/software |
| Tue | Exact Hour — design (OpenSCAD, geometry) |
| Wed | Bewsoa AI Clock — one small feature |
| Thu | Exact Hour — continue/finish the week's task |
| Fri | Lightest day: docs, cleanup, plan Saturday's hardware session |

---

## SAT plan (no apps, just structure)

| Action | When |
| --- | --- |
| Alternate R&W / Math, 1h weekday evenings | Ongoing |
| One timed section or practice set | Every Sunday |
| Mark immediately, log every wrong answer in `sat-mistakes.md` | Same session |
| Book test date (Nov–Dec 2026) + find the test center | This month if not done |
| 6 weeks before test: bump to 1.5h/day | ~October |

---

## Study techniques (kept, minus the app stuff)

**Active recall** — after every session, close everything and write what you remember from memory. Math: attempt blind before checking the solution.

**Mistake logs** — one plain markdown file per exam (YKS / SAT). Every wrong answer goes in with a one-line reason. Reread before the next exam. This replaces flashcards entirely.

**Pomodoro-ish** — 50/10 for projects, 25/5 for study. Stand up during breaks; don't scroll.

**Past papers / denemeler** — highest ROI for both exams. Full, timed, marked immediately. TYT already gives you this weekly — SAT gets it Sundays.

**Evening rule** — after ~10pm, review only. No new material.

---

## The one rule that protects everything

**Never miss twice.**

One missed day is a human being living a life.
Two missed days is a pattern.
Three is a new default.

You don't have to be perfect. You have to be relentless about coming back.

---

## Weekly review template (Sunday, 20 min)

```
Date: ___________

YKS: weekday blocks kept ___ / 5
TYT score this Saturday: ___  (vs last week: ___)
Biggest gap from mistake review:

SAT: hours this week ___ / 6
Weakest area right now:

Projects:
- exact-hour: (what shipped / what's blocked)
- bewsoa-ai-clock:
Commits this week: ___

Gym: ___ / 4–5
Sleep average: ___ h
Energy (1–10): ___

One thing that slowed me down:
Next week's single most important task:
```

---

*Last updated: July 2026*
