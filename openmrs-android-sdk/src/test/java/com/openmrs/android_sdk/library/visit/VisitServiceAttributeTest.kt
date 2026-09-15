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
import com.openmrs.android_sdk.library.databases.AppDatabaseHelper
import com.openmrs.android_sdk.library.models.Visit
import com.openmrs.android_sdk.library.models.VisitAttribute
import com.openmrs.android_sdk.utilities.ApplicationConstants.VisitAttributeTypes
import com.openmrs.android_sdk.utilities.VisitAttributeDeserializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The visit-attribute plumbing behind the start-visit form: how a visit's Service selection is
 * packed into one attribute, how attributes survive a round trip through the local database, and
 * how the two different shapes the server uses for them are read back.
 *
 * Robolectric, because the local representation is JSON and `org.json` is stubbed out in a plain
 * unit test.
 */
@RunWith(RobolectricTestRunner::class)
class VisitServiceAttributeTest {

    private val nutritionUuid = "e5dc1d72-6f5e-4f1a-8a2b-8c8b1b5a0001"
    private val srhUuid = "f73376c9-7bdf-44e5-ba97-ddf4db5bc9f9"

    @Test
    fun `serviceProgramUuids reads every service out of the single Service attribute`() {
        val visit = Visit().apply {
            attributes = listOf(VisitAttribute(VisitAttributeTypes.SERVICE_UUID, "$nutritionUuid,$srhUuid"))
        }

        assertEquals(listOf(nutritionUuid, srhUuid), visit.serviceProgramUuids())
    }

    @Test
    fun `serviceProgramUuids tolerates spacing around the separator`() {
        val visit = Visit().apply {
            attributes = listOf(VisitAttribute(VisitAttributeTypes.SERVICE_UUID, " $nutritionUuid , $srhUuid "))
        }

        assertEquals(listOf(nutritionUuid, srhUuid), visit.serviceProgramUuids())
    }

    @Test
    fun `serviceProgramUuids is empty for a visit that predates the Service field`() {
        val visit = Visit().apply {
            attributes = listOf(VisitAttribute(VisitAttributeTypes.PUNCTUALITY_UUID, "on-time-uuid"))
        }

        assertTrue(visit.serviceProgramUuids().isEmpty())
        assertTrue(Visit().serviceProgramUuids().isEmpty())
    }

    @Test
    fun `visit attributes survive a round trip through the local database column`() {
        val attributes = listOf(
            VisitAttribute(VisitAttributeTypes.SERVICE_UUID, "$nutritionUuid,$srhUuid"),
            VisitAttribute(VisitAttributeTypes.PUNCTUALITY_UUID, "on-time-uuid").apply {
                attributeTypeDisplay = "Punctuality"
                valueDisplay = "On time"
            }
        )

        val restored = AppDatabaseHelper.deserializeVisitAttributes(
            AppDatabaseHelper.serializeVisitAttributes(attributes)
        )

        assertEquals(2, restored!!.size)
        assertEquals(VisitAttributeTypes.SERVICE_UUID, restored[0].attributeType)
        assertEquals("$nutritionUuid,$srhUuid", restored[0].value)
        assertEquals("on-time-uuid", restored[1].value)
        assertEquals("Punctuality", restored[1].attributeTypeDisplay)
        assertEquals("On time", restored[1].valueDisplay)
    }

    @Test
    fun `no attributes is stored as null rather than an empty list`() {
        assertNull(AppDatabaseHelper.serializeVisitAttributes(null))
        assertNull(AppDatabaseHelper.serializeVisitAttributes(emptyList()))
        assertNull(AppDatabaseHelper.deserializeVisitAttributes(null))
        assertNull(AppDatabaseHelper.deserializeVisitAttributes(""))
    }

    @Test
    fun `an unreadable attributes column reads as no attributes instead of throwing`() {
        assertNull(AppDatabaseHelper.deserializeVisitAttributes("not json at all"))
    }

    @Test
    fun `an attribute is posted as an attribute type uuid and read back from a nested object`() {
        val gson = GsonBuilder()
            .excludeFieldsWithoutExposeAnnotation()
            .registerTypeHierarchyAdapter(VisitAttribute::class.java, VisitAttributeDeserializer())
            .create()

        // Written: the server takes the attribute type as a bare uuid.
        assertEquals(
            """{"attributeType":"${VisitAttributeTypes.SERVICE_UUID}","value":"$nutritionUuid"}""",
            gson.toJson(VisitAttribute(VisitAttributeTypes.SERVICE_UUID, nutritionUuid))
        )

        // Read: it comes back as a nested object, and a coded value as a nested concept.
        val response = """
            {
              "uuid": "attr-uuid",
              "attributeType": { "uuid": "${VisitAttributeTypes.PUNCTUALITY_UUID}", "display": "Punctuality" },
              "value": { "uuid": "on-time-uuid", "display": "On time" }
            }
        """.trimIndent()
        val parsed = gson.fromJson(response, VisitAttribute::class.java)

        assertEquals("attr-uuid", parsed.attributeUuid)
        assertEquals(VisitAttributeTypes.PUNCTUALITY_UUID, parsed.attributeType)
        assertEquals("Punctuality", parsed.attributeTypeDisplay)
        // The uuid, not the display text: that is what was submitted and what is matched against.
        assertEquals("on-time-uuid", parsed.value)
        assertEquals("On time", parsed.valueDisplay)
    }

    @Test
    fun `a free-text attribute value is read back as plain text`() {
        val gson = GsonBuilder()
            .excludeFieldsWithoutExposeAnnotation()
            .registerTypeHierarchyAdapter(VisitAttribute::class.java, VisitAttributeDeserializer())
            .create()

        val response = """
            {
              "attributeType": { "uuid": "${VisitAttributeTypes.SERVICE_UUID}", "display": "Service" },
              "value": "$nutritionUuid,$srhUuid"
            }
        """.trimIndent()
        val parsed = gson.fromJson(response, VisitAttribute::class.java)

        assertEquals("$nutritionUuid,$srhUuid", parsed.value)
    }
}
