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
package com.openmrs.android_sdk.utilities

import com.openmrs.android_sdk.library.models.Observation
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import com.openmrs.android_sdk.library.models.ConceptClass
import com.openmrs.android_sdk.library.models.Person
import java.lang.reflect.Type

class ObservationDeserializer : JsonDeserializer<Observation> {
    @Throws(JsonParseException::class)
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): Observation {
        val jsonObject = json.asJsonObject
        val observation = Observation()
        observation.uuid = jsonObject[UUID_KEY].asString
        observation.display = jsonObject[DISPLAY_KEY].asString

        if (jsonObject.has(VALUE_KEY)) {
            val valueElement = jsonObject[VALUE_KEY]
            if (valueElement.isJsonPrimitive) {
                val valueStr = valueElement.asString
                if (!valueStr.equals("null", ignoreCase = true)) {
                    observation.value = valueStr
                }
            } else if (valueElement.isJsonObject) {
                // Handle coded concepts as values
                val valueObj = valueElement.asJsonObject
                if (valueObj.has(DISPLAY_KEY)) {
                    val valueStr = valueObj[DISPLAY_KEY]?.asString
                    if (valueStr != null && !valueStr.equals("null", ignoreCase = true)) {
                        observation.value = valueStr
                    }
                } else if (valueObj.has(UUID_KEY)) {
                     observation.value = valueObj[UUID_KEY].asString
                }
            }
        }

        if (jsonObject.has("obsDatetime")) {
            observation.obsDatetime = jsonObject["obsDatetime"].asString
        }

        if (jsonObject.has("person")) {
            val personJson = jsonObject["person"].asJsonObject
            val person = Person()
            person.uuid = personJson[UUID_KEY].asString
            observation.person = person
        }

        if (jsonObject.has("status")) {
            observation.status = jsonObject["status"].asString
        }

        if (jsonObject.has("interpretation")) {
            val interpretationElement = jsonObject["interpretation"]
            if (interpretationElement.isJsonPrimitive) {
                observation.interpretation = interpretationElement.asString
            } else if (interpretationElement.isJsonObject) {
                observation.interpretation = interpretationElement.asJsonObject[DISPLAY_KEY]?.asString
            }
        }

        val conceptJson = jsonObject["concept"]
        if (conceptJson != null && "Visit Diagnoses" == conceptJson.asJsonObject[DISPLAY_KEY].asString) {
            val diagnosisDetailJSONArray = jsonObject["groupMembers"].asJsonArray
            for (i in 0 until diagnosisDetailJSONArray.size()) {
                val diagnosisDetails = diagnosisDetailJSONArray[i].asJsonObject
                val diagnosisDetail = diagnosisDetails["concept"].asJsonObject[DISPLAY_KEY].asString
                if ("Diagnosis order" == diagnosisDetail) {
                    observation.diagnosisOrder = diagnosisDetails.asJsonObject[VALUE_KEY].asJsonObject[DISPLAY_KEY].asString
                } else if ("Diagnosis certainty" == diagnosisDetail) {
                    observation.setDiagnosisCertanity(
                            diagnosisDetails.asJsonObject[VALUE_KEY].asJsonObject[DISPLAY_KEY].asString)
                } else {
                    try {
                        observation.diagnosisList = diagnosisDetails.asJsonObject[VALUE_KEY].asJsonObject[DISPLAY_KEY].asString
                    } catch (e: IllegalStateException) {
                        observation.diagnosisList = diagnosisDetails.asJsonObject[VALUE_KEY].asString
                    }
                }
            }
        } else if (conceptJson != null && "Text of encounter note" == conceptJson.asJsonObject[DISPLAY_KEY].asString) {
            observation.diagnosisNote = jsonObject.asJsonObject[VALUE_KEY].asString
        }
        if (conceptJson != null) {
            val concept = ConceptClass()
            concept.uuid = conceptJson.asJsonObject[UUID_KEY].asString
            observation.concept = concept
        }
        return observation
    }

    companion object {
        private const val UUID_KEY = "uuid"
        private const val DISPLAY_KEY = "display"
        private const val VALUE_KEY = "value"
    }
}