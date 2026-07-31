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
package org.openmrs.mobile.activities.orderbasket

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import com.openmrs.android_sdk.library.api.repository.DrugOrderBasketStore
import com.openmrs.android_sdk.library.api.repository.EncounterRepository
import com.openmrs.android_sdk.library.api.repository.OrderRepository
import com.openmrs.android_sdk.library.api.repository.ProviderRepository
import com.openmrs.android_sdk.library.api.repository.TestOrderBasketStore
import com.openmrs.android_sdk.library.dao.PatientDAO
import com.openmrs.android_sdk.library.models.DrugOrderBasketItem
import com.openmrs.android_sdk.library.models.DrugOrderCreateRequest
import com.openmrs.android_sdk.library.models.Encounter
import com.openmrs.android_sdk.library.models.EncounterProviderCreate
import com.openmrs.android_sdk.library.models.Encountercreate
import com.openmrs.android_sdk.library.models.Provider
import com.openmrs.android_sdk.library.models.ResultType
import com.openmrs.android_sdk.library.models.TestOrderBasketItem
import com.openmrs.android_sdk.library.models.TestOrderCreateRequest
import com.openmrs.android_sdk.utilities.ApplicationConstants.BundleKeys.PATIENT_ID_BUNDLE
import dagger.hilt.android.lifecycle.HiltViewModel
import org.openmrs.mobile.activities.BaseViewModel
import org.openmrs.mobile.activities.patientdashboard.orders.PatientDashboardOrdersViewModel.Companion.CARE_SETTING_OUTPATIENT
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import javax.inject.Inject

/**
 * Backs the order basket screen, mirroring O3's order basket workspace: shows the drug and lab
 * orders added so far (complete or still "Incomplete"), lets you remove one, and - once every
 * item across both is complete - "Sign and close" creates one shared encounter and submits every
 * item to the server.
 */
@HiltViewModel
class OrderBasketViewModel @Inject constructor(
    patientDAO: PatientDAO,
    private val orderRepository: OrderRepository,
    private val encounterRepository: EncounterRepository,
    private val providerRepository: ProviderRepository,
    private val drugBasketStore: DrugOrderBasketStore,
    private val labBasketStore: TestOrderBasketStore,
    savedStateHandle: SavedStateHandle
) : BaseViewModel<Unit>() {

    private val patientId: Long = savedStateHandle.get(PATIENT_ID_BUNDLE)!!
    private val patientUuid: String = patientDAO.findPatientByID(patientId).uuid.orEmpty()

    val drugOrders: LiveData<List<DrugOrderBasketItem>> = drugBasketStore.liveItems(patientUuid)
    val labOrders: LiveData<List<TestOrderBasketItem>> = labBasketStore.liveItems(patientUuid)

    fun removeDrugItem(id: Long) {
        drugBasketStore.removeItem(patientUuid, id)
    }

    fun removeLabItem(id: Long) {
        labBasketStore.removeItem(patientUuid, id)
    }

    fun signAndClose(): LiveData<ResultType> {
        val resultLiveData = MutableLiveData<ResultType>()
        val drugItems = drugOrders.value.orEmpty()
        val labItems = labOrders.value.orEmpty()

        addSubscription(
            Observable.zip(
                providerRepository.getCurrentProvider(),
                providerRepository.getEncounterRoles()
            ) { provider, roles -> provider to (roles.firstOrNull()?.uuid ?: "") }
                .flatMap { (provider, encounterRoleUuid) ->
                    val encounterCreate = Encountercreate().apply {
                        patient = patientUuid
                        this.patientId = this@OrderBasketViewModel.patientId
                        encounterType = VISIT_NOTE_ENCOUNTER_TYPE_UUID
                        encounterProvider = listOf(EncounterProviderCreate(provider.uuid ?: "", encounterRoleUuid))
                    }
                    encounterRepository.createEncounterOnline(encounterCreate).map { encounter -> provider to encounter }
                }
                .flatMap { (provider, encounter) ->
                    Observable.zip(
                        Observable.from(drugItems)
                            .flatMap { item -> orderRepository.createDrugOrder(buildDrugRequest(item, provider, encounter)) }
                            .toList(),
                        Observable.from(labItems)
                            .flatMap { item -> orderRepository.createTestOrder(buildTestRequest(item, provider, encounter)) }
                            .toList()
                    ) { _, _ -> Unit }
                }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    {
                        drugBasketStore.clear(patientUuid)
                        labBasketStore.clear(patientUuid)
                        resultLiveData.value = ResultType.DrugOrderCreateSuccess
                    },
                    { resultLiveData.value = ResultType.DrugOrderCreateError }
                )
        )

        return resultLiveData
    }

    private fun buildDrugRequest(item: DrugOrderBasketItem, provider: Provider, encounter: Encounter) = DrugOrderCreateRequest(
        patient = patientUuid,
        careSetting = CARE_SETTING_OUTPATIENT,
        concept = item.drug.concept?.uuid.orEmpty(),
        drug = item.drug.uuid.orEmpty(),
        orderer = provider.uuid ?: "",
        encounter = encounter.uuid ?: "",
        dose = item.dose ?: 0.0,
        doseUnits = item.doseUnitsUuid.orEmpty(),
        route = item.routeUuid,
        frequency = item.frequencyUuid.orEmpty(),
        asNeeded = item.asNeeded,
        numRefills = item.numRefills,
        quantity = item.quantity,
        quantityUnits = item.quantityUnitsUuid,
        duration = item.duration,
        durationUnits = item.durationUnitsUuid,
        dosingInstructions = item.dosingInstructions
    )

    private fun buildTestRequest(item: TestOrderBasketItem, provider: Provider, encounter: Encounter) = TestOrderCreateRequest(
        patient = patientUuid,
        careSetting = CARE_SETTING_OUTPATIENT,
        concept = item.concept.uuid.orEmpty(),
        orderer = provider.uuid ?: "",
        encounter = encounter.uuid ?: "",
        urgency = item.urgency,
        accessionNumber = item.accessionNumber,
        instructions = item.instructions
    )

    companion object {
        private const val VISIT_NOTE_ENCOUNTER_TYPE_UUID = "d7151f82-c1f3-4152-a605-2f9ea7414a79"
    }
}
