package com.trimble.ttm.commons.repo

import androidx.annotation.VisibleForTesting
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.trimble.ttm.commons.logger.FeatureLogTags
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.utils.EMPTY_STRING
import com.trimble.ttm.commons.utils.FEATURE_FLAGS
import com.trimble.ttm.commons.utils.FeatureFlagDocument
import com.trimble.ttm.commons.utils.FeatureGatekeeper
import com.trimble.ttm.commons.utils.ext.isNotNull
import com.trimble.ttm.commons.utils.ext.isNull
import kotlinx.coroutines.tasks.await
import java.util.concurrent.ConcurrentHashMap

class FeatureFlagCacheRepoImpl(private val firestoreInstance: FirebaseFirestore = FirebaseFirestore.getInstance()) : FeatureFlagCacheRepo {
    private val tag = "FeatureFlagCacheRepoImpl"
    private val featureFlagListeners: ConcurrentHashMap<FeatureGatekeeper.KnownFeatureFlags, ListenerRegistration> = ConcurrentHashMap()
    private val featureFlagDocumentMap: ConcurrentHashMap<FeatureGatekeeper.KnownFeatureFlags, FeatureFlagDocument> = ConcurrentHashMap()

    override suspend fun listenAndUpdateFeatureFlagCacheMap(
        callback: (Map<FeatureGatekeeper.KnownFeatureFlags, FeatureFlagDocument>) -> Unit
    ): Map<FeatureGatekeeper.KnownFeatureFlags, FeatureFlagDocument> {
        if (!cacheLock) {
            val querySnapshot = firestoreInstance.collection(FEATURE_FLAGS).get().await()
            clearCacheAndListeners(false)
            querySnapshot.forEach { data ->
                data?.let {
                    Log.i(FeatureLogTags.FEATURE_FLAG_TAG.name, "feature flag doc found - $data")
                    val featureFlagName = FeatureGatekeeper.KnownFeatureFlags.from(data.id)
                    if (featureFlagName != null) {
                        featureFlagListeners[featureFlagName] = data.reference.featureSnapshotListener(featureFlagName, callback)
                    }
                }
            }
            cacheLock = false
        }
        return featureFlagDocumentMap
    }

    /**
     * clearCasheAndListeners - This method will clear the feature flag listeners and the flags
     * themselves from the cache. Should be used any time the application has restarted then pull the
     * flags and re-establish the listeners using updateFeatureFlagCacheMap()
     *
     * @param resetLock Boolean - when this function is called, this flag determines if the cache lock should be removed
     * after the cache has cleared. updateFeatureFlagCacheMap() will only run if the lock is open, so individual calls to this method
     * should open the lock (resetLock=true).
     */
    override fun clearCacheAndListeners(resetLock: Boolean) {
        cacheLock = true
        featureFlagListeners.forEach {
            it.value.remove()
        }
        featureFlagListeners.clear()
        featureFlagDocumentMap.clear()
        if (resetLock) {
            cacheLock = false
        }
    }

    private fun updateCache(flagName: FeatureGatekeeper.KnownFeatureFlags, featureDocument: DocumentSnapshot): ConcurrentHashMap<FeatureGatekeeper.KnownFeatureFlags, FeatureFlagDocument> {
        Log.d(tag, "updateCache: $flagName=$featureDocument")
        featureFlagDocumentMap[flagName] = mapFeatureFlagDocument(featureDocument)
        return featureFlagDocumentMap
    }

    private fun DocumentReference.featureSnapshotListener(
        featureFlagName: FeatureGatekeeper.KnownFeatureFlags,
        callback: (ConcurrentHashMap<FeatureGatekeeper.KnownFeatureFlags, FeatureFlagDocument>) -> Unit
    ): ListenerRegistration {
        return addSnapshotListener(MetadataChanges.INCLUDE) { documentSnapshot, fireStoreException ->
            if (fireStoreException.isNull() && documentSnapshot.isNotNull()) {
                callback(updateCache(featureFlagName, documentSnapshot!!))
            } else {
                Log.e(tag, "DocumentReference.featureSnapshotListener", fireStoreException)
            }
            return@addSnapshotListener
        }
    }

    private fun mapFeatureFlagDocument(data: DocumentSnapshot): FeatureFlagDocument {
        val name = data.id
        val cidList = data.get("cidList") as? List<String> ?: listOf()
        val shouldEnableCidFilter = data.get("shouldEnableCIDFilter") as? Boolean ?: false
        val shouldEnableFeature = data.get("shouldEnableFeature") as? Boolean ?: false
        val data = data.get("data") as? String ?: EMPTY_STRING
        return FeatureFlagDocument(name, cidList, shouldEnableCidFilter, shouldEnableFeature, data)
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun getFeatureFlagDocumentMap() = featureFlagDocumentMap

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun getFeatureFlagListeners() = featureFlagListeners

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun getCacheLock() = cacheLock

    companion object {
        /**
         * cacheLock is used whenever a thread is changing the feature flag cache values, either adding or clearing. Only
         * a single coroutine/thread is allowed to modify the cache at a time. Feature flag listeners are allowed to modify
         * the individual feature flag documents they own, so this is only a lock on full invalidation of the cache.
         */
        @Volatile
        private var cacheLock = false
    }
}
