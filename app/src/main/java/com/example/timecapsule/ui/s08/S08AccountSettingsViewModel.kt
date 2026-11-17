package com.example.timecapsule.ui.s08

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.timecapsule.data.AccountSessionManager
import com.example.timecapsule.domain.model.RecipientProfile
import com.example.timecapsule.domain.result.OperationResult
import kotlinx.coroutines.launch

/**
 * ViewModel for S-08 Account & Settings screen handling profile updates, profile repair, and sign out.
 * Prevents concurrent update submissions and uses token counters to invalidate delayed stale profile lookups.
 */
class S08AccountSettingsViewModel(
    private val accountSessionManager: AccountSessionManager
) : ViewModel() {

    private val _updateState = MutableLiveData<OperationResult<Unit>?>()
    val updateState: LiveData<OperationResult<Unit>?> = _updateState

    private val _profileCheckState = MutableLiveData<OperationResult<RecipientProfile>?>()
    val profileCheckState: LiveData<OperationResult<RecipientProfile>?> = _profileCheckState

    private var _lastSubmittedName: String? = null
    val lastSubmittedName: String? get() = _lastSubmittedName

    private var activeProfileCheckToken = 0
    private var isUpdateInFlight = false

    /**
     * Verifies recipient profile existence for active [uid].
     * Uses a version token to ignore delayed async responses if a newer update occurs in flight.
     */
    fun checkProfile(uid: String) {
        val token = ++activeProfileCheckToken
        viewModelScope.launch {
            val result = accountSessionManager.checkProfile(uid)
            if (token == activeProfileCheckToken) {
                _profileCheckState.postValue(result)
            }
        }
    }

    /**
     * Updates user display name through authenticated Profile Repair.
     * Prevents duplicate concurrent submissions while an update operation is in flight,
     * and invalidates any in-flight profile checks initiated before this update.
     */
    fun repairProfile(displayName: String) {
        if (isUpdateInFlight) return
        isUpdateInFlight = true
        activeProfileCheckToken++
        _lastSubmittedName = displayName
        viewModelScope.launch {
            val result = accountSessionManager.repairProfile(displayName)
            isUpdateInFlight = false
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
        _updateState.postValue(null)
    }
}
