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

import com.openmrs.android_sdk.library.api.repository.ProgramEnrollmentRepository.Companion.normalizeEnrollmentDates
import com.openmrs.android_sdk.library.api.repository.ProgramRepository.Companion.filterProgramsByLocation
import com.openmrs.android_sdk.library.databases.entities.ProgramEntity
import com.openmrs.android_sdk.utilities.ApplicationConstants.ProgramLocationRestrictions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two rules the start-visit form's Service field and its enrollment episodes are built on:
 * which services a location offers, and how an episode's dates are reconciled before being sent.
 */
class VisitServiceEnrollmentTest {

    private val deirAlBalahUuid = "ba34b45c-0a0d-4000-9624-ab6fd419f778"
    private val otherLocationUuid = "11111111-2222-3333-4444-555555555555"

    /** Restricted to Deir Al-Balah PHCC by the shared configuration. */
    private val srh = program("f73376c9-7bdf-44e5-ba97-ddf4db5bc9f9", "Sexual Reproductive Health (SRH)")

    /** Deliberately absent from the restrictions, i.e. offered everywhere. */
    private val nutrition = program("e5dc1d72-6f5e-4f1a-8a2b-8c8b1b5a0001", "Nutrition Registration")

    private fun program(uuid: String, name: String) = ProgramEntity().apply {
        this.uuid = uuid
        this.name = name
    }

    @Test
    fun `a restricted service is offered only at the locations it is restricted to`() {
        val offered = filterProgramsByLocation(listOf(srh, nutrition), deirAlBalahUuid)

        assertEquals(listOf(srh.uuid, nutrition.uuid), offered.map { it.uuid })
    }

    @Test
    fun `a restricted service is not offered elsewhere, an unrestricted one still is`() {
        val offered = filterProgramsByLocation(listOf(srh, nutrition), otherLocationUuid)

        assertEquals(listOf(nutrition.uuid), offered.map { it.uuid })
    }

    @Test
    fun `with no location chosen only the unrestricted services are offered`() {
        val offered = filterProgramsByLocation(listOf(srh, nutrition), null)

        assertEquals(listOf(nutrition.uuid), offered.map { it.uuid })
    }

    @Test
    fun `the restrictions are the ones the web client is configured with`() {
        // The whole point of the field is that a service is offered on the same terms in both
        // clients, so these uuids are shared configuration, not incidental constants.
        assertTrue(ProgramLocationRestrictions.RESTRICTIONS.containsKey(srh.uuid))
        assertEquals(listOf(deirAlBalahUuid), ProgramLocationRestrictions.RESTRICTIONS[srh.uuid])
        assertNull(ProgramLocationRestrictions.RESTRICTIONS[nutrition.uuid])
    }

    @Test
    fun `an open episode is sent with no completion date`() {
        val (enrolled, completed) = normalizeEnrollmentDates("2026-09-15T09:00:00.000+0530", null)

        assertEquals("2026-09-15T09:00:00.000+0530", enrolled)
        assertNull(completed)
    }

    @Test
    fun `a visit that ends after it started completes at its own stop time`() {
        val (enrolled, completed) = normalizeEnrollmentDates(
            "2026-09-15T09:00:00.000+0530",
            "2026-09-15T11:30:00.000+0530"
        )

        assertEquals("2026-09-15T09:00:00.000+0530", enrolled)
        assertEquals("2026-09-15T11:30:00.000+0530", completed)
    }

    @Test
    fun `a same-day stop time behind the start time snaps up instead of being rejected`() {
        // The server refuses a completion earlier than its enrolment; a short visit whose stop
        // time rounds behind its start time must still save.
        val (_, completed) = normalizeEnrollmentDates(
            "2026-09-15T09:00:00.000+0530",
            "2026-09-15T00:00:00.000+0530"
        )

        assertEquals("2026-09-15T09:00:00.000+0530", completed)
    }

    @Test
    fun `an earlier day is left alone rather than snapped`() {
        // Genuinely inconsistent dates are the server's to reject - quietly rewriting them would
        // hide a real data problem.
        val (_, completed) = normalizeEnrollmentDates(
            "2026-09-15T09:00:00.000+0530",
            "2026-09-14T09:00:00.000+0530"
        )

        assertEquals("2026-09-14T09:00:00.000+0530", completed)
    }

    @Test
    fun `unparseable dates are passed through untouched`() {
        val (enrolled, completed) = normalizeEnrollmentDates("not a date", "also not a date")

        assertEquals("not a date", enrolled)
        assertEquals("also not a date", completed)
    }
}
