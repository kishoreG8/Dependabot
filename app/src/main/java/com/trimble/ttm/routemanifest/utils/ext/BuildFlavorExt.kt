package com.trimble.ttm.routemanifest.utils.ext

import com.trimble.launchercommunicationlib.commons.model.BuildEnvironment
import com.trimble.ttm.routemanifest.utils.*

fun String.getPanelClientLibBuildEnvironment(): BuildEnvironment {
    return when (this) {
        FLAVOR_STG -> BuildEnvironment.Stg
        else -> BuildEnvironment.Prod
    }
}

fun String.getConsumerKey(): String {
    return when (this) {
        FLAVOR_DEV, FLAVOR_QA, FLAVOR_STG -> CONSUMER_KEY
        else -> PROD_CONSUMER_KEY
    }
}

fun String.getAppLauncherFullPackageName(): String {
    return when (this) {
        FLAVOR_STG -> "$LAUNCHER_PACKAGE_NAME_PREFIX.$FLAVOR_STG"
        else -> LAUNCHER_PACKAGE_NAME_PREFIX
    }
}