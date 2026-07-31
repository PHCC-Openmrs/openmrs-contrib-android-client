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
import java.io.Serializable

/**
 * Response shape of `GET drug?q=...&v=custom:(uuid,display,name,strength,dosageForm:(display,uuid),
 * concept:(display,uuid))`, verified against a live O3 deployment (drug order form's autocomplete).
 */
class DrugSearchResult : Serializable {
    @SerializedName("uuid")
    @Expose
    var uuid: String? = null

    @SerializedName("display")
    @Expose
    var display: String? = null

    @SerializedName("name")
    @Expose
    var name: String? = null

    @SerializedName("strength")
    @Expose
    var strength: String? = null

    @SerializedName("dosageForm")
    @Expose
    var dosageForm: ConceptRef? = null

    @SerializedName("concept")
    @Expose
    var concept: ConceptRef? = null
}

/** A minimal {uuid, display} concept reference, as returned throughout the order-entry endpoints. */
class ConceptRef : Serializable {
    @SerializedName("uuid")
    @Expose
    var uuid: String? = null

    @SerializedName("display")
    @Expose
    var display: String? = null
}
