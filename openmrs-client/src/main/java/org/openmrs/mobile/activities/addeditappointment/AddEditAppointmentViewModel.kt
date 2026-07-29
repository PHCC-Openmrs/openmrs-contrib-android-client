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
package org.openmrs.mobile.activities.addeditappointment

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import com.openmrs.android_sdk.library.OpenmrsAndroid
import com.openmrs.android_sdk.library.api.repository.AppointmentRepository
import com.openmrs.android_sdk.library.api.repository.AppointmentRepository.Companion.formatAppointmentDateTime
import com.openmrs.android_sdk.library.api.repository.ProviderRepository
import com.openmrs.android_sdk.library.dao.PatientDAO
import com.openmrs.android_sdk.library.databases.entities.LocationEntity
import com.openmrs.android_sdk.library.models.Appointment
import com.openmrs.android_sdk.library.models.AppointmentConflictRequest
import com.openmrs.android_sdk.library.models.AppointmentCreateRequest
import com.openmrs.android_sdk.library.models.AppointmentProviderRef
import com.openmrs.android_sdk.library.models.AppointmentServiceInfo
import com.openmrs.android_sdk.library.models.Patient
import com.openmrs.android_sdk.library.models.Provider
import com.openmrs.android_sdk.library.models.RecurringAppointmentPayload
import com.openmrs.android_sdk.library.models.RecurringPattern
import com.openmrs.android_sdk.library.models.ResultType
import com.openmrs.android_sdk.utilities.ApplicationConstants.BundleKeys.APPOINTMENT_BUNDLE
import com.openmrs.android_sdk.utilities.ApplicationConstants.BundleKeys.PATIENT_ID_BUNDLE
import dagger.hilt.android.lifecycle.HiltViewModel
import org.openmrs.mobile.activities.BaseViewModel
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import java.util.TimeZone
import javax.inject.Inject

/**
 * Backs the "Add/Edit appointment" screen, mirroring O3's appointments-form.workspace.tsx: a
 * location/service/provider/appointment-type picker, start date+time+duration, an optional
 * recurring pattern, a "date appointment issued" field, notes, and (when editing) an appointment
 * status picker - submitted through the same conflict-check -> create/edit (or create-recurring)
 * flow O3 uses.
 */
@HiltViewModel
class AddEditAppointmentViewModel @Inject constructor(
    private val patientDAO: PatientDAO,
    private val providerRepository: ProviderRepository,
    private val appointmentRepository: AppointmentRepository,
    private val savedStateHandle: SavedStateHandle
) : BaseViewModel<Unit>() {

    private val patientId: Long = savedStateHandle.get(PATIENT_ID_BUNDLE)!!
    private val patient: Patient = patientDAO.findPatientByID(patientId)

    /** Non-null when editing an existing appointment (passed in by the appointments tab). */
    private val existingAppointment: Appointment? = savedStateHandle.get(APPOINTMENT_BUNDLE)
    val isEditing: Boolean = existingAppointment != null

    /* Lists of form fields, keyed by display string (matches FormAdmissionViewModel's idiom) */
    var locations: LinkedHashMap<String, LocationEntity> = linkedMapOf()
        private set
    var providers: LinkedHashMap<String, Provider> = linkedMapOf()
        private set
    var services: LinkedHashMap<String, AppointmentServiceInfo> = linkedMapOf()
        private set

    /* Spinner selection state, restored across rotation the same way FormAdmissionViewModel does */
    var locationListPosition: Int = 0
    var providerListPosition: Int = 0
    var serviceListPosition: Int = 0

    var locationUuid: String? = null
        private set
    var providerUuid: String? = null
        private set
    var serviceUuid: String? = null
        private set

    /** Prefilled from the selected service's durationMins, matching O3's onChange behavior; user can override. */
    var durationMins: Int? = existingAppointment?.let { appt ->
        val start = appt.startDateTime
        val end = appt.endDateTime
        if (start != null && end != null) ((end - start) / 60_000L).toInt() else null
    }

    var appointmentKind: String = existingAppointment?.appointmentKind ?: Appointment.Kind.SCHEDULED

    var startDateTimeMillis: Long = existingAppointment?.startDateTime ?: System.currentTimeMillis()
    var dateAppointmentScheduledMillis: Long =
        existingAppointment?.dateAppointmentScheduled ?: System.currentTimeMillis()

    var isRecurringAppointment: Boolean = false
    var recurringPatternType: String = RecurringPattern.Type.DAY
    var recurringPeriod: Int = 1
    var recurringDaysOfWeek: MutableSet<String> = mutableSetOf()
    var recurringEndDateMillis: Long? = null

    var notes: String = existingAppointment?.comments ?: ""

    /** Only shown/editable in the UI when [isEditing]; O3 leaves this blank on create and lets the server default it. */
    var status: String = existingAppointment?.status ?: ""

    private val listsObservables
        get() = listOf(
            providerRepository.getLocations(OpenmrsAndroid.getServerUrl()).map { list ->
                locations = LinkedHashMap<String, LocationEntity>().apply {
                    list.forEach { put(it.display ?: it.name.orEmpty(), it) }
                }
            },
            providerRepository.getProviders().map { list ->
                providers = LinkedHashMap<String, Provider>().apply {
                    list.forEach { put(it.display ?: "", it) }
                }
            },
            appointmentRepository.getAppointmentServices().map { list ->
                services = LinkedHashMap<String, AppointmentServiceInfo>().apply {
                    list.forEach { put(it.name ?: "", it) }
                }
            }
        )

    init {
        fetchFormFields()
    }

    private fun fetchFormFields() {
        setLoading()
        addSubscription(Observable.merge(listsObservables)
            .observeOn(AndroidSchedulers.mainThread())
            .doOnCompleted {
                if (locations.isNotEmpty() && services.isNotEmpty()) {
                    restoreSelectionsForEditing()
                    setContent(Unit)
                } else {
                    setError(Throwable("Some form field lists are empty"))
                }
            }
            .subscribe({}, {
                clearSubscriptions()
                setError(it)
            })
        )
    }

    /** Once the pickers' data has loaded, point each spinner at the existing appointment's values, if editing. */
    private fun restoreSelectionsForEditing() {
        val appointment = existingAppointment ?: return
        locations.values.toList().indexOfFirst { it.uuid == appointment.location?.uuid }
            .takeIf { it >= 0 }?.let { locationListPosition = it }
        services.values.toList().indexOfFirst { it.uuid == appointment.service?.uuid }
            .takeIf { it >= 0 }?.let { serviceListPosition = it }
        providers.values.toList().indexOfFirst { it.uuid == appointment.providers?.firstOrNull()?.uuid }
            .takeIf { it >= 0 }?.let { providerListPosition = it }
    }

    fun selectLocation(name: String, listPosition: Int) {
        locationListPosition = listPosition
        locationUuid = locations[name]?.uuid
    }

    fun selectProvider(name: String, listPosition: Int) {
        providerListPosition = listPosition
        providerUuid = providers[name]?.uuid
    }

    fun selectService(name: String, listPosition: Int) {
        serviceListPosition = listPosition
        val service = services[name]
        serviceUuid = service?.uuid
        durationMins = service?.durationMins ?: durationMins
    }

    fun submitAppointment(): LiveData<ResultType> {
        val resultLiveData = MutableLiveData<ResultType>()

        val endDateTimeMillis = startDateTimeMillis + (durationMins ?: 0) * 60_000L
        val startDateTimeIso = formatAppointmentDateTime(startDateTimeMillis)
        val endDateTimeIso = formatAppointmentDateTime(endDateTimeMillis)

        val appointmentRequest = AppointmentCreateRequest(
            appointmentKind = appointmentKind,
            status = status,
            serviceUuid = serviceUuid!!,
            startDateTime = startDateTimeIso,
            endDateTime = endDateTimeIso,
            locationUuid = locationUuid!!,
            providers = providerUuid?.let { listOf(AppointmentProviderRef(it)) } ?: emptyList(),
            patientUuid = patient.uuid!!,
            comments = notes,
            dateAppointmentScheduled = formatAppointmentDateTime(dateAppointmentScheduledMillis),
            uuid = existingAppointment?.uuid
        )

        val conflictRequest = AppointmentConflictRequest(
            patientUuid = patient.uuid!!,
            serviceUuid = serviceUuid!!,
            startDateTime = startDateTimeIso,
            endDateTime = endDateTimeIso,
            locationUuid = locationUuid!!,
            appointmentKind = appointmentKind,
            uuid = existingAppointment?.uuid
        )

        addSubscription(
            appointmentRepository.hasConflict(conflictRequest)
                .flatMap { conflict ->
                    when {
                        conflict -> Observable.just(ResultType.AppointmentConflict)
                        isRecurringAppointment -> {
                            val recurringPattern = RecurringPattern(
                                type = recurringPatternType,
                                period = recurringPeriod,
                                endDate = formatAppointmentDateTime(recurringEndDateMillis!!),
                                daysOfWeek = recurringDaysOfWeek.toList()
                            )
                            val payload = RecurringAppointmentPayload(
                                appointmentRequest = appointmentRequest,
                                recurringPattern = recurringPattern,
                                timeZone = TimeZone.getDefault().id
                            )
                            appointmentRepository.createRecurringAppointments(payload)
                                .map { ResultType.AppointmentCreateSuccess }
                        }
                        else -> appointmentRepository.createAppointment(appointmentRequest)
                            .map { ResultType.AppointmentCreateSuccess }
                    }
                }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { resultLiveData.value = it },
                    { resultLiveData.value = ResultType.AppointmentCreateError }
                )
        )

        return resultLiveData
    }
}
