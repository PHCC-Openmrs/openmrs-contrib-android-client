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
package com.openmrs.android_sdk.library.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.openmrs.android_sdk.library.databases.entities.VisitAttributeTypeEntity

/**
 * The interface Visit attribute type room dao.
 */
@Dao
interface VisitAttributeTypeRoomDAO {

    /**
     * Saves visit attribute types, replacing any already cached under the same uuid.
     *
     * @param types the visit attribute types
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrUpdateVisitAttributeTypes(types: List<VisitAttributeTypeEntity>)

    /**
     * Gets all cached visit attribute types.
     *
     * @return the visit attribute types
     */
    @Query("SELECT * FROM visitattributetypes")
    fun getVisitAttributeTypes(): List<VisitAttributeTypeEntity>

    /**
     * Gets a cached visit attribute type by uuid.
     *
     * @param uuid the uuid
     * @return the visit attribute type, or null if it has never been cached
     */
    @Query("SELECT * FROM visitattributetypes WHERE uuid = :uuid")
    fun getVisitAttributeTypeByUuid(uuid: String): VisitAttributeTypeEntity?
}
