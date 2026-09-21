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
package org.openmrs.mobile.activities.startvisit

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.text.InputType
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
import com.openmrs.android_sdk.library.models.Result
import com.openmrs.android_sdk.library.models.Visit
import com.openmrs.android_sdk.library.models.VisitAttributeType
import com.openmrs.android_sdk.utilities.ApplicationConstants
import com.openmrs.android_sdk.utilities.ApplicationConstants.BundleKeys.PATIENT_ID_BUNDLE
import com.openmrs.android_sdk.utilities.ApplicationConstants.VisitAttributeDatatypes
import com.openmrs.android_sdk.utilities.ToastUtil
import dagger.hilt.android.AndroidEntryPoint
import org.openmrs.mobile.R
import org.openmrs.mobile.activities.BaseFragment
import org.openmrs.mobile.activities.visitdashboard.VisitDashboardActivity
import org.openmrs.mobile.databinding.FragmentStartVisitBinding
import org.openmrs.mobile.databinding.ItemVisitAttributeCheckboxBinding
import org.openmrs.mobile.databinding.ItemVisitAttributeSpinnerBinding
import org.openmrs.mobile.databinding.ItemVisitAttributeTextBinding
import org.openmrs.mobile.utilities.makeGone
import org.openmrs.mobile.utilities.makeVisible
import org.openmrs.mobile.utilities.observeOnce
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * "Start visit" form, mirroring the web client's start-visit workspace: the location the visit
 * takes place at, the service(s) it is for, and whatever extra questions are configured for a
 * visit (Punctuality, ...). The visit's start date/time is not asked here - it is stamped with the
 * moment the user submits (see [StartVisitViewModel.startVisit]), not whenever the form was opened.
 *
 * Asks exactly the same questions offline as online - every list it offers is cached - and saves
 * the visit locally either way, so the form never depends on connectivity to be usable.
 */
@AndroidEntryPoint
class StartVisitFragment : BaseFragment() {
    private var _binding: FragmentStartVisitBinding? = null
    private val binding get() = _binding!!

    private val viewModel: StartVisitViewModel by viewModels()

    private val attributeDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentStartVisitBinding.inflate(inflater, container, false)

        requireActivity().title = getString(R.string.start_visit_form_title)
        setupObservers()

        return binding.root
    }

    private fun setupObservers() {
        viewModel.result.observe(viewLifecycleOwner, Observer { result ->
            when (result) {
                is Result.Loading -> showLoading(true)
                is Result.Success -> {
                    setupLocationSpinner()
                    setupServiceField()
                    setupVisitAttributeFields()
                    initListeners()
                    showLoading(false)
                }
                is Result.Error -> {
                    ToastUtil.error(getString(R.string.start_visit_form_error))
                    showLoading(false)
                }
                else -> Unit
            }
        })

        // The services on offer follow the chosen location, so the field is redrawn whenever the
        // location changes - dropping any selection the new location does not offer.
        viewModel.serviceProgramsUpdated.observe(viewLifecycleOwner, Observer { updateServiceFieldText() })
    }

    private fun setupLocationSpinner() = with(binding.visitLocationSpinner) {
        adapter = ArrayAdapter(requireActivity(), android.R.layout.simple_list_item_1, viewModel.locations.keys.toList())
        setSelection(viewModel.locationListPosition)
        onItemSelectedListener = object : OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                viewModel.selectLocation(position)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    /**
     * The Service field, the mobile equivalent of the web form's multi-select: tapping it opens a
     * multiple-choice dialog of the services offered at the chosen location.
     */
    private fun setupServiceField() {
        updateServiceFieldText()
        binding.visitServiceField.setOnClickListener { showServicePicker() }
    }

    private fun showServicePicker() {
        val programs = viewModel.servicePrograms
        if (programs.isEmpty()) {
            ToastUtil.notify(getString(R.string.visit_service_none_available))
            return
        }
        // A single service is auto-selected by the viewmodel (see
        // StartVisitViewModel.autoSelectSoleService) - nothing left to pick, so opening a
        // one-item checklist would just make the user tap what's already set.
        if (programs.size == 1) return

        val labels = programs.map { it.name.orEmpty() }.toTypedArray()
        val checked = programs.map { viewModel.selectedServiceUuids.contains(it.uuid) }.toBooleanArray()

        AlertDialog.Builder(requireContext(), R.style.AlertDialogTheme)
            .setTitle(R.string.visit_service_label)
            .setMultiChoiceItems(labels, checked) { _, which, isChecked -> checked[which] = isChecked }
            .setPositiveButton(R.string.dialog_button_done) { dialog, _ ->
                viewModel.selectedServiceUuids.clear()
                programs.forEachIndexed { index, program ->
                    if (checked[index]) program.uuid?.let { viewModel.selectedServiceUuids.add(it) }
                }
                updateServiceFieldText()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.dialog_button_cancel) { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun updateServiceFieldText() {
        val selected = viewModel.servicePrograms
            .filter { viewModel.selectedServiceUuids.contains(it.uuid) }
            .mapNotNull { it.name }
        binding.visitServiceField.text =
            if (selected.isEmpty()) getString(R.string.visit_service_hint) else selected.joinToString(", ")
    }

    /**
     * Builds one field per configured visit attribute type, choosing the control from the type's
     * datatype exactly as the web form's `VisitAttributeTypeFields` does.
     */
    private fun setupVisitAttributeFields() = with(binding.visitAttributesContainer) {
        removeAllViews()
        viewModel.visitAttributeTypes.forEach { type ->
            when (type.datatypeClassname) {
                VisitAttributeDatatypes.CONCEPT -> addView(buildCodedField(this, type))
                VisitAttributeDatatypes.BOOLEAN -> addView(buildBooleanField(this, type))
                VisitAttributeDatatypes.DATE -> addView(buildDateField(this, type))
                else -> addView(buildTextField(this, type))
            }
        }
    }

    private fun buildCodedField(container: ViewGroup, type: VisitAttributeType): View {
        val fieldBinding = ItemVisitAttributeSpinnerBinding.inflate(layoutInflater, container, false)
        fieldBinding.attributeLabel.text = labelFor(type)

        // A leading blank choice is what makes an optional coded field answerable with "nothing",
        // matching the web form's "Select an option" placeholder.
        val answers = type.answers
        val labels = listOf(getString(R.string.select_an_option)) + answers.map { it.display }
        fieldBinding.attributeSpinner.adapter =
            ArrayAdapter(requireActivity(), android.R.layout.simple_list_item_1, labels)
        fieldBinding.attributeSpinner.setSelection(
            answers.indexOfFirst { it.uuid == viewModel.attributeValues[type.uuid] }.let { if (it >= 0) it + 1 else 0 }
        )
        fieldBinding.attributeSpinner.onItemSelectedListener = object : OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val uuid = type.uuid ?: return
                if (position == 0) viewModel.attributeValues.remove(uuid)
                else viewModel.attributeValues[uuid] = answers[position - 1].uuid
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        return fieldBinding.root
    }

    private fun buildTextField(container: ViewGroup, type: VisitAttributeType): View {
        val fieldBinding = ItemVisitAttributeTextBinding.inflate(layoutInflater, container, false)
        fieldBinding.attributeLabel.text = labelFor(type)
        fieldBinding.attributeEditText.hint = type.display
        fieldBinding.attributeEditText.inputType = when (type.datatypeClassname) {
            VisitAttributeDatatypes.FLOAT -> InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            VisitAttributeDatatypes.LONG_FREE_TEXT -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            else -> InputType.TYPE_CLASS_TEXT
        }
        fieldBinding.attributeEditText.setText(viewModel.attributeValues[type.uuid].orEmpty())
        fieldBinding.attributeEditText.doAfterTextChanged { text ->
            val uuid = type.uuid ?: return@doAfterTextChanged
            val value = text?.toString().orEmpty()
            if (value.isEmpty()) viewModel.attributeValues.remove(uuid) else viewModel.attributeValues[uuid] = value
        }
        return fieldBinding.root
    }

    private fun buildDateField(container: ViewGroup, type: VisitAttributeType): View {
        val fieldBinding = ItemVisitAttributeTextBinding.inflate(layoutInflater, container, false)
        fieldBinding.attributeLabel.text = labelFor(type)
        fieldBinding.attributeEditText.hint = getString(R.string.date)
        with(fieldBinding.attributeEditText) {
            isFocusable = false
            isFocusableInTouchMode = false
            isCursorVisible = false
            setText(viewModel.attributeValues[type.uuid].orEmpty())
            setOnClickListener {
                showDatePicker(System.currentTimeMillis()) { picked ->
                    val uuid = type.uuid ?: return@showDatePicker
                    val value = attributeDateFormat.format(picked)
                    setText(value)
                    viewModel.attributeValues[uuid] = value
                }
            }
        }
        return fieldBinding.root
    }

    private fun buildBooleanField(container: ViewGroup, type: VisitAttributeType): View {
        val fieldBinding = ItemVisitAttributeCheckboxBinding.inflate(layoutInflater, container, false)
        fieldBinding.attributeCheckBox.text = labelFor(type)
        fieldBinding.attributeCheckBox.isChecked = viewModel.attributeValues[type.uuid] == true.toString()
        fieldBinding.attributeCheckBox.setOnCheckedChangeListener { _, isChecked ->
            val uuid = type.uuid ?: return@setOnCheckedChangeListener
            if (isChecked) viewModel.attributeValues[uuid] = true.toString()
            else viewModel.attributeValues.remove(uuid)
        }
        return fieldBinding.root
    }

    /** Marks optional fields as such, the way the web form's attribute labels do. */
    private fun labelFor(type: VisitAttributeType): String {
        val display = type.display?.trim().orEmpty().ifEmpty { type.name?.trim().orEmpty() }
        val isRequired = ApplicationConstants.VisitAttributeTypes.REQUIRED_ATTRIBUTE_TYPE_UUIDS.contains(type.uuid)
        return if (isRequired) display else getString(R.string.optional_field_label, display)
    }

    private fun initListeners() = with(binding) {
        submitButton.setOnClickListener { validateAndStartVisit() }
        cancelButton.setOnClickListener { requireActivity().finish() }
    }

    private fun validateAndStartVisit() {
        val missingRequiredAttribute = viewModel.visitAttributeTypes.firstOrNull { type ->
            ApplicationConstants.VisitAttributeTypes.REQUIRED_ATTRIBUTE_TYPE_UUIDS.contains(type.uuid) &&
                viewModel.attributeValues[type.uuid].isNullOrEmpty()
        }

        val error = when {
            viewModel.selectedLocation == null -> getString(R.string.warning_select_visit_location)
            // Required for a new visit in the web client too: a visit exists to deliver a service.
            viewModel.selectedServiceUuids.isEmpty() -> getString(R.string.warning_select_visit_service)
            missingRequiredAttribute != null ->
                getString(R.string.warning_visit_attribute_required, missingRequiredAttribute.display.orEmpty())
            else -> null
        }

        if (error != null) {
            ToastUtil.error(error)
            return
        }

        startVisit()
    }

    private fun startVisit() {
        showLoading(true)
        viewModel.startVisit().observeOnce(viewLifecycleOwner, Observer { visit ->
            showLoading(false)
            if (visit == null) {
                ToastUtil.error(getString(R.string.visit_start_error))
                return@Observer
            }
            if (visit.uuid.isNullOrEmpty()) ToastUtil.notify(getString(R.string.visit_saved_offline))
            goToVisitDashboard(visit)
        })
    }

    private fun goToVisitDashboard(visit: Visit) {
        val visitId = visit.id
        if (visitId == null) {
            requireActivity().finish()
            return
        }
        Intent(activity, VisitDashboardActivity::class.java).apply {
            putExtra(ApplicationConstants.BundleKeys.VISIT_ID, visitId)
            startActivity(this)
        }
        // The form has done its job; leaving it on the back stack would offer to start a second
        // visit for a patient who now has an active one.
        requireActivity().finish()
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(patientId: Long) = StartVisitFragment().apply {
            arguments = bundleOf(PATIENT_ID_BUNDLE to patientId)
        }
    }
}
