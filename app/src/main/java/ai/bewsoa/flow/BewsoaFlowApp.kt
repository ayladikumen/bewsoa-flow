package ai.bewsoa.flow

import android.app.Application
import ai.bewsoa.flow.notifications.MotivationWorker
import ai.bewsoa.flow.notifications.NotificationHelper
import ai.bewsoa.flow.notifications.ScheduleSyncWorker
import ai.bewsoa.flow.notifications.TaskAlarmScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BewsoaFlowApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannels(this)
        ScheduleSyncWorker.enqueue(this)
        MotivationWorker.kickoff(this)
        appScope.launch { TaskAlarmScheduler.scheduleUpcoming(this@BewsoaFlowApp) }
    }
}
