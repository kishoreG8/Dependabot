package com.trimble.ttm.formlibrary.repo

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.trimble.ttm.commons.preferenceManager.FormDataStoreManager
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.spyk
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.toSet
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DraftingRepoImplTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    lateinit var context : Context

    private lateinit var SUT: DraftingRepoImpl

    private lateinit var formDataStoreManager: FormDataStoreManager

    private lateinit var preferences : Preferences

    private val testScope = TestScope()

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        context = mockk()
        formDataStoreManager = spyk(FormDataStoreManager(context))
        preferences = emptyPreferences()
        every { context.applicationContext } returns context
        every { context.filesDir } returns temporaryFolder.newFolder()
        SUT = DraftingRepoImpl(formDataStoreManager, testScope)
    }

    @Test
    fun `verify initDraftProcessing changes`() = runTest {
        val results = mutableSetOf<Boolean>()
        val collectJob = launch(UnconfinedTestDispatcher()) {
            SUT.initDraftProcessing.toSet(results)
        }
        SUT.setInitDraftProcessing(false)
        assert(results.elementAt(0).not())
        SUT.setInitDraftProcessing(true)
        assert(results.elementAt(1))
        collectJob.cancel()
    }

    @Test
    fun `verify draftProcessFinished changes`() = runTest {
        val results = mutableSetOf<Boolean>()
        val collectJob = launch(UnconfinedTestDispatcher()) {
            SUT.draftProcessFinished.toSet(results)
        }
        SUT.setDraftProcessFinished(false)
        assert(results.elementAt(0).not())
        SUT.setDraftProcessFinished(true)
        assert(results.elementAt(1))
        collectJob.cancel()
    }

    @Test
    fun `verify restoreDraftProcessFinished changes draftProcessFinished value`() = runTest {
        val results = mutableSetOf<Boolean>()
        val collectJob = launch(UnconfinedTestDispatcher()) {
            SUT.draftProcessFinished.toSet(results)
        }
        SUT.setDraftProcessFinished(true)
        assert(results.elementAt(0))
        SUT.restoreDraftProcessFinished()
        assert(results.elementAt(1).not())
        collectJob.cancel()
    }

    @Test
    fun `verify restoreInitDraftProcessing changes initDraftProcessing value`() = runTest {
        val results = mutableSetOf<Boolean>()
        coEvery {
            formDataStoreManager.setValue(
                FormDataStoreManager.CLOSE_FIRST,
                false
            )
        } just runs
        val collectJob = launch(UnconfinedTestDispatcher()) {
            SUT.initDraftProcessing.toSet(results)
        }
        SUT.setInitDraftProcessing(true)
        assert(results.elementAt(0))
        SUT.restoreInitDraftProcessing()
        assert(results.elementAt(1).not())
        collectJob.cancel()
    }

    @After
    fun after() {
        unmockkAll()
    }

}