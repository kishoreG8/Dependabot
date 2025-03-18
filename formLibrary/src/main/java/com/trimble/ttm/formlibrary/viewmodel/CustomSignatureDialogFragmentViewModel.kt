package com.trimble.ttm.formlibrary.viewmodel

import androidx.lifecycle.ViewModel

class CustomSignatureDialogViewModel : ViewModel() {
    private var cachedSignatureByteArray = byteArrayOf()

    fun cacheSignatureByteArray(byteArray: ByteArray) {
        cachedSignatureByteArray = byteArray
    }

    fun clearCachedSignatureByteArray() {
        cachedSignatureByteArray = byteArrayOf()
    }

    fun getCachedSignatureByteArray() = cachedSignatureByteArray

    override fun onCleared() {
        clearCachedSignatureByteArray()
        super.onCleared()
    }
}