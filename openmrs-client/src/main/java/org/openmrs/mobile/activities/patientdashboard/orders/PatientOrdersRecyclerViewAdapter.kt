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

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.RecyclerView
import com.openmrs.android_sdk.library.models.OrderGet
import com.openmrs.android_sdk.utilities.DateUtils
import org.openmrs.mobile.R
import org.openmrs.mobile.databinding.RowPatientOrderBinding

/**
 * Renders any [OrderGet] generically - used for both the Medications tab (drug orders) and the
 * Orders tab (lab orders), each with its own per-row overflow menu: lab orders get
 * "Modify order / Add result / Cancel order" (all three of [onModifyClick]/[onAddResultClick]/
 * [onCancelClick] supplied); drug orders get "Modify / Renew / Discontinue" (any of
 * [onModifyClick]/[onRenewClick]/[onDiscontinueClick] supplied, matching O3's real Medications
 * table which shows a different subset per bucket - see [PatientMedicationsFragment]). The
 * overflow icon only appears when at least one action callback is supplied.
 */
class PatientOrdersRecyclerViewAdapter(
    private var orders: List<OrderGet>,
    private val onModifyClick: ((OrderGet) -> Unit)? = null,
    private val onAddResultClick: ((OrderGet) -> Unit)? = null,
    private val onCancelClick: ((OrderGet) -> Unit)? = null,
    private val onRenewClick: ((OrderGet) -> Unit)? = null,
    private val onDiscontinueClick: ((OrderGet) -> Unit)? = null
) : RecyclerView.Adapter<PatientOrdersRecyclerViewAdapter.OrderViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OrderViewHolder {
        val binding = RowPatientOrderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return OrderViewHolder(binding)
    }

    override fun onBindViewHolder(holder: OrderViewHolder, position: Int) {
        holder.bind(orders[position], onModifyClick, onAddResultClick, onCancelClick, onRenewClick, onDiscontinueClick)
    }

    override fun getItemCount(): Int = orders.size

    fun updateList(newOrders: List<OrderGet>) {
        orders = newOrders
        notifyDataSetChanged()
    }

    class OrderViewHolder(private val binding: RowPatientOrderBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(
            order: OrderGet,
            onModifyClick: ((OrderGet) -> Unit)?,
            onAddResultClick: ((OrderGet) -> Unit)?,
            onCancelClick: ((OrderGet) -> Unit)?,
            onRenewClick: ((OrderGet) -> Unit)?,
            onDiscontinueClick: ((OrderGet) -> Unit)?
        ) {
            binding.orderConceptName.text = order.concept?.display ?: order.display ?: MISSING_VALUE_PLACEHOLDER
            binding.orderTypeLabel.text = order.orderType?.display ?: MISSING_VALUE_PLACEHOLDER
            binding.orderDate.text = order.dateActivated
                ?.let { DateUtils.convertTime1(it, DateUtils.DATE_WITH_TIME_FORMAT) }
                ?: MISSING_VALUE_PLACEHOLDER
            binding.orderOrderer.text = order.orderer?.display ?: MISSING_VALUE_PLACEHOLDER

            if (order.instructions.isNullOrBlank()) {
                binding.orderInstructions.visibility = View.GONE
            } else {
                binding.orderInstructions.visibility = View.VISIBLE
                binding.orderInstructions.text = order.instructions
            }

            val hasActions = onModifyClick != null || onAddResultClick != null || onCancelClick != null ||
                onRenewClick != null || onDiscontinueClick != null
            if (hasActions) {
                binding.orderActionsButton.visibility = View.VISIBLE
                binding.orderActionsButton.setOnClickListener {
                    showActionsMenu(order, onModifyClick, onAddResultClick, onCancelClick, onRenewClick, onDiscontinueClick)
                }
            } else {
                binding.orderActionsButton.visibility = View.GONE
                binding.orderActionsButton.setOnClickListener(null)
            }
        }

        private fun showActionsMenu(
            order: OrderGet,
            onModifyClick: ((OrderGet) -> Unit)?,
            onAddResultClick: ((OrderGet) -> Unit)?,
            onCancelClick: ((OrderGet) -> Unit)?,
            onRenewClick: ((OrderGet) -> Unit)?,
            onDiscontinueClick: ((OrderGet) -> Unit)?
        ) {
            val context = binding.orderActionsButton.context
            val resultsLabel = if (order.fulfillerStatus == "COMPLETED") {
                context.getString(R.string.order_action_edit_results)
            } else {
                context.getString(R.string.order_action_add_result)
            }
            val modifyLabel = context.getString(R.string.order_action_modify)
            val cancelLabel = context.getString(R.string.order_action_cancel)
            val renewLabel = context.getString(R.string.order_action_renew)
            val discontinueLabel = context.getString(R.string.order_action_discontinue)

            val actions = listOfNotNull(
                onModifyClick?.let { modifyLabel to it },
                onAddResultClick?.let { resultsLabel to it },
                onRenewClick?.let { renewLabel to it },
                onCancelClick?.let { cancelLabel to it },
                onDiscontinueClick?.let { discontinueLabel to it }
            )

            val popup = PopupMenu(context, binding.orderActionsButton)
            actions.forEach { (label, _) -> popup.menu.add(label) }
            popup.setOnMenuItemClickListener { item ->
                actions.firstOrNull { it.first == item.title }?.second?.invoke(order)
                true
            }
            popup.show()
        }

        companion object {
            private const val MISSING_VALUE_PLACEHOLDER = "——"
        }
    }
}
