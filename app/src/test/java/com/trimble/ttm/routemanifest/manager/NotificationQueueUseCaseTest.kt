package com.trimble.ttm.routemanifest.manager

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.datastore.preferences.core.stringPreferencesKey
import com.trimble.ttm.commons.preferenceManager.DataStoreManager
import com.trimble.ttm.commons.preferenceManager.DataStoreManager.Companion.NOTIFICATION_LIST
import com.trimble.ttm.routemanifest.model.FcmData
import com.trimble.ttm.routemanifest.usecases.NotificationQueueUseCase
import com.trimble.ttm.routemanifest.utils.Utils
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.test.KoinTest

class NotificationQueueUseCaseTest: KoinTest {
    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private lateinit var SUT: NotificationQueueUseCase

    private lateinit var dataStoreManager: DataStoreManager

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        dataStoreManager = mockk(relaxed = true)
        SUT= NotificationQueueUseCase(dataStoreManager)

        coEvery {
            dataStoreManager.setValue(
                NOTIFICATION_LIST,
                any()
            )
        } returns Unit
    }

    @Test
    fun `getEnqueuedNotificationsList fetches and removes existing notifications`() = runTest {
        // Arrange
        val notificationType = stringPreferencesKey("notificationType")
        val existingFcmDataList = listOf(FcmData(), FcmData())
        val existingFcmDataListJson = Utils.toPrettyJsonString(existingFcmDataList)

        coEvery { dataStoreManager.getValue(notificationType, "") } returns existingFcmDataListJson

        // Act
        val result = SUT.getEnqueuedNotificationsList(notificationType)

        // Assert
        assert(result == existingFcmDataList)
        coVerify { dataStoreManager.removeItem(notificationType) }
    }

    @Test
    fun `enqueueNotifications saves new notification when there are no existing notifications`() = runTest {
        // Arrange
        val fcmData = FcmData()
        val notificationType = stringPreferencesKey("notificationType")

        coEvery { dataStoreManager.getValue(notificationType, "") } returns ""

        // Act
        SUT.enqueueNotifications(fcmData, notificationType)

        // Assert
        val newFcmDataList = listOf(fcmData)
        val newFcmDataListJson = Utils.toPrettyJsonString(newFcmDataList)
        coVerify { dataStoreManager.setValue(notificationType, newFcmDataListJson) }
    }

    @Test
    fun `enqueueNotifications adds new notification to existing list`() = runTest {
        // Arrange
        val fcmData = FcmData()
        val notificationType = stringPreferencesKey("notificationType")
        val existingFcmDataList = listOf(FcmData(), FcmData())
        val existingFcmDataListJson = Utils.toPrettyJsonString(existingFcmDataList)

        coEvery { dataStoreManager.getValue(notificationType, "") } returns existingFcmDataListJson

        // Act
        SUT.enqueueNotifications(fcmData, notificationType)

        // Assert
        val updatedFcmDataList = existingFcmDataList + fcmData
        val updatedFcmDataListJson = Utils.toPrettyJsonString(updatedFcmDataList)
        coVerify { dataStoreManager.setValue(notificationType, updatedFcmDataListJson) }
    }
}