package com.trimble.ttm.formlibrary.utils

import android.content.Context
import com.trimble.ttm.formlibrary.R
import com.trimble.ttm.formlibrary.model.InspectionType
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class UtilsExtTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = mockk()
    }

    @Test
    fun `verify return values of getInspectionType`() {    //NOSONAR
        every { context.getString(R.string.pre_trip_inspection) } returns "Pre Trip Inspection"
        every { context.getString(R.string.post_trip_inspection) } returns "Post Trip Inspection"
        every { context.getString(R.string.inter_trip_inspection) } returns "Inter Trip Inspection"
        every { context.getString(R.string.dot_inspection) } returns "DOT Inspection"
        Assert.assertEquals(
            "Pre Trip Inspection",
            InspectionType.PreInspection.name.getInspectionTypeUIText(context)
        )
        Assert.assertEquals(
            "Post Trip Inspection",
            InspectionType.PostInspection.name.getInspectionTypeUIText(context)
        )
        Assert.assertEquals(
            "Inter Trip Inspection",
            InspectionType.InterInspection.name.getInspectionTypeUIText(context)
        )
        Assert.assertEquals(
            "DOT Inspection",
            InspectionType.DotInspection.name.getInspectionTypeUIText(context)
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }
}