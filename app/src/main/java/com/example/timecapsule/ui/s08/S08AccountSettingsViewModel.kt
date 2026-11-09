package com.example.timecapsule.ui.s08

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.timecapsule.data.AccountSessionManager
import com.example.timecapsule.domain.result.OperationResult
import kotlinx.coroutines.launch

/**
 * ViewModel for S-08 Account & Settings screen handling profile updates, profile repair, and sign out.
 */
class S08AccountSettingsViewModel(
    private val accountSessionManager: AccountSessionManager
) : ViewModel() {

    private val _updateState = MutableLiveData<OperationResult<Unit>?>()
    val updateState: LiveData<OperationResult<Unit>?> = _updateState

    /**
     * Updates user display name through authenticated Profile Repair.
     */
    fun repairProfile(displayName: String) {
        viewModelScope.launch {
            val result = accountSessionManager.repairProfile(displayName)
            _updateState.postValue(result)
        }
    }

    /**
     * Signs out current user and terminates session.
     */
    fun signOut() {
        accountSessionManager.signOut()
    }

    /** Clears active update state notification. */
    fun clearState() {
        _updateState.value = null
    }
}
