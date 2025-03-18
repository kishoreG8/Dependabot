package com.trimble.ttm.formlibrary.manager

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.preferenceManager.FormDataStoreManager
import com.trimble.ttm.formlibrary.utils.TEST_DELAY_OR_TIMEOUT
import io.mockk.*
import io.mockk.impl.annotations.RelaxedMockK
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class FormDataStoreManagerTest {

    @RelaxedMockK
    private lateinit var formDataStoreManager: FormDataStoreManager
    private val key = longPreferencesKey(name = FormDataStoreManager.IS_IN_FORM_KEY.name)
    private lateinit var log: Log
    private val tag = "FormDataStoreManager"
    private lateinit var preferences : Preferences

    //private val dispatcher = UnconfinedTestDispatcher()
    private val testDispatcher = TestCoroutineScheduler()
    //private val testScope = TestScope()

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        formDataStoreManager = spyk(FormDataStoreManager(mockk(relaxed = true)))
        log = spyk(Log)
        preferences = emptyPreferences()
    }

    private fun <T> throwException(): T {
        log.d(tag, "exception")
        throw Exception()
    }

    @Test
    fun `run getValue without error`() = runTest(testDispatcher) {
        coEvery { formDataStoreManager.getValue(key, 0L) } returns 1

        formDataStoreManager.getValue(key, 0L)

        coVerify(exactly = 1, timeout = TEST_DELAY_OR_TIMEOUT) {
            formDataStoreManager.getValue(key, 0L)
        }
    }

    @Test
    fun `run getValue with error`() = runTest(testDispatcher) {
        coEvery { formDataStoreManager.getValue(key, 0L) } coAnswers { throwException() }

        try {
            formDataStoreManager.getValue(key, 0L)
        } catch (_: Exception) {

        }

        coVerify(exactly = 1, timeout = TEST_DELAY_OR_TIMEOUT) {
            formDataStoreManager.getValue(key, 0L)
            log.d(tag, "exception")
        }
    }

    @Test
    fun `run containsKey without error`() = runTest(testDispatcher) {
        coEvery { formDataStoreManager.containsKey(key) } returns true

        formDataStoreManager.containsKey(key)

        coVerify(exactly = 1, timeout = TEST_DELAY_OR_TIMEOUT) {
            formDataStoreManager.containsKey(key)
        }
    }

    @Test
    fun `run containsKey with error`() = runTest(testDispatcher) {
        coEvery { formDataStoreManager.containsKey(key) } coAnswers { throwException() }

        try {
            formDataStoreManager.containsKey(key)
        } catch (_: Exception) {

        }

        coVerify(exactly = 1, timeout = TEST_DELAY_OR_TIMEOUT) {
            formDataStoreManager.containsKey(key)
            log.d(tag, "exception")
        }
    }

    @Test
    fun `run removeItem without error`() = runTest(testDispatcher) {
        coEvery { formDataStoreManager.removeItem(key) } returns preferences

        formDataStoreManager.removeItem(key)

        coVerify(exactly = 1, timeout = TEST_DELAY_OR_TIMEOUT) {
            formDataStoreManager.removeItem(key)
        }
    }

    @Test
    fun `run removeItem with error`() = runTest(testDispatcher) {
        coEvery { formDataStoreManager.removeItem(key) } coAnswers { throwException() }

        try {
            formDataStoreManager.removeItem(key)
        } catch (_: Exception) {

        }

        coVerify(exactly = 1, timeout = TEST_DELAY_OR_TIMEOUT) {
            formDataStoreManager.removeItem(key)
            log.d(tag, "exception")
        }
    }

    @Test
    fun `run setValue without error`() = runTest(testDispatcher) {
        coEvery { formDataStoreManager.setValue(key, 1) } returns Unit

        formDataStoreManager.setValue(key, 1)

        coVerify(exactly = 1, timeout = TEST_DELAY_OR_TIMEOUT) {
            formDataStoreManager.setValue(key, 1)
        }
    }

    @Test
    fun `run setValue with error`() = runTest(testDispatcher) {
        coEvery { formDataStoreManager.setValue(key, 1) } coAnswers { throwException() }

        try {
            formDataStoreManager.setValue(key, 1)
        } catch (_: Exception) {

        }

        coVerify(exactly = 1, timeout = TEST_DELAY_OR_TIMEOUT) {
            formDataStoreManager.setValue(key, 1)
            log.d(tag, "exception")
        }
    }

    @Test
    fun `run getPreferenceKeys without error`() = runTest(testDispatcher) {
        val keys: Set<Preferences.Key<*>> = setOf()
        coEvery { formDataStoreManager.getPreferenceKeys() } returns keys

        formDataStoreManager.getPreferenceKeys()

        coVerify(exactly = 1, timeout = TEST_DELAY_OR_TIMEOUT) {
            formDataStoreManager.getPreferenceKeys()
        }
    }

    @Test
    fun `run getPreferenceKeys with error`() = runTest(testDispatcher) {
        coEvery { formDataStoreManager.getPreferenceKeys() } coAnswers { throwException() }

        try {
            formDataStoreManager.getPreferenceKeys()
        } catch (_: Exception) {

        }

        coVerify(exactly = 1, timeout = TEST_DELAY_OR_TIMEOUT) {
            formDataStoreManager.getPreferenceKeys()
            log.d(tag, "exception")
        }
    }

    @Test
    fun `run fieldObserver without error`() = runTest(testDispatcher) {
        coEvery { formDataStoreManager.fieldObserver(key) } returns flow { emit(1)}

        formDataStoreManager.fieldObserver(key)

        coVerify(exactly = 1, timeout = TEST_DELAY_OR_TIMEOUT) {
            formDataStoreManager.fieldObserver(key)
        }
    }

    @Test
    fun `run fieldObserver with error`() = runTest(testDispatcher) {
        coEvery { formDataStoreManager.fieldObserver(key) } coAnswers { throwException() }

        try {
            formDataStoreManager.fieldObserver(key)
        } catch (_: Exception) {

        }

        coVerify(exactly = 1, timeout = TEST_DELAY_OR_TIMEOUT) {
            formDataStoreManager.fieldObserver(key)
            log.d(tag, "exception")
        }
    }

}