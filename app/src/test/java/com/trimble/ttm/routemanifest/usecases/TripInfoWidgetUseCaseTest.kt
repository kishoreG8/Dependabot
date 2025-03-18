package com.trimble.ttm.routemanifest.usecases

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import com.trimble.ttm.commons.utils.TestDispatcherProvider
import com.trimble.ttm.commons.preferenceManager.DataStoreManager
import com.trimble.ttm.routemanifest.model.RouteCalculationResult
import com.trimble.ttm.routemanifest.model.StopDetail
import com.trimble.ttm.commons.repo.LocalDataSourceRepo
import com.trimble.ttm.routemanifest.utils.TEST_DELAY_OR_TIMEOUT
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.runs
import io.mockk.unmockkAll
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.Calendar

class TripInfoWidgetUseCaseTest {

    private lateinit var tripInfoWidgetUseCase: TripInfoWidgetUseCase
    private val testScope = TestScope()
    @MockK
    private lateinit var context: Context
    @MockK
    private lateinit var localDataSourceRepo: LocalDataSourceRepo
    @MockK
    private lateinit var sendBroadCastUseCase: SendBroadCastUseCase

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        tripInfoWidgetUseCase = TripInfoWidgetUseCase(
            context = context,
            localDataSourceRepo = localDataSourceRepo,
            sendBroadCastUseCase = sendBroadCastUseCase,
            coroutineScope = testScope,
            coroutineDispatcherProvider = TestDispatcherProvider()
        )
    }

    @Test
    fun `check if trip widget reset, updates the datastore and broadcasts the information`() = runTest {
        coEvery { localDataSourceRepo.setToAppModuleDataStore(any<Preferences.Key<Any>>(), any()) } just runs
        tripInfoWidgetUseCase.resetTripInfoWidget(TEST)
        coVerify(exactly = 1, timeout = TEST_DELAY_OR_TIMEOUT) {
            localDataSourceRepo.setToAppModuleDataStore(DataStoreManager.TOTAL_DISTANCE_KEY, 0F)
            localDataSourceRepo.setToAppModuleDataStore(DataStoreManager.TOTAL_HOURS_KEY, 0F)
            localDataSourceRepo.setToAppModuleDataStore(DataStoreManager.TOTAL_STOPS_KEY, 0)
            sendBroadCastUseCase.sendBroadCast(any(), any())
        }
    }

    @Test
    fun `check if trip widget update, updates the datastore and broadcasts the information`() = runTest {
        val distance = 5.0F
        val hours = 10.0F
        val stopCount = 2
        val stopDetailList = listOf(StopDetail(stopid = 0).apply { etaTime = Calendar.getInstance().time })
        coEvery { localDataSourceRepo.setToAppModuleDataStore(any<Preferences.Key<Any>>(), any()) } just runs
        tripInfoWidgetUseCase.updateTripInfoWidget(distance, hours, stopCount, TEST, RouteCalculationResult(stopDetailList = stopDetailList), stopDetailList)
        coVerify(exactly = 1, timeout = TEST_DELAY_OR_TIMEOUT) {
            localDataSourceRepo.setToAppModuleDataStore(DataStoreManager.TOTAL_DISTANCE_KEY, distance)
            localDataSourceRepo.setToAppModuleDataStore(DataStoreManager.TOTAL_HOURS_KEY, hours)
            localDataSourceRepo.setToAppModuleDataStore(DataStoreManager.TOTAL_STOPS_KEY, stopCount)
            sendBroadCastUseCase.sendBroadCast(any(), any())
        }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

}