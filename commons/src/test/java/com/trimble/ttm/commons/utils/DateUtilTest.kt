package com.trimble.ttm.commons.utils

import android.content.Context
import android.text.format.DateFormat
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.utils.DateUtil.calculateTimeDifference
import com.trimble.ttm.commons.utils.DateUtil.checkIfDeviceTimeFormatIs24HourFormatOrNot
import com.trimble.ttm.commons.utils.DateUtil.convertDateStringToMilliseconds
import com.trimble.ttm.commons.utils.DateUtil.convertMillisToDateWithGivenFormat
import com.trimble.ttm.commons.utils.DateUtil.convertMillisecondsToDeviceFormatDate
import com.trimble.ttm.commons.utils.DateUtil.convertToTimeString
import com.trimble.ttm.commons.utils.DateUtil.getUTCTimeFormatterWithPattern
import com.trimble.ttm.commons.utils.DateUtil.toUTC
import com.trimble.ttm.commons.utils.ext.isNotNull
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.text.SimpleDateFormat
import java.time.ZoneId
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assertions.assertAll
import java.time.Instant
import java.time.temporal.ChronoUnit

class DateUtilTest {

    private lateinit var context: Context

    private lateinit var dateFormat: DateFormat

    @Before
    fun setUp(){
        context = mockk()
        dateFormat = mockk()
        mockkObject(Log)
        mockkObject(OSBuildVersionWrapper)
        mockkStatic(DateFormat::class)
    }

    @Test
    fun `verify convert to system date time format with MM dd yy HH mm ss from date string`(){

        every {
            DateFormat.getDateFormat(context)
        } returns SimpleDateFormat("MM/dd/yy")

        every {
            DateFormat.getTimeFormat(context)
        } returns SimpleDateFormat("HH:mm:ss")

        val systemFormattedDateString = DateUtil.convertToSystemDateTimeFormat("02/22/21 10:15:00", context)

        val expectedDateString = "02/22/21 10:15:00"

        Assert.assertEquals(expectedDateString,systemFormattedDateString)
    }

    @Test
    fun `verify convert to system date time format with dd MM yy HH mm ss from date string`(){

        every {
            DateFormat.getDateFormat(context)
        } returns SimpleDateFormat("dd/MM/yy")

        every {
            DateFormat.getTimeFormat(context)
        } returns SimpleDateFormat("HH:mm:ss")

        val systemFormattedDateString = DateUtil.convertToSystemDateTimeFormat("02/22/21 10:15:00", context)

        val expectedDateString = "22/02/21 10:15:00"

        Assert.assertEquals(expectedDateString,systemFormattedDateString)
    }
    @Test
    fun `verify convert to system date time format with dd MM yy HH mm from date string`(){

        every {
            DateFormat.getDateFormat(context)
        } returns SimpleDateFormat("dd/MM/yy")

        every {
            DateFormat.getTimeFormat(context)
        } returns SimpleDateFormat("HH:mm")

        val systemFormattedDateString = DateUtil.convertToSystemDateTimeFormat("02/22/21 10:15:00", context)

        val expectedDateString = "22/02/21 10:15"

        Assert.assertEquals(expectedDateString,systemFormattedDateString)
    }


    @Test
    fun `verify convert to system date with dd MM yy from date string`(){

        every {
            DateFormat.getDateFormat(context)
        } returns SimpleDateFormat("dd/MM/yy")

        val systemFormattedDateString = DateUtil.convertToSystemDateFormat("02/22/21", context)

        val expectedDateString = "22/02/21"

        Assert.assertEquals(expectedDateString,systemFormattedDateString)
    }

    @Test
    fun `verify convert to system date with MM dd yy from date string`(){

        every {
            DateFormat.getDateFormat(context)
        } returns SimpleDateFormat("MM/dd/yy")

        val systemFormattedDateString = DateUtil.convertToSystemDateFormat("02/22/21", context)

        val expectedDateString = "02/22/21"

        Assert.assertEquals(expectedDateString,systemFormattedDateString)
    }

    @Test
    fun `verify convert to system time with HH mmm ss from date string`(){

        every {
            DateFormat.getTimeFormat(context)
        } returns SimpleDateFormat("HH:mm:ss")

        val systemFormattedDateString = DateUtil.convertToSystemTimeFormat("14:30:12", context)

        val expectedDateString = "14:30:12"

        Assert.assertEquals(expectedDateString,systemFormattedDateString)
    }

    @Test
    fun `verify convert to system time with hh mm ss a from date string`(){
        every {
            DateFormat.getTimeFormat(context)
        } returns SimpleDateFormat("hh:mm:ss a")

        val systemFormattedDateString = DateUtil.convertToSystemTimeFormat("14:30:12", context)

        val expectedDateString = "02:30:12 PM"

        Assert.assertEquals(expectedDateString,systemFormattedDateString.uppercase())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `check getTimeString() returns the correct time string when is24HourTimeFormat is true`() = runTest {
        every { Log.d(any(),any()) } returns ""
        val calenderToRetrieveTime = Calendar.getInstance()
        calenderToRetrieveTime.set(Calendar.HOUR_OF_DAY,13)
        calenderToRetrieveTime.set(Calendar.MINUTE,0)
        calenderToRetrieveTime.set(Calendar.SECOND,0)
        val dateTimeFormat = SimpleDateFormat("HH:mm")
        val coroutineDispatcher = StandardTestDispatcher(testScheduler)
        val actualOutput = convertToTimeString(coroutineDispatcher = coroutineDispatcher, calendarToRetrieveTime = calenderToRetrieveTime, dateTimeFormat = dateTimeFormat, dispatchId = "1234", tag = "Test", is24HourTimeFormat = true)
        assertEquals("13:00",actualOutput)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `check getTimeString() returns the correct time string when is24HourTimeFormat is false`() = runTest {
        every { Log.d(any(),any()) } returns ""
        val calenderToRetrieveTime = Calendar.getInstance()
        calenderToRetrieveTime.set(Calendar.HOUR_OF_DAY,13)
        calenderToRetrieveTime.set(Calendar.MINUTE,0)
        calenderToRetrieveTime.set(Calendar.SECOND,0)
        val dateTimeFormat = SimpleDateFormat("hh:mm a")
        val coroutineDispatcher = StandardTestDispatcher(testScheduler)
        val actualOutput = convertToTimeString(coroutineDispatcher = coroutineDispatcher, calendarToRetrieveTime = calenderToRetrieveTime, dateTimeFormat = dateTimeFormat, dispatchId = "1234", tag = "Test", is24HourTimeFormat = false)
        assertEquals("01:00 PM",actualOutput.uppercase())
    }

    @Test
    fun `check if device time format is 24hr or not`(){
        every { DateFormat.is24HourFormat(context) } returns true
        assertTrue(checkIfDeviceTimeFormatIs24HourFormatOrNot(context = context))
    }

    @Test
    fun `check if device time format is 12hr or not`(){
        every { DateFormat.is24HourFormat(null) } returns false
        assertFalse(checkIfDeviceTimeFormatIs24HourFormatOrNot(context = null))
    }

    @Test
    fun `get time difference between dates`(){
        val calender = Calendar.getInstance()
        val startDate = calender.time
        calender.add(Calendar.MINUTE,30)
        val endDate = calender.time
        assertEquals("0 hours 30 minutes 0 seconds", calculateTimeDifference(startDate,endDate))
    }

    @Test
    fun `check getUTCTimeFormatterWithPattern with valid pattern`(){
        val format = getUTCTimeFormatterWithPattern(DATE_FORMAT)
        Assert.assertEquals(DATE_FORMAT,format.toPattern())
    }

    @Test
    fun `check getUTCTimeFormatterWithPattern with invalid pattern`(){
        val format = getUTCTimeFormatterWithPattern("")
        Assert.assertEquals(true,format.calendar.time.isNotNull())
    }
    @Test
    fun `check convertDateStringToMillis with valid date and pattern`(){
        every {
            DateFormat.getDateFormat(context)
        } returns SimpleDateFormat("dd/MM/yy")

        val timeInMillis = convertDateStringToMilliseconds("01/02/2023", context)
        Assert.assertTrue(timeInMillis>0)
    }

    @Test
    fun `check convertDateStringToMillis with invalid date and pattern`(){
        val timeInMillis = convertDateStringToMilliseconds("invalid date", context)
        Assert.assertEquals(0L,timeInMillis)
    }
    @Test
    fun `check convertMillisecondsToDeviceFormatDate with valid timestamp`(){
        every {
            DateFormat.getDateFormat(context)
        } returns SimpleDateFormat("dd/MM/yy")

        val date = convertMillisecondsToDeviceFormatDate(System.currentTimeMillis(),context)
        Assert.assertTrue(date.isNotEmpty())
    }

    @Test
    fun `check convertMillisecondsToDeviceFormatDate with invalid timestamp`(){
        val date = convertMillisecondsToDeviceFormatDate(0,context)
        Assert.assertEquals(date, EMPTY_STRING)
    }

    @Test
    fun `check convertMillisToDateWithGivenFormat with valid timestamp`(){

        val date = convertMillisToDateWithGivenFormat(System.currentTimeMillis(),"dd/MM/yy")
        Assert.assertTrue(date.isNotEmpty())
    }

    @Test
    fun `check convertMillisToDateWithGivenFormat with invalid timestamp`(){
        val date = convertMillisToDateWithGivenFormat(0,"invalid format")
        Assert.assertEquals(date, EMPTY_STRING)
    }

    @Test
    fun `check getHourAndMinuteFromTimeString with valid timeString and format`(){
        every {
            DateUtil.getSystemTimeFormat(context).toPattern()
        } returns TIME_FORMAT_TO_BE_SENT_TO_SERVER
        val time = DateUtil.getHourAndMinuteFromTimeString(
            "11:20:00",
            DateUtil.getSystemTimeFormat(context).toPattern()
        )
        Assert.assertEquals(11,time.first)
        Assert.assertEquals(20,time.second)
    }

    @Test
    fun `check getHourAndMinuteFromTimeString with invalid timeString and format`(){
        every {
            DateUtil.getSystemTimeFormat(context).toPattern()
        } returns "HHMMTTED"
        val time = DateUtil.getHourAndMinuteFromTimeString(
            "112000",
            DateUtil.getSystemTimeFormat(context).toPattern()
        )
        Assert.assertEquals(0,time.first)
        Assert.assertEquals(0,time.second)
    }
    @Test
    fun `check getTimeFormat with valid timeString`(){
        every {
            DateUtil.getSystemTimeFormat(context).toPattern()
        } returns TIME_FORMAT_TO_BE_SENT_TO_SERVER
        val deviceTimeFormat =
            DateUtil.getTimeFormat(DateUtil.getSystemTimeFormat(context).toPattern())
        Assert.assertTrue(deviceTimeFormat.toPattern().isNotEmpty())
    }

    @Test
    fun `check if getDeviceLocalTimeInUTC returns the correct time in UTC`() {
        val calendar = Calendar.getInstance()
        val expectedTime = calendar.timeInMillis
        val actualTime = DateUtil.getDeviceLocalTimeInUTC()
        assertTrue(actualTime - expectedTime < 10000, "Expected time fetches time before actualTime. So, there will be some delay in actualTime. We have given 10 secs as buffer time")
    }

    @Test
    fun `check if toUTC returns correct time in UTC`() =
        assertEquals(1696863000000L, "2023-10-09T14:50:00.000Z".toUTC("test"))

    @Test
    fun `check if toUTC returns 0L if it throws exception`() {
        every { Log.e(any(),any()) } returns ""
        assertEquals(0L, "2023-10-09T14:50:00".toUTC("test"))
    }

    @Test
    fun `check if toUTC returns 0L if eta is empty`() {
        every { Log.e(any(),any()) } returns ""
        assertEquals(0L, "".toUTC("test"))
    }

    @Test
    fun `check if getMillisFromUTCDateTime returns the correct millis in UTC`() {
        assertEquals(1696863000000L, DateUtil.getMillisFromUTCDateTime("2023-10-09T14:50:00.000Z", "test"))
    }

    @Test
    fun `check if getMillisFromUTCDateTime returns 0L for invalid utcDateTime`() {
        assertEquals(0L, DateUtil.getMillisFromUTCDateTime("2023-10-09T14:50", "test"))
    }

    @Test
    fun `check time difference of two dates in timestamp`() {
        val date1 = "2021-10-09T14:50:00.000Z"
        val date2 = "2021-10-09T14:55:00.000Z"
        assertEquals(300, DateUtil.getDifferenceBetweenTwoDatesInSeconds(date1, date2))
    }

    @Test
    fun `check time difference of two dates if it is under a minute`() {
        val date1 = "2021-10-09T14:50:00.000Z"
        val date2 = "2021-10-09T14:50:10.000Z"
        assertEquals(10, DateUtil.getDifferenceBetweenTwoDatesInSeconds(date1, date2))
    }

    @Test
    fun `check time difference of two dates if created time is lesser than ready time in negative value`() {
        val date1 = "2021-10-09T14:50:00.000Z"
        val date2 = "2021-10-09T13:50:10.001Z"
        assertEquals(-3589, DateUtil.getDifferenceBetweenTwoDatesInSeconds(date1, date2))
    }

    @Test
    fun `check timestamp of date is as expected after conversion to utc`()= runTest {
        val dateFormat = SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy", Locale.getDefault())
        val date: Date = dateFormat.parse("Thu Jan 04 16:47:57 GMT+05:30 2024")
        assertEquals(1704367077000, DateUtil.convertDateInstanceToUTCTimestamp(
            date, "test"))
    }

    @Test
    fun `check timestamp of CST format date is as expected after conversion to utc`()= runTest {
        val dateFormat = SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy", Locale.US)
        val date = dateFormat.parse("Thu Jan 04 05:17:57 CST 2024")
        assertEquals(1704367077000, DateUtil.convertDateInstanceToUTCTimestamp(
            date, "test"))
    }

    @Test
    fun `check timestamp of EST format date is as expected after conversion to utc`()= runTest {
        val dateFormat = SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy", Locale.US)
        val date = dateFormat.parse("Thu Jan 04 06:17:57 EST 2024")
        assertEquals(1704367077000, DateUtil.convertDateInstanceToUTCTimestamp(
            date, "test"))
    }

    @Test
    fun `check difference of time is as expected`()= runTest {
        assertEquals(443484, DateUtil.getDifferenceInSeconds(1704367077000,"2024-01-09T14:29:21.178Z"))
    }

    @Test
    fun `check difference of time is as expected when user login time is in negative`()= runTest {
        // 1704810561 is equivalent to 2024-01-09T14:29:21.178Z
        assertEquals(1704810561, DateUtil.getDifferenceInSeconds(-0,"2024-01-09T14:29:21.178Z"))
    }

    @Test
    fun `check timestamp is as expected when build version is of Oreo`()= runTest {
        every { OSBuildVersionWrapper.isOsVersionOfOreoOrAbove() } returns true
        val dateFormat = SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy", Locale.US)
        val date = dateFormat.parse("Thu Jan 04 06:17:57 EST 2024")
        assertEquals(1704367077000, DateUtil.convertDateInstanceToUTCTimestamp(
            date, "test"))
    }

    @Test
    fun `check timestamp is as expected when build version is less than oreo`()= runTest {
        every { OSBuildVersionWrapper.isOsVersionOfOreoOrAbove() } returns false
        val dateFormat = SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy", Locale.US)
        val date = dateFormat.parse("Thu Jan 04 06:17:57 EST 2024")
        assertEquals(1704367077000, DateUtil.convertDateInstanceToUTCTimestamp(
            date, "test"))
    }

    @Test
    fun `getDateTimeInUTC should return current date and time in UTC with 30 days added`() {

        // Arrange
        val currentDate = Date()

        // Act
        val result = DateUtil.getExpireAtDateTimeForTTLInUTC()

        // Calculate the expected timestamp with 30 days added
        val expectedTimestamp = Instant.ofEpochMilli(currentDate.time)
            .plus(30, ChronoUnit.DAYS)
            .toEpochMilli()

        // Allow a small margin of error (e.g., 100 milliseconds)
        val marginOfError = 100

        // Assert
        assertAll(
            { assertTrue(result.time >= expectedTimestamp - marginOfError, "Check if 30 days added") },
            { assertTrue(result.time <= expectedTimestamp + marginOfError, "Check if 30 days added") },
            { assertEquals("UTC", result.toInstant().atZone(ZoneId.of("UTC")).zone.id, "Check if in UTC") }
        )

    }

    @After
    fun after() {
        unmockkAll()
    }

}