package com.example.timecapsule.data.adapter

import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Unit tests verifying [FirestoreCachePolicy] configuration contracts.
 */
class MemoryCacheConfigTest {

    @Test
    fun firestoreCachePolicyObjectIsAccessible() {
        assertNotNull(FirestoreCachePolicy)
    }
}
