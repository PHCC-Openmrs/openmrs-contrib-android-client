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

import com.openmrs.android_sdk.library.OpenmrsAndroid
import com.openmrs.android_sdk.library.databases.AppDatabase
import com.openmrs.android_sdk.library.databases.AppDatabaseHelper
import com.openmrs.android_sdk.library.databases.entities.ProgramEntity
import com.openmrs.android_sdk.library.models.ProgramCreate
import com.openmrs.android_sdk.library.models.ProgramGet
import com.openmrs.android_sdk.utilities.ApplicationConstants
import com.openmrs.android_sdk.utilities.NetworkUtils
import retrofit2.Call
import rx.Observable
import java.util.concurrent.Callable
import javax.inject.Inject

class ProgramRepository @Inject constructor() : BaseRepository(){

    val representation =  "full"
    var programRoomDAO = AppDatabase.getDatabase(OpenmrsAndroid.getInstance()!!.applicationContext).programRoomDAO()

    /**
     * Executes a retrofit request
     *
     * @param call the interface call
     * @param message the error message to display
     *
     * @return T
     */
    fun <T> executeRequest(call: Call<T>, message: String): T {
        val response = call.execute()

        if (response.isSuccessful && response != null) {
            return response.body()!!
        } else {
            logger.e(message + response.message())
            throw Exception(response.message())
        }
    }

    /**
     * Creates a program on the server
     *
     * @param program the ProgramCreate type
     *
     * @return the ProgramGet type Observable
     */
    fun createProgram(program: ProgramCreate): Observable<ProgramGet> {
        return AppDatabaseHelper.createObservableIO<ProgramGet>(Callable {
            val call = restApi.createProgram(program)
            executeRequest(call, "Error creating Program: ")
        })
    }

    /**
     * Get all Programs from the server
     *
     * @return the List of ProgramGet type
     */
    fun getAllPrograms(): Observable<List<ProgramGet>> {
        return AppDatabaseHelper.createObservableIO<List<ProgramGet>>(Callable {
            val call = restApi.getAllPrograms(representation)
            executeRequest(call, "Error getting all the programs: ").results
        })
    }

    /**
     * Get a Program by UUID
     *
     * @return the Program with given UUID
     */
    fun getProgramByUuid(uuid: String): Observable<ProgramGet> {
        return AppDatabaseHelper.createObservableIO<ProgramGet>(Callable {
            val call = restApi.getProgramByUuid(uuid, representation)
            executeRequest(call, "Error fetching the program ")
        })
    }

    /**
     * Update a Program by UUID
     *
     * @param uuid the uuid of the program to update
     * @param program the ProgramCreate type
     *
     * @return the updated Program
     */
    fun updateProgram(uuid: String, program: ProgramCreate): Observable<ProgramGet> {
        return AppDatabaseHelper.createObservableIO<ProgramGet>(Callable {
            val call = restApi.updateProgram(uuid, program)
            executeRequest(call, "Error updating the program ")
        })
    }

    /**
     * Delete a Program by UUID
     *
     * @param uuid the uuid of the Program to update
     *
     * @return the deleted Program
     */
    fun deleteProgram(uuid: String): Observable<ProgramGet> {
        return AppDatabaseHelper.createObservableIO<ProgramGet>(Callable {
            val call = restApi.deleteProgram(uuid)
            executeRequest(call, "Error deleting the program ")
        })
    }

    /**
     * Get all drugs from the server and save to local database
     *
     * @return Observable<List<Drug>>
     */

    fun getAllProgramsAndSaveLocally(): Observable<List<ProgramGet>> {
        if (!NetworkUtils.isOnline()) throw Exception("Must be online to fetch Programs")
        restApi.getAllPrograms("full").execute().run {

            if (isSuccessful && this.body() != null) {
                val programs: List<ProgramGet> = this.body()!!.results
                val convertedList = AppDatabaseHelper.convertProgramListToEntityList(programs)
                // Replace the whole cache rather than upsert into it: `id`, not `uuid`, is this
                // table's primary key, so REPLACE never finds a conflicting row to overwrite - a
                // plain insert on every refresh would just keep appending duplicate rows per uuid.
                programRoomDAO.deleteAllPrograms()
                programRoomDAO.insertOrUpdatePrograms(convertedList)
                return Observable.just(programs)
            } else {
                throw Exception("getAllProgramsAndSaveLocally error: ${message()}")
            }
        }
    }

    /**
     * The services (programs) selectable for a visit at a given location.
     *
     * Refreshed from the server when online and read from the local cache otherwise, so the
     * start-visit form offers the same services offline as online. The result is filtered by
     * [ApplicationConstants.ProgramLocationRestrictions] against the location chosen in the form
     * (not the session location), mirroring the web client's `useServicePrograms`.
     *
     * @param locationUuid the visit location the services must be offered at
     * @return the services offered there
     */
    fun getServicePrograms(locationUuid: String?): Observable<List<ProgramEntity>> {
        return AppDatabaseHelper.createObservableIO(Callable {
            if (NetworkUtils.isOnline()) {
                try {
                    getAllProgramsAndSaveLocally()
                } catch (e: Exception) {
                    logger.e("Could not refresh the services, using the cached ones: " + e.message)
                }
            }
            val cached = try {
                programRoomDAO.getAllPrograms()
            } catch (e: Exception) {
                emptyList()
            }
            filterProgramsByLocation(cached, locationUuid)
        })
    }

    /**
     * Get a Program by UUID and save to local database
     *
     * @param uuid the uuid of the Program to fetch and save
     *
     * @return the Program fetched from the server
     */
    fun getProgramByUuidAndSaveLocally(uuid: String): Observable<ProgramGet> {
        if (!NetworkUtils.isOnline()) throw Exception("Must be online to fetch Program")
        restApi.getProgramByUuid(uuid, "full").execute().run {

            if (isSuccessful && this.body() != null) {
                val program: ProgramGet = this.body()!!
                val programEntity = AppDatabaseHelper.convert(program)
                // Same reasoning as getAllProgramsAndSaveLocally: `id` is the primary key, not
                // `uuid`, so a second call for the same program would otherwise leave two rows
                // for it instead of replacing the first.
                programRoomDAO.deleteProgramByUuid(uuid)
                programRoomDAO.insertProgram(programEntity)
                return Observable.just(program)
            } else {
                throw Exception("getProgramByUuidAndSaveLocally error: ${message()}")
            }
        }
    }

    companion object {
        /**
         * Keeps only the programs offered at [locationUuid]. A program with no restriction, or one
         * whose restriction lists no locations, is offered everywhere; a restricted program is
         * offered only where it is listed, and nowhere at all while no location is chosen.
         *
         * Mirrors the web client's `filterProgramsByLocation`, which exists for the same reason:
         * OpenMRS programs have no location field of their own.
         */
        @JvmStatic
        fun filterProgramsByLocation(programs: List<ProgramEntity>, locationUuid: String?): List<ProgramEntity> {
            val restrictions = ApplicationConstants.ProgramLocationRestrictions.RESTRICTIONS
            if (restrictions.isEmpty()) return programs

            return programs.filter { program ->
                val allowedLocations = restrictions[program.uuid]
                if (allowedLocations.isNullOrEmpty()) true
                else !locationUuid.isNullOrEmpty() && allowedLocations.contains(locationUuid)
            }
        }
    }
}
