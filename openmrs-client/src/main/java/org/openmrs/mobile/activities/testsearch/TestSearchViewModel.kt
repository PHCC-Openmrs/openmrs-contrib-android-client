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
package org.openmrs.mobile.activities.testsearch

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import com.openmrs.android_sdk.library.api.repository.OrderRepository
import com.openmrs.android_sdk.library.api.repository.TestOrderBasketStore
import com.openmrs.android_sdk.library.dao.PatientDAO
import com.openmrs.android_sdk.library.models.TestOrderBasketItem
import com.openmrs.android_sdk.library.models.TestSearchResult
import com.openmrs.android_sdk.utilities.ApplicationConstants.BundleKeys.PATIENT_ID_BUNDLE
import dagger.hilt.android.lifecycle.HiltViewModel
import org.openmrs.mobile.activities.BaseViewModel
import rx.android.schedulers.AndroidSchedulers
import javax.inject.Inject

/**
 * Backs the test search screen, mirroring [org.openmrs.mobile.activities.drugsearch.DrugSearchViewModel]
 * for lab orders: as-you-type test search, with each result offering a quick "Add to basket" (a
 * test order is complete by default, unlike a drug order) and a full "Order form".
 */
@HiltViewModel
class TestSearchViewModel @Inject constructor(
    patientDAO: PatientDAO,
    private val orderRepository: OrderRepository,
    private val basketStore: TestOrderBasketStore,
    savedStateHandle: SavedStateHandle
) : BaseViewModel<Unit>() {

    val patientId: Long = savedStateHandle.get(PATIENT_ID_BUNDLE)!!
    private val patientUuid: String = patientDAO.findPatientByID(patientId).uuid.orEmpty()

    fun searchTests(query: String): LiveData<List<TestSearchResult>> {
        val resultsLiveData = MutableLiveData<List<TestSearchResult>>()
        if (query.length < MIN_SEARCH_LENGTH) {
            resultsLiveData.value = emptyList()
            return resultsLiveData
        }
        addSubscription(
            orderRepository.searchTests(query)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { results -> resultsLiveData.value = results },
                    { resultsLiveData.value = emptyList() }
                )
        )
        return resultsLiveData
    }

    fun addToBasket(test: TestSearchResult) {
        basketStore.addItem(
            patientUuid,
            TestOrderBasketItem(id = basketStore.nextId(), concept = test)
        )
    }

    companion object {
        private const val MIN_SEARCH_LENGTH = 2
    }
}
