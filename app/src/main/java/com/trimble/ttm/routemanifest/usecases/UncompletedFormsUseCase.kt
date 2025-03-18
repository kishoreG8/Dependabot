package com.trimble.ttm.routemanifest.usecases

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.model.DispatchFormPath
import com.trimble.ttm.commons.utils.Utils.fromJsonString

object UncompletedFormsUseCase {
    private const val tag = "UncompletedFormUseCase"
    fun addFormToPreference(
        formStack: String,
        dispatchFormPath: DispatchFormPath
    ): String {
        var formList = deserializeFormList(formStack)

        Log.i(tag, "FORM_STACK_KEY -> Before Adding Form to FormStack $formList")

        formList = addDispatchFormPathIfNotIncluded(formList, dispatchFormPath)

        Gson().toJson(formList.distinctBy { Pair(it.stopId, it.actionId) }).let { formListJson ->
            Log.i(tag, "FORM_STACK_KEY -> After Adding Form to FormStack $formListJson")
            return formListJson
        }
    }

    internal fun addDispatchFormPathIfNotIncluded(
        formList: ArrayList<DispatchFormPath>,
        dispatchFormPath: DispatchFormPath
    ): ArrayList<DispatchFormPath> {
        formList.filter { form -> form == dispatchFormPath }.let {
            if (it.isEmpty()) {
                formList.add(dispatchFormPath)
                Log.i(
                    tag,
                    "FORM_STACK_KEY -> Added Form's ActionId->${dispatchFormPath.actionId} FormClass->${dispatchFormPath.formClass} FormId->${dispatchFormPath.formId} StopId->${dispatchFormPath.stopId} StopName->${dispatchFormPath.stopName}"
                )
            }
        }
        return formList
    }

    fun deserializeFormList(formStack: String): ArrayList<DispatchFormPath> {
        var dispatchFormPathList = ArrayList<DispatchFormPath>()
        try {
            if (formStack.isEmpty()) return dispatchFormPathList
            fromJsonString<ArrayList<DispatchFormPath>>(formStack)?.let {
                dispatchFormPathList = it
            }
        } catch (e: Exception) {
            Log.e(tag, "Exception in deserializeFormList ${e.message}", e)
        }
        return dispatchFormPathList
    }

    fun removeForm(
        formStack: String,
        stopId: Int
    ): ArrayList<DispatchFormPath> {
        var formList: ArrayList<DispatchFormPath> = ArrayList()
        if (formStack.isNotEmpty())
            formList = Gson().fromJson(
                formStack,
                object : TypeToken<ArrayList<DispatchFormPath>>() {}.type
            )
        Log.i(tag, "FORM_STACK_KEY -> Before Removal of Form from FormStack $formStack")
        formList.find { form -> form.stopId == stopId }?.let {
            formList.remove(it)
            Log.i(
                tag,
                "FORM_STACK_KEY -> Removed Form's ActionId->${it.actionId} FormClass->${it.formClass} FormId->${it.formId} StopId->${it.stopId} StopName->${it.stopName}"
            )
        }
        Gson().toJson(formList).let { formListJson ->
            Log.i(tag, "FORM_STACK_KEY -> After Removal of Form from FormStack $formListJson")
        }
        return formList
    }
}