package com.example.timecapsule.ui.s02

import com.example.timecapsule.domain.session.SessionSnapshot
import com.example.timecapsule.ui.AccountFormViewModel
import com.example.timecapsule.data.AccountSessionManager
import com.example.timecapsule.domain.result.OperationResult

/**
 * ViewModel for S-02 Register and profile repair screen utilizing [AccountSessionManager].
 */
class S02RegisterViewModel(
    private val accountSessionManager: AccountSessionManager
) : AccountFormViewModel() {

    /**
     * Registers a new account, starts session, and creates public recipient profile.
     */
    fun registerAndCreateProfile(email: String, password: String, displayName: String) {
        submit { accountSessionManager.signUpAndCreateProfile(email, password, displayName) }
    }

    /**
     * Performs profile repair for an existing authenticated session.
     */
    fun repairProfile(displayName: String, origin: SessionSnapshot = accountSessionManager.currentSession()) {
        submit {
            when (val result = accountSessionManager.repairProfile(displayName, origin)) {
                is OperationResult.Success -> if (origin.uid != null && accountSessionManager.isSessionValid(origin)) {
                    OperationResult.Success(origin.uid)
                } else OperationResult.Denied("Account changed.")
                is OperationResult.Invalid -> result
                is OperationResult.Denied -> result
                is OperationResult.Unavailable -> result
                is OperationResult.OutcomeUnknown -> result
            }
        }
    }

    /** Leave a repair or verification screen through the existing account cleanup transaction. */
    fun signOut(origin: SessionSnapshot) = submit {
        when (val outcome = accountSessionManager.signOut(origin)) {
            is OperationResult.Success -> OperationResult.Success("")
            is OperationResult.Invalid -> outcome
            is OperationResult.Denied -> outcome
            is OperationResult.Unavailable -> outcome
            is OperationResult.OutcomeUnknown -> outcome
        }
    }
}
