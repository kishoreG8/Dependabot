package com.trimble.ttm.routemanifest.managers

interface IResourceStringsManager {
    fun getStringsForTripCacheUseCase(): Map<StringKeys, String>
}