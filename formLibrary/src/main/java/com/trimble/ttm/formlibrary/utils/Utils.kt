package com.trimble.ttm.formlibrary.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.text.format.DateFormat
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.internal.LinkedTreeMap
import com.trimble.ttm.commons.logger.INBOX_LIST_DIFF_UTIL
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.model.FormField
import com.trimble.ttm.commons.model.FormFieldType
import com.trimble.ttm.commons.model.FormTemplate
import com.trimble.ttm.commons.model.getImageNames
import com.trimble.ttm.commons.utils.DOT_JPG
import com.trimble.ttm.commons.utils.ISO_DATE_TIME_FORMAT
import com.trimble.ttm.commons.utils.SPACE
import com.trimble.ttm.commons.utils.UTC_TIME_ZONE_ID
import com.trimble.ttm.formlibrary.R
import com.trimble.ttm.formlibrary.manager.workmanager.scheduleOneTimeImageUpload
import com.trimble.ttm.formlibrary.model.Message
import com.trimble.ttm.formlibrary.model.Message.Companion.getFormattedUiString
import com.trimble.ttm.formlibrary.ui.activities.FormLibraryActivity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import me.drakeet.support.toast.ToastCompat
import java.io.File
import java.io.Serializable
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.regex.Pattern

@ExperimentalCoroutinesApi
object Utils {

    fun isNumeric(incomingString: String, currencySymbolIfAny: String?): Boolean {
        if (currencySymbolIfAny != null && incomingString.contains(currencySymbolIfAny)) {
            val modifiedString = incomingString.replace(currencySymbolIfAny, "")
            return Pattern.compile("-?\\d+(.\\d+)*,?").matcher(modifiedString).matches()
        }
        return Pattern.compile("-?\\d+(.\\d+)*,?").matcher(incomingString).matches()
    }

    /**
     * Returns the Image File path with image name
     */
    fun getOutputMediaFile(appContext: Context): File? {
        val mediaStorageDir = File(appContext.externalCacheDir.toString())
        if (!mediaStorageDir.exists() && !mediaStorageDir.mkdirs()) {
                return null
        }
        val mImageName = "IMAGE_" + SimpleDateFormat(
            "ddMMyyyy_HHmm",
            Locale.getDefault()
        ).format(Date()) + DOT_JPG
        return File(mediaStorageDir.path + File.separator.toString() + mImageName)
    }

    fun getSystemLocalDateTimeFromUTCDateTime(utcDateTime: String, context: Context): String {
        return try {
            val isoDateFormatter = SimpleDateFormat(ISO_DATE_TIME_FORMAT, Locale.getDefault())
            isoDateFormatter.timeZone = TimeZone.getTimeZone(UTC_TIME_ZONE_ID)
            isoDateFormatter.parse(utcDateTime)?.let { utcDate ->
                getFormattedDateTime(utcDate, context)
            } ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    private fun getFormattedDateTime(date: Date, context: Context): String {
        return DateFormat.getDateFormat(
            context
        ).format(date) + SPACE + DateFormat.getTimeFormat(context).format(
            date
        )
    }

    fun getSubtractedTimeInMillis(daysToBeSubtracted: Int): Long {
        val calendar = Calendar.getInstance(TimeZone.getDefault())
        calendar.add(Calendar.DATE, -daysToBeSubtracted)
        return calendar.timeInMillis
    }

    fun isTablet(context: Context): Boolean =
        context.resources.configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK >= Configuration.SCREENLAYOUT_SIZE_LARGE

    fun noInternetAvailableToast(context: Context){
        ToastCompat.makeText(context,  context.getString(R.string.offline_mode), Toast.LENGTH_SHORT).show()
    }

    fun setTtsHeader(userName: String,sentDate: String,formName: String): ArrayList<String>{
        var ttsInputList = ArrayList<String>()
        ttsInputList.add("$TTS_FROM $userName")
        ttsInputList.add("$TTS_DATE ${sentDate.split("-")[0]}") //To read only the date in datetime, splitted the date & time
        ttsInputList.add("$TTS_FORMNAME $formName")
        return ttsInputList
    }

    fun getTtsList(formFieldsList:ArrayList<FormField> ): ArrayList<String> {
        var ttsInputList = ArrayList<String>()
        formFieldsList.forEach{ formField ->
            if(checkforLabel(formField))
            {
                if(formField.qtext.isNotEmpty()) ttsInputList.add(formField.qtext)
                if(checkforUiData(formField)) {
                    ttsInputList.add(formField.uiData.getFormattedUiString())
                }
            }
        }
        return ttsInputList
    }

    fun checkforLabel(formField: FormField) : Boolean{
        //qtype BranchTarget, BranchTo, Loop Start, Loop End
        return formField.qtype !in setOf(FormFieldType.BRANCH_TARGET.ordinal, FormFieldType.BRANCH_TO.ordinal, FormFieldType.LOOP_START.ordinal, FormFieldType.LOOP_END.ordinal, FormFieldType.AUTO_DATE_TIME.ordinal, FormFieldType.AUTO_VEHICLE_FUEL.ordinal,
            FormFieldType.AUTO_DRIVER_NAME.ordinal, FormFieldType.AUTO_VEHICLE_LOCATION.ordinal, FormFieldType.AUTO_VEHICLE_LATLONG.ordinal, FormFieldType.AUTO_VEHICLE_ODOMETER.ordinal)
    }

    fun checkforUiData(formField: FormField) : Boolean
    {
        // uiData not read in TTS for Image, Signature, Barcode, and ignore if null
        return formField.uiData.getFormattedUiString().isNotEmpty() &&  formField.uiData.getFormattedUiString()!= NULL && formField.qtype !in setOf(FormFieldType.IMAGE_REFERENCE.ordinal ,FormFieldType.SIGNATURE_CAPTURE.ordinal, FormFieldType.BARCODE_SCAN.ordinal)
    }
    fun Button.setVisibilityWhenSendingOrDraftingForm(invisible : Boolean) {
        if (invisible) {
            this.visibility = View.INVISIBLE
            this.isClickable = false
        } else {
            this.visibility = View.VISIBLE
            this.isClickable = true
        }

    }

    fun getIntentDataErrorString(
        context: Context,
        dataName:String,
        dataType:String,
        nullOrEmpty:String,
        actionName:String
    ):String{
        return context.getString(
            R.string.intent_received_data_error,
            dataName,
            dataType,
            nullOrEmpty,
            actionName
        )
    }

    fun getIntentSendErrorString(
        context: Context,
        error:String
    ):String{
        return context.getString(
            R.string.intent_send_error,
            error
        )
    }

    fun getInboxDeletionMessageBasedOnSelection(
        context: Context,
        isSelectAllChecked: Boolean,
        numberOfMessagesSelectedForTrash: Int
    ) = if (isSelectAllChecked) {
        context.getString(R.string.inbox_delete_all_message_content)
    } else context.getString(R.string.inbox_individual_delete_message_content, numberOfMessagesSelectedForTrash)

    fun getPermanentDeletionMessageBasedOnSelection(
        context: Context,
        isSelectAllChecked: Boolean,
        numberOfMessagesSelectedForTrash: Int
    ) = if (isSelectAllChecked) {
        context.getString(R.string.draft_sent_trash_delete_all_message_content)
    } else context.getString(R.string.draft_sent_trash_individual_delete_message_content, numberOfMessagesSelectedForTrash)

    @Suppress("DEPRECATION")
    inline fun <reified T : Serializable> Bundle.customGetSerializable(key: String): T? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getSerializable(key, T::class.java)
        } else {
            getSerializable(key) as? T
        }
    }

    @SuppressLint("SimpleDateFormat")
    fun getDateOrTimeStringForInbox(context: Context, createdUnixTime: Long, defaultDateString: String): String {
        val dateFormatForComparison = SimpleDateFormat("yyyyMMdd")
        val currentDate = Date()
        val timestamp = Date(createdUnixTime)
        timestamp.let { messageTimestamp ->
            if (dateFormatForComparison.format(currentDate)
                    .equals(dateFormatForComparison.format(messageTimestamp))) {
                return if (DateFormat.is24HourFormat(context)) {
                    SimpleDateFormat("HH:mm").format(messageTimestamp)
                } else {
                    SimpleDateFormat("hh:mm aa").format(messageTimestamp)
                }
            }
        }
        return defaultDateString
    }

    fun goToFormLibraryActivity(context: Context, selectedMenuGroupIndex : Int, hotKeysMenuGroupIndex : Int) {
        val intent = Intent(context, FormLibraryActivity::class.java).apply {
            putExtra(
                FORM_GROUP_TAB_INDEX,
                getFormGroupTabIndex(selectedMenuGroupIndex, hotKeysMenuGroupIndex)
            )
        }
        context.startActivity(intent)
    }

    fun getFormGroupTabIndex(selectedMenuGroupIndex: Int, hotKeysMenuGroupIndex: Int) =
        if (selectedMenuGroupIndex == hotKeysMenuGroupIndex) HOTKEYS else FORMS

    fun updateMessageSet(newMessageSet: MutableSet<Message>, messageSet: MutableSet<Message>, adapter: RecyclerView.Adapter<*>): MutableSet<Message> {
        val diffCallback = DiffUtilForMessaging(messageSet, newMessageSet)
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        diffResult.dispatchUpdatesTo(adapter)
        Log.d("${adapter.javaClass.simpleName}$INBOX_LIST_DIFF_UTIL", "updateMessageSet: ${messageSet.size} ${newMessageSet.size}")
        return newMessageSet
    }


    fun getImageIdentifiers(arrayList: ArrayList<Any>): List<String> {
        val uniqueIdentifiers = mutableListOf<String>()
        for (item in arrayList) {
            if (item is LinkedTreeMap<*, *>) { // Check if the item is a LinkedTreeMap
                val entry = item.entries.firstOrNull() // Get the first (and only) entry
                if (entry != null && entry.key == "imageRef") { // Check if the key is "imageRef"
                    val gson = Gson()
                    val imageRefData = gson.fromJson(entry.value.toString(), Map::class.java)
                    if (imageRefData != null) {
                        val uniqueIdentifier = imageRefData["uniqueIdentifier"] as? String
                        if (!uniqueIdentifier.isNullOrEmpty()) { // Check if uniqueIdentifier is not empty
                            uniqueIdentifiers.add(uniqueIdentifier)
                        }
                    }
                }
            }
        }

        return uniqueIdentifiers
    }

    fun Context.scheduleOneTimeImageUploadIfImagesAvailable(formTemplateData: FormTemplate, isDraft: Boolean) {
        formTemplateData.formFieldsList.getImageNames()?.let { this.scheduleOneTimeImageUpload(it, isDraft) }
    }
}