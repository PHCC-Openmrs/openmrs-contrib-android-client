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
package com.openmrs.android_sdk.library.models

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import java.io.Serializable

/**
 * Response shape of the `appointments` (Bahmni-origin) module's `/appointment` and
 * `/appointment/search` endpoints - the same backend O3's esm-appointments-app talks to.
 * This is a different module/data model than the legacy `appointmentscheduling` module
 * (no time-slot/appointment-block indirection).
 */
class Appointment : Serializable {

    @SerializedName("uuid")
    @Expose
    var uuid: String? = null

    @SerializedName("appointmentNumber")
    @Expose
    var appointmentNumber: String? = null

    @SerializedName("service")
    @Expose
    var service: AppointmentServiceInfo? = null

    @SerializedName("location")
    @Expose
    var location: AppointmentLocationInfo? = null

    @SerializedName("startDateTime")
    @Expose
    var startDateTime: Long? = null

    @SerializedName("endDateTime")
    @Expose
    var endDateTime: Long? = null

    @SerializedName("appointmentKind")
    @Expose
    var appointmentKind: String? = null

    @SerializedName("status")
    @Expose
    var status: String? = null

    @SerializedName("comments")
    @Expose
    var comments: String? = null

    @SerializedName("patient")
    @Expose
    var patient: AppointmentPatientInfo? = null

    @SerializedName("providers")
    @Expose
    var providers: List<AppointmentProviderInfo>? = null

    @SerializedName("dateAppointmentScheduled")
    @Expose
    var dateAppointmentScheduled: Long? = null

    object Status {
        const val REQUESTED = "Requested"
        const val WAITLIST = "WaitList"
        const val SCHEDULED = "Scheduled"
        const val ARRIVED = "Arrived"
        const val CHECKED_IN = "CheckedIn"
        const val COMPLETED = "Completed"
        const val CANCELLED = "Cancelled"
        const val MISSED = "Missed"
    }

    object Kind {
        const val SCHEDULED = "Scheduled"
        const val WALK_IN = "WalkIn"
        const val VIRTUAL = "Virtual"
    }
}

class AppointmentServiceInfo : Serializable {
    @SerializedName("uuid")
    @Expose
    var uuid: String? = null

    @SerializedName("name")
    @Expose
    var name: String? = null

    /** Only present when fetched via `GET appointmentService/all/default`, null when embedded in an Appointment. */
    @SerializedName("durationMins")
    @Expose
    var durationMins: Int? = null
}

class AppointmentLocationInfo : Serializable {
    @SerializedName("uuid")
    @Expose
    var uuid: String? = null

    @SerializedName("name")
    @Expose
    var name: String? = null
}

class AppointmentPatientInfo : Serializable {
    @SerializedName("uuid")
    @Expose
    var uuid: String? = null
}

class AppointmentProviderInfo : Serializable {
    @SerializedName("uuid")
    @Expose
    var uuid: String? = null

    @SerializedName("name")
    @Expose
    var name: String? = null
}

/**
 * Request body for `POST appointment/search`.
 *
 * NOTE: O3's own frontend sends a singular `patientUuid` here, but the backend's
 * AppointmentSearchRequestModel only has a `patientUuids` list field - Jackson silently drops
 * the unrecognized singular field, so the search runs unfiltered and returns every patient's
 * appointments (verified: a real server returned appointments for ~40 different patients from a
 * single-patientUuid request). We send the field name the backend actually binds to, and the
 * repository additionally filters the response by patient uuid as a safety net regardless.
 */
class AppointmentSearchRequest(
    @SerializedName("patientUuids")
    @Expose
    val patientUuids: List<String>,

    @SerializedName("startDate")
    @Expose
    val startDate: String
) : Serializable

/** Request body for `POST appointments/{uuid}/status-change` - matches changeAppointmentStatus() in O3. */
class AppointmentStatusChangeRequest(
    @SerializedName("toStatus")
    @Expose
    val toStatus: String,

    @SerializedName("onDate")
    @Expose
    val onDate: String,

    @SerializedName("timeZone")
    @Expose
    val timeZone: String
) : Serializable

class AppointmentProviderRef(
    @SerializedName("uuid")
    @Expose
    val uuid: String
) : Serializable

/**
 * Request body for `POST appointment` (create/edit) and the `appointmentRequest` field of
 * `POST recurring-appointments` - matches AppointmentPayload/constructAppointmentPayload() in O3.
 */
class AppointmentCreateRequest(
    @SerializedName("appointmentKind")
    @Expose
    val appointmentKind: String,

    @SerializedName("status")
    @Expose
    val status: String,

    @SerializedName("serviceUuid")
    @Expose
    val serviceUuid: String,

    @SerializedName("startDateTime")
    @Expose
    val startDateTime: String,

    @SerializedName("endDateTime")
    @Expose
    val endDateTime: String,

    @SerializedName("locationUuid")
    @Expose
    val locationUuid: String,

    @SerializedName("providers")
    @Expose
    val providers: List<AppointmentProviderRef>,

    @SerializedName("patientUuid")
    @Expose
    val patientUuid: String,

    @SerializedName("comments")
    @Expose
    val comments: String,

    @SerializedName("dateAppointmentScheduled")
    @Expose
    val dateAppointmentScheduled: String,

    /** Only set when editing an existing appointment - matches `uuid: isEditing ? appointment.uuid : undefined` in O3. */
    @SerializedName("uuid")
    @Expose
    val uuid: String? = null
) : Serializable

/** Request body for `POST appointments/conflicts` - matches checkAppointmentConflict() in O3. */
class AppointmentConflictRequest(
    @SerializedName("patientUuid")
    @Expose
    val patientUuid: String,

    @SerializedName("serviceUuid")
    @Expose
    val serviceUuid: String,

    @SerializedName("startDateTime")
    @Expose
    val startDateTime: String,

    @SerializedName("endDateTime")
    @Expose
    val endDateTime: String,

    @SerializedName("locationUuid")
    @Expose
    val locationUuid: String,

    @SerializedName("appointmentKind")
    @Expose
    val appointmentKind: String,

    /** O3 always sends this empty regardless of the selected provider - matches its source exactly. */
    @SerializedName("providers")
    @Expose
    val providers: List<AppointmentProviderRef> = emptyList(),

    /** Only set when editing, so the appointment being edited excludes itself from its own conflict check. */
    @SerializedName("uuid")
    @Expose
    val uuid: String? = null
) : Serializable

/** The `recurringPattern` field of `POST recurring-appointments` - matches RecurringPattern in O3. */
class RecurringPattern(
    @SerializedName("type")
    @Expose
    val type: String,

    @SerializedName("period")
    @Expose
    val period: Int,

    @SerializedName("endDate")
    @Expose
    val endDate: String,

    @SerializedName("daysOfWeek")
    @Expose
    val daysOfWeek: List<String> = emptyList()
) : Serializable {
    object Type {
        const val DAY = "DAY"
        const val WEEK = "WEEK"
    }
}

/** Request body for `POST recurring-appointments` - matches RecurringAppointmentsPayload in O3. */
class RecurringAppointmentPayload(
    @SerializedName("appointmentRequest")
    @Expose
    val appointmentRequest: AppointmentCreateRequest,

    @SerializedName("recurringPattern")
    @Expose
    val recurringPattern: RecurringPattern,

    @SerializedName("timeZone")
    @Expose
    val timeZone: String
) : Serializable
