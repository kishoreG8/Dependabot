package com.trimble.ttm.commons.model

import java.io.Serializable

//Nullable because if there is no data for any of these fields, and if we send "" instead null it won't be handled on pfm.
// So, having the type as nullable
data class Recipients(val recipientPfmUser: Any? = null, val recipientEmailUser: Any? = null): Serializable
