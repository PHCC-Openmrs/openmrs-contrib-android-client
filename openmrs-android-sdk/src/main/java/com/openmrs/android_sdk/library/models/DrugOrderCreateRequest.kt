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
 * Request body for `POST order` to create, renew, revise, or discontinue a drug order.
 *
 * The NEW-action field mapping is NOT verified against a captured save request from a live
 * server - only read/config endpoints were captured. It follows the standard, documented OpenMRS
 * core DrugOrder REST contract (rest.openmrs.org). The RENEW/REVISE/DISCONTINUE shapes mirror O3's
 * real `prepMedicationOrderPostData` (openmrs-esm-patient-chart's esm-patient-medications-app/src/
 * api/api.ts): RENEW and REVISE send the same full dosing field set as NEW plus `previousOrder`;
 * DISCONTINUE omits every dosing field entirely (not just null), same as this codebase's already
 * live-verified [TestOrderCreateRequest] DISCONTINUE shape - pass the dosing fields as null for
 * that action, since Gson omits null `@Expose` fields by default in this codebase.
 * Test end-to-end and capture the real "save order" network request if anything is rejected.
 */
class DrugOrderCreateRequest(
    @SerializedName("type")
    @Expose
    val type: String = "drugorder",

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

    @SerializedName("drug")
    @Expose
    val drug: String,

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

    @SerializedName("dosingType")
    @Expose
    val dosingType: String? = "org.openmrs.SimpleDosingInstructions",

    @SerializedName("dose")
    @Expose
    val dose: Double? = null,

    @SerializedName("doseUnits")
    @Expose
    val doseUnits: String? = null,

    @SerializedName("route")
    @Expose
    val route: String? = null,

    @SerializedName("frequency")
    @Expose
    val frequency: String? = null,

    @SerializedName("asNeeded")
    @Expose
    val asNeeded: Boolean? = null,

    @SerializedName("numRefills")
    @Expose
    val numRefills: Int? = null,

    @SerializedName("quantity")
    @Expose
    val quantity: Double? = null,

    @SerializedName("quantityUnits")
    @Expose
    val quantityUnits: String? = null,

    @SerializedName("duration")
    @Expose
    val duration: Int? = null,

    @SerializedName("durationUnits")
    @Expose
    val durationUnits: String? = null,

    @SerializedName("dosingInstructions")
    @Expose
    val dosingInstructions: String? = null
) : Serializable
