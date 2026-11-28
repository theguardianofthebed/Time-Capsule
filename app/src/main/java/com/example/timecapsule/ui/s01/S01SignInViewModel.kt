package com.example.timecapsule.ui.s01

import com.example.timecapsule.ui.AccountFormViewModel
import com.example.timecapsule.data.AccountSessionManager

/**
 * ViewModel for S-01 Sign in screen handling authentication requests via [AccountSessionManager].
 */
class S01SignInViewModel(
    private val accountSessionManager: AccountSessionManager
) : AccountFormViewModel() {

    /**
     * Submits sign in credentials and initiates session tracker update on success.
     */
    fun signIn(email: String, password: String) {
        submit { accountSessionManager.signIn(email, password) }
    }
}
