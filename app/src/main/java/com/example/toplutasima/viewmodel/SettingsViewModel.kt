package com.example.toplutasima.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.toplutasima.data.PrefsManager
import com.example.toplutasima.model.ThemeMode
import com.example.toplutasima.model.UsageType
import com.example.toplutasima.ui.util.CrashLogEntry
import com.example.toplutasima.ui.util.CrashLogger
import com.example.toplutasima.usecase.RmvMesafeBackfillUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val editingFavId: String? = null,
    val editLabel: String = "",
    val editUsageType: UsageType = UsageType.BOTH,
    val showDeleteConfirm: String? = null, // fav id to delete
    val message: String = ""
)

class SettingsViewModel(
    application: Application,
    private val backfillUseCase: RmvMesafeBackfillUseCase
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val appContext = application.applicationContext
    private val _crashLogs = MutableStateFlow<List<CrashLogEntry>>(emptyList())
    val crashLogs: StateFlow<List<CrashLogEntry>> = _crashLogs.asStateFlow()

    var isBackfillRunning by mutableStateOf(false)
        private set
    var backfillProgress by mutableStateOf("")
        private set
    var backfillResultMessage by mutableStateOf("")
        private set

    init {
        refreshCrashLogs()
    }

    fun refreshCrashLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            _crashLogs.value = CrashLogger.readLogs(appContext)
        }
    }

    fun clearCrashLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            CrashLogger.clearLogs(appContext)
            _crashLogs.value = emptyList()
        }
    }

    // ── Theme ────────────────────────────────────────────────────────────────

    fun setThemeMode(mode: ThemeMode) {
        PrefsManager.changeThemeMode(mode)
    }

    // ── Favorite editing ─────────────────────────────────────────────────────

    fun startEditing(favId: String) {
        val fav = PrefsManager.favorites.find { it.id == favId } ?: return
        _uiState.value = _uiState.value.copy(
            editingFavId = favId,
            editLabel = fav.label,
            editUsageType = fav.usageType
        )
    }

    fun updateEditLabel(label: String) {
        _uiState.value = _uiState.value.copy(editLabel = label)
    }

    fun updateEditUsageType(type: UsageType) {
        _uiState.value = _uiState.value.copy(editUsageType = type)
    }

    fun saveEdit() {
        val id = _uiState.value.editingFavId ?: return
        PrefsManager.updateFavorite(id, _uiState.value.editLabel, _uiState.value.editUsageType)
        _uiState.value = _uiState.value.copy(editingFavId = null)
    }

    fun cancelEdit() {
        _uiState.value = _uiState.value.copy(editingFavId = null)
    }

    fun showDeleteConfirm(favId: String) {
        _uiState.value = _uiState.value.copy(showDeleteConfirm = favId)
    }

    fun confirmDelete() {
        val id = _uiState.value.showDeleteConfirm ?: return
        PrefsManager.removeFavorite(id)
        _uiState.value = _uiState.value.copy(showDeleteConfirm = null)
    }

    fun cancelDelete() {
        _uiState.value = _uiState.value.copy(showDeleteConfirm = null)
    }

    fun runMesafeBackfill() {
        if (isBackfillRunning) return
        isBackfillRunning = true
        backfillProgress = ""
        backfillResultMessage = ""

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = backfillUseCase.run { current, total ->
                    backfillProgress = "$current/$total"
                }
                backfillResultMessage = "${result.updated} güncellendi, ${result.failed} hata"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                backfillResultMessage = "Backfill hata: ${e.message ?: "bilinmeyen hata"}"
            } finally {
                isBackfillRunning = false
            }
        }
    }

}
