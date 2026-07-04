package ai.bewsoa.flow

import android.app.Application
import ai.bewsoa.flow.data.CustomProgram
import ai.bewsoa.flow.data.SettingsRepository
import ai.bewsoa.flow.notifications.MotivationWorker
import ai.bewsoa.flow.notifications.NotificationHelper
import ai.bewsoa.flow.notifications.ScheduleSyncWorker
import ai.bewsoa.flow.notifications.TaskAlarmScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class BewsoaFlowApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // The saved AI program must be live before any screen or alarm reads
        // the schedule — one small DataStore read, done synchronously.
        runBlocking {
            SettingsRepository.get(this@BewsoaFlowApp).programJson.first()
                ?.let { json -> CustomProgram.activate(json) }
        }
        NotificationHelper.createChannels(this)
        ScheduleSyncWorker.enqueue(this)
        MotivationWorker.kickoff(this)
        appScope.launch { TaskAlarmScheduler.scheduleUpcoming(this@BewsoaFlowApp) }
    }
}
