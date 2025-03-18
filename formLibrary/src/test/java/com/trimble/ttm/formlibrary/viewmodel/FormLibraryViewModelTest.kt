package com.trimble.ttm.formlibrary.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import com.trimble.ttm.commons.composable.commonComposables.ScreenContentState
import com.trimble.ttm.commons.model.FormDef
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.usecase.AuthenticateUseCase
import com.trimble.ttm.commons.utils.ext.safeCollect
import com.trimble.ttm.formlibrary.CoroutineTestRuleWithStandardDispatcher
import com.trimble.ttm.formlibrary.R
import com.trimble.ttm.commons.preferenceManager.FormDataStoreManager
import com.trimble.ttm.formlibrary.model.Favourite
import com.trimble.ttm.formlibrary.usecases.EDVIRFormUseCase
import com.trimble.ttm.formlibrary.usecases.FormLibraryUseCase
import com.trimble.ttm.formlibrary.utils.EMPTY_STRING
import com.trimble.ttm.formlibrary.utils.FORMS
import com.trimble.ttm.formlibrary.utils.FORMS_SHORTCUT_USE_COUNT
import com.trimble.ttm.formlibrary.utils.FORM_GROUP_TAB_INDEX
import com.trimble.ttm.formlibrary.utils.HOTKEYS
import io.mockk.MockKAnnotations
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.just
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.koin.test.KoinTest
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.isAccessible
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FormLibraryViewModelTest : KoinTest {

    @get:Rule
    var coroutinesTestRule = CoroutineTestRuleWithStandardDispatcher()

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    @RelaxedMockK
    private lateinit var application: Application

    private lateinit var formDataStoreManager: FormDataStoreManager
    @RelaxedMockK
    private lateinit var context: Context

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var formLibraryViewModel: FormLibraryViewModel
    @MockK
    private lateinit var formLibraryUseCase: FormLibraryUseCase
    @MockK
    private lateinit var authenticateUseCase: AuthenticateUseCase
    @MockK
    private lateinit var edvirFormUseCase: EDVIRFormUseCase

    @RelaxedMockK
    private lateinit var appModuleCommunicator: AppModuleCommunicator

    @RelaxedMockK
    private lateinit var isHotKeysAvailableObserver: Observer<Boolean>

    @RelaxedMockK
    private lateinit var intent: Intent

    @Before
    fun setup() {
        MockKAnnotations.init(this, relaxed = true)
        formDataStoreManager = spyk(FormDataStoreManager(context))
        coEvery { authenticateUseCase.getAppModuleCommunicator() } returns appModuleCommunicator
        coEvery { edvirFormUseCase.setCrashReportIdentifier() } just runs
        coEvery { appModuleCommunicator.isFirebaseAuthenticated() } returns true
        coEvery { formLibraryUseCase.didLastFormReached() } returns flow { emit(false) }
        formLibraryViewModel = spyk(FormLibraryViewModel(
            application,
            formLibraryUseCase,
            authenticateUseCase,
            coroutinesTestRule.testDispatcherProvider,
            formDataStoreManager,
            edvirFormUseCase,
            appModuleCommunicator
        )
        )
        every { appModuleCommunicator.getAppModuleApplicationScope() } returns CoroutineScope(Job())
        every { context.filesDir } returns temporaryFolder.newFolder()
        coEvery { appModuleCommunicator.doGetCid() } returns "10119"
        coEvery { appModuleCommunicator.doGetObcId() } returns "123442"
        mockkStatic(android.util.Log::class)
    }


    @Test
    fun `verify cacheFormIdsAndUserIdsFromServer`() {
        every { formLibraryUseCase.listenForFirestoreException(any(), any(), any()) } just runs
        every {
            formLibraryUseCase.cacheGroupIdsFormIdsAndUserIdsFromServer(
                any(),
                any()
            )
        } just runs

        formLibraryViewModel.cacheFormIdsAndUserIdsFromServer()

        verify(exactly = 1) {
            formLibraryUseCase.listenForFirestoreException(any(), any(), any())
            formLibraryUseCase.cacheGroupIdsFormIdsAndUserIdsFromServer(any(), any())
        }
    }

    @Test
    fun `verify checkForUpdateFromServerIfActivityIsResumingFromBackgroundAfterLongTime`() {

        every {
            formLibraryUseCase.checkForUpdateFromServerIfActivityIsResumingFromBackgroundAfterLongTime(
                any(),
                any(),
                any()
            )
        } returns true
        formLibraryViewModel.checkForUpdateFromServerIfActivityIsResumingFromBackgroundAfterLongTime()

        verify(exactly = 1) {
            formLibraryUseCase.checkForUpdateFromServerIfActivityIsResumingFromBackgroundAfterLongTime(
                any(),
                any(),
                any()
            )
        }
    }

    @Test
    fun `verify updateExpiryTimeForFormFetchFromServer`() {

        formLibraryViewModel.updateExpiryTimeForFormFetchFromServer(4231432142314L)

        assertTrue(4231432142314L == formLibraryViewModel.expiryTimeForFormFetchFromServer)
    }

    @Test
    fun `verify getDriverOriginatedForms - forms returned`() = runTest {
        val formList = mutableMapOf<Double, FormDef>()
        formList[123.0] = FormDef(formid = 123)
        formList[124.0] = FormDef(formid = 124)
        formList[125.0] = FormDef(formid = 125)
        formList[126.0] = FormDef(formid = 126)
        val set = mutableSetOf<FormDef>()
        set.add(FormDef(formid = 123))
        set.add(FormDef(formid = 124))
        set.add(FormDef(formid = 125))
        set.add(FormDef(formid = 126))

        coEvery { formLibraryUseCase.getFormListFlow() } returns flow { emit(formList) }
        coEvery { formLibraryUseCase.sortFormListByName(any()) } returns set
        coEvery { formLibraryUseCase.getFormsOfCustomer(any(), any()) } just runs

        formLibraryViewModel.getDriverOriginatedForms()
        advanceUntilIdle()
        coVerify(exactly = 1) {
            formLibraryUseCase.getFormListFlow()
            formLibraryUseCase.sortFormListByName(any())
            formLibraryUseCase.getFormsOfCustomer(any(), any())
        }
    }

    @Test
    fun `verify getDriverOriginatedForms - forms request returned empty map`() = runTest {
        coEvery {
            formDataStoreManager.getValue(
                FormDataStoreManager.IS_FORM_LIBRARY_SNAPSHOT_EMPTY,
                any()
            )
        } returns false
        coEvery { formLibraryUseCase.getFormListFlow() } returns flow { emit(emptyMap()) }
        coEvery { formLibraryUseCase.getFormsOfCustomer(any(), any()) } just runs

        formLibraryViewModel.getDriverOriginatedForms()

        advanceUntilIdle()
        coVerify(exactly = 0) { formLibraryUseCase.sortFormListByName(any()) }
        coVerify(exactly = 1) {
            formLibraryUseCase.getFormListFlow()
            formLibraryUseCase.getFormsOfCustomer(any(), any())
        }
    }

    @Test
    fun `check recordShortCutIconClickEvent gets called when intent is not null, event name is not empty and selected tab is forms`(){
        every { formLibraryUseCase.recordShortCutIconClickEvent(any(),any()) } just runs
        every { intent.extras?.getString(FORM_GROUP_TAB_INDEX) ?: FORMS } returns FORMS
        formLibraryViewModel.recordShortCutIconClickEvent(FORMS_SHORTCUT_USE_COUNT,intent)
        verify(exactly = 1) {
            formLibraryUseCase.recordShortCutIconClickEvent(any(),any())
        }
    }

    @Test
    fun `check recordShortCutIconClickEvent gets called when intent is null and event name is not empty`(){
        every { formLibraryUseCase.recordShortCutIconClickEvent(any(),any()) } just runs
        formLibraryViewModel.recordShortCutIconClickEvent(FORMS_SHORTCUT_USE_COUNT,null)
        verify(exactly = 0) {
            formLibraryUseCase.recordShortCutIconClickEvent(any(),any())
        }
    }

    @Test
    fun `check recordShortCutIconClickEvent gets called when intent is not null and event name empty`(){
        every { formLibraryUseCase.recordShortCutIconClickEvent(any(),any()) } just runs
        formLibraryViewModel.recordShortCutIconClickEvent(EMPTY_STRING,intent)
        verify(exactly = 0) {
            formLibraryUseCase.recordShortCutIconClickEvent(any(),any())
        }
    }

    @Test
    fun `check recordShortCutIconClickEvent gets called when intent is null and event name empty`(){
        every { formLibraryUseCase.recordShortCutIconClickEvent(any(),any()) } just runs
        formLibraryViewModel.recordShortCutIconClickEvent(EMPTY_STRING,null)
        verify(exactly = 0) {
            formLibraryUseCase.recordShortCutIconClickEvent(any(),any())
        }
    }

    @Test
    fun `check recordShortCutIconClickEvent gets called when intent is not null, event name is not empty but selected index is Hotkeys`(){
        every { formLibraryUseCase.recordShortCutIconClickEvent(any(),any()) } just runs
        every { intent.extras?.getString(FORM_GROUP_TAB_INDEX) ?: HOTKEYS } returns  HOTKEYS
        formLibraryViewModel.recordShortCutIconClickEvent(FORMS_SHORTCUT_USE_COUNT,intent)
        verify(exactly = 0) {
            formLibraryUseCase.recordShortCutIconClickEvent(any(),any())
        }
    }



    @Test
    fun `verify getHotkeys when obcId is not empty`() = runTest {
        coEvery { formLibraryUseCase.getHotkeysWithDescription(any(), any(), any(), any(), any(), any(), any()) } just runs
        coEvery { appModuleCommunicator.doGetObcId() } returns "1234"
        formLibraryViewModel.getHotkeys()
        advanceUntilIdle()
        coVerify(exactly = 1) {
            formLibraryUseCase.getHotkeysWithDescription(any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `verify getHotkeys when obcId is empty`() = runTest {
        coEvery { appModuleCommunicator.doGetObcId() } returns EMPTY_STRING
        every { application.getString(R.string.unable_to_fetch_hot_keys) } returns "Failed to fetch hot keys"
        formLibraryViewModel.getHotkeys()
        advanceUntilIdle()
        coVerify(exactly = 0) {
            formLibraryUseCase.getHotkeysWithDescription(any(), any(), any(), any(), any(), any(), any())
        }
        assertEquals(ScreenContentState.Error("Failed to fetch hot keys"), formLibraryViewModel.hotKeysScreenState.value)
    }

    @Test
    fun `verify updateFavourite adds a favourite when currentUser is not empty`() = runTest {
        val favourite = Favourite(formId = "1234", formName = "Test Form", formClass = "1", cid = "5688")
        coEvery { formLibraryUseCase.addFavourite(any(), any()) } just runs
        coEvery { appModuleCommunicator.doGetCurrentUser(any()) } returns "testUser"

        formLibraryViewModel.updateFavourite(favourite)
        advanceUntilIdle()
        coVerify(exactly = 1) {
            formLibraryUseCase.addFavourite(any(), any())
        }
    }

    @Test
    fun `verify updateFavourite adds a favourite when currentUser is empty`() = runTest {
        val favourite = Favourite(formId = "1234", formName = "Test Form", formClass = "1", cid = "5688")
        coEvery { appModuleCommunicator.doGetCurrentUser(any()) } returns EMPTY_STRING

        formLibraryViewModel.updateFavourite(favourite)
        advanceUntilIdle()
        coVerify(exactly = 0) {
            formLibraryUseCase.addFavourite(any(), any())
        }
    }


    @Test
    fun `verify removeFavourite removes a favourite when currentUser is not empty`() = runTest {
        val formId = "testFormId"
        coEvery { formLibraryUseCase.removeFavourite(any(), any()) } just runs
        coEvery { appModuleCommunicator.doGetCurrentUser(any()) } returns "testUser"

        formLibraryViewModel.removeFavourite(formId)
        advanceUntilIdle()
        coVerify(exactly = 1) { formLibraryUseCase.removeFavourite(formId, "testUser") }
    }

    @Test
    fun `verify removeFavourite removes a favourite when currentUser is empty`() = runTest {
        val formId = "testFormId"
        coEvery { formLibraryUseCase.removeFavourite(any(), any()) } just runs
        coEvery { appModuleCommunicator.doGetCurrentUser(any()) } returns EMPTY_STRING

        formLibraryViewModel.removeFavourite(formId)

        coVerify(exactly = 0) { formLibraryUseCase.removeFavourite(formId, "testUser") }
    }

    @Test
    fun `verify removeFavourite removes a favourite when currentUser is not empty but formId is empty`() = runTest {
        val formId = EMPTY_STRING
        coEvery { formLibraryUseCase.removeFavourite(any(), any()) } just runs
        coEvery { appModuleCommunicator.doGetCurrentUser(any()) } returns "testUser"

        formLibraryViewModel.removeFavourite(formId)

        coVerify(exactly = 0) { formLibraryUseCase.removeFavourite(formId, "testUser") }
    }

    @Test
    fun `verify removeFavourite removes a favourite when currentUser is empty and formId is also empty`() = runTest {
        val formId = EMPTY_STRING
        coEvery { formLibraryUseCase.removeFavourite(any(), any()) } just runs
        coEvery { appModuleCommunicator.doGetCurrentUser(any()) } returns EMPTY_STRING

        formLibraryViewModel.removeFavourite(formId)

        coVerify(exactly = 0) { formLibraryUseCase.removeFavourite(formId, "testUser") }
    }

    @Test
    fun `verify getFavouriteForms method when currentUser is not empty`() = runTest {
        coEvery { appModuleCommunicator.doGetCurrentUser(any()) } returns EMPTY_STRING
        val favouritesSet = mutableSetOf(
            Favourite(formId = "1234", formName = "Test Form", formClass = "1", cid = "5688"),
            Favourite(formId = "1235", formName = "Test Form2", formClass = "1", cid = "5688")
        )
        coEvery { formLibraryUseCase.getFavouriteForms(any(), any()) } returns flow { emit(favouritesSet) }
        formLibraryViewModel.getFavouriteForms()
        advanceUntilIdle()
        formLibraryUseCase.getFavouriteForms("path","123").safeCollect(this.javaClass.name) {
            Assert.assertEquals(favouritesSet, it)
        }
        coVerify(exactly = 1) {
            formLibraryUseCase.getFavouriteForms(any(), any())
        }
    }

    @Test
    fun `verify getFavouriteForms method when currentUser is empty`() = runTest {
        coEvery { appModuleCommunicator.doGetCurrentUser(any()) } returns EMPTY_STRING
        formLibraryViewModel.getFavouriteForms()
        coVerify(exactly = 0) {
            formLibraryUseCase.getFavouriteForms(any(), any())
        }
    }

    @Test
    fun `verify getTabListBasedOnHotKeys method when hotKeys is available`() {
        val tabList = listOf(HOTKEYS,FORMS)
        assertEquals(tabList, formLibraryViewModel.getTabListBasedOnHotKeys(isHotKeysAvailable = true))
    }

    @Test
    fun `verify getTabListBasedOnHotKeys method when hotKeys is not available`() {
        val tabList = listOf(FORMS)
        assertEquals(tabList, formLibraryViewModel.getTabListBasedOnHotKeys(isHotKeysAvailable = false))
    }

    @Test
    fun `filterFormsBasedOnSearchText returns all forms when searchText is blank`() {
        val forms = setOf(
            Forms(FormDef(formid = 1, name = "Form1")),
            Forms(FormDef(formid = 2, name = "Form2"))
        )
        val result = formLibraryViewModel.filterFormsBasedOnSearchText(forms, "")
        assertEquals(forms.toMutableList(), result)
    }

    @Test
    fun `filterFormsBasedOnSearchText returns matching forms when searchText is not blank`() {
        val forms = setOf(
            Forms(FormDef(formid = 1, name = "Form1")),
            Forms(FormDef(formid = 2, name = "TestForm"))
        )
        val result = formLibraryViewModel.filterFormsBasedOnSearchText(forms, "Test")
        assertEquals(listOf(Forms(FormDef(formid = 2, name = "TestForm"))), result)
    }

    @Test
    fun `filterFormsBasedOnSearchText returns empty list when no forms match searchText`() {
        val forms = setOf(
            Forms(FormDef(formid = 1, name = "Form1")),
            Forms(FormDef(formid = 2, name = "Form2"))
        )
        val result = formLibraryViewModel.filterFormsBasedOnSearchText(forms, "NonExistent")
        assertEquals(emptyList(), result)
    }

    @Test
    fun `getGridCellsCountBasedOnItemsSize returns 2 when itemCount is less than or equal to 4`() {
        assertEquals(2, formLibraryViewModel.getGridCellsCountBasedOnItemsSize(4))
        assertEquals(2, formLibraryViewModel.getGridCellsCountBasedOnItemsSize(3))
        assertEquals(2, formLibraryViewModel.getGridCellsCountBasedOnItemsSize(2))
        assertEquals(2, formLibraryViewModel.getGridCellsCountBasedOnItemsSize(1))
    }

    @Test
    fun `getGridCellsCountBasedOnItemsSize returns 4 when itemCount is greater than 4`() {
        assertEquals(4, formLibraryViewModel.getGridCellsCountBasedOnItemsSize(5))
        assertEquals(4, formLibraryViewModel.getGridCellsCountBasedOnItemsSize(6))
    }


    @Test
    fun `getDriverOriginatedForms sets error state when customer ID and OBC ID are empty`() = runTest {
        coEvery { appModuleCommunicator.doGetCid() } returns EMPTY_STRING
        coEvery { appModuleCommunicator.doGetObcId() } returns EMPTY_STRING
        every { application.getString(R.string.err_loading_driver_originated_form_list) } returns "Failed to load Driver Originated Forms"

        formLibraryViewModel.getDriverOriginatedForms()

        assertEquals(ScreenContentState.Error("Failed to load Driver Originated Forms"), formLibraryViewModel.formsScreenState.value)
    }

    @Test
    fun `getDriverOriginatedForms sets error state when customer ID is empty`() = runTest {
        coEvery { appModuleCommunicator.doGetCid() } returns EMPTY_STRING
        coEvery { appModuleCommunicator.doGetObcId() } returns "1234"
        every { application.getString(R.string.err_loading_driver_originated_form_list) } returns "Failed to load Driver Originated Forms"

        formLibraryViewModel.getDriverOriginatedForms()

        assertEquals(ScreenContentState.Error("Failed to load Driver Originated Forms"), formLibraryViewModel.formsScreenState.value)
    }

    @Test
    fun `getDriverOriginatedForms sets error state when Obc ID is empty`() = runTest {
        coEvery { appModuleCommunicator.doGetCid() } returns "10119"
        coEvery { appModuleCommunicator.doGetObcId() } returns EMPTY_STRING
        every { application.getString(R.string.err_loading_driver_originated_form_list) } returns "Failed to load Driver Originated Forms"

        formLibraryViewModel.getDriverOriginatedForms()

        assertEquals(ScreenContentState.Error("Failed to load Driver Originated Forms"), formLibraryViewModel.formsScreenState.value)
    }

    @Test
    fun `getDriverOriginatedForms sets error state when form list is empty`() = runTest {
        coEvery { appModuleCommunicator.doGetCid() } returns "customerId"
        coEvery { appModuleCommunicator.doGetObcId() } returns "obcId"
        every { application.getString(R.string.no_forms_available) } returns "No Forms Available"
        coEvery { formLibraryUseCase.getFormListFlow() } returns flow { emit(emptyMap()) }
        coEvery { formLibraryUseCase.getFormsOfCustomer(any(), any()) } just runs
        coEvery { formDataStoreManager.getValue(
            FormDataStoreManager.IS_FORM_LIBRARY_SNAPSHOT_EMPTY,
            true
        ) } returns false

        formLibraryViewModel.getDriverOriginatedForms()
        advanceUntilIdle()
        assertEquals(ScreenContentState.Error("No Forms Available"), formLibraryViewModel.formsScreenState.value)
        assertEquals(0, formLibraryViewModel.formList.value.size)
    }

    @Test
    fun `getDriverOriginatedForms sets error state when form list is empty and internt connection is true`() = runTest {
        coEvery { appModuleCommunicator.doGetCid() } returns "customerId"
        coEvery { appModuleCommunicator.doGetObcId() } returns "obcId"
        every { application.getString(R.string.no_forms_available) } returns "No Forms Available"
        coEvery { formLibraryUseCase.getFormListFlow() } returns flow { emit(emptyMap()) }
        coEvery { formLibraryUseCase.getFormsOfCustomer(any(), any()) } just runs
        coEvery { formDataStoreManager.getValue(
            FormDataStoreManager.IS_FORM_LIBRARY_SNAPSHOT_EMPTY,
            true
        ) } returns true
        coEvery { formLibraryViewModel.isActiveInternetAvailable() } returns true

        formLibraryViewModel.getDriverOriginatedForms()
        assertEquals(0, formLibraryViewModel.formList.value.size)
    }

    @Test
    fun `getDriverOriginatedForms sets success state when form list is not empty`() = runTest {
        coEvery { appModuleCommunicator.doGetCid() } returns "customerId"
        coEvery { appModuleCommunicator.doGetObcId() } returns "obcId"
        val formDefMap = mapOf(1.0 to FormDef(formid = 1, name = "Form1"))
        coEvery { formLibraryUseCase.getFormListFlow() } returns flow { emit(formDefMap) }
        coEvery { formLibraryUseCase.getFormsOfCustomer(any(), any()) } just runs
        coEvery { formLibraryUseCase.sortFormListByName(formDefMap.values.toMutableSet()) } returns formDefMap.values.toMutableSet()

        formLibraryViewModel.getDriverOriginatedForms()
        advanceUntilIdle()
        assertEquals(ScreenContentState.Success(), formLibraryViewModel.formsScreenState.value)
        assertEquals(1, formLibraryViewModel.formList.value.size)
    }

    @Test
    fun `verify changeSelectedTab based on the given tab`() {
        formLibraryViewModel.changeSelectedTab(HOTKEYS)
        assertEquals(HOTKEYS, formLibraryViewModel.selectedTabIndex.value)
    }

    @Test
    fun `verify canDisplaySearch when selected tab is HOTKEYS`() {
        formLibraryViewModel.canDisplaySearch(HOTKEYS)
        assertFalse(formLibraryViewModel.canDisplaySearch.value)
    }

    @Test
    fun `verify canDisplaySearch when selected tab is FORMS`() {
        formLibraryViewModel.canDisplaySearch(FORMS)
        assertTrue(formLibraryViewModel.canDisplaySearch.value)
    }

    @Test
    fun `verify canShowHotKeysMenu when hotKeys is available and obcId is not empty`() = runTest {
        coEvery { appModuleCommunicator.doGetObcId() } returns "1234"
        coEvery { formLibraryUseCase.getHotKeysCount(any(), any(), any()) } returns 2
        formLibraryViewModel.isHotKeysAvailable.observeForever(isHotKeysAvailableObserver)
        formLibraryViewModel.canShowHotKeysTab()
        advanceUntilIdle()
        assertEquals(true, formLibraryViewModel.isHotKeysAvailable.value)
        assertEquals(ScreenContentState.Success(), formLibraryViewModel.formsAndHotKeysScreenContentState.value)
        verify(exactly = 1) { isHotKeysAvailableObserver.onChanged(true) }
        formLibraryViewModel.isHotKeysAvailable.removeObserver(isHotKeysAvailableObserver)
    }

    @Test
    fun `verify canShowHotKeysMenu when hotKeys is not available and obcId is not empty`() = runTest {
        coEvery { appModuleCommunicator.doGetObcId() } returns "1234"
        coEvery { formLibraryUseCase.getHotKeysCount(any(), any(), any()) } returns  0
        formLibraryViewModel.isHotKeysAvailable.observeForever(isHotKeysAvailableObserver)
        formLibraryViewModel.canShowHotKeysTab()
        advanceUntilIdle()
        assertEquals(false, formLibraryViewModel.isHotKeysAvailable.value)
        assertEquals(ScreenContentState.Success(), formLibraryViewModel.formsAndHotKeysScreenContentState.value)
        verify(exactly = 1) { isHotKeysAvailableObserver.onChanged(false) }
        formLibraryViewModel.isHotKeysAvailable.removeObserver(isHotKeysAvailableObserver)
    }

    @Test
    fun `verify canShowHotKeysMenu when obcId is empty`() = runTest {
        coEvery { appModuleCommunicator.doGetObcId() } returns EMPTY_STRING
        formLibraryViewModel.isHotKeysAvailable.observeForever(isHotKeysAvailableObserver)
        formLibraryViewModel.canShowHotKeysTab()
        advanceUntilIdle()
        assertEquals(ScreenContentState.Success(), formLibraryViewModel.formsAndHotKeysScreenContentState.value)
        verify(exactly = 0) { isHotKeysAvailableObserver.onChanged(any()) }
        formLibraryViewModel.isHotKeysAvailable.removeObserver(isHotKeysAvailableObserver)
    }

    @Test
    fun `changeListView sets _isListView to false when isListView is true`() = runTest {
        formLibraryViewModel.changeListView(true)
        assertFalse(formLibraryViewModel.isListView.value)
    }

    @Test
    fun `changeListView sets _isListView to true when isListView is false`() = runTest {
        formLibraryViewModel.changeListView(false)
        assertTrue(formLibraryViewModel.isListView.value)
    }

    @Test
    fun `verify fetchFormsWithPagination does nothing when isPaginationLoading is true`() = runTest {
        val isPaginationLoadingField = formLibraryViewModel::class.declaredMemberProperties
            .first { it.name == "_isPaginationLoading" }
        isPaginationLoadingField.isAccessible = true
        val currentPageField = formLibraryViewModel::class.declaredMemberProperties
            .first { it.name == "currentPage" }
        currentPageField.isAccessible = true
        val currentPageValue = (currentPageField.getter.call(formLibraryViewModel) as Int)
        val isPaginationLoadingMutable = (isPaginationLoadingField.getter.call(formLibraryViewModel) as MutableStateFlow<Boolean>)
        isPaginationLoadingMutable.value = true
        formLibraryViewModel.fetchFormsWithPagination()
        assertTrue(formLibraryViewModel.isPaginationLoading.value)
        coVerify(exactly = 0) { formLibraryViewModel.getDriverOriginatedForms() }
        assertEquals(0,currentPageValue)
    }

    @Test
    fun `verify fetchFormsWithPagination when isPaginationLoading is false`() = runTest {
        coEvery { formLibraryViewModel.getDriverOriginatedForms(any()) } just runs
        val isPaginationLoadingField = formLibraryViewModel::class.declaredMemberProperties
            .first { it.name == "_isPaginationLoading" }
        isPaginationLoadingField.isAccessible = true
        val currentPageField = formLibraryViewModel::class.declaredMemberProperties
            .first { it.name == "currentPage" }
        currentPageField.isAccessible = true
        val isPaginationLoadingMutable = (isPaginationLoadingField.getter.call(formLibraryViewModel) as MutableStateFlow<Boolean>)
        isPaginationLoadingMutable.value = false
        formLibraryViewModel.fetchFormsWithPagination()
        assertTrue(formLibraryViewModel.isPaginationLoading.value)
        assertEquals(1,(currentPageField.getter.call(formLibraryViewModel) as Int))
    }

    @Test
    fun `getMaxLinesForHotKeys returns 2 when hotKeysItemCount is greater than 12`() {
        val result = formLibraryViewModel.getMaxLinesForHotKeys(13)
        assertEquals(2, result)
    }

    @Test
    fun `getMaxLinesForHotKeys returns 3 when hotKeysItemCount is between 9 and 12`() {
        val result = formLibraryViewModel.getMaxLinesForHotKeys(10)
        assertEquals(3, result)
    }

    @Test
    fun `getMaxLinesForHotKeys returns 6 when hotKeysItemCount is less than 9`() {
        val result = formLibraryViewModel.getMaxLinesForHotKeys(8)
        assertEquals(6, result)
    }

    @After
    fun after() {
        unmockkAll()
        clearAllMocks()
    }

}