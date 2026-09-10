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
import androidx.room.Query
import androidx.room.Update
import com.openmrs.android_sdk.library.models.Encountercreate

/**
 * The interface Encounter create room dao.
 */
@Dao
interface EncounterCreateRoomDAO {

    /**
     * Add encounter created long.
     *
     * @param encountercreate the encountercreate
     * @return the long
     */
    @Insert
    fun addEncounterCreated(encountercreate: Encountercreate): Long

    /**
     * Update existing encounter int.
     *
     * @param encountercreate the encountercreate
     */
    @Update
    fun updateExistingEncounter(encountercreate: Encountercreate)


    @Query("Select * FROM encountercreate")
    fun getAllCreatedEncounters(): List<Encountercreate>

    /**
     * Gets created encounters by id.
     *
     * @param id the id
     * @return the created encounters by id
     */
    @Query("Select * FROM encountercreate WHERE _id =:id")
    fun getCreatedEncountersByID(id: Long): Encountercreate

    /**
     * Gets every locally-created encounter for a specific patient (by local row id), regardless
     * of sync state - used to check whether a patient still has any unsynced form data before
     * their local footprint can be safely auto-deleted.
     */
    @Query("SELECT * FROM encountercreate WHERE patientid = :patientId")
    fun getCreatedEncountersByPatientId(patientId: Long): List<Encountercreate>

    /**
     * Deletes every locally-created encounter for a specific patient - part of removing a
     * patient's entire local footprint once everything about them is confirmed synced.
     */
    @Query("DELETE FROM encountercreate WHERE patientid = :patientId")
    fun deleteEncountersByPatientId(patientId: Long)
}