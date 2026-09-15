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

import dagger.hilt.android.AndroidEntryPoint;
import android.app.IntentService;
import android.content.Intent;
import android.util.Log;

import com.openmrs.android_sdk.library.api.repository.ProgramEnrollmentRepository;
import com.openmrs.android_sdk.utilities.NetworkUtils;

/**
 * Pushes the program-enrolment episodes that starting (and ending) a visit for a service opened
 * offline, once connectivity is back. Mirrors {@link VisitService}, and runs right after it in the
 * same chain.
 *
 * <p>An episode only needs its patient to be synced, not its visit - it references the patient and
 * the program, never the visit - so a slow or failing visit push never holds it up.
 */
@AndroidEntryPoint
public class ProgramEnrollmentService extends IntentService {
    public static final String PROGRAM_ENROLLMENT_SERVICE_TAG = "PROGRAM_ENROLLMENT_SERVICE";

    @Inject
    ProgramEnrollmentRepository programEnrollmentRepository;

    public ProgramEnrollmentService() {
        super("Sync Program Enrollments");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        try {
            if (!NetworkUtils.isOnline()) {
                Log.w(PROGRAM_ENROLLMENT_SERVICE_TAG, "No internet connection, sync postponed");
                return;
            }
            programEnrollmentRepository.syncPendingEnrollments();
        } catch (Exception e) {
            Log.e(PROGRAM_ENROLLMENT_SERVICE_TAG, "Failed to sync program enrollments", e);
        } finally {
            // Chain straight into EncounterService, same reasoning as the rest of the chain: one
            // trigger should cascade through every stage.
            startService(new Intent(this, EncounterService.class));
        }
    }
}
