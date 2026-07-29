/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package com.openmrs.android_sdk.library.api.repository

import com.openmrs.android_sdk.library.databases.AppDatabaseHelper
import com.openmrs.android_sdk.library.models.ConceptSearchResult
import com.openmrs.android_sdk.library.models.PatientDiagnosisResponse
import okhttp3.MediaType
import okhttp3.RequestBody
import org.json.JSONObject
import rx.Observable
import java.util.concurrent.Callable
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles diagnosis concept search and patient diagnosis creation, matching the
 * `/ws/rest/v1/concept` (fuzzy search, filtered to the Diagnosis concept class) and
 * `/ws/rest/v1/patientdiagnoses` endpoints used by OpenMRS O3's Visit Note widget.
 */
@Singleton
class DiagnosisRepository @Inject constructor() : BaseRepository() {

    /**
     * Fuzzy-searches Diagnosis-class concepts by name.
     */
    fun searchDiagnoses(query: String): Observable<List<ConceptSearchResult>> {
        return AppDatabaseHelper.createObservableIO(Callable {
            if (query.isBlank()) return@Callable emptyList<ConceptSearchResult>()
            restApi.searchConceptsByClass(query, "fuzzy", DIAGNOSIS_CONCEPT_CLASS_UUID, "custom:(uuid,display)")
                .execute().run {
                    if (isSuccessful && body() != null) body()!!.results
                    else throw Exception("Error searching diagnoses: ${message()}")
                }
        })
    }

    /**
     * Creates a patient diagnosis linked to an already-created encounter.
     *
     * Built as a raw JSON body (not a Gson model) because the server requires the "condition"
     * key to be present even when null, but the app's shared Gson instance omits null fields.
     *
     * @param rank 1 for the primary diagnosis, 2+ for secondary diagnoses, matching add order.
     */
    fun createDiagnosis(
        encounterUuid: String,
        patientUuid: String,
        conceptUuid: String,
        certainty: String,
        rank: Int
    ): Observable<PatientDiagnosisResponse> {
        return AppDatabaseHelper.createObservableIO(Callable {
            val json = JSONObject().apply {
                put("encounter", encounterUuid)
                put("patient", patientUuid)
                put("condition", JSONObject.NULL)
                put("diagnosis", JSONObject().apply { put("coded", conceptUuid) })
                put("certainty", certainty)
                put("rank", rank)
            }
            val body = RequestBody.create(MediaType.parse("application/json; charset=utf-8"), json.toString())
            restApi.createPatientDiagnosis(body).execute().run {
                if (isSuccessful && body() != null) body()!!
                else throw Exception("Error creating diagnosis: ${message()}")
            }
        })
    }

    companion object {
        const val DIAGNOSIS_CONCEPT_CLASS_UUID = "8d4918b0-c2cc-11de-8d13-0010c6dffd0f"
        const val CERTAINTY_CONFIRMED = "CONFIRMED"
        const val CERTAINTY_PRESUMED = "PROVISIONAL"
    }
}
