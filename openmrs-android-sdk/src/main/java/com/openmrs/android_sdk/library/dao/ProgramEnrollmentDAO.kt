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

import com.openmrs.android_sdk.library.OpenmrsAndroid
import com.openmrs.android_sdk.library.databases.AppDatabase
import com.openmrs.android_sdk.library.databases.entities.ProgramEnrollmentCreateEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local store of the program-enrolment episodes the start-visit form opened, and of whether each
 * has reached the server yet. Mirrors [VisitDAO]'s role for locally-started visits.
 */
@Singleton
class ProgramEnrollmentDAO @Inject constructor() {

    /**
     * Resolved lazily rather than in the constructor: this DAO is injected into repositories that
     * are themselves constructed long before anything enrols anyone, and a test that mocks the
     * database need not stub a table it never touches.
     */
    private val programEnrollmentRoomDAO by lazy {
        AppDatabase.getDatabase(OpenmrsAndroid.getInstance()!!.applicationContext).programEnrollmentRoomDAO()
    }

    /**
     * Saves a newly-opened episode locally, before the server has confirmed it.
     *
     * @param enrollment the episode
     * @return the local id of the saved episode
     */
    fun saveEnrollment(enrollment: ProgramEnrollmentCreateEntity): Long =
        programEnrollmentRoomDAO.addProgramEnrollment(enrollment)

    /**
     * Saves changes to an episode - its server uuid once pushed, or its completion date once its
     * visit ends.
     *
     * @param enrollment the episode
     */
    fun updateEnrollment(enrollment: ProgramEnrollmentCreateEntity) {
        programEnrollmentRoomDAO.updateProgramEnrollment(enrollment)
    }

    /**
     * Gets the episodes that still owe the server a create or a completion. Never throws: a
     * failure here must not stop the rest of a sync pass.
     *
     * @return the episodes awaiting a push
     */
    fun getUnsyncedEnrollments(): List<ProgramEnrollmentCreateEntity> = try {
        programEnrollmentRoomDAO.getUnsyncedProgramEnrollments()
    } catch (e: Exception) {
        emptyList()
    }

    /**
     * Gets the episodes a given visit opened.
     *
     * @param visitId the local visit id
     * @return the episodes opened by that visit
     */
    fun getEnrollmentsByVisitId(visitId: Long): List<ProgramEnrollmentCreateEntity> = try {
        programEnrollmentRoomDAO.getProgramEnrollmentsByVisitId(visitId)
    } catch (e: Exception) {
        emptyList()
    }

    /**
     * Deletes every episode of a patient, for when that patient is removed from the device.
     *
     * @param patientId the local patient id
     */
    fun deleteEnrollmentsByPatientId(patientId: Long) {
        try {
            programEnrollmentRoomDAO.deleteProgramEnrollmentsByPatientId(patientId)
        } catch (e: Exception) {
            OpenmrsAndroid.getOpenMRSLogger()?.e("Could not delete the program enrollments of patient $patientId", e)
        }
    }
}
