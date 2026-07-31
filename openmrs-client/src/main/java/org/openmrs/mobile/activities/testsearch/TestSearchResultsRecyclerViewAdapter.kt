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
package org.openmrs.mobile.activities.testsearch

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.openmrs.android_sdk.library.models.TestSearchResult
import org.openmrs.mobile.databinding.RowTestSearchResultBinding

class TestSearchResultsRecyclerViewAdapter(
    private var results: List<TestSearchResult>,
    private val onAddToBasket: (TestSearchResult) -> Unit,
    private val onOrderForm: (TestSearchResult) -> Unit
) : RecyclerView.Adapter<TestSearchResultsRecyclerViewAdapter.ResultViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ResultViewHolder {
        val binding = RowTestSearchResultBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ResultViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ResultViewHolder, position: Int) {
        holder.bind(results[position], onAddToBasket, onOrderForm)
    }

    override fun getItemCount(): Int = results.size

    fun updateList(newResults: List<TestSearchResult>) {
        results = newResults
        notifyDataSetChanged()
    }

    class ResultViewHolder(private val binding: RowTestSearchResultBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(result: TestSearchResult, onAddToBasket: (TestSearchResult) -> Unit, onOrderForm: (TestSearchResult) -> Unit) {
            binding.testResultName.text = result.display.orEmpty()
            binding.addToBasketButton.setOnClickListener { onAddToBasket(result) }
            binding.orderFormButton.setOnClickListener { onOrderForm(result) }
        }
    }
}
