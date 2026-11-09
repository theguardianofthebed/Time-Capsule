package com.example.timecapsule.domain.model

/**
 * Public recipient profile containing the user's public code [uid] and display name [displayName].
 */
data class RecipientProfile(
    val uid: String,
    val displayName: String
)

/**
 * Explicit recipient confirmation state encapsulating [profile] and confirmation status [isConfirmed].
 */
data class RecipientConfirmation(
    val profile: RecipientProfile,
    val isConfirmed: Boolean = false
)
