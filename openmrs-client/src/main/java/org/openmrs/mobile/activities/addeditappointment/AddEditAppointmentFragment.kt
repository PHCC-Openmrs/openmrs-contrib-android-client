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

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.AdapterView.OnItemSelectedListener
import android.widget.ArrayAdapter
import android.widget.DatePicker
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import com.google.android.material.chip.Chip
import com.openmrs.android_sdk.library.models.Appointment
import com.openmrs.android_sdk.library.models.RecurringPattern
import com.openmrs.android_sdk.library.models.Result
import com.openmrs.android_sdk.library.models.ResultType
import com.openmrs.android_sdk.utilities.ApplicationConstants.BundleKeys.APPOINTMENT_BUNDLE
import com.openmrs.android_sdk.utilities.ApplicationConstants.BundleKeys.PATIENT_ID_BUNDLE
import com.openmrs.android_sdk.utilities.ToastUtil
import dagger.hilt.android.AndroidEntryPoint
import org.openmrs.mobile.R
import org.openmrs.mobile.activities.BaseFragment
import org.openmrs.mobile.databinding.FragmentAddEditAppointmentBinding
import org.openmrs.mobile.utilities.makeGone
import org.openmrs.mobile.utilities.makeVisible
import org.openmrs.mobile.utilities.observeOnce
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * "Add appointment" form, mirroring O3's appointments-form.workspace.tsx: location/service/
 * appointment-type/provider pickers, start date+time+duration, an optional recurring pattern,
 * a "date appointment issued" field and notes - submitted through the same conflict-check ->
 * create (or create-recurring) flow O3 uses.
 */
@AndroidEntryPoint
class AddEditAppointmentFragment : BaseFragment() {
    private var _binding: FragmentAddEditAppointmentBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AddEditAppointmentViewModel by viewModels()

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    /** Display label -> API value, matches Appointment.Kind. */
    private val appointmentKindOptions by lazy {
        linkedMapOf(
            getString(R.string.appointment_kind_scheduled) to Appointment.Kind.SCHEDULED,
            getString(R.string.appointment_kind_walk_in) to Appointment.Kind.WALK_IN,
            getString(R.string.appointment_kind_virtual) to Appointment.Kind.VIRTUAL
        )
    }

    /** Calendar day-of-week constant -> the OpenMRS day-of-week id ("MONDAY".."SUNDAY"). */
    private val daysOfWeek = listOf(
        Calendar.MONDAY to "MONDAY", Calendar.TUESDAY to "TUESDAY", Calendar.WEDNESDAY to "WEDNESDAY",
        Calendar.THURSDAY to "THURSDAY", Calendar.FRIDAY to "FRIDAY", Calendar.SATURDAY to "SATURDAY",
        Calendar.SUNDAY to "SUNDAY"
    )

    /** Display label -> API value, matches Appointment.Status. Only shown/used when editing. */
    private val appointmentStatusOptions by lazy {
        linkedMapOf(
            getString(R.string.appointment_status_requested) to Appointment.Status.REQUESTED,
            getString(R.string.appointment_status_waitlist) to Appointment.Status.WAITLIST,
            getString(R.string.appointment_status_scheduled) to Appointment.Status.SCHEDULED,
            getString(R.string.appointment_status_arrived) to Appointment.Status.ARRIVED,
            getString(R.string.appointment_status_checked_in) to Appointment.Status.CHECKED_IN,
            getString(R.string.appointment_status_completed) to Appointment.Status.COMPLETED,
            getString(R.string.appointment_status_cancelled) to Appointment.Status.CANCELLED,
            getString(R.string.appointment_status_missed) to Appointment.Status.MISSED
        )
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAddEditAppointmentBinding.inflate(inflater, container, false)

        requireActivity().title = getString(
            if (viewModel.isEditing) R.string.edit_appointment_title else R.string.new_appointment_title
        )
        setupObserver()

        return binding.root
    }

    private fun setupObserver() {
        viewModel.result.observe(viewLifecycleOwner, Observer { result ->
            when (result) {
                is Result.Loading -> showLoading(true)
                is Result.Success -> {
                    setupLocationSpinner()
                    setupServiceSpinner()
                    setupAppointmentTypeSpinner()
                    setupAppointmentStatusSpinner()
                    setupProviderSpinner()
                    setupDateTimeFields()
                    setupRecurringSection()
                    initListeners()
                    showLoading(false)
                }
                is Result.Error -> {
                    ToastUtil.error(getString(R.string.appointment_form_error))
                    showLoading(false)
                }
                else -> Unit
            }
        })
    }

    private fun setupLocationSpinner() = with(binding.locationSpinner) {
        adapter = ArrayAdapter(requireActivity(), android.R.layout.simple_list_item_1, viewModel.locations.keys.toList())
        setSelection(viewModel.locationListPosition)
        onItemSelectedListener = object : OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                viewModel.selectLocation(selectedItem.toString(), position)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun setupProviderSpinner() = with(binding.providerSpinner) {
        adapter = ArrayAdapter(requireActivity(), android.R.layout.simple_list_item_1, viewModel.providers.keys.toList())
        setSelection(viewModel.providerListPosition)
        onItemSelectedListener = object : OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                viewModel.selectProvider(selectedItem.toString(), position)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun setupServiceSpinner() = with(binding.serviceSpinner) {
        adapter = ArrayAdapter(requireActivity(), android.R.layout.simple_list_item_1, viewModel.services.keys.toList())
        setSelection(viewModel.serviceListPosition)
        onItemSelectedListener = object : OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                viewModel.selectService(selectedItem.toString(), position)
                viewModel.durationMins?.let { binding.durationEditText.setText(it.toString()) }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun setupAppointmentTypeSpinner() = with(binding.appointmentTypeSpinner) {
        adapter = ArrayAdapter(requireActivity(), android.R.layout.simple_list_item_1, appointmentKindOptions.keys.toList())
        setSelection(appointmentKindOptions.values.toList().indexOf(viewModel.appointmentKind).coerceAtLeast(0))
        onItemSelectedListener = object : OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                viewModel.appointmentKind = appointmentKindOptions.values.toList()[position]
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    /** Only shown when editing, matching O3's edit form. */
    private fun setupAppointmentStatusSpinner() = with(binding) {
        if (!viewModel.isEditing) {
            appointmentStatusLabel.makeGone()
            appointmentStatusSpinner.makeGone()
            return@with
        }
        appointmentStatusLabel.makeVisible()
        appointmentStatusSpinner.makeVisible()
        appointmentStatusSpinner.adapter =
            ArrayAdapter(requireActivity(), android.R.layout.simple_list_item_1, appointmentStatusOptions.keys.toList())
        appointmentStatusSpinner.setSelection(
            appointmentStatusOptions.values.toList().indexOf(viewModel.status).coerceAtLeast(0)
        )
        appointmentStatusSpinner.onItemSelectedListener = object : OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                viewModel.status = appointmentStatusOptions.values.toList()[position]
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun setupDateTimeFields() = with(binding) {
        startDateEditText.setText(dateFormat.format(viewModel.startDateTimeMillis))
        startDateEditText.setOnClickListener {
            // showDatePicker merges the picked y/m/d onto the existing initialMillis, preserving its time-of-day.
            showDatePicker(viewModel.startDateTimeMillis) {
                startDateEditText.setText(dateFormat.format(it))
                viewModel.startDateTimeMillis = it
            }
        }

        startTimeEditText.setText(timeFormat.format(viewModel.startDateTimeMillis))
        startTimeEditText.setOnClickListener {
            // showTimePicker merges the picked h/m onto the existing initialMillis, preserving its date.
            showTimePicker(viewModel.startDateTimeMillis) {
                startTimeEditText.setText(timeFormat.format(it))
                viewModel.startDateTimeMillis = it
            }
        }

        dateAppointmentScheduledEditText.setText(dateFormat.format(viewModel.dateAppointmentScheduledMillis))
        dateAppointmentScheduledEditText.setOnClickListener {
            showDatePicker(viewModel.dateAppointmentScheduledMillis) {
                dateAppointmentScheduledEditText.setText(dateFormat.format(it))
                viewModel.dateAppointmentScheduledMillis = it
            }
        }

        recurringEndDateEditText.setOnClickListener {
            showDatePicker(viewModel.recurringEndDateMillis ?: viewModel.startDateTimeMillis) {
                recurringEndDateEditText.setText(dateFormat.format(it))
                // Matches O3: the recurring end date includes the whole of that calendar day.
                viewModel.recurringEndDateMillis = endOfDay(it)
            }
        }
    }

    private fun setupRecurringSection() = with(binding) {
        setupDaysOfWeekChips()

        recurringSwitch.setOnCheckedChangeListener { _, isChecked ->
            viewModel.isRecurringAppointment = isChecked
            if (isChecked) recurringSection.makeVisible() else recurringSection.makeGone()
        }

        recurringPeriodTypeRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            val isWeek = checkedId == R.id.recurringPeriodWeekRadio
            viewModel.recurringPatternType = if (isWeek) RecurringPattern.Type.WEEK else RecurringPattern.Type.DAY
            recurringDaysOfWeekChipGroup.visibility = if (isWeek) View.VISIBLE else View.GONE
        }

        recurringPeriodEditText.doAfterTextChanged { text ->
            viewModel.recurringPeriod = text?.toString()?.toIntOrNull() ?: 1
        }
    }

    private fun setupDaysOfWeekChips() {
        binding.recurringDaysOfWeekChipGroup.removeAllViews()
        val symbols = java.text.DateFormatSymbols(Locale.getDefault())
        daysOfWeek.forEach { (calendarDay, dayId) ->
            val chip = Chip(requireContext()).apply {
                text = symbols.shortWeekdays[calendarDay]
                isCheckable = true
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) viewModel.recurringDaysOfWeek.add(dayId) else viewModel.recurringDaysOfWeek.remove(dayId)
                }
            }
            binding.recurringDaysOfWeekChipGroup.addView(chip)
        }
    }

    private fun initListeners() = with(binding) {
        viewModel.durationMins?.let { durationEditText.setText(it.toString()) }
        notesEditText.setText(viewModel.notes)

        durationEditText.doAfterTextChanged { text ->
            viewModel.durationMins = text?.toString()?.toIntOrNull()
        }
        notesEditText.doAfterTextChanged { text ->
            viewModel.notes = text?.toString().orEmpty()
        }
        submitButton.setOnClickListener { validateAndConfirm() }
        cancelButton.setOnClickListener { requireActivity().finish() }
    }

    private fun validateAndConfirm() {
        val error = when {
            viewModel.locationUuid == null -> R.string.warning_select_location
            viewModel.serviceUuid == null -> R.string.warning_select_service
            viewModel.providerUuid == null -> R.string.warning_select_provider
            (viewModel.durationMins ?: 0) <= 0 -> R.string.warning_invalid_duration
            viewModel.isRecurringAppointment && viewModel.recurringEndDateMillis == null -> R.string.warning_select_recurring_end_date
            viewModel.isRecurringAppointment && viewModel.recurringPatternType == RecurringPattern.Type.WEEK &&
                viewModel.recurringDaysOfWeek.isEmpty() -> R.string.warning_select_recurring_days
            viewModel.dateAppointmentScheduledMillis > viewModel.startDateTimeMillis -> R.string.warning_date_appointment_issued_after_start
            else -> null
        }

        if (error != null) {
            ToastUtil.error(getString(error))
            return
        }

        AlertDialog.Builder(requireContext(), R.style.AlertDialogTheme)
            .setTitle(if (viewModel.isEditing) R.string.edit_appointment_confirm_title else R.string.create_appointment_confirm_title)
            .setMessage(if (viewModel.isEditing) R.string.edit_appointment_confirm_message else R.string.create_appointment_confirm_message)
            .setCancelable(false)
            .setPositiveButton(R.string.mark_patient_deceased_proceed) { dialog, _ ->
                dialog.dismiss()
                submitAppointment()
            }
            .setNegativeButton(R.string.dialog_button_cancel) { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun submitAppointment() {
        showLoading(true)
        viewModel.submitAppointment().observeOnce(viewLifecycleOwner, Observer { result ->
            showLoading(false)
            when (result) {
                ResultType.AppointmentCreateSuccess -> {
                    ToastUtil.success(getString(if (viewModel.isEditing) R.string.appointment_edit_success else R.string.appointment_form_success))
                    requireActivity().finish()
                }
                ResultType.AppointmentConflict -> ToastUtil.error(getString(R.string.appointment_conflict_error))
                else -> ToastUtil.error(getString(if (viewModel.isEditing) R.string.appointment_edit_error else R.string.appointment_form_error))
            }
        })
    }

    private fun showLoading(loading: Boolean) = with(binding) {
        if (loading) {
            transparentScreen.makeVisible()
            progressBar.makeVisible()
        } else {
            transparentScreen.makeGone()
            progressBar.makeGone()
        }
    }

    private fun showDatePicker(initialMillis: Long, onPicked: (Long) -> Unit) {
        val calendar = Calendar.getInstance().apply { timeInMillis = initialMillis }
        DatePickerDialog(
            requireActivity(),
            { _: DatePicker?, year: Int, month: Int, dayOfMonth: Int ->
                val picked = Calendar.getInstance().apply {
                    timeInMillis = initialMillis
                    set(year, month, dayOfMonth)
                }
                onPicked(picked.timeInMillis)
            },
            calendar[Calendar.YEAR], calendar[Calendar.MONTH], calendar[Calendar.DAY_OF_MONTH]
        ).show()
    }

    private fun showTimePicker(initialMillis: Long, onPicked: (Long) -> Unit) {
        val calendar = Calendar.getInstance().apply { timeInMillis = initialMillis }
        TimePickerDialog(
            requireActivity(),
            { _, hourOfDay: Int, minute: Int ->
                val picked = Calendar.getInstance().apply {
                    timeInMillis = initialMillis
                    set(Calendar.HOUR_OF_DAY, hourOfDay)
                    set(Calendar.MINUTE, minute)
                }
                onPicked(picked.timeInMillis)
            },
            calendar[Calendar.HOUR_OF_DAY], calendar[Calendar.MINUTE], true
        ).show()
    }

    /** Matches O3's recurring end date, which is set to 23:59 so the whole calendar day is included. */
    private fun endOfDay(millis: Long): Long = Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 23)
        set(Calendar.MINUTE, 59)
        set(Calendar.SECOND, 59)
        set(Calendar.MILLISECOND, 999)
    }.timeInMillis

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(patientId: Long, appointment: Appointment? = null) = AddEditAppointmentFragment().apply {
            arguments = bundleOf(PATIENT_ID_BUNDLE to patientId, APPOINTMENT_BUNDLE to appointment)
        }
    }
}
