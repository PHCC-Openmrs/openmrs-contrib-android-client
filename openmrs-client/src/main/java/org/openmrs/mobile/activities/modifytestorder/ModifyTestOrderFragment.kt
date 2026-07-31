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
package org.openmrs.mobile.activities.modifytestorder

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
import com.openmrs.android_sdk.library.models.OrderGet
import com.openmrs.android_sdk.library.models.Result
import com.openmrs.android_sdk.library.models.ResultType
import com.openmrs.android_sdk.utilities.ApplicationConstants.BundleKeys.ORDER_BUNDLE
import com.openmrs.android_sdk.utilities.ToastUtil
import dagger.hilt.android.AndroidEntryPoint
import org.openmrs.mobile.R
import org.openmrs.mobile.activities.BaseFragment
import org.openmrs.mobile.databinding.FragmentTestOrderFormBinding
import org.openmrs.mobile.utilities.observeOnce

/**
 * The "Modify order" form for a live lab order - reuses the same field layout as the new-order
 * `TestOrderFormFragment` (test name, priority, reference number, additional instructions), but
 * pre-fills from the existing order and submits a `REVISE` order directly to the network instead
 * of writing into the basket.
 */
@AndroidEntryPoint
class ModifyTestOrderFragment : BaseFragment() {
    private var _binding: FragmentTestOrderFormBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ModifyTestOrderViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTestOrderFormBinding.inflate(inflater, container, false)

        requireActivity().title = getString(R.string.modify_test_order_title)
        setupObserver()

        return binding.root
    }

    private fun setupObserver() {
        viewModel.result.observe(viewLifecycleOwner, Observer { result ->
            if (result is Result.Success) {
                showSelectedTest(viewModel.order)
                setupPrioritySpinner()
                initFieldValues()
                initListeners()
            }
        })
    }

    private fun showSelectedTest(order: OrderGet) = with(binding) {
        selectedTestLabel.text = order.concept?.display ?: order.display.orEmpty()
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private val PRIORITY_OPTIONS = listOf(
            "ROUTINE" to R.string.test_order_priority_routine,
            "STAT" to R.string.test_order_priority_stat
        )

        fun newInstance(order: OrderGet) = ModifyTestOrderFragment().apply {
            arguments = bundleOf(ORDER_BUNDLE to order)
        }
    }
}
