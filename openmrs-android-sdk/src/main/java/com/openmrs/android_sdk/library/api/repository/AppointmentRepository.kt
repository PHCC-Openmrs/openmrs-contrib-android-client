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
package com.openmrs.android_sdk.library.api.repository

import com.openmrs.android_sdk.library.OpenMRSLogger
import com.openmrs.android_sdk.library.api.RestApi
import com.openmrs.android_sdk.library.dao.AppointmentRoomDAO
import com.openmrs.android_sdk.library.databases.AppDatabaseHelper
import com.openmrs.android_sdk.library.databases.AppDatabaseHelper.createObservableIO
import com.openmrs.android_sdk.library.models.Appointment
import com.openmrs.android_sdk.library.models.AppointmentConflictRequest
import com.openmrs.android_sdk.library.models.AppointmentCreateRequest
import com.openmrs.android_sdk.library.models.AppointmentSearchRequest
import com.openmrs.android_sdk.library.models.AppointmentServiceInfo
import com.openmrs.android_sdk.library.models.AppointmentStatusChangeRequest
import com.openmrs.android_sdk.library.models.RecurringAppointmentPayload
import com.openmrs.android_sdk.utilities.DateUtils
import retrofit2.Call
import rx.Observable
import java.io.IOException
import java.util.TimeZone
import java.util.concurrent.Callable
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Talks to the `appointments` (Bahmni-origin) module - the same backend O3's esm-appointments-app
 * uses - via the /appointment/search and /appointments/{uuid}/status-change endpoints (verified
 * against a live O3 deployment). This is a different module from the legacy `appointmentscheduling`
 * one this repository used to target.
 */
@Singleton
class AppointmentRepository @Inject constructor(
    val logger: OpenMRSLogger,
    var restApi: RestApi,
    val appointmentRoomDAO: AppointmentRoomDAO
) {

    /**
     * Executes a retrofit request
     *
     * @param call the interface call
     * @param message the error message to display
     *
     * @return T
     */
    fun <T> executeRequest(call: Call<T>, message: String): T {
        val response = call.execute()

        if (response.isSuccessful && response.body() != null) {
            return response.body()!!
        } else {
            logger.e(message + response.message())
            throw IOException(message + response.message())
        }
    }

    /**
     * Searches a patient's appointments on the server and caches the result locally, mirroring
     * O3's usePatientAppointments hook (6-month lookback, no end date).
     *
     * The response is filtered down to this patient client-side as a safety net: the search
     * endpoint has been observed returning every patient's appointments regardless of the
     * patientUuids filter (see AppointmentSearchRequest's kdoc), so trusting the server's
     * filtering alone isn't safe.
     */
    fun searchAppointmentsAndSave(
        patientUuid: String,
        startDate: String = defaultSearchStartDate()
    ): Observable<List<Appointment>> {
        return createObservableIO(Callable {
            val call = restApi.searchAppointments(AppointmentSearchRequest(listOf(patientUuid), startDate))
            val appointments = executeRequest(call, "Error searching appointments: ")
                .filter { it.patient?.uuid == patientUuid }
            val entities = appointments.map { AppDatabaseHelper.convert(it, patientUuid) }
            appointmentRoomDAO.addOrUpdateAll(entities)
            appointments
        })
    }

    /**
     * Reads a patient's appointments from the local cache only, for offline fallback.
     */
    fun getCachedAppointments(patientUuid: String): Observable<List<Appointment>> {
        return createObservableIO(Callable {
            appointmentRoomDAO.getAppointmentsForPatient(patientUuid).blockingGet()
                .map { AppDatabaseHelper.convert(it) }
        })
    }

    /**
     * Cancels an appointment on the server and reflects the new status in the local cache.
     */
    fun cancelAppointment(appointmentUuid: String): Observable<Unit> {
        return createObservableIO(Callable {
            val statusChangeRequest = AppointmentStatusChangeRequest(
                toStatus = Appointment.Status.CANCELLED,
                onDate = DateUtils.getCurrentDateTime(),
                timeZone = TimeZone.getDefault().id
            )
            val call = restApi.changeAppointmentStatus(appointmentUuid, statusChangeRequest)
            executeRequest(call, "Error cancelling appointment: ")
            appointmentRoomDAO.updateStatus(appointmentUuid, Appointment.Status.CANCELLED)
        })
    }

    /**
     * Fetches the appointment services configured on the server, for the service picker.
     */
    fun getAppointmentServices(): Observable<List<AppointmentServiceInfo>> {
        return createObservableIO(Callable {
            executeRequest(restApi.getAppointmentServices(), "Error fetching appointment services: ")
        })
    }

    /**
     * Checks whether an appointment would conflict with an existing one (double-booking or
     * outside service hours), matching O3's checkAppointmentConflict(). Returns true if a
     * conflict was found.
     */
    fun hasConflict(request: AppointmentConflictRequest): Observable<Boolean> {
        return createObservableIO(Callable {
            val response = restApi.checkAppointmentConflicts(request).execute()
            if (!response.isSuccessful) {
                throw IOException("Error checking appointment conflicts: " + response.message())
            }
            response.code() == 200
        })
    }

    /**
     * Creates a new appointment on the server, matching O3's saveAppointment().
     */
    fun createAppointment(request: AppointmentCreateRequest): Observable<Appointment> {
        return createObservableIO(Callable {
            executeRequest(restApi.createAppointment(request), "Error creating appointment: ")
        })
    }

    /**
     * Creates a recurring series of appointments on the server, matching O3's
     * saveRecurringAppointments().
     */
    fun createRecurringAppointments(request: RecurringAppointmentPayload): Observable<Unit> {
        return createObservableIO(Callable {
            val response = restApi.createRecurringAppointments(request).execute()
            if (!response.isSuccessful) {
                throw IOException("Error creating recurring appointments: " + response.message())
            }
        })
    }

    companion object {
        /** Matches O3's `dayjs().subtract(6, 'month').toISOString()` default search window. */
        fun defaultSearchStartDate(): String {
            val sixMonthsAgo = DateUtils.getDateTimeFromDifference(0, 6).millis
            return DateUtils.convertTime(sixMonthsAgo, DateUtils.OPEN_MRS_REQUEST_FORMAT)
        }

        /**
         * Formats an epoch-millis timestamp the same way O3's `dayjs(...).format()` does when
         * building an appointment create/conflict-check payload (e.g. "2026-07-30T15:22:00+05:30")
         * - a colon-separated offset, no milliseconds. Distinct from [defaultSearchStartDate]'s
         * format, which is what the search/status-change endpoints expect instead.
         */
        fun formatAppointmentDateTime(epochMillis: Long): String {
            val format = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX")
            return format.format(java.util.Date(epochMillis))
        }
    }
}
