package com.example.timecapsule.ui.s01

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.timecapsule.data.AccountSessionManager
import com.example.timecapsule.domain.result.OperationResult
import kotlinx.coroutines.launch

/**
 * ViewModel for S-01 Sign in screen handling authentication requests via [AccountSessionManager].
 */
class S01SignInViewModel(
    private val accountSessionManager: AccountSessionManager
) : ViewModel() {

    private val _signInState = MutableLiveData<OperationResult<String>?>()
    val signInState: LiveData<OperationResult<String>?> = _signInState

    /**
     * Submits sign in credentials and initiates session tracker update on success.
     */
    fun signIn(email: String, password: String) {
        viewModelScope.launch {
            val result = accountSessionManager.signIn(email, password)
            _signInState.postValue(result)
        }
    }

    /** Clears active sign-in result state. */
    fun clearState() {
        _signInState.value = null
    }
}
