package com.trimble.ttm.commons.utils

import androidx.tracing.Trace


inline fun <T> traceBlock(label: String, crossinline block: () -> T): T {
    return androidx.tracing.trace(label, block)
}

/*
 * Used for macrobenchmark app startup analysis
 */
fun forceEnableAppSystemTracingForPerformanceAnalysis() {
    Trace.forceEnableAppTracing()
}


fun traceBeginSection(label: String) {
    Trace.beginAsyncSection(label,0)
}

fun traceEndSection(label: String) {
    Trace.endAsyncSection(label,0)
}