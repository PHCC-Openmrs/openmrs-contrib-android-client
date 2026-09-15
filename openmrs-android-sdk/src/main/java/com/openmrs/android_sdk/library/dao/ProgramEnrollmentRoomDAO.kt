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
package com.openmrs.android_sdk.library.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.openmrs.android_sdk.library.databases.entities.ProgramEnrollmentCreateEntity

/**
 * The interface Program enrollment room dao.
 */
@Dao
interface ProgramEnrollmentRoomDAO {

    /**
     * Add a locally-created program enrolment episode.
     *
     * @param enrollment the enrolment
     * @return the local id of the newly saved row
     */
    @Insert
    fun addProgramEnrollment(enrollment: ProgramEnrollmentCreateEntity): Long

    /**
     * Update a program enrolment episode.
     *
     * @param enrollment the enrolment
     * @return the number of rows updated
     */
    @Update
    fun updateProgramEnrollment(enrollment: ProgramEnrollmentCreateEntity): Int

    /**
     * Gets the episodes that still owe the server something: never pushed at all, or pushed but
     * completed locally since.
     *
     * @return the enrolments awaiting a push
     */
    @Query(
        "SELECT * FROM programenrollmentcreate " +
                "WHERE uuid IS NULL OR uuid = '' " +
                "OR (date_completed IS NOT NULL AND date_completed != '' AND completion_synced = 0)"
    )
    fun getUnsyncedProgramEnrollments(): List<ProgramEnrollmentCreateEntity>

    /**
     * Gets the episodes a given visit opened.
     *
     * @param visitId the local visit id
     * @return the enrolments opened by that visit
     */
    @Query("SELECT * FROM programenrollmentcreate WHERE visit_id = :visitId")
    fun getProgramEnrollmentsByVisitId(visitId: Long): List<ProgramEnrollmentCreateEntity>

    /**
     * Deletes every episode of a patient, for when that patient is removed from the device.
     *
     * @param patientId the local patient id
     * @return the number of rows deleted
     */
    @Query("DELETE FROM programenrollmentcreate WHERE patient_id = :patientId")
    fun deleteProgramEnrollmentsByPatientId(patientId: Long): Int
}
