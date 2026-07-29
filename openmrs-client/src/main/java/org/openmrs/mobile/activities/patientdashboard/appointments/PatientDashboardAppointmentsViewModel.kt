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
package org.openmrs.mobile.activities.patientdashboard.appointments

import androidx.lifecycle.SavedStateHandle
import com.openmrs.android_sdk.library.api.repository.AppointmentRepository
import com.openmrs.android_sdk.library.dao.PatientDAO
import com.openmrs.android_sdk.library.models.Appointment
import com.openmrs.android_sdk.library.models.OperationType.AppointmentCancelling
import com.openmrs.android_sdk.library.models.OperationType.PatientAppointmentsFetching
import com.openmrs.android_sdk.utilities.ApplicationConstants.BundleKeys.PATIENT_ID_BUNDLE
import dagger.hilt.android.lifecycle.HiltViewModel
import org.openmrs.mobile.activities.BaseViewModel
import rx.android.schedulers.AndroidSchedulers
import javax.inject.Inject

@HiltViewModel
class PatientDashboardAppointmentsViewModel @Inject constructor(
    private val patientDAO: PatientDAO,
    private val appointmentRepository: AppointmentRepository,
    private val savedStateHandle: SavedStateHandle
) : BaseViewModel<List<Appointment>>() {

    private val patientId: Long = savedStateHandle.get(PATIENT_ID_BUNDLE)!!

    fun fetchAppointments() {
        setLoading(PatientAppointmentsFetching)
        val patientUuid = patientDAO.findPatientByID(patientId)?.uuid
        if (patientUuid == null) {
            setContent(emptyList(), PatientAppointmentsFetching)
            return
        }
        addSubscription(
            appointmentRepository.searchAppointmentsAndSave(patientUuid)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { appointments -> setContent(appointments, PatientAppointmentsFetching) },
                    { fetchCachedAppointments(patientUuid) }
                )
        )
    }

    private fun fetchCachedAppointments(patientUuid: String) {
        addSubscription(
            appointmentRepository.getCachedAppointments(patientUuid)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { appointments -> setContent(appointments, PatientAppointmentsFetching) },
                    { setError(it, PatientAppointmentsFetching) }
                )
        )
    }

    fun cancelAppointment(appointmentUuid: String) {
        setLoading(AppointmentCancelling)
        addSubscription(
            appointmentRepository.cancelAppointment(appointmentUuid)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { fetchAppointments() },
                    { setError(it, AppointmentCancelling) }
                )
        )
    }
}
