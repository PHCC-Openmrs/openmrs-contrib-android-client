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
package com.openmrs.android_sdk.library.api.repository

import com.openmrs.android_sdk.library.dao.PrivilegeCacheRoomDAO
import com.openmrs.android_sdk.library.databases.entities.PrivilegeCacheEntity
import com.openmrs.android_sdk.library.models.Role
import com.openmrs.android_sdk.library.models.User
import com.openmrs.android_sdk.utilities.ApplicationConstants
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves a [User]'s effective privileges (directly-assigned privileges union
 * the privileges of all its roles, recursively through role inheritance) and
 * caches them locally so [com.openmrs.android_sdk.library.PrivilegeChecker] can
 * answer "does the current user have privilege X" synchronously and offline.
 */
@Singleton
class PrivilegeRepository @Inject constructor(
    private val privilegeCacheRoomDAO: PrivilegeCacheRoomDAO
) : BaseRepository() {

    /**
     * Resolves and caches [user]'s effective privileges. If the server didn't return any
     * roles/privileges at all (older OpenMRS instance without RBAC fields, or a stale/failed
     * fetch), any previously cached data is cleared so [PrivilegeChecker] fails open.
     */
    fun cacheEffectivePrivileges(user: User) {
        if (user.roles == null && user.privileges == null) {
            privilegeCacheRoomDAO.clear()
            return
        }

        val privilegeNames = mutableSetOf<String>()
        val roleNames = mutableSetOf<String>()
        user.privileges?.forEach { it.name?.let(privilegeNames::add) }
        user.roles?.forEach { collectRole(it, privilegeNames, roleNames, mutableSetOf()) }

        val isSuperUser = roleNames.any { it.equals(ApplicationConstants.RBAC.SUPERUSER_ROLE_NAME, ignoreCase = true) }

        privilegeCacheRoomDAO.insert(
            PrivilegeCacheEntity(
                privilegeNames = privilegeNames.toList(),
                roleNames = roleNames.toList(),
                isSuperUser = isSuperUser,
                cachedAt = System.currentTimeMillis()
            )
        )
    }

    fun clearCache() = privilegeCacheRoomDAO.clear()

    /** Recursively walks [role] and its inherited roles, guarding against inheritance cycles. */
    private fun collectRole(
        role: Role,
        privilegeNames: MutableSet<String>,
        roleNames: MutableSet<String>,
        visited: MutableSet<String>
    ) {
        val key = role.uuid ?: role.name ?: return
        if (!visited.add(key)) return
        if (role.retired == true) return

        role.name?.let(roleNames::add)
        role.privileges?.forEach { it.name?.let(privilegeNames::add) }
        role.inheritedRoles?.forEach { collectRole(it, privilegeNames, roleNames, visited) }
    }
}
