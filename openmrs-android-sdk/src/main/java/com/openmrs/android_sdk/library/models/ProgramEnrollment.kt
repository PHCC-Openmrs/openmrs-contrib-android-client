/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package com.openmrs.android_sdk.library.models

import com.google.gson.annotations.Expose
import com.openmrs.android_sdk.library.databases.entities.LocationEntity

/**
 * One episode of a patient's enrolment in a program (a "service", in this deployment's wording).
 *
 * Starting a visit for a service opens an episode, and ending that visit completes it - a visit is
 * its own episode for reporting, so a new one is opened even when the patient already has an
 * active episode in the same program. Mirrors the web client's start-visit form and its
 * `visit-ended` listener.
 *
 * <p> More on program enrollments https://rest.openmrs.org/#program-enrollment </p>
 */
class ProgramEnrollment : Resource() {

    @Expose
    var dateEnrolled: String? = null

    @Expose
    var dateCompleted: String? = null

    @Expose
    var location: LocationEntity? = null

    @Expose
    var program: Resource? = null

    @Expose
    var patient: Resource? = null

    fun isActive() = dateCompleted.isNullOrEmpty()
}

/**
 * Body of `POST /programenrollment`, opening an episode.
 *
 * `states` is sent as an empty list rather than omitted, matching the web client's
 * `createProgramEnrollment`.
 */
data class ProgramEnrollmentCreate(
    @Expose val patient: String,
    @Expose val program: String,
    @Expose val dateEnrolled: String,
    @Expose val dateCompleted: String? = null,
    @Expose val location: String? = null,
    @Expose val states: List<String> = emptyList()
)

/**
 * Body of `POST /programenrollment/{uuid}`, completing (or otherwise editing) an episode.
 *
 * `dateEnrolled` is resent unchanged because the server validates the pair together and rejects a
 * `dateCompleted` earlier than the enrolment it is applied to.
 */
data class ProgramEnrollmentUpdate(
    @Expose val dateEnrolled: String,
    @Expose val dateCompleted: String?,
    @Expose val location: String? = null,
    @Expose val states: List<String> = emptyList()
)
