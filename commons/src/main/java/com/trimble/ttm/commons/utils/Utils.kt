package com.trimble.ttm.commons.utils

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.trimble.ttm.commons.logger.BARCODE_MODULE_AVAILABILITY
import com.trimble.ttm.commons.logger.BARCODE_MODULE_DOWNLOAD
import com.trimble.ttm.commons.logger.BARCODE_RESULTS
import com.trimble.ttm.commons.logger.Log
import kotlinx.coroutines.tasks.await
import java.io.File
import java.util.concurrent.ConcurrentHashMap

object Utils {
    const val TAG = "Utils"
    inline fun <reified T> fromJsonString(json: String, gson: Gson = Gson(), tag : String = TAG): T? =
        try {
            gson.fromJson(json, object : TypeToken<T>() {}.type)
        } catch (e: Exception) {
            Log.e(tag, "error in converting string to object for the json $json ${e.stackTraceToString()}")
            null
        }

    fun toJsonString(src: Any, gson: Gson = Gson()): String? = gson.toJson(src)

    fun Any?.isNull() = this == null

}
fun Any?.toSafeString(): String = try {
    this.toString()
} catch (e: Exception) {
    Log.e("CommonUtils", "exception in safeString: ${e.message}", e)
    EMPTY_STRING
}

//Barcode Scanner to Linear formats and it will return the raw data
suspend fun barcodeScanner(
    appContext: Context
) : String{
    val options = GmsBarcodeScannerOptions.Builder()
        .setBarcodeFormats(
            Barcode.FORMAT_CODABAR,
            Barcode.FORMAT_CODE_39,
            Barcode.FORMAT_CODE_93,
            Barcode.FORMAT_CODE_128,
            Barcode.FORMAT_EAN_8,
            Barcode.FORMAT_EAN_13,
            Barcode.FORMAT_ITF,
            Barcode.FORMAT_UPC_A,
            Barcode.FORMAT_UPC_E
        )
        .enableAutoZoom()
        .build()

    val scanner = GmsBarcodeScanning.getClient(appContext, options)
    try {
        val result = scanner.startScan().await()
        Log.i(BARCODE_RESULTS, result.rawValue.toString())
        return result.rawValue.toString()
    } catch (e: Exception) {
        Log.e(BARCODE_RESULTS, "ERROR ${e.message}")
    }

    return EMPTY_STRING
}


/**To check whether the Barcode Scanner module got downloaded on demand,
 if not downloaded we are requesting it to download **/
fun checkGmsBarcodeScanningModuleAvlAndDownload(context : Context){
    val moduleInstallClient = ModuleInstall.getClient(context)
    val optionalModuleApi = GmsBarcodeScanning.getClient(context)
    moduleInstallClient
        .areModulesAvailable(optionalModuleApi)
        .addOnSuccessListener {moduleAvailabilityResponse ->
            if (moduleAvailabilityResponse.areModulesAvailable()) {
                // Modules are present on the device...
                Log.i(BARCODE_MODULE_AVAILABILITY,"Module is available  $moduleAvailabilityResponse")
            } else {
                // If Modules are not avl then request the download
                Log.d(BARCODE_MODULE_AVAILABILITY,"Module is not available so downloading it $moduleAvailabilityResponse")
                val moduleInstallRequest =
                    ModuleInstallRequest.newBuilder()
                        .addApi(optionalModuleApi)
                        .build()

                moduleInstallClient
                    .installModules(moduleInstallRequest)
                    .addOnSuccessListener {moduleInstallResponse ->
                        if (moduleInstallResponse.areModulesAlreadyInstalled()) {
                            Log.i(BARCODE_MODULE_DOWNLOAD,"Downloaded areModulesAvailable $moduleInstallResponse")
                        }
                    }
                    .addOnFailureListener {exception ->
                        Log.e(BARCODE_MODULE_DOWNLOAD,"addOnFailureListener ${exception.message}")
                    }

            }
        }
        .addOnFailureListener {exception ->
            Log.e(BARCODE_MODULE_AVAILABILITY,"addOnFailureListener ${exception.message}")
        }
}

fun <E> newConcurrentHashSet(mutableSet: MutableSet<E> = mutableSetOf()): MutableSet<E> {
    return ConcurrentHashMap.newKeySet<E>().apply {
        if (mutableSet.isNotEmpty()) addAll(mutableSet)
    }
}

fun Bundle?.getStringOrDefaultValue(key: String) : String {
    return this?.getString(key) ?: EMPTY_STRING
}

fun Bundle?.getBooleanOrDefaultValue(key: String) : Boolean {
    return this?.getBoolean(key) ?: false
}

fun String.hasEvenNumberOfSegments(delimiter: String = "/"): Boolean {
    val values = this.split(delimiter)
    return values.size % 2 == 0
}

fun Context.getOrCreateToBeUploadedFolder(folderDir: File = filesDir, folderName: String = STORAGE_TO_BE_UPLOADED): File {
    val permanentFolder = File(folderDir, folderName)
    if (!permanentFolder.exists()) {
        permanentFolder.mkdirs()
    }
    return permanentFolder
}

fun Context.getOrCreateDraftImagesFolder(folderDir: File = filesDir, folderName: String = STORAGE_DRAFT): File {
    val draftImagesFolder = File(folderDir, folderName)
    if (!draftImagesFolder.exists()) {
        draftImagesFolder.mkdirs()
    }
    return draftImagesFolder
}

fun Context.getOrCreateThumbnailFolder(folderDir: File = filesDir, folderName: String = STORAGE_THUMBNAIL): File {
    val thumbnailFolder = File(folderDir, folderName)
    if (!thumbnailFolder.exists()) {
        thumbnailFolder.mkdirs()
    }
    return thumbnailFolder
}