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
package org.openmrs.mobile.activities.addtestresult

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import com.openmrs.android_sdk.library.api.repository.OrderRepository
import com.openmrs.android_sdk.library.models.AddObsRequest
import com.openmrs.android_sdk.library.models.ConceptDetails
import com.openmrs.android_sdk.library.models.ConceptRef
import com.openmrs.android_sdk.library.models.ObsCreate
import com.openmrs.android_sdk.library.models.OrderGet
import com.openmrs.android_sdk.library.models.ResultType
import com.openmrs.android_sdk.utilities.ApplicationConstants.BundleKeys.ORDER_BUNDLE
import dagger.hilt.android.lifecycle.HiltViewModel
import org.openmrs.mobile.activities.BaseViewModel
import rx.android.schedulers.AndroidSchedulers
import javax.inject.Inject

/**
 * Backs the "Add result" screen for a lab order: fetches the test concept's datatype to decide
 * whether to render a numeric field, a coded-answer spinner, or a plain text fallback, then on
 * submit adds the value as an obs into the order's own existing encounter and marks the order's
 * fulfiller status COMPLETED - a real captured 3-step flow, verified against a live server.
 */
@HiltViewModel
class AddTestResultViewModel @Inject constructor(
    private val orderRepository: OrderRepository,
    savedStateHandle: SavedStateHandle
) : BaseViewModel<ConceptDetails>() {

    val order: OrderGet = savedStateHandle.get(ORDER_BUNDLE)!!

    var numericValue: Double? = null
    var textValue: String = ""
    var selectedAnswerUuid: String? = null

    var conceptDetails: ConceptDetails? = null
        private set

    init {
        fetchConceptDetails()
    }

    private fun fetchConceptDetails() {
        setLoading()
        addSubscription(
            orderRepository.getConceptDetails(order.concept?.uuid.orEmpty())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { details ->
                        conceptDetails = details
                        setContent(details)
                    },
                    { setError(it) }
                )
        )
    }

    fun submit(): LiveData<ResultType> {
        val resultLiveData = MutableLiveData<ResultType>()
        val value: Any? = when (conceptDetails?.datatype?.display) {
            ConceptDetails.DATATYPE_CODED -> selectedAnswerUuid
            ConceptDetails.DATATYPE_NUMERIC -> numericValue
            else -> textValue.takeIf { it.isNotBlank() }
        }
        if (value == null) {
            resultLiveData.value = ResultType.OrderActionError
            return resultLiveData
        }

        val request = AddObsRequest(
            listOf(
                ObsCreate(
                    concept = ConceptRef().apply { uuid = order.concept?.uuid },
                    order = ConceptRef().apply { uuid = order.uuid },
                    value = value
                )
            )
        )

        addSubscription(
            orderRepository.addTestResult(order.encounter?.uuid.orEmpty(), request)
                .flatMap { orderRepository.markOrderFulfilled(order.uuid.orEmpty(), "Test Results Entered") }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { resultLiveData.value = ResultType.OrderActionSuccess },
                    { resultLiveData.value = ResultType.OrderActionError }
                )
        )
        return resultLiveData
    }
}
