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

/**
 * A drug order sitting in the in-memory order basket ([com.openmrs.android_sdk.library.api.repository.DrugOrderBasketStore]),
 * mirroring O3's `DrugOrderBasketItem` - not yet submitted to the server. [isOrderIncomplete] is
 * true until the required fields (dose, dose units, route, frequency) are filled in via the order
 * form, matching O3's structured-dosage validation.
 */
data class DrugOrderBasketItem(
    val id: Long,
    val drug: DrugSearchResult,
    val action: String = "NEW",
    val isOrderIncomplete: Boolean,
    val dose: Double? = null,
    val doseUnitsUuid: String? = null,
    val doseUnitsDisplay: String? = null,
    val routeUuid: String? = null,
    val routeDisplay: String? = null,
    val frequencyUuid: String? = null,
    val frequencyDisplay: String? = null,
    val asNeeded: Boolean = false,
    val numRefills: Int = 0,
    val quantity: Double? = null,
    val quantityUnitsUuid: String? = null,
    val quantityUnitsDisplay: String? = null,
    val duration: Int? = null,
    val durationUnitsUuid: String? = null,
    val durationUnitsDisplay: String? = null,
    val dosingInstructions: String = ""
)
