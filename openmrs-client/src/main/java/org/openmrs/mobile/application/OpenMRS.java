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

package org.openmrs.mobile.application;

import javax.inject.Inject;
import java.io.File;

import dagger.hilt.android.HiltAndroidApp;
import android.content.Intent;
import android.os.Build;

import androidx.hilt.work.HiltWorkerFactory;
import androidx.multidex.MultiDexApplication;
import androidx.annotation.NonNull;
import androidx.work.Configuration;

import com.openmrs.android_sdk.library.OpenMRSLogger;
import com.openmrs.android_sdk.library.OpenmrsAndroid;

import org.jetbrains.annotations.NotNull;
import org.openmrs.mobile.utilities.LanguageUtils;
import org.openmrs.mobile.api.ConnectivityStateMonitor;
import org.openmrs.mobile.services.AuthenticateCheckService;
import org.openmrs.mobile.services.FormListService;

@HiltAndroidApp
public class OpenMRS extends MultiDexApplication implements Configuration.Provider {
    private static final String OPENMRS_DIR_NAME = "OpenMRS";
    private static final String OPENMRS_DIR_PATH = File.separator + OPENMRS_DIR_NAME;
    private static String mExternalDirectoryPath;
    private static OpenMRS instance;
    @Inject
    OpenMRSLogger mLogger;
    @Inject
    HiltWorkerFactory workerFactory;

    public static OpenMRS getInstance() {
        return instance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        OpenmrsAndroid.initializeSdk(this);
        // Before anything below can load a string: services and receivers started here have no
        // activity to take the app's language from - see LanguageUtils.applyLanguage.
        applyAppLanguage();

        if (mExternalDirectoryPath == null) {
            mExternalDirectoryPath = this.getExternalFilesDir(null).toString();
        }
        // Starting a plain background service from Application.onCreate() can be refused by the
        // OS (BackgroundServiceStartNotAllowedException) when the process itself was launched in
        // the background - e.g. the crash-recovery relaunch below, or various OS-triggered
        // restarts. That must never take down app startup itself; these are opportunistic
        // refreshes that also run at other well-defined points (e.g. login).
        try {
            Intent i = new Intent(this, FormListService.class);
            startService(i);
        } catch (Exception e) {
            mLogger.e("Could not start FormListService from Application.onCreate(): " + e.getMessage());
        }
        try {
            Intent intent = new Intent(this, AuthenticateCheckService.class);
            startService(intent);
        } catch (Exception e) {
            mLogger.e("Could not start AuthenticateCheckService from Application.onCreate(): " + e.getMessage());
        }

        new ConnectivityStateMonitor(this).register();
    }

    @Override
    public void onConfigurationChanged(@NonNull android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // A configuration change (rotation, the device language changing, ...) resets the
        // application context to the device's own locale - put the app's language back.
        applyAppLanguage();
    }

    private void applyAppLanguage() {
        try {
            LanguageUtils.applyLanguage(this);
        } catch (Exception e) {
            // The language is cosmetic - never let it take down app startup.
            mLogger.e("Could not apply the app language: " + e.getMessage());
        }
    }

    @NotNull
    @Override
    public Configuration getWorkManagerConfiguration() {
        return new Configuration.Builder()
                .setWorkerFactory(workerFactory)
                .build();
    }


    @Override
    public void onTerminate() {
        super.onTerminate();
    }

    public boolean isRunningKitKatVersionOrHigher() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;
    }
}
