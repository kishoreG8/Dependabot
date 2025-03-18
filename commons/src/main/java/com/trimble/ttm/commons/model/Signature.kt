package com.trimble.ttm.commons.model

data class Signature(
    val uniqueTag: Long,
    val initiallyEmpty: Boolean,
    val compressionType: String,
    val imageType:String,
    val pixelHeight: Int,
    val pixelWidth: Int = 0,
    val signatureData: String
)