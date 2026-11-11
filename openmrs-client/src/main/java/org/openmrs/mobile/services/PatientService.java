/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */

package org.openmrs.mobile.services;

import javax.inject.Inject;
import java.util.List;

import dagger.hilt.android.AndroidEntryPoint;
import android.app.IntentService;
import android.content.Intent;
import android.util.Log;

import com.openmrs.android_sdk.library.api.repository.PatientRepository;
import com.openmrs.android_sdk.library.dao.PatientDAO;
import com.openmrs.android_sdk.library.models.Patient;
import com.openmrs.android_sdk.utilities.NetworkUtils;

/**
 * Uploads patients registered offline. There is deliberately no "similar patient" check and no
 * manual merge step: {@link PatientRepository#syncPatient} creates the patient with the details
 * that were filled in and, when the server reports that the National ID already belongs to a
 * patient, links this one to that existing server patient instead - so the visit and forms then
 * sync to the existing patient. Matching is by identifier only, never by name/birth date.
 */
@AndroidEntryPoint
public class PatientService extends IntentService {
    public static final String PATIENT_SERVICE_TAG = "PATIENT_SERVICE";

    @Inject
    PatientRepository patientRepository;
    @Inject
    PatientDAO patientDAO;

    public PatientService() {
        super("Register Patients");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Log.i(PATIENT_SERVICE_TAG, "PatientService started");
        try {
            if (!NetworkUtils.isOnline()) {
                Log.w(PATIENT_SERVICE_TAG, "No internet connection, sync postponed");
                return;
            }

            List<Patient> patientList = patientDAO.getUnSyncedPatients();
            if (patientList.isEmpty()) {
                Log.i(PATIENT_SERVICE_TAG, "No unsynced patients found");
                return;
            }

            Log.i(PATIENT_SERVICE_TAG, "Found " + patientList.size() + " unsynced patients");

            for (Patient patient : patientList) {
                String patientName = (patient.getName() != null) ? patient.getName().getNameString() : "ID " + patient.getId();
                Log.i(PATIENT_SERVICE_TAG, "Processing patient: " + patientName);

                try {
                    patientRepository.syncPatient(patient).single().toBlocking().first();
                } catch (Exception e) {
                    Log.e(PATIENT_SERVICE_TAG, "Failed to sync patient " + patientName, e);
                }
            }
        } finally {
            // Chain straight into VisitService so a single reconnect/manual-sync trigger cascades
            // through the whole patient -> visit -> encounter dependency chain by itself, instead
            // of needing a separate trigger per stage (VisitService itself is a no-op if there's
            // nothing to sync, or if we're offline, or if a patient it depends on isn't synced yet).
            startService(new Intent(this, VisitService.class));
        }
    }
}
