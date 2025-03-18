package com.trimble.ttm.formlibrary.usecases

import com.trimble.ttm.commons.model.Form
import com.trimble.ttm.commons.model.FormDef
import com.trimble.ttm.formlibrary.repo.CacheGroupsRepo
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class CacheGroupUsecaseTest {

    @RelaxedMockK
    private lateinit var cacheGroupsRepo: CacheGroupsRepo
    private lateinit var cacheGroupsUseCase: CacheGroupsUseCase

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        cacheGroupsUseCase = CacheGroupsUseCase(cacheGroupsRepo)
    }

    @Test
    fun `verify getVidFromVUnitCollection`() = runTest {

        coEvery { cacheGroupsRepo.getVidFromVUnitCollection(any(), any(), any()) } returns Pair(
            12343,
            true
        )
        cacheGroupsUseCase.getVidFromVUnitCollection("123", 2324, "test")

        coVerify { cacheGroupsRepo.getVidFromVUnitCollection(any(), any(), any()) }
    }

    @Test
    fun `verify getGroupIdsFromGroupUnitCollection`() = runTest {

        coEvery {
            cacheGroupsRepo.getGroupIdsFromGroupUnitCollection(
                any(),
                any(),
                any(),
                any()
            )
        } returns Pair(
            setOf(), true
        )
        cacheGroupsUseCase.getGroupIdsFromGroupUnitCollection("1233", 1234, 23342, "test")

        coVerify { cacheGroupsRepo.getGroupIdsFromGroupUnitCollection(any(), any(), any(), any()) }
    }

    @Test
    fun `verify getFormIdsFromGroups`() = runTest {

        coEvery { cacheGroupsRepo.getFormIdsFromGroups(any(), any(), any(), any(), any()) } returns Triple(
            mapOf(), true, true
        )
        cacheGroupsUseCase.getFormIdsFromGroups(setOf(), "123", 342432, "test", true)

        coVerify { cacheGroupsRepo.getFormIdsFromGroups(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `verify getUserIdsFromGroups`() = runTest {

        coEvery { cacheGroupsRepo.getUserIdsFromGroups(any(), any(), any(), any(), any()) } returns Triple(
            mutableSetOf(), true, true
        )
        cacheGroupsUseCase.getUserIdsFromGroups(setOf(), "123", 342432, "test", true)

        coVerify { cacheGroupsRepo.getUserIdsFromGroups(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `verify cacheFormTemplate`() = runTest {

        coEvery { cacheGroupsRepo.cacheFormTemplate(any(), any()) } returns Form()
        cacheGroupsUseCase.cacheFormTemplate("1233", true)

        coVerify { cacheGroupsRepo.cacheFormTemplate(any(), any()) }
    }

    @Test
    fun `verify checkAndUpdateCacheForGroupsFromServer`() = runTest {

        coEvery {
            cacheGroupsRepo.checkAndUpdateCacheForGroupsFromServer(
                any(),
                any(),
                any(),
                any()
            )
        } returns true
        cacheGroupsUseCase.checkAndUpdateCacheForGroupsFromServer(
            "123",
            "1234",
            mockk(),
            "test"
        )

        coVerify {
            cacheGroupsRepo.checkAndUpdateCacheForGroupsFromServer(
                any(),
                any(),
                any(),
                any()
            )
        }
    }

    @Test
    fun `verify getFirestoreExceptionNotifier`() = runTest {

        coEvery { cacheGroupsRepo.getFirestoreExceptionNotifier() } returns flowOf()
        cacheGroupsUseCase.getFirestoreExceptionNotifier()

        coVerify { cacheGroupsRepo.getFirestoreExceptionNotifier() }
    }

    @Test
    fun `verify for valid form name order in formlibrary`() {
        val forms = mutableMapOf<Double, FormDef>()
        forms[1.0] = FormDef(name = "a")
        forms[2.0] = FormDef(name = "A")
        forms[3.0] = FormDef(name = " ")
        forms[4.0] = FormDef(name = "1")
        forms[5.0] = FormDef(name = "~")
        val sortedForms = mutableMapOf<Double, FormDef>()
        cacheGroupsUseCase.sortFormByName(forms, sortedForms)
        assertEquals(listOf(3.0, 4.0, 1.0, 2.0, 5.0).toList(), sortedForms.map { it.key }.toList())
    }

    @Test
    fun `verify for invalid form name order in formlibrary`() {
        val forms = mutableMapOf<Double, FormDef>()
        forms[1.0] = FormDef(name = "a")
        forms[2.0] = FormDef(name = "A")
        forms[3.0] = FormDef(name = " ")
        forms[4.0] = FormDef(name = "1")
        forms[5.0] = FormDef(name = "~")
        forms[6.0] = FormDef(name = "c")
        forms[7.0] = FormDef(name = "C")
        val sortedForms = mutableMapOf<Double, FormDef>()
        cacheGroupsUseCase.sortFormByName(forms, sortedForms)
        assertNotEquals(
            listOf(3.0, 4.0, 2.0, 7.0, 1.0, 5.0, 6.0).toList(),
            sortedForms.map { it.key }.toList()
        )
    }

    @Test
    fun `verify whether forms are sorted alphabetically`() {
        val forms = mutableSetOf<FormDef>()
        forms.add(FormDef(name = "C"))
        forms.add(FormDef(name = "d"))
        forms.add(FormDef(name = "A"))
        forms.add(FormDef(name = "Aa"))
        forms.add(FormDef(name = "ac"))
        forms.add(FormDef(name = "E"))
        forms.add(FormDef(name = "b"))
        val sortedForms = cacheGroupsUseCase.sortFormsAlphabetically(forms)
        assertEquals(FormDef(name = "Aa"), sortedForms.elementAt(1))
        assertEquals(FormDef(name = "C"), sortedForms.elementAt(4))
        assertNotEquals(FormDef(name = "d"), sortedForms.elementAt(2))
    }

    @Test
    fun `verify whether form names with alphanumeric characters are sorted in order`() {
        val forms = mutableSetOf<FormDef>()
        forms.add(FormDef(name = "C2"))
        forms.add(FormDef(name = "d2"))
        forms.add(FormDef(name = "A"))
        forms.add(FormDef(name = "3E2"))
        forms.add(FormDef(name = "b1"))
        val sortedForms = cacheGroupsUseCase.sortFormsAlphabetically(forms)
        assertEquals(FormDef(name = "3E2"), sortedForms.elementAt(0))
        assertNotEquals(FormDef(name = "A"), sortedForms.elementAt(0))
    }

    @Test
    fun `verify whether form names with special characters are sorted in order`() {
        val forms = mutableSetOf<FormDef>()
        forms.add(FormDef(name = "!C2"))
        forms.add(FormDef(name = "#d2"))
        forms.add(FormDef(name = "@A"))
        forms.add(FormDef(name = "&E2"))
        forms.add(FormDef(name = "b1"))
        val sortedForms = cacheGroupsUseCase.sortFormsAlphabetically(forms)
        assertEquals(FormDef(name = "!C2"), sortedForms.elementAt(0))
        assertEquals(FormDef(name = "&E2"), sortedForms.elementAt(2))
        assertNotEquals(FormDef(name = "#d2"), sortedForms.elementAt(2))
    }

    @After
    fun after() {
        unmockkAll()
    }

}