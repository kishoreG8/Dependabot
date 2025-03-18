package com.trimble.ttm.commons.model

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FormFieldTests {

    @Test
    fun getImageNames_withImageReferences_returnsImageNames() {
        val formFields = arrayListOf(
            FormField(qtype = FormFieldType.IMAGE_REFERENCE.ordinal).also {  it.uniqueIdentifier = "image1"},
            FormField(qtype = FormFieldType.TEXT.ordinal).also {  it.uniqueIdentifier = "text1"},
            FormField(qtype = FormFieldType.IMAGE_REFERENCE.ordinal).also {  it.uniqueIdentifier = "image2"}
        )

        val imageNames = formFields.getImageNames()

        assertEquals(listOf("image1", "image2"), imageNames)
    }

    @Test
    fun getImageNames_withoutImageReferences_returnsNull() {
        val formFields = arrayListOf(
            FormField(qtype = FormFieldType.TEXT.ordinal).also { it.uniqueIdentifier = "text1" },
            FormField(qtype = FormFieldType.AUTO_DATE_TIME.ordinal).also {
                it.uniqueIdentifier = "auto date time"
            }
        )

        val imageNames = formFields.getImageNames()

        assertNull(imageNames)
    }

    @Test
    fun getImageNames_withEmptyList_returnsNull() {
        val formFields = arrayListOf<FormField>()

        val imageNames = formFields.getImageNames()

        assertNull(imageNames)
    }

}