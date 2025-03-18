package com.trimble.ttm.routemanifest.utils.ext

import com.google.firebase.firestore.DocumentSnapshot
import com.google.gson.Gson
import com.trimble.ttm.routemanifest.model.Dispatch
import com.trimble.ttm.routemanifest.utils.ISCOMPLETED
import com.trimble.ttm.routemanifest.utils.PAYLOAD

fun DocumentSnapshot?.toDispatch(): Dispatch? =
    this?.let { documentSnapshot ->
        val dispatch = Gson().fromJson(
            Gson().toJson(documentSnapshot[PAYLOAD]),
                Dispatch::class.java
        )
        dispatch.isCompleted = documentSnapshot[ISCOMPLETED] as Boolean
        dispatch
    }