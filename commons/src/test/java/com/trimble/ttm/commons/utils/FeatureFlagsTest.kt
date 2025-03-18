package com.trimble.ttm.commons.utils

import com.trimble.ttm.commons.logger.Log
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.mockkObject
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

class RemoteConfigGatekeeperTest {

    private lateinit var subject: FeatureFlagGateKeeper

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        subject = FeatureFlagGateKeeper()
        mockkObject(Log)
        every {
            Log.e(any(),any(),any())
        } returns Unit
    }

    @Test
    fun `FeatureFlagGateKeeper should be mockable during init block`() {
        // Arrange

        // Act

        // Assert
        Assert.assertNotNull(subject)
    }

    @Test
    fun `isFeatureTurnedOn should be off by default for any feature`() {
        // Arrange
        val flagName = FeatureGatekeeper.KnownFeatureFlags.FORM_COMPOSE_FLAG
        val featureMap: Map<FeatureGatekeeper.KnownFeatureFlags, FeatureFlagDocument> = mapOf()
        val cid = "cid"
        val expected = false

        // Act
        val actual = subject.isFeatureTurnedOn(flagName, featureMap, cid)

        // Assert
        assertEquals(expected, actual)
    }

    @Test
    fun `isFeatureTurnedOn should be off if hard featureEnabled flag is off`() {
        // Arrange
        val flagName = FeatureGatekeeper.KnownFeatureFlags.FORM_COMPOSE_FLAG
        val featureMap: Map<FeatureGatekeeper.KnownFeatureFlags, FeatureFlagDocument> =
            mapOf(flagName to FeatureFlagDocument(flagName.id, shouldEnableFeature = false))
        val cid = "cid"
        val expected = false

        // Act
        val actual = subject.isFeatureTurnedOn(flagName, featureMap, cid)

        // Assert
        assertEquals(expected, actual)
    }

    @Test
    fun `isFeatureTurnedOn should find flag in flag list`() {
        // Arrange
        val flagName = FeatureGatekeeper.KnownFeatureFlags.FORM_COMPOSE_FLAG
        val featureMap: Map<FeatureGatekeeper.KnownFeatureFlags, FeatureFlagDocument> =
            mapOf(flagName to FeatureFlagDocument(flagName.id, shouldEnableFeature = true))
        val cid = "cid"
        val expected = true

        // Act
        val actual = subject.isFeatureTurnedOn(flagName, featureMap,  cid)

        // Assert
        assertEquals(expected, actual)
    }

    @Test
    fun `isFeatureTurnedOn should find cid in cid list and return true when cid filtering is on`() {
        // Arrange
        val flagName = FeatureGatekeeper.KnownFeatureFlags.FORM_COMPOSE_FLAG
        val featureMap: Map<FeatureGatekeeper.KnownFeatureFlags, FeatureFlagDocument> =
            mapOf(flagName to FeatureFlagDocument(flagName.id, shouldEnableFeature = true, shouldEnableCIDFilter = true, cidList = listOf("cid1", "cid2")))
        val cid = "cid2"
        val expected = true

        // Act
        val actual = subject.isFeatureTurnedOn(flagName, featureMap,  cid)

        // Assert
        assertEquals(expected, actual)
    }

    @Test
    fun `isFeatureTurnedOn should be false if cid is not in cid list when cid filtering is on`() {
        // Arrange
        val flagName = FeatureGatekeeper.KnownFeatureFlags.FORM_COMPOSE_FLAG
        val featureMap: Map<FeatureGatekeeper.KnownFeatureFlags, FeatureFlagDocument> =
            mapOf(flagName to FeatureFlagDocument(flagName.id, shouldEnableFeature = true, shouldEnableCIDFilter = true, cidList = listOf("cid1", "cid2")))
        val cid = "cid3"
        val expected = false

        // Act
        val actual = subject.isFeatureTurnedOn(flagName, featureMap,  cid)

        // Assert
        assertEquals(expected, actual)
    }

    @Test
    fun `check isAvailableInFeatureFlag is return true`() {
        Assert.assertTrue(addValuesToFeatureFlagMap(true,
            true).isFeatureTurnedOn(FeatureGatekeeper.KnownFeatureFlags.SAVE_TO_DRAFTS_FLAG))
    }

    @Test
    fun `check isAvailableInFeatureFlag is return false`() {
        Assert.assertFalse(addValuesToFeatureFlagMap(false,
            true).isFeatureTurnedOn(FeatureGatekeeper.KnownFeatureFlags.SAVE_TO_DRAFTS_FLAG))
    }

    @Test
    fun `check isAvailableInFeatureFlag is return false if the given key is not available`() {
        val test = addValuesToFeatureFlagMap(false, true)
        test.get(FeatureGatekeeper.KnownFeatureFlags.SAVE_TO_DRAFTS_FLAG)
        Assert.assertFalse(addValuesToFeatureFlagMap(false,
            true,
            false).isFeatureTurnedOn(FeatureGatekeeper.KnownFeatureFlags.SAVE_TO_DRAFTS_FLAG))
    }


    private fun addValuesToFeatureFlagMap(
        shouldDisplayLatestDispatches: Boolean,
        shouldDisplayAddress: Boolean,
        addDisplayLatestDispatchesKey: Boolean = true,
    ): Map<FeatureGatekeeper.KnownFeatureFlags, FeatureFlagDocument> {
        val featureFlags: HashMap<FeatureGatekeeper.KnownFeatureFlags, FeatureFlagDocument> = hashMapOf()
        if (addDisplayLatestDispatchesKey) {
            featureFlags.put(FeatureGatekeeper.KnownFeatureFlags.SAVE_TO_DRAFTS_FLAG,
                FeatureFlagDocument(
                    FeatureGatekeeper.KnownFeatureFlags.SAVE_TO_DRAFTS_FLAG.id,
                    shouldEnableFeature = shouldDisplayLatestDispatches
                ))
        }
        featureFlags.put(FeatureGatekeeper.KnownFeatureFlags.SHOULD_DISPLAY_ADDRESS,
            FeatureFlagDocument(
                FeatureGatekeeper.KnownFeatureFlags.SHOULD_DISPLAY_ADDRESS.id,
                shouldEnableFeature = shouldDisplayAddress
            ))
        return featureFlags
    }
}