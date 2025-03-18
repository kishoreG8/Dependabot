package com.trimble.ttm.commons.usecase

import android.app.Application
import com.trimble.ttm.commons.model.FormResponse
import com.trimble.ttm.commons.model.FormTemplate
import com.trimble.ttm.commons.repo.DispatchFormRepo
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive

class DispatchFormUseCase(private val applicationInstance: Application, private val dispatchFormRepo: DispatchFormRepo,
                          private val formFieldDataBuilderUseCase: FormFieldDataUseCase
) {
    suspend fun saveDispatchFormResponse(
        path: String, data: HashMap<String, Any>, caller: String
    ): Boolean = dispatchFormRepo.saveDispatchFormResponse(path, data, caller)

    fun addFieldDataInFormResponse(
        formTemplate: FormTemplate,
        formResponse: FormResponse,
        obcId: String
    ): Flow<FormResponse> {
        return callbackFlow {
            if (isActive) {
                this.trySend(
                    formFieldDataBuilderUseCase.buildFormData(
                        formTemplate,
                        formResponse,
                        obcId = obcId,
                        applicationInstance.applicationContext
                    )
                ).isSuccess
            }

            awaitClose {
                cancel()
            }
        }
    }

}