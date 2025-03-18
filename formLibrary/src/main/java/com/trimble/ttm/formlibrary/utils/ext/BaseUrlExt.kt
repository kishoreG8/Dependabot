package com.trimble.ttm.formlibrary.utils.ext

import com.trimble.ttm.formlibrary.http.BuildEnvironment
import com.trimble.ttm.formlibrary.utils.*
import java.util.*

fun BuildEnvironment.getBaseUrl(): String {
    return when (this.toString().lowercase(Locale.getDefault())) {
        FLAVOR_DEV -> BASE_URL_PREFIX + HOST_REGION + HYPHEN_SEPARATOR + PROJECT_ID_DEV + BASE_URL_SUFFIX
        FLAVOR_QA -> BASE_URL_PREFIX + HOST_REGION + HYPHEN_SEPARATOR + PROJECT_ID_QA + BASE_URL_SUFFIX
        FLAVOR_STG -> BASE_URL_PREFIX + HOST_REGION + HYPHEN_SEPARATOR + PROJECT_ID_STG + BASE_URL_SUFFIX
        else -> BASE_URL_PREFIX + HOST_REGION + HYPHEN_SEPARATOR + PROJECT_ID_PROD + BASE_URL_SUFFIX
    }
}

