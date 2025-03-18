package com.trimble.ttm.commons.repo

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import com.trimble.ttm.commons.logger.CHANGE_USER
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.model.TruckInfo

const val ADDRESS_BOOK = "addressBook"
const val DRIVERS = "drivers"

class VehicleDriverMappingRepoImpl(
    private val firebaseFirestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()
) : VehicleDriverMappingRepo {
    override suspend fun updateVehicleDriverMap(
        vehicleId: String,
        ttcAccountId: String,
        ttcIdForCurrentUser: String,
        currentUser: String
    ) {
        try {
            val firebaseCurrentUser = firebaseAuth.currentUser
            if (firebaseCurrentUser == null) {
                Log.e(CHANGE_USER, "User is not signed in")
                return
            }
            val truckReference = firebaseFirestore.collection(ADDRESS_BOOK).document(ttcAccountId)
                .collection(DRIVERS).document(ttcIdForCurrentUser)
            truckReference.set(TruckInfo(vehicleId), SetOptions.merge())
            Log.d(CHANGE_USER, "Successfully updated vehicleId $vehicleId for the Driver $currentUser")
        } catch (e: FirebaseFirestoreException) {
            Log.e(CHANGE_USER, "Error updating truck number", e)
        }
    }
}