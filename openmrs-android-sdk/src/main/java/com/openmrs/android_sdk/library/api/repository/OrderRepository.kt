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
package com.openmrs.android_sdk.library.api.repository

import com.openmrs.android_sdk.R
import com.openmrs.android_sdk.library.databases.AppDatabase
import com.openmrs.android_sdk.library.databases.AppDatabaseHelper
import com.openmrs.android_sdk.library.models.AddObsRequest
import com.openmrs.android_sdk.library.models.ConceptDetails
import com.openmrs.android_sdk.library.models.DrugOrderCreateRequest
import com.openmrs.android_sdk.library.models.DrugOrderDetails
import com.openmrs.android_sdk.library.models.DrugSearchResult
import com.openmrs.android_sdk.library.models.Encounter
import com.openmrs.android_sdk.library.models.FulfillerDetailsRequest
import com.openmrs.android_sdk.library.models.MedicationOrderBuckets
import com.openmrs.android_sdk.library.models.OrderCreate
import com.openmrs.android_sdk.library.models.OrderEntryConfig
import com.openmrs.android_sdk.library.models.OrderGet
import com.openmrs.android_sdk.library.models.OrderTypeInfo
import com.openmrs.android_sdk.library.models.Results
import com.openmrs.android_sdk.library.models.TestOrderCreateRequest
import com.openmrs.android_sdk.library.models.TestSearchResult
import com.openmrs.android_sdk.utilities.DateUtils
import okhttp3.ResponseBody
import retrofit2.Call
import rx.Observable
import java.util.concurrent.Callable
import javax.inject.Inject

class OrderRepository @Inject constructor() : BaseRepository(){

    val representation by lazy { context.resources.getString(R.string.orderGet_resource_representation).trim() }
    val drugSearchRepresentation by lazy { context.resources.getString(R.string.drugSearch_resource_representation).trim() }
    val testSearchRepresentation by lazy { context.resources.getString(R.string.testSearch_resource_representation).trim() }
    val conceptDetailsRepresentation by lazy { context.resources.getString(R.string.conceptDetails_resource_representation).trim() }
    val drugOrderDetailsRepresentation by lazy { context.resources.getString(R.string.drugOrderDetails_resource_representation).trim() }
    val orderRoomDAO by lazy { AppDatabase.getDatabase(context).orderRoomDAO() }

    /**
     * Executes a retrofit request
     *
     * @param call the interface call
     * @param message the error message to display
     *
     * @return T
     */
    fun <T> executeRequest(call: Call<T>, message: String): T {
        val response = call.execute()

        if (response.isSuccessful && response != null) {
            return response.body()!!
        } else {
            logger.e(message + response.message())
            throw Exception(response.message())
        }
    }

    /**
     * Executes a retrofit request and save to database
     *
     * @param call the interface call
     * @param message the error message to display
     *
     * @return T
     */
    fun executeRequestAndSave(call: Call<Results<OrderGet>>, message: String): List<OrderGet> {
        val response = call.execute()
        if (response.isSuccessful && response != null) {
            val orders = response.body()!!.results
            for (order in orders) {
                val orderEntity = AppDatabaseHelper.convert(order)
                orderRoomDAO.addOrder(orderEntity)
            }
            return orders
        } else {
            logger.e(message + response.message())
            throw Exception(response.message())
        }
    }

    /**
     * Creates an Order remotely
     *
     * @param orderCreate the OrderCreate type
     *
     * @return the AppointmentBlock object
     */
    fun createOrder(orderCreate: OrderCreate): Observable<OrderGet> {
        return AppDatabaseHelper.createObservableIO<OrderGet>(Callable {
            val call = restApi.createOrder(orderCreate)
            executeRequest(call, "Error creating the Order: ")
        })
    }

    /**
     * Fetch orders of a patient from server and save to local db
     *
     * @param patientUUID the patient uuid
     */
    fun getOrdersAndSave(patientUUID: String): Observable<List<OrderGet>> {
        return AppDatabaseHelper.createObservableIO<List<OrderGet>>(Callable {
            val call = restApi.getOrdersForPatient(patientUUID, representation)
            return@Callable executeRequestAndSave(call, "Error getting and saving orders from server: ")
        })
    }

    /**
     * Fetch orders of a patient from server and save to local db
     *
     * @param patientUUID the patient uuid
     */
    fun getOrdersAndSave(patientUUID: String, careSetting: String): Observable<List<OrderGet>> {
        return AppDatabaseHelper.createObservableIO<List<OrderGet>>(Callable {
            val call = restApi.getOrdersForPatient(patientUUID, careSetting, representation)
            return@Callable executeRequestAndSave(call, "Error getting and saving orders from server: ")
        })
    }

    /**
     * Fetch orders of a patient from server and save to local db
     *
     * @param patientUUID the patient uuid
     */
    fun getOrdersAndSave(patientUUID: String, careSetting: String, orderType: String): Observable<List<OrderGet>> {
        return AppDatabaseHelper.createObservableIO<List<OrderGet>>(Callable {
            val call = restApi.getOrdersForPatient(patientUUID, orderType, careSetting,representation)
            return@Callable executeRequestAndSave(call, "Error getting and saving orders from server: ")
        })
    }

    /**
     * Fetch orders of a patient from server and save to local db
     *
     * @param patientUUID the patient uuid
     */
    fun getOrdersAndSave(patientUUID: String, careSetting: String, orderType: String, activatedOnOrAfterDate: String): Observable<List<OrderGet>> {
        return AppDatabaseHelper.createObservableIO<List<OrderGet>>(Callable {
            val call = restApi.getOrdersForPatient(patientUUID, orderType, careSetting, activatedOnOrAfterDate, representation)
            return@Callable executeRequestAndSave(call, "Error getting and saving orders from server: ")
        })
    }

    /**
     * Fetch orders of a patient from server and save to local db
     *
     * @param patientUUID the patient uuid
     */
    fun getOrdersWithOrderTypeAndSave(patientUUID: String, orderType: String): Observable<List<OrderGet>> {
        return AppDatabaseHelper.createObservableIO<List<OrderGet>>(Callable {
            val call = restApi.getOrdersForPatientWithOrderType(patientUUID, orderType, representation)
            return@Callable executeRequestAndSave(call, "Error getting and saving orders from server: ")
        })
    }

    /**
     * Fetch orders of a patient from server and save to local db
     *
     * @param patientUUID the patient uuid
     */
    fun getOrdersFromDateAndSave(patientUUID: String, activatedOnOrAfterDate: String): Observable<List<OrderGet>> {
        return AppDatabaseHelper.createObservableIO<List<OrderGet>>(Callable {
            val call = restApi.getOrdersForPatientFromDate(patientUUID, activatedOnOrAfterDate, representation)
            return@Callable executeRequestAndSave(call, "Error getting and saving orders from server: ")
        })
    }

    /**
     * Fetch a patient's orders active on a given day and save to local db - matches the exact
     * query O3's Orders chart tab makes, filtered to a single order type (e.g. Drug Order vs
     * Test Order) so the Medications and Orders tabs each show a clean, mutually-exclusive list.
     */
    fun getOrdersForDateAndSave(patientUUID: String, careSetting: String, date: String, orderTypes: String): Observable<List<OrderGet>> {
        return AppDatabaseHelper.createObservableIO<List<OrderGet>>(Callable {
            val call = restApi.getOrdersForPatientOnDate(patientUUID, careSetting, representation, date, date, true, true, orderTypes)
            return@Callable executeRequestAndSave(call, "Error getting and saving orders from server: ")
        })
    }

    /**
     * Reads a patient's orders from the local cache only, for offline fallback.
     */
    fun getCachedOrders(patientUUID: String): Observable<List<OrderGet>> {
        return AppDatabaseHelper.createObservableIO(Callable {
            orderRoomDAO.getOrdersForPatient(patientUUID).blockingGet().map { AppDatabaseHelper.convert(it) }
        })
    }

    /**
     * Fetch every non-discontinued order of a given type for a patient and save to local db -
     * matches O3's Medications widget query (no date range; buckets into active/upcoming/past
     * client-side via [bucketMedicationOrders] instead).
     */
    fun getMedicationOrdersAndSave(patientUUID: String, careSetting: String, orderTypes: String): Observable<List<OrderGet>> {
        return AppDatabaseHelper.createObservableIO<List<OrderGet>>(Callable {
            val call = restApi.getOrdersForPatientExcludingDiscontinued(patientUUID, careSetting, orderTypes, representation, true)
            return@Callable executeRequestAndSave(call, "Error getting and saving orders from server: ")
        })
    }

    /**
     * Searches drugs by name, for the drug order form's autocomplete.
     */
    fun searchDrugs(query: String): Observable<List<DrugSearchResult>> {
        return AppDatabaseHelper.createObservableIO(Callable {
            executeRequest(restApi.searchDrugs(query, drugSearchRepresentation), "Error searching drugs: ").results
        })
    }

    /**
     * Gets the configuration needed to build a drug order form (routes, dosing/dispensing
     * units, duration units, order frequencies).
     */
    fun getOrderEntryConfig(): Observable<OrderEntryConfig> {
        return AppDatabaseHelper.createObservableIO(Callable {
            executeRequest(restApi.getOrderEntryConfig(), "Error fetching order entry config: ")
        })
    }

    /**
     * Gets an order type by uuid, e.g. to resolve/display "Drug Order" vs "Test Order".
     */
    fun getOrderType(uuid: String): Observable<OrderTypeInfo> {
        return AppDatabaseHelper.createObservableIO(Callable {
            executeRequest(restApi.getOrderType(uuid), "Error fetching order type: ")
        })
    }

    /**
     * Creates a new drug order. The caller is responsible for having already created the
     * encounter this order attaches to (see EncounterRepository.createEncounterOnline).
     */
    fun createDrugOrder(request: DrugOrderCreateRequest): Observable<OrderGet> {
        return AppDatabaseHelper.createObservableIO(Callable {
            executeRequest(restApi.createDrugOrder(request), "Error creating drug order: ")
        })
    }

    /**
     * Fetches full details (drug, dose, route, frequency, etc.) for a single drug order - the
     * Medications list only holds the trimmed [OrderGet] shape, so Modify/Renew/Discontinue fetch
     * this first to pre-fill a form or to get the drug uuid a discontinue request needs.
     */
    fun getDrugOrderDetails(orderUuid: String): Observable<DrugOrderDetails> {
        return AppDatabaseHelper.createObservableIO(Callable {
            executeRequest(restApi.getDrugOrderDetails(orderUuid, drugOrderDetailsRepresentation), "Error fetching drug order details: ")
        })
    }

    /**
     * Searches concepts by name for the lab order form's test search, filtered client-side to
     * the "Test" concept class since this server doesn't support filtering the search by class.
     */
    fun searchTests(query: String): Observable<List<TestSearchResult>> {
        return AppDatabaseHelper.createObservableIO(Callable {
            executeRequest(restApi.searchConcepts(query, testSearchRepresentation), "Error searching tests: ")
                .results
                .filter { it.conceptClass?.uuid == TEST_CONCEPT_CLASS_UUID }
        })
    }

    /**
     * Creates, revises, or discontinues a test (lab) order, depending on `request.action`. For
     * REVISE/DISCONTINUE the caller reuses the original order's own encounter; for NEW the caller
     * is responsible for having already created the encounter this order attaches to (see
     * EncounterRepository.createEncounterOnline).
     */
    fun createTestOrder(request: TestOrderCreateRequest): Observable<OrderGet> {
        return AppDatabaseHelper.createObservableIO(Callable {
            executeRequest(restApi.createTestOrder(request), "Error creating test order: ")
        })
    }

    /**
     * Gets a concept's datatype/answers/reference-range, to decide how to render the "Add
     * result" form for a lab order.
     */
    fun getConceptDetails(conceptUuid: String): Observable<ConceptDetails> {
        return AppDatabaseHelper.createObservableIO(Callable {
            executeRequest(restApi.getConceptDetails(conceptUuid, conceptDetailsRepresentation), "Error fetching concept details: ")
        })
    }

    /**
     * Adds a test result as an obs into an order's own existing encounter.
     */
    fun addTestResult(encounterUuid: String, request: AddObsRequest): Observable<Encounter> {
        return AppDatabaseHelper.createObservableIO(Callable {
            executeRequest(restApi.addObsToEncounter(encounterUuid, request), "Error adding test result: ")
        })
    }

    /**
     * Marks an order's fulfiller status, e.g. COMPLETED after a test result is recorded.
     */
    fun markOrderFulfilled(orderUuid: String, comment: String): Observable<ResponseBody> {
        return AppDatabaseHelper.createObservableIO(Callable {
            executeRequest(restApi.updateFulfillerDetails(orderUuid, FulfillerDetailsRequest(fulfillerComment = comment)), "Error updating fulfiller details: ")
        })
    }

    companion object {
        /** Verified against this server's `/ws/rest/v1/conceptclass` list ("Test"). */
        private const val TEST_CONCEPT_CLASS_UUID = "8d4907b2-c2cc-11de-8d13-0010c6dffd0f"

        /**
         * Kotlin port of `bucketMedicationOrders` from openmrs-esm-patient-chart's
         * esm-patient-medications-app/src/api/api.ts - a drug order is:
         * - upcoming, if it has a future `scheduledDate` and hasn't been stopped;
         * - past, if its `autoExpireDate` or `dateStopped` has already passed;
         * - active, otherwise.
         * Sorted the same way O3 does: soonest-first for upcoming, most-recent-first for the rest.
         */
        fun bucketMedicationOrders(orders: List<OrderGet>, now: Long = System.currentTimeMillis()): MedicationOrderBuckets {
            val upcoming = mutableListOf<OrderGet>()
            val active = mutableListOf<OrderGet>()
            val past = mutableListOf<OrderGet>()

            orders.forEach { order ->
                val dateStopped = DateUtils.convertTime(order.dateStopped)
                val autoExpireDate = DateUtils.convertTime(order.autoExpireDate)
                val dateScheduled = DateUtils.convertTime(order.scheduledDate)

                when {
                    dateScheduled != null && dateScheduled > now && dateStopped == null -> upcoming.add(order)
                    (autoExpireDate != null && autoExpireDate <= now) || (dateStopped != null && dateStopped <= now) -> past.add(order)
                    else -> active.add(order)
                }
            }

            fun startTime(order: OrderGet) = DateUtils.convertTime(order.scheduledDate ?: order.dateActivated) ?: 0L

            return MedicationOrderBuckets(
                activeOrders = active.sortedByDescending(::startTime),
                upcomingOrders = upcoming.sortedBy(::startTime),
                pastOrders = past.sortedByDescending(::startTime)
            )
        }
    }
}