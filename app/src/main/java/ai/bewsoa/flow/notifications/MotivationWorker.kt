package ai.bewsoa.flow.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import ai.bewsoa.flow.data.ProgramRepository
import ai.bewsoa.flow.data.SettingsRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * Self-rechaining worker: shows one motivational notification tied to what the
 * schedule actually says right now (running block, next block, or the streak),
 * then re-enqueues itself with a random delay so the boosts land at
 * unpredictable times. Quiet outside 09:00–23:00.
 */
class MotivationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val settings = SettingsRepository.get(applicationContext)
        val enabled = settings.motivationEnabled.first()
        val intensity = settings.motivationIntensity.first()
        val now = LocalDateTime.now()

        if (enabled && now.hour in ACTIVE_START until ACTIVE_END) {
            val repo = ProgramRepository.get(applicationContext)
            val today = now.toLocalDate()
            val time = now.toLocalTime()
            val doneIds = repo.getDoneIds(today)
            val blocks = repo.blocksFor(today).filter { it.counted }
            val boost = Motivator.contextual(
                current = blocks.firstOrNull {
                    time >= it.start && time < it.end && it.id !in doneIds
                },
                next = blocks.firstOrNull { it.start > time && it.id !in doneIds },
                remainingCounted = blocks.count { it.id !in doneIds },
                streakInfo = repo.computeStreak(today)
            )
            NotificationHelper.showMotivation(applicationContext, boost.title, boost.body)
        }
        scheduleNext(applicationContext, intensity)
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "motivation_chain"
        private const val ACTIVE_START = 9
        private const val ACTIVE_END = 23

        /** Starts the chain if it isn't already running. */
        fun kickoff(context: Context) {
            val request = OneTimeWorkRequestBuilder<MotivationWorker>()
                .setInitialDelay(45, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }

        private fun scheduleNext(context: Context, intensity: String) {
            val minutes = when (intensity) {
                SettingsRepository.INTENSITY_CHILL -> Random.nextLong(300, 541)
                SettingsRepository.INTENSITY_BEAST -> Random.nextLong(90, 181)
                else -> Random.nextLong(150, 301)
            }
            val request = OneTimeWorkRequestBuilder<MotivationWorker>()
                .setInitialDelay(minutes, TimeUnit.MINUTES)
                .build()
            // APPEND_OR_REPLACE chains the next run after this one finishes.
            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }
    }
}
