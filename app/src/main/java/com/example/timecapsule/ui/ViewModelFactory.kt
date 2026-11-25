package com.example.timecapsule.ui

import androidx.lifecycle.ViewModel
import android.content.Context
import com.example.timecapsule.data.media.AndroidPhotoInput
import androidx.lifecycle.ViewModelProvider
import com.example.timecapsule.data.AccountSessionManager
import com.example.timecapsule.data.CapsuleRepository
import com.example.timecapsule.data.recovery.ForegroundRecoveryCoordinator
import com.example.timecapsule.ui.draft.CapsuleDraftViewModel
import com.example.timecapsule.ui.s01.S01SignInViewModel
import com.example.timecapsule.ui.s02.S02RegisterViewModel
import com.example.timecapsule.ui.s08.S08AccountSettingsViewModel

/**
 * Factory class for instantiating ViewModels with [AccountSessionManager] dependencies.
 */
class ViewModelFactory(
    private val accountSessionManager: AccountSessionManager,
    private val context: Context? = null,
    private val repository: CapsuleRepository? = null,
    private val recoveryCoordinator: ForegroundRecoveryCoordinator? = null
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    /** Creates supported models and initializes private media persistence for the shared draft. */
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(S01SignInViewModel::class.java) -> {
                S01SignInViewModel(accountSessionManager) as T
            }
            modelClass.isAssignableFrom(S02RegisterViewModel::class.java) -> {
                S02RegisterViewModel(accountSessionManager) as T
            }
            modelClass.isAssignableFrom(S08AccountSettingsViewModel::class.java) -> {
                S08AccountSettingsViewModel(accountSessionManager) as T
            }
            modelClass.isAssignableFrom(CapsuleDraftViewModel::class.java) -> {
                CapsuleDraftViewModel(accountSessionManager, repository, recoveryCoordinator).also { model ->
                    context?.let { model.configurePhotos(AndroidPhotoInput(it)) }
                } as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
