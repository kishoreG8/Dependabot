package com.trimble.ttm.routemanifest.usecases


import android.content.Context
import com.trimble.ttm.commons.model.DispatchBlob
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.usecase.BackboneUseCase
import com.trimble.ttm.commons.usecase.SendWorkflowEventsToAppUseCase
import com.trimble.ttm.formlibrary.usecases.MessageConfirmationUseCase
import com.trimble.ttm.formlibrary.utils.EMPTY_STRING
import com.trimble.ttm.routemanifest.application.WorkflowApplication
import com.trimble.ttm.commons.preferenceManager.DataStoreManager
import com.trimble.ttm.routemanifest.model.FcmData
import com.trimble.ttm.routemanifest.model.FcmDataState
import com.trimble.ttm.routemanifest.model.StopDetail
import com.trimble.ttm.routemanifest.utils.CoroutineTestRule
import io.mockk.Called
import io.mockk.MockKAnnotations
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.hamcrest.CoreMatchers.instanceOf
import org.hamcrest.MatcherAssert.assertThat
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.loadKoinModules
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.context.unloadKoinModules
import org.koin.dsl.module
import org.koin.test.KoinTest
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.assertTrue


class WorkflowAppNotificationUseCaseTest : KoinTest {

    private lateinit var dataStoreManager: DataStoreManager

    private lateinit var systemUnderTest: WorkflowAppNotificationUseCase

    @RelaxedMockK
    private lateinit var application: WorkflowApplication

    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @RelaxedMockK
    private lateinit var context: Context
    private val testScope = TestScope(UnconfinedTestDispatcher())

    @RelaxedMockK
    private lateinit var formatter: DateTimeFormatter
    @MockK
    private lateinit var appModuleCommunicator: AppModuleCommunicator
    @MockK
    private lateinit var dispatchStopsUseCase: DispatchStopsUseCase
    @MockK
    private lateinit var removeExpiredTripPanelMessageUseCase : RemoveExpiredTripPanelMessageUseCase
    @MockK
    private lateinit var dispatchListUseCase: DispatchListUseCase
    @MockK
    private lateinit var tripCompletionUseCase : TripCompletionUseCase

    @MockK
    private lateinit var backboneUseCase: BackboneUseCase

    private val cacheNewDispatchData: (String, String, String) -> HashMap<String, String> = { _, _, _ ->
        hashMapOf()
    }

    private var koinModuleForTest = module {
        factory { removeExpiredTripPanelMessageUseCase }
    }

    @MockK
    private lateinit var sendWorkflowEventsToAppUseCase: SendWorkflowEventsToAppUseCase

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        startKoin {
            androidContext(application)
            loadKoinModules(koinModuleForTest)
        }
        mockkObject(WorkflowApplication)
        dataStoreManager = spyk(DataStoreManager(context))
        every { context.filesDir } returns temporaryFolder.newFolder()
        every { context.applicationContext } returns context
        val messageConfirmationUseCase: MessageConfirmationUseCase = mockk()
        systemUnderTest = spyk(WorkflowAppNotificationUseCase(
            application, dataStoreManager, messageConfirmationUseCase,
            appModuleCommunicator, sendWorkflowEventsToAppUseCase,
            dispatchStopsUseCase,dispatchListUseCase,backboneUseCase,
            tripCompletionUseCase
        ), recordPrivateCalls = true)

    }

    @Test
    fun `verify size of stop removal notification map when adding new elements`() {
        val notificationMap = systemUnderTest.stopRemovalNotifications
        Assert.assertEquals(0, notificationMap.size)
        val existingStopRemovalData: ConcurrentHashMap<String, NotificationStopRemovalData> =
            ConcurrentHashMap()
        existingStopRemovalData.getOrPut("100") {
            NotificationStopRemovalData(
                mutableSetOf(1), 0, "abc"
            )
        }
        systemUnderTest.updateMapWithNewElements(existingStopRemovalData)
        Assert.assertEquals(1, systemUnderTest.stopRemovalNotifications.size)
    }


    @Test
    fun `verify stop removal notification add count when inserting new dispatch key`() =runTest{
            Assert.assertEquals(
                mapOf("100" to NotificationStopRemovalData(mutableSetOf(1))),
                systemUnderTest.putStopRemovalNotificationsInAMap(
                    systemUnderTest.stopRemovalNotifications,
                    "100",
                    1, "", 0
                )
            )
    }

    @Test
    fun `verify stop removal notification add logic when inserting into existing dispatch key with existing stop`() {
        val existingStopRemovalData: ConcurrentHashMap<String, NotificationStopRemovalData> =
            ConcurrentHashMap()
        existingStopRemovalData.getOrPut("100") {
            NotificationStopRemovalData(
                mutableSetOf(1), 0, "abc"
            )
        }

        runTest {
            Assert.assertEquals(
                mapOf("100" to NotificationStopRemovalData(mutableSetOf(1), 0, "abc")),
                systemUnderTest.putStopRemovalNotificationsInAMap(
                    existingStopRemovalData,
                    "100",
                    1, "", 0
                )
            )
        }
    }

    @Test
    fun `verify stop removal notification add logic when inserting into existing dispatch key with different stop`() {
        val existingStopRemovalData: ConcurrentHashMap<String, NotificationStopRemovalData> =
            ConcurrentHashMap()
        existingStopRemovalData.getOrPut("100") {
            NotificationStopRemovalData(
                mutableSetOf(2)
            )
        }

        runTest {
            Assert.assertEquals(
                mapOf("100" to NotificationStopRemovalData(mutableSetOf(1, 2))),
                systemUnderTest.putStopRemovalNotificationsInAMap(
                    existingStopRemovalData,
                    "100",
                    1, "", 0
                )
            )
        }
    }

    @Test
    fun `verify stop removal notification add logic when inserting new dispatch key with 2 stops`() {
        val existingStopRemovalData: ConcurrentHashMap<String, NotificationStopRemovalData> =
            ConcurrentHashMap()
        existingStopRemovalData.getOrPut(key = "100") {
            NotificationStopRemovalData(
                mutableSetOf(
                    1,
                    2
                )
            )
        }
        existingStopRemovalData.getOrPut(key = "200") {
            NotificationStopRemovalData(
                mutableSetOf(
                    1,
                    2
                )
            )
        }

        runTest {
            Assert.assertEquals(
                mapOf(
                    "100" to NotificationStopRemovalData(mutableSetOf(1, 2)),
                    "200" to NotificationStopRemovalData(mutableSetOf(1, 2)),
                    "300" to NotificationStopRemovalData(mutableSetOf(1))
                ),
                systemUnderTest.putStopRemovalNotificationsInAMap(
                    existingStopRemovalData,
                    "300",
                    1, "", 0
                )
            )
        }
    }


    @Test
    fun `verify sort order of stop removal notifications`() {
        val existingStopRemovalData: ConcurrentHashMap<String, NotificationStopRemovalData> =
            ConcurrentHashMap()
        existingStopRemovalData.getOrPut(key = "100") {
            NotificationStopRemovalData(
                mutableSetOf(
                    1,
                    2
                ), 1623832752
            )
        }
        existingStopRemovalData.getOrPut(key = "200") {
            NotificationStopRemovalData(
                mutableSetOf(
                    1,
                    2
                ), 1623812952
            )
        }
        runTest {
            Assert.assertEquals(
                "200",
                systemUnderTest.sortStopRemovalNotificationsBasedOnTheReceivedTime(
                    existingStopRemovalData
                )[0]
            )
        }
    }


    @Test
    fun `verify current dispatch key available in the notification list key`() {
        coEvery {
            dataStoreManager.getValue(
                DataStoreManager.ACTIVE_DISPATCH_KEY,
                ""
            )
        } returns "100"

        val existingStopRemovalData: ConcurrentHashMap<String, NotificationStopRemovalData> =
            ConcurrentHashMap()
        existingStopRemovalData.getOrPut(key = "100") {
            NotificationStopRemovalData(
                mutableSetOf(
                    1,
                    2
                ), 1623832752
            )
        }

        runTest {
            Assert.assertEquals(
                true,
                systemUnderTest.isCurrentDispatchStopRemovalNotificationAvailableInTheNotificationMap(
                    existingStopRemovalData,
                    dataStoreManager.getValue(DataStoreManager.ACTIVE_DISPATCH_KEY, "")
                )
            )
        }
    }

    @Test
    fun `verify removed stop list is returned for the dispatch key`() {
        val existingStopRemovalData: ConcurrentHashMap<String, NotificationStopRemovalData> =
            ConcurrentHashMap()
        existingStopRemovalData.getOrPut(key = "100") {
            NotificationStopRemovalData(
                mutableSetOf(
                    1,
                    2
                ), 1623832752
            )
        }

        runTest {
            Assert.assertEquals(
                NotificationStopRemovalData(),
                systemUnderTest.getRemovedStopListOfDispatchKey(
                    existingStopRemovalData, "200"
                )
            )
        }

        runTest {
            Assert.assertEquals(
                NotificationStopRemovalData(
                    mutableSetOf(
                        1,
                        2
                    ), 1623832752
                ),
                systemUnderTest.getRemovedStopListOfDispatchKey(
                    existingStopRemovalData, "100"
                )
            )
        }
    }

    @Test
    fun `verify app is in manual inspection screen`() {
        every { WorkflowApplication.isInManualInspectionScreen() } returns true
        Assert.assertEquals(true, systemUnderTest.isInInManualInspectionScreen())
    }

    @Test
    fun `verify app is not in manual inspection screen`() {
        every { WorkflowApplication.isInManualInspectionScreen() } returns false
        Assert.assertEquals(false, systemUnderTest.isInInManualInspectionScreen())
    }

    /*@Test
    fun `verify item has been removed from map once it is dispatched for notification display`() {
        mockkObject(ContextCompat::class)
        every {
            ContextCompat.getColor(context, android.R.color.holo_red_dark)
        } returns 2
        val stopRemovalData: ConcurrentHashMap<String, NotificationStopRemovalData> =
            ConcurrentHashMap()
        stopRemovalData.getOrPut(key = "100") {
            NotificationStopRemovalData(
                mutableSetOf(
                    1,
                    2
                ), 1623832752
            )
        }
        stopRemovalData.getOrPut(key = "200") {
            NotificationStopRemovalData(
                mutableSetOf(
                    1,
                    2
                ), 900111
            )
        }
        runTest {
            systemUnderTest.putStopRemovalNotificationsInAMap(stopRemovalData, "100", 0, "test", 0)
            systemUnderTest.sendNotificationToAppLauncher(stopRemovalData, "100")
        }
        verify { systemUnderTest.stopRemovalNotifications.remove(any()) }
        verify { systemUnderTest.updateMapWithNewElements(any()) }
        Assert.assertEquals(1, systemUnderTest.stopRemovalNotifications.size)
    }*/

    @Test
    fun `verify current dispatch stop removal null`() {
        val stopRemovalData: ConcurrentHashMap<String, NotificationStopRemovalData> =
            ConcurrentHashMap()
        stopRemovalData.getOrPut(key = "200") {
            NotificationStopRemovalData(
                mutableSetOf(
                        1,
                    2
                ), 1623832752, "Current dispatch"
            )
        }
        coEvery {
            dataStoreManager.getValue(
                DataStoreManager.ACTIVE_DISPATCH_KEY,
                ""
            )
        } returns "100"

        runTest {
            Assert.assertEquals(
                "",
                systemUnderTest.checkAndGetIfCurrentDispatchRemovalNotificationAvailable(
                    stopRemovalData,
                    "100"
                ).dispatchName
            )
        }
    }

    @Test
    fun `verify current dispatch stop removal available`() {
        val stopRemovalData: ConcurrentHashMap<String, NotificationStopRemovalData> =
            ConcurrentHashMap()
        stopRemovalData.getOrPut(key = "100") {
            NotificationStopRemovalData(
                mutableSetOf(
                    1,
                    2
                ), 1623832752, "Current dispatch"
            )
        }
        coEvery {
            dataStoreManager.getValue(
                DataStoreManager.ACTIVE_DISPATCH_KEY,
                ""
            )
        } returns "100"

        runTest {
            Assert.assertEquals(
                "Current dispatch",
                systemUnderTest.checkAndGetIfCurrentDispatchRemovalNotificationAvailable(
                    stopRemovalData,
                    "100"
                ).dispatchName
            )
        }
    }

    @Test
    fun `verify timestamp of stop removal time returns null if format is wrong`() {
        val strDate = "2020-08-1213:10:04.897Z"
        every { formatter.parse(strDate) } throws Exception()
        Assert.assertEquals(0, systemUnderTest.convertDateStringToTimeStamp(strDate))
    }

    @Test
    fun `verify timestamp of stop removal time returns timestamp if format has no issue`() {
        val strDate = "2020-08-12T13:10:04.897Z"
        assertThat(
            systemUnderTest.convertDateStringToTimeStamp(strDate),
            instanceOf(Long::class.java)
        )
    }

    @Test
    fun `verify notification not sent if in manual inspection screen`() = runTest {
        every { WorkflowApplication.isInManualInspectionScreen() } returns true
            systemUnderTest.prepareNotificationDataBasedOnPriority(
                ConcurrentHashMap(),
                ""
            )
            coVerify {
                dataStoreManager.getValue(
                    DataStoreManager.ACTIVE_DISPATCH_KEY,
                    ""
                ) wasNot Called
            }
    }

    @Test
    fun `verify notification send sequence if not in manual inspection screen and current dispatch id is empty`() =
        runTest {
            every { WorkflowApplication.isInManualInspectionScreen() } returns false

            coEvery {
                dataStoreManager.getValue(
                    DataStoreManager.ACTIVE_DISPATCH_KEY,
                    ""
                )
            } returns ""
            systemUnderTest.prepareNotificationDataBasedOnPriority(
                ConcurrentHashMap(),  //Passing empty map, so notification send wont be triggered
                ""
            )

            coVerifyOrder {
                systemUnderTest.prepareNotificationDataBasedOnPriority(
                    ConcurrentHashMap(),""
                )
                systemUnderTest.isInInManualInspectionScreen()
                dataStoreManager.getValue(
                    DataStoreManager.ACTIVE_DISPATCH_KEY,
                    ""
                )
                systemUnderTest.getNonActiveDispatchDataAfterSort(ConcurrentHashMap(), "")
                systemUnderTest.updateMapWithNewElements(any())

            }
        }


    @Test
    fun `check message notification flow invoked if fcm data does have the asn number`() = runTest {
        val fcmData = FcmData(
            cid = "10119",
            vid = "22456",
            dispatchId = "100",
            stopId = 123,
            isStopDeleted = false,
            isStopAdded = false,
            asn = 1000
        )
        coEvery {
            dataStoreManager.getValue(
                DataStoreManager.ACTIVE_DISPATCH_KEY, ""
            )
        } returns "100"
        every { appModuleCommunicator.getAppModuleApplicationScope() } returns testScope

        coEvery { systemUnderTest.sendNewMessageAppNotification(any()) } just runs
        coEvery { systemUnderTest.sendInboxMessageDeliveryConfirmation(any()) } just runs

        systemUnderTest.processReceivedFCMMessage(
            fcmData,testScope, UnconfinedTestDispatcher()
        ) {_, _, _ -> }

        coVerify { systemUnderTest.sendNewMessageAppNotification(any()) }
        coVerify { systemUnderTest.sendInboxMessageDeliveryConfirmation(any()) }
    }

    @Test
    fun `verify trip edit for current trip`() = runTest {
        coEvery { appModuleCommunicator.getCurrentWorkFlowId(any()) } returns "100"
        assertTrue(systemUnderTest.isStopManipulatedForActiveTrip("100"))
    }

    @Test
    fun `check stop removal notification flow invoked if fcm data does have deleted field in the pfm`() =
        runTest {
            val fcmData = FcmData(
                cid = "10119",
                vid = "22456",
                dispatchId = "100",
                stopId = 0,
                isStopDeleted = true,
                isStopAdded = false,
                asn = 0
            )
            val stopList = listOf(StopDetail(stopid = 0, deleted = 1, sequenced = 1),StopDetail(stopid = 1, deleted = 0, sequenced = 1))
            coEvery {
                dataStoreManager.getValue(
                    DataStoreManager.ACTIVE_DISPATCH_KEY, ""
                )
            } returns "100"
            coEvery { appModuleCommunicator.getCurrentWorkFlowId(any()) } returns "100"
            every { appModuleCommunicator.getAppModuleApplicationScope() } returns testScope
            coEvery { dispatchStopsUseCase.markActiveDispatchStopAsManipulated() } just runs
            coEvery { dispatchStopsUseCase.getAllActiveStopsAndActions(any()) } returns stopList
            coEvery { removeExpiredTripPanelMessageUseCase.updateStopInformationInTripPanel(any(), any()) } just runs
            coEvery { removeExpiredTripPanelMessageUseCase.removeMessageFromTripPanelQueue(any(),any()) } just runs
            every { sendWorkflowEventsToAppUseCase.sendWorkflowEvent(any(), any()) } just runs
            coEvery { systemUnderTest.sendStopRemovalNotification(any()) } just runs


            systemUnderTest.processReceivedFCMMessage(fcmData,testScope,
                UnconfinedTestDispatcher()
            ) {_, _, _ -> }

            coVerify(exactly = 1) {
                dispatchStopsUseCase.markActiveDispatchStopAsManipulated()
                systemUnderTest["updateTripPanelMessage"](testScope)
                removeExpiredTripPanelMessageUseCase.updateStopInformationInTripPanel(any(), any())
                removeExpiredTripPanelMessageUseCase.removeMessageFromTripPanelQueue(any(),any())
                systemUnderTest.sendStopRemovalNotification(any())
            }

        }

    @Test
    fun `check stop removal notification flow invoked if fcm data does have deleted field in the pfm and there is no active dispatch`() =
        runTest {
            val fcmData = FcmData(
                cid = "10119",
                vid = "22456",
                dispatchId = "101",
                stopId = 0,
                isStopDeleted = true,
                isStopAdded = false,
                asn = 0
            )
            val stopList = listOf(StopDetail(stopid = 0, deleted = 1, sequenced = 1),StopDetail(stopid = 1, deleted = 0, sequenced = 1))
            coEvery {
                dataStoreManager.getValue(
                    DataStoreManager.ACTIVE_DISPATCH_KEY, ""
                )
            } returns EMPTY_STRING
            coEvery { appModuleCommunicator.getCurrentWorkFlowId(any()) } returns EMPTY_STRING
            every { appModuleCommunicator.getAppModuleApplicationScope() } returns testScope
            coEvery {dispatchStopsUseCase.getAllActiveStopsAndActions(any()) } returns stopList
            coEvery { removeExpiredTripPanelMessageUseCase.updateStopInformationInTripPanel(any(), any()) } just runs
            every { sendWorkflowEventsToAppUseCase.sendWorkflowEvent(any(), any()) } just runs
            coEvery { systemUnderTest.sendStopRemovalNotification(any()) } just runs


            systemUnderTest.processReceivedFCMMessage(fcmData,testScope,
                UnconfinedTestDispatcher()
            ) {_, _, _ -> }

            coVerify(exactly = 1) {
                systemUnderTest.sendStopRemovalNotification(any())
            }

            coVerify(exactly = 0) {
                dispatchStopsUseCase.markActiveDispatchStopAsManipulated()
                systemUnderTest["updateTripPanelMessage"](testScope)
                removeExpiredTripPanelMessageUseCase.updateStopInformationInTripPanel(any(), any())
                removeExpiredTripPanelMessageUseCase.removeMessageFromTripPanelQueue(any(),any())
            }
        }

    @Test
    fun `check stop removal notification flow invoked if fcm data does have deleted field in the pfm and stop deleted for non active trip`() =
        runTest {
            val fcmData = FcmData(
                cid = "10119",
                vid = "22456",
                dispatchId = "101",
                stopId = 0,
                isStopDeleted = true,
                isStopAdded = false,
                asn = 0
            )
            val stopList = listOf(StopDetail(stopid = 0, deleted = 1, sequenced = 1),StopDetail(stopid = 1, deleted = 0, sequenced = 1))
            coEvery {
                dataStoreManager.getValue(
                    DataStoreManager.ACTIVE_DISPATCH_KEY, ""
                )
            } returns EMPTY_STRING
            coEvery { appModuleCommunicator.getCurrentWorkFlowId(any()) } returns "100"
            every { appModuleCommunicator.getAppModuleApplicationScope() } returns testScope
            coEvery {dispatchStopsUseCase.getAllActiveStopsAndActions(any()) } returns stopList
            coEvery { removeExpiredTripPanelMessageUseCase.updateStopInformationInTripPanel(any(), any()) } just runs
            every { sendWorkflowEventsToAppUseCase.sendWorkflowEvent(any(), any()) } just runs
            coEvery { systemUnderTest.sendStopRemovalNotification(any()) } just runs


            systemUnderTest.processReceivedFCMMessage(fcmData,testScope,
                UnconfinedTestDispatcher()
            ) {_, _, _ -> }

            coVerify(exactly = 1) {
                systemUnderTest.sendStopRemovalNotification(any())
            }
            coVerify(exactly = 0) {
                dispatchStopsUseCase.markActiveDispatchStopAsManipulated()
                systemUnderTest["updateTripPanelMessage"](testScope)
                removeExpiredTripPanelMessageUseCase.updateStopInformationInTripPanel(any(), any())
                removeExpiredTripPanelMessageUseCase.removeMessageFromTripPanelQueue(any(),any())
            }
        }

    @Test
    fun `notification send flow not invoked if fcm data is null`() =
        runTest {
            val fcmData = null

            systemUnderTest.processReceivedFCMMessage(
                fcmData, testScope,
                UnconfinedTestDispatcher()
            ) {_, _, _ -> }

            coVerify(exactly = 0) { systemUnderTest.sendStopRemovalNotification(any()) }
        }

    @Test
    fun `check stop add notification flow invoked`() =
        runTest {
            val fcmData = FcmData(
                cid = "10119",
                vid = "22456",
                dispatchId = "100",
                stopId = 12,
                isStopDeleted = false,
                isStopAdded = true,
                asn = 0
            )
            val stopList = listOf(StopDetail(stopid = 1, deleted = 0, sequenced = 1),StopDetail(stopid = 2, deleted = 0, sequenced = 1))
            coEvery {
                dataStoreManager.getValue(
                    DataStoreManager.ACTIVE_DISPATCH_KEY, ""
                )
            } returns "100"
            coEvery { appModuleCommunicator.getCurrentWorkFlowId(any()) } returns "100"
            every { appModuleCommunicator.getAppModuleApplicationScope() } returns testScope
            coEvery { dispatchStopsUseCase.markActiveDispatchStopAsManipulated() } just runs
            coEvery { dispatchStopsUseCase.getAllActiveStopsAndActions(any()) } returns stopList
            coEvery { sendWorkflowEventsToAppUseCase.sendWorkflowEvent(any(), any()) } just runs
            coEvery { removeExpiredTripPanelMessageUseCase.updateStopInformationInTripPanel(any(), any()) } just runs


            systemUnderTest.processReceivedFCMMessage(fcmData,testScope,
                UnconfinedTestDispatcher()
            ) {_, _, _ -> }

            coVerify(exactly = 1) {
                dispatchStopsUseCase.markActiveDispatchStopAsManipulated()
                removeExpiredTripPanelMessageUseCase.updateStopInformationInTripPanel(any(), any())
            }
        }

    @Test
    fun `check stop add notification flow invoked when stop added for non active dispatch`() =
        runTest {
            val fcmData = FcmData(
                cid = "10119",
                vid = "22456",
                dispatchId = "101",
                stopId = 12,
                isStopDeleted = false,
                isStopAdded = true,
                asn = 0
            )
            val stopList = listOf(StopDetail(stopid = 1, deleted = 0, sequenced = 1),StopDetail(stopid = 2, deleted = 0, sequenced = 1))
            coEvery {
                dataStoreManager.getValue(
                    DataStoreManager.ACTIVE_DISPATCH_KEY, ""
                )
            } returns "100"
            coEvery { appModuleCommunicator.getCurrentWorkFlowId(any()) } returns "100"
            every { appModuleCommunicator.getAppModuleApplicationScope() } returns testScope
            coEvery { dispatchStopsUseCase.markActiveDispatchStopAsManipulated() } just runs
            coEvery { dispatchStopsUseCase.getAllActiveStopsAndActions(any()) } returns stopList
            coEvery { sendWorkflowEventsToAppUseCase.sendWorkflowEvent(any(), any()) } just runs
            coEvery { removeExpiredTripPanelMessageUseCase.updateStopInformationInTripPanel(any(), any()) } just runs


            systemUnderTest.processReceivedFCMMessage(fcmData,testScope,
                UnconfinedTestDispatcher()
            ) {_, _, _ -> }

            coVerify(exactly = 0) {
                dispatchStopsUseCase.markActiveDispatchStopAsManipulated()
                removeExpiredTripPanelMessageUseCase.updateStopInformationInTripPanel(any(), any())
            }
        }

    @Test
    fun `verify non active dispatch sort`() = runTest {
        val nonActiveDispatchData: ConcurrentHashMap<String, NotificationStopRemovalData> =
            ConcurrentHashMap()
        nonActiveDispatchData["100"] = NotificationStopRemovalData(mutableSetOf(1), 1623832752, "abc")
        coEvery { systemUnderTest.sendStopRemovalNotificationToAppLauncher(any(),any(),any()) } just runs
        systemUnderTest.getNonActiveDispatchDataAfterSort(nonActiveDispatchData, "100")

        coVerify {systemUnderTest.sendStopRemovalNotificationToAppLauncher(eq("100"),eq("abc"),any()) }
    }

    @Test
    fun `verify fcm data state is new trip`() = runTest {
        val fcmData = FcmData(
            cid = "10119",
            vid = "22456",
            dispatchId = "100",
            stopId = -1,
            dispatchCreateTime = "testDispatchCreationTime",
            dispatchReadyTime = "testDispatchReadyTime",
            isStopDeleted = false,
            isStopAdded = false,
            asn = 0
        )
        Assert.assertEquals(FcmDataState.NewTrip,systemUnderTest.buildFCMDataState(fcmData))
    }

    @Test
    fun `verify fcm data state is new message`() = runTest {
        val fcmData = FcmData(
            cid = "10119",
            vid = "22456",
            dispatchId = "100",
            stopId = 123,
            isStopDeleted = false,
            isStopAdded = false,
            asn = 10
        )
        Assert.assertEquals(FcmDataState.NewMessage,systemUnderTest.buildFCMDataState(fcmData))
    }

    @Test
    fun `verify fcm data state is stop added`() = runTest {
        val fcmData = FcmData(
            cid = "10119",
            vid = "22456",
            dispatchId = "100",
            stopId = 123,
            isStopDeleted = false,
            isStopAdded = true,
            asn = 0
        )
        Assert.assertEquals(FcmDataState.IsStopAdded,systemUnderTest.buildFCMDataState(fcmData))
    }

    @Test
    fun `verify fcm data state is stop deleted`() = runTest {
        val fcmData = FcmData(
            cid = "10119",
            vid = "22456",
            dispatchId = "100",
            stopId = 1,
            isStopDeleted = true,
            isStopAdded = false,
            asn = 0
        )
        Assert.assertEquals(FcmDataState.IsStopDeleted,systemUnderTest.buildFCMDataState(fcmData))
    }

    @Test
    fun `verify fcm data state is Ignored`() = runTest {
        val fcmData = FcmData(
            cid = "10119",
            vid = "22456"
        )
        Assert.assertEquals(FcmDataState.Ignore, systemUnderTest.buildFCMDataState(fcmData))
    }

    @Test
    fun `verify log time difference called and invoked expected log if it is new trip fcm`() = runTest {
        val fcmData = FcmData(
            cid = "10119",
            vid = "22456",
            dispatchId = "100",
            stopId = -1,
            dispatchCreateTime = "testDispatchCreationTime",
            dispatchReadyTime = "testDispatchReadyTime",
            isStopDeleted = false,
            isStopAdded = false,
            asn = 0
        )
        coEvery { backboneUseCase.getLoggedInUsersStatus() } returns listOf()
        coEvery { backboneUseCase.getCurrentUser() } returns "swifts5"
        every { appModuleCommunicator.getAppModuleApplicationScope() } returns testScope
        systemUnderTest.logTimeDifferenceIfFCMIsOfNewTrip(fcmData)
        coVerify { backboneUseCase.getLoggedInUsersStatus() }
        coVerify { backboneUseCase.getCurrentUser() }
    }

    @Test
    fun `verify log time difference called and not invoked expected log if it is not new trip fcm`() = runTest {
        val fcmData = FcmData(
            cid = "10119",
            vid = "22456",
            dispatchId = "100",
            stopId = 100,
            isStopDeleted = true,
            isStopAdded = false,
            asn = 0
        )
        coEvery { backboneUseCase.getLoggedInUsersStatus() } returns listOf()
        coEvery { backboneUseCase.getCurrentUser() } returns "swifts5"
        every { appModuleCommunicator.getAppModuleApplicationScope() } returns testScope
        systemUnderTest.logTimeDifferenceIfFCMIsOfNewTrip(fcmData)
        coVerify(exactly = 0) { backboneUseCase.getLoggedInUsersStatus() }
        coVerify(exactly = 0) { backboneUseCase.getCurrentUser() }
    }

    @Test
    fun `test sendInboxMessageDeliveryConfirmation should send delivery confirmation for given FcmData`() = runTest {
        // Arrange
        val fcmData = FcmData(cid = "10119", vid = "vehicle1", asn = 123)
        val messageConfirmationUseCase = mockk<MessageConfirmationUseCase>()
        //val appModuleCommunicator = mockk<AppModuleCommunicator>()
        coEvery { appModuleCommunicator.doGetCid() } returns "10119"
        coEvery { appModuleCommunicator.doGetTruckNumber() } returns "vehicle1"
        coEvery { messageConfirmationUseCase.sendInboxMessageDeliveryConfirmation(any(), any(), any()) } just Runs

        val workflowAppNotificationUseCase = WorkflowAppNotificationUseCase(
            application = mockk(relaxed = true),
            dataStoreManager = mockk(relaxed = true),
            messageConfirmationUseCase = messageConfirmationUseCase,
            appModuleCommunicator = appModuleCommunicator,
            sendWorkflowEventsToAppUseCase = mockk(relaxed = true),
            dispatchStopsUseCase = mockk(relaxed = true),
            backboneUseCase = mockk(relaxed = true),
            dispatchListUseCase = dispatchListUseCase,
            tripCompletionUseCase = tripCompletionUseCase
        )

        // Act
        workflowAppNotificationUseCase.sendInboxMessageDeliveryConfirmation(fcmData)

        // Assert
        coVerify { messageConfirmationUseCase.sendInboxMessageDeliveryConfirmation(any(),"123", any()) }
    }

    @Test
    fun `test sendInboxMessageDeliveryConfirmation should not send delivery confirmation for given FcmData if cid or vid is different`() = runTest {
        // Arrange
        val fcmData = FcmData(cid = "10119", vid = "vehicle1", asn = 123)
        val messageConfirmationUseCase = mockk<MessageConfirmationUseCase>()
        //val appModuleCommunicator = mockk<AppModuleCommunicator>()
        coEvery { appModuleCommunicator.doGetCid() } returns "5688"
        coEvery { appModuleCommunicator.doGetTruckNumber() } returns "vehicle3"
        coEvery { messageConfirmationUseCase.sendInboxMessageDeliveryConfirmation(any(), any(), any()) } just Runs

        val workflowAppNotificationUseCase = WorkflowAppNotificationUseCase(
            application = mockk(relaxed = true),
            dataStoreManager = mockk(relaxed = true),
            messageConfirmationUseCase = messageConfirmationUseCase,
            appModuleCommunicator = appModuleCommunicator,
            sendWorkflowEventsToAppUseCase = mockk(relaxed = true),
            dispatchStopsUseCase = mockk(relaxed = true),
            backboneUseCase = mockk(relaxed = true),
            dispatchListUseCase = dispatchListUseCase,
            tripCompletionUseCase = tripCompletionUseCase
        )

        // Act
        workflowAppNotificationUseCase.sendInboxMessageDeliveryConfirmation(fcmData)

        // Assert
        coVerify(exactly = 0) { messageConfirmationUseCase.sendInboxMessageDeliveryConfirmation(any(), "123", any()) }
    }

    @Test
    fun `processDispatchBlobData should get, send and delete dispatch blob`() = runTest {
        // Arrange
        val dispatchBlobPushMessageMap = mutableMapOf(
            "cid" to "10119",
            "vid" to "vehicle22",
            "DispatchBlobDocRef" to "qwerty12345"
        )
        val dispatchBlob = DispatchBlob(10119, "vehicle22", blobMessage = "blob message 1", createDate = Instant.parse("2021-08-13T08:30:21.504Z"), appId = 101, hostId = 201).also { it.id = "qwerty12345"}

        val getDispatchBlob: suspend (cid: String, vehicleNumber: String, blobId: String) -> DispatchBlob = { _, _, _ ->
            dispatchBlob
        }

        val deleteDispatchBlobDocument: suspend (cid: String, vehicleNumber: String, blobId: String) -> Unit = { _, _, _ ->
        }

        coEvery { sendWorkflowEventsToAppUseCase.sendDispatchBlobEventToThirdPartyApps(any(), any(), any(), any()) } just Runs
        coEvery { dispatchStopsUseCase.deleteDispatchBlobDocument(any(), any(), any()) } just Runs

        // Act
        systemUnderTest.processDispatchBlobData(dispatchBlobPushMessageMap, getDispatchBlob, deleteDispatchBlobDocument)

        // Assert
        coVerify {
            sendWorkflowEventsToAppUseCase.sendDispatchBlobEventToThirdPartyApps(any(), any(), any(), any())
            deleteDispatchBlobDocument("10119", "vehicle22", "qwerty12345")
        }
    }

    @Test
    fun `processDispatchBlobData should handle missing dispatch blob data`() = runTest {
        // Arrange
        val dispatchBlobPushMessageMap = mapOf<String, String>()

        val getDispatchBlob: suspend (cid: String, vehicleNumber: String, blobId: String) -> DispatchBlob = { _, _, _ ->
            throw Exception("Dispatch blob not found")
        }

        val deleteDispatchBlobDocument: suspend (cid: String, vehicleNumber: String, blobId: String) -> Unit = { _, _, _ ->
        }

        // Act
        systemUnderTest.processDispatchBlobData(dispatchBlobPushMessageMap, getDispatchBlob, deleteDispatchBlobDocument)

        // Assert
        coVerify(exactly = 0) { sendWorkflowEventsToAppUseCase.sendDispatchBlobEventToThirdPartyApps(any(), any(), any(), any()) }
    }

    @Test
    fun verifySendAddStopEventsForNewDispatch() {
        val fcmData = FcmData(
            cid = "5688",
            vid = "22456",
            asn = 1000,
            dispatchId = "100",
            stopId = 123
        )
        val stopListFromCachedData = HashMap<String, String>().let {
            it["stop1"] = "Stop 1 Olive Garden Italian Restaurant"
            it["stop2"] = "Stop 2 The Container Store"
            it
        }
        every { sendWorkflowEventsToAppUseCase.sendWorkflowEvent(any(), any()) } returns Unit
        systemUnderTest.sendAddStopEventsForNewDispatch(fcmData, stopListFromCachedData)

        // With the stopListFromCachedData two ADD_STOP_EVENT events should be sent as there are two stops in cached data
        verify(exactly = 2) {
            sendWorkflowEventsToAppUseCase.sendWorkflowEvent(any(), any())
        }

    }

    @Test
    fun verifySendAddStopEventsForNewDispatchIfStopListHashMapIsEmpty() {
        val fcmData = FcmData(
            cid = "5688",
            vid = "22456",
            asn = 1000,
            dispatchId = "100",
            stopId = 123,
            stopName = "Stop 1 Olive Garden Italian Restaurant"
        )
        val stopListFromCachedData = HashMap<String, String>()
        systemUnderTest.sendAddStopEventsForNewDispatch(fcmData, stopListFromCachedData)
        verify(exactly = 0) {
            sendWorkflowEventsToAppUseCase.sendWorkflowEvent(any(), any())
        }


    }

    @Test
    fun `verify fcmData with new trip state and cid and vid is have value`() = runTest {

        val fcmData = FcmData(
            cid = "10119",
            vid = "22456",
            dispatchId = "100",
            stopId = -1,
            dispatchCreateTime = "testDispatchCreationTime",
            dispatchReadyTime = "testDispatchReadyTime",
            isStopDeleted = false,
            isStopAdded = false,
            asn = 0
        )

        coEvery {
            dataStoreManager.getValue(
                DataStoreManager.ACTIVE_DISPATCH_KEY, ""
            )
        } returns "100"



        every { appModuleCommunicator.getAppModuleApplicationScope() } returns testScope

        coEvery { systemUnderTest.sendWorkflowEventToThirdPartyApps(any(),any()) } just runs
        coEvery { systemUnderTest.sendAddStopEventsForNewDispatch(any(),any()) } just runs
        coEvery { dispatchListUseCase.getDispatchesForTheTruckAndScheduleAutoStartTrip(any(),any(),any()) } just runs


        systemUnderTest.processReceivedFCMMessage(
            fcmData,testScope, UnconfinedTestDispatcher()
        ) {_, _, _ -> }

        coVerify { dispatchListUseCase.getDispatchesForTheTruckAndScheduleAutoStartTrip(any(),any(),any()) }
    }

    @Test
    fun `verify workflowevents are sent to third party apps`() = runTest{
        val fcmData_new_trip = FcmData(
            cid = "10119",
            vid = "22456",
            dispatchId = "100",
            stopId = -1,
            dispatchCreateTime = "testDispatchCreationTime",
            dispatchReadyTime = "testDispatchReadyTime",
            isStopDeleted = false,
            isStopAdded = false,
            asn = 0
        )
        val fcmData_add_stop = FcmData(
            cid = "10119",
            vid = "22456",
            dispatchId = "100",
            stopId = 123,
            isStopDeleted = false,
            isStopAdded = true,
            asn = 0
        )

        val fcmData_remove_stop = FcmData(
            cid = "10119",
            vid = "22456",
            dispatchId = "100",
            stopId = 123,
            isStopDeleted = true,
            isStopAdded = false,
            asn = 0
        )

        coEvery {
            dataStoreManager.getValue(
                DataStoreManager.ACTIVE_DISPATCH_KEY, ""
            )
        } returns "100"

        every { appModuleCommunicator.getAppModuleApplicationScope() } returns testScope
        every { sendWorkflowEventsToAppUseCase.sendWorkflowEvent(any(), any()) } returns Unit
        coEvery { dispatchListUseCase.getDispatchesForTheTruckAndScheduleAutoStartTrip(any(),any(),any()) } just runs

        systemUnderTest.sendWorkflowEventToThirdPartyApps(
            fcmData_new_trip, cacheNewDispatchData = cacheNewDispatchData
        )
        systemUnderTest.sendWorkflowEventToThirdPartyApps(
            fcmData_add_stop, cacheNewDispatchData = cacheNewDispatchData
        )
        systemUnderTest.sendWorkflowEventToThirdPartyApps(
            fcmData_remove_stop, cacheNewDispatchData = cacheNewDispatchData
        )

        verify(exactly = 3) { sendWorkflowEventsToAppUseCase.sendWorkflowEvent(any(),any()) }
        verify(exactly = 1) { systemUnderTest.sendAddStopEventsForNewDispatch(any(),any()) }
    }

    @Test
    fun `verify FCMState - Ignore Does not call any of the methods`() = runTest {
        val fcmDataIgnoreState = FcmData(
            cid = "10119",
            vid = "22456",
            dispatchId = "100",
        )

        systemUnderTest.processReceivedFCMMessage(
            fcmDataIgnoreState, testScope, UnconfinedTestDispatcher()
        ) { _, _, _ -> }


        coVerify(exactly = 0) {
            systemUnderTest.sendNewMessageAppNotification(any())
            systemUnderTest.sendStopRemovalNotification(any())
            systemUnderTest["updateTripPanelMessage"](testScope)
            systemUnderTest.sendInboxMessageDeliveryConfirmation(any())
            dispatchStopsUseCase.markActiveDispatchStopAsManipulated()
            dispatchListUseCase.getDispatchesForTheTruckAndScheduleAutoStartTrip(
                any(), any(), any()
            )
            removeExpiredTripPanelMessageUseCase.updateStopInformationInTripPanel(any(), any())
            removeExpiredTripPanelMessageUseCase.updateStopInformationInTripPanel(any(), any())
            removeExpiredTripPanelMessageUseCase.removeMessageFromTripPanelQueue(any(), any())
        }
    }

    @Test
    fun `verify processDispatchDeletion() calls processDeletedDispatchAndSendTripCompletionEventsToPFM() inside tripCompletionUseCase`() = runTest {
        val fcmData = FcmData(
            cid = "TestCustomerId",
            vid = "TestVehicleId",
            dispatchId = "TestDispatchId",
            dispatchDeletedTime = "TestDispatchDeletedTime"
        )

        coEvery { tripCompletionUseCase.processDeletedDispatchAndSendTripCompletionEventsToPFM(fcmData) } just runs

        systemUnderTest.processDispatchDeletion(fcmData)

        coVerify(exactly = 1) { tripCompletionUseCase.processDeletedDispatchAndSendTripCompletionEventsToPFM(fcmData) }
    }

    @After
    fun tearDown() {
        unmockkAll()
        unloadKoinModules(koinModuleForTest)
        stopKoin()
    }
}

