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
package org.openmrs.mobile.activities.drugsearch

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import com.openmrs.android_sdk.library.api.repository.DrugOrderBasketStore
import com.openmrs.android_sdk.library.api.repository.OrderRepository
import com.openmrs.android_sdk.library.dao.PatientDAO
import com.openmrs.android_sdk.library.models.DrugOrderBasketItem
import com.openmrs.android_sdk.library.models.DrugSearchResult
import com.openmrs.android_sdk.utilities.ApplicationConstants.BundleKeys.PATIENT_ID_BUNDLE
import dagger.hilt.android.lifecycle.HiltViewModel
import org.openmrs.mobile.activities.BaseViewModel
import rx.android.schedulers.AndroidSchedulers
import javax.inject.Inject

/**
 * Backs the drug search screen, mirroring O3's `drug-search`/`order-basket-search-results`
 * workspace: as-you-type drug search, with each result offering a quick "Add to basket" (marks
 * the item incomplete right away) and a full "Order form" (opens [org.openmrs.mobile.activities.orderform.DrugOrderFormActivity]
 * to fill it in before it's added).
 */
@HiltViewModel
class DrugSearchViewModel @Inject constructor(
    patientDAO: PatientDAO,
    private val orderRepository: OrderRepository,
    private val basketStore: DrugOrderBasketStore,
    savedStateHandle: SavedStateHandle
) : BaseViewModel<Unit>() {

    val patientId: Long = savedStateHandle.get(PATIENT_ID_BUNDLE)!!
    private val patientUuid: String = patientDAO.findPatientByID(patientId).uuid.orEmpty()

    fun searchDrugs(query: String): LiveData<List<DrugSearchResult>> {
        val resultsLiveData = MutableLiveData<List<DrugSearchResult>>()
        if (query.length < MIN_SEARCH_LENGTH) {
            resultsLiveData.value = emptyList()
            return resultsLiveData
        }
        addSubscription(
            orderRepository.searchDrugs(query)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { results -> resultsLiveData.value = results },
                    { resultsLiveData.value = emptyList() }
                )
        )
        return resultsLiveData
    }

    fun addToBasket(drug: DrugSearchResult) {
        basketStore.addItem(
            patientUuid,
            DrugOrderBasketItem(id = basketStore.nextId(), drug = drug, isOrderIncomplete = true)
        )
    }

    companion object {
        private const val MIN_SEARCH_LENGTH = 2
    }
}
