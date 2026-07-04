package ai.bewsoa.flow.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import ai.bewsoa.flow.MainActivity
import ai.bewsoa.flow.R
import ai.bewsoa.flow.data.ProgramRepository
import ai.bewsoa.flow.data.TaskBlock
import java.time.LocalDate

const val EXTRA_TASK_ID = "extra_task_id"
const val EXTRA_DATE = "extra_date"
const val EXTRA_NOTIFICATION_ID = "extra_notification_id"

object NotificationHelper {

    const val CHANNEL_TASKS = "task_reminders"
    const val CHANNEL_MOTIVATION = "motivation"
    private const val MOTIVATION_ID = 7001

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_TASKS,
                context.getString(R.string.channel_tasks_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = context.getString(R.string.channel_tasks_desc) }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_MOTIVATION,
                context.getString(R.string.channel_motivation_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = context.getString(R.string.channel_motivation_desc) }
        )
    }

    fun notificationId(date: LocalDate, taskId: String): Int =
        ((date.toEpochDay().toInt() * 31) + taskId.hashCode()) and 0x7FFFFFFF

    private fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun openAppIntent(context: Context, requestCode: Int): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode,
            Intent(context, MainActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    suspend fun showTaskEnd(context: Context, block: TaskBlock, date: LocalDate, offsetMinutes: Int) {
        if (!canNotify(context)) return
        val id = notificationId(date, block.id)

        val markIntent = Intent(context, MarkDoneReceiver::class.java)
            .putExtra(EXTRA_TASK_ID, block.id)
            .putExtra(EXTRA_DATE, date.toString())
            .putExtra(EXTRA_NOTIFICATION_ID, id)
        val markPending = PendingIntent.getBroadcast(
            context, id, markIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = "${block.track.emoji} ${block.title} — wrapped up?"
        val text = if (offsetMinutes > 0) {
            "Ended $offsetMinutes min ago. ${Motivator.taskEndLine(block.track)}"
        } else {
            "Time's up. ${Motivator.taskEndLine(block.track)}"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_TASKS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openAppIntent(context, id))
            .addAction(0, "Mark as done", markPending)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()

        NotificationManagerCompat.from(context).notify(id, notification)
        ProgramRepository.get(context).logNotification("task", title, text)
    }

    suspend fun showMotivation(context: Context, title: String, message: String) {
        if (!canNotify(context)) return
        val notification = NotificationCompat.Builder(context, CHANNEL_MOTIVATION)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(openAppIntent(context, MOTIVATION_ID))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(MOTIVATION_ID, notification)
        ProgramRepository.get(context).logNotification("motivation", title, message)
    }
}
