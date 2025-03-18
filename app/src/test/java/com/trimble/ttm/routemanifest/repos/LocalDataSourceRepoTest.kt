package com.trimble.ttm.routemanifest.repos

import android.content.Context
import com.trimble.ttm.commons.model.FormDef
import com.trimble.ttm.commons.model.FormField
import com.trimble.ttm.commons.model.FormTemplate
import com.trimble.ttm.commons.model.Stop
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.preferenceManager.DataStoreManager
import com.trimble.ttm.commons.preferenceManager.DataStoreManager.Companion.ACTIVE_DISPATCH_KEY
import com.trimble.ttm.commons.preferenceManager.DataStoreManager.Companion.CURRENT_STOP_KEY
import com.trimble.ttm.commons.preferenceManager.FormDataStoreManager
import com.trimble.ttm.commons.preferenceManager.FormDataStoreManager.Companion.IS_IN_FORM_KEY
import com.trimble.ttm.commons.repo.BackboneRepository
import com.trimble.ttm.commons.repo.LocalDataSourceRepo
import com.trimble.ttm.commons.repo.LocalDataSourceRepoImpl
import com.trimble.ttm.formlibrary.utils.EMPTY_STRING
import com.trimble.ttm.formlibrary.utils.FormUtils
import com.trimble.ttm.routemanifest.application.WorkflowApplication
import com.trimble.ttm.routemanifest.utils.ApplicationContextProvider
import com.trimble.ttm.routemanifest.utils.Utils
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockkObject
import io.mockk.spyk
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.context.unloadKoinModules
import org.koin.dsl.module

class LocalDataSourceRepoTest {
    private val startBucks = "Star Bucks"
    private lateinit var localDataSourceRepo: LocalDataSourceRepo

    @RelaxedMockK
    private lateinit var application: WorkflowApplication

    @MockK
    private lateinit var appModuleCommunicator: AppModuleCommunicator

    @MockK
    private lateinit var backboneRepository: BackboneRepository

    private var modulesRequiredForTest = module {
        single { dataStoreManager }
        single { formDataStoreManager }
        single { backboneRepository }
        single<LocalDataSourceRepo> { LocalDataSourceRepoImpl(get(), get(), appModuleCommunicator) }
    }
    private lateinit var dataStoreManager: DataStoreManager
    private lateinit var formDataStoreManager: FormDataStoreManager

    @MockK
    private lateinit var context: Context
    @get:Rule
    val temporaryFolder = TemporaryFolder()


    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        startKoin {
            modules(modulesRequiredForTest)
        }
        every { context.packageName } returns "com.trimble.ttm.formsandworkflow"
        mockkObject(WorkflowApplication)
        mockkObject(ApplicationContextProvider)
        every { application.applicationContext } returns application
        every { ApplicationContextProvider.getApplicationContext() } returns application.applicationContext
        dataStoreManager = spyk(DataStoreManager(context))
        formDataStoreManager = spyk(FormDataStoreManager(context))
        every { context.filesDir } returns temporaryFolder.newFolder()
        localDataSourceRepo = spyk(LocalDataSourceRepoImpl(dataStoreManager, formDataStoreManager, appModuleCommunicator))
        every { context.applicationContext } returns context
    }

    @Test
    fun `verify hasActiveDispatch`() = runTest {    //NOSONAR
        coEvery { dataStoreManager.containsKey(ACTIVE_DISPATCH_KEY) } returns true
        assertEquals(true, localDataSourceRepo.hasActiveDispatch())
    }

    @Test
    fun `verify getActiveDispatchId`() = runTest {  //NOSONAR
        coEvery { dataStoreManager.getValue(ACTIVE_DISPATCH_KEY, EMPTY_STRING) } returns "748901"
        assertEquals("748901", localDataSourceRepo.getActiveDispatchId(EMPTY_STRING))
    }

    @Test
    fun `verify getCurrentStop`() = runTest {    //NOSONAR
        val currentStop = Stop().apply {
            stopId = 0
            stopName = startBucks
        }
        coEvery { dataStoreManager.getValue(CURRENT_STOP_KEY, EMPTY_STRING) } returns Utils.toJsonString(currentStop).toString()
        assertEquals(0, localDataSourceRepo.getCurrentStop()?.stopId)
        assertEquals(startBucks, localDataSourceRepo.getCurrentStop()?.stopName)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `verify getErrorFieldsCount`() {
        val formFieldList = arrayListOf<FormField>()
        formFieldList.add(
            FormField(required = 1).also
        {
            it.uiData = "rwer"
            it.errorMessage = ""
        })
        formFieldList.add(
            FormField(required = 1).also
        {
            it.uiData = ""
            it.errorMessage = ""
        })
        formFieldList.add(
            FormField(required = 1).also
        {
            it.uiData = ""
            it.errorMessage = "tests"
        })
        formFieldList.add(
            FormField(required = 1).also
        {
            it.uiData = "rwer"
            it.errorMessage = ""
        })
        assertTrue(FormUtils.getErrorFields(FormTemplate(FormDef(), formFieldList)).count() == 2)
    }

    @Test
    fun `verify setToAppModuleDataStore call`() = runTest {
        localDataSourceRepo.setToAppModuleDataStore(ACTIVE_DISPATCH_KEY, "100")
        coVerify {
            dataStoreManager.setValue(ACTIVE_DISPATCH_KEY, "100")
        }
    }

    @Test
    fun `verify setToFormLibModuleDataStore call`() = runTest {
        localDataSourceRepo.setToFormLibModuleDataStore(IS_IN_FORM_KEY, true)
        coVerify {
            formDataStoreManager.setValue(IS_IN_FORM_KEY, true)
        }
    }

    @Test
    fun `verify getFromAppModuleDataStore call`() = runTest {
        localDataSourceRepo.getFromAppModuleDataStore(ACTIVE_DISPATCH_KEY, "123")
        coVerify {
            dataStoreManager.getValue(ACTIVE_DISPATCH_KEY, "123")
        }
    }

    @Test
    fun `verify getFromFormLibModuleDataStore call`() = runTest {
        localDataSourceRepo.getFromFormLibModuleDataStore(IS_IN_FORM_KEY, false)
        coVerify {
            formDataStoreManager.getValue(IS_IN_FORM_KEY, false)
        }
    }

    @Test
    fun `verify removeAllKeysOfAppModuleDataStore call`() = runTest {
        localDataSourceRepo.removeAllKeysOfAppModuleDataStore()
        coVerify {
            dataStoreManager.removeAllKeys()
        }
    }

    @Test
    fun `verify removeItemFromAppModuleDataStore call`() = runTest {
        localDataSourceRepo.removeItemFromAppModuleDataStore(ACTIVE_DISPATCH_KEY)
        coVerify {
            dataStoreManager.removeItem(ACTIVE_DISPATCH_KEY)
        }
    }

    @Test
    fun `verify isKeyAvailableInAppModuleDataStore call`() = runTest {
        localDataSourceRepo.isKeyAvailableInAppModuleDataStore(ACTIVE_DISPATCH_KEY)
        coVerify {
            dataStoreManager.containsKey(ACTIVE_DISPATCH_KEY)
        }
    }

    @Test
    fun `verify getAppModuleCommunicator call`() = runTest {
        val appCommunicator = localDataSourceRepo.getAppModuleCommunicator()
        assertEquals(appModuleCommunicator, appCommunicator)
    }


    @After
    fun after() {
        unloadKoinModules(modulesRequiredForTest)
        stopKoin()
        unmockkAll()
    }
}