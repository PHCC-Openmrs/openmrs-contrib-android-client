/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */

package org.openmrs.mobile.api;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.openmrs.android_sdk.utilities.ToastUtil;

import org.openmrs.mobile.R;
import org.openmrs.mobile.services.PatientService;

public class SyncStateReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        ToastUtil.notify(context.getString(R.string.patent_and_form_data_sync_resumed));
        // Only PatientService is started directly - it chains into VisitService, which in turn
        // chains into EncounterService, once each stage's own work is done. That way a single
        // trigger here cascades through the whole patient -> visit -> encounter dependency chain
        // by itself, instead of each stage only advancing on its own separate future trigger.
        // Starting VisitService/EncounterService here too would run them concurrently with (rather
        // than strictly after) their dependency's own sync, which is exactly the race that used to
        // cause duplicate patients/visits on the server.
        Intent i = new Intent(context, PatientService.class);
        context.startService(i);
    }
}
