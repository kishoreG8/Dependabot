package com.trimble.ttm.formlibrary.utils

import com.trimble.ttm.formlibrary.model.InspectionType
import com.trimble.ttm.formlibrary.utils.EDVIRFormUtils.createAnnotationText
import org.junit.Assert
import org.junit.Test

class EDVIRFormUtilsTest {

    private val tn02349 = "TN 02349"

    @Test
    fun `createAnnotationText method returns trimmed text if the annotation text exceeds maximum limit`() {    //NOSONAR
        val annotationText = createAnnotationText(
            InspectionType.PostInspection.name,
            "12383438302834723948209348",
            tn02349
        )
        Assert.assertEquals(60, annotationText.length)
        assert(annotationText.length in 1..60)
    }

    @Test
    fun `createAnnotationText method returns actual text if the annotation text does not exceed the maximum limit`() {    //NOSONAR
        val annotationText = createAnnotationText(
            InspectionType.PostInspection.name,
            "123834383028347",
            tn02349
        )
        assert(annotationText.length in 1..60)
    }

    @Test
    fun `createAnnotationText method returns empty string if it contains `() {    //NOSONAR
        val annotationText = createAnnotationText(
            InspectionType.PostInspection.name,
            "12383438307%$#",
            tn02349
        )
        assert(annotationText.length in 1..60)
    }
}