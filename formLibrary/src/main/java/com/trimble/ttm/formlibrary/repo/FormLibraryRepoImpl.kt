package com.trimble.ttm.formlibrary.repo

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Transaction
import com.google.gson.Gson
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.model.FormDef
import com.trimble.ttm.commons.model.isFreeForm
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.formlibrary.model.Favourite
import com.trimble.ttm.formlibrary.model.HotKeys
import com.trimble.ttm.formlibrary.model.HotKeysDescription
import com.trimble.ttm.formlibrary.usecases.CacheGroupsUseCase
import com.trimble.ttm.formlibrary.utils.FORM_COUNT_PER_PAGE
import com.trimble.ttm.formlibrary.utils.HOTKEYS
import com.trimble.ttm.formlibrary.utils.HOTKEYS_DESCRIPTION_COLLECTION_NAME
import com.trimble.ttm.formlibrary.utils.defaultValue
import com.trimble.ttm.formlibrary.utils.ext.getFromCache
import com.trimble.ttm.formlibrary.utils.ext.getFromServer
import com.trimble.ttm.formlibrary.utils.ext.isCacheEmpty
import com.trimble.ttm.formlibrary.utils.getCallbackFlow
import com.trimble.ttm.formlibrary.utils.isEqualTo
import com.trimble.ttm.formlibrary.utils.isLessThanAndEqualTo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

const val FormsFavouriteCollection = "FormsFavouriteCollection"

@Suppress("UNCHECKED_CAST")
class FormLibraryRepoImpl(private val appModuleCommunicator: AppModuleCommunicator, private val cacheGroupsUseCase: CacheGroupsUseCase) : FormLibraryRepo {
    private val tag = "FormLibraryRepoImpl"
    private val customerIdLogKey = "customer id"
    private val obcIdLogKey = "obc id"
    private val formDefListFlowPair = getCallbackFlow<Map<Double, FormDef>>()
    private var customerId = ""
    private var obcId = defaultValue
    private var vId = defaultValue
    private var groupIds = setOf<Long>()
    private var formsMap = mutableMapOf<Double, FormDef>()
    private var formFetchLimit = 0
    private var didAllFormsReachedForVehicle = getCallbackFlow<Boolean>()
    private var lastFetchedFormCount = 0L
    private var isServerSync = false
    private val gson: Gson = Gson()

    override fun addHotkeysSnapshotFlow(path: String, documentId: String): Flow<Set<HotKeys>> = callbackFlow {
        val listener = FirebaseFirestore.getInstance().collection(path).document(documentId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error) // Handle errors gracefully
                    Log.e(HOTKEYS, "Error in getting hotkeys snapshot data $error")
                    return@addSnapshotListener
                }
                trySend(convertToHotKeysCollection(snapshot))
            }
        awaitClose { listener.remove() } // Clean up the listener
    }

    override suspend fun getHotKeysDescription(
        hotKeysSet: Set<HotKeys>,
        isInternetAvailable: Boolean
    ): MutableSet<HotKeys> {
        val hotKeysWithDescription = mutableSetOf<HotKeys>()
        val hotKeysDocumentReference =
            FirebaseFirestore.getInstance().collection(HOTKEYS_DESCRIPTION_COLLECTION_NAME).limit(1)
        val hotKeysDocumentSnapshot =
            if (hotKeysDocumentReference.isCacheEmpty() && isInternetAvailable) {
                hotKeysDocumentReference.getFromServer().await()
            } else if (hotKeysDocumentReference.isCacheEmpty().not()) {
                hotKeysDocumentReference.getFromCache().await()
            } else {
                null
            }
        hotKeysDocumentSnapshot?.let {
            hotKeysWithDescription.addAll(getDescriptionForHotKey(it, hotKeysSet))
        }
        Log.d(
            tag + HOTKEYS,
            "Hotkeys Description document cache empty:${hotKeysDocumentReference.isCacheEmpty()}, isInternetAvailable : $isInternetAvailable, hotKeys size: ${hotKeysWithDescription.size}"
        )
        return hotKeysWithDescription
    }

    override suspend fun getHotKeysCount(
        path: String,
        documentId: String,
        isInternetAvailable: Boolean
    ): Int {
        val hotKeysDocumentReference =
            FirebaseFirestore.getInstance().collection(path).document(documentId)
        Log.d(
            tag + HOTKEYS,
            "Hotkeys document cache empty:${hotKeysDocumentReference.isCacheEmpty()}, isInternetAvailable : $isInternetAvailable"
        )
        val hotKeysDocumentSnapshot =
            if (hotKeysDocumentReference.isCacheEmpty() && isInternetAvailable) {
                hotKeysDocumentReference.getFromServer().await()
            } else if (hotKeysDocumentReference.isCacheEmpty().not()) {
                hotKeysDocumentReference.getFromCache().await()
            } else {
                null
            }
        val hotKeysCount = hotKeysDocumentSnapshot?.data?.size ?: 0
        Log.d(tag + HOTKEYS, "HotKeys count: $hotKeysCount")
        return hotKeysCount
    }

    override suspend fun addFavourite(favourite: Favourite, driverId: String) {
        val documentRef =
            FirebaseFirestore.getInstance().collection(FormsFavouriteCollection).document(driverId)

        val innerMap = hashMapOf(
            favourite.formId to favourite
        )
        val outerMap = hashMapOf(
            "favoriteForms" to innerMap
        )

        documentRef.set(outerMap, SetOptions.merge())
    }

    override suspend fun removeFavourite(formid: String, driverId: String) {
        val db = FirebaseFirestore.getInstance()
        val documentRef =
            db.collection(FormsFavouriteCollection).document(driverId)

        db.runTransaction { transaction: Transaction ->
            val snapshot = transaction.get(documentRef)
            val currentMap =
                snapshot.get("favoriteForms") as? Map<*, *> ?: return@runTransaction null

            val updatedMap = currentMap.toMutableMap()
            updatedMap.remove(formid)

            transaction.update(documentRef, "favoriteForms", updatedMap)
            null
        }.addOnSuccessListener {
            println("Transaction successful!")
        }.addOnFailureListener { e ->
            println("Transaction failed: $e")
        }

        documentRef.update(
            hashMapOf<String, Any>(
                formid to FieldValue.delete()
            )
        ).await()
    }

    override fun getFavouriteForms(
        path: String,
        driverId: String
    ): Flow<MutableSet<Favourite>> =
        callbackFlow {
            val listener = FirebaseFirestore.getInstance().collection(path).document(driverId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        close(error) // Handle errors gracefully
                        return@addSnapshotListener
                    }

                    val ff = convertToFavouritesCollection(snapshot)
                    trySend(ff.toMutableSet())
                }

            awaitClose { listener.remove() } // Clean up the listener
        }

    private fun convertToFavouritesCollection(snapshot: DocumentSnapshot?): Set<Favourite> {
        val documents = mutableSetOf<Favourite>()
        snapshot?.data?.let { data ->
            // Cast the outer map to the correct type
            (data as? Map<String, Map<String, Map<String, String>>>)?.values?.forEach { outerMapValue ->
                outerMapValue.values.forEach { innerMap ->
                    val formId = innerMap["formId"] ?: ""
                    val formName = innerMap["formName"] ?: ""
                    val cid = innerMap["cid"] ?: ""
                    val formClass = innerMap["formClass"] ?: ""
                    documents.add(Favourite(formId, formName, cid, formClass))
                }
            }
        }
        return documents
    }

    private fun convertToHotKeysCollection(snapshot: DocumentSnapshot?): Set<HotKeys> {
        val documents = mutableSetOf<HotKeys>()
        snapshot?.data?.let { data ->
            // Cast the outer map to the correct type
            data.forEach { (_, value) ->
                try {
                    val hotKey: HotKeys = gson.fromJson(gson.toJson(value), HotKeys::class.java)
                    Log.d(tag + HOTKEYS, "Fetched HotKey: $hotKey")
                    documents.add(hotKey)
                    appModuleCommunicator.getAppModuleApplicationScope()
                        .launch(Dispatchers.IO + SupervisorJob()) {
                            val form = cacheGroupsUseCase.cacheFormTemplate(
                                hotKey.formId.toString(), false
                            )
                            Log.i(
                                tag,
                                "FormTemplate cache complete for formId: ${form.formTemplate.formDef.formid}"
                            )
                        }
                } catch(e: Exception){
                    Log.e(HOTKEYS, "Error in converting snapshot data to hotkeys $e")
                }
            }
        }
        return documents
    }

    override suspend fun getForms(customerId: String, obcId: Long, isServerSync: Boolean) {
        this.customerId = customerId
        this.obcId = obcId
        this.isServerSync = isServerSync
        if (vId.isEqualTo(defaultValue) or groupIds.isEmpty() or formsMap.isEmpty() or isServerSync) {
            cacheGroupsUseCase.getVidFromVUnitCollection(customerId, obcId, tag).let { vUnitPair ->
                vId = vUnitPair.first
                if (vUnitPair.second) {
                    val groupUnitPair = getGroupUnits()
                    groupIds = groupUnitPair.first
                    if (groupUnitPair.second) fetchFormIdsFromVehicleGroups()
                    else {
                        didAllFormsReachedForVehicle.first.notify(true)
                        notifyAboutFormsUpdate(emptyMap())
                        Log.e(tag, "No Forms Available - getGroupIdsFromGroupUnitCollection")
                    }
                } else {
                    didAllFormsReachedForVehicle.first.notify(true)
                    notifyAboutFormsUpdate(emptyMap())
                    Log.e(tag, "No Forms Available - getVidFromVUnitCollection")
                }
            }
        } else {
            getForms()
        }

    }

    private suspend fun getGroupUnits() =
        if (isServerSync)
            cacheGroupsUseCase.getGroupIdsFromGroupUnitCollection(
                customerId,
                obcId,
                vId,
                tag,
                shouldFetchFromServer = true
            )
        else cacheGroupsUseCase.getGroupIdsFromGroupUnitCollection(customerId, obcId, vId, tag)

    private suspend fun fetchFormIdsFromVehicleGroups() {
        val formIdsFetchResultTriple = if (isServerSync) {
            cacheGroupsUseCase.getFormIdsFromGroups(
                groupIds, customerId, obcId, tag, shouldFetchFromServer = true
            )
        } else {
            cacheGroupsUseCase.getFormIdsFromGroups(
                groupIds, customerId, obcId, tag
            )
        }
        cacheGroupsUseCase.sortFormByName(formIdsFetchResultTriple.first, formsMap)
        if (formIdsFetchResultTriple.second) {
            getForms()
        } else {
            didAllFormsReachedForVehicle.first.notify(true)
            notifyAboutFormsUpdate(emptyMap())
            Log.e(tag, "No Forms Available - getFormsOfAllVehicleGroups")
        }
    }

    /**
     * The forms are paginated and sent to ui from here. If there is no update from pfm, then there won't be any server sync calls.
     * Cache hit call - The else part will set startIndex and endIndex for pagination.
     * Server hit call - The start index will be 0 always.
     * Reason: If there is any update for already paginated forms(in UI) then those should be updated in ui.
     */
    private suspend fun getForms() =
        try {
            if (formsMap.isNotEmpty()) {
                val formFetchStartIndex: Int
                val formFetchEndIndex: Int
                with(getFormFetchStartAndEndIndex()) {
                    formFetchStartIndex = first
                    formFetchEndIndex = second
                }
                if (formFetchStartIndex >= 0) {
                    Log.i(
                        tag,
                        "formsCount: ${formsMap.size}.Start index: $formFetchStartIndex End index: $formFetchEndIndex"
                    )
                    val formDef = formsMap.toList().subList(formFetchStartIndex, formFetchEndIndex).toMap()
                    checkAndSendFormsToSubscriber(formDef)
                    syncForms(formDef)
                } else {
                    //Ignore
                }
                formFetchLimit += FORM_COUNT_PER_PAGE
            } else {
                didAllFormsReachedForVehicle.first.notify(true)
                notifyAboutFormsUpdate(emptyMap())
                Log.e(
                    tag,
                    "No Forms Available - Form ids are empty",
                    null,
                    customerIdLogKey to customerId,
                    obcIdLogKey to obcId,
                    "vid" to vId
                )
            }
        } catch (e: Exception) {
            didAllFormsReachedForVehicle.first.notify(true)
            notifyAboutFormsUpdate(emptyMap())
            Log.e(
                tag,
                "No Forms Available - Exception in getForms",
                e,
                customerIdLogKey to customerId,
                obcIdLogKey to obcId,
                "vid" to vId
            )
        }

    private fun getFormFetchStartAndEndIndex(): Pair<Int, Int> {
        val formFetchStartIndex: Int
        val formFetchEndIndex: Int
        if (isServerSync) {
            formFetchStartIndex = 0
            formFetchEndIndex = formFetchLimit + FORM_COUNT_PER_PAGE
            Log.i(
                tag,
                "From server.Start index: $formFetchStartIndex End index: $formFetchEndIndex"
            )
        } else {
            formFetchStartIndex = if (formFetchLimit >= formsMap.size) {
                checkAndSendFormsToSubscriber(emptyMap())
                -1
            } else formFetchLimit
            formFetchEndIndex =
                if ((formFetchStartIndex + FORM_COUNT_PER_PAGE) >  formsMap.size) {
                    formsMap.size
                } else formFetchStartIndex + FORM_COUNT_PER_PAGE
        }
        return Pair(formFetchStartIndex, formFetchEndIndex)
    }

    private suspend fun syncForms(formDefMap: Map<Double, FormDef>) {
        formDefMap.forEach { formDef ->
            //Caches FormDefs, FormField and FormChoices
            appModuleCommunicator.getAppModuleApplicationScope()
                .launch(Dispatchers.IO + SupervisorJob()) {
                    val form = cacheGroupsUseCase.cacheFormTemplate(
                        formDef.value.formid.toString(),
                        formDef.value.isFreeForm()
                    )
                    Log.i(
                        tag,
                        "FormTemplate cache complete for formId: ${form.formTemplate.formDef.formid}"
                    )
                }
        }
    }

    private fun checkAndSendFormsToSubscriber(
        formDefMap: Map<Double, FormDef>
    ) {
        val totalFormCountForVehicle = formsMap.size
        didAllFormsReachedForVehicle.first.notify(
            totalFormCountForVehicle.isLessThanAndEqualTo(formFetchLimit)
        )
        lastFetchedFormCount = formDefMap.size.toLong()
        notifyAboutFormsUpdate(formDefMap)
    }

    override fun resetPagination() {
        formFetchLimit = 0
        didAllFormsReachedForVehicle.first.notify(false)
        lastFetchedFormCount = 0L
        customerId = ""
        obcId = defaultValue
        vId = defaultValue
        groupIds = setOf()
        formsMap = mutableMapOf()
    }

    override fun didLastItemReached() = didAllFormsReachedForVehicle.second

    private fun notifyAboutFormsUpdate(formDefMap: Map<Double, FormDef>) {
        formDefListFlowPair.first.notify(formDefMap)
    }

    override suspend fun getFormDefListFlow() = formDefListFlowPair.second

    private fun getDescriptionForHotKey(querySnapshot: QuerySnapshot, hotKeysSet: Set<HotKeys>) : MutableSet<HotKeys> {
        val hotKeysWithDescription = mutableSetOf<HotKeys>()
        if (!querySnapshot.isEmpty && querySnapshot.documents.isNotEmpty()) {
            val document = querySnapshot.documents.first()
            hotKeysSet.forEach { hotKey ->
                document?.data?.let { data ->
                    val hotKeyId = hotKey.hkId.toString()
                    if(hotKeyId in data) {
                        try {
                            val description = gson.fromJson(
                                gson.toJson(data[hotKeyId]),
                                HotKeysDescription::class.java
                            )
                            Log.d(
                                tag + HOTKEYS,
                                "Description for HotKey: $description for hotKey:$hotKeyId"
                            )
                            hotKeysWithDescription.add(hotKey.copy(hotKeysDescription = description))
                        } catch (e: Exception){
                            Log.e(HOTKEYS, "Error in converting hotkeys description snapshot $e")
                        }
                    }
                }
            }
        }
        return hotKeysWithDescription
    }

}