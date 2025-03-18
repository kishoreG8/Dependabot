package com.trimble.ttm.formlibrary.utils

import kotlinx.coroutines.delay

/**
 * Contract to hide the delay implementation
 * and make more easy the unit testing
 * */
interface DelayResolver{

    suspend fun callDelay(timeMillis: Long)

}

class DelayProvider : DelayResolver {
    override suspend fun callDelay(timeMillis: Long) {
        delay(timeMillis)
    }
}


class TestDelayProvider : DelayResolver {

    /**
     * empty method implementation to avoid the failing tests
     * when a delay is used
     * */
    override suspend fun callDelay(timeMillis: Long) {
        //don't add the daly implementation
    }
}