/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package com.openmrs.android_sdk.library.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.openmrs.android_sdk.library.databases.entities.PrivilegeCacheEntity

/**
 * Plain synchronous methods (DB is opened with allowMainThreadQueries()) since
 * privilege checks happen frequently on the UI thread while building menus/tabs.
 */
@Dao
interface PrivilegeCacheRoomDAO {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(cache: PrivilegeCacheEntity)

    @Query("SELECT * FROM privilege_cache_table WHERE id = 0 LIMIT 1")
    fun get(): PrivilegeCacheEntity?

    @Query("DELETE FROM privilege_cache_table")
    fun clear()
}
