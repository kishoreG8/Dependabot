package com.trimble.ttm.routemanifest.manager

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.preferenceManager.DataStoreManager
import com.trimble.ttm.routemanifest.utils.TEST_DELAY_OR_TIMEOUT
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class DataStoreManagerTest {

    @RelaxedMockK
    private lateinit var dataStoreManager: DataStoreManager
    private val key = longPreferencesKey(name = DataStoreManager.VID_KEY.name)
    private lateinit var log: Log
    private val tag = "DataStoreManager"
    private lateinit var preferences : Preferences

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        dataStoreManager = spyk(DataStoreManager(mockk(relaxed = true)))
        log = spyk(Log)
        preferences = emptyPreferences()
    }

    private fun <T> throwException(): T {
        log.d(tag, "exception")
        throw Exception()
    }

    @Test
    fun `run getValue without error`() = runTest {
        coEvery { dataStoreManager.getValue(key, 0L) } returns 1

        dataStoreManager.getValue(key, 0L)

        coVerify(exactly = 1, timeout = TEST_DELAY_OR_TIMEOUT) {
            dataStoreManager.getValue(key, 0L)
        }
    }

    @Test
    fun `run getValue with error`() = runTest {
        coEvery { dataStoreManager.getValue(key, 0L) } coAnswers { throwException() }

        try {
            dataStoreManager.getValue(key, 0L)
        } catch (_: Exception) {

        }

        coVerify(exactly = 1, timeout = TEST_DELAY_OR_TIMEOUT) {
            dataStoreManager.getValue(key, 0L)
            log.d(tag, "exception")
        }
    }

    @Test
    fun `run containsKey without error`() = runTest {
        coEvery { dataStoreManager.containsKey(key) } returns true

        dataStoreManager.containsKey(key)

        coVerify(exactly = 1, timeout = TEST_DELAY_OR_TIMEOUT) {
            dataStoreManager.containsKey(key)
        }
    }

    @Test
    fun `run containsKey with error`() = runTest {
        coEvery { dataStoreManager.containsKey(key) } coAnswers { throwException() }

        try {
            dataStoreManager.containsKey(key)
        } catch (_: Exception) {

        }

        coVerify(exactly = 1, timeout = TEST_DELAY_OR_TIMEOUT) {
            dataStoreManager.containsKey(key)
            log.d(tag, "exception")
        }
    }

    @Test
    fun `run removeItem without error`() = runTest {
        coEvery { dataStoreManager.removeItem(key) } returns preferences

        dataStoreManager.removeItem(key)

        coVerify(exactly = 1, timeout = TEST_DELAY_OR_TIMEOUT) {
            dataStoreManager.removeItem(key)
        }
    }

    @Test
    fun `run removeItem with error`() = runTest {
        coEvery { dataStoreManager.removeItem(key) } coAnswers { throwException() }

        try {
            dataStoreManager.removeItem(key)
        } catch (_: Exception) {

        }

        coVerify(exactly = 1, timeout = TEST_DELAY_OR_TIMEOUT) {
            dataStoreManager.removeItem(key)
            log.d(tag, "exception")
        }
    }

    @Test
    fun `run setValue without error`() = runTest {
        coEvery { dataStoreManager.setValue(key, 1) } returns Unit

        dataStoreManager.setValue(key, 1)

        coVerify(exactly = 1, timeout = TEST_DELAY_OR_TIMEOUT) {
            dataStoreManager.setValue(key, 1)
        }
    }

    @Test
    fun `run setValue with error`() = runTest {
        coEvery { dataStoreManager.setValue(key, 1) } coAnswers { throwException() }

        try {
            dataStoreManager.setValue(key, 1)
        } catch (_: Exception) {

        }

        coVerify(exactly = 1, timeout = TEST_DELAY_OR_TIMEOUT) {
            dataStoreManager.setValue(key, 1)
            log.d(tag, "exception")
        }
    }

    @Test
    fun `run removeAllKeys without error`() = runTest {
        coEvery { dataStoreManager.removeAllKeys() } returns Unit

        dataStoreManager.removeAllKeys()

        coVerify(exactly = 1, timeout = TEST_DELAY_OR_TIMEOUT) {
            dataStoreManager.removeAllKeys()
        }
    }

    @Test
    fun `run removeAllKeys with error`() = runTest {
        coEvery { dataStoreManager.removeAllKeys() } coAnswers { throwException() }

        try {
            dataStoreManager.removeAllKeys()
        } catch (_: Exception) {

        }

        coVerify(exactly = 1, timeout = TEST_DELAY_OR_TIMEOUT) {
            dataStoreManager.removeAllKeys()
            log.d(tag, "exception")
        }
    }

    @Test
    fun `run fieldObserver without error`() = runTest {
        coEvery { dataStoreManager.fieldObserver(key) } returns flow { emit(1)}

        dataStoreManager.fieldObserver(key)

        coVerify(exactly = 1, timeout = TEST_DELAY_OR_TIMEOUT) {
            dataStoreManager.fieldObserver(key)
        }
    }

    @Test
    fun `run fieldObserver with error`() = runTest {
        coEvery { dataStoreManager.fieldObserver(key) } coAnswers { throwException() }

        try {
            dataStoreManager.fieldObserver(key)
        } catch (_: Exception) {

        }

        coVerify(exactly = 1, timeout = TEST_DELAY_OR_TIMEOUT) {
            dataStoreManager.fieldObserver(key)
            log.d(tag, "exception")
        }
    }

}