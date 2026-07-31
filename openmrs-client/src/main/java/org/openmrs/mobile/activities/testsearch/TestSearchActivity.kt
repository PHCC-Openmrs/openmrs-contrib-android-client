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

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import com.openmrs.android_sdk.library.models.TestSearchResult
import com.openmrs.android_sdk.utilities.ApplicationConstants.BundleKeys.PATIENT_ID_BUNDLE
import dagger.hilt.android.AndroidEntryPoint
import org.openmrs.mobile.R
import org.openmrs.mobile.activities.ACBaseActivity
import org.openmrs.mobile.activities.testorderform.TestOrderFormActivity
import org.openmrs.mobile.databinding.ActivityTestSearchBinding
import org.openmrs.mobile.utilities.makeGone
import org.openmrs.mobile.utilities.makeVisible

/**
 * Test search, mirroring [org.openmrs.mobile.activities.drugsearch.DrugSearchActivity] for lab
 * orders: search as-you-type, "N results for "query"", a "Clear Results" link, and each result
 * offering both a quick "Add to basket" and a full "Order form".
 */
@AndroidEntryPoint
class TestSearchActivity : ACBaseActivity() {
    private lateinit var binding: ActivityTestSearchBinding

    private val viewModel: TestSearchViewModel by viewModels()

    private lateinit var adapter: TestSearchResultsRecyclerViewAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTestSearchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.run {
            elevation = 0f
            title = getString(R.string.test_search_title)
            setDisplayHomeAsUpEnabled(true)
        }

        setupAdapter()
        setupListeners()
    }

    private fun setupAdapter() {
        adapter = TestSearchResultsRecyclerViewAdapter(
            emptyList(),
            onAddToBasket = { test ->
                viewModel.addToBasket(test)
                finish()
            },
            onOrderForm = { test ->
                startActivity(TestOrderFormActivity.newIntentForNewItem(this, viewModel.patientId, test))
                finish()
            }
        )
        with(binding.testSearchResultsRecyclerView) {
            layoutManager = LinearLayoutManager(this@TestSearchActivity)
            adapter = this@TestSearchActivity.adapter
        }
    }

    private fun setupListeners() = with(binding) {
        testSearchEditText.doAfterTextChanged { text ->
            val query = text?.toString().orEmpty()
            viewModel.searchTests(query).observe(
                this@TestSearchActivity,
                Observer { results -> showResults(query, results) }
            )
        }
        clearResultsButton.setOnClickListener {
            testSearchEditText.text = null
            showResults("", emptyList())
        }
    }

    private fun showResults(query: String, results: List<TestSearchResult>) = with(binding) {
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
            Intent(context, TestSearchActivity::class.java).apply {
                putExtra(PATIENT_ID_BUNDLE, patientId)
            }
    }
}
