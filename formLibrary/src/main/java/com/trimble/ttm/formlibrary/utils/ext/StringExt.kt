package com.trimble.ttm.formlibrary.utils.ext

fun String.toMutableSet(): MutableSet<String> {
    return when {
        this.isEmpty() -> mutableSetOf()
        else -> this.split(",").toMutableSet()
    }
}