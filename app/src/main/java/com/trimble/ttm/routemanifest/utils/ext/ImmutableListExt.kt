package com.trimble.ttm.routemanifest.utils.ext

class ImmutableList<T> private constructor(private val inner: List<T>) : List<T> by inner {
    companion object {
        fun <T> create(inner: List<T>) = if (inner is ImmutableList<T>) {
            inner
        } else {
            ImmutableList(inner.toList())
        }
    }
}

fun <T> List<T>.toImmutableList(): List<T> =
    ImmutableList.create(this)