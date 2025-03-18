package com.trimble.ttm.commons.utils.ext

import android.content.Intent
import android.os.Build
import com.trimble.ttm.commons.model.DispatchFormPath
import com.trimble.ttm.commons.model.FormResponse
import com.trimble.ttm.commons.utils.DISPATCH_FORM_PATH_SAVED
import com.trimble.ttm.commons.utils.DRIVER_FORM_ID
import com.trimble.ttm.commons.utils.FORM_DATA_KEY
import com.trimble.ttm.commons.utils.IS_FROM_DRAFT

/**
 * Bridging the gap of Parcelable with Serializable versions in Android
 * https://issuetracker.google.com/issues/243986223?pli=1
 * https://developer.android.com/reference/android/os/Bundle#getParcelable(java.lang.String)
 */
private val isTiramisuOrLatestVersion: Boolean
    get() = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)


fun Intent.getFormData(): FormResponse {
    return this.getParcelableData(FORM_DATA_KEY, FormResponse::class.java) ?: FormResponse()
}

fun Intent.isFromDraft(): Boolean {
    return this.getBooleanExtra(IS_FROM_DRAFT, false)
}

fun Intent?.fromIntent(destinationActivity: String): Intent {
    if(this != null) {
        return this.setAction(destinationActivity)
    }
    return Intent(destinationActivity)
}

fun Intent.getCompleteFormID(): DispatchFormPath {
    return this.getParcelableData(DISPATCH_FORM_PATH_SAVED, DispatchFormPath::class.java)
        ?: DispatchFormPath()
}

fun Intent.getDriverFormID(): DispatchFormPath {
    return this.getParcelableData(DRIVER_FORM_ID, DispatchFormPath::class.java)
        ?: DispatchFormPath()
}

@Suppress("DEPRECATION")
fun <T> Intent.getParcelableData(
    nameProperty: String,
    clazz : Class<T>
) : T? {
    if(isTiramisuOrLatestVersion){
        return getParcelableExtra(nameProperty, clazz)
    }
    return getParcelableExtra(nameProperty) as? T
}


