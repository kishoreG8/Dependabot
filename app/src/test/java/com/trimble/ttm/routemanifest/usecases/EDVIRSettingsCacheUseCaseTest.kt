package com.trimble.ttm.routemanifest.usecases

import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.utils.EMPTY_STRING
import com.trimble.ttm.formlibrary.model.EDVIRPayload
import com.trimble.ttm.formlibrary.usecases.MessageConfirmationUseCase
import com.trimble.ttm.routemanifest.repo.FireStoreCacheRepository
import com.trimble.ttm.routemanifest.utils.TEST_DELAY_OR_TIMEOUT
import com.trimble.ttm.routemanifest.utils.Utils
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

class EDVIRSettingsCacheUseCaseTest {
    @MockK
    private lateinit var fireStoreCacheRepository: FireStoreCacheRepository
    @MockK
    private lateinit var messageConfirmationUseCase: MessageConfirmationUseCase
    @MockK
    private lateinit var appModuleCommunicator: AppModuleCommunicator

    private lateinit var edvirSettingsCacheUseCase: EDVIRSettingsCacheUseCase

    private val cid = "1022032"
    private val obcId = "232323"

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        edvirSettingsCacheUseCase = spyk(
            EDVIRSettingsCacheUseCase(fireStoreCacheRepository, messageConfirmationUseCase, appModuleCommunicator)
        )
        mockkObject(Utils)
        mockkObject(Log)
        coEvery { appModuleCommunicator.doGetCid() } returns cid
        coEvery { appModuleCommunicator.doGetObcId() } returns obcId
        coEvery { Utils.setCrashReportIdentifierAfterBackboneDataCache(any()) } just runs
    }

    @Test
    fun `verify listenToEDVIRSettingsLiveUpdates for exception handling from setCrashReportIdentifierAfterBackboneDataCache`() = runTest {
        coEvery { Utils.setCrashReportIdentifierAfterBackboneDataCache(any()) } answers {
            throw Exception("Test")
        }
        every { fireStoreCacheRepository.addSnapshotListenerForEDVIRSetting(any(), any(), any()) } returns flow {}

        edvirSettingsCacheUseCase.listenToEDVIRSettingsLiveUpdates()

        verify(exactly = 1) { Log.e(any(), any(), any()) }
    }

    @Test
    fun `verify listenToEDVIRSettingsLiveUpdates for invalid cid`() = runTest {
        coEvery { appModuleCommunicator.doGetCid() } returns EMPTY_STRING

        edvirSettingsCacheUseCase.listenToEDVIRSettingsLiveUpdates()

        verify(exactly = 1) { Log.e(any(), any(), any()) }
    }

    @Test
    fun `verify listenToEDVIRSettingsLiveUpdates for invalid obcid`() = runTest {
        coEvery { appModuleCommunicator.doGetObcId() } returns EMPTY_STRING

        edvirSettingsCacheUseCase.listenToEDVIRSettingsLiveUpdates()

        verify(exactly = 1) { Log.e(any(), any(), any()) }
    }

    @Test
    fun `verify listenToEdvirMetadata and listenToEdvirMetaData for invalid flow`() = runTest {
        every { fireStoreCacheRepository.addSnapshotListenerForEDVIRSetting(any(), any(), any()) } returns null

        edvirSettingsCacheUseCase.listenToEDVIRSettingsLiveUpdates()

        verify(exactly = 6, timeout = TEST_DELAY_OR_TIMEOUT) { Log.e(any(), any(), any()) }
        verify(exactly = 0) { Log.d(any(), any()) }
    }

    @Test
    fun `verify listenToEdvirMetadata and listenToEdvirMetaData for exception in the catch`() = runTest {
        every { fireStoreCacheRepository.addSnapshotListenerForEDVIRSetting(any(), any(), any()) } returns flow { throw Exception("Test") }

        edvirSettingsCacheUseCase.listenToEDVIRSettingsLiveUpdates()

        verify(exactly = 6, timeout = TEST_DELAY_OR_TIMEOUT) { Log.d(any(), any(), any()) }
        verify(exactly = 0) { Log.e(any(), any()) }
    }

    @Test
    fun `verify listenToEdvirMetadata and listenToEdvirMetaData for invalid dsn`() = runTest {
        every { fireStoreCacheRepository.addSnapshotListenerForEDVIRSetting(any(), any(), any()) } returns flow { emit(EDVIRPayload()) }

        edvirSettingsCacheUseCase.listenToEDVIRSettingsLiveUpdates()

        verify(exactly = 6, timeout = TEST_DELAY_OR_TIMEOUT) { Log.w(any(), any()) }
        verify(exactly = 0) { Log.e(any(), any()) }
    }

    @Test
    fun `verify confirmMessageViewStatus for invalid asn`() = runTest {
        every { fireStoreCacheRepository.addSnapshotListenerForEDVIRSetting(any(), any(), any()) } returns flow { emit(EDVIRPayload(dsn = 867676)) }

        edvirSettingsCacheUseCase.listenToEDVIRSettingsLiveUpdates()
        verify(exactly = 0) { messageConfirmationUseCase.sendEdvirMessageViewedConfirmation(any(), any(), any()) }
    }

    @Test
    fun `verify confirmMessageViewStatus for valid asn and edvir confirmation send`() = runTest {
        every { fireStoreCacheRepository.addSnapshotListenerForEDVIRSetting(any(), any(), any()) } returns flow { emit(EDVIRPayload(dsn = 867676, asn = 334)) }
        every { messageConfirmationUseCase.sendEdvirMessageViewedConfirmation(any(), any(), any()) } just runs

        edvirSettingsCacheUseCase.listenToEDVIRSettingsLiveUpdates()

        verify(exactly = 0, timeout = TEST_DELAY_OR_TIMEOUT) { Log.w(any(), any()) }
        verify(exactly = 0, timeout = TEST_DELAY_OR_TIMEOUT) { Log.e(any(), any()) }
        verify(exactly = 6, timeout = TEST_DELAY_OR_TIMEOUT) { messageConfirmationUseCase.sendEdvirMessageViewedConfirmation(any(), any(), any()) }
    }

    @Test
    fun `verify syncEDVIRInspectionForm for exception`() = runTest {
        val edvirPayload = EDVIRPayload(dsn = 867676, asn = 33434)
        every { fireStoreCacheRepository.addSnapshotListenerForEDVIRSetting(any(), any(), any()) } returns flow { emit(edvirPayload) }
        coEvery { edvirSettingsCacheUseCase.processInspectionFormSync(edvirPayload, any()) } answers {
            throw Exception("Test")
        }
        coEvery {
            messageConfirmationUseCase.sendEdvirMessageViewedConfirmation(any(), any(), any())
        } just runs

        edvirSettingsCacheUseCase.listenToEDVIRSettingsLiveUpdates()

        verify(exactly = 4, timeout = TEST_DELAY_OR_TIMEOUT) { Log.e(any(), any()) }
    }

    @Test
    fun `verify processInspectionFormSync for invalid form data`() = runTest {
        val edvirPayload = EDVIRPayload(dsn = 867676, asn = 33434)
        every { fireStoreCacheRepository.addSnapshotListenerForEDVIRSetting(any(), any(), any()) } returns flow { emit(edvirPayload) }
        coEvery {
            messageConfirmationUseCase.sendEdvirMessageViewedConfirmation(any(), any(), any())
        } just runs

        edvirSettingsCacheUseCase.listenToEDVIRSettingsLiveUpdates()

        verify(exactly = 0, timeout = TEST_DELAY_OR_TIMEOUT) { Log.e(any(), any()) }
    }

    @Test
    fun `verify processInspectionFormSync for invalid form data with pendInt`() = runTest {
        val edvirPayload = EDVIRPayload(dsn = 867676, asn = 0, pendIntValue = 8767)
        every { fireStoreCacheRepository.addSnapshotListenerForEDVIRSetting(any(), any(), any()) } returns flow { emit(edvirPayload) }
        coEvery {
            messageConfirmationUseCase.sendEdvirMessageViewedConfirmation(any(), any(), any())
        } just runs

        edvirSettingsCacheUseCase.listenToEDVIRSettingsLiveUpdates()

        verify(exactly = 4, timeout = TEST_DELAY_OR_TIMEOUT) { Log.e(any(), any()) }
    }

    @Test
    fun `verify processInspectionFormSync for valid form data sync`() = runTest {
        val edvirPayload = EDVIRPayload(dsn = 867676, asn = 0, formClass = 1, intValue = 34343)
        every { fireStoreCacheRepository.addSnapshotListenerForEDVIRSetting(any(), any(), any()) } returns flow { emit(edvirPayload) }
        coEvery {
            messageConfirmationUseCase.sendEdvirMessageViewedConfirmation(any(), any(), any())
        } just runs
        coEvery { fireStoreCacheRepository.syncFormData(any(), any(), any()) } just runs

        edvirSettingsCacheUseCase.listenToEDVIRSettingsLiveUpdates()

        coVerify(exactly = 4, timeout = TEST_DELAY_OR_TIMEOUT) { fireStoreCacheRepository.syncFormData(any(), any(), any()) }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

}