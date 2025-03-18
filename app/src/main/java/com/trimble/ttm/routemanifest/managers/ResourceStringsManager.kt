package com.trimble.ttm.routemanifest.managers

import android.content.Context
import com.trimble.ttm.routemanifest.R

enum class StringKeys {
    DID_YOU_ARRIVE_AT,
    SELECT_STOP_TO_NAVIGATE,
    YOUR_NEXT_STOP,
    YES,
    NO,
    OPEN,
    DISMISS,
    COMPLETE_FORM_FOR_STOP,
    COMPLETE_FORM_FOR_ARRIVED_STOPS,
    OK
}

class ResourceStringsManager(private val context: Context) : IResourceStringsManager {
    override fun getStringsForTripCacheUseCase(): Map<StringKeys, String> {
        val map = mutableMapOf<StringKeys, String>()
        map[StringKeys.DID_YOU_ARRIVE_AT] = context.getString(R.string.did_you_arrive_at)
        map[StringKeys.SELECT_STOP_TO_NAVIGATE] =
            context.getString(R.string.select_stop_to_navigate)
        map[StringKeys.YOUR_NEXT_STOP] = context.getString(R.string.your_next_stop)
        map[StringKeys.YES] = context.getString(R.string.yes)
        map[StringKeys.NO] = context.getString(R.string.no)
        map[StringKeys.OPEN] = context.getString(R.string.open)
        map[StringKeys.DISMISS] = context.getString(R.string.dismiss)
        map[StringKeys.COMPLETE_FORM_FOR_STOP] = context.getString(R.string.complete_form_for_stop)
        map[StringKeys.COMPLETE_FORM_FOR_ARRIVED_STOPS] =
            context.getString(R.string.complete_form_for_arrived_stops)
        map[StringKeys.OK] = context.getString(R.string.ok_text)
        return map
    }
}