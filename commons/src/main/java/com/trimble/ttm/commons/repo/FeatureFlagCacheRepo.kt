package com.trimble.ttm.commons.repo

import com.trimble.ttm.commons.utils.FeatureFlagDocument
import com.trimble.ttm.commons.utils.FeatureGatekeeper

interface FeatureFlagCacheRepo {
    suspend fun listenAndUpdateFeatureFlagCacheMap(callback: (Map<FeatureGatekeeper.KnownFeatureFlags, FeatureFlagDocument>) -> Unit):
        Map<FeatureGatekeeper.KnownFeatureFlags, FeatureFlagDocument>

    fun clearCacheAndListeners(resetLock: Boolean = true): Unit
}
