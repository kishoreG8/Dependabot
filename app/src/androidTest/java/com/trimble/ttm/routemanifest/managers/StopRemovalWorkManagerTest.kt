package com.trimble.ttm.routemanifest.managers

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.trimble.ttm.routemanifest.DISPATCH_ID
import com.trimble.ttm.routemanifest.managers.workmanager.StopRemovalNotificationWorker
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import kotlin.test.assertEquals

@RunWith(JUnit4::class)
class StopRemovalWorkManagerTest {
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun testMyWork() {
        // Get the ListenableWorker
        val worker =
            TestListenableWorkerBuilder<StopRemovalNotificationWorker>(context).setInputData(
                workDataOf(DISPATCH_ID to "1234")
            ).build()
        // Run the worker synchronously
        val result = worker.startWork().get()

        assertEquals(ListenableWorker.Result.success(),result)
    }


}