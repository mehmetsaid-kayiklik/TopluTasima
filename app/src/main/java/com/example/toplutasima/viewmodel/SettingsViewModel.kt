package com.example.toplutasima.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.toplutasima.data.PrefsManager
import com.example.toplutasima.data.local.AppDatabase
import com.example.toplutasima.data.local.entity.ProfileEntity
import com.example.toplutasima.model.ThemeMode
import com.example.toplutasima.model.UsageType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

data class SettingsUiState(
    val editingFavId: String? = null,
    val editLabel: String = "",
    val editUsageType: UsageType = UsageType.BOTH,
    val showDeleteConfirm: String? = null, // fav id to delete
    val message: String = "",

    // Profiles
    val profiles: List<ProfileEntity> = emptyList(),
    val showProfileManager: Boolean = false,
    val showProfileEditDialog: Boolean = false,
    val editingProfile: ProfileEntity? = null, // null means creating a new profile
    val profileDeleteConfirmId: String? = null
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

    // ── Profiles ─────────────────────────────────────────────────────────────

    fun loadProfiles() {
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) {
                AppDatabase.getDatabase(getApplication()).profileDao().getAllProfiles()
            }
            _uiState.value = _uiState.value.copy(profiles = list)
        }
    }

    fun showProfileManager(show: Boolean) {
        _uiState.value = _uiState.value.copy(showProfileManager = show)
        if (show) {
            loadProfiles()
        }
    }

    fun startEditingProfile(profile: ProfileEntity?) {
        _uiState.value = _uiState.value.copy(
            editingProfile = profile,
            showProfileEditDialog = true
        )
    }

    fun cancelEditingProfile() {
        _uiState.value = _uiState.value.copy(
            editingProfile = null,
            showProfileEditDialog = false
        )
    }

    fun saveProfile(
        displayName: String,
        nameKind: String,
        birthHint: String,
        memoryNote: String,
        infoSource: String
    ) {
        val current = _uiState.value.editingProfile
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val dao = AppDatabase.getDatabase(getApplication()).profileDao()
                val profile = if (current != null) {
                    current.copy(
                        displayName = displayName,
                        nameKind = nameKind,
                        birthHint = birthHint.takeIf { it.isNotBlank() },
                        memoryNote = memoryNote.takeIf { it.isNotBlank() },
                        infoSource = infoSource,
                        updatedAt = System.currentTimeMillis()
                    )
                } else {
                    ProfileEntity(
                        id = UUID.randomUUID().toString(),
                        displayName = displayName,
                        nameKind = nameKind,
                        birthHint = birthHint.takeIf { it.isNotBlank() },
                        memoryNote = memoryNote.takeIf { it.isNotBlank() },
                        infoSource = infoSource,
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis(),
                        archived = false
                    )
                }
                dao.upsert(profile)
            }
            _uiState.value = _uiState.value.copy(
                editingProfile = null,
                showProfileEditDialog = false
            )
            loadProfiles()
        }
    }

    fun showDeleteProfileConfirm(profileId: String?) {
        _uiState.value = _uiState.value.copy(profileDeleteConfirmId = profileId)
    }

    fun confirmDeleteProfile() {
        val id = _uiState.value.profileDeleteConfirmId ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                AppDatabase.getDatabase(getApplication()).profileDao().deleteProfile(id)
            }
            _uiState.value = _uiState.value.copy(profileDeleteConfirmId = null)
            loadProfiles()
        }
    }

    fun toggleArchiveProfile(profile: ProfileEntity) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                AppDatabase.getDatabase(getApplication()).profileDao().upsert(
                    profile.copy(
                        archived = !profile.archived,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
            loadProfiles()
        }
    }
}
