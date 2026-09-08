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

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import com.openmrs.android_sdk.library.models.PersonAttribute
import com.openmrs.android_sdk.library.models.PersonAttributeType
import java.lang.reflect.Type

/**
 * Deserializes a person attribute's "value". For most attribute types this is a plain string, but
 * a coded (concept-backed) attribute type - e.g. this app's Patient Status (Resident/IDP) - has
 * the server return it as a nested concept object instead (e.g. {"uuid":...,"display":"Resident"}),
 * which breaks the default Gson mapping against PersonAttribute.value: String and throws
 * "Expected a string but was BEGIN_OBJECT" while parsing any response that includes it. Mirrors
 * ObservationDeserializer's handling of the same coded-value quirk for observations.
 */
class PersonAttributeDeserializer : JsonDeserializer<PersonAttribute> {
    @Throws(JsonParseException::class)
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): PersonAttribute {
        val jsonObject = json.asJsonObject
        val attribute = PersonAttribute()

        val typeElement = jsonObject[ATTRIBUTE_TYPE_KEY]
        if (typeElement != null && typeElement.isJsonObject) {
            val typeJson = typeElement.asJsonObject
            attribute.attributeType = PersonAttributeType().apply {
                if (typeJson.has(UUID_KEY)) uuid = typeJson[UUID_KEY].asString
                if (typeJson.has(DISPLAY_KEY)) display = typeJson[DISPLAY_KEY]?.asString
            }
        }

        val valueElement = jsonObject[VALUE_KEY]
        attribute.value = when {
            valueElement == null || valueElement.isJsonNull -> null
            valueElement.isJsonPrimitive -> valueElement.asString
            valueElement.isJsonObject -> {
                // Prefer the concept's uuid over its display text: callers (e.g. Patient Status's
                // uuid-to-label mapping) match against the uuid, not the localized display string.
                val valueObj = valueElement.asJsonObject
                when {
                    valueObj.has(UUID_KEY) -> valueObj[UUID_KEY]?.asString
                    valueObj.has(DISPLAY_KEY) -> valueObj[DISPLAY_KEY]?.asString
                    else -> null
                }
            }
            else -> null
        }

        return attribute
    }

    companion object {
        private const val UUID_KEY = "uuid"
        private const val DISPLAY_KEY = "display"
        private const val VALUE_KEY = "value"
        private const val ATTRIBUTE_TYPE_KEY = "attributeType"
    }
}
