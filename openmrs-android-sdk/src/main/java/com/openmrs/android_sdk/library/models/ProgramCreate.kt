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

// See ProgramGet: @Expose is required on every field, here for the *outgoing* direction - without
// it, POST/PUT program requests serialize to "{}" against the shared (excludeFieldsWithoutExpose)
// Gson instance.
data class ProgramCreate(
    @Expose var name: String,
    @Expose var description: String,
    @Expose var retired: Boolean,
    @Expose var concept: String,
    @Expose var outcomesConcept: String?
)
