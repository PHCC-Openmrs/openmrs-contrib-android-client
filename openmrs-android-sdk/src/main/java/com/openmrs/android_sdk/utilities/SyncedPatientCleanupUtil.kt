/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package com.openmrs.android_sdk.utilities

import com.openmrs.android_sdk.library.OpenmrsAndroid
import com.openmrs.android_sdk.library.dao.PatientDAO
import com.openmrs.android_sdk.library.dao.VisitDAO
import com.openmrs.android_sdk.library.databases.AppDatabase

/**
 * Removes a patient's entire local footprint (patient record, visits, encounters/forms,
 * allergies) once everything about them has been confirmed synced to the server. Opt-in via the
 * "Auto-delete synced patients" Settings toggle ([isEnabled]/[setEnabled], off by default - this
 * is an irreversible local data wipe, so it must never run unless the user has explicitly turned
 * it on).
 *
 * [checkAndCleanupIfFullySynced] is called right at the point each of
 * `PatientRepository#syncPatient`/`#updatePatient`, `VisitRepository#syncStartedVisit`, and
 * `EncounterRepository#saveEncounter` actually confirms a successful push - deliberately at this
 * shared, low level (not just from the background IntentServices that process the offline
 * backlog) so it fires uniformly whether that push happened directly while online, or was queued
 * offline and caught up later; either path runs through these exact methods. A patient is left
 * completely alone if ANY of their visits or forms still haven't reached the server yet, so a
 * partially-synced patient is never partially deleted.
 */
object SyncedPatientCleanupUtil {

    @JvmStatic
    fun isEnabled(): Boolean {
        return OpenmrsAndroid.getOpenMRSSharedPreferences()
                .getBoolean(ApplicationConstants.AutoDeleteSyncedPatients.KEY_AUTO_DELETE_SYNCED_PATIENTS, false)
    }

    @JvmStatic
    fun setEnabled(enabled: Boolean) {
        OpenmrsAndroid.getOpenMRSSharedPreferences().edit()
                .putBoolean(ApplicationConstants.AutoDeleteSyncedPatients.KEY_AUTO_DELETE_SYNCED_PATIENTS, enabled)
                .apply()
    }

    /**
     * Sweeps every locally-known patient and cleans up any that already qualify as fully synced
     * right now - needed because [checkAndCleanupIfFullySynced] only ever fires as a side effect
     * of a push actually happening for a given patient, so a patient that was already fully
     * synced before this setting was ever turned on (e.g. downloaded, or synced in an earlier
     * session) would otherwise sit there forever with nothing to trigger a check for it. Call
     * this when the setting is first enabled, and from a general "sync now" action, so those
     * patients get swept too rather than only ones touched again after the fact.
     */
    @JvmStatic
    fun sweepAllFullySyncedPatients() {
        if (!isEnabled()) return
        try {
            val patients = PatientDAO().allPatients.execute()
            patients.forEach { checkAndCleanupIfFullySynced(it.id) }
        } catch (e: Exception) {
            OpenmrsAndroid.getOpenMRSLogger().e("Failed to sweep fully synced patients: ${e.message}")
        }
    }

    @JvmStatic
    fun checkAndCleanupIfFullySynced(patientId: Long?) {
        if (patientId == null || !isEnabled()) return
        val logger = OpenmrsAndroid.getOpenMRSLogger()
        try {
            val db = AppDatabase.getDatabase(OpenmrsAndroid.getInstance()!!.applicationContext)
            val patientDAO = PatientDAO()
            val visitDAO = VisitDAO()

            val patient = patientDAO.findPatientByID(patientId) ?: return
            if (!patient.isSynced) return

            val visits = visitDAO.getVisitsByPatientID(patientId).execute()
            if (visits.any { it.uuid.isNullOrEmpty() }) return

            val encounters = db.encounterCreateRoomDAO().getCreatedEncountersByPatientId(patientId)
            if (encounters.any { !it.synced }) return

            val uuid = patient.uuid
            logger.i("Patient $patientId is fully synced - removing local data (auto-delete enabled)")

            patientDAO.deletePatient(patientId)
            visitDAO.deleteVisitsByPatientId(patientId).execute()
            db.encounterCreateRoomDAO().deleteEncountersByPatientId(patientId)
            if (!uuid.isNullOrEmpty()) {
                db.allergyRoomDAO().deleteAllPatientAllergy(uuid)
            }
        } catch (e: Exception) {
            // Best-effort cleanup - a failure here must never disrupt the actual sync it's
            // piggybacking on, so it's swallowed rather than propagated.
            logger.e("Failed to check/cleanup synced patient $patientId: ${e.message}")
        }
    }
}
