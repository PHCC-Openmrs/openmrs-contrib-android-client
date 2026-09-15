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
package com.openmrs.android_sdk.library.databases.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A program-enrolment episode opened by starting a visit for a service, held locally so the whole
 * flow works offline - mirrors `encountercreate`, the app's other "created locally, pushed when
 * back online" queue.
 *
 * The row outlives its push: it stays after [uuid] is filled in, because ending the visit has to
 * find the very episode that visit opened in order to complete it, rather than guessing from the
 * patient's active enrolments.
 */
@Entity(tableName = "programenrollmentcreate")
class ProgramEnrollmentCreateEntity {
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "_id")
    var id: Long? = null

    /** Server uuid of the episode, null until it has been pushed. */
    @ColumnInfo(name = "uuid")
    var uuid: String? = null

    /** Local id of the patient, so the episode can be pushed once that patient itself is synced. */
    @ColumnInfo(name = "patient_id")
    var patientId: Long = 0

    /** Local id of the visit this episode belongs to; the visit need not be synced itself. */
    @ColumnInfo(name = "visit_id")
    var visitId: Long? = null

    @ColumnInfo(name = "program_uuid")
    var programUuid: String = ""

    @ColumnInfo(name = "date_enrolled")
    var dateEnrolled: String = ""

    /** Set when the visit ends; null while the visit is still open. */
    @ColumnInfo(name = "date_completed")
    var dateCompleted: String? = null

    @ColumnInfo(name = "location_uuid")
    var locationUuid: String? = null

    /**
     * Whether [dateCompleted] has reached the server. Tracked separately from [uuid] because an
     * episode created online and completed offline needs a second push that the presence of a
     * uuid alone would hide.
     */
    @ColumnInfo(name = "completion_synced")
    var completionSynced: Boolean = false
}
