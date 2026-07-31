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
package org.openmrs.mobile.activities.drugsearch

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import com.openmrs.android_sdk.library.models.DrugSearchResult
import com.openmrs.android_sdk.utilities.ApplicationConstants.BundleKeys.PATIENT_ID_BUNDLE
import dagger.hilt.android.AndroidEntryPoint
import org.openmrs.mobile.R
import org.openmrs.mobile.activities.ACBaseActivity
import org.openmrs.mobile.activities.orderform.DrugOrderFormActivity
import org.openmrs.mobile.databinding.ActivityDrugSearchBinding
import org.openmrs.mobile.utilities.makeGone
import org.openmrs.mobile.utilities.makeVisible

/**
 * Drug search, mirroring O3's `drug-search`/`order-basket-search-results` workspace: search
 * as-you-type, "N results for "query"", a "Clear Results" link, and each result offering both a
 * quick "Add to basket" and a full "Order form".
 */
@AndroidEntryPoint
class DrugSearchActivity : ACBaseActivity() {
    private lateinit var binding: ActivityDrugSearchBinding

    private val viewModel: DrugSearchViewModel by viewModels()

    private lateinit var adapter: DrugSearchResultsRecyclerViewAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDrugSearchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.run {
            elevation = 0f
            title = getString(R.string.drug_search_title)
            setDisplayHomeAsUpEnabled(true)
        }

        setupAdapter()
        setupListeners()
    }

    private fun setupAdapter() {
        adapter = DrugSearchResultsRecyclerViewAdapter(
            emptyList(),
            onAddToBasket = { drug ->
                viewModel.addToBasket(drug)
                finish()
            },
            onOrderForm = { drug ->
                startActivity(DrugOrderFormActivity.newIntentForNewItem(this, viewModel.patientId, drug))
                finish()
            }
        )
        with(binding.drugSearchResultsRecyclerView) {
            layoutManager = LinearLayoutManager(this@DrugSearchActivity)
            adapter = this@DrugSearchActivity.adapter
        }
    }

    private fun setupListeners() = with(binding) {
        drugSearchEditText.doAfterTextChanged { text ->
            val query = text?.toString().orEmpty()
            viewModel.searchDrugs(query).observe(
                this@DrugSearchActivity,
                Observer { results -> showResults(query, results) }
            )
        }
        clearResultsButton.setOnClickListener {
            drugSearchEditText.text = null
            showResults("", emptyList())
        }
    }

    private fun showResults(query: String, results: List<DrugSearchResult>) = with(binding) {
        adapter.updateList(results)
        if (query.isEmpty()) {
            resultsHeaderRow.makeGone()
            emptyResultsLabel.makeGone()
        } else {
            resultsHeaderRow.makeVisible()
            resultsCountLabel.text = getString(R.string.drug_search_results_for, results.size, query)
            if (results.isEmpty()) emptyResultsLabel.makeVisible() else emptyResultsLabel.makeGone()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) finish()
        else super.onOptionsItemSelected(item)
        return true
    }

    companion object {
        fun newIntent(context: Context, patientId: Long) =
            Intent(context, DrugSearchActivity::class.java).apply {
                putExtra(PATIENT_ID_BUNDLE, patientId)
            }
    }
}
