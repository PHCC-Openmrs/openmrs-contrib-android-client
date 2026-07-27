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
package com.openmrs.android_sdk.library

import com.openmrs.android_sdk.library.databases.AppDatabase

/**
 * Synchronous, offline-friendly privilege lookup for the currently logged-in user.
 *
 * Fails open by design: if the local cache hasn't been populated yet (fresh install,
 * login not completed, server too old to return roles/privileges, or any lookup error),
 * every check returns `true` so the UI behaves exactly as it did before RBAC existed.
 */
object PrivilegeChecker {

    @JvmStatic
    fun hasPrivilege(privilegeName: String): Boolean {
        val context = OpenmrsAndroid.getInstance() ?: return true
        val cache = try {
            AppDatabase.getDatabase(context).privilegeCacheRoomDAO().get()
        } catch (e: Exception) {
            null
        } ?: return true

        if (cache.isSuperUser) return true
        return cache.privilegeNames.any { it.equals(privilegeName, ignoreCase = true) }
    }

    @JvmStatic
    fun hasAnyPrivilege(vararg privilegeNames: String): Boolean =
        privilegeNames.isEmpty() || privilegeNames.any(::hasPrivilege)

    @JvmStatic
    fun clearCache() {
        val context = OpenmrsAndroid.getInstance() ?: return
        AppDatabase.getDatabase(context).privilegeCacheRoomDAO().clear()
    }
}
