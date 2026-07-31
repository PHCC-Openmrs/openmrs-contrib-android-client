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
package org.openmrs.mobile.activities.modifydrugorder

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.AdapterView.OnItemSelectedListener
import android.widget.ArrayAdapter
import androidx.core.os.bundleOf
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import com.openmrs.android_sdk.library.models.ConceptRef
import com.openmrs.android_sdk.library.models.OrderGet
import com.openmrs.android_sdk.library.models.Result
import com.openmrs.android_sdk.library.models.ResultType
import com.openmrs.android_sdk.utilities.ApplicationConstants.BundleKeys.ORDER_BUNDLE
import com.openmrs.android_sdk.utilities.ToastUtil
import dagger.hilt.android.AndroidEntryPoint
import org.openmrs.mobile.R
import org.openmrs.mobile.activities.BaseFragment
import org.openmrs.mobile.databinding.FragmentDrugOrderFormBinding
import org.openmrs.mobile.utilities.makeGone
import org.openmrs.mobile.utilities.makeVisible
import org.openmrs.mobile.utilities.observeOnce

/**
 * The "Modify drug order" form - reuses the same field layout as the new-order
 * `DrugOrderFormFragment` (dose, route, frequency, as-needed, duration, quantity, refills,
 * instructions), but pre-fills from the live order's full details and submits a `REVISE` order
 * directly to the network instead of writing into the basket.
 */
@AndroidEntryPoint
class ModifyDrugOrderFragment : BaseFragment() {
    private var _binding: FragmentDrugOrderFormBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ModifyDrugOrderViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDrugOrderFormBinding.inflate(inflater, container, false)

        requireActivity().title = getString(R.string.modify_drug_order_title)
        setupObserver()

        return binding.root
    }

    private fun setupObserver() {
        viewModel.result.observe(viewLifecycleOwner, Observer { result ->
            when (result) {
                is Result.Loading -> showLoading(true)
                is Result.Success -> {
                    showSelectedDrug(viewModel.order)
                    setupConfigSpinners()
                    initFieldValues()
                    initListeners()
                    showLoading(false)
                }
                is Result.Error -> showLoading(false)
                else -> Unit
            }
        })
    }

    private fun showSelectedDrug(order: OrderGet) = with(binding.selectedDrugLabel) {
        text = order.concept?.display ?: order.display.orEmpty()
    }

    private fun setupConfigSpinners() = with(binding) {
        val config = viewModel.orderEntryConfig
        setupConceptSpinner(doseUnitsSpinner, config.drugDosingUnits, viewModel.doseUnitsUuid) { viewModel.doseUnitsUuid = it }
        setupConceptSpinner(routeSpinner, config.drugRoutes, viewModel.routeUuid) { viewModel.routeUuid = it }
        setupConceptSpinner(frequencySpinner, config.orderFrequencies, viewModel.frequencyUuid) { viewModel.frequencyUuid = it }
        setupConceptSpinner(durationUnitsSpinner, config.durationUnits, viewModel.durationUnitsUuid) { viewModel.durationUnitsUuid = it }
        setupConceptSpinner(quantityUnitsSpinner, config.drugDispensingUnits, viewModel.quantityUnitsUuid) { viewModel.quantityUnitsUuid = it }
    }

    private fun setupConceptSpinner(
        spinner: android.widget.Spinner,
        options: List<ConceptRef>,
        initialUuid: String?,
        onSelected: (String?) -> Unit
    ) {
        val labels = options.map { it.display.orEmpty() }
        spinner.adapter = ArrayAdapter(requireActivity(), android.R.layout.simple_list_item_1, labels)
        val initialIndex = options.indexOfFirst { it.uuid == initialUuid }
        if (initialIndex >= 0) spinner.setSelection(initialIndex)
        spinner.onItemSelectedListener = object : OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                onSelected(options.getOrNull(position)?.uuid)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun initFieldValues() = with(binding) {
        doseEditText.setText(viewModel.dose?.toString().orEmpty())
        durationEditText.setText(viewModel.duration?.toString().orEmpty())
        quantityEditText.setText(viewModel.quantity?.toString().orEmpty())
        numRefillsEditText.setText(viewModel.numRefills.toString())
        dosingInstructionsEditText.setText(viewModel.dosingInstructions)
        asNeededSwitch.isChecked = viewModel.asNeeded
    }

    private fun initListeners() = with(binding) {
        doseEditText.doAfterTextChanged { text -> viewModel.dose = text?.toString()?.toDoubleOrNull() }
        durationEditText.doAfterTextChanged { text -> viewModel.duration = text?.toString()?.toIntOrNull() }
        quantityEditText.doAfterTextChanged { text -> viewModel.quantity = text?.toString()?.toDoubleOrNull() }
        numRefillsEditText.doAfterTextChanged { text -> viewModel.numRefills = text?.toString()?.toIntOrNull() ?: 0 }
        dosingInstructionsEditText.doAfterTextChanged { text -> viewModel.dosingInstructions = text?.toString().orEmpty() }
        asNeededSwitch.setOnCheckedChangeListener { _, isChecked -> viewModel.asNeeded = isChecked }

        submitButton.setOnClickListener { submit() }
        cancelButton.setOnClickListener { requireActivity().finish() }
    }

    private fun submit() {
        viewModel.submit().observeOnce(viewLifecycleOwner, Observer { result ->
            when (result) {
                ResultType.OrderActionSuccess -> requireActivity().finish()
                else -> ToastUtil.error(getString(R.string.order_action_error))
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(order: OrderGet) = ModifyDrugOrderFragment().apply {
            arguments = bundleOf(ORDER_BUNDLE to order)
        }
    }
}
