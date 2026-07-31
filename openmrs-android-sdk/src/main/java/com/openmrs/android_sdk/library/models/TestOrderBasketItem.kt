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
 * A lab/test order sitting in the in-memory order basket
 * ([com.openmrs.android_sdk.library.api.repository.TestOrderBasketStore]), mirroring O3's
 * `TestOrderBasketItem` - not yet submitted to the server. Unlike a drug order, a test order is
 * complete as soon as it's added since [urgency] always has a default value.
 */
data class TestOrderBasketItem(
    val id: Long,
    val concept: TestSearchResult,
    val action: String = "NEW",
    val isOrderIncomplete: Boolean = false,
    val urgency: String = "ROUTINE",
    val accessionNumber: String? = null,
    val instructions: String = ""
)
