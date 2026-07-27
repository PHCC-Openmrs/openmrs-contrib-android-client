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
import com.openmrs.android_sdk.library.api.RestApi
import com.openmrs.android_sdk.library.api.RestServiceBuilder
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
     * Fetches all "Login Location"-tagged locations registered in a server, via the FHIR2
     * module - the same API the OpenMRS O3 web app uses for its login-location picker. The
     * legacy REST tag search (`/ws/rest/v1/location?tag=...`) can return an empty result set
     * for a tag search that this FHIR endpoint correctly resolves (a known quirk/bug in the
     * legacy REST webservices module's location-tag search on some OpenMRS instances), so this
     * avoids that entirely rather than working around it.
     *
     * Unlike the legacy endpoint, FHIR2 requires authentication outright (a plain 401 instead
     * of a silent empty result for an anonymous request). This is called from the login screen
     * before the user has necessarily submitted their credentials yet, so [username]/[password]
     * let the caller pass whatever is currently typed in the login form (mirroring how
     * [LoginRepository.getSession] authenticates the actual login call) - falling back to
     * whatever's already stored (e.g. a previous session, for the post-login "Select Location"
     * dialog) when not provided.
     *
     * @param url the URL of the server to fetch the locations from
     * @return observable list of LocationEntity
     */
    fun getLocations(url: String, username: String? = null, password: String? = null): Observable<List<LocationEntity>> {
        return createObservableIO(Callable {
            val fhirLocationEndPoint = url + ApplicationConstants.API.FHIR2_LOCATION_ENDPOINT
            val locations = mutableListOf<LocationEntity>()
            val fhirRestApi = if (!username.isNullOrEmpty() && !password.isNullOrEmpty()) {
                RestServiceBuilder.createService(RestApi::class.java, username, password)
            } else {
                restApi
            }

            var response = fhirRestApi.getFhirLocationsByTag(
                fhirLocationEndPoint, "Login Location", "data", FHIR_PAGE_SIZE
            ).execute()
            if (!response.isSuccessful || response.body() == null) {
                throw Exception("Error fetching locations: ${response.message()}")
            }

            var pageCount = 0
            while (true) {
                val bundle = response.body()!!
                bundle.entry?.forEach { entry ->
                    val resource = entry.resource
                    if (resource?.id != null && resource.name != null) {
                        locations.add(LocationEntity(resource.name!!).apply {
                            uuid = resource.id
                            name = resource.name
                        })
                    }
                }

                pageCount++
                val nextPageUrl = bundle.link?.firstOrNull { it.relation == "next" }?.url
                // Safety cap on pagination depth in case of a malformed/looping "next" link.
                if (nextPageUrl == null || pageCount >= MAX_FHIR_PAGES) break

                response = fhirRestApi.getFhirBundlePage(nextPageUrl).execute()
                if (!response.isSuccessful || response.body() == null) break
            }

            locations
        })
    }

    companion object {
        private const val FHIR_PAGE_SIZE = 50
        private const val MAX_FHIR_PAGES = 20
    }
}
