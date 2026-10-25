package com.example.timecapsule.data

import com.example.timecapsule.domain.model.CapsuleDraft
import com.example.timecapsule.domain.model.CapsuleSummary
import com.example.timecapsule.domain.model.ProtectedCapsuleContent

/** Contract only; do not create a fake success implementation. */
interface CapsuleRepository {
    suspend fun loadSent(uid: String): Result<List<CapsuleSummary>>
    suspend fun loadReceived(uid: String): Result<List<CapsuleSummary>>

    /** Success may be returned ONLY after required uploads and remote capsule ACK. */
    suspend fun send(draft: CapsuleDraft): Result<String>

    /** Must enforce account/time eligibility remotely; check current location in the official client. */
    suspend fun loadEligibleContent(capsuleId: String): Result<ProtectedCapsuleContent>
}
