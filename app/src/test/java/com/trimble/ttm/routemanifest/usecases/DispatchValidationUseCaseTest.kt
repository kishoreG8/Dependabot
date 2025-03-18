package com.trimble.ttm.routemanifest.usecases

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.trimble.ttm.formlibrary.utils.FormUtils
import com.trimble.ttm.routemanifest.application.WorkflowApplication
import com.trimble.ttm.commons.preferenceManager.DataStoreManager
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.unmockkObject
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class DispatchValidationUseCaseTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private lateinit var dataStoreManager: DataStoreManager
    private lateinit var context: Context
    private lateinit var SUT : DispatchValidationUseCase
    private val testScope = TestScope()

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        context = mockk()
        mockkObject(FormUtils)
        mockkObject(WorkflowApplication)
        dataStoreManager = spyk(DataStoreManager(context))
        SUT = DispatchValidationUseCase(
            dataStoreManager,
            testScope,
            UnconfinedTestDispatcher()
        )
        coEvery {
            dataStoreManager.setValue(any(), any<String>())
        } returns Unit
        coEvery {
            dataStoreManager.setValue(any(), any<Int>())
        } returns Unit
        coEvery {
            dataStoreManager.getValue(any(), any<String>())
        } returns ""
        coEvery {
            dataStoreManager.fieldObserver(
                DataStoreManager.ACTIVE_DISPATCH_KEY
            )
        } returns flow {
            emit("wer")
        }
    }

    @Test
    fun `check hasAnActiveDispatch call from dataStoreManager`() = runTest {
        coEvery {
            dataStoreManager.hasActiveDispatch(any(), any())
        } returns true
        SUT.hasAnActiveDispatch()
        coVerify {
            dataStoreManager.hasActiveDispatch(any(), any())
        }
    }

    @Test
    fun `when restoreSelected is called check if setValue from dataStoreManager is called`() = runTest {
        val id = "id"
        SUT.restoreSelected(id)
        coVerify {
            dataStoreManager.setValue(any(), any<String>())
            dataStoreManager.getValue(any(), any<String>())
        }
    }

    @Test
    fun `when updateNameOnSelected is called check if setValue from dataStoreManager is called`() = runTest {
        val name = "name"
        SUT.updateNameOnSelected(name)
        coVerify {
            dataStoreManager.setValue(any(), name)
        }
    }

    @Test
    fun `when hasOnlyOne is called returns true when dataStoreManager getValue returns 1`() = runTest {
        coEvery {
            dataStoreManager.getValue(any(), any<Int>())
        } returns 1
        val result = SUT.hasOnlyOne()
        assert(result)
        coVerify {
            dataStoreManager.getValue(any(), any<Int>())
        }
    }

    @Test
    fun `when hasOnlyOne is called returns false when dataStoreManager getValue returns diferent than 1`() = runTest {
        coEvery {
            dataStoreManager.getValue(any(), any<Int>())
        } returns 2
        val result = SUT.hasOnlyOne()
        assert(!result)
        coVerify {
            dataStoreManager.getValue(any(), any<Int>())
        }
    }

    @Test
    fun `when updateQuantity is called check if setValue from dataStoreManager is called`() = runTest {
        val quantity = 1
        SUT.updateQuantity(quantity)
        coVerify {
            dataStoreManager.setValue(any(), quantity)
        }
    }

    @After
    fun tearDown() {
        unmockkObject(FormUtils)
        unmockkObject(WorkflowApplication)
        unmockkAll()
    }


}