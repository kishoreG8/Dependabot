package com.trimble.ttm.formlibrary.usecases

import com.trimble.ttm.commons.model.DTFConditions
import com.trimble.ttm.commons.model.FormChoice
import com.trimble.ttm.commons.model.FormDef
import com.trimble.ttm.commons.model.FormField
import com.trimble.ttm.commons.model.FormFieldType
import com.trimble.ttm.commons.model.FormTemplate
import com.trimble.ttm.commons.model.UIFormResponse
import com.trimble.ttm.formlibrary.utils.EMPTY_STRING
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.util.Stack
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FormRenderUsecaseTest {

    private lateinit var formRenderUseCase: FormRenderUseCase

    @Before
    fun setup() {
        formRenderUseCase = FormRenderUseCase()
    }


    @Test
    fun `check if form is valid`() {
        val formFieldList = ArrayList<FormField>()
        formFieldList.add(FormField(qnum = 1))
        var formTemplate =
            FormTemplate(formDef = FormDef(cid = 10119), formFieldsList = formFieldList)
        assertTrue(formRenderUseCase.checkIfFormIsValid(formTemplate = formTemplate))
        formTemplate = FormTemplate(formDef = FormDef(cid = -1), formFieldList)
        assertFalse(formRenderUseCase.checkIfFormIsValid(formTemplate = formTemplate))
        formTemplate = FormTemplate(formDef = FormDef(cid = 10119), ArrayList())
        assertFalse(formRenderUseCase.checkIfFormIsValid(formTemplate = formTemplate))
    }

    @Test
    fun `check if form is DTF`() {
        val userFdlScript1 =
            "form_header_info(\"DTF FORM Testing\",\"Use to enter information at consignee. PACOS only.\",can_send,can_send_force_urgent)\n" +
                    "field_text(\"ORDER NUMBER\",\"\",not_editable,optional)\n" +
                    "field_multiple_choice(\"DO YOU NEED TO POST-PONE UNLOADING?\",\"\",required,optional,\"Yes\"(\"End\"),\"No\")\n" +
                    "field_multiple_choice(\"DID YOU HOOK A TRAILER?\",\"\",required,optional,\"Yes\",\"No\"(\"LIVELOADTRAILER\"))\n" +
                    "field_text(\"TRAILER DROPPED:\",\"\",required,optional)\n" +
                    "field_text(\"TRAILER HOOKED:\",\"\",required,optional)\n" +
                    "branch_to(\"BILLS\")\n" +
                    "branch_target(\"LIVELOADTRAILER\")\n" +
                    "field_text(\"LIVE UNLOAD TRAILER:\",\"\",required,optional)\n" +
                    "branch_target(\"BILLS\")\n" +
                    "field_multiple_choice(\"WERE THE BILLS SIGNED CLEAN?\",\"\",required,optional,\"Yes\"(\"EMPTY\"),\"No\")\n" +
                    "field_multiple_choice(\"ALL PRODUCT REJECTED?\",\"\",required,optional,\"Yes\"(\"Call\"),\"No\")\n" +
                    "loopstart(25)\n" +
                    "field_text(\"ITEM NUMBER\",\"Driver indicates item\",required,optional)\n" +
                    "field_multiple_choice(\"OVER, SHORT, DAMAGE?\",\"\",required,optional,\"Over\",\"Short\",\"Damage\")\n" +
                    "field_multiple_choice(\"MORE ITEMS?\",\"\",required,optional,\"No\"(\"Call\"),\"Yes\")\n" +
                    "loopend\n" +
                    "branch_target(\"EMPTY\")\n" +
                    "field_multiple_choice(\"TRAILER EMPTY?\",\"\",required,optional,\"Yes\",\"No\"(\"ETA\"))\n" +
                    "field_multiple_choice(\"ARE YOU AVAILABLE FOR ANOTHER LOAD?\",\"\",required,optional,\"Yes\"(\"End\"),\"No\")\n" +
                    "field_date_and_time(\"WHAT IS YOUR PTA?\",\"\",optional,optional)\n" +
                    "branch_to(\"End\")\n" +
                    "branch_target(\"ETA\")\n" +
                    "field_date_and_time(\"ETA TO NEXT STOP DATE/TIME:\",\"\",required,optional)\n" +
                    "field_multiple_choice(\"TRAILER SEALED AND LOCKED?\",\"\",required,optional,\"Yes\",\"No\")\n" +
                    "branch_to(\"End\")\n" +
                    "branch_target(\"Call\")\n" +
                    "field_multiple_choice(\"CALL DISPATCH PRIOR TO MOVING\",\"\",required,optional,\"OK\")\n" +
                    "branch_target(\"End\")\n" +
                    "field_text(\"ANY FINAL REMARKS?\",\"\",optional,optional)\n" +
                    "field_text(\"EVENT\",\"\",not_editable,optional)\n" +
                    "field_text(\"EVENT DESC\",\"\",not_editable,optional)\n" +
                    "field_text(\"COMPANY ID\",\"\",not_editable,optional)\n" +
                    "field_text(\"COMPANY NAME\",\"\",not_editable,optional)\n" +
                    "field_number(\"STOP#\",\"\",not_editable,optional,-999999999999,999999999999,0,\"\",0,left-justify)\n" +
                    "field_number(\"SEGMENT#\",\"\",not_editable,optional,-999999999999,999999999999,0,\"\",0,left-justify)\n" +
                    "field_auto_location\n" +
                    "field_auto_latlong\n" +
                    "field_auto_datetime\n"
        val userFdlScript2 =
            "issubject(field_text(\"load number\",\"load number\",not_editable,optional))"
        assertTrue(
            formRenderUseCase.checkIfFormIsDtf(
                formTemplate = FormTemplate(
                    formDef = FormDef(
                        userFdlScript = userFdlScript1
                    )
                )
            )
        )
        assertFalse(
            formRenderUseCase.checkIfFormIsDtf(
                formTemplate = FormTemplate(
                    formDef = FormDef(
                        userFdlScript = userFdlScript2
                    )
                )
            )
        )
    }

    @Test
    fun `check if form is Saved`() {
        var uiFormResponse = UIFormResponse(isSyncDataToQueue = true)
        assertTrue(formRenderUseCase.checkIfFormIsSaved(uiFormResponse = uiFormResponse))
        uiFormResponse = UIFormResponse(isSyncDataToQueue = false)
        assertFalse(formRenderUseCase.checkIfFormIsSaved(uiFormResponse = uiFormResponse))
    }

    @Test
    fun `check if the formTemplate is serialized`() {
        val expectedResult =
            "{\"FormDef\":{\"cid\":-1,\"formid\":-1,\"name\":\"\",\"description\":\"\",\"formHash\":0,\"formClass\":-1,\"driverOriginate\":0,\"driverEditable\":0,\"recipients\":{}},\"FormFields\":[],\"error\":\"\",\"asn\":0}"
        assertEquals(
            expectedResult,
            formRenderUseCase.serializeFormTemplate(formTemplate = FormTemplate())
        )
    }


    @Test
    fun `check the count of autoField`() {
        val formFieldList = arrayListOf(
            FormField(qtype = FormFieldType.TEXT.ordinal),
            FormField(qtype = FormFieldType.AUTO_DRIVER_NAME.ordinal),
            FormField(qtype = FormFieldType.AUTO_VEHICLE_LOCATION.ordinal),
            FormField(qtype = FormFieldType.AUTO_VEHICLE_LATLONG.ordinal),
            FormField(qtype = FormFieldType.AUTO_VEHICLE_ODOMETER.ordinal),
            FormField(qtype = FormFieldType.AUTO_VEHICLE_FUEL.ordinal),
            FormField(qtype = FormFieldType.AUTO_DATE_TIME.ordinal),
            FormField(qtype = FormFieldType.AUTO_VEHICLE_FUEL.ordinal)
        )
        assertEquals(7,formRenderUseCase.countOfAutoFields(formFieldList = formFieldList))
        formFieldList.clear()
        formFieldList.add(FormField(qtype = FormFieldType.TEXT.ordinal))
        formFieldList.add(FormField(qtype = FormFieldType.MULTIPLE_CHOICE.ordinal))
        assertEquals(0,formRenderUseCase.countOfAutoFields(formFieldList = formFieldList))
    }

    @Test
    fun `check if all fields are auto`() {
        assertTrue(formRenderUseCase.areAllAutoFields(formFieldSize = 5, autoFieldCount = 5))
        assertFalse(formRenderUseCase.areAllAutoFields(formFieldSize = 0, autoFieldCount = 0))
        assertFalse(formRenderUseCase.areAllAutoFields(formFieldSize = 0, autoFieldCount = 3))
        assertFalse(formRenderUseCase.areAllAutoFields(formFieldSize = 3, autoFieldCount = 0))
    }

    @Test
    fun `check get template based on branch target id works as expected`() {
        val formFieldList = arrayListOf(
            FormField(qnum = 1),
            FormField(qnum = 2, qtype = FormFieldType.LOOP_END.ordinal),
            FormField(qnum = 3)
        )
        val formTemplate = FormTemplate(formFieldsList = formFieldList)
        assertEquals(
            formRenderUseCase.getFormTemplateBasedOnBranchTargetId(
                branchTargetId = 5,
                loopEndId = -1,
                isDTF = false,
                formTemplate = formTemplate,
                formTemplateSerializedString = EMPTY_STRING,
                formFieldStack = Stack<FormField>()
            ), formTemplate
        )
        val formTemplateSerializedString =
            "{\"FormDef\":{\"cid\":-1,\"formid\":-1,\"name\":\"\",\"description\":\"\",\"formHash\":0,\"formClass\":-1,\"driverOriginate\":0,\"recipients\":{}},\"FormFields\":[{\"qnum\":1,\"qtext\":\"\",\"qtype\":0,\"formid\":0,\"description\":\"\",\"fieldId\":0,\"required\":0,\"dispatchEditable\":0,\"driverEditable\":0,\"displayText\":\"\",\"bcBarCodeType\":0,\"bcMinLength\":0,\"bcMaxLength\":0,\"ffmLength\":2000,\"formChoices\":[],\"formChoiceList\":[],\"uiData\":\"\",\"errorMessage\":\"\",\"viewId\":-1,\"signViewHeight\":0,\"signViewWidth\":0,\"uniqueIdentifier\":\"\",\"isPartOfDTF\":false,\"isInDriverForm\":false,\"isSystemFormattable\":false},{\"qnum\":2,\"qtext\":\"\",\"qtype\":0,\"formid\":0,\"description\":\"\",\"fieldId\":0,\"required\":0,\"dispatchEditable\":0,\"driverEditable\":0,\"displayText\":\"\",\"bcBarCodeType\":0,\"bcMinLength\":0,\"bcMaxLength\":0,\"ffmLength\":2000,\"formChoices\":[],\"formChoiceList\":[],\"uiData\":\"\",\"errorMessage\":\"\",\"viewId\":-1,\"signViewHeight\":0,\"signViewWidth\":0,\"uniqueIdentifier\":\"\",\"isPartOfDTF\":false,\"isInDriverForm\":false,\"isSystemFormattable\":false},{\"qnum\":3,\"qtext\":\"\",\"qtype\":0,\"formid\":0,\"description\":\"\",\"fieldId\":0,\"required\":0,\"dispatchEditable\":0,\"driverEditable\":0,\"displayText\":\"\",\"bcBarCodeType\":0,\"bcMinLength\":0,\"bcMaxLength\":0,\"ffmLength\":2000,\"formChoices\":[],\"formChoiceList\":[],\"uiData\":\"\",\"errorMessage\":\"\",\"viewId\":-1,\"signViewHeight\":0,\"signViewWidth\":0,\"uniqueIdentifier\":\"\",\"isPartOfDTF\":false,\"isInDriverForm\":false,\"isSystemFormattable\":false},{\"qnum\":4,\"qtext\":\"\",\"qtype\":0,\"formid\":0,\"description\":\"\",\"fieldId\":0,\"required\":0,\"dispatchEditable\":0,\"driverEditable\":0,\"displayText\":\"\",\"bcBarCodeType\":0,\"bcMinLength\":0,\"bcMaxLength\":0,\"ffmLength\":2000,\"formChoices\":[],\"formChoiceList\":[],\"uiData\":\"\",\"errorMessage\":\"\",\"viewId\":-1,\"signViewHeight\":0,\"signViewWidth\":0,\"uniqueIdentifier\":\"\",\"isPartOfDTF\":false,\"isInDriverForm\":false,\"isSystemFormattable\":false}],\"error\":\"\"}"
        val result = formRenderUseCase.getFormTemplateBasedOnBranchTargetId(
            branchTargetId = 1,
            loopEndId = 3,
            isDTF = true,
            formTemplate = formTemplate,
            formTemplateSerializedString = formTemplateSerializedString,
            formFieldStack = Stack<FormField>()
        )
        assertEquals(6,result.formFieldsList.size)
        val stack = Stack<FormField>()
        stack.push(FormField(qnum = 2, qtype = FormFieldType.LOOP_END.ordinal))
        assertEquals(
            formTemplate,
            formRenderUseCase.getFormTemplateBasedOnBranchTargetId(
                branchTargetId = 3,
                loopEndId = -1,
                isDTF = true,
                formTemplate = formTemplate,
                formTemplateSerializedString = formTemplateSerializedString,
                formFieldStack = stack
            )
        )
    }

    @Test
    fun `check get last index of branch target id works as expected`() {
        val formFieldList = arrayListOf(
            FormField(qnum = 3),
            FormField(qnum = 4),
            FormField(qnum = 5),
            FormField(qnum = 3)
        )
        assertEquals(
            3,
            formRenderUseCase.getLastIndexOfBranchTargetId(
                formTemplate = FormTemplate(
                    formFieldsList = formFieldList
                ), branchTargetId = 3
            )
        )
        assertEquals(
            1,
            formRenderUseCase.getLastIndexOfBranchTargetId(
                formTemplate = FormTemplate(
                    formFieldsList = formFieldList
                ), branchTargetId = 4
            )
        )
    }

    @Test
    fun `check the driver formId and reply formId is of same type`(){
        assertTrue(formRenderUseCase.isReplayWithSameFormType(driverFormId = 1234, replyFormId = 1234))
        assertFalse(formRenderUseCase.isReplayWithSameFormType(driverFormId = 1234, replyFormId = 123))
    }

    @Test
    fun `check the driver formId and reply formId is of new type`(){
        assertTrue(formRenderUseCase.isReplayWithNewFormType(driverFormId = 123, replyFormId = 1234))
        assertFalse(formRenderUseCase.isReplayWithNewFormType(driverFormId = 1234, replyFormId = 1234))
    }
    @Test
    fun `verify auto fields are populated in the constructed form field list`(){
        val formFieldList = arrayListOf(
            FormField(qtype = FormFieldType.AUTO_DATE_TIME.ordinal),
            FormField(qtype = FormFieldType.AUTO_DRIVER_NAME.ordinal),
            FormField(qtype = FormFieldType.AUTO_VEHICLE_LOCATION.ordinal),
            FormField(qtype = FormFieldType.NUMERIC.ordinal),
            FormField(qtype = FormFieldType.AUTO_VEHICLE_LATLONG.ordinal),
            FormField(qtype = FormFieldType.AUTO_VEHICLE_ODOMETER.ordinal),
            FormField(qtype = FormFieldType.AUTO_VEHICLE_FUEL.ordinal),
            FormField(qtype = FormFieldType.TEXT.ordinal),
            FormField(qtype = FormFieldType.BARCODE_SCAN.ordinal)
        )

        val formFieldListFromDTFIteration = arrayListOf(
            FormField(qtype = FormFieldType.TEXT.ordinal),
            FormField(qtype = FormFieldType.NUMERIC.ordinal),
            FormField(qtype = FormFieldType.BARCODE_SCAN.ordinal)
        )

        val formTemplate = FormTemplate(formFieldsList = formFieldList)

        // Act
        formRenderUseCase.constructFormFieldsListForDTFWithAutoFieldsToSend(formTemplate, formFieldListFromDTFIteration)

        // Assert
        assertEquals(7, formFieldListFromDTFIteration.size)
        assertTrue(formFieldListFromDTFIteration.none { it.qtype == FormFieldType.AUTO_DRIVER_NAME.ordinal
                || it.qtype == FormFieldType.AUTO_DATE_TIME.ordinal })
    }

    @Test
    fun `verify when branchTargetId is not null`(){
        val formChoice = getFormChoice(branchTargetId = 2)
        formChoice.viewId = 1
        val expectedTriple = Triple(2, 1, -1)
        val actualTriple = formRenderUseCase.processNextComposeFieldToBeRendered(
            selectedFormChoice = formChoice,
            formTemplate = FormTemplate(),
            formFieldStack = Stack()
        )
        assert(
            expectedTriple.first == actualTriple.first && expectedTriple.second == actualTriple.second && expectedTriple.third == actualTriple.third
        )
    }

    @Test
    fun `verify when branchTargetId is null and loop count is greater more than 1 and formFieldStack is not empty`(){
        val expectedTriple = Triple(2, 1, 1)
        val formTemplate = getFormTemplate()
        val formFieldStack = getStackFieldWithLoopValue(3)
        val formChoice = getFormChoice(viewId = 1)
        val actualTriple = formRenderUseCase.processNextComposeFieldToBeRendered(
            selectedFormChoice = formChoice,
            formTemplate = formTemplate,
            formFieldStack = formFieldStack
        )
        assert(
            expectedTriple.first == actualTriple.first && expectedTriple.second == actualTriple.second && expectedTriple.third == actualTriple.third
        )
    }

    @Test
    fun `verify when branchTargetId is null and loop count is 1 and formFieldStack is not empty`(){
        val expectedTriple = Triple(2, 1, -1)
        val formTemplate = getFormTemplate()
        val formFieldStack = getStackFieldWithLoopValue()
        val formChoice = getFormChoice(viewId = 1)
        val actualTriple = formRenderUseCase.processNextComposeFieldToBeRendered(
            selectedFormChoice = formChoice,
            formTemplate = formTemplate,
            formFieldStack = formFieldStack
        )
        assert(
            expectedTriple.first == actualTriple.first && expectedTriple.second == expectedTriple.second && expectedTriple.third == actualTriple.third
        )
    }

    @Test
    fun `verify when branchTargetId is null and formFieldStack is empty`(){
        val expectedTriple = Triple(2,1,-1)
        val formTemplate = getFormTemplate()
        val formChoice = getFormChoice(viewId = 1)
        val actualTriple = formRenderUseCase.processNextComposeFieldToBeRendered(
            selectedFormChoice = formChoice,
            formTemplate = formTemplate,
            formFieldStack = Stack()
        )
        assert(
            expectedTriple.first == actualTriple.first && expectedTriple.second == actualTriple.second && expectedTriple.third == actualTriple.third
        )
    }

    @Test
    fun `assert fetchFormTemplate for correct data`() {
        val formTemplateSerializedString =
            "{\"FormDef\":{\"cid\":10,\"formid\":11,\"name\":\"\",\"description\":\"\",\"formHash\":0,\"formClass\":-1,\"driverOriginate\":0,\"recipients\":{}},\"FormFields\":[],\"error\":\"\"}"
        assertEquals(10, formRenderUseCase.fetchFormTemplate(formTemplateSerializedString).formDef.cid)
        assertEquals(11, formRenderUseCase.fetchFormTemplate(formTemplateSerializedString).formDef.formid)
    }

    @Test
    fun `assert fetchFormTemplate for invalid data`() {
        val formTemplateSerializedString = "gtftftttt"
        assertEquals(FormTemplate(), formRenderUseCase.fetchFormTemplate(formTemplateSerializedString))
    }

    @Test
    fun `verify if the DTF condition is valid`() {
        val dtfConditions = DTFConditions(
            branchTargetId = 3,
            selectedViewId = 2,
            loopEndId = 2,
            actualLoopCount = 4,
            currentLoopCount = -3
        )
        assertTrue(formRenderUseCase.isDTFConditionsAreValid(dtfConditions))
    }

    @Test
    fun `verify if the DTF condition not valid` () {
        val dtfConditions = DTFConditions(
            branchTargetId = -1,
            selectedViewId = -1,
            loopEndId = -1,
            actualLoopCount = -1,
            currentLoopCount = -1
        )
        assertFalse(formRenderUseCase.isDTFConditionsAreValid(dtfConditions))
    }


    private fun getFormChoice(
        branchTargetId: Int? = null,
        viewId: Int = 0,
        qNum: Int = 1,
        choiceNum: Int = 1,
        formId: Int = 1
    ): FormChoice {
        val formChoice = FormChoice(
            qNum,
            choiceNum,
            "",
            formId,
            branchTargetId
        )
        formChoice.viewId = viewId
        return formChoice
    }

    private fun getFormTemplate() = FormTemplate(
        formFieldsList = arrayListOf(
            FormField(
                qnum = 1
            ),
            FormField(
                qnum = 2
            ),
            FormField(
                qnum = 3
            ),
        )
    )

    private fun getStackFieldWithLoopValue(
        loopValue: Int = 1,
        viewId: Int = 1,
        qNum: Int = 1,
        actualLoopCount: Int = 1
    ): Stack<FormField> {
        val formFieldStack = Stack<FormField>()
        val formField = FormField(
            qnum = qNum,
            loopcount = loopValue
        )
        formField.viewId = viewId
        formField.actualLoopCount = actualLoopCount
        formFieldStack.push(
            formField
        )
        return formFieldStack
    }

    @Test
    fun `test getNextFieldToRenderIfBranchTargetIsNull when next form field is not LOOP_END`() =
        runTest {
            val formTemplate = getFormTemplate()
            val formFieldStack = Stack<FormField>()
            val selectedFormChoice = getFormChoice(viewId = 1, qNum = 1)
            val _renderValues = MutableStateFlow(DTFConditions(-1, -1, -1, -1, -1))

            formRenderUseCase.getNextFieldToRenderIfBranchTargetIsNull(
                selectedFormChoice = selectedFormChoice,
                formTemplate = formTemplate,
                formFieldStack = formFieldStack,
                _renderValues = _renderValues
            )

            val expected = DTFConditions(2, 1, -1, -1, -1)
            assertEquals(expected, _renderValues.value)
        }

    @Test
    fun `test getNextFieldToRenderIfBranchTargetIsNull when stack form field has loop count greater than 1`() =
        runTest {
            val formTemplate = getFormTemplate()
            val formFieldStack = getStackFieldWithLoopValue(loopValue = 2)
            val selectedFormChoice = getFormChoice(viewId = 1, qNum = 1)
            val _renderValues = MutableStateFlow(DTFConditions(-1, -1, -1, -1, -1))

            formRenderUseCase.getNextFieldToRenderIfBranchTargetIsNull(
                selectedFormChoice = selectedFormChoice,
                formTemplate = formTemplate,
                formFieldStack = formFieldStack,
                _renderValues = _renderValues
            )

            val expected = DTFConditions(2, 1, -1, 1, 2)
            assertEquals(expected, _renderValues.value)
        }

    @Test
    fun `test getNextFieldToRenderIfBranchTargetIsNull when stack form field has loop count of 1`() =
        runTest {
            val formTemplate = getFormTemplate()
            val formFieldStack = getStackFieldWithLoopValue(loopValue = 1)
            val selectedFormChoice = getFormChoice(viewId = 1, qNum = 1)
            val _renderValues = MutableStateFlow(DTFConditions(-1, -1, -1, -1, -1))

            formRenderUseCase.getNextFieldToRenderIfBranchTargetIsNull(
                selectedFormChoice = selectedFormChoice,
                formTemplate = formTemplate,
                formFieldStack = formFieldStack,
                _renderValues = _renderValues
            )

            val expected = DTFConditions(2, 1, -1, 1, 1)
            assertEquals(expected, _renderValues.value)
        }

    @Test
    fun `test getNextFieldToRenderIfBranchTargetIsNull when stack is empty`() = runTest {
        val formTemplate = getFormTemplate()
        val formFieldStack = Stack<FormField>()
        val selectedFormChoice = getFormChoice(viewId = 1, qNum = 1)
        val _renderValues = MutableStateFlow(DTFConditions(-1, -1, -1, -1, -1))

        formRenderUseCase.getNextFieldToRenderIfBranchTargetIsNull(
            selectedFormChoice = selectedFormChoice,
            formTemplate = formTemplate,
            formFieldStack = formFieldStack,
            _renderValues = _renderValues
        )

        val expected = DTFConditions(2, 1, -1, -1, -1)
        assertEquals(expected, _renderValues.value)
    }

    @Test
    fun `test getNextFieldToRenderIfBranchTargetIsNull when actualLoopCount is null`() =
        runTest {
            val formTemplate = getFormTemplate()
            val formFieldStack = Stack<FormField>().apply {
                push(FormField(qnum = 1))
            }
            val selectedFormChoice = getFormChoice(viewId = 1, qNum = 1)
            val _renderValues = MutableStateFlow(DTFConditions(-1, -1, -1, -1, -1))

            formRenderUseCase.getNextFieldToRenderIfBranchTargetIsNull(
                selectedFormChoice = selectedFormChoice,
                formTemplate = formTemplate,
                formFieldStack = formFieldStack,
                _renderValues = _renderValues
            )

            val expected = DTFConditions(2, 1, -1, -1, -1)
            assertEquals(expected, _renderValues.value)
        }

    @Test
    fun `test getNextFieldToBeRenderedForFieldsOutsideTheLoops when next form field is not LOOP_END`() =
        runTest {
            val formTemplate = getFormTemplate()
            val formFieldStack = Stack<FormField>()
            val selectedFormChoice = getFormChoice(viewId = 1, qNum = 1)
            val _renderValues = MutableStateFlow(DTFConditions(-1, -1, -1, -1, -1))

            formRenderUseCase.getNextFieldToBeRenderedForFieldsOutsideTheLoops(
                selectedFormChoice = selectedFormChoice,
                formTemplate = formTemplate,
                formFieldStack = formFieldStack
            )

            val expected = DTFConditions(-1, -1, -1, -1, -1)
            assertEquals(expected, _renderValues.value)
        }

    @Test
    fun `test getNextFieldToBeRenderedForFieldsOutsideTheLoops when stack form field has loop count greater than 1`() =
        runTest {
            val formTemplate = getFormTemplate()
            val formFieldStack = getStackFieldWithLoopValue(loopValue = 2, viewId = 1, qNum = 1, actualLoopCount = 5)
            val selectedFormChoice = getFormChoice(viewId = 1, qNum = 1, branchTargetId = 2)

            val actual = formRenderUseCase.getNextFieldToBeRenderedForFieldsOutsideTheLoops(
                selectedFormChoice = selectedFormChoice,
                formTemplate = formTemplate,
                formFieldStack = formFieldStack
            )

            val expected = DTFConditions(2, 1, -1, 5, 2)
            assertEquals(expected, actual)
        }

    @Test
    fun `test getNextFieldToBeRenderedForFieldsOutsideTheLoops when stack form field has loop count of 1`() =
        runTest {
            val formTemplate = getFormTemplate()
            val formFieldStack = getStackFieldWithLoopValue(loopValue = 1, viewId = 1, qNum = 1, actualLoopCount = 2)
            val selectedFormChoice = getFormChoice(viewId = 1, qNum = 1, branchTargetId = 17)

            val actual = formRenderUseCase.getNextFieldToBeRenderedForFieldsOutsideTheLoops(
                selectedFormChoice = selectedFormChoice,
                formTemplate = formTemplate,
                formFieldStack = formFieldStack
            )

            val expected = DTFConditions(17, 1, -1, 2, 1)
            assertEquals(expected, actual)
        }

    @Test
    fun `test getNextFieldToBeRenderedForFieldsOutsideTheLoops when stack is empty`() = runTest {
        val formTemplate = getFormTemplate()
        val formFieldStack = Stack<FormField>()
        val selectedFormChoice = getFormChoice(viewId = 1, qNum = 1)

        val actual = formRenderUseCase.getNextFieldToBeRenderedForFieldsOutsideTheLoops(
            selectedFormChoice = selectedFormChoice,
            formTemplate = formTemplate,
            formFieldStack = formFieldStack
        )

        val expected = DTFConditions(-1, -1, -1, -1, -1)
        assertEquals(expected, actual)
    }

    @Test
    fun `test getNextFieldToBeRenderedForFieldsOutsideTheLoops when formFieldStack size greater than 1`() =
        runTest {
            val formTemplate = getFormTemplate()
            val formFieldStack = Stack<FormField>().apply {
                push(FormField(qnum = 1, loopcount = 2))
                push(FormField(qnum = 2, loopcount = 3))
            }
            val selectedFormChoice = getFormChoice(viewId = 1, qNum = 1)

            val actual = formRenderUseCase.getNextFieldToBeRenderedForFieldsOutsideTheLoops(
                selectedFormChoice = selectedFormChoice,
                formTemplate = formTemplate,
                formFieldStack = formFieldStack
            )

            val expected = DTFConditions(2, 1, 1, -1, 2)
            assertEquals(expected, actual)
        }

    @Test
    fun `test getNextFieldToBeRenderedForFieldsOutsideTheLoops when formFieldStack firstOrNull is null`() =
        runTest {
            val formTemplate = getFormTemplate()
            val formFieldStack = Stack<FormField>()
            val selectedFormChoice = getFormChoice(viewId = 1, qNum = 1)

            val actual = formRenderUseCase.getNextFieldToBeRenderedForFieldsOutsideTheLoops(
                selectedFormChoice = selectedFormChoice,
                formTemplate = formTemplate,
                formFieldStack = formFieldStack
            )

            val expected = DTFConditions(-1, -1, -1, -1, -1)
            assertEquals(expected, actual)
        }

    @Test
    fun `test getNextFieldToBeRenderedForFieldsOutsideTheLoops when formFieldStack firstOrNull actualLoopCount is null`() =
        runTest {
            val formTemplate = getFormTemplate()
            val formFieldStack = Stack<FormField>().apply {
                push(FormField(qnum = 1, loopcount = null))
            }
            val selectedFormChoice = getFormChoice(viewId = 1, qNum = 1)

            val actual = formRenderUseCase.getNextFieldToBeRenderedForFieldsOutsideTheLoops(
                selectedFormChoice = selectedFormChoice,
                formTemplate = formTemplate,
                formFieldStack = formFieldStack
            )

            val expected = DTFConditions(-1, 1, 1, -1, -1)
            assertEquals(expected, actual)
        }

    @Test
    fun `test getNextFieldToBeRenderedForFieldsOutsideTheLoops when formFieldStack firstOrNull loopcount is null`() =
        runTest {
            val formTemplate = getFormTemplate()
            val formFieldStack = Stack<FormField>().apply {
                push(FormField(qnum = 1, loopcount = 2))
            }
            val selectedFormChoice = getFormChoice(viewId = 1, qNum = 1)

            val actual = formRenderUseCase.getNextFieldToBeRenderedForFieldsOutsideTheLoops(
                selectedFormChoice = selectedFormChoice,
                formTemplate = formTemplate,
                formFieldStack = formFieldStack
            )

            val expected = DTFConditions(-1, 1, 1, -1, 2)
            assertEquals(expected, actual)
        }
}