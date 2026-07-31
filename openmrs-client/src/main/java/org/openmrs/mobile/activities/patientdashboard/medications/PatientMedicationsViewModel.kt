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
package org.openmrs.mobile.activities.patientdashboard.medications

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import com.openmrs.android_sdk.library.api.repository.OrderRepository
import com.openmrs.android_sdk.library.api.repository.OrderRepository.Companion.bucketMedicationOrders
import com.openmrs.android_sdk.library.api.repository.ProviderRepository
import com.openmrs.android_sdk.library.dao.PatientDAO
import com.openmrs.android_sdk.library.models.DrugOrderCreateRequest
import com.openmrs.android_sdk.library.models.MedicationOrderBuckets
import com.openmrs.android_sdk.library.models.OperationType.PatientOrdersFetching
import com.openmrs.android_sdk.library.models.OrderGet
import com.openmrs.android_sdk.library.models.ResultType
import com.openmrs.android_sdk.utilities.ApplicationConstants.BundleKeys.PATIENT_ID_BUNDLE
import dagger.hilt.android.lifecycle.HiltViewModel
import org.openmrs.mobile.activities.BaseViewModel
import org.openmrs.mobile.activities.patientdashboard.orders.PatientDashboardOrdersViewModel.Companion.CARE_SETTING_OUTPATIENT
import org.openmrs.mobile.activities.patientdashboard.orders.PatientDashboardOrdersViewModel.Companion.DRUG_ORDER_TYPE_UUID
import rx.android.schedulers.AndroidSchedulers
import javax.inject.Inject

/**
 * Backs the patient dashboard's Medications tab: every non-discontinued drug order, bucketed into
 * Active/Upcoming/Past exactly like O3's Medications widget (`useMedicationOrders` +
 * `bucketMedicationOrders` in esm-patient-medications-app/src/api/api.ts) - no "today" date
 * filter, since a drug order started weeks ago that hasn't been stopped is still Active.
 */
@HiltViewModel
class PatientMedicationsViewModel @Inject constructor(
    private val patientDAO: PatientDAO,
    private val orderRepository: OrderRepository,
    private val providerRepository: ProviderRepository,
    private val savedStateHandle: SavedStateHandle
) : BaseViewModel<MedicationOrderBuckets>() {

    private val patientId: Long = savedStateHandle.get(PATIENT_ID_BUNDLE)!!

    fun fetchOrders() {
        setLoading(PatientOrdersFetching)
        val patientUuid = patientDAO.findPatientByID(patientId)?.uuid
        if (patientUuid == null) {
            setContent(bucketMedicationOrders(emptyList()), PatientOrdersFetching)
            return
        }
        addSubscription(
            orderRepository.getMedicationOrdersAndSave(patientUuid, CARE_SETTING_OUTPATIENT, DRUG_ORDER_TYPE_UUID)
                .map { orders -> bucketMedicationOrders(orders) }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { buckets -> setContent(buckets, PatientOrdersFetching) },
                    { fetchCachedOrders(patientUuid) }
                )
        )
    }

    private fun fetchCachedOrders(patientUuid: String) {
        addSubscription(
            orderRepository.getCachedOrders(patientUuid)
                .map { orders -> orders.filter { it.orderType?.uuid == DRUG_ORDER_TYPE_UUID } }
                .map { orders -> bucketMedicationOrders(orders) }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { buckets -> setContent(buckets, PatientOrdersFetching) },
                    { setError(it, PatientOrdersFetching) }
                )
        )
    }

    /**
     * Renews a drug order: fetches the order's full dosing details, then submits a `RENEW` order
     * with those SAME values, reusing the original order's own encounter - mirrors O3's real
     * `handleRenewClick` (medications-details-table.component.tsx), which builds the renewed item
     * straight from the existing order with no edit step and submits against
     * `medication.encounter.uuid`.
     */
    fun renewOrder(order: OrderGet): LiveData<ResultType> = submitOrderAction(order, "RENEW")

    /**
     * Discontinues a drug order: real O3 (`prepMedicationOrderPostData`'s DISCONTINUE branch)
     * sends only type/action/previousOrder/patient/careSetting/encounter/orderer/concept/drug -
     * every dosing field is omitted entirely, not just null.
     */
    fun discontinueOrder(order: OrderGet): LiveData<ResultType> = submitOrderAction(order, "DISCONTINUE")

    private fun submitOrderAction(order: OrderGet, action: String): LiveData<ResultType> {
        val resultLiveData = MutableLiveData<ResultType>()
        val orderUuid = order.uuid
        if (orderUuid == null) {
            resultLiveData.value = ResultType.OrderActionError
            return resultLiveData
        }
        addSubscription(
            orderRepository.getDrugOrderDetails(orderUuid)
                .flatMap { details ->
                    providerRepository.getCurrentProvider().map { provider -> details to provider }
                }
                .flatMap { (details, provider) ->
                    val request = if (action == "DISCONTINUE") {
                        DrugOrderCreateRequest(
                            action = "DISCONTINUE",
                            previousOrder = orderUuid,
                            patient = details.patient?.uuid.orEmpty(),
                            careSetting = details.careSetting?.uuid.orEmpty(),
                            concept = details.concept?.uuid.orEmpty(),
                            drug = details.drug?.uuid.orEmpty(),
                            orderer = provider.uuid ?: "",
                            encounter = details.encounter?.uuid.orEmpty(),
                            urgency = null,
                            dosingType = null
                        )
                    } else {
                        DrugOrderCreateRequest(
                            action = action,
                            previousOrder = orderUuid,
                            patient = details.patient?.uuid.orEmpty(),
                            careSetting = details.careSetting?.uuid.orEmpty(),
                            concept = details.concept?.uuid.orEmpty(),
                            drug = details.drug?.uuid.orEmpty(),
                            orderer = provider.uuid ?: "",
                            encounter = details.encounter?.uuid.orEmpty(),
                            dosingType = details.dosingType,
                            dose = details.dose,
                            doseUnits = details.doseUnits?.uuid,
                            route = details.route?.uuid,
                            frequency = details.frequency?.uuid,
                            asNeeded = details.asNeeded,
                            numRefills = details.numRefills,
                            quantity = details.quantity,
                            quantityUnits = details.quantityUnits?.uuid,
                            duration = details.duration,
                            durationUnits = details.durationUnits?.uuid,
                            dosingInstructions = details.dosingInstructions
                        )
                    }
                    orderRepository.createDrugOrder(request)
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
