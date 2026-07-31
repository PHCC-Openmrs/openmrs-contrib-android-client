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
package org.openmrs.mobile.activities.orderbasket

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.openmrs.android_sdk.library.models.DrugOrderBasketItem
import org.openmrs.mobile.databinding.RowDrugOrderBasketItemBinding
import org.openmrs.mobile.utilities.makeGone
import org.openmrs.mobile.utilities.makeVisible

class DrugOrderBasketRecyclerViewAdapter(
    private var items: List<DrugOrderBasketItem>,
    private val onItemClick: (DrugOrderBasketItem) -> Unit,
    private val onRemoveClick: (DrugOrderBasketItem) -> Unit
) : RecyclerView.Adapter<DrugOrderBasketRecyclerViewAdapter.BasketItemViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BasketItemViewHolder {
        val binding = RowDrugOrderBasketItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return BasketItemViewHolder(binding)
    }

    override fun onBindViewHolder(holder: BasketItemViewHolder, position: Int) {
        holder.bind(items[position], onItemClick, onRemoveClick)
    }

    override fun getItemCount(): Int = items.size

    fun updateList(newItems: List<DrugOrderBasketItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    class BasketItemViewHolder(private val binding: RowDrugOrderBasketItemBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(
            item: DrugOrderBasketItem,
            onItemClick: (DrugOrderBasketItem) -> Unit,
            onRemoveClick: (DrugOrderBasketItem) -> Unit
        ) = with(binding) {
            basketItemDrugName.text = listOfNotNull(item.drug.name ?: item.drug.display, item.drug.strength, item.drug.dosageForm?.display)
                .joinToString(" — ")
            incompleteChip.visibility = if (item.isOrderIncomplete) android.view.View.VISIBLE else android.view.View.GONE

            basketItemSummary.text = listOfNotNull(
                item.dose?.let { dose -> listOfNotNull(dose.toString(), item.doseUnitsDisplay).joinToString(" ") },
                item.routeDisplay,
                item.frequencyDisplay
            ).joinToString(" · ").ifEmpty { null }

            if (basketItemSummary.text.isNullOrEmpty()) basketItemSummary.makeGone() else basketItemSummary.makeVisible()

            root.setOnClickListener { onItemClick(item) }
            removeItemButton.setOnClickListener { onRemoveClick(item) }
        }
    }
}
