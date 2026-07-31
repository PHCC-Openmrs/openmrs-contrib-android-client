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
import com.openmrs.android_sdk.library.models.TestOrderBasketItem
import org.openmrs.mobile.R
import org.openmrs.mobile.databinding.RowTestOrderBasketItemBinding
import org.openmrs.mobile.utilities.makeVisible

class TestOrderBasketRecyclerViewAdapter(
    private var items: List<TestOrderBasketItem>,
    private val onItemClick: (TestOrderBasketItem) -> Unit,
    private val onRemoveClick: (TestOrderBasketItem) -> Unit
) : RecyclerView.Adapter<TestOrderBasketRecyclerViewAdapter.BasketItemViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BasketItemViewHolder {
        val binding = RowTestOrderBasketItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return BasketItemViewHolder(binding)
    }

    override fun onBindViewHolder(holder: BasketItemViewHolder, position: Int) {
        holder.bind(items[position], onItemClick, onRemoveClick)
    }

    override fun getItemCount(): Int = items.size

    fun updateList(newItems: List<TestOrderBasketItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    class BasketItemViewHolder(private val binding: RowTestOrderBasketItemBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(
            item: TestOrderBasketItem,
            onItemClick: (TestOrderBasketItem) -> Unit,
            onRemoveClick: (TestOrderBasketItem) -> Unit
        ) = with(binding) {
            basketItemTestName.text = item.concept.display.orEmpty()
            incompleteChip.visibility = if (item.isOrderIncomplete) android.view.View.VISIBLE else android.view.View.GONE

            val priorityLabel = when (item.urgency) {
                "STAT" -> binding.root.context.getString(R.string.test_order_priority_stat)
                else -> binding.root.context.getString(R.string.test_order_priority_routine)
            }
            basketItemSummary.text = listOfNotNull(priorityLabel, item.accessionNumber).joinToString(" · ")
            basketItemSummary.makeVisible()

            root.setOnClickListener { onItemClick(item) }
            removeItemButton.setOnClickListener { onRemoveClick(item) }
        }
    }
}
