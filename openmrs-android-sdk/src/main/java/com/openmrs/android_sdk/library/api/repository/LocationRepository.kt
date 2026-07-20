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
import com.openmrs.android_sdk.library.dao.LocationDAO
import com.openmrs.android_sdk.library.databases.AppDatabaseHelper.createObservableIO
import com.openmrs.android_sdk.library.databases.entities.LocationEntity
import com.openmrs.android_sdk.utilities.ApplicationConstants
import rx.Observable
import javax.inject.Inject
import javax.inject.Singleton
import java.util.concurrent.Callable

/**
 * The type Location repository.
 */
@Singleton
class LocationRepository @Inject constructor(private val locationDAO: LocationDAO) : BaseRepository() {

    /**
     * Gets location (only has uuid).
     *
     * @return the location LocationEntity
     */
    val location: LocationEntity?
        get() {
            val locationName = OpenmrsAndroid.getLocation()
            if (locationName.isNotBlank()) {
                try {
                    val localLocation = locationDAO.findLocationByName(locationName)
                    if (localLocation != null && localLocation.uuid != null && localLocation.uuid!!.isNotBlank()) {
                        return localLocation
                    }
                } catch (e: Exception) {
                    logger.w("Failed to fetch location from local DAO: ${e.message}")
                }
            }

            try {
                // Use static service builder to avoid lateinit initialization issues in background services
                val service = com.openmrs.android_sdk.library.api.RestServiceBuilder.createService(com.openmrs.android_sdk.library.api.RestApi::class.java)
                val response = service.getLocations(null).execute()
                if (response.isSuccessful && response.body() != null) {
                    for (result in response.body()!!.results) {
                        if (result.display?.trim().equals(locationName.trim(), ignoreCase = true)) {
                            return result
                        }
                    }
                }
            } catch (e: Exception) {
                logger.e("Error fetching location from server", e)
            }
            return null
        }

    /**
     * Fetches all locations registered in a server.
     *
     * @param url the URL of the server to fetch the locations from
     * @return observable list of LocationEntity
     */
    fun getLocations(url: String): Observable<List<LocationEntity>> {
        return createObservableIO(Callable {
            val locationEndPoint = url + ApplicationConstants.API.REST_ENDPOINT + "location"
            restApi.getLocations(locationEndPoint, "Login Location", "full").execute().run {
                if (isSuccessful && body() != null) return@Callable body()!!.results
                else throw Exception("Error fetching concepts: ${message()}")
            }
        })
    }
}
