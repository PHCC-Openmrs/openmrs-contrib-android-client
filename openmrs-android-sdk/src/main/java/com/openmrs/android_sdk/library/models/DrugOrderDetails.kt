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
 * Full single-order representation for a drug order - fetched with `GET order/{uuid}`, since the
 * list-level [OrderGet] representation deliberately omits DrugOrder-only fields (drug, dose,
 * route, frequency, etc.) to keep the mixed-order-type Medications/Orders list queries light.
 * Needed to pre-fill Modify/Renew, and to discontinue a drug order (which needs the drug uuid).
 */
class DrugOrderDetails : Serializable {

    @SerializedName("uuid")
    @Expose
    var uuid: String? = null

    @SerializedName("action")
    @Expose
    var action: String? = null

    @SerializedName("patient")
    @Expose
    var patient: OrderResource? = null

    @SerializedName("careSetting")
    @Expose
    var careSetting: OrderResource? = null

    @SerializedName("concept")
    @Expose
    var concept: OrderResource? = null

    @SerializedName("encounter")
    @Expose
    var encounter: OrderEncounterInfo? = null

    @SerializedName("orderer")
    @Expose
    var orderer: OrderResource? = null

    @SerializedName("drug")
    @Expose
    var drug: DrugOrderDrugRef? = null

    @SerializedName("dose")
    @Expose
    var dose: Double? = null

    @SerializedName("doseUnits")
    @Expose
    var doseUnits: OrderResource? = null

    @SerializedName("route")
    @Expose
    var route: OrderResource? = null

    @SerializedName("frequency")
    @Expose
    var frequency: OrderResource? = null

    @SerializedName("asNeeded")
    @Expose
    var asNeeded: Boolean = false

    @SerializedName("numRefills")
    @Expose
    var numRefills: Int? = null

    @SerializedName("quantity")
    @Expose
    var quantity: Double? = null

    @SerializedName("quantityUnits")
    @Expose
    var quantityUnits: OrderResource? = null

    @SerializedName("duration")
    @Expose
    var duration: Int? = null

    @SerializedName("durationUnits")
    @Expose
    var durationUnits: OrderResource? = null

    @SerializedName("dosingInstructions")
    @Expose
    var dosingInstructions: String? = null

    @SerializedName("dosingType")
    @Expose
    var dosingType: String? = null
}

/** A drug reference with its own nested concept - `drug:(uuid,display,concept:ref)`. */
class DrugOrderDrugRef : Serializable {
    @SerializedName("uuid")
    @Expose
    var uuid: String? = null

    @SerializedName("display")
    @Expose
    var display: String? = null

    @SerializedName("concept")
    @Expose
    var concept: OrderResource? = null
}
