package com.trimble.ttm.formlibrary.repo

import com.trimble.ttm.commons.model.FormTemplate
import com.trimble.ttm.formlibrary.dataSource.IFormsDataSource

open class FormsRepoImpl(private val fireStoreFormsDataSource : IFormsDataSource, private val cloudFunctionFormsDataSource: IFormsDataSource) : FormsRepo {
    override suspend fun getForm(customerId: String, formId: Int, isMandatoryInspection: Boolean) : FormTemplate {
        return if(isMandatoryInspection){
            transformFormTemplate(cloudFunctionFormsDataSource.getForm(customerId, formId))
        }else{
            transformFormTemplate(fireStoreFormsDataSource.getForm(customerId, formId))
        }
    }
    override suspend fun getFreeForm(formId: Int, isMandatoryInspection: Boolean) : FormTemplate {
        return if(isMandatoryInspection){
            cloudFunctionFormsDataSource.getFreeForm(formId)
        }else{
            fireStoreFormsDataSource.getFreeForm(formId)
        }
    }
    private fun transformFormTemplate(formTemplate : FormTemplate) : FormTemplate {
        //transform FormFieldList
        formTemplate.formFieldsList.forEach {
            it.actualLoopCount = it.loopcount
        }
        return formTemplate
    }
}