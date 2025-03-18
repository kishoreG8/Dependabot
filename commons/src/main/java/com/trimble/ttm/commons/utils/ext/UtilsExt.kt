package com.trimble.ttm.commons.utils.ext
fun Any?.isNull() = this == null

fun Any?.isNotNull() = this != null

fun Double.removeDecimalPoints(): Int {
    return try {
        return this.toInt()
    } catch (e: Exception) {
        -1
    }
}