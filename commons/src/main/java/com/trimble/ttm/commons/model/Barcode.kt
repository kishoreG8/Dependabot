package com.trimble.ttm.commons.model

data class Barcode(
    val uniqueTag: Long,
    val initiallyEmpty: Boolean,
    val barcodeDataLength: Int,
    val barcodeFlags: Int,
    val barcodeType: Int,
    val barcodeData: String
)