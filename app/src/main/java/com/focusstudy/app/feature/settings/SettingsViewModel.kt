package com.focusstudy.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusstudy.app.core.backup.DataBackupManager
import com.focusstudy.app.core.backup.ImportResult
import com.focusstudy.app.core.database.AppDatabase
import com.focusstudy.app.core.datastore.UserPreferences
import com.focusstudy.app.core.repository.UserSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val preferences: UserPreferences? = null,
    val isExporting: Boolean = false,
    val exportedJson: String? = null,
    val exportMessage: String? = null,
    val isImporting: Boolean = false,
    val importResult: ImportResult? = null
)

class SettingsViewModel(
    private val settingsRepository: UserSettingsRepository,
    private val db: AppDatabase
) : ViewModel() {

    private val backupManager = DataBackupManager(db)

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.preferencesFlow.collect { prefs ->
                _uiState.value = _uiState.value.copy(preferences = prefs)
            }
        }
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch {
            settingsRepository.setThemeMode(mode)
        }
    }

    fun setSoundEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setSoundEnabled(enabled)
        }
    }

    fun setHapticEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setHapticEnabled(enabled)
        }
    }

    fun setSessionRemindersEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setSessionRemindersEnabled(enabled)
        }
    }

    fun setDailyPlanReminderEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setDailyPlanReminderEnabled(enabled)
        }
    }

    fun setExamCountdownEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setExamCountdownEnabled(enabled)
        }
    }

    fun setAiConsent(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAiConsent(consentGiven = enabled, featuresEnabled = enabled)
        }
    }

    fun setCustomGeminiApiKey(key: String) {
        viewModelScope.launch {
            settingsRepository.setCustomGeminiApiKey(key)
        }
    }

    fun setPersonalizationPreferences(technique: String, burnoutGuard: Boolean) {
        viewModelScope.launch {
            settingsRepository.setPersonalizationPreferences(technique, burnoutGuard)
        }
    }

    fun exportData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isExporting = true, exportMessage = null)
            try {
                val json = backupManager.exportDataToJson()
                _uiState.value = _uiState.value.copy(
                    isExporting = false,
                    exportedJson = json,
                    exportMessage = "Backup generated successfully (${json.length} characters)"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isExporting = false,
                    exportMessage = "Export failed: ${e.localizedMessage}"
                )
            }
        }
    }

    fun importData(jsonString: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isImporting = true, importResult = null)
            try {
                val result = backupManager.importDataFromJson(jsonString)
                _uiState.value = _uiState.value.copy(
                    isImporting = false,
                    importResult = result
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isImporting = false,
                    importResult = ImportResult(false, "Import failed: ${e.localizedMessage}")
                )
            }
        }
    }

    fun clearFeedback() {
        _uiState.value = _uiState.value.copy(exportMessage = null, importResult = null, exportedJson = null)
    }
}
