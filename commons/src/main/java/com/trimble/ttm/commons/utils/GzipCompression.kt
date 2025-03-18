package com.trimble.ttm.commons.utils

import com.trimble.ttm.commons.logger.Log
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

object GzipCompression {
    private var TAG = "COMPRESSED_DATA_SIZE"
    fun gzipCompress(uncompressedData: ByteArray): ByteArray {
        var result = byteArrayOf()
        try {
            ByteArrayOutputStream(uncompressedData.size).use { bos ->
                GZIPOutputStream(bos).use { gzipOS ->
                    gzipOS.write(uncompressedData)
                    gzipOS.close()
                    result = bos.toByteArray()

                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        Log.d(TAG, result.size.toString())
        return result
    }

    fun gzipDecompress(compressedData: ByteArray): ByteArray {
        var result = byteArrayOf()
        try {
            ByteArrayInputStream(compressedData).use { bis ->
                ByteArrayOutputStream().use { bos ->
                    GZIPInputStream(bis).use { gzipIS ->
                        val buffer = ByteArray(1024)
                        var len: Int
                        while (gzipIS.read(buffer).also { len = it } != -1) {
                            bos.write(buffer, 0, len)
                        }
                        result = bos.toByteArray()
                    }
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        Log.d(TAG, result.size.toString())
        return result
    }
}