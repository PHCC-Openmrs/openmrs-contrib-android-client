/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package com.openmrs.android_sdk.utilities

import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializer
import com.openmrs.android_sdk.library.models.Form
import com.openmrs.android_sdk.utilities.StringUtils.unescapeJavaString
import java.lang.reflect.Modifier

object FormUtils {

    // Some O3 form schemas write boolean-valued fields (e.g. "required") as quoted strings
    // ("true"/"false") instead of JSON booleans. Gson's default strict deserialization throws
    // on that type mismatch, crashing the form renderer before it can even display - accept
    // either representation instead.
    private val LENIENT_BOOLEAN_DESERIALIZER = JsonDeserializer { json, _, _ ->
        when {
            json.asJsonPrimitive.isBoolean -> json.asBoolean
            else -> json.asString.toBoolean()
        }
    }

    @JvmStatic
    fun getForm(valueReference: String?): Form {
        val unescapedValueReference = unescapeJavaString(valueReference!!)
        val builder = GsonBuilder()
        builder.excludeFieldsWithModifiers(Modifier.FINAL, Modifier.TRANSIENT, Modifier.STATIC)
        builder.excludeFieldsWithoutExposeAnnotation()
        builder.registerTypeAdapter(Boolean::class.javaPrimitiveType, LENIENT_BOOLEAN_DESERIALIZER)
        builder.registerTypeAdapter(Boolean::class.javaObjectType, LENIENT_BOOLEAN_DESERIALIZER)
        val gson = builder.create()
        return gson.fromJson(unescapedValueReference, Form::class.java)
    }
}
