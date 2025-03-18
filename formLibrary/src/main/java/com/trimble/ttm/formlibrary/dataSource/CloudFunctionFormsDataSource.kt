package com.trimble.ttm.formlibrary.dataSource

import com.google.android.gms.tasks.Task
import com.google.firebase.functions.FirebaseFunctions
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.model.FormDef
import com.trimble.ttm.commons.model.FormField
import com.trimble.ttm.commons.model.FormTemplate
import com.trimble.ttm.commons.utils.CUSTOMER_ID_KEY
import com.trimble.ttm.commons.utils.FORM_ID_KEY
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await

class CloudFunctionFormsDataSource(private val ioDispatcher : CoroutineDispatcher = Dispatchers.IO) : IFormsDataSource{
    private val tag = "CloudFunctionFormsDataStoreImpl"

    private val gson = Gson()

    override suspend fun getForm(customerId: String, formId: Int): FormTemplate {
        fetchFormFromCloudFunction(customerId, formId, NORMAL_FORM_CLASS).let {
            return getFormTemplateFromHashMap(it)
        }
    }

    override suspend fun getFreeForm(formId: Int): FormTemplate {
        fetchFormFromCloudFunction("", formId, FREE_FORM_CLASS).let {
            return getFormTemplateFromHashMap(it)
        }
    }

    private fun getFormTemplateFromHashMap(hashMap: HashMap<String,Any>) : FormTemplate{
        val formTemplate = FormTemplate()
        parseAndGetFormDefAndFormFields(hashMap).let {defAndFields ->
            formTemplate.formDef = defAndFields.first
            formTemplate.formFieldsList = defAndFields.second
            formTemplate.formFieldsList.forEach {
                it.formChoiceList = it.formChoices
            }
        }
        return formTemplate
    }

    private suspend fun fetchFormFromCloudFunction(
        customerId: String,
        formID: Int,
        formClass : String
    ) : HashMap<String,Any> = coroutineScope {
        async(CoroutineName(tag)) {
            try {
                val defAndFields = getFormTemplateFromCloudFunction(
                    customerId,
                    formID.toString(),
                    formClass
                ).await()
                return@async defAndFields
            } catch (e: Exception) {
                Log.e(
                    tag, "Exception in fetchForm", e,
                    CUSTOMER_ID_KEY to customerId,
                    FORM_ID_KEY to formID
                )
            }
            return@async HashMap<String, Any>()
        }
    }.await()

    @Suppress("UNCHECKED_CAST")
    private fun getFormTemplateFromCloudFunction(
        customerId: String,
        formID: String, formClass: String
    ): Task<HashMap<String, Any>> {
        val request = HashMap<String, String>()
        request["customer_id"] = customerId
        request["form_id"] = formID
        request["form_class"] = formClass
        return FirebaseFunctions.getInstance(CF_REGION)
            .getHttpsCallable(FORM_TEMPLATE_CF_ENDPOINT)
            .call(request)
            .continueWith { task -> // This continuation runs on either success or failure, but if the task
                // has failed then getResult() will throw an Exception which will be
                // propagated down.
                task.result?.data as? HashMap<String, Any> ?: kotlin.run {
                    Log.e(tag, "Error in fetching form template from cloud function")
                    HashMap<String, Any>()
                }
            }
    }

    fun parseAndGetFormDefAndFormFields(
        responseMap: HashMap<String, Any>
    ): Pair<FormDef, ArrayList<FormField>> {
        var formDef = FormDef()
        var fieldList = ArrayList<FormField>()
        try {
            responseMap[FORM_DEF_KEY]?.let {
                formDef =
                    gson.fromJson(gson.toJson(it), FormDef::class.java)

            } ?: Log.e(tag, "FormDef in response map is null")
            responseMap[FORM_FIELDS_KEY]?.let {
                fieldList = gson.fromJson(
                    gson.toJson(it),
                    object : TypeToken<List<FormField>>() {}.type
                )
            } ?: Log.e(tag, "FormFields in response map in responseMap is null")
        }catch (e:Exception) {
            Log.e(tag, "Failed to form FormDefinition and FormField list fro cloud function response, Exception in parseAndGetFormDefAndFormFields", e)
        }
        return Pair(formDef, fieldList)
    }
}