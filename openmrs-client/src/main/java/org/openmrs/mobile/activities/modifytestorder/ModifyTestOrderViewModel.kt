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
package org.openmrs.mobile.activities.modifytestorder

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import com.openmrs.android_sdk.library.api.repository.OrderRepository
import com.openmrs.android_sdk.library.api.repository.ProviderRepository
import com.openmrs.android_sdk.library.models.OrderGet
import com.openmrs.android_sdk.library.models.ResultType
import com.openmrs.android_sdk.library.models.TestOrderCreateRequest
import com.openmrs.android_sdk.utilities.ApplicationConstants.BundleKeys.ORDER_BUNDLE
import dagger.hilt.android.lifecycle.HiltViewModel
import org.openmrs.mobile.activities.BaseViewModel
import rx.android.schedulers.AndroidSchedulers
import javax.inject.Inject

/**
 * Backs the "Modify order" screen for a live lab order: pre-fills priority/reference
 * number/instructions from the existing [OrderGet], then submits a `REVISE` order on the SAME
 * encounter the original order already lives in - verified against a live server (O3's
 * `prepTestOrderPostData` sends the complete field set for REVISE, not a partial patch).
 */
@HiltViewModel
class ModifyTestOrderViewModel @Inject constructor(
    private val orderRepository: OrderRepository,
    private val providerRepository: ProviderRepository,
    savedStateHandle: SavedStateHandle
) : BaseViewModel<Unit>() {

    val order: OrderGet = savedStateHandle.get(ORDER_BUNDLE)!!

    var urgency: String = order.urgency ?: "ROUTINE"
    var accessionNumber: String? = order.accessionNumber
    var instructions: String = order.instructions.orEmpty()

    init {
        setContent(Unit)
    }

    fun submit(): LiveData<ResultType> {
        val resultLiveData = MutableLiveData<ResultType>()
        addSubscription(
            providerRepository.getCurrentProvider()
                .flatMap { provider ->
                    orderRepository.createTestOrder(
                        TestOrderCreateRequest(
                            action = "REVISE",
                            patient = order.patient?.uuid.orEmpty(),
                            careSetting = order.careSetting?.uuid.orEmpty(),
                            concept = order.concept?.uuid.orEmpty(),
                            orderer = provider.uuid ?: "",
                            encounter = order.encounter?.uuid.orEmpty(),
                            previousOrder = order.uuid,
                            urgency = urgency,
                            accessionNumber = accessionNumber,
                            instructions = instructions
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
