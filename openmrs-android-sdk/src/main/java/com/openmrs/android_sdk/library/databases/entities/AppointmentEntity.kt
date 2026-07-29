/*
 * The contents of this file are subject to the OpenMRS Public License
 * Version 1.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://license.openmrs.org
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations
 * under the License.
 *
 * Copyright (C) OpenMRS, LLC.  All Rights Reserved.
 */
package com.openmrs.android_sdk.library.databases.entities

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.PrimaryKey

@Entity(tableName = "appointments")
class AppointmentEntity {

    @PrimaryKey
    @ColumnInfo(name = "uuid")
    var uuid: String = ""

    @ColumnInfo(name = "patient_uuid")
    var patientUuid: String? = null

    @ColumnInfo(name = "appointment_number")
    var appointmentNumber: String? = null

    @ColumnInfo(name = "service_name")
    var serviceName: String? = null

    @ColumnInfo(name = "location_name")
    var locationName: String? = null

    @ColumnInfo(name = "start_date_time")
    var startDateTime: Long? = null

    @ColumnInfo(name = "end_date_time")
    var endDateTime: Long? = null

    @ColumnInfo(name = "appointment_kind")
    var appointmentKind: String? = null

    @ColumnInfo(name = "status")
    var status: String? = null

    @ColumnInfo(name = "comments")
    var comments: String? = null
}
