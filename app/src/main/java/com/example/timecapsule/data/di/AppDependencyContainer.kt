package com.example.timecapsule.data.di

import com.example.timecapsule.data.CapsuleRepository
import com.example.timecapsule.data.adapter.AuthAdapter
import com.example.timecapsule.data.adapter.FirestoreAdapter
import com.example.timecapsule.data.adapter.LocalRetentionAdapter
import com.example.timecapsule.data.adapter.LocationAdapter
import com.example.timecapsule.data.adapter.StorageAdapter
import com.example.timecapsule.data.session.DefaultSessionTracker

/**
 * Lightweight manual dependency injection container holding application adapter seams and repository references.
 */
class AppDependencyContainer(
    val sessionTracker: DefaultSessionTracker,
    val authAdapter: AuthAdapter,
    val firestoreAdapter: FirestoreAdapter,
    val storageAdapter: StorageAdapter,
    val locationAdapter: LocationAdapter,
    val retentionAdapter: LocalRetentionAdapter,
    val repository: CapsuleRepository
)
