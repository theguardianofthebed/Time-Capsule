package com.example.timecapsule.domain.model

/**
 * Container holding metadata lists for sent capsules [sent] and received capsules [received].
 */
data class CapsuleListsSnapshot(
    val sent: List<CapsuleSummary>,
    val received: List<CapsuleSummary>
)
