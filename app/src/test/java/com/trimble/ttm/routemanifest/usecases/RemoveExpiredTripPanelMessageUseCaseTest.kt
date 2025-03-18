package com.trimble.ttm.routemanifest.usecases

import android.content.Context
import com.google.android.gms.common.GoogleApiAvailability
import com.google.gson.Gson
import com.trimble.ttm.backbone.api.Backbone
import com.trimble.ttm.backbone.api.BackboneFactory
import com.trimble.ttm.commons.model.DispatchFormPath
import com.trimble.ttm.commons.usecase.BackboneUseCase
import com.trimble.ttm.commons.utils.DefaultDispatcherProvider
import com.trimble.ttm.commons.preferenceManager.FormDataStoreManager
import com.trimble.ttm.formlibrary.utils.EMPTY_STRING
import com.trimble.ttm.formlibrary.utils.FormUtils
import com.trimble.ttm.routemanifest.application.WorkflowApplication
import com.trimble.ttm.routemanifest.customComparator.LauncherMessagePriorityComparator
import com.trimble.ttm.routemanifest.customComparator.LauncherMessageWithPriority
import com.trimble.ttm.commons.preferenceManager.DataStoreManager
import com.trimble.ttm.routemanifest.managers.ResourceStringsManager
import com.trimble.ttm.routemanifest.model.LastSentTripPanelMessage
import com.trimble.ttm.routemanifest.model.StopDetail
import com.trimble.ttm.commons.repo.LocalDataSourceRepo
import com.trimble.ttm.commons.repo.LocalDataSourceRepoImpl
import com.trimble.ttm.routemanifest.repo.TripPanelEventRepo
import com.trimble.ttm.routemanifest.utils.ApplicationContextProvider
import com.trimble.ttm.routemanifest.utils.CoroutineTestRule
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.hamcrest.CoreMatchers.hasItem
import org.hamcrest.CoreMatchers.hasItems
import org.hamcrest.CoreMatchers.not
import org.hamcrest.MatcherAssert.assertThat
import org.junit.After
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
import java.util.concurrent.PriorityBlockingQueue
import kotlin.test.assertEquals

class RemoveExpiredTripPanelMessageUseCaseTest : KoinTest {

    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    private lateinit var removeExpiredTripPanelMessageUseCase: RemoveExpiredTripPanelMessageUseCase

    @RelaxedMockK
    private lateinit var application: WorkflowApplication

    @RelaxedMockK
    private lateinit var googleApiAvailability: GoogleApiAvailability

    @RelaxedMockK
    private lateinit var backbone: Backbone

    @RelaxedMockK
    private lateinit var context: Context

    @RelaxedMockK
    private lateinit var dataStoreManager: DataStoreManager

    @RelaxedMockK
    private lateinit var formDataStoreManager: FormDataStoreManager

    @RelaxedMockK
    private lateinit var tripPanelRepo: TripPanelEventRepo

    private lateinit var tripPanelUseCase: TripPanelUseCase

    @RelaxedMockK
    private lateinit var sendBroadCastUseCase: SendBroadCastUseCase

    @RelaxedMockK
    private lateinit var resourceStringsManager: ResourceStringsManager

    @RelaxedMockK
    private lateinit var backboneUseCase: BackboneUseCase

    @RelaxedMockK
    private lateinit var localDataSourceRepo: LocalDataSourceRepo

    private val testScope = TestScope(UnconfinedTestDispatcher())

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private var modulesRequiredForTest = module {
        single { tripPanelRepo }
        single { backboneUseCase }
        single { dataStoreManager }
        factory { resourceStringsManager }
        factory { sendBroadCastUseCase }
    }

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        context = mockk()

        every { context.packageName } returns "com.trimble.ttm.formsandworkflow"
        startKoin {
            androidContext(application)
            loadKoinModules(modulesRequiredForTest)
        }
        mockkObject(WorkflowApplication)
        mockkObject(ApplicationContextProvider)
        mockkStatic(GoogleApiAvailability::class)
        localDataSourceRepo = LocalDataSourceRepoImpl(dataStoreManager, formDataStoreManager, mockk())
        every { ApplicationContextProvider.getApplicationContext() } returns application.applicationContext
        every { GoogleApiAvailability.getInstance() } returns googleApiAvailability
        backbone = mockk(relaxed = true)
        mockkStatic("com.trimble.ttm.backbone.api.BackboneFactory")
        every { BackboneFactory.backbone(any()) } returns backbone
        every { context.filesDir } returns temporaryFolder.newFolder()
        dataStoreManager = spyk(DataStoreManager(context))
        formDataStoreManager = spyk(FormDataStoreManager(context))
        mockkObject(FormUtils)
        mockkObject(com.trimble.ttm.commons.utils.FormUtils)
        tripPanelUseCase = spyk(TripPanelUseCase(
            tripPanelRepo,
            backboneUseCase,
            resourceStringsManager,
            sendBroadCastUseCase, localDataSourceRepo, mockk(), mockk(),context = context,
            arriveTriggerDataStoreKeyManipulationUseCase = mockk(),
            fetchDispatchStopsAndActionsUseCase = mockk()
        ))
        coEvery {
            dataStoreManager.setValue(
                DataStoreManager.UNCOMPLETED_DISPATCH_FORMS_STACK_KEY,
                any()
            )
        } just runs

        removeExpiredTripPanelMessageUseCase = RemoveExpiredTripPanelMessageUseCase(
            TestScope(),
            tripPanelUseCase, mockk(),
            DefaultDispatcherProvider()
        )
        coEvery {
            backboneUseCase.getCurrentLocation()
        } returns Pair(12.5, 30.0)
    }

    @Test
    fun `test message is removed from priority queue if the stop is removed`() {    //NOSONAR
        val stopList = mutableListOf(
            StopDetail(stopid = 1), StopDetail(stopid = 3),
            StopDetail(stopid = 4), StopDetail(stopid = 2)
        )

        val listOfLauncherMessage = PriorityBlockingQueue(
            1,
            LauncherMessagePriorityComparator
        )


        listOfLauncherMessage.add(LauncherMessageWithPriority("", 0, -1, Pair(0.0, 0.0), Pair(0.0, 0.0) ,1, 2, 5))

        listOfLauncherMessage.add(LauncherMessageWithPriority("", 0, -1, Pair(0.0, 0.0), Pair(0.0, 0.0),6))

        listOfLauncherMessage.add(LauncherMessageWithPriority("", 0, -1, Pair(0.0, 0.0), Pair(0.0, 0.0),1, 2))

        tripPanelUseCase.listLauncherMessageWithPriority = listOfLauncherMessage


        val newMessages =
            removeExpiredTripPanelMessageUseCase.removeLauncherMessageOfRemovedStopsFromPriorityQueue(
                stopList
            )

        assertThat(newMessages.first.element().stopId.toMutableList(), not(hasItem(5)))

        assertThat(newMessages.first.element().stopId.toMutableList(), not(hasItem(6)))

        assertThat(newMessages.first.element().stopId.toMutableList(), hasItems(1, 2))
    }


    @Test
    fun `dismiss last sent trip panel message if that message stop removed`() =
        runTest {    //NOSONAR
            val stopList = mutableListOf(
                StopDetail(stopid = 1), StopDetail(stopid = 3),
                StopDetail(stopid = 4), StopDetail(stopid = 2)
            )

            val listOfLauncherMessage = PriorityBlockingQueue(
                1,
                LauncherMessagePriorityComparator
            )

            coEvery { localDataSourceRepo.getLastSentTripPanelMessageId() } returns -1
            coEvery {
                dataStoreManager.setValue(
                    DataStoreManager.LAST_SENT_TRIP_PANEL_MESSAGE_ID,
                    any()
                )
            } just runs

            listOfLauncherMessage.add(
                LauncherMessageWithPriority(
                    "",
                    0,
                    -1,
                    Pair(0.0, 0.0),
                    Pair(0.0, 0.0),
                    1,
                    2,
                    5
                )
            )

            listOfLauncherMessage.add(LauncherMessageWithPriority("", 0, -1, Pair(0.0, 0.0), Pair(0.0, 0.0),6))

            listOfLauncherMessage.add(LauncherMessageWithPriority("", 0, -1, Pair(0.0, 0.0), Pair(0.0, 0.0),1, 2))

            tripPanelUseCase.listLauncherMessageWithPriority = listOfLauncherMessage

            every { tripPanelRepo.dismissEvent(any()) } just runs

            val lastSentTripPanelMessage = LastSentTripPanelMessage(1, "", 5)

            tripPanelUseCase.setLastSentTripPanelMessageForUnitTest( lastSentTripPanelMessage)

            val (_, distinctStopIdList) = removeExpiredTripPanelMessageUseCase.removeLauncherMessageOfRemovedStopsFromPriorityQueue(
                stopList
            )
            coEvery {
                dataStoreManager.getValue(
                    DataStoreManager.UNCOMPLETED_DISPATCH_FORMS_STACK_KEY,
                    EMPTY_STRING
                )
            } returns "1,2"

            removeExpiredTripPanelMessageUseCase.removeSentMessageIfTheStopRemoved(
                distinctStopIdList
            )

            verify(exactly = 2) { tripPanelRepo.dismissEvent(any()) }

        }

    @Test
    fun `verify dismiss trip panel not invoked if the stop is not removed`() =
        runTest {    //NOSONAR
            val stopList = mutableListOf(
                StopDetail(stopid = 1), StopDetail(stopid = 3),
                StopDetail(stopid = 4), StopDetail(stopid = 2)
            )

            val listOfLauncherMessage = PriorityBlockingQueue(
                1,
                LauncherMessagePriorityComparator
            )

            listOfLauncherMessage.add(
                LauncherMessageWithPriority(
                    "",
                    0,
                    -1,
                    Pair(0.0, 0.0),
                    Pair(0.0, 0.0),
                    1,
                    2,
                    5
                )
            )

            listOfLauncherMessage.add(LauncherMessageWithPriority("", 0, -1, Pair(0.0, 0.0), Pair(0.0, 0.0), 6))

            listOfLauncherMessage.add(LauncherMessageWithPriority("", 0, -1, Pair(0.0, 0.0), Pair(0.0, 0.0),1, 2))

            tripPanelUseCase.listLauncherMessageWithPriority = listOfLauncherMessage

            every { tripPanelRepo.dismissEvent(any()) } just runs

            val lastSentTripPanelMessage = LastSentTripPanelMessage(1, "", 2, 1)

            tripPanelUseCase.setLastSentTripPanelMessageForUnitTest(lastSentTripPanelMessage)

            val (_, distinctStopIdList) = removeExpiredTripPanelMessageUseCase.removeLauncherMessageOfRemovedStopsFromPriorityQueue(
                stopList
            )

            removeExpiredTripPanelMessageUseCase.removeSentMessageIfTheStopRemoved(
                distinctStopIdList
            )

            verify(exactly = 0) { tripPanelRepo.dismissEvent(any()) }
        }

    @Test
    fun `verify form stack is updated as per removed stops`() = runTest {    //NOSONAR

        val stopList = mutableListOf(
            StopDetail(stopid = 1), StopDetail(stopid = 3),
            StopDetail(stopid = 4), StopDetail(stopid = 2)
        )

        val listOfLauncherMessage = PriorityBlockingQueue(
            1,
            LauncherMessagePriorityComparator
        )

        listOfLauncherMessage.add(LauncherMessageWithPriority("", 0, -1, Pair(0.0, 0.0), Pair(0.0, 0.0),1, 2, 5))

        listOfLauncherMessage.add(LauncherMessageWithPriority("", 0, -1, Pair(0.0, 0.0), Pair(0.0, 0.0),6))

        listOfLauncherMessage.add(LauncherMessageWithPriority("", 0, -1, Pair(0.0, 0.0), Pair(0.0, 0.0),1, 2))

        tripPanelUseCase.listLauncherMessageWithPriority = listOfLauncherMessage

        val (_, distinctStopIdList) = removeExpiredTripPanelMessageUseCase.removeLauncherMessageOfRemovedStopsFromPriorityQueue(
            stopList
        )

        val formsIds = arrayListOf<DispatchFormPath>()
        formsIds.add(DispatchFormPath(stopId = 1, actionId = 1))
        formsIds.add(DispatchFormPath(stopId = 2, actionId = 1))
        formsIds.add(DispatchFormPath(stopId = 3, actionId = 1))
        formsIds.add(DispatchFormPath(stopId = 4, actionId = 1))
        formsIds.add(DispatchFormPath(stopId = 5, actionId = 1))

        val form = Gson().toJson(formsIds)

        coEvery {
            dataStoreManager.getValue(
                DataStoreManager.UNCOMPLETED_DISPATCH_FORMS_STACK_KEY,
                EMPTY_STRING
            )
        } returns form

        val newFormStack = removeExpiredTripPanelMessageUseCase.updateFormStack(
            dataStoreManager,
            distinctStopIdList.toMutableList()
        )

        assertThat(newFormStack.toMutableList(), hasItems(1, 2, 3, 4))

        assertThat(newFormStack.toMutableList(), not(hasItem(5)))

    }

    @Test
    fun `verify form stack when no stops are removed`() = runTest {    //NOSONAR

        val stopList = mutableListOf(
            StopDetail(stopid = 1), StopDetail(stopid = 3),
            StopDetail(stopid = 4), StopDetail(stopid = 2), StopDetail(stopid = 5)
        )

        val listOfLauncherMessage = PriorityBlockingQueue(
            1,
            LauncherMessagePriorityComparator
        )

        listOfLauncherMessage.add(LauncherMessageWithPriority("", 0, -1, Pair(0.0, 0.0), Pair(0.0, 0.0),1, 2, 5))

        listOfLauncherMessage.add(LauncherMessageWithPriority("", 0, -1, Pair(0.0, 0.0), Pair(0.0, 0.0), 6))

        listOfLauncherMessage.add(LauncherMessageWithPriority("", 0, -1, Pair(0.0, 0.0), Pair(0.0, 0.0),1, 2))

        tripPanelUseCase.listLauncherMessageWithPriority = listOfLauncherMessage

        val (_, distinctStopIdList) = removeExpiredTripPanelMessageUseCase.removeLauncherMessageOfRemovedStopsFromPriorityQueue(
            stopList
        )

        val formsIds = arrayListOf<DispatchFormPath>()
        formsIds.add(DispatchFormPath(stopId = 1, actionId = 1))
        formsIds.add(DispatchFormPath(stopId = 2, actionId = 1))
        formsIds.add(DispatchFormPath(stopId = 3, actionId = 1))
        formsIds.add(DispatchFormPath(stopId = 4, actionId = 1))
        formsIds.add(DispatchFormPath(stopId = 5, actionId = 1))

        val form = Gson().toJson(formsIds)

        coEvery {
            dataStoreManager.getValue(
                DataStoreManager.UNCOMPLETED_DISPATCH_FORMS_STACK_KEY,
                EMPTY_STRING
            )
        } returns form

        val newFormStack = removeExpiredTripPanelMessageUseCase.updateFormStack(
            dataStoreManager,
            distinctStopIdList.toMutableList()
        )

        assertThat(newFormStack.toMutableList(), hasItems(1, 2, 3, 4, 5))

    }

    @Test
    fun `verify form stack when all stops are removed`() = runTest {    //NOSONAR

        val stopList = mutableListOf<StopDetail>()

        val listOfLauncherMessage = PriorityBlockingQueue(
            1,
            LauncherMessagePriorityComparator
        )

        listOfLauncherMessage.add(LauncherMessageWithPriority("", 0, -1, Pair(0.0, 0.0), Pair(0.0, 0.0),1, 2, 5))

        listOfLauncherMessage.add(LauncherMessageWithPriority("", 0, -1, Pair(0.0, 0.0), Pair(0.0, 0.0),6))

        listOfLauncherMessage.add(LauncherMessageWithPriority("", 0, -1, Pair(0.0, 0.0), Pair(0.0, 0.0),1, 2))

        tripPanelUseCase.listLauncherMessageWithPriority = listOfLauncherMessage

        val (_, distinctStopIdList) = removeExpiredTripPanelMessageUseCase.removeLauncherMessageOfRemovedStopsFromPriorityQueue(
            stopList
        )

        val formsIds = arrayListOf<DispatchFormPath>()
        formsIds.add(DispatchFormPath(stopId = 1, actionId = 1))
        formsIds.add(DispatchFormPath(stopId = 2, actionId = 1))
        formsIds.add(DispatchFormPath(stopId = 3, actionId = 1))
        formsIds.add(DispatchFormPath(stopId = 4, actionId = 1))
        formsIds.add(DispatchFormPath(stopId = 5, actionId = 1))

        val form = Gson().toJson(formsIds)

        coEvery {
            dataStoreManager.getValue(
                DataStoreManager.UNCOMPLETED_DISPATCH_FORMS_STACK_KEY,
                EMPTY_STRING
            )
        } returns form

        val newFormStack = removeExpiredTripPanelMessageUseCase.updateFormStack(
            dataStoreManager,
            distinctStopIdList.toMutableList()
        )

        assertEquals(mutableListOf(), newFormStack.toMutableList())

    }

    @Test
    fun `verify updateFormStack fun returns no stopId when formStack is already empty`() =
        runTest {    //NOSONAR

            val stopList = mutableListOf(
                StopDetail(stopid = 1), StopDetail(stopid = 3),
                StopDetail(stopid = 4), StopDetail(stopid = 2), StopDetail(stopid = 5)
            )

            val listOfLauncherMessage = PriorityBlockingQueue(
                1,
                LauncherMessagePriorityComparator
            )

            listOfLauncherMessage.add(
                LauncherMessageWithPriority(
                    "",
                    0,
                    -1,
                    Pair(0.0, 0.0),
                    Pair(0.0, 0.0),
                    1,
                    2,
                    5
                )
            )

            listOfLauncherMessage.add(LauncherMessageWithPriority("", 0, -1, Pair(0.0, 0.0), Pair(0.0, 0.0),6))

            listOfLauncherMessage.add(LauncherMessageWithPriority("", 0, -1, Pair(0.0, 0.0), Pair(0.0, 0.0),1, 2))

            tripPanelUseCase.listLauncherMessageWithPriority = listOfLauncherMessage

            val (_, distinctStopIdList) = removeExpiredTripPanelMessageUseCase.removeLauncherMessageOfRemovedStopsFromPriorityQueue(
                stopList
            )

            coEvery {
                dataStoreManager.getValue(
                    DataStoreManager.UNCOMPLETED_DISPATCH_FORMS_STACK_KEY,
                    EMPTY_STRING
                )
            } returns EMPTY_STRING

            val newFormStack = removeExpiredTripPanelMessageUseCase.updateFormStack(
                dataStoreManager,
                distinctStopIdList.toMutableList()
            )

            assertEquals(0, newFormStack.size)

        }

    @Test
    fun `verify updateStopInformationInTripPanel`() = runTest {
        coEvery { tripPanelUseCase.updateStopInformationInTripPanel(any(), any()) } just runs
        removeExpiredTripPanelMessageUseCase.updateStopInformationInTripPanel(testScope, listOf())
        coVerify(exactly = 1) {
            tripPanelUseCase.updateStopInformationInTripPanel(any(), any())
        }
    }

    @After
    fun clear() {
        unloadKoinModules(modulesRequiredForTest)
        unmockkObject(FormUtils)
        unmockkObject(com.trimble.ttm.commons.utils.FormUtils)
        stopKoin()
        unmockkAll()
    }

}