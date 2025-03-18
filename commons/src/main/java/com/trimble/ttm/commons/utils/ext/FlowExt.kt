package com.trimble.ttm.commons.utils.ext

import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.trimble.ttm.commons.logger.Log
import kotlinx.coroutines.flow.*
import java.util.concurrent.CancellationException

suspend inline fun <T> Flow<T>.safeCollect(
    sourceFileName: String,
    crossinline action: suspend (value: T) -> Unit
) {
    try {
        collect { value -> action(value) }
    } catch (e: Exception) {
        when (e) {
            is CancellationException -> {
                // Ignore
            }
            else -> {
                Log.e(coRoutineTag, "safeCollect ${e.message}, $sourceFileName", e)
            }
        }
    }
}

fun <T> Flow<T>.asStateFlow() = this.stateIn(
    ProcessLifecycleOwner.get().lifecycleScope,
    SharingStarted.WhileSubscribed(5000),
    null
)