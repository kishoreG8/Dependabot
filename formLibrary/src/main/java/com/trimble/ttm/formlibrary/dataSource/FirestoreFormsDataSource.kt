package com.trimble.ttm.formlibrary.dataSource

import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import com.google.gson.Gson
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.model.FormChoice
import com.trimble.ttm.commons.model.FormDef
import com.trimble.ttm.commons.model.FormField
import com.trimble.ttm.commons.model.FormTemplate
import com.trimble.ttm.commons.utils.CUSTOMER_ID_KEY
import com.trimble.ttm.commons.utils.FORM_ID_KEY
import com.trimble.ttm.commons.utils.MULTIPLE_CHOICE_FIELD_TYPE
import com.trimble.ttm.formlibrary.utils.FORMS_COLLECTION
import com.trimble.ttm.formlibrary.utils.FORMS_LIST_COLLECTION
import com.trimble.ttm.formlibrary.utils.FORM_CHOICES_COLLECTION
import com.trimble.ttm.formlibrary.utils.FORM_FIELD_COLLECTION
import com.trimble.ttm.formlibrary.utils.FREE_FORMS_COLLECTION
import com.trimble.ttm.formlibrary.utils.FormUtils.getFormRecipients
import com.trimble.ttm.formlibrary.utils.PAYLOAD
import com.trimble.ttm.formlibrary.utils.ext.getFromCache
import com.trimble.ttm.formlibrary.utils.ext.getFromServer
import com.trimble.ttm.formlibrary.utils.ext.isCacheEmpty
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await

const val FORM_TEMPLATE_CF_ENDPOINT = "FormDefinition"
const val FREE_FORM_CLASS = "1"
const val NORMAL_FORM_CLASS = "0"
const val CF_REGION = "us-central1"
const val FORM_DEF_KEY = "FormDef"
const val FORM_FIELDS_KEY = "FormFields"
class FirestoreFormsDataSource(private val ioDispatcher : CoroutineDispatcher = Dispatchers.IO) : IFormsDataSource {

    private val tag = "FirestoreFormsDataSourceImpl"
    private val gson = Gson()

    override suspend fun getForm(customerId: String, formId: Int): FormTemplate {
        return fetchForm(customerId, formId)
    }

    override suspend fun getFreeForm(formId: Int): FormTemplate = coroutineScope {
        async(ioDispatcher + CoroutineName(tag)) {
            val formTemplate = FormTemplate()
            try {
                return@async getFormTemplateFromFirestoreSDK(formId)
            } catch (e: Exception) {
                Log.e(tag, "error retrieving free form id $formId ${e.stackTraceToString()}")
                return@async formTemplate
            }
        }
    }.await()

    private suspend fun fetchForm(
        customerId: String,
        formID: Int
    ): FormTemplate = coroutineScope {
        async(ioDispatcher + CoroutineName(tag)) {
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
                        CUSTOMER_ID_KEY to customerId,
                        FORM_ID_KEY to formID
                    )
                    val formDefDocument = documentReference.getFromServer().await()
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
                        CUSTOMER_ID_KEY to customerId,
                        FORM_ID_KEY to formID
                    )
                    val formDefDocument = documentReference.getFromCache().await()
                    formTemplate = getFormTemplate(formDefDocument, customerId)
                    Log.d(
                        tag,
                        "FormTemplate from cache - $formTemplate",
                        throwable = null,
                        "function" to "fetchForm",
                        "path" to documentReference.path
                    )
                }
            } catch (e: Exception) {
                Log.e(
                    tag, "Exception in fetchForm", e,
                    CUSTOMER_ID_KEY to customerId,
                    FORM_ID_KEY to formID
                )
            }
            return@async formTemplate
        }
    }.await()

    private fun getFormFieldFormChoiceCollectionReference(
        customerId: String,
        formField: FormField
    ) = getFormListCollectionReference(customerId)
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

    private fun getFormsFieldCollectionReference(customerId: String, formId: String) =
        getFormListCollectionReference(customerId)
            .document(formId).collection(FORM_FIELD_COLLECTION)

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
                        CUSTOMER_ID_KEY to customerId,
                        FORM_ID_KEY to formDef.formid
                    )
                    formFieldCollectionRef.getFromServer().await()
                } else {
                    Log.d(
                        tag, "Form fields found in cache",
                        throwable = null,
                        "path" to formFieldCollectionRef.path,
                        CUSTOMER_ID_KEY to customerId,
                        FORM_ID_KEY to formDef.formid
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
        } catch (e: Exception) {
            Log.e(
                tag, "Exception in getFormTemplate", e,
                CUSTOMER_ID_KEY to customerId
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

                if (formField.qtype != MULTIPLE_CHOICE_FIELD_TYPE)
                    formFieldList.add(formField)
                else {
                    // Fetching Form Choices of a FormField
                    fetchFormChoices(customerId, formField).let { processedFormField ->
                        formFieldList.add(processedFormField)
                    }
                }
            }
        }
    }

    //This is child job, so do not cancel the coroutineScope here upon child job completion.
    private suspend fun fetchFormChoices(
        customerId: String,
        formField: FormField
    ): FormField = coroutineScope {
        async(ioDispatcher + CoroutineName(tag)) {
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
                        CUSTOMER_ID_KEY to customerId,
                        FORM_ID_KEY to formField.formid,
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
                        CUSTOMER_ID_KEY to customerId,
                        FORM_ID_KEY to formField.formid,
                        "qnum" to formField.qnum
                    )
                    val formChoicesCollection = documentReference.getFromCache().await()
                    formField.formChoiceList =
                        getFormChoiceList(formChoicesCollection, formChoicesList)
                }
            } catch (e: Exception) {
                Log.e(
                    tag, "Exception in fetchFormChoices", e,
                    CUSTOMER_ID_KEY to customerId,
                    FORM_ID_KEY to formField.formid,
                    "qnum" to formField.qnum
                )
            }
            return@async formField
        }
    }.await()

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
            .mapTo(formChoicesList) { gson.fromJson(it, FormChoice::class.java) }
        return formChoicesList
    }

    private suspend fun getFormTemplateFromFirestoreSDK(
        formId: Int
    ): FormTemplate {
        var formTemplate = FormTemplate()
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
                formTemplate = FormTemplate(formDef, formFieldList)
            }
        }
        return formTemplate
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
}