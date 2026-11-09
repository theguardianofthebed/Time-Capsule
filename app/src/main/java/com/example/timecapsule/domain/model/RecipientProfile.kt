package com.example.timecapsule.domain.model

/**
 * Public recipient profile containing the user's public code [uid] and display name [displayName].
 */
data class RecipientProfile(
    val uid: String,
    val displayName: String
)
