package com.trimble.ttm.commons.viewModel

import androidx.lifecycle.ViewModel


class SignatureDialogViewModel : ViewModel() {
    internal val cachedSignatureByteArrayMap = HashMap<Int,ByteArray?>()

    fun cacheSignatureByteArray(byteArray: ByteArray?,viewId:Int) {
        cachedSignatureByteArrayMap[viewId] = byteArray
    }

    fun clearCachedSignatureByteArray(viewId: Int) {
        cachedSignatureByteArrayMap[viewId] = null
    }


    fun clearCachedSignatureByteArray(){
        cachedSignatureByteArrayMap.clear()
    }

    fun getCachedSignatureByteArray(viewId: Int) = cachedSignatureByteArrayMap[viewId]

    override fun onCleared() {
        clearCachedSignatureByteArray()
        super.onCleared()
    }
}