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
package org.openmrs.mobile.activities.addtestresult

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
import com.openmrs.android_sdk.library.models.ConceptDetails
import com.openmrs.android_sdk.library.models.OrderGet
import com.openmrs.android_sdk.library.models.Result
import com.openmrs.android_sdk.library.models.ResultType
import com.openmrs.android_sdk.utilities.ApplicationConstants.BundleKeys.ORDER_BUNDLE
import com.openmrs.android_sdk.utilities.ToastUtil
import dagger.hilt.android.AndroidEntryPoint
import org.openmrs.mobile.R
import org.openmrs.mobile.activities.BaseFragment
import org.openmrs.mobile.databinding.FragmentAddTestResultBinding
import org.openmrs.mobile.utilities.makeGone
import org.openmrs.mobile.utilities.makeVisible
import org.openmrs.mobile.utilities.observeOnce

/**
 * The "Add result" (or "Edit results", once already completed) form for a lab order: renders a
 * numeric field, a coded-answer spinner, or a plain text fallback depending on the test concept's
 * datatype, matching a real captured 3-step O3 flow (fetch concept datatype, add the value as an
 * obs into the order's own encounter, mark the order's fulfiller status COMPLETED).
 */
@AndroidEntryPoint
class AddTestResultFragment : BaseFragment() {
    private var _binding: FragmentAddTestResultBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AddTestResultViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAddTestResultBinding.inflate(inflater, container, false)

        requireActivity().title = getString(R.string.add_test_result_title)
        setupObserver()

        return binding.root
    }

    private fun setupObserver() {
        viewModel.result.observe(viewLifecycleOwner, Observer { result ->
            when (result) {
                is Result.Loading -> showLoading(true)
                is Result.Success -> {
                    showLoading(false)
                    showSelectedTest(viewModel.order)
                    setupValueInput(result.data)
                    initListeners()
                }
                is Result.Error -> {
                    showLoading(false)
                    ToastUtil.error(getString(R.string.order_action_error))
                }
                else -> Unit
            }
        })
    }

    private fun showSelectedTest(order: OrderGet) = with(binding) {
        selectedTestLabel.text = order.concept?.display ?: order.display.orEmpty()
    }

    private fun setupValueInput(conceptDetails: ConceptDetails) = with(binding) {
        when (conceptDetails.datatype?.display) {
            ConceptDetails.DATATYPE_NUMERIC -> {
                numericValueEditText.makeVisible()
                numericValueEditText.doAfterTextChanged { text ->
                    viewModel.numericValue = text?.toString()?.toDoubleOrNull()
                }
            }
            ConceptDetails.DATATYPE_CODED -> {
                codedValueSpinner.makeVisible()
                val labels = conceptDetails.answers.map { it.display.orEmpty() }
                codedValueSpinner.adapter = ArrayAdapter(requireActivity(), android.R.layout.simple_list_item_1, labels)
                codedValueSpinner.onItemSelectedListener = object : OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                        viewModel.selectedAnswerUuid = conceptDetails.answers.getOrNull(position)?.uuid
                    }

                    override fun onNothingSelected(parent: AdapterView<*>?) = Unit
                }
                viewModel.selectedAnswerUuid = conceptDetails.answers.firstOrNull()?.uuid
            }
            else -> {
                textValueEditText.makeVisible()
                textValueEditText.doAfterTextChanged { text -> viewModel.textValue = text?.toString().orEmpty() }
            }
        }
    }

    private fun initListeners() = with(binding) {
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
        fun newInstance(order: OrderGet) = AddTestResultFragment().apply {
            arguments = bundleOf(ORDER_BUNDLE to order)
        }
    }
}
