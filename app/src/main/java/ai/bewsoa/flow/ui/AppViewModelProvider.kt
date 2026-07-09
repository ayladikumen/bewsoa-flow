package ai.bewsoa.flow.ui

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ai.bewsoa.flow.BewsoaFlowApp
import ai.bewsoa.flow.data.FocusRepository
import ai.bewsoa.flow.data.ProgramRepository
import ai.bewsoa.flow.data.SettingsRepository
import ai.bewsoa.flow.data.TaskRepository
import ai.bewsoa.flow.ui.alerts.AlertsViewModel
import ai.bewsoa.flow.ui.focus.FocusViewModel
import ai.bewsoa.flow.ui.progress.ProgressViewModel
import ai.bewsoa.flow.ui.review.ReviewViewModel
import ai.bewsoa.flow.ui.settings.SettingsViewModel
import ai.bewsoa.flow.ui.tasks.TasksViewModel
import ai.bewsoa.flow.ui.today.TodayViewModel

object AppViewModelProvider {
    val Factory = viewModelFactory {
        initializer {
            val app = this[APPLICATION_KEY] as BewsoaFlowApp
            TodayViewModel(app, ProgramRepository.get(app))
        }
        initializer {
            val app = this[APPLICATION_KEY] as BewsoaFlowApp
            TasksViewModel(TaskRepository.get(app), SettingsRepository.get(app))
        }
        initializer {
            val app = this[APPLICATION_KEY] as BewsoaFlowApp
            ProgressViewModel(ProgramRepository.get(app), FocusRepository.get(app))
        }
        initializer {
            val app = this[APPLICATION_KEY] as BewsoaFlowApp
            FocusViewModel(FocusRepository.get(app))
        }
        initializer {
            val app = this[APPLICATION_KEY] as BewsoaFlowApp
            ReviewViewModel(ProgramRepository.get(app))
        }
        initializer {
            val app = this[APPLICATION_KEY] as BewsoaFlowApp
            AlertsViewModel(app)
        }
        initializer {
            val app = this[APPLICATION_KEY] as BewsoaFlowApp
            SettingsViewModel(app)
        }
    }
}
