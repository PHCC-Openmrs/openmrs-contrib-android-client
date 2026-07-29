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

import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Dao
import com.openmrs.android_sdk.library.databases.entities.AppointmentEntity
import io.reactivex.Single

/**
 * Room DAO backing the offline cache of a patient's appointments (from the `appointments`
 * module's `/appointment/search` endpoint).
 */
@Dao
interface AppointmentRoomDAO {

    /**
     * Inserts or replaces (by uuid) a batch of appointments, e.g. after a fresh server search.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun addOrUpdateAll(appointmentEntities: List<AppointmentEntity>)

    /**
     * Updates just the status of a cached appointment, e.g. after cancelling it.
     */
    @Query("UPDATE appointments SET status = :status WHERE uuid = :uuid")
    fun updateStatus(uuid: String, status: String)

    /**
     * Get cached appointments for a patient.
     */
    @Query("SELECT * FROM appointments WHERE patient_uuid = :patientUuid")
    fun getAppointmentsForPatient(patientUuid: String): Single<List<AppointmentEntity>>
}
