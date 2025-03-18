package com.trimble.ttm.routemanifest.repo

import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.QuerySnapshot
import com.google.gson.Gson
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.model.FormField
import com.trimble.ttm.commons.utils.MULTIPLE_CHOICE_FIELD_TYPE
import com.trimble.ttm.formlibrary.model.EDVIRPayload
import com.trimble.ttm.formlibrary.utils.EDVIR_COLLECTION_ID
import com.trimble.ttm.formlibrary.utils.EDVIR_DOT_TRIP_SETTINGS_DOCUMENT_ID
import com.trimble.ttm.formlibrary.utils.EDVIR_ENABLED_SETTINGS_DOCUMENT_ID
import com.trimble.ttm.formlibrary.utils.EDVIR_INTER_TRIP_SETTINGS_DOCUMENT_ID
import com.trimble.ttm.formlibrary.utils.EDVIR_MANDATORY_SETTINGS_DOCUMENT_ID
import com.trimble.ttm.formlibrary.utils.EDVIR_POST_TRIP_SETTINGS_DOCUMENT_ID
import com.trimble.ttm.formlibrary.utils.EDVIR_PRE_TRIP_SETTINGS_DOCUMENT_ID
import com.trimble.ttm.formlibrary.utils.FORMS_COLLECTION
import com.trimble.ttm.formlibrary.utils.FORMS_LIST_COLLECTION
import com.trimble.ttm.formlibrary.utils.FORM_CHOICES_COLLECTION
import com.trimble.ttm.formlibrary.utils.FORM_FIELD_COLLECTION
import com.trimble.ttm.formlibrary.utils.FREE_FORMS_COLLECTION
import com.trimble.ttm.formlibrary.utils.FREE_FORM_FORM_CLASS
import com.trimble.ttm.routemanifest.viewmodel.PAYLOAD
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class FireStoreCacheRepositoryImpl(private val firebaseFirestore: FirebaseFirestore = FirebaseFirestore.getInstance()) : FireStoreCacheRepository {
    private val tag = "FireStoreCacheRepo"
    private val formFieldsCollectionPathLogKey = "FormFields collection path"
    private var edvirPreTripListenerRegistration: ListenerRegistration? = null
    private var edvirPostTripListenerRegistration: ListenerRegistration? = null
    private var edvirInterTripListenerRegistration: ListenerRegistration? = null
    private var edvirDotTripListenerRegistration: ListenerRegistration? = null
    private var edvirEnabledListenerRegistration: ListenerRegistration? = null
    private var edvirMandatoryListenerRegistration: ListenerRegistration? = null

    override fun addSnapshotListenerForEDVIRSetting(
        cid: String,
        dsn: String,
        settingsDocumentId: String
    ): Flow<EDVIRPayload>? {
        val settingsDocRef = getEDVIRCollectionRef(cid, dsn, settingsDocumentId)
        return when (settingsDocumentId) {
            EDVIR_PRE_TRIP_SETTINGS_DOCUMENT_ID -> {
                edvirPreTripListenerRegistration?.remove()
                addSnapshotListenerForEDVIRPreTripSetting(settingsDocRef)
            }
            EDVIR_POST_TRIP_SETTINGS_DOCUMENT_ID -> {
                edvirPostTripListenerRegistration?.remove()
                addSnapshotListenerForEDVIRPostTripSetting(settingsDocRef)
            }
            EDVIR_INTER_TRIP_SETTINGS_DOCUMENT_ID -> {
                edvirInterTripListenerRegistration?.remove()
                addSnapshotListenerForEDVIRInterTripSetting(settingsDocRef)
            }
            EDVIR_DOT_TRIP_SETTINGS_DOCUMENT_ID -> {
                edvirDotTripListenerRegistration?.remove()
                addSnapshotListenerForEDVIRDOTTripSetting(settingsDocRef)
            }
            EDVIR_ENABLED_SETTINGS_DOCUMENT_ID -> {
                edvirEnabledListenerRegistration?.remove()
                addSnapshotListenerForEDVIREnabledSetting(settingsDocRef)
            }
            EDVIR_MANDATORY_SETTINGS_DOCUMENT_ID -> {
                edvirMandatoryListenerRegistration?.remove()
                addSnapshotListenerForEDVIRMandatorySetting(settingsDocRef)
            }

            else -> null
        }
    }

    private fun getEDVIRCollectionRef(
        cid: String,
        dsn: String,
        settingsDocumentId: String
    ): DocumentReference {
        return firebaseFirestore
            .collection(EDVIR_COLLECTION_ID)
            .document(cid)
            .collection(dsn)
            .document(settingsDocumentId)
    }

    private fun addSnapshotListenerForEDVIRPreTripSetting(
        docRef: DocumentReference
    ): Flow<EDVIRPayload> {
        return callbackFlow {
            edvirPreTripListenerRegistration =
                docRef.addSnapshotListener { docSnapshot, exception ->
                    processEDVIRListenerResults(docSnapshot, exception).let {
                        this.trySend(it)
                    }
                }
            awaitClose {
                edvirPreTripListenerRegistration?.remove()
            }
        }
    }

    private fun addSnapshotListenerForEDVIRPostTripSetting(
        docRef: DocumentReference
    ): Flow<EDVIRPayload> {
        return callbackFlow {
            edvirPostTripListenerRegistration =
                docRef.addSnapshotListener { docSnapshot, exception ->
                    processEDVIRListenerResults(docSnapshot, exception).let {
                        this.trySend(it)
                    }
                }
            awaitClose {
                edvirPostTripListenerRegistration?.remove()
            }
        }
    }

    private fun addSnapshotListenerForEDVIRInterTripSetting(
        docRef: DocumentReference
    ): Flow<EDVIRPayload> {
        return callbackFlow {
            edvirInterTripListenerRegistration =
                docRef.addSnapshotListener { docSnapshot, exception ->
                    processEDVIRListenerResults(docSnapshot, exception).let {
                        this.trySend(it)
                    }
                }
            awaitClose {
                edvirInterTripListenerRegistration?.remove()
            }
        }
    }

    private fun addSnapshotListenerForEDVIRDOTTripSetting(
        docRef: DocumentReference
    ): Flow<EDVIRPayload> {
        return callbackFlow {
            edvirDotTripListenerRegistration =
                docRef.addSnapshotListener { docSnapshot, exception ->
                    processEDVIRListenerResults(docSnapshot, exception).let {
                        this.trySend(it)
                    }
                }
            awaitClose {
                edvirDotTripListenerRegistration?.remove()
            }
        }
    }

    private fun addSnapshotListenerForEDVIREnabledSetting(
        docRef: DocumentReference
    ): Flow<EDVIRPayload> {
        return callbackFlow {
            edvirEnabledListenerRegistration =
                docRef.addSnapshotListener { docSnapshot, exception ->
                    processEDVIRListenerResults(docSnapshot, exception).let {
                        this.trySend(it)
                    }
                }
            awaitClose {
                edvirEnabledListenerRegistration?.remove()
            }
        }
    }

    private fun addSnapshotListenerForEDVIRMandatorySetting(
        docRef: DocumentReference
    ): Flow<EDVIRPayload> {
        return callbackFlow {
            edvirMandatoryListenerRegistration =
                docRef.addSnapshotListener { docSnapshot, exception ->
                    processEDVIRListenerResults(docSnapshot, exception).let {
                        this.trySend(it)
                    }
                }
            awaitClose {
                edvirMandatoryListenerRegistration?.remove()
            }
        }
    }

    private fun processEDVIRListenerResults(
        docSnapshot: DocumentSnapshot?,
        exception: FirebaseFirestoreException?
    ): EDVIRPayload {
        return if (exception != null) {
            Log.i(
                tag,
                "Firebase firestore exception getting EDVIR doc snapshot ${exception.message}"
            )
            EDVIRPayload()
        } else {
            try {
                if (docSnapshot != null && docSnapshot.exists()) {
                    Gson().fromJson(
                        Gson().toJson(docSnapshot[PAYLOAD]),
                        EDVIRPayload::class.java
                    )
                } else {
                    EDVIRPayload()
                }
            } catch (e: Exception) {
                Log.i(
                    tag,
                    "Exception process EDVIR doc snapshot result ${e.message}"
                )
                EDVIRPayload()
            }
        }
    }

    override suspend fun syncFormData(
        cid: String,
        formId: String,
        formClass: Int
    ) {
        val formDefCollRef = if (formClass.isFreeForm()) getFreeFormCollectionReference()
        else getDispatchFormCollectionReference(cid)
        return suspendCoroutine { continuation ->
            fetchFormDef(formDefCollRef, formId) {
                fetchFormFields(
                    formDefCollRef.document(formId).collection(FORM_FIELD_COLLECTION),
                    formId
                ) { formFieldSnapshots ->
                    formFieldSnapshots.documents.forEach { formFieldDocSnapshot ->
                        if (formFieldDocSnapshot != null && formFieldDocSnapshot.exists()) {
                            val gson = Gson()
                            val formField = gson.fromJson(
                                gson.toJson(formFieldDocSnapshot.data?.getValue(PAYLOAD)),
                                FormField::class.java
                            )
                            when (formField.qtype) {
                                MULTIPLE_CHOICE_FIELD_TYPE -> {
                                    processMultipleChoiceField(
                                        formId,
                                        formDefCollRef,
                                        formField
                                    )
                                }
                            }
                        } else Log.e(
                            tag,
                            "FormField document not exists for form $formId"
                        )
                    }
                    continuation.resume(Unit)
                }
            }
        }
    }

    private fun processMultipleChoiceField(
        formId: String,
        formDefCollRef: CollectionReference,
        formField: FormField
    ) {
        fetchFormChoices(
            formId,
            formDefCollRef.document(formId)
                .collection(FORM_FIELD_COLLECTION),
            formField.qnum.toString()
        ) {
            //Do nothing since we only caching form choices
        }
    }

    private fun fetchFormDef(
        formDefCollRef: CollectionReference,
        formId: String,
        onFormDefDocSnapshot: (formDefDocSnapshot: DocumentSnapshot) -> Unit
    ) {
        val formDefDocRef = formDefCollRef.document(formId)
        formDefDocRef.get()
            .addOnSuccessListener { formDefDocSnapshot ->
                if (formDefDocSnapshot != null && formDefDocSnapshot.exists()) {
                    onFormDefDocSnapshot(formDefDocSnapshot)
                } else Log.e(
                    tag, "FormDef document not exists for form id $formId", null,
                    "FormDef document path" to formDefDocRef.path
                )
            }
            .addOnFailureListener { exception ->
                Log.e(
                    tag,
                    "Error fetching FormDef document ${exception.message}",
                    exception,
                    "FormDef path" to formDefDocRef.path
                )
            }
    }

    private fun fetchFormFields(
        formFieldsCollRef: CollectionReference,
        formId: String,
        onFormFieldsQuerySnapshot: (formFieldSnapshots: QuerySnapshot) -> Unit
    ) {
        formFieldsCollRef.get()
            .addOnSuccessListener { formFieldSnapshots ->
                if (formFieldSnapshots != null && formFieldSnapshots.isEmpty.not()) {
                    onFormFieldsQuerySnapshot(formFieldSnapshots)
                } else Log.e(
                    tag, "FormFields collection is empty for form id $formId",
                    null,
                    formFieldsCollectionPathLogKey to formFieldsCollRef.path
                )
            }
            .addOnFailureListener { exception ->
                Log.e(
                    tag,
                    "Error fetching FormFields collection ${exception.message}",
                    exception,
                    formFieldsCollectionPathLogKey to formFieldsCollRef.path
                )
            }
    }

    private fun fetchFormChoices(
        formId: String,
        formFieldsCollRef: CollectionReference,
        questionNum: String, onFormChoicesSnapshots: (formChoicesSnapshots: QuerySnapshot) -> Unit
    ) {
        val formChoicesCollection =
            formFieldsCollRef.document(questionNum).collection(FORM_CHOICES_COLLECTION)
        formChoicesCollection
            .get()
            .addOnSuccessListener { formChoicesSnapshots ->
                if (formChoicesSnapshots != null && formChoicesSnapshots.isEmpty.not()) {
                    onFormChoicesSnapshots(formChoicesSnapshots)
                } else Log.e(
                    tag,
                    "FormChoices collection is empty for form id $formId form field $questionNum",
                    null,
                    "FormChoices collection path" to formChoicesCollection.path
                )
            }.addOnFailureListener { exception ->
                Log.e(
                    tag, "Error fetching FormChoices collection ${exception.message}", exception,
                    "FormChoices path" to formChoicesCollection.path
                )
            }
    }


    private fun Int.isFreeForm(): Boolean = this == FREE_FORM_FORM_CLASS

    private fun getFreeFormCollectionReference() = firebaseFirestore.collection(FREE_FORMS_COLLECTION)

    private fun getDispatchFormCollectionReference(customerId: String) =
        firebaseFirestore.collection(FORMS_COLLECTION).document(customerId).collection(FORMS_LIST_COLLECTION)

}