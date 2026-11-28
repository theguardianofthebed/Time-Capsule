package com.example.timecapsule.ui.s08

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.timecapsule.data.AccountSessionManager
import com.example.timecapsule.domain.model.RecipientProfile
import com.example.timecapsule.domain.result.OperationResult
import com.example.timecapsule.domain.session.SessionSnapshot
import kotlinx.coroutines.launch

/**
 * ViewModel for S-08 Account & Settings screen handling profile updates, profile repair, and sign out.
 * Enforces originating session validation to prevent obsolete screens from modifying or signing out newer sessions.
 */
class S08AccountSettingsViewModel(
    private val accountSessionManager: AccountSessionManager
) : ViewModel() {

    private val _updateState = MutableLiveData<OperationResult<Unit>?>()
    val updateState: LiveData<OperationResult<Unit>?> = _updateState

    private val _signOutState = MutableLiveData<OperationResult<Unit>?>()
    val signOutState: LiveData<OperationResult<Unit>?> = _signOutState

    private val _profileCheckState = MutableLiveData<OperationResult<RecipientProfile>?>()
    val profileCheckState: LiveData<OperationResult<RecipientProfile>?> = _profileCheckState

    private var _lastSubmittedName: String? = null
    val lastSubmittedName: String? get() = _lastSubmittedName

    private var activeProfileCheckToken = 0
    private var isUpdateInFlight = false

    /**
     * Verifies recipient profile existence for active [uid].
     * Uses a version token and originating [origin] session check to ignore stale responses.
     */
    fun checkProfile(uid: String, origin: SessionSnapshot? = null) {
        val token = ++activeProfileCheckToken
        viewModelScope.launch {
            if (origin != null && !accountSessionManager.isSessionValid(origin)) return@launch
            val result = accountSessionManager.checkProfile(uid)
            if (token == activeProfileCheckToken && (origin == null || accountSessionManager.isSessionValid(origin))) {
                _profileCheckState.postValue(result)
            }
        }
    }

    /**
     * Updates user display name through authenticated Profile Repair for originating [origin] session.
     * Rejects updates if [origin] is no longer active or valid.
     */
    fun repairProfile(displayName: String, origin: SessionSnapshot? = null) {
        val targetSession = origin ?: accountSessionManager.currentSession()
        if (isUpdateInFlight) return
        if (!accountSessionManager.isSessionValid(targetSession)) {
            _updateState.postValue(OperationResult.Denied("Account changed or session expired."))
            return
        }
        isUpdateInFlight = true
        activeProfileCheckToken++
        _lastSubmittedName = displayName
        viewModelScope.launch {
            if (!accountSessionManager.isSessionValid(targetSession)) {
                isUpdateInFlight = false
                _updateState.postValue(OperationResult.Denied("Account changed or session expired."))
                return@launch
            }
            val result = accountSessionManager.repairProfile(displayName, targetSession)
            isUpdateInFlight = false
            if (accountSessionManager.isSessionValid(targetSession)) {
                _updateState.postValue(result)
            } else {
                _updateState.postValue(OperationResult.Denied("Account changed or session expired."))
            }
        }
    }

    /**
     * Signs out current user asynchronously for originating [origin] session.
     * Rejects sign out if [origin] is no longer active or valid.
     */
    fun signOut(origin: SessionSnapshot? = null) {
        val targetSession = origin ?: accountSessionManager.currentSession()
        if (!accountSessionManager.isSessionValid(targetSession)) {
            _signOutState.postValue(OperationResult.Denied("Account changed or session expired."))
            return
        }
        viewModelScope.launch {
            if (!accountSessionManager.isSessionValid(targetSession)) {
                _signOutState.postValue(OperationResult.Denied("Account changed or session expired."))
                return@launch
            }
            val result = accountSessionManager.signOut(targetSession)
            _signOutState.postValue(result)
        }
    }

    /** Clears active update state notification. */
    fun clearState() {
        _updateState.postValue(null)
    }
}
