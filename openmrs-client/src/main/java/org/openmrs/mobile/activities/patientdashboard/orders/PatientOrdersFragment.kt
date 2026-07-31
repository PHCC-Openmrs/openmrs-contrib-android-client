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
package org.openmrs.mobile.activities.patientdashboard.orders

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import com.openmrs.android_sdk.library.models.OperationType.PatientOrdersFetching
import com.openmrs.android_sdk.library.models.OrderGet
import com.openmrs.android_sdk.library.models.Result
import com.openmrs.android_sdk.library.models.ResultType
import com.openmrs.android_sdk.utilities.ApplicationConstants.BundleKeys.PATIENT_ID_BUNDLE
import com.openmrs.android_sdk.utilities.ApplicationConstants.Privileges.ADD_ORDERS
import com.openmrs.android_sdk.utilities.ToastUtil
import com.openmrs.android_sdk.utilities.ToastUtil.error
import dagger.hilt.android.AndroidEntryPoint
import org.openmrs.mobile.R
import org.openmrs.mobile.activities.BaseFragment
import org.openmrs.mobile.activities.addtestresult.AddTestResultActivity
import org.openmrs.mobile.activities.modifytestorder.ModifyTestOrderActivity
import org.openmrs.mobile.activities.orderbasket.OrderBasketActivity
import org.openmrs.mobile.databinding.FragmentPatientOrdersBinding
import org.openmrs.mobile.utilities.makeGone
import org.openmrs.mobile.utilities.makeVisible
import org.openmrs.mobile.utilities.observeOnce

/**
 * Mirrors O3's patient-chart Orders widget: the current lab/test orders active today, each with
 * Modify order / Add result / Cancel order actions (drug orders live on the separate Medications
 * tab instead, with no such actions).
 */
@AndroidEntryPoint
class PatientOrdersFragment : BaseFragment() {
    private var _binding: FragmentPatientOrdersBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PatientDashboardOrdersViewModel by viewModels()

    private lateinit var adapter: PatientOrdersRecyclerViewAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPatientOrdersBinding.inflate(inflater, null, false)

        setupAdapter()
        setupObserver()

        return binding.root
    }

    override fun onResume() {
        super.onResume()
        viewModel.fetchOrders()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        if (hasPrivilege(ADD_ORDERS)) {
            inflater.inflate(R.menu.patient_orders_tab_menu, menu)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.actionAddDrugOrder) {
            val patientId = requireArguments().getLong(PATIENT_ID_BUNDLE)
            startActivity(OrderBasketActivity.newIntent(requireContext(), patientId))
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun setupAdapter() {
        adapter = PatientOrdersRecyclerViewAdapter(
            emptyList(),
            onModifyClick = { order -> startActivity(ModifyTestOrderActivity.newIntent(requireContext(), order)) },
            onAddResultClick = { order -> startActivity(AddTestResultActivity.newIntent(requireContext(), order)) },
            onCancelClick = { order -> confirmCancel(order) }
        )
        with(binding.patientOrdersRecyclerView) {
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(activity)
            adapter = this@PatientOrdersFragment.adapter
        }
    }

    private fun confirmCancel(order: OrderGet) {
        AlertDialog.Builder(requireContext(), R.style.AlertDialogTheme)
            .setTitle(R.string.order_cancel_confirm_title)
            .setMessage(R.string.order_cancel_confirm_message)
            .setPositiveButton(R.string.mark_patient_deceased_proceed) { dialog, _ ->
                dialog.dismiss()
                cancelOrder(order)
            }
            .setNegativeButton(R.string.dialog_button_cancel) { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun cancelOrder(order: OrderGet) {
        viewModel.cancelOrder(order).observeOnce(viewLifecycleOwner, Observer { result ->
            when (result) {
                ResultType.OrderActionSuccess -> viewModel.fetchOrders()
                else -> ToastUtil.error(getString(R.string.order_action_error))
            }
        })
    }

    private fun setupObserver() {
        viewModel.result.observe(viewLifecycleOwner, Observer { result ->
            when (result) {
                is Result.Success -> if (result.operationType == PatientOrdersFetching) {
                    showOrdersList(result.data)
                }
                is Result.Error -> if (result.operationType == PatientOrdersFetching) {
                    error(getString(R.string.get_orders_error))
                    showOrdersList(emptyList())
                }
                else -> Unit
            }
        })
    }

    private fun showOrdersList(orders: List<OrderGet>) {
        adapter.updateList(orders)
        with(binding) {
            if (orders.isEmpty()) {
                patientOrdersRecyclerView.makeGone()
                emptyOrdersList.makeVisible()
            } else {
                patientOrdersRecyclerView.makeVisible()
                emptyOrdersList.makeGone()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(patientId: Long): PatientOrdersFragment {
            val fragment = PatientOrdersFragment()
            fragment.arguments = bundleOf(Pair(PATIENT_ID_BUNDLE, patientId))
            return fragment
        }
    }
}
