package com.trimble.ttm.formlibrary.utils

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import java.lang.reflect.Field

const val TEST_DELAY_OR_TIMEOUT = 2000L

fun ViewModel.callOnCleared() {
    val viewModelStore = ViewModelStore()
    val viewModelProvider = ViewModelProvider(viewModelStore, object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = this@callOnCleared as T
    })
    viewModelProvider[this@callOnCleared::class.java]
    viewModelStore.clear()
}


fun getFieldFromObject(fieldName: String, instance: Any) : Field {
    instance.javaClass.declaredFields.forEach {
        it.setAccessible(true)
        if("${it.name}" == fieldName) {
            println("${it.name} -----> ${it.get(instance)}")
            return it
        }
    }
    throw Exception("Did not find $fieldName in ${instance.javaClass}")
}