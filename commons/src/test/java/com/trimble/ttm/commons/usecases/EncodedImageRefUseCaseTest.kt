package com.trimble.ttm.commons.usecases

import com.trimble.ttm.commons.model.FormDef
import com.trimble.ttm.commons.model.FormField
import com.trimble.ttm.commons.model.FormFieldType
import com.trimble.ttm.commons.model.FormTemplate
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.repo.EncodedImageRefRepo
import com.trimble.ttm.commons.usecase.EncodedImageRefUseCase
import com.trimble.ttm.commons.utils.EMPTY_STRING
import com.trimble.ttm.commons.utils.TEST_DELAY_OR_TIMEOUT
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

class EncodedImageRefUseCaseTest {

    @MockK
    private lateinit var encodedImageRefRepo: EncodedImageRefRepo
    @MockK
    private lateinit var appModuleCommunicator: AppModuleCommunicator
    private lateinit var encodedImageRefUseCase: EncodedImageRefUseCase
    private val cid = "10119"
    private val truckNum = "1234"
    private val caller = "test"
    private val imgData = "343rjnonfu44u4u4hru4ru4r"
    private val uniqueId = "56789"

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        encodedImageRefUseCase = EncodedImageRefUseCase(encodedImageRefRepo)
        every { encodedImageRefRepo.generateUUID() } returns "1234"
    }

    @Test
    fun `mapImageUniqueIdentifier assigns uniqueIdentifier to image fields with non-empty uiData`() = runTest {
        val formTemp = FormTemplate(
            FormDef(cid = cid.toInt()),
            arrayListOf(
                FormField(
                    qnum = 0,
                    qtype = FormFieldType.IMAGE_REFERENCE.ordinal
                ).also {
                    it.uiData = imgData
                    it.uniqueIdentifier = EMPTY_STRING
                    it.needToSyncImage = true
                },
                FormField(
                    qnum = 1,
                    qtype = FormFieldType.IMAGE_REFERENCE.ordinal
                ).also {
                    it.uiData = imgData
                    it.uniqueIdentifier = EMPTY_STRING
                    it.needToSyncImage = true
                }
            )
        )
        encodedImageRefUseCase.mapImageUniqueIdentifier(formTemp)
        formTemp.formFieldsList.forEach { formField ->
            if (formField.qtype == FormFieldType.IMAGE_REFERENCE.ordinal && formField.uiData.isNotEmpty()) {
                assert(formField.uniqueIdentifier.isNotEmpty())
            }
        }
    }

    @Test
    fun `mapImageUniqueIdentifier does not assign uniqueIdentifier to non-image fields`() = runTest {
        val formTemp = FormTemplate(
            FormDef(cid = cid.toInt()),
            arrayListOf(
                FormField(
                    qnum = 0,
                    qtype = FormFieldType.MULTIPLE_CHOICE.ordinal
                ).also {
                    it.uiData = imgData
                    it.uniqueIdentifier = EMPTY_STRING
                }
            )
        )
        encodedImageRefUseCase.mapImageUniqueIdentifier(formTemp)
        formTemp.formFieldsList.forEach { formField ->
            if (formField.qtype != FormFieldType.IMAGE_REFERENCE.ordinal) {
                assert(formField.uniqueIdentifier.isEmpty())
            }
        }
    }

    @Test
    fun `mapImageUniqueIdentifier does not assign uniqueIdentifier to image fields with empty uiData`() = runTest {
        val formTemp = FormTemplate(
            FormDef(cid = cid.toInt()),
            arrayListOf(
                FormField(
                    qnum = 0,
                    qtype = FormFieldType.IMAGE_REFERENCE.ordinal
                ).also {
                    it.uiData = EMPTY_STRING
                    it.uniqueIdentifier = EMPTY_STRING
                }
            )
        )
        encodedImageRefUseCase.mapImageUniqueIdentifier(formTemp)
        formTemp.formFieldsList.forEach { formField ->
            if (formField.qtype == FormFieldType.IMAGE_REFERENCE.ordinal && formField.uiData.isEmpty()) {
                assert(formField.uniqueIdentifier.isEmpty())
            }
        }
    }

    @After
    fun after() {
        unmockkAll()
    }

}