package com.trimble.ttm.formlibrary.dataLayer

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinComponent

class MandatoryEDVIRDataProvider : ContentProvider(), KoinComponent {

    private val database: MandatoryInspectionRelatedDatabase by inject()

    override fun query(
        uri: Uri, projection: Array<String>?, selection: String?,
        selectionArgs: Array<String>?, sortOrder: String?
    ): Cursor {
        val cursor: Cursor = database.query("SELECT * FROM MandatoryInspection LIMIT 1", null)
        cursor.setNotificationUri(context!!.contentResolver, uri)
        return cursor
    }

    override fun getType(uri: Uri): String? = null

    override fun onCreate(): Boolean = true

    override fun insert(uri: Uri, values: ContentValues?): Uri = uri

    override fun update(
        uri: Uri, values: ContentValues?, selection: String?,
        selectionArgs: Array<String>?
    ): Int = 0

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<String>?
    ): Int = 0

}