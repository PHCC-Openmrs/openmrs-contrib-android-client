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

import com.openmrs.android_sdk.library.dao.PatientDAO
import com.openmrs.android_sdk.library.dao.ProgramEnrollmentDAO
import com.openmrs.android_sdk.library.databases.entities.ProgramEnrollmentCreateEntity
import com.openmrs.android_sdk.library.models.Patient
import com.openmrs.android_sdk.library.models.ProgramEnrollment
import com.openmrs.android_sdk.library.models.ProgramEnrollmentCreate
import com.openmrs.android_sdk.library.models.ProgramEnrollmentUpdate
import com.openmrs.android_sdk.library.models.Visit
import com.openmrs.android_sdk.utilities.DateUtils
import com.openmrs.android_sdk.utilities.NetworkUtils
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps a patient's program-enrolment episodes in step with their visits, the way the web client
 * does: starting a visit for one or more services opens an episode per service, and ending that
 * visit completes them.
 *
 * A new episode is always opened, even when the patient already has one active in the same
 * program, because each visit is its own episode for reporting.
 *
 * Every episode is written locally first and pushed afterwards, so the whole flow works offline;
 * [syncPendingEnrollments] drains what is still owed once connectivity is back. A failure to
 * enrol never rolls the visit back - the visit already exists and must stand.
 */
@Singleton
class ProgramEnrollmentRepository @Inject constructor(
    private val programEnrollmentDAO: ProgramEnrollmentDAO,
    private val patientDAO: PatientDAO
) : BaseRepository() {

    /**
     * Opens an episode per selected service for a visit that has just started.
     *
     * @param patient the patient the visit is for
     * @param visitId the local id of the visit
     * @param programUuids the selected services, as program uuids
     * @param dateEnrolled the visit's start date/time
     * @param locationUuid the visit's location
     */
    fun enrollForVisit(
        patient: Patient,
        visitId: Long?,
        programUuids: List<String>,
        dateEnrolled: String,
        locationUuid: String?
    ) {
        programUuids.filter { it.isNotEmpty() }.forEach { programUuid ->
            val enrollment = ProgramEnrollmentCreateEntity().apply {
                this.patientId = patient.id ?: 0
                this.visitId = visitId
                this.programUuid = programUuid
                this.dateEnrolled = dateEnrolled
                this.locationUuid = locationUuid
            }
            try {
                enrollment.id = programEnrollmentDAO.saveEnrollment(enrollment)
                pushEnrollment(enrollment, patient)
            } catch (e: Exception) {
                logger.e("Could not open a service enrollment for program $programUuid", e)
            }
        }
    }

    /**
     * Completes the episodes a visit opened, as of when the visit ended.
     *
     * Episodes this device opened are completed by uuid. A visit that has none - started on the
     * web, or on another device - falls back to what the web client's `visit-ended` listener does:
     * read the services off the visit and complete the patient's most recent active episode in
     * each. A visit with no Service attribute at all is left untouched, so visits predating this
     * feature are unaffected.
     *
     * @param visit the visit that has just ended
     * @param stopDatetime when it ended
     */
    fun completeEnrollmentsForVisit(visit: Visit, stopDatetime: String) {
        val visitId = visit.id
        val localEnrollments = if (visitId != null) programEnrollmentDAO.getEnrollmentsByVisitId(visitId) else emptyList()

        if (localEnrollments.isNotEmpty()) {
            val patient = visit.patientOrNull()
            localEnrollments.filter { it.dateCompleted.isNullOrEmpty() }.forEach { enrollment ->
                enrollment.dateCompleted = stopDatetime
                enrollment.completionSynced = false
                try {
                    programEnrollmentDAO.updateEnrollment(enrollment)
                    pushEnrollment(enrollment, patient)
                } catch (e: Exception) {
                    logger.e("Could not complete the service enrollment ${enrollment.id}", e)
                }
            }
            return
        }

        completeEnrollmentsFromServer(visit, stopDatetime)
    }

    /**
     * Pushes every episode that still owes the server a create or a completion. Safe to call
     * repeatedly - an episode whose patient is not synced yet stays queued for the next attempt.
     */
    fun syncPendingEnrollments() {
        if (!NetworkUtils.isOnline()) return
        programEnrollmentDAO.getUnsyncedEnrollments().forEach { enrollment ->
            try {
                pushEnrollment(enrollment, patientDAO.findPatientByID(enrollment.patientId))
            } catch (e: Exception) {
                logger.e("Could not sync the service enrollment ${enrollment.id}", e)
            }
        }
    }

    /**
     * Creates the episode on the server, or - if it is already there and has since been completed
     * locally - pushes that completion. Returns quietly when offline or when the patient has no
     * server uuid to attach the episode to, leaving the row queued for the next attempt.
     */
    private fun pushEnrollment(enrollment: ProgramEnrollmentCreateEntity, patient: Patient?) {
        if (!NetworkUtils.isOnline()) return
        val patientUuid = patient?.uuid
        if (patient == null || !patient.isSynced || patientUuid.isNullOrEmpty()) return

        try {
            if (enrollment.uuid.isNullOrEmpty()) {
                val dates = normalizeEnrollmentDates(enrollment.dateEnrolled, enrollment.dateCompleted)
                val response = restApi.createProgramEnrollment(
                    ProgramEnrollmentCreate(
                        patient = patientUuid,
                        program = enrollment.programUuid,
                        dateEnrolled = dates.first,
                        dateCompleted = dates.second,
                        location = enrollment.locationUuid
                    )
                ).execute()
                if (response.isSuccessful && response.body() != null) {
                    enrollment.uuid = response.body()!!.uuid
                    // A create carries the completion date with it, so nothing is left to push.
                    enrollment.completionSynced = !enrollment.dateCompleted.isNullOrEmpty()
                    programEnrollmentDAO.updateEnrollment(enrollment)
                } else {
                    logger.e("Error opening a service enrollment: " + response.message())
                }
            } else if (!enrollment.dateCompleted.isNullOrEmpty() && !enrollment.completionSynced) {
                val dates = normalizeEnrollmentDates(enrollment.dateEnrolled, enrollment.dateCompleted)
                val response = restApi.updateProgramEnrollment(
                    enrollment.uuid!!,
                    ProgramEnrollmentUpdate(
                        dateEnrolled = dates.first,
                        dateCompleted = dates.second,
                        location = enrollment.locationUuid
                    )
                ).execute()
                if (response.isSuccessful) {
                    enrollment.completionSynced = true
                    programEnrollmentDAO.updateEnrollment(enrollment)
                } else {
                    logger.e("Error completing a service enrollment: " + response.message())
                }
            }
        } catch (e: Exception) {
            logger.e("Service enrollment will be retried when back online: " + e.message)
        }
    }

    /**
     * The fallback for a visit this device did not start: complete, for each of the visit's
     * services, the patient's most recently opened active episode in that program - which can only
     * be the one that visit opened.
     */
    private fun completeEnrollmentsFromServer(visit: Visit, stopDatetime: String) {
        val programUuids = visit.serviceProgramUuids()
        val patientUuid = visit.patientOrNull()?.uuid
        if (programUuids.isEmpty() || patientUuid.isNullOrEmpty() || !NetworkUtils.isOnline()) return

        try {
            val response = restApi.getProgramEnrollments(patientUuid, ENROLLMENT_REPRESENTATION).execute()
            if (!response.isSuccessful || response.body() == null) {
                logger.e("Error fetching the patient's service enrollments: " + response.message())
                return
            }
            val enrollments = response.body()!!.results

            programUuids.forEach { programUuid ->
                val active = enrollments
                    .filter { it.isActive() && it.program?.uuid == programUuid }
                    .sortedByDescending { DateUtils.convertTime(it.dateEnrolled) ?: 0L }
                val toComplete = active.firstOrNull() ?: return@forEach
                // Both are required by the update: the server validates the pair together, so an
                // episode the server returned without an enrolment date cannot be completed here.
                val enrolledOn = toComplete.dateEnrolled ?: return@forEach
                val enrollmentUuid = toComplete.uuid ?: return@forEach
                val dates = normalizeEnrollmentDates(enrolledOn, stopDatetime)
                val updateResponse = restApi.updateProgramEnrollment(
                    enrollmentUuid,
                    ProgramEnrollmentUpdate(
                        dateEnrolled = dates.first,
                        dateCompleted = dates.second,
                        location = toComplete.location?.uuid
                    )
                ).execute()
                if (!updateResponse.isSuccessful) {
                    logger.e("Error completing a service enrollment: " + updateResponse.message())
                }
            }
        } catch (e: Exception) {
            // The visit has already ended and must not be reopened over a failure to complete its
            // enrollments; log for diagnostics only.
            logger.e("Could not complete this visit's service enrollments: " + e.message)
        }
    }

    companion object {
        private const val ENROLLMENT_REPRESENTATION =
            "custom:(uuid,dateEnrolled,dateCompleted,location:(uuid),program:(uuid,name))"

        /**
         * The server rejects an episode whose completion date is earlier than its enrolment date. A
         * same-day pair can trip that purely from rounding (a visit short enough that its stop time
         * lands behind its start time); snap such a completion up to the enrolment instant instead
         * of failing the save. Different-day and later-same-day values pass through unchanged.
         *
         * Mirrors the web client's `normalizeProgramEnrollmentDates`.
         *
         * @return the enrolment and completion dates to send
         */
        @JvmStatic
        fun normalizeEnrollmentDates(dateEnrolled: String, dateCompleted: String?): Pair<String, String?> {
            if (dateCompleted.isNullOrEmpty()) return Pair(dateEnrolled, null)

            val enrolledMillis = DateUtils.convertTime(dateEnrolled)
            val completedMillis = DateUtils.convertTime(dateCompleted)
            if (enrolledMillis == null || completedMillis == null) return Pair(dateEnrolled, dateCompleted)

            val snap = completedMillis < enrolledMillis && isSameCalendarDay(enrolledMillis, completedMillis)
            return Pair(dateEnrolled, if (snap) dateEnrolled else dateCompleted)
        }

        private fun isSameCalendarDay(firstMillis: Long, secondMillis: Long): Boolean {
            val first = Calendar.getInstance().apply { timeInMillis = firstMillis }
            val second = Calendar.getInstance().apply { timeInMillis = secondMillis }
            return first[Calendar.YEAR] == second[Calendar.YEAR] &&
                    first[Calendar.DAY_OF_YEAR] == second[Calendar.DAY_OF_YEAR]
        }
    }
}
