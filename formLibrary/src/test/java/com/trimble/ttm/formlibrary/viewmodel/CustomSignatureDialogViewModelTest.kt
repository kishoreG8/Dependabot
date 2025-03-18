package com.trimble.ttm.formlibrary.viewmodel

import com.trimble.ttm.formlibrary.utils.callOnCleared
import io.mockk.MockKAnnotations
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Before
import org.junit.Test

class CustomSignatureDialogViewModelTest {

    private lateinit var customSignatureDialogViewModel: CustomSignatureDialogViewModel

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        customSignatureDialogViewModel = CustomSignatureDialogViewModel()
   }

    @Test
    fun `verify function calls`() {    //NOSONAR
        customSignatureDialogViewModel.cacheSignatureByteArray(byteArrayOf())
        customSignatureDialogViewModel.getCachedSignatureByteArray()
    }

    @Test
    fun `verify onClear call`() =    //NOSONAR
        customSignatureDialogViewModel.callOnCleared()

    @After
    fun after() {
        unmockkAll()
    }

}