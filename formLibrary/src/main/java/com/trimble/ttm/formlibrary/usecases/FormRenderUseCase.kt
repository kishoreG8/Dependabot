package com.trimble.ttm.formlibrary.usecases

import androidx.annotation.VisibleForTesting
import com.trimble.ttm.commons.model.DTFConditions
import com.trimble.ttm.commons.model.FormChoice
import com.trimble.ttm.commons.model.FormField
import com.trimble.ttm.commons.model.FormFieldType
import com.trimble.ttm.commons.model.FormTemplate
import com.trimble.ttm.commons.model.UIFormResponse
import com.trimble.ttm.commons.utils.Utils.fromJsonString
import com.trimble.ttm.commons.utils.Utils.toJsonString
import com.trimble.ttm.formlibrary.utils.EMPTY_STRING
import com.trimble.ttm.formlibrary.utils.FormUtils
import com.trimble.ttm.formlibrary.utils.ZERO
import com.trimble.ttm.formlibrary.utils.isEqualTo
import com.trimble.ttm.formlibrary.utils.isNotEqualTo
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import java.util.Stack

class FormRenderUseCase(private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main) {

    fun checkIfFormIsValid(formTemplate: FormTemplate): Boolean  {
        return formTemplate.formDef.cid >= ZERO && formTemplate.formFieldsList.isNotEmpty() }

    fun checkIfFormIsDtf(formTemplate: FormTemplate): Boolean =
        FormUtils.isDecisionTreeForm(formTemplate.formDef.userFdlScript ?: EMPTY_STRING)

    fun checkIfFormIsSaved(uiFormResponse: UIFormResponse): Boolean =
        uiFormResponse.isSyncDataToQueue

    fun serializeFormTemplate(formTemplate: FormTemplate): String =
        toJsonString(formTemplate) ?: EMPTY_STRING

    private fun getFormTemplateCopy(formTemplateSerializedString: String): FormTemplate =
        fromJsonString(formTemplateSerializedString) ?: FormTemplate()

    fun fetchFormTemplate(
        formTemplateSerializedString: String
    ): FormTemplate {
        return getFormTemplateCopy(formTemplateSerializedString)
    }

    private fun isAutoField(type: Int): Boolean {
        return type == FormFieldType.AUTO_DATE_TIME.ordinal || type == FormFieldType.AUTO_DRIVER_NAME.ordinal || type == FormFieldType.AUTO_VEHICLE_FUEL.ordinal || type == FormFieldType.AUTO_VEHICLE_LATLONG.ordinal || type == FormFieldType.AUTO_VEHICLE_LOCATION.ordinal || type == FormFieldType.AUTO_VEHICLE_ODOMETER.ordinal
    }

    fun isReplayWithNewFormType(driverFormId:Int,replyFormId:Int) : Boolean = driverFormId.isNotEqualTo(replyFormId)

    fun isReplayWithSameFormType(driverFormId:Int,replyFormId:Int) : Boolean = driverFormId.isEqualTo(replyFormId)

    fun countOfAutoFields(formFieldList: ArrayList<FormField>): Int =
        formFieldList.filter { isAutoField(it.qtype) }.size

    fun areAllAutoFields(formFieldSize: Int, autoFieldCount: Int): Boolean =
        formFieldSize > ZERO && formFieldSize == autoFieldCount

    fun getFormTemplateBasedOnBranchTargetId(
        branchTargetId: Int,
        loopEndId: Int,
        isDTF: Boolean,
        formTemplate: FormTemplate,
        formTemplateSerializedString: String,
        formFieldStack: Stack<FormField>
    ): FormTemplate {
        var formTemplateToRender = FormTemplate()
        when {
            isDTF.not() -> formTemplateToRender = formTemplate
            loopEndId != -1 -> {
                // Isolates the loop fields, add them to the formFieldList and increment the qNum on the new added and next fields
                getFormTemplateCopy(formTemplateSerializedString).formFieldsList.filter {
                    it.qnum in branchTargetId..loopEndId
                }.let { loopFieldsList ->
                    // Retrieves the end of the loop of the current form
                    val insertIndex = formTemplate.formFieldsList.indexOfLast {
                        it.qnum == loopEndId
                    }
                    insertIndex.let {
                        if (it >= 0) {
                            formTemplate.formFieldsList.addAll(it + 1, loopFieldsList)
                            formTemplateToRender = formTemplate
                        }
                    }
                }
            }
            else -> {
                formTemplate.formFieldsList.findLast { it.qnum <= branchTargetId - 1 }?.let {
                    if (it.qtype == FormFieldType.LOOP_END.ordinal && formFieldStack.isNotEmpty()) {
                        formFieldStack.pop()
                    }
                }
                formTemplateToRender = formTemplate
            }
        }
        return formTemplateToRender
    }

    fun getLastIndexOfBranchTargetId(formTemplate: FormTemplate, branchTargetId: Int): Int {
        return formTemplate.formFieldsList.indexOfLast {
            it.qnum == branchTargetId
        }
    }

    suspend fun processNextFieldToBeRendered(
        selectedFormChoice: FormChoice,
        formTemplate: FormTemplate,
        formFieldStack: Stack<FormField>,
        _renderValues: MutableStateFlow<DTFConditions>
    ) = withContext(mainDispatcher) {
        if (selectedFormChoice.branchTargetId != null) {
            val nextDTFConditions = getNextFieldToBeRenderedForFieldsOutsideTheLoops(
                selectedFormChoice = selectedFormChoice,
                formTemplate = formTemplate,
                formFieldStack = formFieldStack
            )
            if(isDTFConditionsAreValid(nextDTFConditions)) {
                _renderValues.emit(nextDTFConditions)
            } else {
                _renderValues.emit(
                    DTFConditions(
                        branchTargetId = selectedFormChoice.branchTargetId!!,
                        selectedViewId = selectedFormChoice.viewId,
                        loopEndId = -1,
                        actualLoopCount = -1,
                        currentLoopCount = -1
                    )
                )
            }
        } else {
            //check the field next to selected field is of type LOOP_END or not
            // if it is LOOP_END then check the stack not empty and check loopCount and decide to call renderForms with either stack.qNum+1 or it.qNum+1
            // else selectedFormChoice.qnum+1
            getNextFieldToRenderIfBranchTargetIsNull(
                selectedFormChoice = selectedFormChoice,
                formTemplate = formTemplate,
                formFieldStack = formFieldStack,
                _renderValues = _renderValues
            )
        }
    }

    suspend fun getNextFieldToRenderIfBranchTargetIsNull(
        selectedFormChoice: FormChoice,
        formTemplate: FormTemplate,
        _renderValues: MutableStateFlow<DTFConditions>,
        formFieldStack: Stack<FormField>
    ) {
        val nextFormField = FormUtils.getNextFormField(selectedFormChoice.qnum, formTemplate)
        if (nextFormField?.qtype != FormFieldType.LOOP_END.ordinal) {
            _renderValues.emit(
                DTFConditions(
                    branchTargetId = FormUtils.getNextQNum(
                        selectedFormChoice.qnum + 1,
                        formTemplate
                    ),
                    selectedViewId = selectedFormChoice.viewId,
                    loopEndId = -1,
                    actualLoopCount = formFieldStack.firstOrNull()?.actualLoopCount ?: -1,
                    currentLoopCount = formFieldStack.firstOrNull()?.loopcount ?: -1
                )
            )
        } else {
            val stackFormField = if (formFieldStack.isNotEmpty()) formFieldStack.peek() else null
            if (stackFormField?.loopcount != null && stackFormField.loopcount!! > 1) {
                val currentLoopCount = stackFormField.loopcount!!
                stackFormField.loopcount =
                    stackFormField.loopcount!! - 1

                _renderValues.emit(
                    DTFConditions(
                        branchTargetId = FormUtils.getNextQNum(
                            stackFormField.qnum + 1,
                            formTemplate
                        ),
                        selectedViewId = selectedFormChoice.viewId,
                        loopEndId = selectedFormChoice.qnum,
                        actualLoopCount = stackFormField.actualLoopCount!!,
                        currentLoopCount = currentLoopCount
                    )
                )
            } else {
                val nextDTFConditions = getNextFieldToBeRenderedForFieldsOutsideTheLoops(
                    selectedFormChoice = selectedFormChoice,
                    formTemplate = formTemplate,
                    formFieldStack = formFieldStack
                )
                if(isDTFConditionsAreValid(nextDTFConditions)) {
                    _renderValues.emit(nextDTFConditions)
                }
                 else {
                    _renderValues.emit(
                        DTFConditions(
                            branchTargetId = FormUtils.getNextQNum(
                                selectedFormChoice.qnum + 1,
                                formTemplate
                            ),
                            selectedViewId = selectedFormChoice.viewId,
                            loopEndId = -1,
                            actualLoopCount = -1,
                            currentLoopCount = -1
                        )
                    )
                }
            }
        }
    }

    fun processNextComposeFieldToBeRendered(
        selectedFormChoice: FormChoice,
        formTemplate: FormTemplate,
        formFieldStack: Stack<FormField>
    ): Triple<Int,Int,Int> {
        var renderTripleValue = Triple(-1,-1,-1)
        if (selectedFormChoice.branchTargetId != null) {
            renderTripleValue = Triple(
                selectedFormChoice.branchTargetId?: ZERO,
                selectedFormChoice.viewId,
                -1
            )
        } else {
            //check stack not empty and check loopCount and decide to call renderForms with either stack.qNum+1 or it.qNum+1
            if (formFieldStack.isNotEmpty()) {
                formFieldStack.peek().let { stackFormField ->
                    if (stackFormField.loopcount != null) {
                        if (stackFormField.loopcount!! > 1) {
                            stackFormField.loopcount =
                                stackFormField.loopcount!! - 1
                            renderTripleValue = Triple(
                                FormUtils.getNextQNum(
                                    stackFormField.qnum + 1,
                                    formTemplate
                                ),
                                selectedFormChoice.viewId,
                                selectedFormChoice.qnum
                            )
                        } else {
                            renderTripleValue = Triple(
                                FormUtils.getNextQNum(
                                    selectedFormChoice.qnum + 1,
                                    formTemplate
                                ),
                                selectedFormChoice.viewId,
                                -1
                            )
                        }
                    }
                }
            } else {
                //lets continue building the tree from the next available form choice
                renderTripleValue = Triple(
                    FormUtils.getNextQNum(
                        selectedFormChoice.qnum + 1,
                        formTemplate
                    ),
                    selectedFormChoice.viewId,
                    -1
                )
            }
        }
        return renderTripleValue
    }

    //We should not send AUTO_DATE_TIME AND AUTO_DRIVER_NAME fields to the server.
    fun constructFormFieldsListForDTFWithAutoFieldsToSend(formTemplate : FormTemplate, constructedFormFieldList : ArrayList<FormField>) =
        constructedFormFieldList.addAll(formTemplate.formFieldsList.filter { (it.qtype == FormFieldType.AUTO_VEHICLE_FUEL.ordinal
                || it.qtype == FormFieldType.AUTO_VEHICLE_LATLONG.ordinal || it.qtype == FormFieldType.AUTO_VEHICLE_LOCATION.ordinal
                || it.qtype == FormFieldType.AUTO_VEHICLE_ODOMETER.ordinal) })

    /*
       This method will return the DTF condition for the field which present outside the inner loop.
       This method will check If the stack have more than one formField then it will fetch the second formField from last and return the actual loop count
       Else it will return actual loop count as -1
     */
    fun getNextFieldToBeRenderedForFieldsOutsideTheLoops(
        selectedFormChoice: FormChoice,
        formTemplate: FormTemplate,
        formFieldStack: Stack<FormField>
    ): DTFConditions {
        if (formFieldStack.size == 1) {
           // find whether the selectedFormChoice.branchTargetId is greater than formTemplate.formFieldsList's loop end ordinal. if so end the loop
            val loopEndQNum =
                formTemplate.formFieldsList.find { it.qtype == FormFieldType.LOOP_END.ordinal && it.qnum > selectedFormChoice.qnum }?.qnum
                    ?: selectedFormChoice.qnum
            val loopEndId =
                if (selectedFormChoice.branchTargetId != null && selectedFormChoice.branchTargetId!! > loopEndQNum) -1 else selectedFormChoice.qnum
            return DTFConditions(
                selectedFormChoice.branchTargetId?: -1,
                selectedFormChoice.viewId,
                loopEndId,
                formFieldStack.firstOrNull()?.actualLoopCount ?: -1,
                formFieldStack.firstOrNull()?.loopcount ?: -1
            )
        }
        if (formFieldStack.size > 1) {
            val nextFormFieldToBeRendered = formFieldStack[formFieldStack.size - 2]
            return DTFConditions(
                FormUtils.getNextQNum(
                    selectedFormChoice.qnum + 1,
                    formTemplate
                ),
                selectedFormChoice.viewId,
                selectedFormChoice.qnum,
                nextFormFieldToBeRendered.actualLoopCount ?: -1,
                nextFormFieldToBeRendered.loopcount ?: -1
            )
        }
        return DTFConditions(-1, -1, -1, -1, -1)
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    // This method will check if the DTF conditions are valid or not
    fun isDTFConditionsAreValid(dtfConditions: DTFConditions) = dtfConditions.branchTargetId != -1 && dtfConditions.actualLoopCount != -1 && dtfConditions.currentLoopCount != -1 && dtfConditions.loopEndId != -1 && dtfConditions.selectedViewId != -1

}