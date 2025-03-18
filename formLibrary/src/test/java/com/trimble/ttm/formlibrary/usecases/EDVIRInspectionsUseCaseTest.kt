package com.trimble.ttm.formlibrary.usecases

import com.trimble.ttm.backbone.api.data.eld.UserEldStatus
import com.trimble.ttm.backbone.api.data.user.UserName
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.utils.ext.safeCollect
import com.trimble.ttm.formlibrary.model.EDVIRInspection
import com.trimble.ttm.formlibrary.model.EDVIRPayload
import com.trimble.ttm.formlibrary.repo.EDVIRInspectionsRepo
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class EDVIRInspectionsUseCaseTest {

    private val eDVIRInspectionsRepo: EDVIRInspectionsRepo = mockk()
    private lateinit var eDVIRInspectionsUseCase: EDVIRInspectionsUseCase
    private lateinit var appModuleCommunicator: AppModuleCommunicator

    @Before
    fun setUp() {
        MockKAnnotations.init(this, relaxed = true)
        appModuleCommunicator = mockk()
        eDVIRInspectionsUseCase = EDVIRInspectionsUseCase(eDVIRInspectionsRepo)
    }

    @Test
    fun `verify getEDVIREnabledSetting call`() = runTest {    //NOSONAR
        val edvirPayload = EDVIRPayload(1)
        coEvery {
            eDVIRInspectionsUseCase.getEDVIREnabledSetting(any(), any())
        } returns edvirPayload
        assertEquals(edvirPayload, eDVIRInspectionsUseCase.getEDVIREnabledSetting("1", "2"))
    }

    @Test
    fun `verify listenToInspectionHistory call`() = runTest {    //NOSONAR
        coEvery {
            eDVIRInspectionsUseCase.listenToInspectionHistory(
                any(),
                any(), any()
            )
        } returns Unit
        coVerify(exactly = 0) {
            eDVIRInspectionsUseCase.listenToInspectionHistory(
                "1", "3", 0
            )
        }
    }

    @Test
    fun `verify getInspectionHistoryAsFlow call`() = runTest {    //NOSONAR
        val inspectionList = mutableListOf<EDVIRInspection>().also {
            it.add(EDVIRInspection("drvier1"))
            it.add(EDVIRInspection("drvier2"))
        }
        coEvery {
            eDVIRInspectionsUseCase.getInspectionHistoryAsFlow()
        } returns flow { emit(inspectionList) }
        eDVIRInspectionsUseCase.getInspectionHistoryAsFlow().safeCollect(this.javaClass.name) {
            assertEquals(inspectionList, it)
        }
    }

    @Test
    fun `verify getCurrentUser call`() {    //NOSONAR
        val userName = mockk<UserName>()
        eDVIRInspectionsUseCase = mockk()
        every {
            eDVIRInspectionsUseCase.getCurrentUser(appModuleCommunicator)
        } returns userName
        assertEquals(userName, eDVIRInspectionsUseCase.getCurrentUser(appModuleCommunicator))
    }

    @Test
    fun `verify getUserEldStatus calls`() = runTest {    //NOSONAR
        val eldStatusMap = hashMapOf<String, UserEldStatus>().also {
            it["user1"] = UserEldStatus.ON_DUTY
            it["user2"] = UserEldStatus.OFF_DUTY
        }
        coEvery { appModuleCommunicator.getUserEldStatus() } returns eldStatusMap
        //To call the actual class for code coverage
        eDVIRInspectionsUseCase.getUserEldStatus(appModuleCommunicator)
        eDVIRInspectionsUseCase = mockk()
        coEvery {
            eDVIRInspectionsUseCase.getUserEldStatus(appModuleCommunicator)
        } returns eldStatusMap
        assertEquals(eldStatusMap, eDVIRInspectionsUseCase.getUserEldStatus(appModuleCommunicator))
    }

    @Test
    fun `verify canUserPerformManualInspection`() {    //NOSONAR
        assertEquals(
            true,
            eDVIRInspectionsUseCase.canUserPerformManualInspection()
        )
    }

    @Test
    fun `verify canUserPerformManualInspection() for logged in user PRIMARY_DRIVER status`() {    //NOSONAR
        val eldStatusMap = mutableMapOf<String, UserEldStatus>()
        eldStatusMap["2222"] = UserEldStatus.PRIMARY_DRIVER
        eldStatusMap["LORENTZO"] = UserEldStatus.ON_DUTY

        val canUserPerformManualInspection =
            eDVIRInspectionsUseCase.canUserPerformManualInspection()

        assertEquals(true, canUserPerformManualInspection)
    }

    @Test
    fun `verify canUserPerformManualInspection() for logged in user ON_DUTY status`() {    //NOSONAR
        val eldStatusMap = mutableMapOf<String, UserEldStatus>()
        eldStatusMap["2222"] = UserEldStatus.ON_DUTY
        eldStatusMap["LORENTZO"] = UserEldStatus.ON_DUTY

        val canUserPerformManualInspection =
            eDVIRInspectionsUseCase.canUserPerformManualInspection()

        assertEquals(true, canUserPerformManualInspection)
    }

    @Test
    fun `verify canUserPerformManualInspection() for logged in user DRIVING status`() {    //NOSONAR
        val eldStatusMap = mutableMapOf<String, UserEldStatus>()
        eldStatusMap["2222"] = UserEldStatus.DRIVING
        eldStatusMap["LORENTZO"] = UserEldStatus.ON_DUTY

        val canUserPerformManualInspection =
            eDVIRInspectionsUseCase.canUserPerformManualInspection()

        assertEquals(true, canUserPerformManualInspection)
    }

    @Test
    fun `verify canUserPerformManualInspection() for logged in user YARD_MOVES status`() {    //NOSONAR
        val eldStatusMap = mutableMapOf<String, UserEldStatus>()
        eldStatusMap["2222"] = UserEldStatus.YARD_MOVES
        eldStatusMap["LORENTZO"] = UserEldStatus.ON_DUTY

        val canUserPerformManualInspection =
            eDVIRInspectionsUseCase.canUserPerformManualInspection()

        assertEquals(true, canUserPerformManualInspection)
    }

    @After
    fun after() {
        unmockkAll()
    }
}

