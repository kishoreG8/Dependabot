package com.trimble.ttm.formlibrary.usecases

import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.formlibrary.dataLayer.MandatoryInspectionMetaData
import com.trimble.ttm.formlibrary.repo.InspectionExposeRepo
import com.trimble.ttm.formlibrary.repo.InspectionExposeRepoImpl
import com.trimble.ttm.formlibrary.repo.LocalRepo
import com.trimble.ttm.formlibrary.utils.TEST_DELAY_OR_TIMEOUT
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.spyk
import io.mockk.unmockkAll
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UpdateInspectionInformationUseCaseTest {
    private lateinit var updateInspectionInformationUseCase: UpdateInspectionInformationUseCase

    @RelaxedMockK
    private val localRepo: LocalRepo = mockk()

    @RelaxedMockK
    private lateinit var inspectionExposeRepo: InspectionExposeRepo

    private val appModuleCommunicator: AppModuleCommunicator = mockk()

    @Before
    fun setup() {
        MockKAnnotations.init()
        inspectionExposeRepo = spyk(InspectionExposeRepoImpl(mockk(relaxed = true)))
        updateInspectionInformationUseCase =
            UpdateInspectionInformationUseCase(
                localRepo,
                inspectionExposeRepo,
                appModuleCommunicator
            )
    }

    @Test
    fun `verify updatePreTripInspectionRequire call`() = runTest {    //NOSONAR
        coEvery { localRepo.updatePreTripInspectionRequire(any()) } just runs
        coEvery { inspectionExposeRepo.getLatestData() } returns MandatoryInspectionMetaData(id = 4)
        updateInspectionInformationUseCase.updatePreTripInspectionRequire(false)

        coVerify(exactly = 1) {
            inspectionExposeRepo.getLatestData()
            inspectionExposeRepo.insert(any())
            inspectionExposeRepo.delete()
        }
    }

    @Test
    fun `verify updatePreTripInspectionRequire call when latest data is invalid`() =
        runTest {    //NOSONAR
            coEvery { localRepo.updatePreTripInspectionRequire(any()) } just runs
            coEvery { inspectionExposeRepo.getLatestData() } returns MandatoryInspectionMetaData()
            updateInspectionInformationUseCase.updatePreTripInspectionRequire(false)

            coVerify(exactly = 1) {
                inspectionExposeRepo.getLatestData()
                inspectionExposeRepo.insert(any())
            }
        }

    @Test
    fun `verify updatePostTripInspectionRequire call`() = runTest {    //NOSONAR
        coEvery { localRepo.updatePostTripInspectionRequire(any()) } just runs
        coEvery { inspectionExposeRepo.getLatestData() } returns MandatoryInspectionMetaData(id = 2)
        updateInspectionInformationUseCase.updatePostTripInspectionRequire(false)

        coVerify(exactly = 1) {
            inspectionExposeRepo.getLatestData()
            inspectionExposeRepo.insert(any())
            inspectionExposeRepo.delete()
        }
    }

    @Test
    fun `verify updatePostTripInspectionRequire call when latest data is invalid`() =
        runTest {    //NOSONAR
            coEvery { localRepo.updatePostTripInspectionRequire(any()) } just runs
            coEvery { inspectionExposeRepo.getLatestData() } returns MandatoryInspectionMetaData()
            updateInspectionInformationUseCase.updatePostTripInspectionRequire(false)

            coVerify(exactly = 1) {
                inspectionExposeRepo.getLatestData()
                inspectionExposeRepo.insert(any())
            }
        }

    @Test
    fun `verify updateInspectionRequire call`() = runTest {    //NOSONAR
        coEvery { localRepo.updateInspectionRequire(any()) } just runs
        coEvery { localRepo.updateInspectionRequire(any()) } just runs
        coEvery { inspectionExposeRepo.getLatestData() } returns MandatoryInspectionMetaData(id = 3)
        updateInspectionInformationUseCase.updateInspectionRequire(false)

        coVerify(exactly = 2) {
            inspectionExposeRepo.getLatestData()
            inspectionExposeRepo.insert(any())
            inspectionExposeRepo.delete()
        }
    }

    @Test
    fun `verify updatePreviousPreTripAnnotation call`() = runTest {    //NOSONAR
        coEvery { localRepo.updatePreviousPreTripAnnotation(any()) } just runs
        coEvery { inspectionExposeRepo.getLatestData() } returns MandatoryInspectionMetaData(id = 2)
        coEvery { appModuleCommunicator.getAppModuleApplicationScope() } returns GlobalScope
        updateInspectionInformationUseCase.updatePreviousPreTripAnnotation("")

        coVerify(exactly = 1, timeout = TEST_DELAY_OR_TIMEOUT) {
            inspectionExposeRepo.getLatestData()
            inspectionExposeRepo.insert(any())
            inspectionExposeRepo.delete()
        }
    }

    @Test
    fun `verify updatePreviousPreTripAnnotation call when latest data is not valid`() = runTest {    //NOSONAR
        coEvery { localRepo.updatePreviousPreTripAnnotation(any()) } just runs
        coEvery { inspectionExposeRepo.getLatestData() } returns MandatoryInspectionMetaData()
        coEvery { appModuleCommunicator.getAppModuleApplicationScope() } returns GlobalScope
        updateInspectionInformationUseCase.updatePreviousPreTripAnnotation("")

        coVerify(exactly = 1, timeout = TEST_DELAY_OR_TIMEOUT) {
            inspectionExposeRepo.getLatestData()
            inspectionExposeRepo.insert(any())
        }
    }

    @Test
    fun `verify updatePreviousPostTripAnnotation call`() = runTest {    //NOSONAR
        coEvery { localRepo.updatePreviousPostTripAnnotation(any()) } just runs
        coEvery { inspectionExposeRepo.getLatestData() } returns MandatoryInspectionMetaData(id = 5)
        coEvery { appModuleCommunicator.getAppModuleApplicationScope() } returns GlobalScope
        updateInspectionInformationUseCase.updatePreviousPostTripAnnotation("")

        coVerify(exactly = 1, timeout = TEST_DELAY_OR_TIMEOUT) {
            inspectionExposeRepo.getLatestData()
            inspectionExposeRepo.insert(any())
            inspectionExposeRepo.delete()
        }
    }

    @Test
    fun `verify updatePreviousPostTripAnnotation call when latest data is invalid`() = runTest {    //NOSONAR
        coEvery { localRepo.updatePreviousPostTripAnnotation(any()) } just runs
        coEvery { inspectionExposeRepo.getLatestData() } returns MandatoryInspectionMetaData()
        coEvery { appModuleCommunicator.getAppModuleApplicationScope() } returns GlobalScope
        updateInspectionInformationUseCase.updatePreviousPostTripAnnotation("")

        coVerify(exactly = 1, timeout = TEST_DELAY_OR_TIMEOUT) {
            inspectionExposeRepo.getLatestData()
            inspectionExposeRepo.insert(any())
        }
    }

    @Test
    fun `verify isPreTripInspectionRequired call`() = runTest {    //NOSONAR
        coEvery { localRepo.isPreTripInspectionRequired() } returns true
        assertTrue(updateInspectionInformationUseCase.isPreTripInspectionRequired())

        coVerify(exactly = 1) {
            localRepo.isPreTripInspectionRequired()
        }
    }

    @Test
    fun `verify isPostTripInspectionRequired call`() = runTest {    //NOSONAR
        coEvery { localRepo.isPostTripInspectionRequired() } returns true
        assertTrue(updateInspectionInformationUseCase.isPostTripInspectionRequired())

        coVerify(exactly = 1) {
            localRepo.isPostTripInspectionRequired()
        }
    }

    @Test
    fun `verify getPreviousPreTripAnnotation call`() = runTest {    //NOSONAR
        coEvery {
            localRepo.getPreviousPreTripAnnotation()
        } returns "ABC"
        assertEquals(updateInspectionInformationUseCase.getPreviousPreTripAnnotation(), "ABC")

        coVerify(exactly = 1) {
            localRepo.getPreviousPreTripAnnotation()
        }
    }

    @Test
    fun `verify getPreviousPostTripAnnotation call`() = runTest {    //NOSONAR
        coEvery {
            localRepo.getPreviousPostTripAnnotation()
        } returns "ABC"
        assertEquals(updateInspectionInformationUseCase.getPreviousPostTripAnnotation(), "ABC")

        coVerify(exactly = 1) {
            localRepo.getPreviousPostTripAnnotation()
        }
    }

    @Test
    fun `verify clearPreviousAnnotations call`() = runTest {    //NOSONAR
        coEvery { localRepo.clearPreviousAnnotations() } just runs
        coEvery { inspectionExposeRepo.getLatestData() } returns MandatoryInspectionMetaData(id = 4)
        coEvery { appModuleCommunicator.getAppModuleApplicationScope() } returns GlobalScope
        updateInspectionInformationUseCase.clearPreviousAnnotations()

        coVerify(exactly = 2, timeout = TEST_DELAY_OR_TIMEOUT) {
            inspectionExposeRepo.getLatestData()
            inspectionExposeRepo.insert(any())
            inspectionExposeRepo.delete()
        }
        coVerify(exactly = 1) {
            localRepo.clearPreviousAnnotations()
        }
    }

    @Test
    fun `verify setLastSignedInDriversCount call`() = runTest {    //NOSONAR
        coEvery { localRepo.setLastSignedInDriversCount(any()) } just runs
        updateInspectionInformationUseCase.setLastSignedInDriversCount(5)

        coVerify(exactly = 1) {
            localRepo.setLastSignedInDriversCount(any())
        }
    }

    @Test
    fun `verify getLastSignedInDriversCount call`() = runTest {    //NOSONAR
        coEvery { localRepo.getLastSignedInDriversCount() } returns 2
        assertTrue(updateInspectionInformationUseCase.getLastSignedInDriversCount() == 2)

        coVerify(exactly = 1) {
            localRepo.getLastSignedInDriversCount()
        }
    }

    @After
    fun after() {
        unmockkAll()
    }
}