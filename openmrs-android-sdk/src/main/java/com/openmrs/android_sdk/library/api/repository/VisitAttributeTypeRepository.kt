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
import com.openmrs.android_sdk.library.databases.AppDatabaseHelper.createObservableIO
import com.openmrs.android_sdk.library.databases.entities.VisitAttributeTypeEntity
import com.openmrs.android_sdk.library.models.ConceptAnswer
import com.openmrs.android_sdk.library.models.VisitAttributeType
import com.openmrs.android_sdk.utilities.ApplicationConstants
import com.openmrs.android_sdk.utilities.NetworkUtils
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import rx.Observable
import java.util.concurrent.Callable
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Supplies the extra questions the start-visit form asks about a visit - Punctuality and any other
 * type listed in [ApplicationConstants.VisitAttributeTypes.FORM_ATTRIBUTE_TYPE_UUIDS] - together
 * with the allowed answers of the coded ones.
 *
 * Everything is cached locally on the way through, so the form asks exactly the same questions
 * offline as it does online. Reading is therefore network-first, local-fallback: a refresh keeps
 * the cache current, but a device that has been offline since login still gets the last known
 * definitions rather than a form with fields missing.
 */
@Singleton
class VisitAttributeTypeRepository @Inject constructor() : BaseRepository() {

    private val visitAttributeTypeRoomDAO by lazy {
        AppDatabase.getDatabase(OpenmrsAndroid.getInstance()!!.applicationContext).visitAttributeTypeRoomDAO()
    }

    /**
     * The attribute types the start-visit form asks about, in the order they are configured -
     * refreshed from the server when online, read from the cache otherwise.
     *
     * @return the configured attribute types that are known, coded answers included
     */
    fun getFormVisitAttributeTypes(): Observable<List<VisitAttributeType>> {
        return createObservableIO(Callable {
            if (NetworkUtils.isOnline()) {
                try {
                    fetchAndCacheVisitAttributeTypes()
                } catch (e: Exception) {
                    logger.e("Could not refresh the visit attribute types, using the cached ones: " + e.message)
                }
            }
            readConfiguredTypesFromCache()
        })
    }

    /**
     * Downloads every visit attribute type - and, for coded ones, the answers a user may pick -
     * and caches them for offline use. Called on its own at login to warm the cache, and again by
     * [getFormVisitAttributeTypes] whenever the form is opened online.
     *
     * @return the cached attribute types the form is configured to ask about
     */
    fun fetchAndCacheVisitAttributeTypes(): List<VisitAttributeType> {
        val response = restApi.getVisitAttributeTypes(ATTRIBUTE_TYPE_REPRESENTATION).execute()
        if (!response.isSuccessful || response.body() == null) {
            throw Exception("getVisitAttributeTypes error: " + response.message())
        }

        val entities = response.body()!!.results.map { type ->
            VisitAttributeTypeEntity().apply {
                uuid = type.uuid.orEmpty()
                display = type.display?.takeIf { it.isNotEmpty() } ?: type.name
                datatypeClassname = type.datatypeClassname
                datatypeConfig = type.datatypeConfig
                answers = serializeAnswers(fetchAnswers(type))
            }
        }.filter { it.uuid.isNotEmpty() }

        visitAttributeTypeRoomDAO.insertOrUpdateVisitAttributeTypes(entities)
        return readConfiguredTypesFromCache()
    }

    /**
     * The allowed values of a coded attribute type, read from the concept its datatype config
     * names. A type of any other datatype, or one whose concept cannot be read, simply has none -
     * the field still renders, just as a free-text/number/date input rather than a picker.
     */
    private fun fetchAnswers(type: VisitAttributeType): List<ConceptAnswer> {
        val conceptUuid = type.datatypeConfig
        if (type.datatypeClassname != ApplicationConstants.VisitAttributeDatatypes.CONCEPT ||
            conceptUuid.isNullOrEmpty()
        ) {
            return emptyList()
        }
        return try {
            val response = restApi.getConceptFromUUID(conceptUuid).execute()
            if (response.isSuccessful && response.body() != null) {
                response.body()!!.answers.mapNotNull { answer ->
                    val uuid = answer.uuid ?: return@mapNotNull null
                    ConceptAnswer(uuid, answer.display.orEmpty())
                }
            } else {
                logger.e("Error fetching the answers of visit attribute type ${type.uuid}: " + response.message())
                emptyList()
            }
        } catch (e: Exception) {
            logger.e("Error fetching the answers of visit attribute type ${type.uuid}: " + e.message)
            emptyList()
        }
    }

    /**
     * Reads the configured attribute types out of the cache, keeping the configured order and
     * silently skipping any that has never been cached - a form with one field missing is far
     * better than no form at all.
     */
    private fun readConfiguredTypesFromCache(): List<VisitAttributeType> {
        return ApplicationConstants.VisitAttributeTypes.FORM_ATTRIBUTE_TYPE_UUIDS.mapNotNull { uuid ->
            val entity = try {
                visitAttributeTypeRoomDAO.getVisitAttributeTypeByUuid(uuid)
            } catch (e: Exception) {
                null
            } ?: return@mapNotNull null

            VisitAttributeType().apply {
                this.uuid = entity.uuid
                this.display = entity.display
                this.name = entity.display
                this.datatypeClassname = entity.datatypeClassname
                this.datatypeConfig = entity.datatypeConfig
                this.answers = deserializeAnswers(entity.answers)
            }
        }
    }

    companion object {
        private const val ATTRIBUTE_TYPE_REPRESENTATION =
            "custom:(uuid,display,name,datatypeClassname,datatypeConfig)"
        private const val UUID_KEY = "uuid"
        private const val DISPLAY_KEY = "display"

        private fun serializeAnswers(answers: List<ConceptAnswer>): String? {
            if (answers.isEmpty()) return null
            val jsonArray = JSONArray()
            answers.forEach { answer ->
                jsonArray.put(JSONObject().apply {
                    put(UUID_KEY, answer.uuid)
                    put(DISPLAY_KEY, answer.display)
                })
            }
            return jsonArray.toString()
        }

        private fun deserializeAnswers(json: String?): List<ConceptAnswer> {
            if (json.isNullOrEmpty()) return emptyList()
            return try {
                val jsonArray = JSONArray(json)
                (0 until jsonArray.length()).mapNotNull { index ->
                    val jsonObject = jsonArray.optJSONObject(index) ?: return@mapNotNull null
                    val uuid = jsonObject.optString(UUID_KEY).takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                    ConceptAnswer(uuid, jsonObject.optString(DISPLAY_KEY))
                }
            } catch (e: JSONException) {
                emptyList()
            }
        }
    }
}
