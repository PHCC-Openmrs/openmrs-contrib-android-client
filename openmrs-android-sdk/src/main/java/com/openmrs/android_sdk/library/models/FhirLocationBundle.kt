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
import com.google.gson.annotations.SerializedName

/**
 * Minimal model for a FHIR R4 "searchset" Bundle, used only for fetching Location resources
 * (e.g. GET .../ws/fhir2/R4/Location?_tag=Login+Location). Only the fields this app needs are
 * modeled - see https://www.hl7.org/fhir/bundle.html.
 */
data class FhirLocationBundle(
    @Expose @SerializedName("total") var total: Int? = null,
    @Expose @SerializedName("entry") var entry: List<FhirBundleEntry>? = null,
    @Expose @SerializedName("link") var link: List<FhirBundleLink>? = null
)

data class FhirBundleEntry(
    @Expose @SerializedName("resource") var resource: FhirLocationResource? = null
)

data class FhirBundleLink(
    @Expose @SerializedName("relation") var relation: String? = null,
    @Expose @SerializedName("url") var url: String? = null
)

data class FhirLocationResource(
    @Expose @SerializedName("id") var id: String? = null,
    @Expose @SerializedName("name") var name: String? = null
)
