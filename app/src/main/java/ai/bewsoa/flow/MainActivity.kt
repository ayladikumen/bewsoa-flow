package ai.bewsoa.flow

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import ai.bewsoa.flow.data.ProgramRepository
import ai.bewsoa.flow.data.XpRepository
import ai.bewsoa.flow.ui.AppRoot
import ai.bewsoa.flow.ui.theme.BewsoaFlowTheme
import ai.bewsoa.flow.ui.theme.LocalPalette
import ai.bewsoa.flow.widget.Widgets
import kotlinx.coroutines.launch
import java.time.LocalDate

class MainActivity : ComponentActivity() {

    override fun onResume() {
        super.onResume()
        // The one place a streak freeze is actually spent. Deliberately not in
        // computeStreak: that is read from widgets and workers, and a read that
        // writes from three uncoordinated callers is a data race.
        lifecycleScope.launch {
            val streak = ProgramRepository.get(applicationContext).settleStreak(LocalDate.now())
            // Milestone bonuses ride the same single-writer moment as the freeze.
            XpRepository.get(applicationContext).settleMilestones(streak.current)
            Widgets.refreshAll(applicationContext)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BewsoaFlowTheme {
                // System bar icons must flip with the palette (dark icons on light themes).
                val isLight = LocalPalette.current.isLight
                LaunchedEffect(isLight) {
                    val transparent = android.graphics.Color.TRANSPARENT
                    val style = if (isLight) {
                        SystemBarStyle.light(transparent, transparent)
                    } else {
                        SystemBarStyle.dark(transparent)
                    }
                    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                }
                NotificationPermissionGate()
                AppRoot()
            }
        }
    }
}

/** Asks for POST_NOTIFICATIONS once on first launch (Android 13+). */
@Composable
private fun NotificationPermissionGate() {
    if (Build.VERSION.SDK_INT < 33) return
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
