package com.trimble.ttm.routemanifest.usecases

import android.content.Context
import com.trimble.ttm.backbone.api.Backbone
import com.trimble.ttm.backbone.api.BackboneFactory
import com.trimble.ttm.backbone.api.Publisher
import com.trimble.ttm.backbone.api.data.ConfigurableOdometerKm
import com.trimble.ttm.backbone.api.data.CustomerId
import com.trimble.ttm.backbone.api.data.DisplayLocation
import com.trimble.ttm.backbone.api.data.EngineOdometerKm
import com.trimble.ttm.backbone.api.data.ObcId
import com.trimble.ttm.backbone.api.data.VehicleId
import com.trimble.ttm.commons.repo.BackboneRepository
import com.trimble.ttm.commons.repo.BackboneRepositoryImpl
import com.trimble.ttm.commons.usecase.BackboneUseCase
import com.trimble.ttm.commons.utils.BACKBONE_ERROR_VALUE
import com.trimble.ttm.commons.utils.EMPTY_STRING
import com.trimble.ttm.commons.utils.TestDispatcherProvider
import com.trimble.ttm.formlibrary.utils.isNull
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BackboneUseCaseTests {
    @RelaxedMockK
    private lateinit var backbone: Backbone
    private lateinit var backboneRepository: BackboneRepository
    @RelaxedMockK
    private lateinit var publisher: Publisher
    @RelaxedMockK
    private lateinit var context: Context

    private var testDispatcher = TestDispatcherProvider()

    private lateinit var backboneUseCase: BackboneUseCase

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        mockkStatic("com.trimble.ttm.backbone.api.BackboneFactory")
        every { BackboneFactory.backbone(any()) } returns backbone
        every { BackboneFactory.publisher(any()) } returns publisher
        backboneRepository = spyk(BackboneRepositoryImpl(context, testDispatcher))
        backboneUseCase = BackboneUseCase(backboneRepository)
    }

    @Test
    fun `get customer id from backbone`() = runTest {
        coEvery { backbone.retrieveDataFor(CustomerId).fetch()?.data?.value } returns "123"
        val customerId = backboneUseCase.getCustomerId()?: EMPTY_STRING
        assertEquals("123", customerId)
    }

    @Test
    fun `get vehicle id from backbone`() = runTest {
        coEvery { backbone.retrieveDataFor(VehicleId).fetch()?.data?.value } returns "123"

        assertTrue(backboneUseCase.getVehicleId() == "123")
    }

    @Test
    fun `get dsn from backbone`() = runTest {
        coEvery { backbone.retrieveDataFor(ObcId).fetch()?.data?.unitId } returns "123"

        assertTrue(backboneUseCase.getOBCId() == "123")
    }

    @Test
    fun `get customer id  returns null`() = runTest {
        coEvery { backbone.retrieveDataFor(CustomerId).fetch()?.data?.value } returns null

        assertTrue(backboneUseCase.getCustomerId().isNull())
    }

    @Test
    fun `get vehicle id returns null or empty`() = runTest {
        coEvery { backbone.retrieveDataFor(VehicleId).fetch()?.data?.value } returns null

        assertTrue(backboneUseCase.getVehicleId().isNullOrEmpty())
    }

    @Test
    fun `get dsn returns null`() = runTest {
        coEvery { backbone.retrieveDataFor(ObcId).fetch()?.data?.unitId } returns null

        assertTrue(backboneUseCase.getOBCId().isNull())
    }

    @Test
    fun `set workflow start action into backbone`() = runTest {
        coEvery { publisher.publish(any<Publisher.Update>()) } just runs

        backboneUseCase.setWorkflowStartAction(213234)

        coVerify(exactly = 1) {
            backboneRepository.setWorkflowStartAction(any())
        }
    }

    @Test
    fun `set workflow end action into backbone`() = runTest {
        coEvery { publisher.publish(any<Publisher.Update>()) } just runs

        backboneUseCase.setWorkflowEndAction(213234)

        coVerify(exactly = 1) {
            backboneRepository.setWorkflowEndAction(any())
        }
    }

    @Test
    fun `verify getCurrentLocation from backbone returns expected location`() = runTest {
        val lat = 80.25
        val long = 12.98
        val displayLocation = mockk<DisplayLocation> {
            every { latitude } returns lat
            every { longitude } returns long
        }
        every { backbone.retrieveDataFor(DisplayLocation).fetch()?.data } returns displayLocation
        val result = backboneUseCase.getCurrentLocation()
        coVerify { backboneRepository.getCurrentLocation()  } 
        assertEquals(result, Pair(80.25, 12.98))
    }

    @Test
    fun verifyConfigurableOdometerFetchedFromBackbone() = runTest {
        every {
            backbone.retrieveDataFor(ConfigurableOdometerKm).fetch()?.data?.value
        } returns 20000.0
        assertTrue { backboneUseCase.getOdometerReading(true) == 20000.0 }

        verify { backbone.retrieveDataFor(ConfigurableOdometerKm).fetch()?.data?.value }
    }

    @Test
    fun verifyEngineOdometerFetchedFromBackbone() = runTest {
        every {
            backbone.retrieveDataFor(EngineOdometerKm).fetch()?.data?.value
        } returns 10000.0
        assertTrue { backboneUseCase.getOdometerReading(false) == 10000.0 }
        verify { backbone.retrieveDataFor(EngineOdometerKm).fetch()?.data?.value }
    }

    @Test
    fun verifyConfigurableOdometerFetchedFromBackboneWasNull() = runTest {
        every {
            backbone.retrieveDataFor(ConfigurableOdometerKm).fetch()?.data
        } returns null
        assertTrue { backboneUseCase.getOdometerReading(true) == BACKBONE_ERROR_VALUE }
    }

    @Test
    fun verifyEngineOdometerFetchedFromBackboneWasNull() = runTest {
        every {
            backbone.retrieveDataFor(EngineOdometerKm).fetch()?.data
        } returns null

        assertTrue { backboneUseCase.getOdometerReading(false) == BACKBONE_ERROR_VALUE }
    }

    @Test
    fun `monitorTrailersData returns expected data`() = runTest {
        val expectedData = listOf("Trailer1", "Trailer2")
        coEvery { backboneRepository.monitorTrailersData() } returns flowOf(expectedData)

        val result = backboneUseCase.monitorTrailersData().first()
        assertEquals(expectedData, result)
    }

    @Test
    fun `monitorTrailersData returns empty list`() = runTest {
        val expectedData = emptyList<String>()
        coEvery { backboneRepository.monitorTrailersData() } returns flowOf(expectedData)

        val result = backboneUseCase.monitorTrailersData().first()
        assertEquals(expectedData, result)
    }

    @Test
    fun `monitorShipmentsData returns expected data`() = runTest {
        val expectedData = listOf("Shipment1", "Shipment2")
        coEvery { backboneRepository.monitorShipmentsData() } returns flowOf(expectedData)

        val result = backboneUseCase.monitorShipmentsData().first()
        assertEquals(expectedData, result)
    }

    @Test
    fun `monitorShipmentsData returns empty list`() = runTest {
        val expectedData = emptyList<String>()
        coEvery { backboneRepository.monitorShipmentsData() } returns flowOf(expectedData)

        val result = backboneUseCase.monitorShipmentsData().first()
        assertEquals(expectedData, result)
    }

    @After
    fun clear() {
        unmockkAll()
    }
}