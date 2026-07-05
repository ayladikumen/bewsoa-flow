package ai.bewsoa.flow

import android.app.Application
import ai.bewsoa.flow.data.CustomProgram
import ai.bewsoa.flow.data.SettingsRepository
import ai.bewsoa.flow.notifications.CoachWorker
import ai.bewsoa.flow.notifications.MotivationWorker
import ai.bewsoa.flow.notifications.NotificationHelper
import ai.bewsoa.flow.notifications.ScheduleSyncWorker
import ai.bewsoa.flow.notifications.TaskAlarmScheduler
import ai.bewsoa.flow.ui.theme.ThemeCache
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
        // The saved AI program and theme must be live before the first frame —
        // two small DataStore reads, done synchronously.
        runBlocking {
            val settings = SettingsRepository.get(this@BewsoaFlowApp)
            settings.programJson.first()?.let { json -> CustomProgram.activate(json) }
            ThemeCache.initialId = settings.appTheme.first()
        }
        NotificationHelper.createChannels(this)
        ScheduleSyncWorker.enqueue(this)
        MotivationWorker.kickoff(this)
        CoachWorker.schedule(this)
        appScope.launch { TaskAlarmScheduler.scheduleUpcoming(this@BewsoaFlowApp) }
    }
}
