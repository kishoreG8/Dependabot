package com.trimble.ttm.routemanifest.usecases

import android.os.Bundle
import com.trimble.ttm.commons.preferenceManager.DataStoreManager
import com.trimble.ttm.routemanifest.repo.TripPanelEventRepo
import com.trimble.ttm.routemanifest.utils.TEST_DELAY_OR_TIMEOUT
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

class DockModeUseCaseTests {

    @RelaxedMockK
    private lateinit var dataStoreManager: DataStoreManager

    @RelaxedMockK
    private lateinit var tripPanelEventRepo: TripPanelEventRepo

    private lateinit var dockModeUseCase: DockModeUseCase

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        dataStoreManager = spyk(DataStoreManager(mockk(relaxed = true)))
        dockModeUseCase = DockModeUseCase(tripPanelEventRepo, dataStoreManager)
    }

    @Test
    fun `set dock mode called once`() {
        every { tripPanelEventRepo.setDockMode(any(), any()) } just runs

        dockModeUseCase.setDockMode(Bundle(), "", "")

        verify(exactly = 1) {
            tripPanelEventRepo.setDockMode(any(), any())
        }
    }

    @Test
    fun `reset dock mode called once`() = runTest {
        every { tripPanelEventRepo.resetDockMode(any(), any()) } just runs
        coEvery { dataStoreManager.getValue(DataStoreManager.DOCK_MODE_ACK_ID_KEY, -1) } returns 11

        dockModeUseCase.resetDockMode()

        verify(exactly = 1, timeout = TEST_DELAY_OR_TIMEOUT) {
            tripPanelEventRepo.resetDockMode(any(), any())
        }
    }

    @Test
    fun `reset dock mode called when DOCK_MODE_ACK_ID_KEY is less than or equal to 0`() = runTest {
        every { tripPanelEventRepo.resetDockMode(any(), any()) } just runs
        coEvery { dataStoreManager.getValue(DataStoreManager.DOCK_MODE_ACK_ID_KEY, -1) } returns -1

        dockModeUseCase.resetDockMode()

        verify(exactly = 1, timeout = TEST_DELAY_OR_TIMEOUT) {
            tripPanelEventRepo.resetDockMode(any(), any())
        }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

}