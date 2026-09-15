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
import retrofit2.Response;
import android.app.IntentService;
import android.content.Intent;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.openmrs.android_sdk.library.api.RestApi;
import com.openmrs.android_sdk.library.api.repository.FormRepository;
import com.openmrs.android_sdk.library.dao.EncounterTypeRoomDAO;
import com.openmrs.android_sdk.library.databases.AppDatabase;
import com.openmrs.android_sdk.library.databases.entities.FormResourceEntity;
import com.openmrs.android_sdk.library.models.EncounterType;
import com.openmrs.android_sdk.library.models.Results;
import com.openmrs.android_sdk.utilities.ApplicationConstants;
import com.openmrs.android_sdk.utilities.NetworkUtils;
import com.openmrs.android_sdk.utilities.ToastUtil;

import org.openmrs.mobile.utilities.PrivilegeUtils;

@AndroidEntryPoint
public class FormListService extends IntentService {
    public static final String ACTION_FORM_LIST_SYNCED = "org.openmrs.mobile.ACTION_FORM_LIST_SYNCED";

    @Inject
    RestApi apiService;
    @Inject
    AppDatabase appDatabase;
    @Inject
    FormRepository formRepository;

    public FormListService() {
        super("Sync Form List");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (!NetworkUtils.isOnline()) return;
        // Refresh forms - only for roles that can actually access Form Entry; other roles
        // (e.g. Clerk) don't hold "Get Forms" server-side, so attempting this would just
        // 403 and surface a confusing error toast right after login.
        if (PrivilegeUtils.hasAnyPrivilege(ApplicationConstants.Privileges.ADD_ENCOUNTERS, ApplicationConstants.Privileges.FORM_ENTRY)) {
            List<FormResourceEntity> formResourceList = formRepository.syncFormList();
            if (formResourceList != null) {
                ToastUtil.notify("Synced " + formResourceList.size() + " forms");
            } else {
                ToastUtil.error("Error fetching forms");
            }
        }
        // Refresh encounter types
        EncounterTypeRoomDAO encounterTypeRoomDAO = appDatabase.encounterTypeRoomDAO();
        try {
            Response<Results<EncounterType>> response2 = apiService.getEncounterTypes().execute();
            if (response2.isSuccessful() && response2.body() != null) {
                encounterTypeRoomDAO.deleteAllEncounterTypes();
                List<EncounterType> encounterTypeList = response2.body().getResults();
                for (EncounterType encounterType : encounterTypeList) {
                    encounterTypeRoomDAO.addEncounterType(encounterType);
                }
            } else {
                ToastUtil.error(response2.message());
            }
        } catch (Exception e) {
            ToastUtil.error("Error fetching encounter types: " + e.getMessage());
        }

        LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(ACTION_FORM_LIST_SYNCED));
    }
}
