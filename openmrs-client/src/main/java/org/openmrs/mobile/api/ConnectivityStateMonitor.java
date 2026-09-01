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

import java.util.concurrent.atomic.AtomicBoolean;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;

import com.openmrs.android_sdk.library.OpenmrsAndroid;
import com.openmrs.android_sdk.utilities.NetworkUtils;

/**
 * Watches system connectivity and automatically resumes the patient/encounter sync -
 * the same trigger the toolbar sync button uses - as soon as the network comes back,
 * instead of requiring the user to notice and tap sync manually.
 */
public class ConnectivityStateMonitor {

    public static final String SYNC_PATIENTS_ACTION = "org.openmrs.mobile.intent.action.SYNC_PATIENTS";

    private final Context appContext;
    private final AtomicBoolean wasDisconnected = new AtomicBoolean(false);

    private final ConnectivityManager.NetworkCallback networkCallback = new ConnectivityManager.NetworkCallback() {
        @Override
        public void onAvailable(Network network) {
            if (wasDisconnected.compareAndSet(true, false)) {
                OpenmrsAndroid.setSyncState(true);
                triggerSync(appContext);
            }
        }

        @Override
        public void onLost(Network network) {
            if (!NetworkUtils.hasNetwork()) {
                wasDisconnected.set(true);
            }
        }
    };

    public ConnectivityStateMonitor(Context context) {
        this.appContext = context.getApplicationContext();
        this.wasDisconnected.set(!NetworkUtils.hasNetwork());
    }

    /**
     * Starts watching for connectivity changes for the lifetime of the process.
     */
    public void register() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            // registerNetworkCallback isn't available; the manual sync button remains the
            // only way to resume sync on these very old devices.
            return;
        }
        ConnectivityManager connectivityManager =
                (ConnectivityManager) appContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) {
            return;
        }
        NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();
        connectivityManager.registerNetworkCallback(request, networkCallback);
    }

    /**
     * Fires the patient/encounter sync. Uses an explicit intent naming {@link SyncStateReceiver}
     * directly, rather than an action-only implicit broadcast: since Android 8.0 (API 26), the OS
     * drops implicit broadcasts sent to manifest-declared receivers (with a small exemption list
     * for system broadcasts that {@code SYNC_PATIENTS} isn't part of), so an implicit broadcast
     * here would silently never reach {@link SyncStateReceiver} on any real device.
     */
    public static void triggerSync(Context context) {
        Intent intent = new Intent(context, SyncStateReceiver.class);
        intent.setAction(SYNC_PATIENTS_ACTION);
        context.sendBroadcast(intent);
    }
}
