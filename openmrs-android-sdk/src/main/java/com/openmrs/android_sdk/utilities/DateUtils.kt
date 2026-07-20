/*
 * The contents of this file are subject to the OpenMRS Public License
 * Version 1.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://license.openmrs.org
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations
 * under the License.
 *
 * Copyright (C) OpenMRS, LLC.  All Rights Reserved.
 */
package com.openmrs.android_sdk.utilities

import com.openmrs.android_sdk.library.OpenmrsAndroid
import com.openmrs.android_sdk.utilities.StringUtils.notNull
import org.joda.time.DateTime
import org.joda.time.LocalDate
import org.joda.time.format.DateTimeFormat
import org.joda.time.format.DateTimeFormatter
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone


object DateUtils {
    const val DEFAULT_DATE_FORMAT = "dd/MM/yyyy"
    const val DATE_WITH_TIME_FORMAT = "dd/MM/yyyy HH:mm"
    private const val OPEN_MRS_RESPONSE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSZ"
    private const val OPEN_MRS_RESPONSE_FORMAT_ISO = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX"
    const val OPEN_MRS_REQUEST_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSZ"
    const val OPEN_MRS_REQUEST_PATIENT_FORMAT = "yyyy-MM-dd"
    const val ZERO = 0L
    private val openMRSLogger = OpenmrsAndroid.getOpenMRSLogger();

    @JvmStatic
    fun convertTime(time: Long, dateFormat: String?, timeZone: TimeZone): String {
        val date = Date(time)
        val format = SimpleDateFormat(dateFormat)
        format.timeZone = timeZone
        return format.format(date)
    }

    @JvmStatic
    fun convertTime(time: Long, dateFormat: String?): String {
        return convertTime(time, dateFormat, TimeZone.getDefault())
    }

    @JvmStatic
    fun convertTime(timestamp: Long, timeZone: TimeZone): String {
        return convertTime(timestamp, DEFAULT_DATE_FORMAT, timeZone)
    }

    @JvmStatic
    fun convertTime(timestamp: Long): String {
        return convertTime(timestamp, DEFAULT_DATE_FORMAT, TimeZone.getDefault())
    }

    @JvmStatic
    fun convertTime(dateAsString: String?): Long? {
        if (dateAsString == null) return null
        
        val formats = arrayOf(
            DEFAULT_DATE_FORMAT,
            DATE_WITH_TIME_FORMAT,
            OPEN_MRS_RESPONSE_FORMAT,
            OPEN_MRS_RESPONSE_FORMAT_ISO,
            "yyyy-MM-dd'T'HH:mm:ss.SSS",
            "yyyy-MM-dd'T'HH:mm:ss",
            OPEN_MRS_REQUEST_PATIENT_FORMAT,
            "yyyy-MM-dd'T'HH:mm:ss.SSSZ",
            "yyyy-MM-dd'T'HH:mm:ss.SSS+0000"
        )

        for (format in formats) {
            try {
                val sdf = SimpleDateFormat(format)
                val date = sdf.parse(dateAsString)
                if (date != null) return date.time
            } catch (e: Exception) {
            }
        }
        
        openMRSLogger.w("Failed to parse date :$dateAsString")
        return null
    }

    @JvmStatic
    fun convertTime(dateAsString: String?, dateFormat: String?): Long? {
        if (dateAsString == null) return null
        try {
            val format: DateFormat = SimpleDateFormat(dateFormat)
            val formattedDate = format.parse(dateAsString)
            return formattedDate?.time
        } catch (e: Exception) {
            return convertTime(dateAsString)
        }
    }

    @JvmStatic
    fun convertTimeString(dateAsString: String?): DateTime? {
        var date: DateTime? = null
        if (notNull(dateAsString)) {
            val originalFormat: DateTimeFormatter
            originalFormat = if (dateAsString!!.length == OPEN_MRS_REQUEST_PATIENT_FORMAT.length) {
                DateTimeFormat.forPattern(OPEN_MRS_REQUEST_PATIENT_FORMAT)
            } else {
                DateTimeFormat.forPattern(OPEN_MRS_REQUEST_FORMAT)
            }
            date = originalFormat.parseDateTime(dateAsString)
        }
        return date
    }

    @JvmStatic
    fun convertTime1(dateAsString: String, dateFormat: String?): String {
        val time = convertTime(dateAsString)
        return if (time != null) {
            convertTime(time, dateFormat, TimeZone.getDefault())
        } else dateAsString
    }

    @JvmStatic
    fun getDateFromString(dateAsString: String): Date? {
        return getDateFromString(dateAsString, DEFAULT_DATE_FORMAT)
    }

    @JvmStatic
    fun getDateFromString(dateAsString: String?, dateFormat: String?): Date? {
        val time = convertTime(dateAsString, dateFormat)
        return if (time != null) Date(time) else null
    }

    @JvmStatic
    fun getCurrentDateTime(): String {
        val dateFormat: DateFormat = SimpleDateFormat(OPEN_MRS_RESPONSE_FORMAT)
        val date = Date()
        return dateFormat.format(date)
    }

    fun getDateTimeFromDifference(yearDiff: Int, monthDiff: Int): DateTime {
        return LocalDate().toDateTimeAtStartOfDay().minusYears(yearDiff).minusMonths(monthDiff)
    }

    @JvmStatic
    fun validateDate(dateString: String, minDate: DateTime, maxDate: DateTime): Boolean {
        if (minDate.isAfter(maxDate)) {
            return false
        }
        val s = dateString.trim { it <= ' ' }
        if (s.isEmpty() || s.length < 8 || s.length > 10) {
            return false
        }
        if (!s.contains("/")) {
            return false
        }
        var numberOfDashes = 0
        for (i in 0 until s.length) {
            if (s[i] == '/') {
                numberOfDashes++
            }
        }
        return if (numberOfDashes != 2) {
            false
        } else {
            try {
                val bundledDate = s.split("/").toTypedArray()
                val day = bundledDate[0].toInt()
                val month = bundledDate[1].toInt()
                val year = bundledDate[2].toInt()
                val maxDays: Int = if (month == 2) {
                    if (year % 4 == 0) 29 else 28
                } else if (day == 31 && (month == 4 || month == 6 || month == 9 || month == 11)) {
                    30
                } else {
                    31
                }
                if (day <= 0 || day > maxDays || month <= 0 || month > 12 || year <= minDate.year || year > maxDate.year) {
                    false
                } else {
                    val formatter = DateTimeFormat.forPattern(DEFAULT_DATE_FORMAT)
                    val dob = formatter.parseDateTime(s)
                    dob.isAfter(minDate) && dob.isBefore(maxDate)
                }
            } catch (e: Exception) {
                false
            }
        }
    }

    @JvmStatic
    fun isValidFormat(format: String?, dateAsString: String?): Boolean {
        if (dateAsString == null || format == null) return false
        try {
            val simpleDateFormat = SimpleDateFormat(format)
            val date = simpleDateFormat.parse(dateAsString)
            return date != null
        } catch (exception: Exception) {
            return false
        }
    }
}
