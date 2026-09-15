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
package com.openmrs.android_sdk.library.models

import com.google.gson.annotations.Expose

// See ProgramGet: @Expose is required on every field for the shared (excludeFieldsWithoutExpose)
// Gson instance to populate it at all.
data class StateGet(
    @Expose var uuid: String,
    @Expose var retired: Boolean,
    @Expose var programConcept: ProgramConcept
)
