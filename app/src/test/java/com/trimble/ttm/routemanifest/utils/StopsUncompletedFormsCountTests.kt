package com.trimble.ttm.routemanifest.utils

import android.content.Context
import com.trimble.ttm.commons.model.DispatchFormPath
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.utils.DefaultDispatcherProvider
import com.trimble.ttm.routemanifest.application.WorkflowApplication
import com.trimble.ttm.routemanifest.repo.DispatchFirestoreRepo
import com.trimble.ttm.routemanifest.usecases.DispatchStopsUseCase
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.mockk.spyk
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.koin.core.component.KoinComponent

const val ONE_STOP_MESSAGE = "Complete form for %s"
const val MULTIPLE_STOPS_MESSAGE = "Complete forms for %s arrived stops"

class StopsUncompletedFormsCountTests:KoinComponent {
    private lateinit var context: Context

    @RelaxedMockK
    private lateinit var application: WorkflowApplication
    private lateinit var dispatchStopsUseCase: DispatchStopsUseCase
    @MockK
    private lateinit var dispatchFirestoreRepo: DispatchFirestoreRepo
    @MockK
    private lateinit var appModuleCommunicator: AppModuleCommunicator

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        ApplicationContextProvider.init(application)
        context = mockk()
        every { dispatchFirestoreRepo.getAppModuleCommunicator() } returns appModuleCommunicator
        dispatchStopsUseCase= spyk(
            DispatchStopsUseCase(
                mockk(),
                dispatchFirestoreRepo,
                DefaultDispatcherProvider(),
                mockk(),
                mockk(),
                mockk(),
                mockk(),
                mockk(),
                mockk(),
                mockk(),
                mockk(),
                mockk()
        ))

    }

    @Test
    fun `validate uncompleted form message is correct when only one form is pending`() {    //NOSONAR

        val formStack = arrayListOf<DispatchFormPath>()
        formStack.add(DispatchFormPath("Stop1", 0, 6789352, 8675))

        Assert.assertEquals(
            String.format(ONE_STOP_MESSAGE, "Stop1"),
            dispatchStopsUseCase.getUncompletedFormsMessage(
                formStack,
                ONE_STOP_MESSAGE,
                MULTIPLE_STOPS_MESSAGE
            )
        )
    }

    @Test
    fun `validate uncompleted form message is correct when two forms are pending for different stops`() {    //NOSONAR

        val formStack = arrayListOf<DispatchFormPath>()
        formStack.add(DispatchFormPath("Stop1", 0, 6789352, 8675))
        formStack.add(DispatchFormPath("Stop2", 1, 6789352, 8675))

        Assert.assertEquals(
            String.format(MULTIPLE_STOPS_MESSAGE, 2),
            dispatchStopsUseCase.getUncompletedFormsMessage(
                formStack,
                MULTIPLE_STOPS_MESSAGE,
                MULTIPLE_STOPS_MESSAGE
            )
        )
    }

    @Test
    fun `validate uncompleted form message is correct when two forms are pending for same stop`() {    //NOSONAR

        val formStack =arrayListOf<DispatchFormPath>()
        formStack.add(DispatchFormPath("Stop1", 0, 6789352, 8675))
        formStack.add(DispatchFormPath("Stop1", 0, 6789352, 8675))

        Assert.assertEquals(
            String.format(ONE_STOP_MESSAGE, "Stop1"),
            dispatchStopsUseCase.getUncompletedFormsMessage(
                formStack,
                ONE_STOP_MESSAGE,
                MULTIPLE_STOPS_MESSAGE
            )
        )
    }

    @Test
    fun `validate empty message is returned if formstack has no items`() {    //NOSONAR
        Assert.assertEquals(
            "",
            dispatchStopsUseCase.getUncompletedFormsMessage(
                arrayListOf(),
                ONE_STOP_MESSAGE,
                MULTIPLE_STOPS_MESSAGE
            )
        )
    }
}