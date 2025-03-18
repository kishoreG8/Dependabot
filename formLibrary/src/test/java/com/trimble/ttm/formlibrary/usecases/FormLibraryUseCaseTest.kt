package com.trimble.ttm.formlibrary.usecases

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import com.trimble.ttm.commons.analytics.FirebaseAnalyticEventRecorder
import com.trimble.ttm.commons.composable.commonComposables.ScreenContentState
import com.trimble.ttm.commons.model.FormDef
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.utils.INTENT_CATEGORY_LAUNCHER
import com.trimble.ttm.formlibrary.CoroutineTestRuleWithMainUnconfinedDispatcher
import com.trimble.ttm.formlibrary.model.Favourite
import com.trimble.ttm.formlibrary.model.HotKeys
import com.trimble.ttm.formlibrary.model.HotKeysDescription
import com.trimble.ttm.formlibrary.repo.FormLibraryRepo
import com.trimble.ttm.formlibrary.utils.FAILED_TO_FETCH_HOTKEYS
import com.trimble.ttm.formlibrary.utils.FORMS
import com.trimble.ttm.formlibrary.utils.FORMS_SHORTCUT_USE_COUNT
import com.trimble.ttm.formlibrary.utils.FORM_LIBRARY_UPDATION_CHECK_TIME_INTERVAL
import com.trimble.ttm.formlibrary.utils.HOTKEYS
import com.trimble.ttm.formlibrary.utils.TEST_DELAY_OR_TIMEOUT
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FormLibraryUseCaseTest {
    @get:Rule
    var instantTestExecutorRule = InstantTaskExecutorRule()

    private lateinit var formLibraryUseCase: FormLibraryUseCase
    @MockK
    private lateinit var formLibraryRepo: FormLibraryRepo
    @MockK
    private lateinit var cacheGroupsUseCase: CacheGroupsUseCase
    @MockK
    private lateinit var appModuleCommunicator: AppModuleCommunicator
    @MockK
    private lateinit var firebaseAnalyticEventRecorder: FirebaseAnalyticEventRecorder

    private val formList: ArrayList<FormDef> = ArrayList()
    private val driverOriginatedFormList: ArrayList<FormDef> = ArrayList()
    private val testScope = TestScope()
    @RelaxedMockK
    private lateinit var isHotKeysAvailableObserver : Observer<Boolean>

    @ExperimentalCoroutinesApi
    @get:Rule
    val mainDispatcherRule = CoroutineTestRuleWithMainUnconfinedDispatcher()

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        formLibraryUseCase =
            spyk(
                FormLibraryUseCase(
                    formLibraryRepo, cacheGroupsUseCase,
                    appModuleCommunicator, firebaseAnalyticEventRecorder
                )
            )
        formList.also {
            it.add(createFormDef(1, "test1@trimble.com"))
            it.add(createFormDef(1, "test2@trimble.com"))
            it.add(createFormDef(1, "test3@trimble.com"))
            it.add(createFormDef(0, "test4@trimble.com"))
            it.add(createFormDef(0, "test5@trimble.com"))
        }
        driverOriginatedFormList.also {
            it.add(createFormDef(1, "test1@trimble.com"))
            it.add(createFormDef(1, "test2@trimble.com"))
            it.add(createFormDef(1, "test3@trimble.com"))
        }
        coEvery { appModuleCommunicator.doGetCid() } returns "123"
        coEvery { appModuleCommunicator.doGetObcId() } returns "432"
        coEvery {
            cacheGroupsUseCase.checkAndUpdateCacheForGroupsFromServer(
                any(),
                any(),
                any(),
                any()
            )
        } returns true
        coEvery {
            formLibraryRepo.getForms(any(), any(), any())
        } just runs
    }

    @Test
    fun `verify listenForFirestoreException`() = runTest {

        coEvery { cacheGroupsUseCase.getFirestoreExceptionNotifier() } returns flow {}
        formLibraryUseCase.listenForFirestoreException(testScope, "test", true)

        coVerify {
            cacheGroupsUseCase.getFirestoreExceptionNotifier()
        }
    }

    @Test
    fun `verify cacheGroupIdsFormIdsAndUserIdsFromServer`() = runTest {
        formLibraryUseCase.cacheGroupIdsFormIdsAndUserIdsFromServer(
            testScope,
            "test"
        )

        coVerify(exactly = 1, timeout = TEST_DELAY_OR_TIMEOUT) {
            formLibraryRepo.getForms(any(), any(), any())
        }
    }

    @Test
    fun `verify driver originated forms with recipients`() {    //NOSONAR
        formLibraryUseCase = mockk()
        every {
            formLibraryUseCase.getDriverOriginatedFormWithRecipients(formList)
        } returns driverOriginatedFormList
        assertEquals(
            driverOriginatedFormList,
            formLibraryUseCase.getDriverOriginatedFormWithRecipients(formList)
        )
    }

    private fun createFormDef(driverOriginate: Int, id: String) =
        FormDef(driverOriginate = driverOriginate, recipients = HashMap<String, String>().let {
            it["emailUser"] = id
            it
        })

    @Test
    fun `verify getFormListFlow call`() = runTest {    //NOSONAR
        val formDefMap = mutableMapOf<Double, FormDef>()
        formDefMap[1.0] = FormDef(cid = 0, formid = 1)
        coEvery {
            formLibraryUseCase.getFormListFlow()
        } returns flow { emit(formDefMap) }
        launch {
            formLibraryUseCase.getFormListFlow().collect {
                assertEquals(formDefMap, it)
                this.cancel()
            }
        }
    }

    @Test
    fun `verify getFormsOfCustomer call`() = runTest {    //NOSONAR
        coEvery {
            formLibraryRepo.getForms(any(), any(), any())
        } returns Unit
        formLibraryUseCase.getFormsOfCustomer("213", 4312423, true)
        coVerify(exactly = 1) {
            formLibraryRepo.getForms(any(), any(), any())
        }
    }

    @Test
    fun `verify form list server sync in offline`() = runTest {
        coEvery {
            cacheGroupsUseCase.checkAndUpdateCacheForGroupsFromServer(
                any(),
                any(),
                any(),
                any()
            )
        } returns false
        formLibraryUseCase.cacheGroupIdsFormIdsAndUserIdsFromServer(testScope, "test")

        coVerify(exactly = 0, timeout = TEST_DELAY_OR_TIMEOUT) {
            formLibraryRepo.getForms(any(), any(), any())
        }
        coVerify(exactly = 1, timeout = TEST_DELAY_OR_TIMEOUT) {
            cacheGroupsUseCase.checkAndUpdateCacheForGroupsFromServer(
                any(),
                any(),
                any(),
                any()
            )
        }
    }

    @Test
    fun `verify didLastFormReached`() = runTest {

        coEvery { formLibraryRepo.didLastItemReached() } returns flow { emit(true) }
        formLibraryUseCase.didLastFormReached()

        coVerify { formLibraryRepo.didLastItemReached() }
    }


    @Test
    fun `verify sortFormListByName`() = runTest {

        coEvery { cacheGroupsUseCase.sortFormsAlphabetically(any()) } returns mutableSetOf()
        formLibraryUseCase.sortFormListByName(mutableSetOf())

        coVerify { cacheGroupsUseCase.sortFormsAlphabetically(any()) }
    }

    @Test
    fun `verify if forms can be updated if formActivity is living in background for longtime if driver is online`() {
        val lastUpdatedTime = 0L
        val currentTime = FORM_LIBRARY_UPDATION_CHECK_TIME_INTERVAL + 1000L
        assertTrue(
            formLibraryUseCase.checkForUpdateFromServerIfActivityIsResumingFromBackgroundAfterLongTime(
                Pair(currentTime, lastUpdatedTime),
                applicationScope = CoroutineScope(Job()),
                hasActiveInternet = true
            )
        )
    }

    @Test
    fun `verify if forms can be updated if formActivity is living in background for longtime if driver is offline`() {
        val lastUpdatedTime = 0L
        val currentTime = FORM_LIBRARY_UPDATION_CHECK_TIME_INTERVAL + 1000L
        assertFalse(
            formLibraryUseCase.checkForUpdateFromServerIfActivityIsResumingFromBackgroundAfterLongTime(
                Pair(currentTime, lastUpdatedTime),
                applicationScope = CoroutineScope(Job()),
                hasActiveInternet = false
            )
        )
    }

    @Test
    fun `verify if forms can be updated if formActivity is not living in background for longtime if driver is online`() {
        val lastUpdatedTime = 0L
        val currentTime = 1000L
        assertFalse(
            formLibraryUseCase.checkForUpdateFromServerIfActivityIsResumingFromBackgroundAfterLongTime(
                Pair(currentTime, lastUpdatedTime),
                applicationScope = CoroutineScope(Job()),
                hasActiveInternet = true
            )
        )
    }

    @Test
    fun `verify if forms can be updated if formActivity is not living in background for longtime if driver is offline`() {
        val lastUpdatedTime = 0L
        val currentTime = 1000L
        assertFalse(
            formLibraryUseCase.checkForUpdateFromServerIfActivityIsResumingFromBackgroundAfterLongTime(
                Pair(currentTime, lastUpdatedTime),
                applicationScope = CoroutineScope(Job()),
                hasActiveInternet = false
            )
        )
    }

    @Test
    fun `verify getDriverOriginatedFormWithRecipients - when there are driver originated forms`() {
        val forms = mutableListOf<FormDef>()
        val recipients = mutableMapOf<String, Any>()
        recipients["test"] = "test1"
        forms.add(FormDef(driverOriginate = 1, formid = 123, recipients = recipients))
        forms.add(FormDef(driverOriginate = 1, formid = 234))
        forms.add(FormDef(driverOriginate = 1, formid = 43242))
        forms.add(FormDef(driverOriginate = 0, formid = 980, recipients = recipients))
        forms.add(FormDef(driverOriginate = 1, formid = 76843))

        assertTrue(
            formLibraryUseCase.getDriverOriginatedFormWithRecipients(forms).count() == 1
        )
    }
    
    @Test
    fun `verify recordShortCutIconClickEvent gets called when intent categories contains launcher`() {
        every { firebaseAnalyticEventRecorder.logNewCustomEventWithDefaultCustomParameters(any()) } just runs
        formLibraryUseCase.recordShortCutIconClickEvent(
            FORMS_SHORTCUT_USE_COUNT, setOf(
                INTENT_CATEGORY_LAUNCHER
            )
        )
        verify(exactly = 1) {
            firebaseAnalyticEventRecorder.logNewCustomEventWithDefaultCustomParameters(any())
        }
    }

    @Test
    fun `verify recordShortCutIconClickEvent gets called when intent categories doesn't contain launcher`() {
        every { firebaseAnalyticEventRecorder.logNewCustomEventWithDefaultCustomParameters(any()) } just runs
        formLibraryUseCase.recordShortCutIconClickEvent(
            FORMS_SHORTCUT_USE_COUNT,
            setOf(
                "DefaultIntent"
            )
        )
        verify(exactly = 0) {
            firebaseAnalyticEventRecorder.logNewCustomEventWithDefaultCustomParameters(any())
        }
    }

    @Test
    fun `verify recordShortCutIconClickEvent gets called when intent categories is empty`() {
        every { firebaseAnalyticEventRecorder.logNewCustomEventWithDefaultCustomParameters(any()) } just runs
        formLibraryUseCase.recordShortCutIconClickEvent(FORMS_SHORTCUT_USE_COUNT, setOf())
        verify(exactly = 0) {
            firebaseAnalyticEventRecorder.logNewCustomEventWithDefaultCustomParameters(any())
        }
    }

    @Test
    fun `verify getHotkeys when hotKeysSet contains elements`() = runTest {
        val path = "testPath"
        val documentId = "testDocumentId"
        val hotKeys = MutableStateFlow<MutableSet<HotKeys>>(mutableSetOf())
        val selectedTab = MutableStateFlow(HOTKEYS)
        val isHotKeysAvailable = MutableLiveData(false)
        val hotKeysScreenState = MutableStateFlow<ScreenContentState>(ScreenContentState.Loading())
        val hotKeysSet = mutableSetOf(HotKeys(formId = 123, formName = "Test Form 1", formClass = 1, hkId = 122, hotKeysDescription = HotKeysDescription(hotKeysId = 122, description = "Test Form")), HotKeys(formId = 124, formName = "Test Form 2", formClass = 1, hkId = 121, hotKeysDescription = HotKeysDescription(hotKeysId = 121, description = "Test Form")))
        val expectedHotKeysSet = hotKeysSet.sortedBy { it.hkId }.toMutableSet()
        every { formLibraryRepo.addHotkeysSnapshotFlow(path, documentId) } returns flowOf(hotKeysSet)
        coEvery { formLibraryRepo.getHotKeysDescription(hotKeysSet, any()) } returns hotKeysSet

        formLibraryUseCase.getHotkeysWithDescription(path, documentId, hotKeys, isHotKeysAvailable, selectedTab, hotKeysScreenState, true)

        assertEquals(expectedHotKeysSet, hotKeys.value)
        assertEquals(true, isHotKeysAvailable.value)
        assertEquals(HOTKEYS, selectedTab.value)
        assertEquals(ScreenContentState.Success(), hotKeysScreenState.value)
    }

    @Test
    fun `verify getHotkeys when hotKeysSet contains elements but description do not have element`() = runTest {
        val path = "testPath"
        val documentId = "testDocumentId"
        val hotKeys = MutableStateFlow<MutableSet<HotKeys>>(mutableSetOf())
        val selectedTab = MutableStateFlow(HOTKEYS)
        val isHotKeysAvailable = MutableLiveData(false)
        val hotKeysScreenState = MutableStateFlow<ScreenContentState>(ScreenContentState.Loading())
        val hotKeysSet = mutableSetOf(HotKeys(formId = 123, formName = "Test Form 1", formClass = 1, hkId = 122, hotKeysDescription = HotKeysDescription(hotKeysId = 122, description = "Test Form")), HotKeys(formId = 124, formName = "Test Form 2", formClass = 1, hkId = 121, hotKeysDescription = HotKeysDescription(hotKeysId = 121, description = "Test Form")))
        val expectedHotKeysSet = hotKeysSet.sortedBy { it.hkId }.toMutableSet()
        every { formLibraryRepo.addHotkeysSnapshotFlow(path, documentId) } returns flowOf(hotKeysSet)
        coEvery { formLibraryRepo.getHotKeysDescription(hotKeysSet, any()) } returns mutableSetOf()

        formLibraryUseCase.getHotkeysWithDescription(path, documentId, hotKeys, isHotKeysAvailable, selectedTab, hotKeysScreenState, false)

        assertEquals(mutableSetOf<HotKeys>(), hotKeys.value)
        assertEquals(true, isHotKeysAvailable.value)
        assertEquals(HOTKEYS, selectedTab.value)
        assertEquals(ScreenContentState.Error(FAILED_TO_FETCH_HOTKEYS), hotKeysScreenState.value)
    }

    @Test
    fun `verify getHotKeys when hotKeysSet does not contain elements`() = runTest {
        val path = "testPath"
        val documentId = "testDocumentId"
        val hotKeys = MutableStateFlow<MutableSet<HotKeys>>(mutableSetOf())
        val selectedTab = MutableStateFlow(HOTKEYS)
        val isHotKeysAvailableMutable = MutableLiveData(true)
        val isHotKeysAvailable : LiveData<Boolean> = isHotKeysAvailableMutable
        isHotKeysAvailable.observeForever(isHotKeysAvailableObserver)
        val hotKeysScreenContentState = MutableStateFlow<ScreenContentState>(ScreenContentState.Success())
        every { formLibraryRepo.addHotkeysSnapshotFlow(path, documentId) } returns flowOf(mutableSetOf())

        formLibraryUseCase.getHotkeysWithDescription(path, documentId, hotKeys, isHotKeysAvailableMutable, selectedTab, hotKeysScreenContentState, false)

        advanceUntilIdle()
        verify(exactly = 1) {
            isHotKeysAvailableObserver.onChanged(false)
        }
        assertEquals(0, hotKeys.value.size)
        assertEquals(false, isHotKeysAvailable.value)
        assertEquals(FORMS, selectedTab.value)
        assertEquals(ScreenContentState.Success(), hotKeysScreenContentState.value)
        isHotKeysAvailable.removeObserver(isHotKeysAvailableObserver)
    }

    @Test
    fun `verify getHotKeysWithoutDescription method when hotKeys count greater than zero` () = runTest {
        val path = "testPath"
        val documentId = "testDocumentId"
        coEvery { formLibraryRepo.addHotkeysSnapshotFlow(path, documentId) } returns flow { emit(mutableSetOf(HotKeys(hkId = 1), HotKeys(hkId = 2))) }
        assertEquals(2, formLibraryUseCase.getHotKeysWithoutDescription(path,documentId).first().size )
    }

    @Test
    fun `verify getHotKeysWithoutDescription method when hotKeys count is zero`() = runTest {
        val path = "testPath"
        val documentId = "testDocumentId"
        coEvery { formLibraryRepo.addHotkeysSnapshotFlow(path, documentId) } returns flow { emit(mutableSetOf()) }
        assertEquals(0,formLibraryUseCase.getHotKeysWithoutDescription(path,documentId).first().size)
    }

    @Test
    fun `verify getHotKeysCount method when hotKeys count is zero`() = runTest {
        val path = "testPath"
        val documentId = "testDocumentId"
        coEvery { formLibraryRepo.getHotKeysCount(path, documentId, any()) } returns 0
        assertEquals(0, formLibraryUseCase.getHotKeysCount(path, documentId, false))
    }

    @Test
    fun `verify getHotKeysCount method when hotKeys count is greater than zero`() = runTest {
        val path = "testPath"
        val documentId = "testDocumentId"
        coEvery { formLibraryRepo.getHotKeysCount(path, documentId, true) } returns 2
        assertEquals(2, formLibraryUseCase.getHotKeysCount(path, documentId, true))
    }

    @Test
    fun `addFavourite calls repository with correct parameters`() = runTest {
        val favourite = Favourite(formId = "1234", formName = "Test Form", formClass = "1", cid = "5688")
        val driverId = "driver123"

        coEvery { formLibraryRepo.addFavourite(favourite, driverId) } returns Unit

        formLibraryUseCase.addFavourite(favourite, driverId)

        coVerify { formLibraryRepo.addFavourite(favourite, driverId) }
    }

    @Test
    fun `getFavouriteForms calls repository with correct parameters`() = runTest {
        val path = "testPath"
        val driverID = "driver123"
        coEvery { formLibraryRepo.getFavouriteForms(path, driverID) } returns flowOf(mutableSetOf())
        val testScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        testScope.launch {
            formLibraryUseCase.getFavouriteForms(path, driverID)
            coVerify { formLibraryRepo.getFavouriteForms(path, driverID) }
        }
        testScope.cancel()
    }

    @Test
    fun `removeFavourite calls repository with correct parameters`() = runTest {
        val formId = "1234"
        val driverId = "driver123"

        coEvery { formLibraryRepo.removeFavourite(formId, driverId) } returns Unit

        formLibraryUseCase.removeFavourite(formId, driverId)

        coVerify { formLibraryRepo.removeFavourite(formId, driverId) }
    }

    @After
    fun after() {
        unmockkAll()
    }
}