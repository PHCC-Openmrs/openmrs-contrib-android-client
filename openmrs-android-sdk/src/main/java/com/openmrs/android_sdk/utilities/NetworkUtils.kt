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
package com.openmrs.android_sdk.utilities

import android.content.Context
import android.net.ConnectivityManager
import android.preference.PreferenceManager
import com.openmrs.android_sdk.library.OpenmrsAndroid

object NetworkUtils {
    @JvmStatic
    fun hasNetwork(): Boolean {
        val connectivityManager = OpenmrsAndroid.getInstance()?.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetworkInfo = connectivityManager.activeNetworkInfo
        return activeNetworkInfo != null && activeNetworkInfo.isConnectedOrConnecting
    }

    /**
     * True when the user hasn't manually disabled sync (the toolbar sync toggle) AND the device
     * currently has real network connectivity - always recomputed live from [hasNetwork].
     *
     * This used to also PERSIST "false" to SharedPreferences the first time it observed no
     * connectivity, then trusted that stale value forever after without ever re-checking real
     * connectivity - so a single transient offline moment anywhere in the app (any background
     * sync attempt, on any screen) could silently and permanently disable ALL later sync
     * (patients, visits, encounters/forms, allergies, providers, observations...) even once the
     * device reconnected, until the user happened to manually toggle the sync icon. A read-only
     * connectivity check must never have that kind of persistent side effect - the manual toggle
     * itself is still written explicitly via [com.openmrs.android_sdk.library.OpenmrsAndroid.setSyncState].
     */
    @JvmStatic
    fun isOnline(): Boolean {
        val prefs = PreferenceManager.getDefaultSharedPreferences(OpenmrsAndroid.getInstance())
        val toggle = prefs.getBoolean("sync", true)
        return toggle && hasNetwork()
    }
}