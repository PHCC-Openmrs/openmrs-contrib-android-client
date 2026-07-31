/*
 * The contents of this file are subject to the OpenMRS Public License
 * Version 1.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://license.openmrs.org
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations
 * under the License.
 *
 * Copyright (C) OpenMRS, LLC.  All Rights Reserved.
 */
package org.openmrs.mobile.activities.testorderform

import androidx.lifecycle.SavedStateHandle
import com.openmrs.android_sdk.library.api.repository.TestOrderBasketStore
import com.openmrs.android_sdk.library.dao.PatientDAO
import com.openmrs.android_sdk.library.models.TestOrderBasketItem
import com.openmrs.android_sdk.library.models.TestSearchResult
import com.openmrs.android_sdk.utilities.ApplicationConstants.BundleKeys.PATIENT_ID_BUNDLE
import com.openmrs.android_sdk.utilities.ApplicationConstants.BundleKeys.TEST_ORDER_BASKET_ITEM_ID_BUNDLE
import com.openmrs.android_sdk.utilities.ApplicationConstants.BundleKeys.TEST_SEARCH_RESULT_BUNDLE
import dagger.hilt.android.lifecycle.HiltViewModel
import org.openmrs.mobile.activities.BaseViewModel
import javax.inject.Inject

/**
 * Backs the test order form, which mirrors O3's `TestOrderForm` workspace: it's reached either
 * from the test search screen's "Order form" action (a brand new, not-yet-basketed item) or by
 * tapping an item already sitting in the order basket (edits it in place). Saving always writes
 * the item into the [TestOrderBasketStore] - it never talks to the network directly. A test order
 * is complete as soon as it's saved since [urgency] always has a value.
 */
@HiltViewModel
class TestOrderFormViewModel @Inject constructor(
    patientDAO: PatientDAO,
    private val basketStore: TestOrderBasketStore,
    savedStateHandle: SavedStateHandle
) : BaseViewModel<Unit>() {

    private val patientId: Long = savedStateHandle.get(PATIENT_ID_BUNDLE)!!
    private val patient = patientDAO.findPatientByID(patientId)
    private val patientUuid: String = patient.uuid.orEmpty()

    private val existingItemId: Long? = savedStateHandle.get<Long>(TEST_ORDER_BASKET_ITEM_ID_BUNDLE)
        ?.takeIf { it != NO_BASKET_ITEM_ID }
    private val existingItem: TestOrderBasketItem? = existingItemId?.let { basketStore.getItem(patientUuid, it) }

    val selectedTest: TestSearchResult = existingItem?.concept
        ?: savedStateHandle.get(TEST_SEARCH_RESULT_BUNDLE)!!

    var urgency: String = existingItem?.urgency ?: "ROUTINE"
    var accessionNumber: String? = existingItem?.accessionNumber
    var instructions: String = existingItem?.instructions.orEmpty()

    init {
        setContent(Unit)
    }

    fun save() {
        val item = TestOrderBasketItem(
            id = existingItemId ?: basketStore.nextId(),
            concept = selectedTest,
            isOrderIncomplete = false,
            urgency = urgency,
            accessionNumber = accessionNumber,
            instructions = instructions
        )

        if (existingItemId != null) {
            basketStore.updateItem(patientUuid, existingItemId, item)
        } else {
            basketStore.addItem(patientUuid, item)
        }
    }

    companion object {
        const val NO_BASKET_ITEM_ID = -1L
    }
}
