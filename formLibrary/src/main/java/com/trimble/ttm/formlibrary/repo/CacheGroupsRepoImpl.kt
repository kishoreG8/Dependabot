package com.trimble.ttm.formlibrary.repo

import androidx.datastore.preferences.core.Preferences
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.QueryDocumentSnapshot
import com.google.firebase.firestore.QuerySnapshot
import com.google.gson.Gson
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.model.Form
import com.trimble.ttm.commons.model.FormDef
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.utils.ext.safeLaunch
import com.trimble.ttm.commons.preferenceManager.FormDataStoreManager
import com.trimble.ttm.formlibrary.model.User
import com.trimble.ttm.formlibrary.usecases.MessageFormUseCase
import com.trimble.ttm.formlibrary.utils.ALL_VEHICLES_GROUP_ID
import com.trimble.ttm.formlibrary.utils.CID_FIELD
import com.trimble.ttm.formlibrary.utils.DSN_QUERY_PARAM
import com.trimble.ttm.formlibrary.utils.EMPTY_STRING
import com.trimble.ttm.formlibrary.utils.FORM_LIST_FIELD
import com.trimble.ttm.formlibrary.utils.GID_FIELD
import com.trimble.ttm.formlibrary.utils.GROUP_FORMS_COLLECTION
import com.trimble.ttm.formlibrary.utils.GROUP_UNITS_COLLECTION
import com.trimble.ttm.formlibrary.utils.GROUP_USERS_COLLECTION
import com.trimble.ttm.formlibrary.utils.IS_BLU_USE_CHECKED_FORM
import com.trimble.ttm.formlibrary.utils.LAST_MODIFIED
import com.trimble.ttm.formlibrary.utils.QUERY_DRIVER_ORIGINATED_FORM
import com.trimble.ttm.formlibrary.utils.QUERY_FORM_IN_USE_BITS
import com.trimble.ttm.formlibrary.utils.USER_LIST_FIELD
import com.trimble.ttm.formlibrary.utils.VID_FIELD
import com.trimble.ttm.formlibrary.utils.V_UNIT_COLLECTION
import com.trimble.ttm.formlibrary.utils.defaultValue
import com.trimble.ttm.formlibrary.utils.ext.getFromCache
import com.trimble.ttm.formlibrary.utils.ext.getFromServer
import com.trimble.ttm.formlibrary.utils.ext.isCacheEmpty
import com.trimble.ttm.formlibrary.utils.getCallbackFlow
import com.trimble.ttm.formlibrary.utils.isEqualTo
import com.trimble.ttm.formlibrary.utils.isGreaterThan
import com.trimble.ttm.formlibrary.utils.isLessThan
import com.trimble.ttm.formlibrary.utils.isNotEqualTo
import com.trimble.ttm.formlibrary.utils.toSafeDouble
import com.trimble.ttm.formlibrary.utils.toSafeInt
import com.trimble.ttm.formlibrary.utils.toSafeLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.asDeferred
import kotlinx.coroutines.tasks.await

@Suppress("UNCHECKED_CAST")
class CacheGroupsRepoImpl(val messageFormUseCase: MessageFormUseCase, val formDataStoreManager: FormDataStoreManager, val appModuleCommunicator: AppModuleCommunicator): CacheGroupsRepo {
    private val customerIdLogKey = "customer id"
    private val obcIdLogKey = "obc id"
    private val firestoreExceptionFlowPair = getCallbackFlow<Unit>()
    private val gson = Gson()
    override suspend fun getVidFromVUnitCollection(
        customerId: String,
        obcId: Long,
        tag: String
    ): Pair<Long, Boolean>  = try {
        // Query VUnits table to get Vid
        val query = FirebaseFirestore.getInstance().collection(V_UNIT_COLLECTION)
            .whereEqualTo(CID_FIELD,customerId.toLong()).whereEqualTo(DSN_QUERY_PARAM, obcId)
        val vUnitDocumentSnapshot: QueryDocumentSnapshot? = try {
            if (query.isCacheEmpty()) query.getFromServer().await().firstOrNull()
            else query.getFromCache().await().firstOrNull()
        } catch (e: FirebaseFirestoreException) {
            query.getFromCache().await().firstOrNull()
        }
        getVid(vUnitDocumentSnapshot, customerId, obcId, tag)
    } catch (e: CancellationException) {
        //ignore
        Pair(defaultValue, false)
    } catch (e: Exception) {
        Log.e(
            tag,
            "Exception in getVidAndCidFromVUnitCollection",
            null,
            "exception" to e.stackTraceToString(),
            customerIdLogKey to customerId,
            obcIdLogKey to obcId
        )
        Pair(defaultValue, false)
    }

    private fun getVid(vUnitDocumentSnapshot: QueryDocumentSnapshot?, customerId: String, obcId: Long, tag: String): Pair<Long, Boolean> =
        if (vUnitDocumentSnapshot != null && vUnitDocumentSnapshot.exists()) {
            val vId = vUnitDocumentSnapshot[VID_FIELD] as? Long ?: defaultValue
            val cId = vUnitDocumentSnapshot[CID_FIELD] as? Long ?: defaultValue
            if (vId.isGreaterThan(defaultValue) and cId.isGreaterThan(defaultValue) and (cId.toString() == customerId))
                Pair(vId, true)
            else {
                Log.e(tag, "vunit data not available", null, "vId" to vId, "customer id from the VUnit table" to cId,
                    "customer id from the unit" to customerId, obcIdLogKey to obcId)
                Pair(vId, false)
            }
        } else {
            Log.e(tag, "Queried VUnit doc snapshot not available", null, customerIdLogKey to customerId,
                obcIdLogKey to obcId)
            Pair(defaultValue, false)
        }


    override suspend fun getGroupIdsFromGroupUnitCollection(
        customerId: String,
        obcId: Long,
        vId: Long,
        tag: String,
        shouldFetchFromServer: Boolean
    ): Pair<Set<Long>, Boolean>  = try {
        val groupIds = mutableSetOf<Long>()
        /** Add All Vehicles group id to the list.Though the vehicle is not assigned
         * to any specific group we need to display the driver originated
         * forms under the All Vehicles group*/
        groupIds.add(ALL_VEHICLES_GROUP_ID.toLong())
        // Query GroupUnits table to get all the Gid the Vid belonged to
        val collectionRef = FirebaseFirestore.getInstance().collection(GROUP_UNITS_COLLECTION)
            .document(customerId).collection(vId.toString())
        val groupUnitQuerySnapshot: QuerySnapshot = try {
            if (shouldFetchFromServer) collectionRef.getFromServer().await()
            else {
                if (collectionRef.isCacheEmpty()){
                    collectionRef.getFromServer().await()
                } else {
                    collectionRef.getFromCache().await()
                }
            }
        } catch (e: FirebaseFirestoreException) {
            collectionRef.getFromCache().await()
        }
        getGroupIds(groupUnitQuerySnapshot, groupIds, customerId, obcId, vId, tag)
    } catch (e: CancellationException) {
        //ignore
        Pair(setOf(), false)
    }catch (e: Exception) {
        Log.e(tag, "Exception in getGroupIdsFromGroupUnitCollection", e, customerIdLogKey to customerId,
            obcIdLogKey to obcId, "vid" to vId)
        Pair(setOf(), false)
    }

    private fun getGroupIds(groupUnitQuerySnapshot: QuerySnapshot?, groupIds: MutableSet<Long>, customerId: String,
                            obcId: Long, vId: Long, tag: String): Pair<Set<Long>, Boolean> =
        if (groupUnitQuerySnapshot?.documents.isNullOrEmpty().not()) {
            groupUnitQuerySnapshot?.documents?.forEach { groupUnitDocSnapshot ->
                (groupUnitDocSnapshot?.data?.get(GID_FIELD) as? Map<String, Any>)?.keys?.onEach { groupId ->
                    groupIds.add(groupId.toSafeLong())
                }
            }
            Pair(groupIds, true)
        } else {
            Log.d(tag, "Group unit doc snapshots not available", null, customerIdLogKey to customerId,
                obcIdLogKey to obcId, "vid" to vId)
            Pair(groupIds, true)
        }

    override suspend fun getFormIdsFromGroups(
        groupIds: Set<Long>,
        customerId: String,
        obcId: Long,
        tag: String,
        shouldFetchFromServer: Boolean
    ): Triple<Map<Double, FormDef>, Boolean, Boolean> = try {
        val formsMap = mutableMapOf<Double, FormDef>()
        var isAllSnapshotsFromServer = true
        groupIds.forEach { groupId ->
            val collRef = FirebaseFirestore.getInstance().collection(GROUP_FORMS_COLLECTION)
                .document(customerId).collection(groupId.toString())
            try {
                if (shouldFetchFromServer) getFormIds(collRef.getFromServer().await(), groupId, formsMap, tag)
                else {
                    if (collRef.isCacheEmpty()) {
                        getFormIds(collRef.getFromServer().await(), groupId, formsMap, tag)
                    } else
                        getFormIds(collRef.getFromCache().await(), groupId, formsMap, tag)
                }
            } catch (e: FirebaseFirestoreException) {
                isAllSnapshotsFromServer = false
                getFormIds(collRef.getFromCache().await(), groupId, formsMap, tag)
            }
        }
        if (groupIds.isEmpty()) isAllSnapshotsFromServer = false
        Triple(formsMap, true, isAllSnapshotsFromServer)
    } catch (e: CancellationException) {
        //ignore
        Triple(emptyMap(), false, false)
    } catch (e: Exception) {
        Log.e(tag, "Exception in getFormIdsFromGroups for cId: $customerId obcId: $obcId", e)
        Triple(emptyMap(), false, false)
    }

    private fun getFormIds(formIdQuerySnapshot: QuerySnapshot, groupId: Long, formsMap: MutableMap<Double, FormDef>, tag: String) {
        formIdQuerySnapshot.documents.forEach { formIdDocSnapshot ->
            (formIdDocSnapshot?.data?.get(FORM_LIST_FIELD) as? Map<String, Any>)?.entries?.onEach {
                parseFormId(it, formsMap, groupId, tag)
            }
        }
    }

    override suspend fun getUserIdsFromGroups(
        groupIds: Set<Long>,
        customerId: String,
        obcId: Long,
        tag: String,
        shouldFetchFromServer: Boolean
    ): Triple<MutableSet<User>, Boolean, Boolean> = try {
        val userList = mutableSetOf<User>()
        var isAllSnapshotsFromServer = true
        groupIds.forEach { groupId ->
            val collRef = FirebaseFirestore.getInstance().collection(GROUP_USERS_COLLECTION).document(customerId)
                .collection(groupId.toString())
            val documentSnapshot = try {
                if (shouldFetchFromServer) collRef.getFromServer().await()
                else {
                    if (collRef.isCacheEmpty()) {
                        collRef.getFromServer().await()
                    } else
                        collRef.getFromCache().await()
                }
            } catch (e: FirebaseFirestoreException) {
                isAllSnapshotsFromServer = false
                collRef.getFromCache().await()
            }
            documentSnapshot.documents.forEach { groupUserDoc ->
                (groupUserDoc?.data?.get(USER_LIST_FIELD) as? Map<String, Any>)?.values?.onEach { items ->
                    parseUserId(items, userList, groupId, tag)
                }
            }
        }
        if (groupIds.isEmpty()) isAllSnapshotsFromServer = false
        Triple(userList, true, isAllSnapshotsFromServer)
    } catch (e: CancellationException) {
        //ignore
        Triple(mutableSetOf(), false, false)
    }catch (e: Exception) {
        Log.e(tag, "Exception in getUserIdsFromGroups", e, customerIdLogKey to customerId,
            obcIdLogKey to obcId)
        Triple(mutableSetOf(), false, false)
    }

    internal fun parseUserId(data: Any, userList: MutableSet<User>, groupId: Long, tag: String): Boolean {
        val newUser = gson.fromJson(gson.toJson(data), User::class.java)
        if (newUser.addressBook) {
            userList.add(newUser)
            Log.i(tag, "AddrBook:${newUser.addressBook} UserId: ${newUser.uID} UserMail: ${newUser.email} UserName:${newUser.username} : GroupId: $groupId")
            return true
        }
        return false
    }

    internal fun parseFormId(data: Map.Entry<String, Any>, formsMap: MutableMap<Double, FormDef>, groupId: Long, tag: String ): Boolean {
        (data.value as? Map<String, Any>)?.also {
            if ((it[QUERY_FORM_IN_USE_BITS] as? Long ?: 0L >= IS_BLU_USE_CHECKED_FORM) && (it[QUERY_DRIVER_ORIGINATED_FORM] as? Long ?: 0L == 1L)) {
                formsMap[data.key.toSafeDouble()] = FormDef(
                    name = it["formName"] as? String ?: EMPTY_STRING,
                    formid = (it["formid"] as? Long)?.toSafeInt() ?: -1,
                    formClass = (it["formClass"] as? Long)?.toSafeInt() ?: 0
                )
                Log.i(tag, "FormId: ${data.key} GroupId: $groupId")
                return true
            }
        }
        return false
    }

    private fun cacheFormIdsFromGroupUnitsCollection(tag: String, coroutineScope: CoroutineScope) {
        coroutineScope.safeLaunch(Dispatchers.IO + SupervisorJob()) {
            val customerId = appModuleCommunicator.doGetCid()
            val obcId = appModuleCommunicator.doGetObcId().toSafeLong()
            getVidFromVUnitCollection(customerId, obcId, tag).let { vUnitPair ->
                val vId = vUnitPair.first
                if (vUnitPair.second) {
                    getGroupIdsFromGroupUnitCollection(customerId, obcId,
                        vId, tag, shouldFetchFromServer = true
                    ).let { groupUnitPair ->
                        val groupIds = groupUnitPair.first
                        if (groupUnitPair.second) {
                            cacheFormIdsForFormLibrary(groupIds, customerId, obcId, coroutineScope, tag)
                        } else Log.e(tag, "GroupIds not Available for cId: $customerId obcId: $obcId")
                    }
                } else Log.e(tag, "Vid not available for cId: $customerId obcId: $obcId")
            }
        }
    }

    private fun cacheUserIdsFromGroupUnitsCollection(tag: String, coroutineScope: CoroutineScope) {
        coroutineScope.safeLaunch(Dispatchers.IO + SupervisorJob()) {
            val customerId = appModuleCommunicator.doGetCid()
            val obcId = appModuleCommunicator.doGetObcId().toSafeLong()
            getVidFromVUnitCollection(customerId, obcId, tag).let { vUnitPair ->
                val vId = vUnitPair.first
                if (vUnitPair.second) {
                    getGroupIdsFromGroupUnitCollection(customerId, obcId,
                        vId, tag, shouldFetchFromServer = true
                    ).let { groupUnitPair ->
                        val groupIds = groupUnitPair.first
                        if (groupUnitPair.second) {
                            cacheUserIdsForContacts(groupIds, customerId, obcId, coroutineScope, tag)
                        } else Log.e(tag, "GroupIds not Available for cId: $customerId obcId: $obcId")
                    }
                } else Log.e(tag, "Vid not available for cId: $customerId obcId: $obcId")
            }
        }
    }

    private fun cacheFormIdsForFormLibrary(groupIds: Set<Long>, customerId: String, obcId: Long, coroutineScope: CoroutineScope, tag: String) {
        coroutineScope.safeLaunch(Dispatchers.IO + SupervisorJob()) {
            val formCacheResultTriple = getFormIdsFromGroups(groupIds, customerId, obcId, tag, shouldFetchFromServer = true)
            if (formCacheResultTriple.second) {
                if (formCacheResultTriple.third) formDataStoreManager.setValue(FormDataStoreManager.IS_FORM_LIBRARY_SNAPSHOT_EMPTY, false)
                else formDataStoreManager.setValue(FormDataStoreManager.IS_FORM_LIBRARY_SNAPSHOT_EMPTY, true)
                logFormIdsCacheResult(customerId, obcId, formCacheResultTriple.first.size, "success", tag)
            }
            else logFormIdsCacheResult(customerId, obcId, formCacheResultTriple.first.size, "fail", tag)
        }
    }

    private fun cacheUserIdsForContacts(groupIds: Set<Long>, customerId: String, obcId: Long, coroutineScope: CoroutineScope, tag: String) {
        coroutineScope.safeLaunch(Dispatchers.IO + SupervisorJob()) {
            val userCacheResultTriple = getUserIdsFromGroups(groupIds, customerId, obcId, tag, shouldFetchFromServer = true)
            if (userCacheResultTriple.second) {
                if (userCacheResultTriple.third) formDataStoreManager.setValue(FormDataStoreManager.IS_CONTACTS_SNAPSHOT_EMPTY, false)
                else formDataStoreManager.setValue(FormDataStoreManager.IS_CONTACTS_SNAPSHOT_EMPTY, true)
                logUserIdsCacheResult(customerId, obcId, userCacheResultTriple.first.size, "success", tag)
            }
            else logUserIdsCacheResult(customerId, obcId, userCacheResultTriple.first.size, "fail", tag)
        }
    }

    private fun logFormIdsCacheResult(customerId: String, obcId: Long, cachedFormCount: Int, cacheResult: String, tag: String) =
        Log.i(tag, "FormId's cache $cacheResult for cId: $customerId obcId: $obcId cachedFormCount: $cachedFormCount")

    private fun logUserIdsCacheResult(customerId: String, obcId: Long, cachedUserCount: Int, cacheResult: String, tag: String) =
        Log.i(tag, "User's cache $cacheResult for cId: $customerId obcId: $obcId cachedUserCount: $cachedUserCount")

    override suspend fun cacheFormTemplate(formId: String, isFreeForm: Boolean): Form =
        messageFormUseCase.getForm(formId, isFreeForm)

    override suspend fun checkAndUpdateCacheForGroupsFromServer(
        cid: String,
        obcId: String,
        applicationScope: CoroutineScope,
        tag: String
    ): Boolean =
        try {
            val groupUnitsDocRef = FirebaseFirestore.getInstance()
                .collection(GROUP_UNITS_COLLECTION).document(cid)
            val groupFormsDocRef = FirebaseFirestore.getInstance()
                .collection(GROUP_FORMS_COLLECTION).document(cid)
            val groupUsersDocRef = FirebaseFirestore.getInstance()
                .collection(GROUP_USERS_COLLECTION).document(cid)
            val result = awaitAll(
                groupUnitsDocRef.getFromServer().asDeferred(),
                groupFormsDocRef.getFromServer().asDeferred(),
                groupUsersDocRef.getFromServer().asDeferred()
            )
            val groupUnitsLastModifiedTimeFromFirestore = result[0].getTimestamp(LAST_MODIFIED)?.seconds ?: 0L
            val groupFormsLastModifiedTimeFromFirestore = result[1].getTimestamp(LAST_MODIFIED)?.seconds ?: 0L
            val groupUsersLastModifiedTimeFromFirestore = result[2].getTimestamp(LAST_MODIFIED)?.seconds ?: 0L

            updateLastModifiedTimeForGroupsInFirestoreIfEmpty(Pair(groupUnitsDocRef, groupUnitsLastModifiedTimeFromFirestore), tag)
            updateLastModifiedTimeForGroupsInFirestoreIfEmpty(Pair(groupFormsDocRef, groupFormsLastModifiedTimeFromFirestore), tag)
            updateLastModifiedTimeForGroupsInFirestoreIfEmpty(Pair(groupUsersDocRef, groupUsersLastModifiedTimeFromFirestore), tag)

            if (isCacheOutDatedOrEmpty(groupUnitsLastModifiedTimeFromFirestore,
                    groupFormsLastModifiedTimeFromFirestore,
                    groupUsersLastModifiedTimeFromFirestore)
            ) {
                Log.i(tag, "Groups modified or not up-to-date. Updating local cache for cId: $cid obcId: $obcId")
                cacheFormIdsFromGroupUnitsCollection(tag, applicationScope)
                cacheUserIdsFromGroupUnitsCollection(tag, applicationScope)
                updateLastModifiedTimeForGroupsInDataStore(FormDataStoreManager.GROUP_UNITS_LAST_MODIFIED_TIME_KEY, groupUnitsLastModifiedTimeFromFirestore)
                updateLastModifiedTimeForGroupsInDataStore(FormDataStoreManager.GROUP_FORMS_LAST_MODIFIED_TIME_KEY, groupFormsLastModifiedTimeFromFirestore)
                updateLastModifiedTimeForGroupsInDataStore(FormDataStoreManager.GROUP_USERS_LAST_MODIFIED_TIME_KEY, groupUsersLastModifiedTimeFromFirestore)
                true
            } else false
        } catch (e: CancellationException) {
            false
        }catch (e: FirebaseFirestoreException) {
            firestoreExceptionFlowPair.first.notify(Unit)
            false
        } catch (e: Exception) {
            Log.e(tag, "Exception in checkAndUpdateGroupsFromServer for cId: $cid obcId: $obcId Error: ${e.message}")
            false
        }

    internal suspend fun isCacheOutDatedOrEmpty(
        groupUnitsLastModifiedTimeFromFirestore: Long,
        groupFormsLastModifiedTimeFromFirestore: Long,
        groupUsersLastModifiedTimeFromFirestore: Long,
    ): Boolean {
        val isCacheOutDated =
            formDataStoreManager.getValue(
                FormDataStoreManager.GROUP_UNITS_LAST_MODIFIED_TIME_KEY,
                0L).isLessThan(groupUnitsLastModifiedTimeFromFirestore) or
                    formDataStoreManager.getValue(
                        FormDataStoreManager.GROUP_FORMS_LAST_MODIFIED_TIME_KEY,
                        0L).isLessThan(
                        groupFormsLastModifiedTimeFromFirestore) or
                    formDataStoreManager.getValue(
                        FormDataStoreManager.GROUP_USERS_LAST_MODIFIED_TIME_KEY, 0L).isLessThan(
                        groupUsersLastModifiedTimeFromFirestore)

        val isLastModifiedEmptyInFirestore = groupUnitsLastModifiedTimeFromFirestore.isEqualTo(0) or
                groupFormsLastModifiedTimeFromFirestore.isEqualTo(0) or
                groupUsersLastModifiedTimeFromFirestore.isEqualTo(0)

        val isFormLibraryOrContactsSnapshotEmpty = formDataStoreManager.getValue(
            FormDataStoreManager.IS_FORM_LIBRARY_SNAPSHOT_EMPTY,
            true) or
                formDataStoreManager.getValue(
                    FormDataStoreManager.IS_CONTACTS_SNAPSHOT_EMPTY,
                    true)

        return isCacheOutDated or isLastModifiedEmptyInFirestore or isFormLibraryOrContactsSnapshotEmpty
    }

    private fun updateLastModifiedTimeForGroupsInFirestoreIfEmpty(groupsPair: Pair<DocumentReference, Long>, tag: String) {
        if (groupsPair.second.isEqualTo(0)) {
            with(HashMap<String, Any>()) {
                this[LAST_MODIFIED] = FieldValue.serverTimestamp()
                groupsPair.first.set(this).addOnSuccessListener {
                    Log.i(tag, "Updated last modified in firestore for path: ${groupsPair.first.path}")
                }.addOnFailureListener { e ->
                    Log.e(tag, "Error updating last modified in firestore for path: ${groupsPair.first.path}", e)
                }
            }
        }
    }

    private suspend fun updateLastModifiedTimeForGroupsInDataStore(groupsLastModifiedKey: Preferences.Key<Long>, groupsLastModifiedValue: Long) {
        if (groupsLastModifiedValue.isNotEqualTo(0)) formDataStoreManager.setValue(groupsLastModifiedKey, groupsLastModifiedValue)
    }

    override fun getFirestoreExceptionNotifier(): Flow<Unit> = firestoreExceptionFlowPair.second

}