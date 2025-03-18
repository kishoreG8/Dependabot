package com.trimble.ttm.commons.utils

import android.content.Context
import android.os.Build
import android.text.format.DateFormat
import com.trimble.ttm.commons.logger.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.text.ParseException
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object DateUtil {

    const val TAG = "Dateutil"

    fun convertToSystemDateFormat(dateString: String, context: Context): String {
        val dateFormat = SimpleDateFormat(DATE_FORMAT, Locale.getDefault())
        return if (dateString.isNotEmpty()) {
            getSystemDateFormat(context)
                .format(dateFormat.parse(dateString)!!)
        } else {
            ""
        }
    }

    fun convertToSystemDateTimeFormat(dateTimeString: String, context: Context): String {
        val dateFormat = getSystemDateFormat(context)
        val timeFormat = getSystemTimeFormat(context)

        val systemDateTimeFormat = SimpleDateFormat(
            dateFormat.toLocalizedPattern() + SPACE + timeFormat.toLocalizedPattern(),
            Locale.getDefault()
        )
        val serverDateTimeFormat = SimpleDateFormat(DATE_TIME_FORMAT, Locale.getDefault())
        if (dateTimeString.isNotEmpty()) {
            val date = serverDateTimeFormat.parse(dateTimeString)
            systemDateTimeFormat.timeZone = TimeZone.getDefault()
            return systemDateTimeFormat.format(date!!)
        }
        return ""
    }

    fun convertToSystemTimeFormat(timeString: String, context: Context): String {
        val systemTimeFormat =
            getSystemTimeFormat(context)
        val serverTimeFormat =
            SimpleDateFormat(TIME_FORMAT_TO_BE_SENT_TO_SERVER, Locale.getDefault())
        val date = serverTimeFormat.parse(timeString)
        return systemTimeFormat.format(date!!)
    }

    fun getUTCFormattedDate(localDate: Date): String {
        try {
            return SimpleDateFormat(ISO_DATE_TIME_FORMAT, Locale.getDefault()).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.format(localDate)
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
        return ""
    }

    fun convertToServerDateFormat(dateString: String, context: Context): String {
        val dateFormat =
            DateFormat.getDateFormat(context) as SimpleDateFormat
        return if (dateString.isNotEmpty()) SimpleDateFormat(
            DATE_FORMAT_TO_BE_SENT_TO_SERVER,
            Locale.getDefault()
        ).format(dateFormat.parse(dateString)!!) else {
            ""
        }
    }

    fun convertToServerTimeFormat(timeString: String, context: Context): String {
        val dateFormat =
            getSystemDateFormat(context)
        val timeFormat =
            getSystemTimeFormat(context)
        val sdfToParse = SimpleDateFormat(
            dateFormat.toLocalizedPattern() + SPACE + timeFormat.toLocalizedPattern(),
            Locale.getDefault()
        )
        return if (timeString.isNotEmpty()) {
            try {
                StringBuilder().append(dateFormat.format(Calendar.getInstance().time))
                    .append(SPACE)
                    .append(timeString).toString().let {
                        SimpleDateFormat(
                            TIME_FORMAT_TO_BE_SENT_TO_SERVER,
                            Locale.getDefault()
                        ).format(
                            sdfToParse.parse(it)!!
                        )
                    }
            } catch (e: ParseException) {
                ""
            }
        } else {
            ""
        }
    }

    fun convertToServerDateTimeFormat(dateTimeString: String, context: Context): String {
        val dateFormat =
            getSystemDateFormat(context)
        val timeFormat =
            getSystemTimeFormat(context)
        val sdfToParse = SimpleDateFormat(
            dateFormat.toLocalizedPattern() + SPACE + timeFormat.toLocalizedPattern(),
            Locale.getDefault()
        )
        return if (dateTimeString.isNotEmpty()) {
            try {
                SimpleDateFormat(DATE_TIME_FORMAT_TO_SERVER, Locale.getDefault()).format(
                    sdfToParse.parse(
                        dateTimeString
                    )!!
                )
            } catch (e: ParseException) {
                ""
            }
        } else {
            ""
        }
    }

    fun getDate(dateTimeFormat: String, time: String): Date =
        try {
            if (time.isEmpty()) Date()
            SimpleDateFormat(dateTimeFormat, Locale.getDefault()).parse(time)
                ?.let { selectedDateTime ->
                    return@let selectedDateTime
                } ?: Date()
        } catch (e: ParseException) {
            Log.e(
                TAG,
                "Exception in getDate while parsing date value: $time dateFormatToParse: $dateTimeFormat",
                e
            )
            Date()
        }

    fun getCalendar(): Calendar = Calendar.getInstance()


    fun getCurrentTimeInMillisInUTC() =
        Calendar.getInstance(TimeZone.getTimeZone("UTC")).timeInMillis

    fun getSystemTimeFormat(context: Context) =
        DateFormat.getTimeFormat(context) as SimpleDateFormat

    fun getSystemDateFormat(context: Context) =
        DateFormat.getDateFormat(context) as SimpleDateFormat

    fun getSystemDateTimeFormatString(context: Context) =
        getSystemDateFormat(context).toLocalizedPattern() + SPACE + getSystemTimeFormat(context).toLocalizedPattern()

    fun checkIfDeviceTimeFormatIs24HourFormatOrNot(context: Context?): Boolean {
        return if (context != null) {
            DateFormat.is24HourFormat(context)
        } else {
            false
        }
    }

    suspend fun convertToTimeString(
        coroutineDispatcher: CoroutineDispatcher,
        calendarToRetrieveTime: Calendar,
        dateTimeFormat: SimpleDateFormat,
        dispatchId: String,
        tag: String,
        is24HourTimeFormat: Boolean
    ): String = withContext(coroutineDispatcher) {
        return@withContext if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val timeZoneId = calendarToRetrieveTime.timeZone.toZoneId()
            val dateTime = calendarToRetrieveTime.toInstant()
                .atZone(timeZoneId).toLocalDateTime()
            Log.d(
                tag,
                "Dispatch Id $dispatchId, Incoming Time ${calendarToRetrieveTime.time}, Time Zone ${timeZoneId.id}"
            )
            val timePattern =
                if (is24HourTimeFormat) TWENTY_FOUR_HOUR_TIME_PATTERN else TWELVE_HOUR_TIME_PATTERN
            val formatter = DateTimeFormatter.ofPattern(timePattern)
            val timeString = dateTime.format(formatter)
            calendarToRetrieveTime.add(Calendar.HOUR, 1)
            timeString
        } else {
            Log.d(
                tag,
                "Dispatch Id $dispatchId, Incoming Time ${calendarToRetrieveTime.time}, Time Zone ${calendarToRetrieveTime.timeZone.displayName}"
            )
            val timeString = dateTimeFormat.format(calendarToRetrieveTime.time)
            calendarToRetrieveTime.add(Calendar.HOUR, 1)
            timeString
        }
    }

    fun String.toUTC(caller: String): Long {
        if (this.isEmpty()) return 0L
        return try {
            val date = getUTCTimeFormatter().parse(this) ?: return Date().time
            date.time
        } catch (e: Exception) {
            Log.e(
                caller,
                "Exception converting the time to utc ${e.stackTraceToString()}"
            )
            0L
        }
    }

    fun getDeviceLocalTimeInUTC(): Long {
        return getUTCTimeFormatter().let { formatter ->
            formatter.parse(formatter.format(Date()))?.time ?: 0L
        }
    }

    private fun getUTCTimeFormatter(): SimpleDateFormat {
        val formatter = SimpleDateFormat(ISO_DATE_TIME_FORMAT, Locale.getDefault())
        formatter.timeZone = TimeZone.getTimeZone(UTC_TIME_ZONE_ID)
        return formatter
    }

    /**
     * This method converts the DateTime string to utc millis
     */
    fun getMillisFromUTCDateTime(utcDateTime: String, caller: String): Long {
        return try {
            val isoDateFormatter = SimpleDateFormat(ISO_DATE_TIME_FORMAT, Locale.getDefault())
            isoDateFormatter.timeZone = TimeZone.getTimeZone(UTC_TIME_ZONE_ID)
            isoDateFormatter.parse(utcDateTime)?.time ?: 0L
        } catch (e: Exception) {
            Log.e(
                caller,
                "Exception converting utcDateTime to utc millis ${e.stackTraceToString()}"
            )
            0L
        }
    }

    /**
     * This method converts the date instance to UTC timestamp
     */
    fun convertDateInstanceToUTCTimestamp(date: Date, caller: String): Long {
        return try {
            if (OSBuildVersionWrapper.isOsVersionOfOreoOrAbove()) {
                Instant.ofEpochMilli(date.time).toEpochMilli()
            } else {
                val formatter = SimpleDateFormat(ISO_DATE_TIME_FORMAT, Locale.getDefault())
                formatter.timeZone = TimeZone.getTimeZone(UTC_TIME_ZONE_ID)
                formatter.parse(formatter.format(date))?.time ?: 0L
            }
        } catch (e: Exception) {
            Log.e(caller, "Exception converting date to utc timestamp ${e.stackTraceToString()}")
            0L
        }
    }

    fun getDifferenceInSeconds(userLogInTime: Long, tripReadyTime: String): Long {
        val readyTimeDate = getMillisFromUTCDateTime(tripReadyTime, TAG)
        return (readyTimeDate - userLogInTime) / 1000
    }

    //Convert two dates to timestamps and get the difference in minutes
    fun getDifferenceBetweenTwoDatesInSeconds(created: String, tripReadyTime: String): Long {
        val createdDate = getMillisFromUTCDateTime(created, TAG)
        val readyTimeDate = getMillisFromUTCDateTime(tripReadyTime, TAG)
        return (readyTimeDate - createdDate) / 1000
    }

    fun calculateTimeDifference(startTime: Date, endTime: Date): String {
        val timeDiffInMilliSeconds = endTime.time - startTime.time
        val seconds = (timeDiffInMilliSeconds / 1000 % 60).toInt()
        val minutes = (timeDiffInMilliSeconds / (1000 * 60) % 60).toInt()
        val hours = (timeDiffInMilliSeconds / (1000 * 60 * 60)).toInt()
        return "$hours hours $minutes minutes $seconds seconds"
    }

    fun getUTCTimeFormatterWithPattern(pattern: String): SimpleDateFormat {
        val formatter = SimpleDateFormat(pattern, Locale.getDefault())
        formatter.timeZone = TimeZone.getTimeZone(UTC)
        return formatter
    }

    fun convertDateStringToMilliseconds(date: String, context: Context): Long {
        return try {
            getUTCTimeFormatterWithPattern(getSystemDateFormat(context).toPattern()).parse(date)?.time
                ?: 0L
        } catch (e: Exception) {
            Log.e(
                TAG,
                "Exception in convertDateStringToMilliseconds while parsing date value: $date",
                e
            )
            0L
        }
    }

    fun convertMillisecondsToDeviceFormatDate(
        milliseconds: Long,
        context: Context
    ): String {
        return try {
            val dateFormat = getSystemDateFormat(context)
            val date = Date(milliseconds)
            dateFormat.format(date)
        } catch (e: Exception) {
            Log.e(
                TAG,
                "Exception in convertMillisecondsToDeviceFormatDate while parsing milliseconds: $milliseconds",
                e
            )
            EMPTY_STRING
        }

    }

    fun convertMillisToDateWithGivenFormat(millis: Long, format: String): String {
        return try {
            val formatter = SimpleDateFormat(format, Locale.getDefault())
            formatter.format(Date(millis))
        } catch (e: Exception) {
            Log.e(
                TAG,
                "Exception in convertMillisToDateWithGivenFormat while parsing milliseconds: $millis and format: $format",
                e
            )
            EMPTY_STRING
        }
    }

    fun getHourAndMinuteFromTimeString(timeString: String, timeFormat: String): Pair<Int, Int> {
        return try {
            val format = SimpleDateFormat(timeFormat, Locale.getDefault())
            val date = format.parse(timeString)

            val calendar = getCalendar()
            if (date != null) {
                calendar.time = date
            }

            Pair(calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE))
        } catch (e: Exception) {
            Log.e(
                TAG,
                "Exception in getHourAndMinuteFromTimeString while parsing Hour and minute: $timeString",
                e
            )
            Pair(0, 0)
        }
    }

    /**
     * This method converts the date  instance string format of  24/08/07 09:39 to UTC timestamp
     * this is specific to custom workflow data date handling
     */
    fun convertToUTCFormattedDate(dateString: String): String {
        if (dateString.isEmpty()) return EMPTY_STRING
        return try {
            val inputFormat = SimpleDateFormat("yy/MM/dd HH:mm", Locale.getDefault())
            inputFormat.timeZone = TimeZone.getDefault()
            val date = inputFormat.parse(dateString)

            val outputFormat = SimpleDateFormat(ISO_DATE_TIME_FORMAT, Locale.getDefault())
            outputFormat.timeZone = TimeZone.getTimeZone(UTC)
            outputFormat.format(date)
        } catch (e: ParseException) {
            Log.e(
                TAG,
                "Exception while parsing date string to UTC format: $dateString",
                e
            )
            EMPTY_STRING
        }
    }

    fun getTimeFormat(format: String) = SimpleDateFormat(format, Locale.getDefault())

    fun getExpireAtDateTimeForTTLInUTC(): Date = Calendar.getInstance(TimeZone.getTimeZone(UTC)).apply { add(Calendar.DAY_OF_MONTH, 30) }.time

    fun String.getTimeDifferenceFromNow(caller: String): Long {
        val etaInUTC = this.toUTC(caller)
        val currentTimeInUTC = getDeviceLocalTimeInUTC()
        return etaInUTC - currentTimeInUTC
    }

}