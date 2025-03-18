package com.trimble.ttm.commons.utils

import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.utils.ext.safeLaunch
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.verify
import junit.framework.Assert.assertEquals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertTrue

class CoroutineExtTest {

    val exception = Exception("Some error")

    @Before
    fun setUp() {
        mockkObject(Log)
        every {
            Log.e(any(),any(),any())
        } returns Unit
        every {
            Log.i(any(),any(),any())
        } returns Unit
        every {
            Log.v(any(), any())
        } returns Unit
        every {
            Log.d(any(), any())
        } returns Unit
    }

    @Test
    fun `check no log interaction When no crash occur on getExceptionHandler`() = runTest(Job()+UnconfinedTestDispatcher()) {
        safeLaunch {
            launch {
                Log.i("someTag","someMsg")
            }
        }
        verify(timeout = TEST_DELAY_OR_TIMEOUT, exactly = 0) {
            Log.e(any(),any(),any())
        }
    }

    @Test
    fun `check parallel coroutines exception propagation`() =
        runTest(Job() + UnconfinedTestDispatcher()) {
            var count = 0
            safeLaunch {
                delay(1000L)
                throw NullPointerException()
            }
            safeLaunch {
                delay(500L)
                count++
            }
            safeLaunch {
                delay(1500L)
                count++
            }
            delay(2000L)
            assertEquals(2, count)
        }

    @Test
    fun `check parent child coroutine creation and avoid exception propagation`() = runTest(Job()+UnconfinedTestDispatcher()) {
        val scope = CoroutineScope(coroutineContext)
        scope.safeLaunch {
            safeLaunch {
                delay(1000L)
                val algo = 0
                throw Exception("Failed coroutine")
            }
            delay(2000L)
            assert(true)
        }
    }

    @Test
    fun `check safelaunch does cancel the job, but logs as a warning`() = runTest(Job()+UnconfinedTestDispatcher()) {
        val scope = CoroutineScope(coroutineContext)
        val job = scope.safeLaunch {
            launch {
                Log.i("someTag","someMsg")
            }
            delay(500L)
        }
        delay(100L)
        job.cancel()
        verify(timeout = TEST_DELAY_OR_TIMEOUT, exactly = 1) {
            Log.i(any(),any(),any())
        }
        verify(timeout = TEST_DELAY_OR_TIMEOUT, exactly = 0) {
            Log.e(any(),any(),any())
        }
        assertTrue(job.isCancelled, "Job was canceled")
    }
}
