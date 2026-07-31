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
package org.openmrs.mobile.activities.patientdashboard.orders

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import com.openmrs.android_sdk.library.api.repository.OrderRepository
import com.openmrs.android_sdk.library.api.repository.ProviderRepository
import com.openmrs.android_sdk.library.dao.PatientDAO
import com.openmrs.android_sdk.library.models.OperationType.PatientOrdersFetching
import com.openmrs.android_sdk.library.models.OrderGet
import com.openmrs.android_sdk.library.models.ResultType
import com.openmrs.android_sdk.library.models.TestOrderCreateRequest
import com.openmrs.android_sdk.utilities.ApplicationConstants.BundleKeys.PATIENT_ID_BUNDLE
import dagger.hilt.android.lifecycle.HiltViewModel
import org.openmrs.mobile.activities.BaseViewModel
import rx.android.schedulers.AndroidSchedulers
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject

/**
 * Backs the patient dashboard's Orders tab: the current lab/test orders active today, mirroring
 * O3's Orders chart tab (GET order?...&activatedOnOrAfterDate=today&activatedOnOrBeforeDate=today,
 * excluding discontinued/cancelled/expired orders). Drug orders live on the separate Medications
 * tab instead - see [org.openmrs.mobile.activities.patientdashboard.medications.PatientMedicationsViewModel].
 */
@HiltViewModel
class PatientDashboardOrdersViewModel @Inject constructor(
    private val patientDAO: PatientDAO,
    private val orderRepository: OrderRepository,
    private val providerRepository: ProviderRepository,
    private val savedStateHandle: SavedStateHandle
) : BaseViewModel<List<OrderGet>>() {

    private val patientId: Long = savedStateHandle.get(PATIENT_ID_BUNDLE)!!

    fun fetchOrders() {
        setLoading(PatientOrdersFetching)
        val patientUuid = patientDAO.findPatientByID(patientId)?.uuid
        if (patientUuid == null) {
            setContent(emptyList(), PatientOrdersFetching)
            return
        }
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date())
        addSubscription(
            orderRepository.getOrdersForDateAndSave(patientUuid, CARE_SETTING_OUTPATIENT, today, TEST_ORDER_TYPE_UUID)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { orders -> setContent(orders, PatientOrdersFetching) },
                    { fetchCachedOrders(patientUuid) }
                )
        )
    }

    private fun fetchCachedOrders(patientUuid: String) {
        addSubscription(
            orderRepository.getCachedOrders(patientUuid)
                .map { orders -> orders.filter { it.orderType?.uuid == TEST_ORDER_TYPE_UUID } }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { orders -> setContent(orders, PatientOrdersFetching) },
                    { setError(it, PatientOrdersFetching) }
                )
        )
    }

    /**
     * Discontinues a live lab order - a real captured `DISCONTINUE` order revision, reusing the
     * original order's own encounter, with `urgency`/`accessionNumber`/`instructions` omitted
     * entirely (not just null) to match the real capture exactly.
     */
    fun cancelOrder(order: OrderGet): LiveData<ResultType> {
        val resultLiveData = MutableLiveData<ResultType>()
        addSubscription(
            providerRepository.getCurrentProvider()
                .flatMap { provider ->
                    orderRepository.createTestOrder(
                        TestOrderCreateRequest(
                            action = "DISCONTINUE",
                            patient = order.patient?.uuid.orEmpty(),
                            careSetting = order.careSetting?.uuid.orEmpty(),
                            concept = order.concept?.uuid.orEmpty(),
                            orderer = provider.uuid ?: "",
                            encounter = order.encounter?.uuid.orEmpty(),
                            previousOrder = order.uuid,
                            urgency = null,
                            accessionNumber = null,
                            instructions = null
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

    companion object {
        /** The "Outpatient" CareSetting uuid - verified from a live O3 deployment's real traffic. */
        const val CARE_SETTING_OUTPATIENT = "6f0c9a92-6f24-11e3-af88-005056821db0"

        /** The "Drug Order" order type uuid - verified via this server's `/ws/rest/v1/ordertype`. */
        const val DRUG_ORDER_TYPE_UUID = "131168f4-15f5-102d-96e4-000c29c2a5d7"

        /** The "Test Order" order type uuid - verified via this server's `/ws/rest/v1/ordertype`. */
        const val TEST_ORDER_TYPE_UUID = "52a447d3-a64a-11e3-9aeb-50e549534c5e"
    }
}
