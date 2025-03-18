package com.trimble.ttm.routemanifest.utils

import android.content.Context
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.utils.DefaultDispatcherProvider
import com.trimble.ttm.routemanifest.application.WorkflowApplication
import com.trimble.ttm.routemanifest.model.StopDetail
import com.trimble.ttm.routemanifest.repo.DispatchFirestoreRepo
import com.trimble.ttm.routemanifest.usecases.DispatchStopsUseCase
import com.trimble.ttm.routemanifest.utils.ext.isEqualTo
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.mockk.spyk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.koin.core.component.KoinComponent

class StopNavigationEligibilityTest : KoinComponent {
    private lateinit var context: Context

    @RelaxedMockK
    private lateinit var application: WorkflowApplication
    private lateinit var dispatchStopsUseCase: DispatchStopsUseCase
    @MockK
    private lateinit var dispatchFirestoreRepo: DispatchFirestoreRepo
    @MockK
    private lateinit var appModuleCommunicator: AppModuleCommunicator

    private val at20200210T072617Z = "2020-02-10T07:26:17Z"

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        ApplicationContextProvider.init(application)
        context = mockk()
        every { dispatchFirestoreRepo.getAppModuleCommunicator() } returns appModuleCommunicator
        dispatchStopsUseCase = spyk(
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
            )
        )
    }

    @Test
    fun `stop list with all free floating stops should have next eligible stop ids size as 0`() {
        assertEquals(
            0,
            dispatchStopsUseCase.setSequencedStopsEligibility(
                0,
                listOf(
                    StopDetail(
                        stopid = 0,
                        sequenced = 0,
                    ), StopDetail(
                        stopid = 1,
                        sequenced = 0,
                    ), StopDetail(
                        stopid = 2,
                        sequenced = 0,
                    )
                ),
                mutableSetOf()
            ).size
        )
    }

    @Test
    fun `stop list with all completed stops should have next eligible stop ids size as 0 even though they contains sequenced stops`() {
        assertEquals(
            0,
            dispatchStopsUseCase.setSequencedStopsEligibility(
                4,
                listOf(
                    StopDetail(
                        stopid = 0,
                        sequenced = 1,
                        completedTime = at20200210T072617Z
                    ), StopDetail(
                        stopid = 1,
                        sequenced = 1,
                        completedTime = at20200210T072617Z
                    )
                ),
                mutableSetOf()
            ).size
        )
    }

    @Test
    fun `stoplist with 1 stop and arriving on it should have eligible stop ids size 0`() {    //NOSONAR
        assertEquals(
            0,
            dispatchStopsUseCase.setSequencedStopsEligibility(
                0,
                listOf(
                    StopDetail(
                        stopid = 0,
                        sequenced = 1,
                        completedTime = at20200210T072617Z
                    )
                ),
                mutableSetOf()
            ).size
        )
    }

    @Test
    fun `2 stops in trip and arriving on 1st stop should have eligible stop ids size 0 when 2nd stop already arrived`() {    //NOSONAR
        assertEquals(
            0,
            dispatchStopsUseCase.setSequencedStopsEligibility(
                0,
                listOf(
                    StopDetail(
                        stopid = 0,
                        sequenced = 1,
                        completedTime = at20200210T072617Z
                    ),
                    StopDetail(
                        stopid = 1,
                        sequenced = 1,
                        completedTime = at20200210T072617Z
                    )
                ),
                mutableSetOf()
            ).size
        )
    }

    @Test
    fun `fifth stop is free float and arrive on 3rd stop manually will set stops 1 and 4 eligible for navigation`() {    //NOSONAR
        val eligibleList = dispatchStopsUseCase.setSequencedStopsEligibility(
            2,
            listOf(
                StopDetail(
                    stopid = 0,
                    sequenced = 1
                ),
                StopDetail(
                    stopid = 1,
                    sequenced = 1
                ),
                StopDetail(
                    stopid = 2,
                    sequenced = 1,
                    completedTime = at20200210T072617Z
                ),
                StopDetail(
                    stopid = 3,
                    sequenced = 1
                ),
                StopDetail(
                    stopid = 4,
                    sequenced = 0
                )
            ),
            mutableSetOf<String>().also {
                it.add("0")
            }
        )
        assertTrue(
            eligibleList.contains("0") and eligibleList.contains("3") and eligibleList.size.isEqualTo(
                2
            )
        )
    }

    @Test
    fun `fifth stop is free float and first stop is finished and arrive on 3rd stop will set stop 2 and 4 eligible for navigation`() {    //NOSONAR
        val eligibleList = dispatchStopsUseCase.setSequencedStopsEligibility(
            2,
            listOf(
                StopDetail(
                    stopid = 0,
                    sequenced = 1,
                    completedTime = at20200210T072617Z
                ),
                StopDetail(
                    stopid = 1,
                    sequenced = 1
                ),
                StopDetail(
                    stopid = 2,
                    sequenced = 1,
                    completedTime = at20200210T072617Z
                ),
                StopDetail(
                    stopid = 3,
                    sequenced = 1
                ),
                StopDetail(
                    stopid = 4,
                    sequenced = 0
                )
            ),
            mutableSetOf<String>().also {
                it.add("1")
            }
        )
        assertTrue(
            eligibleList.contains("3") and eligibleList.contains("1") and eligibleList.size.isEqualTo(
                2
            )
        )
    }

    @Test
    fun `last 2 stops are free float and first two stops are finished and arrive on 3rd stop will not set any stop as eligible for navigation`() {    //NOSONAR
        val eligibleList = dispatchStopsUseCase.setSequencedStopsEligibility(
            2,
            listOf(
                StopDetail(
                    stopid = 0,
                    sequenced = 1,
                    completedTime = at20200210T072617Z
                ),
                StopDetail(
                    stopid = 1,
                    sequenced = 1,
                    completedTime = at20200210T072617Z
                ),
                StopDetail(
                    stopid = 2,
                    sequenced = 1,
                    completedTime = at20200210T072617Z
                ),
                StopDetail(
                    stopid = 3,
                    sequenced = 0
                ),
                StopDetail(
                    stopid = 4,
                    sequenced = 0
                )
            ),
            mutableSetOf()
        )
        assertTrue(eligibleList.size == 0)
    }

    @Test
    fun `first 2 stops are free float and next five stops are sequential and arrive on 2nd stop will set 3rd stop as eligible for navigation`() {    //NOSONAR
        val eligibleList = dispatchStopsUseCase.setSequencedStopsEligibility(
            1,
            listOf(
                StopDetail(
                    stopid = 0,
                    sequenced = 0
                ),
                StopDetail(
                    stopid = 1,
                    sequenced = 0
                ),
                StopDetail(
                    stopid = 2,
                    sequenced = 1
                ),
                StopDetail(
                    stopid = 3,
                    sequenced = 1
                ),
                StopDetail(
                    stopid = 4,
                    sequenced = 1
                ),
                StopDetail(
                    stopid = 5,
                    sequenced = 1
                ),
                StopDetail(
                    stopid = 6,
                    sequenced = 1
                )
            ),
            mutableSetOf()
        )
        assertTrue(eligibleList.contains("2") and eligibleList.size.isEqualTo(1))
    }

    @Test
    fun `first 2 stops are free float and next five stops are sequential and arrive on 3rd stop will set 4th stop as eligible for navigation`() {    //NOSONAR
        val eligibleList = dispatchStopsUseCase.setSequencedStopsEligibility(
            2,
            listOf(
                StopDetail(
                    stopid = 0,
                    sequenced = 0
                ),
                StopDetail(
                    stopid = 1,
                    sequenced = 0
                ),
                StopDetail(
                    stopid = 2,
                    sequenced = 1,
                    completedTime = at20200210T072617Z
                ),
                StopDetail(
                    stopid = 3,
                    sequenced = 1
                ),
                StopDetail(
                    stopid = 4,
                    sequenced = 1
                ),
                StopDetail(
                    stopid = 5,
                    sequenced = 1
                ),
                StopDetail(
                    stopid = 6,
                    sequenced = 1
                )
            ),
            mutableSetOf()
        )
        assertTrue(eligibleList.contains("3") and eligibleList.size.isEqualTo(1))
    }

    @Test
    fun `first 2 stops are free float and next five stops are sequential and arrive on 4th stop will set 3,5th stops as eligible for navigation`() {    //NOSONAR
        val eligibleList = dispatchStopsUseCase.setSequencedStopsEligibility(
            3,
            listOf(
                StopDetail(
                    stopid = 0,
                    sequenced = 0
                ),
                StopDetail(
                    stopid = 1,
                    sequenced = 0
                ),
                StopDetail(
                    stopid = 2,
                    sequenced = 1
                ),
                StopDetail(
                    stopid = 3,
                    sequenced = 1,
                    completedTime = at20200210T072617Z
                ),
                StopDetail(
                    stopid = 4,
                    sequenced = 1
                ),
                StopDetail(
                    stopid = 5,
                    sequenced = 1
                ),
                StopDetail(
                    stopid = 6,
                    sequenced = 1
                )
            ),
            mutableSetOf<String>().also {
                it.add("2")
            }
        )
        assertTrue(
            eligibleList.contains("2") and eligibleList.contains("4") and eligibleList.size.isEqualTo(
                2
            )
        )
    }

    @Test
    fun `first 2 stops are free float and next five stops are sequential and 3rd stop already finished and arrive on 7th stop will set stops 4,5,6 as eligible for navigation`() {    //NOSONAR
        val eligibleList = dispatchStopsUseCase.setSequencedStopsEligibility(
            6,
            listOf(
                StopDetail(
                    stopid = 0,
                    sequenced = 0
                ),
                StopDetail(
                    stopid = 1,
                    sequenced = 0
                ),
                StopDetail(
                    stopid = 2,
                    sequenced = 1,
                    completedTime = at20200210T072617Z
                ),
                StopDetail(
                    stopid = 3,
                    sequenced = 1
                ),
                StopDetail(
                    stopid = 4,
                    sequenced = 1
                ),
                StopDetail(
                    stopid = 5,
                    sequenced = 1
                ),
                StopDetail(
                    stopid = 6,
                    sequenced = 1,
                    completedTime = at20200210T072617Z
                )
            ),
            mutableSetOf<String>().also {
                it.add("3")
            }
        )
        assertTrue(eligibleList.contains("3") and eligibleList.size.isEqualTo(1))
    }

    @Test
    fun `0,1-FF and 2-7-Sequence & stop 5 already arrived, arrive on stop 4 which will set 3,6 as eligible for navigation`() {    //NOSONAR
        val eligibleList = dispatchStopsUseCase.setSequencedStopsEligibility(
            3,
            listOf(
                StopDetail(
                    stopid = 0,
                    sequenced = 0
                ),
                StopDetail(
                    stopid = 1,
                    sequenced = 0
                ),
                StopDetail(
                    stopid = 2,
                    sequenced = 1
                ),
                StopDetail(
                    stopid = 3,
                    sequenced = 1,
                    completedTime = at20200210T072617Z
                ),
                StopDetail(
                    stopid = 4,
                    sequenced = 1,
                    completedTime = at20200210T072617Z
                ),
                StopDetail(
                    stopid = 5,
                    sequenced = 1
                ),
                StopDetail(
                    stopid = 6,
                    sequenced = 1
                ),
                StopDetail(
                    stopid = 7,
                    sequenced = 1
                )
            ),
            mutableSetOf<String>().also {
                it.add("2")
            }
        )
        assertTrue(
            eligibleList.contains("5") and eligibleList.contains("2") and eligibleList.size.isEqualTo(
                2
            )
        )
    }

    @Test
    fun `0,1-FF and 2-7-Sequence & stop 5,6,7 already arrived, arrive on stop 4 which will set 3,8 as eligible for navigation`() {    //NOSONAR
        val eligibleList = dispatchStopsUseCase.setSequencedStopsEligibility(
            3,
            listOf(
                StopDetail(
                    stopid = 0,
                    sequenced = 0
                ),
                StopDetail(
                    stopid = 1,
                    sequenced = 0
                ),
                StopDetail(
                    stopid = 2,
                    sequenced = 1
                ),
                StopDetail(
                    stopid = 3,
                    sequenced = 1,
                    completedTime = at20200210T072617Z
                ),
                StopDetail(
                    stopid = 4,
                    sequenced = 1,
                    completedTime = at20200210T072617Z
                ),
                StopDetail(
                    stopid = 5,
                    sequenced = 1,
                    completedTime = at20200210T072617Z
                ),
                StopDetail(
                    stopid = 6,
                    sequenced = 1,
                    completedTime = at20200210T072617Z
                ),
                StopDetail(
                    stopid = 7,
                    sequenced = 1
                )
            ),
            mutableSetOf<String>().also {
                it.add("2")
            }
        )
        assertTrue(
            eligibleList.contains("7") and eligibleList.contains("2") and eligibleList.size.isEqualTo(
                2
            )
        )
    }

    @Test
    fun `0 is current stop and manually arrive on 4th stop, only stop 1 and 5 will be eligible for navigation`() {    //NOSONAR
        val eligibleList = dispatchStopsUseCase.setSequencedStopsEligibility(
            3,
            listOf(
                StopDetail(
                    stopid = 0,
                    sequenced = 1
                ),
                StopDetail(
                    stopid = 1,
                    sequenced = 1
                ),
                StopDetail(
                    stopid = 2,
                    sequenced = 1
                ),
                StopDetail(
                    stopid = 3,
                    sequenced = 1,
                    completedTime = at20200210T072617Z
                ),
                StopDetail(
                    stopid = 4,
                    sequenced = 1
                ),
                StopDetail(
                    stopid = 5,
                    sequenced = 1
                ),
                StopDetail(
                    stopid = 6,
                    sequenced = 1
                ),
                StopDetail(
                    stopid = 7,
                    sequenced = 0
                )
            ),
            mutableSetOf<String>().also {
                it.add("0")
            }
        )
        assertTrue(
            eligibleList.contains("0") and eligibleList.contains("4") and eligibleList.size.isEqualTo(
                2
            )
        )
    }

    @Test
    fun `stop 1 is completed, manually arrive into stop 7 will set only stop 2 eligible for navigation`() {    //NOSONAR
        val eligibleList = dispatchStopsUseCase.setSequencedStopsEligibility(
            6,
            listOf(
                StopDetail(
                    stopid = 0,
                    sequenced = 1,
                    completedTime = at20200210T072617Z
                ),
                StopDetail(
                    stopid = 1,
                    sequenced = 1
                ),
                StopDetail(
                    stopid = 2,
                    sequenced = 1
                ),
                StopDetail(
                    stopid = 3,
                    sequenced = 1
                ),
                StopDetail(
                    stopid = 4,
                    sequenced = 1
                ),
                StopDetail(
                    stopid = 5,
                    sequenced = 1
                ),
                StopDetail(
                    stopid = 6,
                    sequenced = 1,
                    completedTime = at20200210T072617Z
                ),
                StopDetail(
                    stopid = 7,
                    sequenced = 0
                )
            ),
            mutableSetOf<String>().also {
                it.add("1")
            }
        )
        assertTrue(eligibleList.contains("1") and eligibleList.size.isEqualTo(1))
    }

    /*
 http://docs.peoplenetonline.com/integration/Content/OpenInterface/Automated%20Workflow/srv_pnet_dispatch.html
*/
    @Test
    fun `step 1`() {
        val eligibleList = dispatchStopsUseCase.setSequencedStopsEligibility(
            -1,
            listOf(
                StopDetail(
                    stopid = 0,
                    sequenced = 1
                ),
                StopDetail(
                    stopid = 1,
                    sequenced = 1
                ),
                StopDetail(
                    stopid = 2,
                    sequenced = 1
                ),
                StopDetail(
                    stopid = 3,
                    sequenced = 1
                ),
                StopDetail(
                    stopid = 4,
                    sequenced = 1
                ),
                StopDetail(
                    stopid = 5,
                    sequenced = 0
                )
            ),
            mutableSetOf()
        )
        assertTrue(eligibleList.contains("0") and eligibleList.size.isEqualTo(1))
    }

    /*
 http://docs.peoplenetonline.com/integration/Content/OpenInterface/Automated%20Workflow/srv_pnet_dispatch.html
*/
    @Test
    fun `step 2`() {
        val eligibleList = dispatchStopsUseCase.setSequencedStopsEligibility(
            0,
            listOf(
                StopDetail(
                    stopid = 0,
                    sequenced = 1,
                    completedTime = at20200210T072617Z
                ),
                StopDetail(
                    stopid = 1,
                    sequenced = 1
                ),
                StopDetail(
                    stopid = 2,
                    sequenced = 1
                ),
                StopDetail(
                    stopid = 3,
                    sequenced = 1
                ),
                StopDetail(
                    stopid = 4,
                    sequenced = 1
                ),
                StopDetail(
                    stopid = 5,
                    sequenced = 0
                )
            ),
            mutableSetOf()
        )
        assertTrue(eligibleList.contains("1") and eligibleList.size.isEqualTo(1))
    }

    /*
 http://docs.peoplenetonline.com/integration/Content/OpenInterface/Automated%20Workflow/srv_pnet_dispatch.html
*/
    @Test
    fun `step 3`() {
        val eligibleList = dispatchStopsUseCase.setSequencedStopsEligibility(
            3,
            listOf(
                StopDetail(
                    stopid = 0,
                    sequenced = 1,
                    completedTime = at20200210T072617Z
                ),
                StopDetail(
                    stopid = 1,
                    sequenced = 1
                ),
                StopDetail(
                    stopid = 2,
                    sequenced = 1
                ),
                StopDetail(
                    stopid = 3,
                    sequenced = 1,
                    completedTime = at20200210T072617Z
                ),
                StopDetail(
                    stopid = 4,
                    sequenced = 1
                ),
                StopDetail(
                    stopid = 5,
                    sequenced = 0
                )
            ),
            mutableSetOf<String>().also {
                it.add("1")
            }
        )
        assertTrue(
            eligibleList.contains("1") and eligibleList.contains("4") and eligibleList.size.isEqualTo(
                2
            )
        )
    }

    /*
    http://docs.peoplenetonline.com/integration/Content/OpenInterface/Automated%20Workflow/srv_pnet_dispatch.html
     */
    @Test
    fun `step 4`() {
        val eligibleList = dispatchStopsUseCase.setSequencedStopsEligibility(
            1,
            listOf(
                StopDetail(
                    stopid = 0,
                    sequenced = 1,
                    completedTime = at20200210T072617Z
                ),
                StopDetail(
                    stopid = 1,
                    sequenced = 1,
                    completedTime = at20200210T072617Z
                ),
                StopDetail(
                    stopid = 2,
                    sequenced = 1
                ),
                StopDetail(
                    stopid = 3,
                    sequenced = 1,
                    completedTime = at20200210T072617Z
                ),
                StopDetail(
                    stopid = 4,
                    sequenced = 1
                ),
                StopDetail(
                    stopid = 5,
                    sequenced = 0
                )
            ),
            mutableSetOf<String>().also {
                it.add("4")
            }
        )
        assertTrue(
            eligibleList.contains("2") and eligibleList.contains("4") and eligibleList.size.isEqualTo(
                2
            )
        )
    }

    /*
 http://docs.peoplenetonline.com/integration/Content/OpenInterface/Automated%20Workflow/srv_pnet_dispatch.html
*/
    @Test
    fun `step 5`() {
        val eligibleList = dispatchStopsUseCase.setSequencedStopsEligibility(
            2,
            listOf(
                StopDetail(
                    stopid = 0,
                    sequenced = 1,
                    completedTime = at20200210T072617Z
                ),
                StopDetail(
                    stopid = 1,
                    sequenced = 1,
                    completedTime = at20200210T072617Z
                ),
                StopDetail(
                    stopid = 2,
                    sequenced = 1,
                    completedTime = at20200210T072617Z
                ),
                StopDetail(
                    stopid = 3,
                    sequenced = 1,
                    completedTime = at20200210T072617Z
                ),
                StopDetail(
                    stopid = 4,
                    sequenced = 1
                ),
                StopDetail(
                    stopid = 5,
                    sequenced = 0
                )
            ),
            mutableSetOf()
        )
        assertTrue(eligibleList.contains("4") and eligibleList.size.isEqualTo(1))
    }

    /*
 http://docs.peoplenetonline.com/integration/Content/OpenInterface/Automated%20Workflow/srv_pnet_dispatch.html
*/
    @Test
    fun `step 6`() {
        val eligibleList = dispatchStopsUseCase.setSequencedStopsEligibility(
            -1,
            listOf(
                StopDetail(
                    stopid = 0,
                    sequenced = 1,
                    completedTime = at20200210T072617Z
                ),
                StopDetail(
                    stopid = 1,
                    sequenced = 1,
                    completedTime = at20200210T072617Z
                ),
                StopDetail(
                    stopid = 2,
                    sequenced = 1,
                    completedTime = at20200210T072617Z
                ),
                StopDetail(
                    stopid = 3,
                    sequenced = 1,
                    completedTime = at20200210T072617Z
                ),
                StopDetail(
                    stopid = 4,
                    sequenced = 1,
                    completedTime = at20200210T072617Z
                ),
                StopDetail(
                    stopid = 5,
                    sequenced = 0
                )
            ),
            mutableSetOf()
        )
        assertTrue(eligibleList.size == 0)
    }

    /*
    http://docs.peoplenetonline.com/integration/Content/OpenInterface/Automated%20Workflow/srv_pnet_dispatch.html
   */
    @Test
    fun `step 7`() {
        val eligibleList = dispatchStopsUseCase.setSequencedStopsEligibility(
            5,
            listOf(
                StopDetail(
                    stopid = 0,
                    sequenced = 1,
                    completedTime = at20200210T072617Z
                ),
                StopDetail(
                    stopid = 1,
                    sequenced = 1,
                    completedTime = at20200210T072617Z
                ),
                StopDetail(
                    stopid = 2,
                    sequenced = 1,
                    completedTime = at20200210T072617Z
                ),
                StopDetail(
                    stopid = 3,
                    sequenced = 1,
                    completedTime = at20200210T072617Z
                ),
                StopDetail(
                    stopid = 4,
                    sequenced = 1,
                    completedTime = at20200210T072617Z
                ),
                StopDetail(
                    stopid = 5,
                    sequenced = 0,
                    completedTime = at20200210T072617Z
                )
            ),
            mutableSetOf()
        )
        assertTrue(eligibleList.size == 0)
    }

    @Test
    fun `1 2 3 FF and 4 5 6 sequential and arrive at 5 to make all stop sequential stops eligible`() {
        val eligibleList = dispatchStopsUseCase.setSequencedStopsEligibility(
            4,
            listOf(
                StopDetail(
                    stopid = 0,
                    sequenced = 0
                ),
                StopDetail(
                    stopid = 1,
                    sequenced = 0
                ),
                StopDetail(
                    stopid = 2,
                    sequenced = 0
                ),
                StopDetail(
                    stopid = 3,
                    sequenced = 1
                ),
                StopDetail(
                    stopid = 4,
                    sequenced = 1,
                    completedTime = at20200210T072617Z
                ),
                StopDetail(
                    stopid = 5,
                    sequenced = 1
                )
            ),
            mutableSetOf<String>().also {
                it.add("3")
            }
        )
        assertTrue(
            eligibleList.contains("3") and eligibleList.contains("5") and eligibleList.size.isEqualTo(
                2
            )
        )
    }

    @Test
    fun `1 2 3 FF and 4 5 6 sequential and arrive at 3 to make the 1st sequential stop eligible`() { //NOSONAR
        val eligibleList = dispatchStopsUseCase.setSequencedStopsEligibility(
            2,
            listOf(
                StopDetail(
                    stopid = 0,
                    sequenced = 0
                ),
                StopDetail(
                    stopid = 1,
                    sequenced = 0
                ),
                StopDetail(
                    stopid = 2,
                    sequenced = 0
                ),
                StopDetail(
                    stopid = 3,
                    sequenced = 1
                ),
                StopDetail(
                    stopid = 4,
                    sequenced = 1
                ),
                StopDetail(
                    stopid = 5,
                    sequenced = 1
                )
            ),
            mutableSetOf()
        )
        assertTrue(eligibleList.contains("3") and eligibleList.size.isEqualTo(1))
    }

    @Test
    fun `1 sequential and 2 3 are FF so all stop sequential stops eligible`() {
        val eligibleList = dispatchStopsUseCase.setSequencedStopsEligibility(
            -1,
            listOf(
                StopDetail(
                    stopid = 0,
                    sequenced = 1
                ),
                StopDetail(
                    stopid = 1,
                    sequenced = 0
                ),
                StopDetail(
                    stopid = 2,
                    sequenced = 0
                )
            ),
            mutableSetOf()
        )
        assertTrue(eligibleList.contains("0") and eligibleList.size.isEqualTo(1))
    }

    @Test
    fun `1 2 3 sequential  and 4 5 6  FF and arrive at 2 to make all stop sequential stops eligible`() {
        val eligibleList = dispatchStopsUseCase.setSequencedStopsEligibility(
            1,
            listOf(
                StopDetail(
                    stopid = 0,
                    sequenced = 1
                ),
                StopDetail(
                    stopid = 1,
                    sequenced = 1,
                    completedTime = at20200210T072617Z
                ),
                StopDetail(
                    stopid = 2,
                    sequenced = 1
                ),
                StopDetail(
                    stopid = 3,
                    sequenced = 0
                ),
                StopDetail(
                    stopid = 4,
                    sequenced = 0
                ),
                StopDetail(
                    stopid = 5,
                    sequenced = 0
                )
            ),
            mutableSetOf<String>().also {
                it.add("0")
            }
        )
        assertTrue(
            eligibleList.contains("0") and eligibleList.contains("2") and eligibleList.size.isEqualTo(
                2
            )
        )
    }

    @Test
    fun `1 2 sequential and 3 are FF so all stop sequential stops eligible`() {
        val eligibleList = dispatchStopsUseCase.setSequencedStopsEligibility(
            -1,
            listOf(
                StopDetail(
                    stopid = 0,
                    sequenced = 1
                ),
                StopDetail(
                    stopid = 1,
                    sequenced = 0
                ),
                StopDetail(
                    stopid = 2,
                    sequenced = 0
                )
            ),
            mutableSetOf()
        )
        assertTrue(eligibleList.contains("0") and eligibleList.size.isEqualTo(1))
    }

    @Test
    fun `1 2 FF and 3 sequential  and 4 5 FF and 6 sequential and first sequential stop is eligible`() {
        val eligibleList = dispatchStopsUseCase.setSequencedStopsEligibility(
            -1,
            listOf(
                StopDetail(
                    stopid = 0,
                    sequenced = 0
                ),
                StopDetail(
                    stopid = 1,
                    sequenced = 0
                ),
                StopDetail(
                    stopid = 2,
                    sequenced = 1
                ),
                StopDetail(
                    stopid = 3,
                    sequenced = 0
                ),
                StopDetail(
                    stopid = 4,
                    sequenced = 0
                ),
                StopDetail(
                    stopid = 5,
                    sequenced = 1
                )
            ),
            mutableSetOf()
        )
        assertTrue(eligibleList.contains("2") and eligibleList.size.isEqualTo(1))
    }

    @Test
    fun `1 3 5 FF and 2 4 6 sequential and first sequential stop is eligible`() { //NOSONAR
        val eligibleList = dispatchStopsUseCase.setSequencedStopsEligibility(
            2,
            listOf(
                StopDetail(
                    stopid = 0,
                    sequenced = 0
                ),
                StopDetail(
                    stopid = 1,
                    sequenced = 1
                ),
                StopDetail(
                    stopid = 2,
                    sequenced = 0,
                    completedTime = at20200210T072617Z
                ),
                StopDetail(
                    stopid = 3,
                    sequenced = 1
                ),
                StopDetail(
                    stopid = 4,
                    sequenced = 0
                ),
                StopDetail(
                    stopid = 5,
                    sequenced = 1
                )
            ),
            mutableSetOf<String>().also {
                it.add("1")
            }
        )
        assert(eligibleList.size > 0)
        assertTrue(
            eligibleList.contains("1") and eligibleList.contains("3") and eligibleList.size.isEqualTo(
                2
            )
        )
    }

    @Test
    fun `1 3 5 FF and 2 4 6 sequential and 2nd sequential stop is eligible on completing 1st sequential`() { //NOSONAR
        val eligibleList = dispatchStopsUseCase.setSequencedStopsEligibility(
            1,
            listOf(
                StopDetail(
                    stopid = 0,
                    sequenced = 0
                ),
                StopDetail(
                    stopid = 1,
                    sequenced = 1,
                    completedTime = at20200210T072617Z
                ),
                StopDetail(
                    stopid = 2,
                    sequenced = 0
                ),
                StopDetail(
                    stopid = 3,
                    sequenced = 1
                ),
                StopDetail(
                    stopid = 4,
                    sequenced = 0
                ),
                StopDetail(
                    stopid = 5,
                    sequenced = 1
                )
            ),
            mutableSetOf()
        )
        assertTrue(eligibleList.size == 0)
    }

    @Test
    fun `stop id removed from eligible stops when it is not completed`() {
        val eligibleStops = dispatchStopsUseCase.setSequencedStopsEligibility(1, listOf(
            StopDetail(
                stopid = 0,
                sequenced = 0
            ),
            StopDetail(
                stopid = 1,
                sequenced = 1,
                completedTime = at20200210T072617Z
            )
        ), mutableSetOf<String>().also {
            it.add("0")
        })

        assert(eligibleStops.contains("1").not())

        every { dispatchStopsUseCase.checkAllStopsAreCompletedOrNoSequentialStopsToSet(any()) } throws IllegalArgumentException()

        assert(
            dispatchStopsUseCase.setSequencedStopsEligibility(
                1, listOf(),
                mutableSetOf()
            ).isEmpty()
        )

    }

    @Test
    fun `all stops are completed return empty  eligible stops to navigate`() {
        val stopList = listOf(
            StopDetail(
                stopid = 0,
                sequenced = 0,
                completedTime = at20200210T072617Z
            ),
            StopDetail(
                stopid = 1,
                sequenced = 1,
                completedTime = at20200210T072617Z
            )
        )
        dispatchStopsUseCase.setSequencedStopsEligibility(-1, stopList, mutableSetOf())

        assertEquals(
            dispatchStopsUseCase.checkAllStopsAreCompletedOrNoSequentialStopsToSet(stopList),
            true
        )
    }

}