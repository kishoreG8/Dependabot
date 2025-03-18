package com.trimble.ttm.commons.model

data class Odometer(
    val uniqueTag: Long, val initiallyEmpty: Boolean,
    val j1708: Int, val gps: Int
)