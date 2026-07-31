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
package org.openmrs.mobile.activities.testorderform

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
import com.openmrs.android_sdk.library.models.Result
import com.openmrs.android_sdk.library.models.TestSearchResult
import com.openmrs.android_sdk.utilities.ApplicationConstants.BundleKeys.PATIENT_ID_BUNDLE
import com.openmrs.android_sdk.utilities.ApplicationConstants.BundleKeys.TEST_ORDER_BASKET_ITEM_ID_BUNDLE
import com.openmrs.android_sdk.utilities.ApplicationConstants.BundleKeys.TEST_SEARCH_RESULT_BUNDLE
import dagger.hilt.android.AndroidEntryPoint
import org.openmrs.mobile.R
import org.openmrs.mobile.activities.BaseFragment
import org.openmrs.mobile.databinding.FragmentTestOrderFormBinding

/**
 * The test order form: a read-only test name, a priority (Routine/Stat), an optional reference
 * number, and optional additional instructions - matching O3's `TestOrderForm` workspace, which
 * has no dose/route/frequency fields at all.
 */
@AndroidEntryPoint
class TestOrderFormFragment : BaseFragment() {
    private var _binding: FragmentTestOrderFormBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TestOrderFormViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTestOrderFormBinding.inflate(inflater, container, false)

        requireActivity().title = getString(R.string.new_test_order_title)
        setupObserver()

        return binding.root
    }

    private fun setupObserver() {
        viewModel.result.observe(viewLifecycleOwner, Observer { result ->
            when (result) {
                is Result.Success -> {
                    showSelectedTest(viewModel.selectedTest)
                    setupPrioritySpinner()
                    initFieldValues()
                    initListeners()
                }
                else -> Unit
            }
        })
    }

    private fun showSelectedTest(test: TestSearchResult) = with(binding) {
        selectedTestLabel.text = test.display.orEmpty()
    }

    private fun setupPrioritySpinner() = with(binding) {
        val labels = PRIORITY_OPTIONS.map { getString(it.second) }
        prioritySpinner.adapter = ArrayAdapter(requireActivity(), android.R.layout.simple_list_item_1, labels)
        val initialIndex = PRIORITY_OPTIONS.indexOfFirst { it.first == viewModel.urgency }
        if (initialIndex >= 0) prioritySpinner.setSelection(initialIndex)
        prioritySpinner.onItemSelectedListener = object : OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                viewModel.urgency = PRIORITY_OPTIONS[position].first
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun initFieldValues() = with(binding) {
        accessionNumberEditText.setText(viewModel.accessionNumber.orEmpty())
        instructionsEditText.setText(viewModel.instructions)
    }

    private fun initListeners() = with(binding) {
        accessionNumberEditText.doAfterTextChanged { text -> viewModel.accessionNumber = text?.toString() }
        instructionsEditText.doAfterTextChanged { text -> viewModel.instructions = text?.toString().orEmpty() }

        submitButton.setOnClickListener {
            viewModel.save()
            requireActivity().finish()
        }
        cancelButton.setOnClickListener { requireActivity().finish() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private val PRIORITY_OPTIONS = listOf(
            "ROUTINE" to R.string.test_order_priority_routine,
            "STAT" to R.string.test_order_priority_stat
        )

        fun newInstanceForNewItem(patientId: Long, test: TestSearchResult) = TestOrderFormFragment().apply {
            arguments = bundleOf(PATIENT_ID_BUNDLE to patientId, TEST_SEARCH_RESULT_BUNDLE to test)
        }

        fun newInstanceForExistingItem(patientId: Long, basketItemId: Long) = TestOrderFormFragment().apply {
            arguments = bundleOf(PATIENT_ID_BUNDLE to patientId, TEST_ORDER_BASKET_ITEM_ID_BUNDLE to basketItemId)
        }
    }
}
