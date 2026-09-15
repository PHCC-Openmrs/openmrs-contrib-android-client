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
package com.openmrs.android_sdk.library.visit

import com.google.gson.GsonBuilder
import com.openmrs.android_sdk.library.models.ProgramGet
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regression test for a bug that blocked the start-visit form's Service field on a real device:
 * the app's shared Gson (see `RestServiceBuilder.buildGsonConverter`) is built with
 * `excludeFieldsWithoutExposeAnnotation()`, so a model field with no `@Expose` is not skipped, it
 * is silently left at its default - null, for `ProgramGet`'s non-nullable fields - on every
 * response. `ProgramGet.allWorkflows` being null then blew up `ProgramEntity.setAllWorkflows`,
 * which has a non-null Kotlin parameter check, the moment `getAllProgramsAndSaveLocally()` ran
 * against a real server ("program?v=full" - confirmed via a live call to always return
 * `"allWorkflows": []`, an empty array, not a missing key).
 *
 * Exercises the same Gson configuration this test builds elsewhere for `VisitAttribute` (this
 * exclusion strategy applies to every model deserialized through `RestApi`, not just visits), so a
 * model added later without `@Expose` fails here instead of only on a live server.
 */
class ProgramGsonExposeTest {

    private val gson = GsonBuilder()
        .excludeFieldsWithoutExposeAnnotation()
        .create()

    @Test
    fun `a program is fully populated through the app's actual Gson configuration`() {
        // Exactly the shape confirmed live from openmrs-care-dev.beehyv.com's program endpoint.
        val json = """
            {
              "uuid": "f73376c9-7bdf-44e5-ba97-ddf4db5bc9f9",
              "name": "Sexual Reproductive Health (SRH)",
              "allWorkflows": []
            }
        """.trimIndent()

        val program = gson.fromJson(json, ProgramGet::class.java)

        assertEquals("f73376c9-7bdf-44e5-ba97-ddf4db5bc9f9", program.uuid)
        assertEquals("Sexual Reproductive Health (SRH)", program.name)
        assertEquals(emptyList<Any>(), program.allWorkflows)
    }
}
