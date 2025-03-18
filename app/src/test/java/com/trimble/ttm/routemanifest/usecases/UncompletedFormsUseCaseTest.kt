package com.trimble.ttm.routemanifest.usecases

import com.google.gson.Gson
import com.trimble.ttm.commons.model.DispatchFormPath
import com.trimble.ttm.formlibrary.utils.EMPTY_STRING
import org.junit.Test
import org.koin.test.KoinTest
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class UncompletedFormsUseCaseTest : KoinTest {
    private val uncompletedFormsUseCaseTests = UncompletedFormsUseCase

    private fun createDispatchFormSample() =
        DispatchFormPath("stopSample", 1, 1, 1, 1)

    private fun createListWithoutDispatchFormSample(): ArrayList<DispatchFormPath> {
        val dispatchFormList = ArrayList<DispatchFormPath>()
        dispatchFormList.add(DispatchFormPath("stopSample2", 2, 2, 2, 2))
        dispatchFormList.add(DispatchFormPath("stopSample3", 3, 3, 3, 3))
        dispatchFormList.add(DispatchFormPath("stopSample4", 4, 4, 4, 4))

        return dispatchFormList
    }

    private fun createListWithDispatchFormSample(): ArrayList<DispatchFormPath> {
        val dispatchFormList = createListWithoutDispatchFormSample()
        dispatchFormList.add(createDispatchFormSample())

        return dispatchFormList
    }

    @Test
    fun `test addFormToPreference with stackForm being json must save the form into the list`() {
        //Arrange
        val dispatchFormSample = createDispatchFormSample()
        val dispatchFormListJson = Gson().toJson(createListWithDispatchFormSample())

        //Act
        val result = uncompletedFormsUseCaseTests.addFormToPreference(
            Gson().toJson(createListWithDispatchFormSample()),
            dispatchFormSample
        )

        //Assert
        assertEquals(dispatchFormListJson, result)
    }

    @Test
    fun `test addFormToPreference with stackForm not being json must return a new list with only the added form`() {
        //Arrange
        val dispatchFormSample = createDispatchFormSample()
        val dispatchFormControlList = ArrayList<DispatchFormPath>()
        dispatchFormControlList.add(createDispatchFormSample())

        //Act
        val result = uncompletedFormsUseCaseTests.addFormToPreference(EMPTY_STRING, dispatchFormSample)

        //Assert
        assertEquals(Gson().toJson(dispatchFormControlList), result)
    }

    @Test
    fun `test addDispatchIfNotIncluded when dispatch is not on list must include it`() {
        //Arrange - Create a list with 3 items, one dispatch to be added in this first list and a second list already with the item for control
        val pureList = createListWithoutDispatchFormSample()
        val dispatchFormSample = createDispatchFormSample()
        val listWithFormSample = createListWithDispatchFormSample()

        //Act - Call addDispatchIfNotIncluded passing dispatchFormSample to be added to the dispatchForm list
        val result = uncompletedFormsUseCaseTests.addDispatchFormPathIfNotIncluded(
            pureList,
            dispatchFormSample
        )

        //Assert - Verify if the list contains the dispatch
        assertEquals(
            HashSet<DispatchFormPath>(listWithFormSample),
            HashSet<DispatchFormPath>(result)
        )
    }

    @Test
    fun `test addDispatchIfNotIncluded when dispatch is on list must return the list as it was before`() {
        //Arrange - Create two lists with the same 4 items and a dispatch already existent to be added in the list
        val originalFormList = createListWithDispatchFormSample()
        val listWithFormSample = createListWithDispatchFormSample()
        val dispatchFormSample = createDispatchFormSample()

        //Act - Call addDispatchIfNotIncluded passing dispatchFormSample to be added to the dispatchForm list
        val result = uncompletedFormsUseCaseTests.addDispatchFormPathIfNotIncluded(
            originalFormList,
            dispatchFormSample
        )

        //Assert - Verify if item is not duplicated on the list
        assertContentEquals(listWithFormSample, result)
    }

    @Test
    fun `test deserializeFormList passing a valid dispatchFormList in json should return the list deserialized`() {
        //Arrange
        val dispatchFormList = createListWithoutDispatchFormSample()
        val dispatchFormListJson = Gson().toJson(dispatchFormList)

        //Act
        val result = uncompletedFormsUseCaseTests.deserializeFormList(dispatchFormListJson)

        //Assert
        assertEquals(dispatchFormList, result)
    }

    @Test
    fun `test deserializeFormList passing an invalid json should return an empty list`() {
        //Arrange
        val dispatchFormList = ArrayList<DispatchFormPath>()

        //Act
        val result = uncompletedFormsUseCaseTests.deserializeFormList(EMPTY_STRING)

        //Assert
        assertEquals(dispatchFormList, result)
    }

    @Test
    fun `test removeForm should remove the form with the given stopId from the formStack`() {
        // Arrange
        val stopIdToRemove = 2
        val dispatchFormList = createListWithDispatchFormSample()
        val dispatchFormListJson = Gson().toJson(dispatchFormList)
        val expectedList = dispatchFormList.filter { it.stopId != stopIdToRemove }

        // Act
        val result = uncompletedFormsUseCaseTests.removeForm(dispatchFormListJson, stopIdToRemove)

        // Assert
        assertEquals(expectedList, result)
    }

    @Test
    fun `test removeForm should not remove the form with the given stopId from the formStack if stopid is not present in the formstack`() {
        // Arrange
        val stopIdToRemove = 6
        val dispatchFormList = createListWithDispatchFormSample()
        val dispatchFormListJson = Gson().toJson(dispatchFormList)
        val expectedList = dispatchFormList.filter { it.stopId != stopIdToRemove }

        // Act
        val result = uncompletedFormsUseCaseTests.removeForm(dispatchFormListJson, stopIdToRemove)

        // Assert
        assertEquals(expectedList, result)
    }
}