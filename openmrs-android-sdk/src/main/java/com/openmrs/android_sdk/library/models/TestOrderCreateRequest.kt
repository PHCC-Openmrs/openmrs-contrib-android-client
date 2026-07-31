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
 * Request body for `POST order` to create, revise, or discontinue a test (lab) order. Verified
 * end-to-end against a live server for all three actions:
 * - NEW: this exact field set was accepted and returned a real order number.
 * - REVISE: same full field set plus `previousOrder`, per O3's `prepTestOrderPostData`.
 * - DISCONTINUE: real captured request omits `urgency`/`accessionNumber`/`instructions` entirely
 *   (not just null) - pass `urgency = null` for that action to match, since Gson omits null
 *   `@Expose` fields by default in this codebase.
 * The server infers `orderType` from `type` - it must not be sent.
 */
class TestOrderCreateRequest(
    @SerializedName("type")
    @Expose
    val type: String = "testorder",

    @SerializedName("action")
    @Expose
    val action: String = "NEW",

    @SerializedName("patient")
    @Expose
    val patient: String,

    @SerializedName("careSetting")
    @Expose
    val careSetting: String,

    @SerializedName("concept")
    @Expose
    val concept: String,

    @SerializedName("orderer")
    @Expose
    val orderer: String,

    @SerializedName("encounter")
    @Expose
    val encounter: String,

    @SerializedName("previousOrder")
    @Expose
    val previousOrder: String? = null,

    @SerializedName("urgency")
    @Expose
    val urgency: String? = "ROUTINE",

    @SerializedName("accessionNumber")
    @Expose
    val accessionNumber: String? = null,

    @SerializedName("instructions")
    @Expose
    val instructions: String? = null
) : Serializable
