package com.example.timecapsule.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.timecapsule.data.AccountSessionManager
import com.example.timecapsule.ui.s01.S01SignInViewModel
import com.example.timecapsule.ui.s02.S02RegisterViewModel
import com.example.timecapsule.ui.s08.S08AccountSettingsViewModel

/**
 * Factory class for instantiating ViewModels with [AccountSessionManager] dependencies.
 */
class ViewModelFactory(
    private val accountSessionManager: AccountSessionManager
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
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
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
