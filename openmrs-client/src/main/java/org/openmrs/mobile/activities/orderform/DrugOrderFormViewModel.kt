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
package org.openmrs.mobile.activities.orderform

import androidx.lifecycle.SavedStateHandle
import com.openmrs.android_sdk.library.api.repository.DrugOrderBasketStore
import com.openmrs.android_sdk.library.api.repository.OrderRepository
import com.openmrs.android_sdk.library.dao.PatientDAO
import com.openmrs.android_sdk.library.models.ConceptRef
import com.openmrs.android_sdk.library.models.DrugOrderBasketItem
import com.openmrs.android_sdk.library.models.DrugSearchResult
import com.openmrs.android_sdk.library.models.OrderEntryConfig
import com.openmrs.android_sdk.utilities.ApplicationConstants.BundleKeys.DRUG_ORDER_BASKET_ITEM_ID_BUNDLE
import com.openmrs.android_sdk.utilities.ApplicationConstants.BundleKeys.DRUG_SEARCH_RESULT_BUNDLE
import com.openmrs.android_sdk.utilities.ApplicationConstants.BundleKeys.PATIENT_ID_BUNDLE
import dagger.hilt.android.lifecycle.HiltViewModel
import org.openmrs.mobile.activities.BaseViewModel
import rx.android.schedulers.AndroidSchedulers
import javax.inject.Inject

/**
 * Backs the drug order form, which mirrors O3's `DrugOrderForm` workspace: it's reached either
 * from the drug search screen's "Order form" action (a brand new, not-yet-basketed item, built
 * from a [DrugSearchResult]) or by tapping an item already sitting in the order basket (edits it
 * in place). Saving always writes the item into the [DrugOrderBasketStore] - it never talks to
 * the network directly. Whether the saved item counts as complete or still needs attention is
 * decided by [buildBasketItem] and shown back on the order basket screen as an "Incomplete" chip.
 */
@HiltViewModel
class DrugOrderFormViewModel @Inject constructor(
    private val patientDAO: PatientDAO,
    private val orderRepository: OrderRepository,
    private val basketStore: DrugOrderBasketStore,
    savedStateHandle: SavedStateHandle
) : BaseViewModel<Unit>() {

    private val patientId: Long = savedStateHandle.get(PATIENT_ID_BUNDLE)!!
    private val patient = patientDAO.findPatientByID(patientId)
    private val patientUuid: String = patient.uuid.orEmpty()

    private val existingItemId: Long? = savedStateHandle.get<Long>(DRUG_ORDER_BASKET_ITEM_ID_BUNDLE)
        ?.takeIf { it != NO_BASKET_ITEM_ID }
    private val existingItem: DrugOrderBasketItem? = existingItemId?.let { basketStore.getItem(patientUuid, it) }

    var orderEntryConfig: OrderEntryConfig = OrderEntryConfig()
        private set

    val selectedDrug: DrugSearchResult = existingItem?.drug
        ?: savedStateHandle.get(DRUG_SEARCH_RESULT_BUNDLE)!!

    var routeUuid: String? = existingItem?.routeUuid
    var doseUnitsUuid: String? = existingItem?.doseUnitsUuid
    var frequencyUuid: String? = existingItem?.frequencyUuid
    var durationUnitsUuid: String? = existingItem?.durationUnitsUuid
    var quantityUnitsUuid: String? = existingItem?.quantityUnitsUuid

    var dose: Double? = existingItem?.dose
    var asNeeded: Boolean = existingItem?.asNeeded ?: false
    var numRefills: Int = existingItem?.numRefills ?: 0
    var quantity: Double? = existingItem?.quantity
    var duration: Int? = existingItem?.duration
    var dosingInstructions: String = existingItem?.dosingInstructions.orEmpty()

    init {
        fetchOrderEntryConfig()
    }

    private fun fetchOrderEntryConfig() {
        setLoading()
        addSubscription(
            orderRepository.getOrderEntryConfig()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { config ->
                        orderEntryConfig = config
                        setContent(Unit)
                    },
                    { setError(it) }
                )
        )
    }

    /**
     * Always saves - there's no invalid state that blocks writing to the basket. Whether the
     * saved item is complete only affects whether "Sign and close" is enabled back on the basket
     * screen, matching O3 (a half-filled order just sits there flagged "Incomplete" until edited
     * again). Quantity is required here (unlike O3's default config) because this server enforces
     * DrugOrder.error.quantityIsNullForOutPatient for the Outpatient care setting.
     */
    fun save() {
        val isIncomplete = dose == null || (dose ?: 0.0) <= 0.0 ||
            doseUnitsUuid == null || routeUuid == null || frequencyUuid == null ||
            quantity == null || (quantity ?: 0.0) <= 0.0 || quantityUnitsUuid == null

        val item = DrugOrderBasketItem(
            id = existingItemId ?: basketStore.nextId(),
            drug = selectedDrug,
            isOrderIncomplete = isIncomplete,
            dose = dose,
            doseUnitsUuid = doseUnitsUuid,
            doseUnitsDisplay = displayFor(orderEntryConfig.drugDosingUnits, doseUnitsUuid),
            routeUuid = routeUuid,
            routeDisplay = displayFor(orderEntryConfig.drugRoutes, routeUuid),
            frequencyUuid = frequencyUuid,
            frequencyDisplay = displayFor(orderEntryConfig.orderFrequencies, frequencyUuid),
            asNeeded = asNeeded,
            numRefills = numRefills,
            quantity = quantity,
            quantityUnitsUuid = quantityUnitsUuid,
            quantityUnitsDisplay = displayFor(orderEntryConfig.drugDispensingUnits, quantityUnitsUuid),
            duration = duration,
            durationUnitsUuid = durationUnitsUuid,
            durationUnitsDisplay = displayFor(orderEntryConfig.durationUnits, durationUnitsUuid),
            dosingInstructions = dosingInstructions
        )

        if (existingItemId != null) {
            basketStore.updateItem(patientUuid, existingItemId, item)
        } else {
            basketStore.addItem(patientUuid, item)
        }
    }

    private fun displayFor(options: List<ConceptRef>, uuid: String?): String? =
        options.firstOrNull { it.uuid == uuid }?.display

    companion object {
        const val NO_BASKET_ITEM_ID = -1L
    }
}
