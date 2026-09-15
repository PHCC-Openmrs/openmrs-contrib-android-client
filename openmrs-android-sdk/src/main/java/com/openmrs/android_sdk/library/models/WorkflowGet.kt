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

// See ProgramGet: @Expose is required on every field for the shared (excludeFieldsWithoutExpose)
// Gson instance to populate it at all.
data class WorkflowGet(
    @Expose var uuid: String,
    @Expose var retired: Boolean,
    @Expose var programConcept: ProgramConcept,
    @Expose var states: List<StateGet>
)
