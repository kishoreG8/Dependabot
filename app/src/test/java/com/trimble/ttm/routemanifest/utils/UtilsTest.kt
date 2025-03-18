package com.trimble.ttm.routemanifest.utils

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import com.trimble.ttm.commons.model.SiteCoordinate
import com.trimble.ttm.formlibrary.R
import com.trimble.ttm.formlibrary.utils.EMPTY_STRING
import com.trimble.ttm.commons.preferenceManager.DataStoreManager
import com.trimble.ttm.commons.utils.CIRCULAR
import com.trimble.ttm.commons.utils.POLYGON
import com.trimble.ttm.routemanifest.model.Address
import com.trimble.ttm.routemanifest.model.RouteData
import com.trimble.ttm.routemanifest.repo.isAppLauncherWithMapsPerformanceFixInstalled
import com.trimble.ttm.routemanifest.utils.Utils.fromJsonString
import com.trimble.ttm.routemanifest.utils.Utils.getAppLauncherVersionAndSaveInMemory
import com.trimble.ttm.routemanifest.utils.Utils.getGsonInstanceWithIsoDateFormatter
import com.trimble.ttm.routemanifest.utils.Utils.getRouteData
import com.trimble.ttm.routemanifest.utils.Utils.getTripPanelIconUri
import com.trimble.ttm.routemanifest.utils.Utils.getUniqueIdentifier
import com.trimble.ttm.routemanifest.utils.Utils.toJsonString
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.spyk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import kotlin.test.assertFalse

class UtilsTest {

    private lateinit var dataStoreManager: DataStoreManager
    private lateinit var context: Context

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val coordinates = mutableListOf(SiteCoordinate(23.5, 12.7), SiteCoordinate(88.5, -98.7))
    private val emptyCoordinates = mutableListOf(SiteCoordinate(0.0,0.0))

    @Before
    fun setUp(){
        context = mockk()
        dataStoreManager = spyk(DataStoreManager(context))
        every { context.applicationContext } returns context
        every { context.filesDir } returns temporaryFolder.newFolder()
        mockkObject(AppBuildConfig)
    }

    @Test
    fun `should return zero hours and zero minutes if start and end time are same`() {    //NOSONAR
        val hrsMinPair =
            Utils.getDiffInHrsAndMinsRemAsPair(Calendar.getInstance(), Calendar.getInstance())
        assertEquals(0, hrsMinPair.first)
        assertEquals(0, hrsMinPair.second)
    }

    @Test
    fun `should return difference in hours with zero minutes`() {    //NOSONAR
        val startCal = Calendar.getInstance()
        val endCal = Calendar.getInstance()
        endCal.add(Calendar.HOUR, 1)

        val hrsMinPair = Utils.getDiffInHrsAndMinsRemAsPair(startCal, endCal)
        assertEquals(1, hrsMinPair.first)
        assertEquals(0, hrsMinPair.second)
    }

    @Test
    fun `should return difference in hours and minutes`() {    //NOSONAR
        val startCal = Calendar.getInstance()
        val endCal = Calendar.getInstance()
        endCal.add(Calendar.HOUR, 1)
        endCal.add(Calendar.MINUTE, 30)

        val hrsMinPair = Utils.getDiffInHrsAndMinsRemAsPair(startCal, endCal)
        assertEquals(1, hrsMinPair.first)
        assertEquals(30, hrsMinPair.second)
    }

    @Test
    fun `should return formatted address based on country code if address field available`() {    //NOSONAR
        val address = mockk<Address>()
        every { address.name } returns ""
        every { address.address } returns "15153 Highwood Drive"
        every { address.city } returns "Hennepin"
        every { address.state } returns "MN"
        every { address.zip } returns "55345"

        val formattedAddress = Utils.getFormattedAddress(address)

        assertEquals("15153 Highwood Drive\nHennepin, MN 55345", formattedAddress)
    }

    @Test
    fun `should return formatted address based on country code if address field not available`() {    //NOSONAR
        val address = mockk<Address>()
        every { address.name } returns ""
        every { address.address } returns ""
        every { address.city } returns "Hennepin"
        every { address.state } returns "MN"
        every { address.zip } returns "55345"

        val formattedAddress = Utils.getFormattedAddress(address)

        assertEquals("Hennepin, MN 55345", formattedAddress)
    }

    @Test
    fun `should return formatted address based on country code if address field available for next stop address in trip panel`() {    //NOSONAR
        val address = mockk<Address>()
        every { address.name } returns ""
        every { address.address } returns "15153 Highwood Drive"
        every { address.city } returns "Hennepin"
        every { address.state } returns "MN"
        every { address.zip } returns "55345"

        val formattedAddress = Utils.getFormattedAddress(address, true)

        assertEquals("15153 Highwood Drive, Hennepin, MN 55345", formattedAddress)
    }

    @Test
    fun `should return formatted address based on country code if address field not available for next stop address in trip panel`() {    //NOSONAR
        val address = mockk<Address>()
        every { address.name } returns ""
        every { address.address } returns ""
        every { address.city } returns "Hennepin"
        every { address.state } returns "MN"
        every { address.zip } returns "55345"

        val formattedAddress = Utils.getFormattedAddress(address, true)

        assertEquals("Hennepin, MN 55345", formattedAddress)
    }

    @Test
    fun `should return hours from minutes if minute less than or equal to zero`() {    //NOSONAR
        assertEquals("0:00", Utils.getHoursFromMinutes(0f))
    }

    @Test
    fun `should return hours from minutes if minute is greater than 60`() {    //NOSONAR
        assertEquals("01:06", Utils.getHoursFromMinutes(66f))
    }

    @Test
    fun `should return hours from minutes if hours greater than 24`() {    //NOSONAR
        assertEquals("24:59", Utils.getHoursFromMinutes(1499f))
    }

    @Test
    fun `should return hours from minutes if days greater than 4 and less than 41`() {    //NOSONAR
        assertEquals("100:00", Utils.getHoursFromMinutes(6000f))
    }

    @Test
    fun `verify return values of getTripPanelIconUri stg debug`() {    //NOSONAR
        mockkObject(AppBuildConfig)
        val applicationId = "com.trimble.ttm.formsandworkflow.stg.debug"

        `assert getTripPanelIconUri`(applicationId)
    }

    @Test
    fun `verify return values of getTripPanelIconUri stg release`() {    //NOSONAR
        mockkObject(AppBuildConfig)
        val applicationId = "com.trimble.ttm.formsandworkflow.stg.release"

        `assert getTripPanelIconUri`(applicationId)
    }

    @Test
    fun `verify return values of getTripPanelIconUri prod debug`() {    //NOSONAR
        mockkObject(AppBuildConfig)
        val applicationId = "com.trimble.ttm.formsandworkflow.prod.debug"

        `assert getTripPanelIconUri`(applicationId)
    }

    @Test
    fun `verify return values of getTripPanelIconUri prod release`() {    //NOSONAR
        val applicationId = "com.trimble.ttm.formsandworkflow.prod.release"

        `assert getTripPanelIconUri`(applicationId)
    }

    private fun `assert getTripPanelIconUri`(applicationId: String) = runTest {    //NOSONAR
        coEvery { AppBuildConfig.getApplicationId() } returns applicationId

        assertEquals(
            "android.resource://$applicationId/drawable/$TRIP_PANEL_DID_YOU_ARRIVE_MSG_ICON_FILE_NAME",
            getTripPanelIconUri(TRIP_PANEL_DID_YOU_ARRIVE_MSG_ICON_FILE_NAME)
        )
        assertEquals(
            "android.resource://$applicationId/drawable/$TRIP_PANEL_COMPLETE_FORM_MSG_ICON_FILE_NAME",
            getTripPanelIconUri(TRIP_PANEL_COMPLETE_FORM_MSG_ICON_FILE_NAME)
        )
        assertEquals(
            "android.resource://$applicationId/drawable/$TRIP_PANEL_SELECT_STOP_MSG_ICON_FILE_NAME",
            getTripPanelIconUri(TRIP_PANEL_SELECT_STOP_MSG_ICON_FILE_NAME)
        )
        assertEquals(
            "android.resource://$applicationId/drawable/$TRIP_PANEL_MILES_AWAY_MSG_ICON_FILE_NAME",
            getTripPanelIconUri(TRIP_PANEL_MILES_AWAY_MSG_ICON_FILE_NAME)
        )
    }

    @Test
    fun `verify return values of getUniqueIdentifier`() {    //NOSONAR
        assertEquals(
            "",
            getUniqueIdentifier("")
        )
        assertEquals(
            "10119-INST660-11000660-lorentzo",
            getUniqueIdentifier("10119", "INST660", "11000660", "lorentzo")
        )
    }

    @Test
    fun `verify false if package was not installed`() {
        val spykPackageManger = spyk<PackageManager>()
        every {
            spykPackageManger["getPackageInfo"](
                any<String>(),
                any<Int>()
            )
        } throws PackageManager.NameNotFoundException()
        assertEquals(false, Utils.isPackageInstalled("com.trimble.ttm.applauncher", spykPackageManger))
    }

    @Test
    fun `verify true if package was installed`() {
        val spykPackageManger = spyk<PackageManager>()
        every { spykPackageManger["getPackageInfo"](any<String>(), any<Int>()) } returns PackageInfo()
        assertEquals(true, Utils.isPackageInstalled("com.trimble.ttm.applauncher", spykPackageManger))
    }

    @Test
    fun `verify date value deserialize object json string`(){
        val routeData = RouteData(Date(),null,null)
        val routeDataString = toJsonString(routeData, getGsonInstanceWithIsoDateFormatter())
        routeDataString?.let {
            val deserailizedRouteData = fromJsonString<RouteData>(routeDataString,
                getGsonInstanceWithIsoDateFormatter())
            assertEquals(routeData.etaTime.time,deserailizedRouteData?.etaTime?.time)
        }
    }

    @Test
    fun `verify null deserialize empty string`(){
        val deserailizedRouteData = fromJsonString<RouteData>("",
            getGsonInstanceWithIsoDateFormatter())
        assertEquals(null,deserailizedRouteData)
    }

    @Test
    fun `verify null deserialize non json string`(){
        val routeData = fromJsonString<RouteData>("abcdefghijk", getGsonInstanceWithIsoDateFormatter())
        assertEquals(null,routeData)
    }

    @Test
    fun `verify routeData for valid trip with 3 stops`() = runTest {
        coEvery { dataStoreManager.containsKey(DataStoreManager.ROUTE_DATA_KEY) } returns true
        coEvery { dataStoreManager.getValue(DataStoreManager.ROUTE_DATA_KEY, EMPTY_STRING) } returns "{\"0\":{\"address\":{\"address\":\"I-494, MN-5\",\"city\":\"Minneapolis\",\"country\":\"\",\"county\":\"Hennepin\",\"name\":\"\",\"state\":\"MN\",\"zip\":\"55435\"},\"etaTime\":\"2022-04-05T14:00:21.000Z\"},\"1\":{\"address\":{\"address\":\"France Avenue South \\u0026 Gallagher Drive\",\"city\":\"Minneapolis\",\"country\":\"\",\"county\":\"Hennepin\",\"name\":\"\",\"state\":\"MN\",\"zip\":\"55435\"},\"etaTime\":\"2022-04-05T14:05:24.000Z\",\"leg\":{\"distance\":3.9,\"time\":5.5}},\"2\":{\"address\":{\"address\":\"3151 West 66th Street  (Route 53)\",\"city\":\"Minneapolis\",\"country\":\"\",\"county\":\"Hennepin\",\"name\":\"\",\"state\":\"MN\",\"zip\":\"55435\"},\"etaTime\":\"2022-04-05T14:07:24.000Z\",\"leg\":{\"distance\":1.3,\"time\":2.0}}}"
        val routeData = getRouteData(1,dataStoreManager)
        val sdf = SimpleDateFormat(ISO_DATE_TIME_FORMAT)
        assertEquals("2022-04-05T14:05:24.000Z", routeData?.etaTime?.let { sdf.format(it) })
        assertEquals(3.9,routeData?.leg?.distance)
        assertEquals(5.5,routeData?.leg?.time)
        assertEquals("France Avenue South & Gallagher Drive",routeData?.address?.address)
    }

    @Test
    fun `verify routeData for valid trip with 1 stop`() = runTest {
        coEvery { dataStoreManager.containsKey(DataStoreManager.ROUTE_DATA_KEY) } returns true
        coEvery { dataStoreManager.getValue(DataStoreManager.ROUTE_DATA_KEY, EMPTY_STRING) } returns "{\"0\":{\"address\":{\"address\":\"I-494, MN-5\",\"city\":\"Minneapolis\",\"country\":\"\",\"county\":\"Hennepin\",\"name\":\"\",\"state\":\"MN\",\"zip\":\"55435\"},\"etaTime\":\"2022-04-05T14:00:21.000Z\"}}"
        val routeData = getRouteData(0,dataStoreManager)
        val sdf = SimpleDateFormat(ISO_DATE_TIME_FORMAT)
        assertEquals("2022-04-05T14:00:21.000Z", routeData?.etaTime?.let { sdf.format(it) })
        assertEquals(null,routeData?.leg)
        assertEquals("I-494, MN-5",routeData?.address?.address)
    }

    @Test
    fun `verify routeData for invalidRouteData`() = runTest {
        coEvery { dataStoreManager.containsKey(DataStoreManager.ROUTE_DATA_KEY) } returns true
        coEvery { dataStoreManager.getValue(DataStoreManager.ROUTE_DATA_KEY, EMPTY_STRING) } returns "{\"address\":{\"address\":\"I-494, MN-5\",\"city\":\"Minneapolis\",\"country\":\"\",\"county\":\"Hennepin\",\"name\":\"\",\"state\":\"MN\",\"zip\":\"55435\"},\"etaTime\":\"2022-04-05T14:00:21.000Z\"}"
        val routeData = getRouteData(0,dataStoreManager)
        assertEquals(null,routeData?.etaTime)
        assertEquals(null,routeData?.leg)
        assertEquals(null,routeData?.address)
    }

    @Test
    fun `verify getIntentDataErrorString returns a string with the expected values`(){
        val dataName = "value"
        val dataType = "Int"
        val nullOrEmpty = "null"
        val actionName = "myIntent"
        val expected = "RECEIVED DATA INTENT ERROR ---> $dataName $dataType is $nullOrEmpty when is got from an intent. action: $actionName"
        every {
            context.getString(
                R.string.intent_received_data_error,
                dataName,
                dataType,
                nullOrEmpty,
                actionName
            )
        } returns expected
        val result = Utils.getIntentDataErrorString(
            context,
            dataName,
            dataType,
            nullOrEmpty,
            actionName
        )
        assertTrue(result==expected)
    }

    @Test
    fun `verify isAppLauncherWithMapsPerformanceFixInstalled is getting assigned when value is present in datastore`() = runTest {
        isAppLauncherWithMapsPerformanceFixInstalled = false
        coEvery { dataStoreManager.containsKey(DataStoreManager.IS_APP_LAUNCHER_WITH_PERFORMANCE_FIX_INSTALLED) } returns true
        coEvery { dataStoreManager.getValue(DataStoreManager.IS_APP_LAUNCHER_WITH_PERFORMANCE_FIX_INSTALLED, false) } returns true
        getAppLauncherVersionAndSaveInMemory(dataStoreManager)
        assertTrue(isAppLauncherWithMapsPerformanceFixInstalled)
    }

    @Test
    fun `verify isAppLauncherWithMapsPerformanceFixInstalled is getting assigned when value is not present in datastore`() = runTest {
        isAppLauncherWithMapsPerformanceFixInstalled = false
        coEvery { dataStoreManager.containsKey(DataStoreManager.IS_APP_LAUNCHER_WITH_PERFORMANCE_FIX_INSTALLED) } returns false
        coEvery { dataStoreManager.getValue(DataStoreManager.IS_APP_LAUNCHER_WITH_PERFORMANCE_FIX_INSTALLED, false) } returns true
        getAppLauncherVersionAndSaveInMemory(dataStoreManager)
        assertFalse(isAppLauncherWithMapsPerformanceFixInstalled)
    }

    @Test
    fun `Geofence type is Circular if polygonal is opted out or site coordinates are empty`() {
        assertEquals(Utils.getGeofenceType(siteCoordinates = coordinates, isPolygonalOptOut = true), CIRCULAR)
        assertEquals(Utils.getGeofenceType(siteCoordinates = emptyCoordinates, isPolygonalOptOut = false), CIRCULAR)
        assertEquals(Utils.getGeofenceType(siteCoordinates = mutableListOf(), isPolygonalOptOut = true), CIRCULAR)
    }

    @Test
    fun `Geofence type is polygonal if both polygonal is opted and site coordinates exist`() {
        assertEquals(Utils.getGeofenceType(siteCoordinates = coordinates, isPolygonalOptOut = false), POLYGON)
    }

}