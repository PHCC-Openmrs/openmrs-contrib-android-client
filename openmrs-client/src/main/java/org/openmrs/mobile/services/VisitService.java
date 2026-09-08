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

import com.openmrs.android_sdk.library.api.repository.VisitRepository;
import com.openmrs.android_sdk.library.dao.VisitDAO;
import com.openmrs.android_sdk.library.models.Patient;
import com.openmrs.android_sdk.library.models.Visit;
import com.openmrs.android_sdk.utilities.NetworkUtils;

/**
 * Pushes visits that were started offline (or before their patient was synced) to the server,
 * once connectivity is back. Mirrors {@link PatientService} and {@link EncounterService}.
 */
@AndroidEntryPoint
public class VisitService extends IntentService {
    public static final String VISIT_SERVICE_TAG = "VISIT_SERVICE";

    @Inject
    VisitRepository visitRepository;
    @Inject
    VisitDAO visitDAO;

    public VisitService() {
        super("Sync Visits");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        try {
            if (!NetworkUtils.isOnline()) {
                Log.w(VISIT_SERVICE_TAG, "No internet connection, sync postponed");
                return;
            }

            List<Visit> unsyncedVisits = visitDAO.getUnsyncedVisits();
            if (unsyncedVisits.isEmpty()) {
                return;
            }

            Log.i(VISIT_SERVICE_TAG, "Found " + unsyncedVisits.size() + " unsynced visit(s)");
            for (Visit visit : unsyncedVisits) {
                try {
                    Patient patient = visit.getPatient();
                    if (patient == null || patient.getId() == null || !patient.isSynced()) {
                        // Since this only ever runs after PatientService's own sync attempt has
                        // already fully completed (see the chained startService call below, and
                        // the matching one in PatientService), a patient still unsynced at this
                        // point genuinely failed to sync (e.g. a server error) rather than just
                        // being mid-flight - nothing more to do for this visit until that's
                        // resolved and the chain runs again.
                        continue;
                    }
                    visitRepository.syncStartedVisit(visit, patient);
                } catch (Exception e) {
                    Log.e(VISIT_SERVICE_TAG, "Failed to sync visit " + visit.getId(), e);
                }
            }
        } finally {
            // Chain straight into EncounterService, same reasoning as PatientService chaining into
            // this service: one trigger should cascade through the whole dependency chain.
            startService(new Intent(this, EncounterService.class));
        }
    }
}
