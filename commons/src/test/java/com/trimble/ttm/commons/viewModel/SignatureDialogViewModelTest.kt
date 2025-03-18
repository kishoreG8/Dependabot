package com.trimble.ttm.commons.viewModel

import org.junit.Before
import org.junit.Test
import kotlin.test.assertTrue

class SignatureDialogViewModelTest {

    private lateinit var signatureDialogViewModel : SignatureDialogViewModel

    @Before
    fun setup(){
        signatureDialogViewModel = SignatureDialogViewModel()
    }

    @Test
    fun `check whether the byte array is cached or not`(){
        signatureDialogViewModel.cacheSignatureByteArray(byteArray = byteArrayOf(13243.toByte()), viewId = 1)
        assertTrue {
            signatureDialogViewModel.cachedSignatureByteArrayMap.size == 1
        }
    }

    @Test
    fun `clear single views byte array`(){
        signatureDialogViewModel.cacheSignatureByteArray(byteArray = byteArrayOf(13243.toByte()), viewId = 1)
        signatureDialogViewModel.clearCachedSignatureByteArray(viewId = 1)
        assertTrue {
            signatureDialogViewModel.getCachedSignatureByteArray(viewId = 1) == null
        }
    }

    @Test
    fun `clear entire byte array map`(){
        signatureDialogViewModel.cacheSignatureByteArray(byteArray = byteArrayOf(1234.toByte()), viewId = 1)
        signatureDialogViewModel.clearCachedSignatureByteArray()
        assertTrue {
            signatureDialogViewModel.cachedSignatureByteArrayMap.size == 0
        }
    }

    @Test
    fun `check whether able to get the cached byte array`(){
        signatureDialogViewModel.cacheSignatureByteArray(byteArray = byteArrayOf(1234.toByte()), viewId = 1)
        assertTrue {
            signatureDialogViewModel.getCachedSignatureByteArray(viewId = 1)
                .contentEquals(byteArrayOf(1234.toByte()))
        }
    }


}