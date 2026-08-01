package ai.bewsoa.flow.data.exacthour

import android.content.Context
import ai.bewsoa.flow.data.FocusRepository
import ai.bewsoa.flow.data.ProgramRepository
import ai.bewsoa.flow.data.SettingsRepository
import ai.bewsoa.flow.data.TaskBlock
import ai.bewsoa.flow.data.WeeklyProgram
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.atomic.AtomicReference

/**
 * Keeps the LED clock showing whatever the app is counting down.
 *
 * Called from a lot of places — every `Widgets.refreshAll`, every block
 * boundary alarm, every focus transition — so the cheap path matters: when
 * nothing has changed it returns before opening a socket.
 *
 * Three separate things stop it from fighting the user:
 *  - the plan key, so an unchanged countdown is never restarted;
 *  - the manual hold, armed whenever the remote screen sends a command;
 *  - the automation check, because a running preset is an explicit choice.
 */
object ClockMirror {

    /**
     * A completion the user just confirmed, waiting to be shown.
     *
     * Not pushed on the spot: a finished block and the next block's announce
     * land milliseconds apart, and the next countdown's `reset()` would wipe an
     * overlay sent before it. So it is held here and folded into whatever
     * single overlay the sync ends up sending. Dropped rather than queued if a
     * second completion beats it — one celebration, not a backlog.
     */
    private val pendingFlourish = AtomicReference<String?>(null)

    /** Triggers arrive concurrently; the device is one device. */
    private val gate = Mutex()

    /** Fire-and-forget: a clock that's switched off must never break the app. */
    suspend fun syncQuietly(context: Context) {
        runCatching { sync(context) }
    }

    /** The user ticked something off. Show it, then move on to what's next. */
    suspend fun celebrate(context: Context, block: TaskBlock) {
        celebrate(context, block.title)
    }

    suspend fun celebrate(context: Context, label: String) {
        armFlourish(label)
        syncQuietly(context)
    }

    /**
     * Queue the celebration without pushing yet — for callers that are about to
     * change the very state the plan is derived from. Syncing here would
     * describe the world as it was a line ago, and the caller's own sync moments
     * later would immediately overwrite it.
     */
    fun armFlourish(label: String) {
        // "DONE" rather than a tick: the matrix has 46 glyphs and "✓" isn't one
        // of them — the device would draw a blank where the mark should be.
        pendingFlourish.set("${label.trim()} $DONE_MARK")
    }

    suspend fun sync(context: Context): Result<Unit> = gate.withLock {
        val settings = SettingsRepository.get(context)
        if (!settings.clockEnabled.first()) return@withLock Result.success(Unit)

        val repo = ExactHourRepository.get(context)
        // Cheapest possible bail-out: no clock could be reachable over
        // cellular, and checking costs microseconds against a 1.5s timeout.
        if (!repo.onLocalNetwork()) return@withLock Result.success(Unit)
        if (settings.clockHoldUntil.first() > System.currentTimeMillis()) {
            return@withLock Result.success(Unit)
        }

        val plan = resolvePlan(context, settings)
        val flourish = pendingFlourish.get()
        val lastKey = settings.clockLastPlanKey.first()
        val planChanged = plan.key != lastKey
        // The common path by far: nothing happened, nothing to say.
        if (!planChanged && flourish == null) return@withLock Result.success(Unit)

        val client = repo.client() ?: return@withLock Result.failure(ExactHourError.NotConfigured)

        // A preset the user started by hand outranks anything automatic.
        val current = client.status().getOrNull()
        if (current?.automationRunning == true) return@withLock Result.success(Unit)

        val banner = banner(flourish, plan)
        val result = if (!planChanged) {
            // Only a celebration to deliver, and the countdown on screen is
            // still the right one — so say it without touching the timer.
            // (This is what stops ticking a block off early from restarting
            // the countdown you're still inside.)
            client.text(scroll(banner)).map { }
        } else {
            apply(client, plan, banner)
        }

        result
            .onSuccess {
                pendingFlourish.compareAndSet(flourish, null)
                if (planChanged) settings.setClockLastPlanKey(plan.key)
                settings.setClockLastSeenAt(System.currentTimeMillis())
            }
        // On failure the key is deliberately left alone, so the next trigger
        // — and there are many — retries the same push.
        return@withLock result
    }

    private suspend fun apply(
        client: ExactHourClient,
        plan: ClockPlan,
        banner: String?
    ): Result<Unit> = when (plan) {
        is ClockPlan.Countdown -> {
            // reset() first is not optional: /api/set is silently ignored while
            // the timer runs, so a new plan can't land on a live countdown.
            client.reset()
                .mapCatching { client.set(plan.minutes, plan.seconds).getOrThrow() }
                .mapCatching { client.start().getOrThrow() }
                .mapCatching {
                    // The announce goes on *top* of an already-running
                    // countdown. That's what the device's text engine is for,
                    // and it means the title slides across without the timer
                    // losing the seconds the scroll takes.
                    if (banner != null) client.text(scroll(banner)).getOrThrow()
                }
                .map { }
        }

        is ClockPlan.Marquee -> client.text(scroll(banner ?: plan.text)).map { }

        ClockPlan.Clear -> client.reset()
            .mapCatching {
                // A pending celebration still gets said before the display goes
                // quiet; otherwise clear whatever was up there.
                client.text(banner?.let(::scroll) ?: TextOverlay.clear()).getOrThrow()
            }
            .map { }
    }

    /**
     * The one overlay a sync sends, carrying both halves of the moment: what
     * you just finished, and what you're on now.
     *
     * Internal rather than private so the composition can be tested without a
     * device.
     */
    internal fun banner(pendingFlourish: String?, plan: ClockPlan): String? {
        val announce = when (plan) {
            is ClockPlan.Countdown -> plan.label
            is ClockPlan.Marquee -> plan.text
            ClockPlan.Clear -> null
        }
        // Both halves go through the matrix's own alphabet first, so a title
        // carrying an emoji doesn't scroll past as a gap.
        val done = TextOverlay.displayable(pendingFlourish.orEmpty()).takeIf { it.isNotEmpty() }
        val next = TextOverlay.displayable(announce.orEmpty()).takeIf { it.isNotEmpty() }
        // " - " rather than a middle dot, for the same reason.
        val text = listOfNotNull(done, next).joinToString(SEPARATOR)
        return text.takeIf { it.isNotEmpty() }?.take(TextOverlay.MAX_CHARS)
    }

    /** Both drawable on the clock's 7-pixel font — see [TextOverlay.displayable]. */
    private const val DONE_MARK = "DONE"
    private const val SEPARATOR = " - "

    private fun scroll(text: String?): TextOverlay =
        TextOverlay(text = text.orEmpty(), mode = TextOverlay.MODE_SCROLL).sanitized()

    private suspend fun resolvePlan(context: Context, settings: SettingsRepository): ClockPlan {
        val today = LocalDate.now()
        val programs = ProgramRepository.get(context)
        return ClockIntent.resolve(
            focus = FocusRepository.get(context).activeSession.first(),
            blocks = WeeklyProgram.blocksFor(today),
            doneIds = programs.getDoneIds(today),
            skippedIds = programs.getSkippedIds(today),
            now = LocalTime.now(),
            nowMillis = System.currentTimeMillis(),
            mirrorFocus = settings.clockMirrorFocus.first(),
            mirrorBlocks = settings.clockMirrorBlocks.first(),
            maxMinutes = ExactHourRepository.get(context).status.value?.maxMinutes
                ?: ClockLimits.DEFAULT_MAX_MINUTES
        )
    }
}
