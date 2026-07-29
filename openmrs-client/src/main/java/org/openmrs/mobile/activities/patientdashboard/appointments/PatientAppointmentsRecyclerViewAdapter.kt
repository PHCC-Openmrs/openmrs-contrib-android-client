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

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.openmrs.android_sdk.library.models.Appointment
import com.openmrs.android_sdk.utilities.DateUtils
import org.openmrs.mobile.R
import org.openmrs.mobile.databinding.RowPatientAppointmentBinding

class PatientAppointmentsRecyclerViewAdapter(
    private var appointments: List<Appointment>,
    private val onCancelClicked: (Appointment) -> Unit,
    private val onEditClicked: (Appointment) -> Unit
) : RecyclerView.Adapter<PatientAppointmentsRecyclerViewAdapter.AppointmentViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppointmentViewHolder {
        val binding = RowPatientAppointmentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AppointmentViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AppointmentViewHolder, position: Int) {
        holder.bind(appointments[position], onCancelClicked, onEditClicked)
    }

    override fun getItemCount(): Int = appointments.size

    fun updateList(newAppointments: List<Appointment>) {
        appointments = newAppointments
        notifyDataSetChanged()
    }

    class AppointmentViewHolder(private val binding: RowPatientAppointmentBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(appointment: Appointment, onCancelClicked: (Appointment) -> Unit, onEditClicked: (Appointment) -> Unit) {
            val context = binding.root.context

            binding.appointmentService.text = appointment.service?.name
                ?: context.getString(R.string.appointment_no_service)
            binding.appointmentDate.text = appointment.startDateTime
                ?.let { DateUtils.convertTime(it, DateUtils.DATE_WITH_TIME_FORMAT) }
                ?: MISSING_VALUE_PLACEHOLDER
            binding.appointmentLocation.text = appointment.location?.name ?: MISSING_VALUE_PLACEHOLDER
            binding.appointmentType.text = appointment.appointmentKind ?: MISSING_VALUE_PLACEHOLDER
            binding.appointmentStatus.text = appointment.status ?: MISSING_VALUE_PLACEHOLDER

            if (appointment.comments.isNullOrBlank()) {
                binding.appointmentNotes.visibility = View.GONE
            } else {
                binding.appointmentNotes.visibility = View.VISIBLE
                binding.appointmentNotes.text = appointment.comments
            }

            val isCancellable = CANCELLABLE_STATUSES.contains(appointment.status)
            binding.appointmentCancelButton.visibility = if (isCancellable) View.VISIBLE else View.GONE
            binding.appointmentCancelButton.setOnClickListener { onCancelClicked(appointment) }

            // Always shown, matching O3's row overflow menu (Edit is available regardless of status).
            binding.appointmentEditButton.setOnClickListener { onEditClicked(appointment) }
        }

        companion object {
            private const val MISSING_VALUE_PLACEHOLDER = "——"

            /** Statuses O3 still allows cancelling from (mirrors the Edit/Cancel row menu). */
            private val CANCELLABLE_STATUSES = setOf(
                Appointment.Status.REQUESTED,
                Appointment.Status.WAITLIST,
                Appointment.Status.SCHEDULED,
                Appointment.Status.ARRIVED,
                Appointment.Status.CHECKED_IN
            )
        }
    }
}
