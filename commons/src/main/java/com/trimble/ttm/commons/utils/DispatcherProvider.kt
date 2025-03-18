package com.trimble.ttm.commons.utils

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher

interface DispatcherProvider {
    fun main(): CoroutineDispatcher = Dispatchers.Main
    fun mainImmediate(): CoroutineDispatcher = Dispatchers.Main.immediate
    fun default(): CoroutineDispatcher = Dispatchers.Default
    fun io(): CoroutineDispatcher = Dispatchers.IO
    fun unconfined(): CoroutineDispatcher = Dispatchers.Unconfined
}

class DefaultDispatcherProvider : DispatcherProvider

@OptIn(ExperimentalCoroutinesApi::class)
class TestDispatcherProvider : DispatcherProvider {
    override fun main(): CoroutineDispatcher = UnconfinedTestDispatcher()
    override fun default(): CoroutineDispatcher = UnconfinedTestDispatcher()
    override fun io(): CoroutineDispatcher = UnconfinedTestDispatcher()
    override fun unconfined(): CoroutineDispatcher = UnconfinedTestDispatcher()
}