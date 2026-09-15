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
import com.openmrs.android_sdk.library.models.VisitAttribute
import java.lang.reflect.Type

/**
 * Deserializes a visit attribute. The same field is shaped differently in each direction: the app
 * *posts* `attributeType` as a bare uuid string, but the server *returns* it as a nested object,
 * and a coded (concept-backed) attribute type - e.g. Punctuality - likewise returns its `value` as
 * a nested concept object rather than a string. Either would break the default Gson mapping onto
 * [VisitAttribute]'s string fields with "Expected a string but was BEGIN_OBJECT".
 *
 * Mirrors [PersonAttributeDeserializer], which handles exactly this quirk for person attributes.
 */
class VisitAttributeDeserializer : JsonDeserializer<VisitAttribute> {
    @Throws(JsonParseException::class)
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): VisitAttribute {
        val jsonObject = json.asJsonObject
        val attribute = VisitAttribute()

        if (jsonObject.has(UUID_KEY) && !jsonObject[UUID_KEY].isJsonNull) {
            attribute.attributeUuid = jsonObject[UUID_KEY].asString
        }

        when (val typeElement = jsonObject[ATTRIBUTE_TYPE_KEY]) {
            null -> Unit
            else -> when {
                typeElement.isJsonPrimitive -> attribute.attributeType = typeElement.asString
                typeElement.isJsonObject -> {
                    val typeJson = typeElement.asJsonObject
                    if (typeJson.has(UUID_KEY)) attribute.attributeType = typeJson[UUID_KEY]?.asString
                    if (typeJson.has(DISPLAY_KEY)) attribute.attributeTypeDisplay = typeJson[DISPLAY_KEY]?.asString
                }
                else -> Unit
            }
        }

        val valueElement = jsonObject[VALUE_KEY]
        when {
            valueElement == null || valueElement.isJsonNull -> Unit
            valueElement.isJsonPrimitive -> {
                attribute.value = valueElement.asString
                attribute.valueDisplay = attribute.value
            }
            valueElement.isJsonObject -> {
                // Prefer the concept's uuid over its display text: callers match a coded value
                // against the uuid (that is what was submitted), and keep the display purely to
                // show it back to the user.
                val valueObj = valueElement.asJsonObject
                if (valueObj.has(UUID_KEY)) attribute.value = valueObj[UUID_KEY]?.asString
                attribute.valueDisplay = when {
                    valueObj.has(DISPLAY_KEY) -> valueObj[DISPLAY_KEY]?.asString
                    else -> attribute.value
                }
            }
            else -> Unit
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
