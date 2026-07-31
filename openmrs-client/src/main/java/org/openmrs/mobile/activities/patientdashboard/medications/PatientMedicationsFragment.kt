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
package org.openmrs.mobile.activities.patientdashboard.medications

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
import androidx.recyclerview.widget.RecyclerView
import com.openmrs.android_sdk.library.models.MedicationOrderBuckets
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
import org.openmrs.mobile.activities.modifydrugorder.ModifyDrugOrderActivity
import org.openmrs.mobile.activities.orderbasket.OrderBasketActivity
import org.openmrs.mobile.activities.patientdashboard.orders.PatientOrdersRecyclerViewAdapter
import org.openmrs.mobile.databinding.FragmentPatientMedicationsBinding
import org.openmrs.mobile.utilities.makeGone
import org.openmrs.mobile.utilities.makeVisible
import org.openmrs.mobile.utilities.observeOnce

/**
 * Mirrors O3's Medications widget: every non-discontinued drug order, split into Active/Upcoming/
 * Past sections (see [PatientMedicationsViewModel]/`bucketMedicationOrders`), each with its own
 * per-row action subset matching O3's real table (active-medications/past-medications/
 * future-medications .component.tsx): Active shows Modify + Renew + Discontinue, Upcoming shows
 * Modify + Discontinue (no Renew), Past shows Renew only (no Modify/Discontinue - a stopped order
 * can't be revised or discontinued again).
 */
@AndroidEntryPoint
class PatientMedicationsFragment : BaseFragment() {
    private var _binding: FragmentPatientMedicationsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PatientMedicationsViewModel by viewModels()

    private lateinit var activeAdapter: PatientOrdersRecyclerViewAdapter
    private lateinit var upcomingAdapter: PatientOrdersRecyclerViewAdapter
    private lateinit var pastAdapter: PatientOrdersRecyclerViewAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPatientMedicationsBinding.inflate(inflater, null, false)

        setupAdapters()
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

    private fun setupAdapters() {
        activeAdapter = PatientOrdersRecyclerViewAdapter(
            emptyList(),
            onModifyClick = { order -> openModify(order) },
            onRenewClick = { order -> confirmRenew(order) },
            onDiscontinueClick = { order -> confirmDiscontinue(order) }
        )
        upcomingAdapter = PatientOrdersRecyclerViewAdapter(
            emptyList(),
            onModifyClick = { order -> openModify(order) },
            onDiscontinueClick = { order -> confirmDiscontinue(order) }
        )
        pastAdapter = PatientOrdersRecyclerViewAdapter(
            emptyList(),
            onRenewClick = { order -> confirmRenew(order) }
        )

        setupRecyclerView(binding.activeMedicationsRecyclerView, activeAdapter)
        setupRecyclerView(binding.upcomingMedicationsRecyclerView, upcomingAdapter)
        setupRecyclerView(binding.pastMedicationsRecyclerView, pastAdapter)
    }

    private fun setupRecyclerView(recyclerView: RecyclerView, adapter: PatientOrdersRecyclerViewAdapter) {
        recyclerView.layoutManager = LinearLayoutManager(activity)
        recyclerView.adapter = adapter
    }

    private fun openModify(order: OrderGet) {
        startActivity(ModifyDrugOrderActivity.newIntent(requireContext(), order))
    }

    private fun confirmRenew(order: OrderGet) {
        AlertDialog.Builder(requireContext(), R.style.AlertDialogTheme)
            .setTitle(R.string.drug_order_renew_confirm_title)
            .setMessage(R.string.drug_order_renew_confirm_message)
            .setPositiveButton(R.string.mark_patient_deceased_proceed) { dialog, _ ->
                dialog.dismiss()
                renewOrder(order)
            }
            .setNegativeButton(R.string.dialog_button_cancel) { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun confirmDiscontinue(order: OrderGet) {
        AlertDialog.Builder(requireContext(), R.style.AlertDialogTheme)
            .setTitle(R.string.drug_order_discontinue_confirm_title)
            .setMessage(R.string.drug_order_discontinue_confirm_message)
            .setPositiveButton(R.string.mark_patient_deceased_proceed) { dialog, _ ->
                dialog.dismiss()
                discontinueOrder(order)
            }
            .setNegativeButton(R.string.dialog_button_cancel) { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun renewOrder(order: OrderGet) {
        viewModel.renewOrder(order).observeOnce(viewLifecycleOwner, Observer { result ->
            when (result) {
                ResultType.OrderActionSuccess -> viewModel.fetchOrders()
                else -> ToastUtil.error(getString(R.string.order_action_error))
            }
        })
    }

    private fun discontinueOrder(order: OrderGet) {
        viewModel.discontinueOrder(order).observeOnce(viewLifecycleOwner, Observer { result ->
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
                    showBuckets(result.data)
                }
                is Result.Error -> if (result.operationType == PatientOrdersFetching) {
                    error(getString(R.string.get_orders_error))
                    showBuckets(MedicationOrderBuckets(emptyList(), emptyList(), emptyList()))
                }
                else -> Unit
            }
        })
    }

    private fun showBuckets(buckets: MedicationOrderBuckets) {
        showSection(activeAdapter, binding.activeMedicationsRecyclerView, binding.emptyActiveMedications, buckets.activeOrders)
        showSection(upcomingAdapter, binding.upcomingMedicationsRecyclerView, binding.emptyUpcomingMedications, buckets.upcomingOrders)
        showSection(pastAdapter, binding.pastMedicationsRecyclerView, binding.emptyPastMedications, buckets.pastOrders)
    }

    private fun showSection(
        adapter: PatientOrdersRecyclerViewAdapter,
        recyclerView: RecyclerView,
        emptyView: View,
        orders: List<OrderGet>
    ) {
        adapter.updateList(orders)
        if (orders.isEmpty()) {
            recyclerView.makeGone()
            emptyView.makeVisible()
        } else {
            recyclerView.makeVisible()
            emptyView.makeGone()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(patientId: Long): PatientMedicationsFragment {
            val fragment = PatientMedicationsFragment()
            fragment.arguments = bundleOf(Pair(PATIENT_ID_BUNDLE, patientId))
            return fragment
        }
    }
}
