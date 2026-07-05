package ai.bewsoa.flow.notifications

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import ai.bewsoa.flow.data.AiProgramUpdater
import ai.bewsoa.flow.data.Insights
import ai.bewsoa.flow.data.ProgramRepository
import ai.bewsoa.flow.data.SettingsRepository
import ai.bewsoa.flow.data.WeeklyProgram
import ai.bewsoa.flow.data.db.WeeklyReviewEntity
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.TimeUnit

/**
 * The Sunday-evening coach: reads the month's adherence history and the weekly
 * review, asks the AI to draft next week's program, and parks the proposal for
 * the user to accept on the Today screen. Never applies anything by itself.
 */
class CoachWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext
        val settings = SettingsRepository.get(app)

        val provider = settings.aiProvider.first()
        val apiKey = if (provider == SettingsRepository.PROVIDER_GEMINI) {
            settings.geminiApiKey.first()
        } else {
            settings.apiKey.first()
        }
        // No key, or a draft already waiting — nothing for the coach to do.
        if (apiKey.isBlank()) return Result.success()
        if (settings.pendingProposalJson.first() != null) return Result.success()

        val repo = ProgramRepository.get(app)
        val today = LocalDate.now()
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val rows = repo.getRange(monday.minusWeeks(4), today)
        val insights = Insights.compute(today, rows, WeeklyProgram::blocksFor)
        val review = repo.getReview(monday)
        // Without any evidence the coach would just guess — skip this week.
        if (rows.isEmpty()) return Result.success()

        val markdown = settings.programMd.first() ?: loadBundledMd(app)
        val currentJson = settings.programJson.first()

        return AiProgramUpdater.proposeNextWeek(
            provider = provider,
            apiKey = apiKey.trim(),
            markdown = markdown,
            currentJson = currentJson,
            insights = insights.map { it.text },
            reviewNotes = review?.toCoachNotes()
        ).fold(
            onSuccess = { proposal ->
                settings.setPendingProposal(proposal.programJson, proposal.coachNote)
                NotificationHelper.showCoach(
                    app,
                    "🧠 Your coach drafted next week",
                    proposal.coachNote.ifBlank {
                        "Open Bewsoa Flow to review and accept the new program."
                    }
                )
                Result.success()
            },
            onFailure = {
                if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.success()
            }
        )
    }

    private fun loadBundledMd(context: Context): String = runCatching {
        context.assets.open("weekly_program.md").bufferedReader().use { it.readText() }
    }.getOrDefault("")

    private fun WeeklyReviewEntity.toCoachNotes(): String = buildString {
        if (slowedMeDown.isNotBlank()) append("Slowed me down: $slowedMeDown. ")
        if (nextWeekTask.isNotBlank()) append("Priority next week: $nextWeekTask. ")
        if (biggestGap.isNotBlank()) append("Biggest gap: $biggestGap. ")
        append("Energy: $energy/10.")
    }

    companion object {
        private const val WORK_NAME = "weekly_coach"
        private const val MAX_RETRIES = 3
        private const val COACH_HOUR = 19

        /** Sunday evening, every week, needs network. */
        fun schedule(context: Context) {
            val now = LocalDateTime.now()
            var next = now.toLocalDate()
                .with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
                .atTime(COACH_HOUR, 0)
            if (!next.isAfter(now)) next = next.plusWeeks(1)

            val request = PeriodicWorkRequestBuilder<CoachWorker>(7, TimeUnit.DAYS)
                .setInitialDelay(Duration.between(now, next))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request
            )
        }
    }
}
