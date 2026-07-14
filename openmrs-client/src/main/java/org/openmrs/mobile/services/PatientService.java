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
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

import dagger.hilt.android.AndroidEntryPoint;
import retrofit2.Call;
import retrofit2.Response;
import android.app.IntentService;
import android.content.Intent;
import android.util.Log;

import com.openmrs.android_sdk.library.api.RestApi;
import com.openmrs.android_sdk.library.api.repository.PatientRepository;
import com.openmrs.android_sdk.library.dao.PatientDAO;
import com.openmrs.android_sdk.library.models.Module;
import com.openmrs.android_sdk.library.models.Patient;
import com.openmrs.android_sdk.library.models.PatientDto;
import com.openmrs.android_sdk.library.models.Results;
import com.openmrs.android_sdk.utilities.ApplicationConstants;
import com.openmrs.android_sdk.utilities.ModuleUtils;
import com.openmrs.android_sdk.utilities.NetworkUtils;
import com.openmrs.android_sdk.utilities.PatientAndMatchingPatients;
import com.openmrs.android_sdk.utilities.PatientComparator;
import com.openmrs.android_sdk.utilities.ToastUtil;

import org.openmrs.mobile.R;
import org.openmrs.mobile.activities.matchingpatients.MatchingPatientsActivity;
import org.openmrs.mobile.utilities.PatientAndMatchesWrapper;

@AndroidEntryPoint
public class PatientService extends IntentService {
    public static final String PATIENT_SERVICE_TAG = "PATIENT_SERVICE";
    private boolean calculatedLocally = false;

    @Inject
    PatientRepository patientRepository;
    @Inject
    RestApi restApi;
    @Inject
    PatientDAO patientDAO;

    public PatientService() {
        super("Register Patients");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Log.i(PATIENT_SERVICE_TAG, "PatientService started");
        if (!NetworkUtils.isOnline()) {
            Log.w(PATIENT_SERVICE_TAG, "No internet connection, sync postponed");
            return;
        }

        List<Patient> patientList = patientDAO.getUnSyncedPatients();
        if (patientList.isEmpty()) {
            Log.i(PATIENT_SERVICE_TAG, "No unsynced patients found");
            return;
        }

        Log.i(PATIENT_SERVICE_TAG, "Found " + patientList.size() + " unsynced patients. Checking server capabilities...");
        
        boolean isRegistrationCorePresent = false;
        try {
            Response<Results<Module>> moduleResp = restApi.getModules(ApplicationConstants.API.FULL).execute();
            if (moduleResp.isSuccessful() && moduleResp.body() != null) {
                isRegistrationCorePresent = ModuleUtils.isRegistrationCore1_7orAbove(moduleResp.body().getResults());
            }
        } catch (IOException e) {
            Log.e(PATIENT_SERVICE_TAG, "Error fetching modules, defaulting to local similarity check", e);
        }

        PatientAndMatchesWrapper patientAndMatchesWrapper = new PatientAndMatchesWrapper();
        for (Patient patient : patientList) {
            String patientName = (patient.getName() != null) ? patient.getName().getNameString() : "ID " + patient.getId();
            Log.i(PATIENT_SERVICE_TAG, "Processing patient: " + patientName);

            try {
                if (isRegistrationCorePresent) {
                    syncPatientWithServerSimilarityCheck(patient, patientAndMatchesWrapper);
                } else {
                    syncPatientWithLocalSimilarityCheck(patient, patientAndMatchesWrapper);
                }
            } catch (Exception e) {
                Log.e(PATIENT_SERVICE_TAG, "Failed to sync patient " + patientName, e);
            }
        }

        if (!patientAndMatchesWrapper.getMatchingPatients().isEmpty()) {
            Log.i(PATIENT_SERVICE_TAG, "Found potential duplicates on server, showing MatchingPatientsActivity");
            Intent intent1 = new Intent(getApplicationContext(), MatchingPatientsActivity.class);
            intent1.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent1.putExtra(ApplicationConstants.BundleKeys.CALCULATED_LOCALLY, calculatedLocally);
            intent1.putExtra(ApplicationConstants.BundleKeys.PATIENTS_AND_MATCHES, patientAndMatchesWrapper);
            startActivity(intent1);
        }
    }

    private void syncPatientWithLocalSimilarityCheck(Patient patient, PatientAndMatchesWrapper patientAndMatchesWrapper) throws Exception {
        calculatedLocally = true;
        String givenName = (patient.getName() != null) ? patient.getName().getGivenName() : null;
        
        if (givenName == null || givenName.isEmpty()) {
            Log.i(PATIENT_SERVICE_TAG, "No given name, skipping similarity check and syncing directly");
            patientRepository.syncPatient(patient).single().toBlocking().first();
            return;
        }

        Response<Results<PatientDto>> resp = restApi.getPatientsDto(givenName, ApplicationConstants.API.FULL).execute();
        if (resp.isSuccessful() && resp.body() != null) {
            List<Patient> patientList = new ArrayList<>();
            for (PatientDto p : resp.body().getResults()) {
                patientList.add(p.getPatient());
            }
            List<Patient> similarPatients = new PatientComparator().findSimilarPatient(patientList, patient);
            if (!similarPatients.isEmpty()) {
                patientAndMatchesWrapper.addToList(new PatientAndMatchingPatients(patient, similarPatients));
            } else {
                patientRepository.syncPatient(patient).single().toBlocking().first();
            }
        } else {
            Log.e(PATIENT_SERVICE_TAG, "Search failed: " + resp.message() + ". Syncing directly.");
            patientRepository.syncPatient(patient).single().toBlocking().first();
        }
    }

    private void syncPatientWithServerSimilarityCheck(Patient patient, PatientAndMatchesWrapper patientAndMatchesWrapper) throws Exception {
        calculatedLocally = false;
        Response<Results<Patient>> resp = restApi.getSimilarPatients(patient.toMap()).execute();
        if (resp.isSuccessful() && resp.body() != null) {
            List<Patient> similarPatients = resp.body().getResults();
            if (!similarPatients.isEmpty()) {
                patientAndMatchesWrapper.addToList(new PatientAndMatchingPatients(patient, similarPatients));
            } else {
                patientRepository.syncPatient(patient).single().toBlocking().first();
            }
        } else {
            Log.e(PATIENT_SERVICE_TAG, "Server similarity check failed: " + resp.message() + ". Falling back to local check.");
            syncPatientWithLocalSimilarityCheck(patient, patientAndMatchesWrapper);
        }
    }
}
