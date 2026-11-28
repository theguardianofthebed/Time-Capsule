package com.example.timecapsule.domain.model

/**
 * Indicates whether the capsule lists were completely server-confirmed, partially offline, or stale offline.
 */
enum class ListFreshness {
    SERVER_CONFIRMED,
    PARTIAL_OFFLINE,
    STALE_OFFLINE
}

/**
 * Container holding metadata lists for sent capsules [sent] and received capsules [received].
 */
data class CapsuleListsSnapshot(
    val sent: List<CapsuleSummary>,
    val received: List<CapsuleSummary>,
    val freshness: ListFreshness = ListFreshness.SERVER_CONFIRMED
)
