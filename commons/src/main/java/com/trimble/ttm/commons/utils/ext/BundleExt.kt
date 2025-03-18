package com.trimble.ttm.commons.utils.ext

import android.os.Build
import android.os.Bundle
import androidx.annotation.VisibleForTesting
import com.trimble.ttm.commons.model.DispatchFormPath
import com.trimble.ttm.commons.model.FormDef
import com.trimble.ttm.commons.utils.DISPATCH_FORM_PATH_SAVED
import com.trimble.ttm.commons.utils.DRIVER_FORM_ID
import com.trimble.ttm.commons.utils.IMESSAGE_REPLY_FORM_DEF
import com.trimble.ttm.commons.utils.UNCOMPLETED_DISPATCH_FORM_PATH

@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
fun Bundle.isTiramisuOrLatestVersion(): Boolean = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)

fun Bundle.getReplyFormDef(): FormDef {
    return (this.getParcelableData(IMESSAGE_REPLY_FORM_DEF, FormDef::class.java) ?: FormDef())
}

fun Bundle.getDispatchFormPathSaved(): DispatchFormPath {
    return (this.getParcelableData(DISPATCH_FORM_PATH_SAVED, DispatchFormPath::class.java)
        ?: DispatchFormPath())
}

fun Bundle.getUnCompletedDispatchFormPath(): DispatchFormPath {
    return (this.getParcelableData(UNCOMPLETED_DISPATCH_FORM_PATH, DispatchFormPath::class.java)
        ?: DispatchFormPath())
}

fun Bundle.getDriverFormId(): DispatchFormPath {
    return (this.getParcelableData(DRIVER_FORM_ID, DispatchFormPath::class.java)
        ?: DispatchFormPath())
}

@Suppress("DEPRECATION")
fun <T> Bundle.getParcelableData(
    nameProperty: String,
    clazz : Class<T>
) : T? {
    if(isTiramisuOrLatestVersion()){
        return getParcelable(nameProperty, clazz)
    }
    return getParcelable(nameProperty) as? T
}