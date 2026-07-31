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
package org.openmrs.mobile.activities.modifydrugorder

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import com.openmrs.android_sdk.library.api.repository.OrderRepository
import com.openmrs.android_sdk.library.api.repository.ProviderRepository
import com.openmrs.android_sdk.library.models.DrugOrderCreateRequest
import com.openmrs.android_sdk.library.models.DrugOrderDetails
import com.openmrs.android_sdk.library.models.OrderEntryConfig
import com.openmrs.android_sdk.library.models.OrderGet
import com.openmrs.android_sdk.library.models.ResultType
import com.openmrs.android_sdk.utilities.ApplicationConstants.BundleKeys.ORDER_BUNDLE
import dagger.hilt.android.lifecycle.HiltViewModel
import org.openmrs.mobile.activities.BaseViewModel
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import javax.inject.Inject

/**
 * Backs the "Modify drug order" screen: pre-fills dose/route/frequency/duration/quantity/refills/
 * as-needed/instructions from the live order's full details (the Medications list itself only
 * carries the trimmed [OrderGet] shape), then submits a `REVISE` order on the SAME encounter the
 * original order already lives in - mirrors O3's real `handleModifyClick`
 * (medications-details-table.component.tsx), which opens the drug order form pre-filled via
 * `buildMedicationOrder(medication, 'REVISE')` and submits against `medication.encounter.uuid`.
 */
@HiltViewModel
class ModifyDrugOrderViewModel @Inject constructor(
    private val orderRepository: OrderRepository,
    private val providerRepository: ProviderRepository,
    savedStateHandle: SavedStateHandle
) : BaseViewModel<Unit>() {

    val order: OrderGet = savedStateHandle.get(ORDER_BUNDLE)!!

    var orderEntryConfig: OrderEntryConfig = OrderEntryConfig()
        private set
    private lateinit var orderDetails: DrugOrderDetails

    var routeUuid: String? = null
    var doseUnitsUuid: String? = null
    var frequencyUuid: String? = null
    var durationUnitsUuid: String? = null
    var quantityUnitsUuid: String? = null

    var dose: Double? = null
    var asNeeded: Boolean = false
    var numRefills: Int = 0
    var quantity: Double? = null
    var duration: Int? = null
    var dosingInstructions: String = ""

    init {
        fetchFormData()
    }

    private fun fetchFormData() {
        setLoading()
        addSubscription(
            Observable.zip(
                orderRepository.getOrderEntryConfig(),
                orderRepository.getDrugOrderDetails(order.uuid.orEmpty())
            ) { config, details -> config to details }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { (config, details) ->
                        orderEntryConfig = config
                        orderDetails = details
                        prefillFrom(details)
                        setContent(Unit)
                    },
                    { setError(it) }
                )
        )
    }

    private fun prefillFrom(details: DrugOrderDetails) {
        routeUuid = details.route?.uuid
        doseUnitsUuid = details.doseUnits?.uuid
        frequencyUuid = details.frequency?.uuid
        durationUnitsUuid = details.durationUnits?.uuid
        quantityUnitsUuid = details.quantityUnits?.uuid
        dose = details.dose
        asNeeded = details.asNeeded
        numRefills = details.numRefills ?: 0
        quantity = details.quantity
        duration = details.duration
        dosingInstructions = details.dosingInstructions.orEmpty()
    }

    fun submit(): LiveData<ResultType> {
        val resultLiveData = MutableLiveData<ResultType>()
        addSubscription(
            providerRepository.getCurrentProvider()
                .flatMap { provider ->
                    orderRepository.createDrugOrder(
                        DrugOrderCreateRequest(
                            action = "REVISE",
                            previousOrder = order.uuid,
                            patient = orderDetails.patient?.uuid.orEmpty(),
                            careSetting = orderDetails.careSetting?.uuid.orEmpty(),
                            concept = orderDetails.concept?.uuid.orEmpty(),
                            drug = orderDetails.drug?.uuid.orEmpty(),
                            orderer = provider.uuid ?: "",
                            encounter = orderDetails.encounter?.uuid.orEmpty(),
                            dosingType = orderDetails.dosingType,
                            dose = dose,
                            doseUnits = doseUnitsUuid,
                            route = routeUuid,
                            frequency = frequencyUuid,
                            asNeeded = asNeeded,
                            numRefills = numRefills,
                            quantity = quantity,
                            quantityUnits = quantityUnitsUuid,
                            duration = duration,
                            durationUnits = durationUnitsUuid,
                            dosingInstructions = dosingInstructions
                        )
                    )
                }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { resultLiveData.value = ResultType.OrderActionSuccess },
                    { resultLiveData.value = ResultType.OrderActionError }
                )
        )
        return resultLiveData
    }
}
