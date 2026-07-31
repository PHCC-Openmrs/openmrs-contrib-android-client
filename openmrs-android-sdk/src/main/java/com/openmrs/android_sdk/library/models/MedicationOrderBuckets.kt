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
 * A patient's drug orders split into O3's Medications widget buckets - see
 * `bucketMedicationOrders` in openmrs-esm-patient-chart's esm-patient-medications-app/src/api/api.ts.
 */
data class MedicationOrderBuckets(
    val activeOrders: List<OrderGet>,
    val upcomingOrders: List<OrderGet>,
    val pastOrders: List<OrderGet>
)
