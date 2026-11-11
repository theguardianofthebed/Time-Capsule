package com.example.timecapsule.ui.s02

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.timecapsule.data.AccountSessionManager
import com.example.timecapsule.domain.result.OperationResult
import kotlinx.coroutines.launch

/**
 * ViewModel for S-02 Register and profile repair screen utilizing [AccountSessionManager].
 */
class S02RegisterViewModel(
    private val accountSessionManager: AccountSessionManager
) : ViewModel() {

    private val _registerState = MutableLiveData<OperationResult<String>?>()
    val registerState: LiveData<OperationResult<String>?> = _registerState

    /**
     * Registers a new account, starts session, and creates public recipient profile.
     */
    fun registerAndCreateProfile(email: String, password: String, displayName: String) {
        viewModelScope.launch {
            val result = accountSessionManager.signUpAndCreateProfile(email, password, displayName)
            _registerState.postValue(result)
        }
    }

    /**
     * Performs profile repair for an existing authenticated session.
     */
    fun repairProfile(displayName: String) {
        viewModelScope.launch {
            val result = accountSessionManager.repairProfile(displayName)
            val mapped = when (result) {
                is OperationResult.Success -> OperationResult.Success("Profile repaired successfully!")
                is OperationResult.Invalid -> OperationResult.Invalid(result.message)
                is OperationResult.Denied -> OperationResult.Denied(result.message)
                is OperationResult.Unavailable -> OperationResult.Unavailable(result.message)
                is OperationResult.OutcomeUnknown -> OperationResult.Unavailable(result.message)
            }
            _registerState.postValue(mapped)
        }
    }

    /** Clears active registration state notification. */
    fun clearState() {
        _registerState.value = null
    }
}
