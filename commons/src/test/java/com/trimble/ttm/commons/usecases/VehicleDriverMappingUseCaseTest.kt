package com.trimble.ttm.commons.usecases

import com.trimble.ttm.commons.repo.VehicleDriverMappingRepo
import com.trimble.ttm.commons.usecase.BackboneUseCase
import com.trimble.ttm.commons.usecase.VehicleDriverMappingUseCase
import com.trimble.ttm.commons.utils.EMPTY_STRING
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class VehicleDriverMappingUseCaseTest {

    private lateinit var backboneUseCase: BackboneUseCase
    private lateinit var vehicleDriverMappingRepo: VehicleDriverMappingRepo
    private lateinit var vehicleDriverMappingUseCase: VehicleDriverMappingUseCase

    @Before
    fun setUp() {
        backboneUseCase = mockk()
        vehicleDriverMappingRepo = mockk()
        vehicleDriverMappingUseCase = VehicleDriverMappingUseCase(backboneUseCase, vehicleDriverMappingRepo)
    }

    @Test
    fun `test updateVehicleDriverMapping with valid fields`() = runTest {
        // Arrange
        val vehicleId = "vehicle123"
        val currentUser = "user123"
        val ttcAccountId = "account123"
        val ttcIdForCurrentUser = "ttcId123"

        coEvery { backboneUseCase.getVehicleId() } returns vehicleId
        coEvery { backboneUseCase.getCurrentUser() } returns currentUser
        coEvery { backboneUseCase.getTTCAccountId() } returns ttcAccountId
        coEvery { backboneUseCase.getTTCIdForCurrentUser(currentUser) } returns ttcIdForCurrentUser
        coEvery { vehicleDriverMappingRepo.updateVehicleDriverMap(vehicleId, ttcAccountId, ttcIdForCurrentUser, currentUser) } returns Unit

        // Act
        vehicleDriverMappingUseCase.updateVehicleDriverMapping()

        // Assert
        coVerify { vehicleDriverMappingRepo.updateVehicleDriverMap(vehicleId, ttcAccountId, ttcIdForCurrentUser, currentUser) }
    }

    @Test
    fun `test updateVehicleDriverMapping with invalid fields`() = runTest {
        // Arrange
        val vehicleId = EMPTY_STRING
        val currentUser = "user123"
        val ttcAccountId = "account123"
        val ttcIdForCurrentUser = "ttcId123"

        coEvery { backboneUseCase.getVehicleId() } returns vehicleId
        coEvery { backboneUseCase.getCurrentUser() } returns currentUser
        coEvery { backboneUseCase.getTTCAccountId() } returns ttcAccountId
        coEvery { backboneUseCase.getTTCIdForCurrentUser(currentUser) } returns ttcIdForCurrentUser

        // Act
        vehicleDriverMappingUseCase.updateVehicleDriverMapping()

        // Assert
        coVerify(exactly = 0) { vehicleDriverMappingRepo.updateVehicleDriverMap(any(), any(), any(), any()) }
    }
}
