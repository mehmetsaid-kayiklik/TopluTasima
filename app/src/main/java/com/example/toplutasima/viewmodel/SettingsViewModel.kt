package com.example.toplutasima.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.toplutasima.data.PrefsManager
import com.example.toplutasima.model.ThemeMode
import com.example.toplutasima.model.UsageType
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

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

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

}
