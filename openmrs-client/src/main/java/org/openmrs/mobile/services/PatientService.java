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
import java.util.ArrayList;
import java.util.List;

import dagger.hilt.android.AndroidEntryPoint;
import android.app.IntentService;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.openmrs.android_sdk.library.api.repository.PatientRepository;
import com.openmrs.android_sdk.library.dao.PatientDAO;
import com.openmrs.android_sdk.library.models.Patient;
import com.openmrs.android_sdk.utilities.NetworkUtils;
import com.openmrs.android_sdk.utilities.ToastUtil;

import org.openmrs.mobile.R;

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

            List<String[]> failures = new ArrayList<>();
            for (Patient patient : patientList) {
                String patientName = (patient.getName() != null) ? patient.getName().getNameString() : "ID " + patient.getId();
                Log.i(PATIENT_SERVICE_TAG, "Processing patient: " + patientName);

                try {
                    patientRepository.syncPatient(patient).single().toBlocking().first();
                } catch (Exception e) {
                    Log.e(PATIENT_SERVICE_TAG, "Failed to sync patient " + patientName, e);
                    failures.add(new String[]{patientName, describeFailure(e)});
                }
            }

            // A patient that fails here stays on the device and is retried on the next sync, but
            // without this the user would never know - the failure only reached the log.
            if (!failures.isEmpty()) {
                final int failedCount = failures.size();
                final String firstName = failures.get(0)[0];
                final String firstReason = failures.get(0)[1];
                // Posted to the main thread: this service's worker thread ends as soon as it
                // returns, and a toast created on it never shows ("Handler ... on a dead thread").
                new Handler(Looper.getMainLooper()).post(() ->
                        ToastUtil.error(R.string.patient_sync_failed, failedCount, firstName, firstReason));
            }
        } finally {
            // Chain straight into VisitService so a single reconnect/manual-sync trigger cascades
            // through the whole patient -> visit -> encounter dependency chain by itself, instead
            // of needing a separate trigger per stage (VisitService itself is a no-op if there's
            // nothing to sync, or if we're offline, or if a patient it depends on isn't synced yet).
            startService(new Intent(this, VisitService.class));
        }
    }

    /**
     * A reason the user can act on. The repository's own messages (e.g. an ID already registered
     * to another patient) are readable as they are. A server validation error (HTTP 400 with
     * fieldErrors/globalErrors) is reduced to its field messages, e.g. "birthdate: Cannot be a
     * date in the future" - which will not fix itself on retry. Anything else is raw
     * network/server output, replaced by a generic "will retry" note (details stay in the log).
     */
    private String describeFailure(Exception e) {
        Throwable cause = e.getCause() != null ? e.getCause() : e;
        String message = cause.getMessage();
        if (message != null && (message.startsWith("This ID") || message.startsWith("Sync failed:"))) {
            return message;
        }
        String validation = validationMessages(message);
        return validation != null ? validation : getString(R.string.patient_sync_failed_generic_reason);
    }

    /** Field/global error messages from an OpenMRS "Invalid Submission" body, or null. */
    private static String validationMessages(String message) {
        if (message == null) return null;
        int start = message.indexOf('{');
        if (start < 0) return null;
        try {
            JsonObject error = JsonParser.parseString(message.substring(start)).getAsJsonObject().getAsJsonObject("error");
            if (error == null) return null;
            java.util.Set<String> parts = new java.util.LinkedHashSet<>();
            if (error.has("fieldErrors") && error.get("fieldErrors").isJsonObject()) {
                for (String field : error.getAsJsonObject("fieldErrors").keySet()) {
                    JsonElement errors = error.getAsJsonObject("fieldErrors").get(field);
                    if (!errors.isJsonArray()) continue;
                    for (JsonElement item : (JsonArray) errors) {
                        if (item.isJsonObject() && item.getAsJsonObject().has("message")) {
                            parts.add(field + ": " + item.getAsJsonObject().get("message").getAsString());
                        }
                    }
                }
            }
            if (error.has("globalErrors") && error.get("globalErrors").isJsonArray()) {
                for (JsonElement item : error.getAsJsonArray("globalErrors")) {
                    if (item.isJsonObject() && item.getAsJsonObject().has("message")) {
                        parts.add(item.getAsJsonObject().get("message").getAsString());
                    }
                }
            }
            return parts.isEmpty() ? null : String.join("; ", parts);
        } catch (RuntimeException notJson) {
            return null;
        }
    }
}
