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

import com.openmrs.android_sdk.library.OpenmrsAndroid;
import com.openmrs.android_sdk.library.api.repository.EncounterRepository;
import com.openmrs.android_sdk.library.databases.AppDatabase;
import com.openmrs.android_sdk.library.models.Encountercreate;
import com.openmrs.android_sdk.utilities.NetworkUtils;

/**
 * The type Encounter service.
 */
@AndroidEntryPoint
public class EncounterService extends IntentService {
    public static final String ENCOUNTER_SERVICE_TAG = "ENCOUNTER_SERVICE";

    @Inject
    EncounterRepository encounterRepository;

    /**
     * Instantiates a new Encounter service.
     */
    public EncounterService() {
        super("Save Encounter");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (!NetworkUtils.isOnline()) return;

        List<Encountercreate> encounterCreateList = AppDatabase.getDatabase(OpenmrsAndroid.getInstance().getApplicationContext())
                .encounterCreateRoomDAO()
                .getAllCreatedEncounters();

        for (final Encountercreate encounterCreate : encounterCreateList) {
            if (encounterCreate.getSynced()) {
                continue;
            }
            // Deliberately does not try to sync the encounter's visit here first: VisitService is
            // the sole owner of syncing a visit. Doing it here too would race with VisitService's
            // own attempt for the same visit (both running concurrently, unaware of each other)
            // and can create a duplicate visit on the server. saveEncounter() below already
            // handles an unsynced visit gracefully by just queuing for the next reconnect.
            encounterRepository.saveEncounter(encounterCreate)
                    .subscribe(result -> { }, throwable ->
                            Log.e(ENCOUNTER_SERVICE_TAG, "Failed to sync encounter " + encounterCreate.getId(), throwable));
        }
    }
}
