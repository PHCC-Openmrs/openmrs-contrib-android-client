/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package com.openmrs.android_sdk.library.databases.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Single-row cache of the current logged-in user's effective privileges/roles,
 * resolved (union + inherited roles) at login time. Only one account is ever
 * logged in at a time in this app, so a denormalized singleton row is enough.
 */
@Entity(tableName = "privilege_cache_table")
data class PrivilegeCacheEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    @ColumnInfo(name = "privilege_names") val privilegeNames: List<String>,
    @ColumnInfo(name = "role_names") val roleNames: List<String>,
    @ColumnInfo(name = "is_super_user") val isSuperUser: Boolean,
    @ColumnInfo(name = "cached_at") val cachedAt: Long
) {
    companion object {
        const val SINGLETON_ID = 0
    }
}
