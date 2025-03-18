package com.trimble.ttm.routemanifest.exts

import com.trimble.launchercommunicationlib.commons.model.BuildEnvironment
import com.trimble.ttm.routemanifest.utils.FLAVOR_PROD
import com.trimble.ttm.routemanifest.utils.FLAVOR_STG
import com.trimble.ttm.routemanifest.utils.LAUNCHER_PACKAGE_NAME_PREFIX
import com.trimble.ttm.routemanifest.utils.ext.getAppLauncherFullPackageName
import com.trimble.ttm.routemanifest.utils.ext.getPanelClientLibBuildEnvironment
import org.junit.Assert
import org.junit.Test

class BuildFlavorExtTest {
    @Test
    fun `verify return values of build environment from flavor`() {    //NOSONAR
        Assert.assertEquals(BuildEnvironment.Stg, FLAVOR_STG.getPanelClientLibBuildEnvironment())
        Assert.assertEquals(BuildEnvironment.Prod, FLAVOR_PROD.getPanelClientLibBuildEnvironment())
    }

    @Test
    fun `verify return values of app launcher package name from flavor`() {    //NOSONAR
        Assert.assertEquals(
            "$LAUNCHER_PACKAGE_NAME_PREFIX.$FLAVOR_STG",
            FLAVOR_STG.getAppLauncherFullPackageName()
        )
        Assert.assertEquals(
            LAUNCHER_PACKAGE_NAME_PREFIX,
            FLAVOR_PROD.getAppLauncherFullPackageName()
        )
    }
}