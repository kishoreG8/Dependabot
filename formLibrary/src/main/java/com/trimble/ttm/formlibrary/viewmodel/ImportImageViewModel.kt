package com.trimble.ttm.formlibrary.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import com.trimble.ttm.formlibrary.usecases.ImageHandlerUseCase
import kotlinx.coroutines.CoroutineDispatcher
import java.io.File

class ImportImageViewModel(private val imageHandlerUseCase: ImageHandlerUseCase) : ViewModel() {

    private var _file: File? = null

    val file: File?
        get() = _file

    fun setFile(file: File?) {
        _file = file
    }

    suspend fun saveImageLocally(io: CoroutineDispatcher, uri: Uri, uniqueId: String) =
        imageHandlerUseCase.saveImageLocally(io, uri, uniqueId)

}