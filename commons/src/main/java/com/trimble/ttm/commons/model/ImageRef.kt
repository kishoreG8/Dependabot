package com.trimble.ttm.commons.model

data class ImageRef(
    val uniqueTag: Long,
    val initiallyEmpty: Boolean,
    val dateTimeOfImage: String,
    val imageName: String,
    val mimeType: String,
    val uniqueIdentifier: String
)