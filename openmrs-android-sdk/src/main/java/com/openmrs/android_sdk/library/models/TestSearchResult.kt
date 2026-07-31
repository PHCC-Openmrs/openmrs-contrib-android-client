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
 * Response shape of `GET concept?q=...&v=custom:(uuid,display,conceptClass:(uuid,display))`,
 * verified against a live OpenMRS server (lab order form's test search). The server doesn't
 * support filtering this search by concept class, so results are filtered client-side to the
 * "Test" concept class in [com.openmrs.android_sdk.library.api.repository.OrderRepository].
 */
class TestSearchResult : Serializable {
    @SerializedName("uuid")
    @Expose
    var uuid: String? = null

    @SerializedName("display")
    @Expose
    var display: String? = null

    @SerializedName("conceptClass")
    @Expose
    var conceptClass: ConceptRef? = null
}
