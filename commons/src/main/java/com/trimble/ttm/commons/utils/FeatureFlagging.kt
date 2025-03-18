package com.trimble.ttm.commons.utils

import com.trimble.ttm.commons.logger.Log

data class FeatureFlagDocument(
    val featureFlagName: String = EMPTY_STRING,
    val cidList: List<String> = listOf(),
    val shouldEnableCIDFilter: Boolean = false,
    val shouldEnableFeature: Boolean = false,
    val data: String = EMPTY_STRING
)

/**
 * ## FeatureGatekeeper
 * Defines methods to control execution flow whenever feature flags/toggles are present.
 *
 * Check https://martinfowler.com/articles/feature-toggles.html for an introduction on the concept.
 */
interface FeatureGatekeeper {

    /**
     * Return the feature flag state based on the mapped documents retrieved by firestore.
     * @param featureFlagName - The name of the feature to look up
     * @param featureFlagDocumentMap - Mapping of all the featureFlagSaved docs
     * @param cid - Customer Id to check against to turn on feature (if enabledCIDFilter)
     */
    fun isFeatureTurnedOn(
        featureFlagName: KnownFeatureFlags,
        featureFlagDocumentMap: Map<KnownFeatureFlags, FeatureFlagDocument>,
        cid: String = EMPTY_STRING
    ): Boolean

    /**
     * A set of known feature flags keys for the application.
     */
    enum class KnownFeatureFlags(val id: String) {
        /**
         * To decide if address needs to be displayed
         */
        SHOULD_DISPLAY_ADDRESS("should_display_address"),

        /**
         * App Launcher version which has maps performance fix changes.
         */
        LAUNCHER_MAPS_PERFORMANCE_FIX_VERSION("launcher_maps_performance_fix_version"),

        /**
         * To decide if Pre Planned Arrival needs to be displayed
         */
        SHOULD_DISPLAY_PREPLANNED_ARRIVAL("should_display_preplanned_arrival"),

        /**
         * To decide to display the dispatch form using compose or xml
         */
        FORM_COMPOSE_FLAG("form_compose"),

        /**
         * To decide if we should allow draft of dispatch forms
         */
        SAVE_TO_DRAFTS_FLAG("save_to_drafts"),

        /**
         * Removal of one minute delay
         */
        ONE_MINUTE_DELAY_REMOVE("one_minute_delay_remove"),

        /**
         * Find constant from string value
         */
        SHOULD_USE_CONFIGURABLE_ODOMETER("should_use_configurable_odometer");

        companion object {
            /**
             * Find constant from string value
             */
            infix fun from(id: String): KnownFeatureFlags? = entries.firstOrNull { it.id == id }
        }
    }
}

/**
 * ## FeatureFlagGateKeeper
 * Used to check if feature flag from firestore is turned on/off
 */
class FeatureFlagGateKeeper : FeatureGatekeeper {
    override fun isFeatureTurnedOn(
        featureFlagName: FeatureGatekeeper.KnownFeatureFlags,
        featureFlagDocumentMap: Map<FeatureGatekeeper.KnownFeatureFlags, FeatureFlagDocument>,
        cid: String
    ): Boolean {
        val document = featureFlagDocumentMap[featureFlagName]
        if (document != null) {
            //New feature flag document found and used
            if (!document.shouldEnableFeature) {
                return false
            } else {
                if (document.shouldEnableCIDFilter) {
                    return document.cidList.contains(cid)
                }
                return true
            }
        }
        return false
    }
}

fun Map<FeatureGatekeeper.KnownFeatureFlags, FeatureFlagDocument>.isFeatureTurnedOn(key: FeatureGatekeeper.KnownFeatureFlags): Boolean {
    if (this.containsKey(key)) {
        return this[key]?.shouldEnableFeature ?: false
    }
    return false
}

fun Map<FeatureGatekeeper.KnownFeatureFlags, FeatureFlagDocument>.getFeatureFlagDataString(key: FeatureGatekeeper.KnownFeatureFlags): String {
    if (this.containsKey(key)) {
        return this[key]?.data ?: EMPTY_STRING
    }
    return EMPTY_STRING
}

fun Map<FeatureGatekeeper.KnownFeatureFlags, FeatureFlagDocument>.getFeatureFlagDataAsLong(key: FeatureGatekeeper.KnownFeatureFlags): Long {
    val data = this.getFeatureFlagDataString(key)
    if (data != EMPTY_STRING) {
        return try {
            data.toLong()
        } catch (npe: NumberFormatException) {
            Log.e("FeatureFlagging", "Can't parse number for $key", npe)
            -1L
        }
    }
    return -1L
}

/**
 * This fun will return false if the feature flag is not available in the map so that the caller will invoke getting the feature flag document from firestore.
 */
fun Map<FeatureGatekeeper.KnownFeatureFlags, FeatureFlagDocument>.isFeatureTurnedOnIfFeatureFlagAvailable(
    key: FeatureGatekeeper.KnownFeatureFlags
): Pair<Boolean, Boolean> {
    if (this.containsKey(key)) {
        return Pair(this[key]?.shouldEnableFeature ?: false, true)
    }
    return Pair(false, false)
}