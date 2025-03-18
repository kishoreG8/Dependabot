package com.trimble.ttm.routemanifest.contentProvider

import android.content.ContentProvider
import android.content.ContentValues
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.net.Uri
import com.trimble.ttm.commons.logger.Log
import java.io.FileNotFoundException
import java.io.IOException

class DrawableResDataProvider : ContentProvider() {
    private val tag = "DrawableResDataCP"

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        return null
    }

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor? {
        return null
    }

    override fun onCreate(): Boolean {
        return true
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<String>?
    ): Int {
        return 0
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int {
        return 0
    }

    override fun getType(uri: Uri): String? {
        return null
    }

    @Throws(FileNotFoundException::class)
    override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor? {
        val am = context?.assets
        val fileName = uri.lastPathSegment ?: throw FileNotFoundException()
        var fileDescriptor: AssetFileDescriptor? = null
        try {
            fileDescriptor = am?.openFd(fileName)
        } catch (e: IOException) {
            Log.e(tag, e.message, e)
        }
        return fileDescriptor
    }
}