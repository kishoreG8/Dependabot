package com.trimble.ttm.routemanifest.repo

import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import com.google.gson.Gson
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.model.FormChoice
import com.trimble.ttm.commons.model.FormDef
import com.trimble.ttm.commons.model.FormField
import com.trimble.ttm.commons.model.FormResponse
import com.trimble.ttm.commons.model.FormTemplate
import com.trimble.ttm.commons.model.Recipients
import com.trimble.ttm.commons.model.UIFormResponse
import com.trimble.ttm.commons.utils.CUSTOMER_ID_KEY
import com.trimble.ttm.commons.utils.DISPATCH_COLLECTION
import com.trimble.ttm.commons.utils.DISPATCH_FORM_SAVE_PATH
import com.trimble.ttm.commons.utils.DRAFT_KEY
import com.trimble.ttm.commons.utils.FormUtils.getRecipient
import com.trimble.ttm.commons.utils.MULTIPLE_CHOICE_FIELD_TYPE
import com.trimble.ttm.commons.utils.SENT_KEY
import com.trimble.ttm.formlibrary.utils.EMPTY_STRING
import com.trimble.ttm.formlibrary.utils.FORMS_COLLECTION
import com.trimble.ttm.formlibrary.utils.FORMS_LIST_COLLECTION
import com.trimble.ttm.formlibrary.utils.FORM_CHOICES_COLLECTION
import com.trimble.ttm.formlibrary.utils.FORM_FIELD_COLLECTION
import com.trimble.ttm.formlibrary.utils.FORM_RESPONSES
import com.trimble.ttm.formlibrary.utils.FREE_FORMS_COLLECTION
import com.trimble.ttm.formlibrary.utils.FREE_FORM_FORM_CLASS
import com.trimble.ttm.formlibrary.utils.FormUtils.getFormRecipients
import com.trimble.ttm.formlibrary.utils.INBOX_FORM_DRAFT_RESPONSE_COLLECTION
import com.trimble.ttm.formlibrary.utils.INBOX_FORM_RESPONSE_COLLECTION
import com.trimble.ttm.formlibrary.utils.ext.getFromCache
import com.trimble.ttm.formlibrary.utils.ext.getFromServer
import com.trimble.ttm.formlibrary.utils.ext.isCacheEmpty
import com.trimble.ttm.formlibrary.utils.getCallbackFlow
import com.trimble.ttm.formlibrary.utils.isNull
import com.trimble.ttm.routemanifest.model.Action
import com.trimble.ttm.routemanifest.utils.INVALID_ACTION_ID
import com.trimble.ttm.routemanifest.utils.PAYLOAD
import com.trimble.ttm.routemanifest.viewmodel.ACTIONS
import com.trimble.ttm.routemanifest.viewmodel.STOPS
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class FormsRepositoryImpl(private val scope: CoroutineScope) : FormsRepository {

    private val tag = "FormsRepository"
    private val formIdLogKey = "form id"
    private val customerIdLogKey = "customer id"
    private val gson = Gson()
    private val formTemplateListFlowPair = getCallbackFlow<ArrayList<FormTemplate>>()
    val IS_FROM_SERVER = "isFromServer"

    override suspend fun formsSync(customerId: String, formDefList: ArrayList<FormDef>) {
        val formTemplateList = ArrayList<FormTemplate>()
        formDefList.forEach { formDef ->
            val documentReference: DocumentReference =
                if (formDef.formClass == FREE_FORM_FORM_CLASS)
                    getFreeFormsCollectionReference().document(formDef.formid.toString())
                else
                    getFormListCollectionReference(customerId).document(formDef.formid.toString())
            if (documentReference.isCacheEmpty()) {
                Log.d(
                    tag,
                    "data not found in cache formSync",
                    throwable = null,
                    "form" to formDef.formid
                )
                documentReference.getFromServer()
                    .addOnSuccessListener { formDocument ->
                        scope.launch(CoroutineName("$tag Forms Sync - get from server")) {
                            //Create FormTemplate from the data
                            createFormTemplate(customerId, formDocument, formTemplateList)
                        }
                    }
                    .addOnFailureListener {
                        Log.e(
                            tag, "form not found in server formSync", it,
                            "path" to documentReference.path
                        )
                    }
            } else {
                Log.d(tag, "data from cache formSync")
                documentReference.getFromCache()
                    .addOnSuccessListener { formDocument ->
                        scope.launch(CoroutineName("$tag Forms Sync - get form cache")) {
                            //Create FormTemplate from the data
                            createFormTemplate(customerId, formDocument, formTemplateList)
                        }
                    }
                    .addOnFailureListener {
                        Log.e(
                            tag, "form not found in cache formSync", it,
                            "path" to documentReference.path
                        )
                    }
            }
        }
    }

    private suspend fun createFormTemplate(
        customerId: String,
        formDocument: DocumentSnapshot,
        formTemplateList: ArrayList<FormTemplate>
    ) {
        if (formDocument.data.isNull()) {
            formTemplateList.add(
                FormTemplate(
                    FormDef(),
                    ArrayList()
                )
            )
            formTemplateListFlowPair.first.notify(formTemplateList)
        } else {
            formDocument.data?.getValue(PAYLOAD)?.let {
                val formDef: FormDef = gson.fromJson(
                    gson.toJson(it),
                    FormDef::class.java
                )
                val formFieldList = ArrayList<FormField>()
                //Fetch FormFields for this document
                val documentReference: CollectionReference =
                    if (formDef.formClass == FREE_FORM_FORM_CLASS)
                        getFreeFormsFormFieldCollectionReference(formDef.formid)
                    else getFormsFieldCollectionReference(customerId, formDef.formid.toString())
                if (documentReference.isCacheEmpty()) {
                    documentReference.getFromServer().let { querySnapshot ->
                        querySnapshot.addOnSuccessListener { formFieldDocuments ->
                            generateFormTemplateList(
                                formTemplateList,
                                formFieldDocuments,
                                formFieldList,
                                formDef,
                                mapOf(IS_FROM_SERVER to true, CUSTOMER_ID_KEY to customerId)
                            )
                        }.addOnFailureListener { e ->
                            Log.e(
                                tag,
                                "data not found in cache and server CreateFormTemplate",
                                throwable = null,
                                "stack" to e.stackTraceToString(),
                                "formid" to formDef.formid
                            )
                        }
                    }
                } else {
                    documentReference.getFromCache().let { querySnapshot ->
                        querySnapshot.addOnSuccessListener { formFieldDocuments ->
                            generateFormTemplateList(
                                formTemplateList,
                                formFieldDocuments,
                                formFieldList,
                                formDef,
                                mapOf(IS_FROM_SERVER to false, CUSTOMER_ID_KEY to customerId)
                            )
                        }.addOnFailureListener { e ->
                            Log.e(tag, "formField not found in cache CreateFormTemplate", e)
                        }
                    }
                }
            }
        }
    }

    private fun generateFormTemplateList(
        formTemplateList: ArrayList<FormTemplate>,
        formFieldDocuments: QuerySnapshot, formFieldList: ArrayList<FormField>,
        formDef: FormDef, fieldMap: Map<String, Any>
    ) {
        scope.launch(CoroutineName("$tag generate form template list")) {
            iterateFormFieldsDocuments(
                fieldMap[CUSTOMER_ID_KEY].toString(),
                formFieldDocuments,
                formFieldList
            )
            FormTemplate(
                formDef,
                formFieldList
            ).let { formTemplate ->
                formTemplateList.add(formTemplate)
                formTemplateListFlowPair.first.notify(formTemplateList)
                Log.d(tag, "${formDef.formid} template source is server : ${fieldMap[IS_FROM_SERVER]}")
                this.cancel()
            }
        }
    }

    private suspend fun iterateFormFieldsDocuments(
        customerId: String,
        formFieldDocuments: QuerySnapshot,
        formFieldList: ArrayList<FormField>
    ) {
        formFieldDocuments.forEach { formFieldDocument ->
            val formField = gson.fromJson(
                gson.toJson(
                    formFieldDocument?.data?.getValue(
                        PAYLOAD
                    )
                ), FormField::class.java
            )

            when (formField.qtype) {
                MULTIPLE_CHOICE_FIELD_TYPE -> fetchFormChoices(
                    customerId,
                    formField
                ).let { formFieldWithChoices ->
                    formFieldList.add(formFieldWithChoices)
                }
                else -> formFieldList.add(formField)
            }
        }
    }

    private suspend fun fetchFormChoices(
        customerId: String,
        formField: FormField
    ): FormField = coroutineScope {
        val formChoicesList = ArrayList<FormChoice>()
        try {
            val documentReference =
                getFormFieldFormChoiceCollectionReference(customerId, formField)
            if (documentReference.isCacheEmpty()) {
                Log.d(
                    tag, "Form choices not found in cache",
                    throwable = null,
                    "function" to "fetchFormChoices",
                    "path" to documentReference.path,
                    customerIdLogKey to customerId,
                    formIdLogKey to formField.formid,
                    "qnum" to formField.qnum
                )
                val formChoicesCollection = documentReference.getFromServer().await()
                formField.formChoiceList =
                    getFormChoiceList(formChoicesCollection, formChoicesList)
            } else {
                Log.d(
                    tag, "Form choices found in cache",
                    throwable = null,
                    "function" to "fetchFormChoices",
                    "path" to documentReference.path,
                    customerIdLogKey to customerId,
                    formIdLogKey to formField.formid,
                    "qnum" to formField.qnum
                )
                val formChoicesCollection = documentReference.getFromCache().await()
                formField.formChoiceList =
                    getFormChoiceList(formChoicesCollection, formChoicesList)
            }
        } catch (e: Exception) {
            Log.e(
                tag, "Exception in fetchFormChoices", e,
                customerIdLogKey to customerId,
                formIdLogKey to formField.formid,
                "qnum" to formField.qnum
            )
        }
        formField
    }

    private fun getFormChoiceList(
        formChoicesCollection: QuerySnapshot,
        formChoicesList: ArrayList<FormChoice>
    ): ArrayList<FormChoice> {
        formChoicesCollection.documents
            .asSequence()
            .map {
                gson.toJson(
                    it?.data?.getValue(
                        PAYLOAD
                    )
                )
            }
            .mapTo(formChoicesList) { gson.fromJson(it,FormChoice::class.java) }
        return formChoicesList
    }

    override suspend fun getForm(
        customerId: String,
        formID: Int
    ): FormTemplate = fetchForm(customerId, formID)

    private suspend fun fetchForm(customerId: String, formID: Int): FormTemplate = coroutineScope {
        var formTemplate = FormTemplate()
        try {
            val documentReference =
                getFormListCollectionReference(customerId).document(formID.toString())
            if (documentReference.isCacheEmpty()) {
                Log.d(
                    tag, "Form def not found in cache",
                    throwable = null,
                    "function" to "fetchForm",
                    "path" to documentReference.path,
                    customerIdLogKey to customerId,
                    formIdLogKey to formID
                )
                val formDefDocument = documentReference.getFromServer().await()
                /**
                 * A dispatch is allowed to be created with forced form id which is not available even in pfm in turn firestore. so returning here without firestore SDK call.
                We already checked cache empty so if it is not in server assume it is not available.
                 */
                if (formDefDocument.exists().not()) return@coroutineScope formTemplate
                formTemplate = getFormTemplate(formDefDocument, customerId)
                Log.d(
                    tag,
                    "FormTemplate from server - $formTemplate",
                    throwable = null,
                    "function" to "fetchForm",
                    "path" to documentReference.path
                )
            } else {
                Log.d(
                    tag, "Form def found in cache",
                    throwable = null,
                    "function" to "fetchForm",
                    "path" to documentReference.path,
                    customerIdLogKey to customerId,
                    formIdLogKey to formID
                )
                val formDefDocument = documentReference.getFromCache().await()
                if (formDefDocument.exists().not()) return@coroutineScope formTemplate
                formTemplate = getFormTemplate(formDefDocument, customerId)
                Log.d(
                    tag,
                    "FormTemplate from cache - $formTemplate",
                    throwable = null,
                    "function" to "fetchForm",
                    "path" to documentReference.path
                )
            }
            formTemplate
        } catch (e: CancellationException) {
            formTemplate
        } catch (e: Exception) {
            Log.e(
                tag, "Exception in fetchForm", e,
                customerIdLogKey to customerId,
                formIdLogKey to formID
            )
            formTemplate
        }
    }

    //Gets the latest recipient from server 1st, if server not available then fetches data from the cache
    override suspend fun getLatestFormRecipients(
        customerId: Int,
        formID: Int
    ): ArrayList<Recipients> {
        val recipientList: ArrayList<Recipients> = ArrayList()
        try {
            val documentReference =
                getFormListCollectionReference(customerId.toString()).document(formID.toString())
            val formDefDocument = documentReference.get().await()
            formDefDocument.data?.let {
                it.getFormRecipients().also { recipientsMap ->
                    if (recipientsMap.isNotEmpty()) {
                        recipientsMap.values.forEach { recipient ->
                            recipientList.add(recipient.getRecipient())
                        }
                    }
                }
            }
            return recipientList
        } catch (e: CancellationException) {
            return recipientList
        } catch (e: Exception) {
            Log.e(
                tag,
                "Exception in getLatestFormRecipients", e,
                customerIdLogKey to customerId,
                formIdLogKey to formID
            )
            return recipientList
        }
    }

    private suspend fun getFormTemplate(
        formDefDocument: DocumentSnapshot,
        customerId: String
    ): FormTemplate {
        try {
            val formDef = gson.fromJson(
                gson.toJson(formDefDocument.data?.getValue(PAYLOAD)),
                FormDef::class.java
            )
            // Form recipients
            formDefDocument.data?.let {
                formDef?.let { def ->
                    def.recipients = it.getFormRecipients()
                }
            }
            // Fetching FormFields
            val formFieldCollectionRef =
                getFormsFieldCollectionReference(customerId, formDef.formid.toString())
            val formFieldCollection: QuerySnapshot =
                if (formFieldCollectionRef.isCacheEmpty()) { // Check form field collection cache data availability
                    Log.d(
                        tag, "Form fields not found in cache",
                        throwable = null,
                        "path" to formFieldCollectionRef.path,
                        customerIdLogKey to customerId,
                        formIdLogKey to formDef.formid
                    )
                    formFieldCollectionRef.getFromServer().await()
                } else {
                    Log.d(
                        tag, "Form fields found in cache",
                        throwable = null,
                        "path" to formFieldCollectionRef.path,
                        customerIdLogKey to customerId,
                        formIdLogKey to formDef.formid
                    )
                    formFieldCollectionRef.getFromCache().await()
                }

            val formFieldList = ArrayList<FormField>()
            processFormFieldDocuments(
                customerId,
                formFieldCollection,
                formFieldList
            )
            return FormTemplate(formDef, formFieldList)
        } catch (e: CancellationException) {
            return FormTemplate()
        } catch (e: Exception) {
            Log.e(
                tag, "Exception in getFormTemplate", e,
                customerIdLogKey to customerId
            )
            return FormTemplate()
        }
    }

    private suspend fun processFormFieldDocuments(
        customerId: String,
        formFieldCollection: QuerySnapshot,
        formFieldList: ArrayList<FormField>
    ) {
        formFieldCollection.forEach {
            it?.let {
                val formField =
                    gson.fromJson(gson.toJson(it.data.getValue(PAYLOAD)), FormField::class.java)

                //Actual loop count of the field
                formField.actualLoopCount = formField.loopcount

                if (formField.qtype != MULTIPLE_CHOICE_FIELD_TYPE)
                    formFieldList.add(formField)
                else {
                    // Fetching Form Choices of a FormField
                    fetchFormChoices(customerId, formField).let { formChoice ->
                        formFieldList.add(formChoice)
                    }
                }
            }
        }
    }

    override suspend fun getFromFormResponses(
        path: String,
        actionId: String,
        shouldFetchFromServer: Boolean,
        caller: String
    ): Pair<UIFormResponse, Boolean> {
        val docReference: DocumentReference
        val formResponsePath = getFormResponsePath(path)
        try {
            docReference = FirebaseFirestore.getInstance().collection(formResponsePath).document(actionId)
            return if (docReference.isCacheEmpty()) {
                Log.d(tag, "Cache is Empty for $caller on querying $path")
                if (shouldFetchFromServer) {
                    Log.d(tag, "Fetching from server for $caller on querying $path")
                    val formDataDocument = docReference.getFromServer().await()
                    if (formDataDocument.exists()) {
                        Pair(getUiFormResponse(formDataDocument, true, caller), true)
                    }
                }
                Pair(UIFormResponse(), false)
            } else {
                val formDataDocument = docReference.getFromCache().await()
                return Pair(getUiFormResponse(formDataDocument, false, caller), true)
            }
        } catch (e: CancellationException) {
            Log.e(tag, "CancellationException in getFromFormResponses for $caller")
            throw e
        } catch (e: Exception) {
            Log.e(
                tag,
                "Exception in getSavedFormResponseFromDraftOrSent for $caller, on Getting Form Response collection ${e.message}"
            )
        }
        return Pair(UIFormResponse(), false)
    }

    override suspend fun getSavedFormResponseFromDraftOrSent(
        queryPath: String,
        shouldFetchFromServer: Boolean,
        caller: String
    ): Pair<UIFormResponse, Boolean> {
        val path = when (caller) {
            SENT_KEY -> getPFMFormResponsePath(queryPath)
            DRAFT_KEY -> getPfmDraftFormPath(queryPath)
            else -> EMPTY_STRING
        }

        try {
            Log.i(
                tag,
                "Querying the data for $caller getSavedFormResponse(path: $path & query: $queryPath)"
            )
            val documentReference = FirebaseFirestore.getInstance().collection(path)
                .whereEqualTo(DISPATCH_FORM_SAVE_PATH, queryPath)
            return if (documentReference.isCacheEmpty()) {
                Log.d(
                    tag,
                    "data not found in cache for $caller getSavedFormResponse(path: $path & query: $queryPath)"
                )
                if (shouldFetchFromServer) {
                    Log.d(
                        tag,
                        "fetching getSavedFormResponse for $caller (path: $path & query: $queryPath) from server"
                    )
                    val formDataDocument = documentReference.getFromServer().await()
                    if (formDataDocument.documents.isNotEmpty()) {
                        Pair(
                            getUiFormResponse(formDataDocument.documents.last(), true, caller),
                            true
                        )
                    }
                }
                Pair(UIFormResponse(), false)
            } else {
                val formDataDocument = documentReference.getFromCache().await()
                Pair(getUiFormResponse(formDataDocument.documents.last(), false, caller), true)
            }
        } catch (e: CancellationException) {
            Log.e(tag, "CancellationException in getSavedFormResponseFromDraftOrSent for $caller")
            throw e
        } catch (e: Exception) {
            Log.e(
                tag,
                "Exception in getSavedFormResponseFromDraftOrSent for $caller, Error: ${e.message}",
                throwable = null,
                "stack" to e.stackTraceToString()
            )
        }
        return Pair(UIFormResponse(), false)
    }

    @Suppress("UNCHECKED_CAST")
    private fun getUiFormResponse(
        formDataDocument: DocumentSnapshot,
        isCacheEmpty: Boolean,
        caller: String
    ): UIFormResponse {
        if (formDataDocument.data.isNull()) {
            Log.d(
                tag,
                "Data not found for getSavedFormResponse(path: ${formDataDocument.reference.path}) from source isCacheEmpty: $isCacheEmpty"
            )
            return UIFormResponse()
        }
        formDataDocument.data?.let {
            return if (it.isNotEmpty()) {
                try {
                    val isSyncDataToQueue: Boolean = checkIsSyncDataToQueue(it, caller)
                    val formDataMap: HashMap<String, Any> =
                        it["formData"] as HashMap<String, Any>
                    val formResponse: FormResponse =
                        gson.fromJson(gson.toJson(formDataMap), FormResponse::class.java)
                    UIFormResponse(
                        isSyncDataToQueue,
                        formResponse
                    )
                } catch (cancelException: CancellationException) {
                    UIFormResponse()
                } catch (e: Exception) {
                    Log.e(
                        tag,
                        "exception in getSavedFormResponse Error: ${e.message}",
                        throwable = null,
                        "stack" to e.stackTraceToString()
                    )
                    UIFormResponse()
                }
            } else UIFormResponse()
        }
        return UIFormResponse()
    }

    private fun checkIsSyncDataToQueue(data: Map<String,Any>, caller: String): Boolean {
        return when(caller) {
            DRAFT_KEY -> false
            SENT_KEY  -> true
            FORM_RESPONSES -> data["isSyncDataToQueue"] as Boolean
            else -> false
        }.also {
            Log.d(tag, "isSyncDataToQueue is $it for $caller")
        }
    }

    override suspend fun getActionForStop(
        vehicleId: String,
        cid: String,
        dispatchId: String,
        stopId: String,
        actionId: String
    ): Action {
        try {
            val documentReference = FirebaseFirestore.getInstance()
                .collection(DISPATCH_COLLECTION).document(cid)
                .collection(vehicleId).document(dispatchId)
                .collection(STOPS).document(stopId)
                .collection(ACTIONS)
            val stopActionDocChangeList: List<DocumentChange> =
                if (documentReference.isCacheEmpty()) {
                    Log.d(tag, "data from server getActionForStop(path: ${documentReference.path})")
                    documentReference.getFromServer().await().documentChanges
                } else {
                    Log.d(tag, "data from cache getActionForStop(path: ${documentReference.path})")
                    documentReference.getFromCache().await().documentChanges
                }
            val actionIdInt = actionId.toIntOrNull()
            if (actionIdInt != null && actionIdInt < stopActionDocChangeList.size) {
                val stopActionDocChange = stopActionDocChangeList[actionIdInt]
                return Gson().fromJson(
                    Gson().toJson(stopActionDocChange.document[PAYLOAD]),
                    Action::class.java
                )
            } else {
                Log.w(
                    tag, "Action id is invalid",
                    throwable = null,
                    "vehicle id" to vehicleId,
                    "company id" to cid,
                    "dispatch id" to dispatchId,
                    "stop id" to stopId,
                    "action id" to actionId,
                    "actions size" to stopActionDocChangeList.size
                )
                return Action(actionid = INVALID_ACTION_ID)
            }
        } catch (e: CancellationException) {
            return Action(actionid = INVALID_ACTION_ID)
        } catch (e: Exception) {
            Log.e(
                tag, "Exception occurred while fetching stop action.", e,
                "vehicle id" to vehicleId,
                "company id" to cid,
                "dispatch id" to dispatchId,
                "stop id" to stopId,
                "action id" to actionId
            )
            return Action(actionid = INVALID_ACTION_ID)
        }
    }

    override fun getFormsTemplateListFlow(): Flow<ArrayList<FormTemplate>> =
        formTemplateListFlowPair.second

    private fun getFormsFieldCollectionReference(customerId: String, formId: String) =
        getFormListCollectionReference(customerId)
            .document(formId).collection(FORM_FIELD_COLLECTION)

    private fun getFormFieldFormChoiceCollectionReference(
        customerId: String,
        formField: FormField
    ) =
        getFormListCollectionReference(customerId)
            .document(formField.formid.toString()).collection(FORM_FIELD_COLLECTION).document(
                formField.qnum.toString()
            ).collection(
                FORM_CHOICES_COLLECTION
            )

    private fun getFormListCollectionReference(customerId: String): CollectionReference {
        return FirebaseFirestore.getInstance().collection(FORMS_COLLECTION).document(customerId)
            .collection(
                FORMS_LIST_COLLECTION
            )
    }

    override suspend fun getFreeForm(formId: Int): FormTemplate {
        return try {
            val freeFormFormDefDocumentData = getFreeFormFormDefDocumentData(formId)
            freeFormFormDefDocumentData.first.let { docSnapshot ->
                if (docSnapshot.exists()) {
                    var formDef = FormDef()
                    docSnapshot.data?.let {
                        formDef = gson.fromJson(
                            gson.toJson(it.getValue(PAYLOAD)),
                            FormDef::class.java
                        )
                    }
                    val freeFormFormFieldQuerySnapshot = getFreeFormFormFieldSubCollection(
                        formId,
                        freeFormFormDefDocumentData.second
                    )
                    val formFieldList = ArrayList<FormField>()
                    freeFormFormFieldQuerySnapshot.forEach { queryDocSnapshot ->
                        queryDocSnapshot?.let {
                            val formField =
                                gson.fromJson(
                                    gson.toJson(it.data.getValue(PAYLOAD)),
                                    FormField::class.java
                                )
                            if (formField.qtype != MULTIPLE_CHOICE_FIELD_TYPE)
                                formFieldList.add(formField)
                        }
                    }
                    FormTemplate(formDef, formFieldList)
                } else {
                    // Form does not exists in FreeForms collection
                    FormTemplate()
                }
            }
        } catch (e: CancellationException) {
            FormTemplate()
        } catch (e: Exception) {
            Log.e(tag, "error retrieving free form id $formId ${e.stackTraceToString()}")
            FormTemplate()
        }
    }

    private suspend fun getFreeFormFormDefDocumentData(formId: Int): Pair<DocumentSnapshot, Boolean> {
        val documentReference = getFreeFormsCollectionReference().document(formId.toString())
        val documentSnapshotTask: Task<DocumentSnapshot>
        val isCacheEmpty = documentReference.isCacheEmpty()
        documentSnapshotTask = if (isCacheEmpty) {
            documentReference.getFromServer()
        } else {
            documentReference.getFromCache()
        }
        val documentSnapshot = documentSnapshotTask.await()
        return Pair(documentSnapshot, isCacheEmpty)
    }

    private suspend fun getFreeFormFormFieldSubCollection(
        formId: Int,
        isCacheEmpty: Boolean
    ): QuerySnapshot {
        val formFieldCollectionQuerySnapshotTask = if (isCacheEmpty) {
            getFreeFormsFormFieldCollectionReference(formId).getFromServer()
        } else {
            getFreeFormsFormFieldCollectionReference(formId).getFromCache()
        }
        return formFieldCollectionQuerySnapshotTask.await()
    }

    private fun getFreeFormsCollectionReference(): CollectionReference =
        FirebaseFirestore.getInstance().collection(FREE_FORMS_COLLECTION)

    private fun getFreeFormsFormFieldCollectionReference(formId: Int): CollectionReference =
        FirebaseFirestore.getInstance().collection(FREE_FORMS_COLLECTION)
            .document(formId.toString())
            .collection(FORM_FIELD_COLLECTION)

    //PFMFormResponses/CID/VID
    private fun getPFMFormResponsePath(path: String): String {
        val pathArray = path.split("/")
        return if (pathArray.size > 2) "$INBOX_FORM_RESPONSE_COLLECTION/${pathArray[1]}/${pathArray[2]}" else EMPTY_STRING
    }

    //PFMFormDraftResponses/CID/VID
    private fun getPfmDraftFormPath(path: String): String {
        val pathArray = path.split("/")
        return if (pathArray.size > 2) "$INBOX_FORM_DRAFT_RESPONSE_COLLECTION/${pathArray[1]}/${pathArray[2]}" else EMPTY_STRING
    }

    //Received Path - PFMFormResponses/CID/VID/DISPID/SID/AID
    //Returned Path - FormResponses/CID/VID/DISPID/SID
    private fun getFormResponsePath(path: String): String {
        val formResponsePath = path.replace(INBOX_FORM_RESPONSE_COLLECTION, FORM_RESPONSES).substringBeforeLast("/")
        return formResponsePath
    }

}