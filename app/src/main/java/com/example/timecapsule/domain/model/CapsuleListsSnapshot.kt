package com.example.timecapsule.domain.model

/** Evidence available for one participant query. Retained rows can represent only part of a list. */
enum class ListProvenance { SERVER_CONFIRMED, OFFLINE_RETAINED, UNAVAILABLE }

/** Each list carries the source of its own rows, including when a query confirmed zero matches. */
data class CapsuleListsSnapshot(
    val sent: List<CapsuleSummary>,
    val received: List<CapsuleSummary>,
    val sentProvenance: ListProvenance = ListProvenance.SERVER_CONFIRMED,
    val receivedProvenance: ListProvenance = ListProvenance.SERVER_CONFIRMED
)
