package com.trimble.ttm.formlibrary.viewmodel

import android.app.Application
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import com.trimble.ttm.backbone.api.data.eld.UserEldStatus
import com.trimble.ttm.backbone.api.data.user.UserName
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.formlibrary.CoroutineTestRuleWithMainUnconfinedDispatcher
import com.trimble.ttm.formlibrary.R
import com.trimble.ttm.formlibrary.model.EDVIRInspection
import com.trimble.ttm.formlibrary.usecases.EDVIRInspectionsUseCase
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EDVIRInspectionViewModelTest {

    private lateinit var inspectionViewModel: EDVIRInspectionsViewModel
    private lateinit var appModuleCommunicator: AppModuleCommunicator
    private lateinit var application: Application
    private lateinit var inspectionsUsecase: EDVIRInspectionsUseCase

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    @RelaxedMockK
    private lateinit var errorDataObserver: Observer<String>

    @RelaxedMockK
    private lateinit var errorDataObserver1: Observer<String>

    @RelaxedMockK
    private lateinit var errorDataObserver2: Observer<String>

    @RelaxedMockK
    private lateinit var canShowInspectionObserver: Observer<Boolean>

    @RelaxedMockK
    private lateinit var inspectionListObserver: Observer<List<EDVIRInspection>>

    @RelaxedMockK
    private lateinit var userName: UserName

    @get:Rule
    var coroutinesTestRule = CoroutineTestRuleWithMainUnconfinedDispatcher()

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        application = mockk()
        appModuleCommunicator = mockk()
        inspectionsUsecase = mockk()
        inspectionViewModel =
            EDVIRInspectionsViewModel(application, inspectionsUsecase, appModuleCommunicator)
        mockkStatic(android.util.Log::class)
        userName = mockk<UserName> {
            every { firstName } returns "firstName"
            every { lastName } returns "lastName"
            every { middleName } returns "middleName"
            every { title } returns "title"
            every { userId } returns "userId"
        }
    }

    @Test
    fun `verify is current user initialised`() {
        assertFalse(inspectionViewModel.isCurrentUserInitialised())
    }

    @Test
    fun `verify retrieved current user is empty`() = runTest {
        currentUserTestHelper(userName = mockk<UserName> {
            every { firstName } returns "firstName"
            every { lastName } returns "lastName"
            every { middleName } returns "middleName"
            every { title } returns "title"
            every { userId } returns ""
        })
    }

    private fun currentUserTestHelper(userName: UserName) = runTest {
        coEvery { appModuleCommunicator.doGetCid() } returns "123"
        coEvery { appModuleCommunicator.doGetObcId() } returns "rwer23"
        every { inspectionsUsecase.getCurrentUser(any()) } returns userName
        every { application.getString(R.string.err_current_driver_not_available) } returns "Current driver not available"
        inspectionViewModel.errorDataForToast.observeForever(errorDataObserver)

        inspectionViewModel.retrieveCurrentUserInfo()

        assertTrue(inspectionViewModel.errorDataForToast.value == "Current driver not available")

        verify(exactly = 1) {
            application.getString(R.string.err_current_driver_not_available)
            errorDataObserver.onChanged("Current driver not available")
        }
        coVerify {
            appModuleCommunicator.doGetCid()
            appModuleCommunicator.doGetObcId()
        }
    }

    @Test
    fun `verify retrieve current user info - throws exception`() = runTest {

        coEvery { appModuleCommunicator.doGetCid() } returns "123"
        coEvery { appModuleCommunicator.doGetObcId() } returns "rwer23"
        every { inspectionsUsecase.getCurrentUser(any()) } throws Exception()

        inspectionViewModel.retrieveCurrentUserInfo()

        coVerify {
            appModuleCommunicator.doGetCid()
            appModuleCommunicator.doGetObcId()
        }

    }

    @Test
    fun `verify retrieve eld status info - throws exception`() = runTest {
        userName = mockk<UserName> {
            every { firstName } returns ""
            every { lastName } returns ""
            every { middleName } returns ""
            every { title } returns ""
            every { userId } returns ""
        }
        coEvery { appModuleCommunicator.doGetCid() } returns "123"
        coEvery { appModuleCommunicator.doGetObcId() } returns "rwer23"
        every { inspectionsUsecase.getCurrentUser(any()) } returns userName
        coEvery { inspectionsUsecase.getUserEldStatus(any()) } throws Exception()

        inspectionViewModel.retrieveCurrentUserInfo()

        coVerify {
            appModuleCommunicator.doGetCid()
            appModuleCommunicator.doGetObcId()
        }

    }

    @Test
    fun `verify retrieved current user  class is empty`() = runTest {
        currentUserTestHelper(userName = mockk<UserName> {
            every { firstName } returns ""
            every { lastName } returns ""
            every { middleName } returns ""
            every { title } returns ""
            every { userId } returns ""
        })
    }

    @Test
    fun `verify retrieved current user is not empty`() = runTest {

        coEvery { appModuleCommunicator.doGetCid() } returns "123"
        coEvery { appModuleCommunicator.doGetObcId() } returns "rwer23"
        every { inspectionsUsecase.getCurrentUser(any()) } returns userName
        coEvery { inspectionsUsecase.getUserEldStatus(any()) } returns emptyMap()
        every { application.getString(R.string.err_duty_status_not_available) } returns "Driver duty status not available"
        inspectionViewModel.errorDataForToast.observeForever(errorDataObserver1)

        inspectionViewModel.retrieveCurrentUserInfo()

        assertTrue(inspectionViewModel.errorDataForToast.value == "Driver duty status not available")

        verify(exactly = 1) {
            application.getString(R.string.err_duty_status_not_available)
            errorDataObserver1.onChanged("Driver duty status not available")
        }
        coVerify {
            appModuleCommunicator.doGetCid()
            appModuleCommunicator.doGetObcId()
        }
    }

    @Test
    fun `verify retrieved current user eld status is not empty`() = runTest {

        val map = mutableMapOf<String, UserEldStatus>()
        map["1233"] = UserEldStatus.DRIVING
        coEvery { appModuleCommunicator.doGetCid() } returns "123"
        coEvery { appModuleCommunicator.doGetObcId() } returns "rwer23"
        every { inspectionsUsecase.getCurrentUser(any()) } returns userName
        coEvery { inspectionsUsecase.getUserEldStatus(any()) } returns map
        every { application.getString(R.string.err_duty_status_not_available) } returns "Driver duty status not available"
        inspectionViewModel.canShowInspectionMenu.observeForever(canShowInspectionObserver)
        coEvery { inspectionsUsecase.canUserPerformManualInspection() } returns true

        inspectionViewModel.retrieveCurrentUserInfo()

        assertTrue(inspectionViewModel.canShowInspectionMenu.value == true)
        verify(exactly = 0) {
            application.getString(R.string.err_duty_status_not_available)
            errorDataObserver1.onChanged("Driver duty status not available")
        }
    }

    @Test
    fun `verify get inspection list returns error if CID is empty`() = runTest {
        helper("", "123")
    }

    @Test
    fun `verify get inspection list returns error if OBC ID is empty`() = runTest {
        helper("123", "")
    }

    private fun helper(cid: String, obcId: String) = runTest {
        coEvery { appModuleCommunicator.doGetCid() } returns cid
        coEvery { appModuleCommunicator.doGetObcId() } returns obcId
        every { application.getString(R.string.err_loading_inspections_list) } returns "Failed to load Inspections history"
        inspectionViewModel.errorData.observeForever(errorDataObserver2)

        inspectionViewModel.getInspectionsHistory()

        assertTrue(inspectionViewModel.errorData.value == "Failed to load Inspections history")

        verify(exactly = 1) {
            application.getString(R.string.err_loading_inspections_list)
            errorDataObserver2.onChanged("Failed to load Inspections history")
        }
        coVerify {
            appModuleCommunicator.doGetCid()
            appModuleCommunicator.doGetObcId()
        }
    }

    @Test
    fun `verify get inspection list returns data`() = runTest {
        val listInspection = mutableListOf<EDVIRInspection>()
        listInspection.add(EDVIRInspection("AAA", 1, 1, "Pre"))
        listInspection.add(EDVIRInspection("BBB", 1, 1, "Pre"))
        listInspection.add(EDVIRInspection("CCC", 1, 1, "Pre"))
        listInspection.add(EDVIRInspection("AAA", 2, 1, "Post"))
        coEvery { appModuleCommunicator.doGetCid() } returns "12233"
        coEvery { appModuleCommunicator.doGetObcId() } returns "3213sd"
        coEvery { inspectionsUsecase.getInspectionHistoryAsFlow() } returns flow {
            emit(
                listInspection
            )
        }
        coEvery {
            inspectionsUsecase.listenToInspectionHistory(
                any(), any(), any()
            )
        } just runs

        inspectionViewModel.inspectionsList.observeForever(inspectionListObserver)

        inspectionViewModel.getInspectionsHistory()

        coVerify {
            inspectionListObserver.onChanged(listInspection.reversed())
            inspectionsUsecase.listenToInspectionHistory(any(), any(), any())
        }
    }


    @After
    fun after() {
        unmockkAll()
    }

}