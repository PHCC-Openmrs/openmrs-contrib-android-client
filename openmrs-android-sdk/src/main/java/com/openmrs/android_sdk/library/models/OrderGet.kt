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
 * Response shape of the standard OpenMRS core `order` resource - verified against a live O3
 * deployment's `GET order?...&v=custom:(uuid,display,orderNumber,accessionNumber,patient,concept,
 * action,careSetting,previousOrder,dateActivated,scheduledDate,dateStopped,autoExpireDate,
 * encounter:(uuid,display,visit),orderer:ref,orderReason,orderReasonNonCoded,orderType,urgency,
 * instructions,commentToFulfiller,fulfillerStatus)` request.
 *
 * Only holds the fields in that representation - it deliberately omits DrugOrder-only fields
 * (drug, dose, frequency, etc.) since a mixed order-type list wouldn't request those on plain
 * Test Orders. Fetch a single order with `v=full` for those when showing order details.
 */
class OrderGet : Serializable {

    @SerializedName("uuid")
    @Expose
    var uuid: String? = null

    @SerializedName("display")
    @Expose
    var display: String? = null

    @SerializedName("orderNumber")
    @Expose
    var orderNumber: String? = null

    @SerializedName("accessionNumber")
    @Expose
    var accessionNumber: String? = null

    @SerializedName("patient")
    @Expose
    var patient: OrderResource? = null

    @SerializedName("concept")
    @Expose
    var concept: OrderResource? = null

    @SerializedName("action")
    @Expose
    var action: String? = null

    @SerializedName("careSetting")
    @Expose
    var careSetting: OrderResource? = null

    @SerializedName("previousOrder")
    @Expose
    var previousOrder: OrderResource? = null

    @SerializedName("dateActivated")
    @Expose
    var dateActivated: String? = null

    @SerializedName("scheduledDate")
    @Expose
    var scheduledDate: String? = null

    @SerializedName("dateStopped")
    @Expose
    var dateStopped: String? = null

    @SerializedName("autoExpireDate")
    @Expose
    var autoExpireDate: String? = null

    @SerializedName("encounter")
    @Expose
    var encounter: OrderEncounterInfo? = null

    @SerializedName("orderer")
    @Expose
    var orderer: OrderResource? = null

    @SerializedName("orderReason")
    @Expose
    var orderReason: String? = null

    @SerializedName("orderReasonNonCoded")
    @Expose
    var orderReasonNonCoded: String? = null

    @SerializedName("orderType")
    @Expose
    var orderType: OrderResource? = null

    @SerializedName("urgency")
    @Expose
    var urgency: String? = null

    @SerializedName("instructions")
    @Expose
    var instructions: String? = null

    @SerializedName("commentToFulfiller")
    @Expose
    var commentToFulfiller: String? = null

    @SerializedName("fulfillerStatus")
    @Expose
    var fulfillerStatus: String? = null
}

class OrderEncounterInfo : Serializable {
    @SerializedName("uuid")
    @Expose
    var uuid: String? = null

    @SerializedName("display")
    @Expose
    var display: String? = null

    @SerializedName("visit")
    @Expose
    var visit: OrderResource? = null
}
