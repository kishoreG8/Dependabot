package com.trimble.ttm.routemanifest.model

import java.util.*

data class RouteData(var etaTime: Date, var address: Address? = null, var leg: Leg? = null)