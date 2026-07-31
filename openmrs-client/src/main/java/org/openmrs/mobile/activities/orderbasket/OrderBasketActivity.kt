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

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import com.openmrs.android_sdk.library.models.DrugOrderBasketItem
import com.openmrs.android_sdk.library.models.ResultType
import com.openmrs.android_sdk.library.models.TestOrderBasketItem
import com.openmrs.android_sdk.utilities.ApplicationConstants.BundleKeys.PATIENT_ID_BUNDLE
import com.openmrs.android_sdk.utilities.ToastUtil
import dagger.hilt.android.AndroidEntryPoint
import org.openmrs.mobile.R
import org.openmrs.mobile.activities.ACBaseActivity
import org.openmrs.mobile.activities.drugsearch.DrugSearchActivity
import org.openmrs.mobile.activities.orderform.DrugOrderFormActivity
import org.openmrs.mobile.activities.testorderform.TestOrderFormActivity
import org.openmrs.mobile.activities.testsearch.TestSearchActivity
import org.openmrs.mobile.databinding.ActivityOrderBasketBinding
import org.openmrs.mobile.utilities.makeGone
import org.openmrs.mobile.utilities.makeVisible
import org.openmrs.mobile.utilities.observeOnce

/**
 * The order basket, mirroring O3's order basket workspace: a "Drug orders (N)" and "Lab orders (N)"
 * section, each expandable/collapsible, each item either complete or flagged "Incomplete", and a
 * "Sign and close" action that's only enabled once every item (across both sections) is complete.
 */
@AndroidEntryPoint
class OrderBasketActivity : ACBaseActivity() {
    private lateinit var binding: ActivityOrderBasketBinding

    private val viewModel: OrderBasketViewModel by viewModels()

    private lateinit var drugAdapter: DrugOrderBasketRecyclerViewAdapter
    private lateinit var labAdapter: TestOrderBasketRecyclerViewAdapter
    private var patientId: Long = 0
    private var isDrugOrdersExpanded = false
    private var isLabOrdersExpanded = false
    private var lastDrugItems: List<DrugOrderBasketItem> = emptyList()
    private var lastLabItems: List<TestOrderBasketItem> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOrderBasketBinding.inflate(layoutInflater)
        setContentView(binding.root)

        patientId = intent.extras?.getLong(PATIENT_ID_BUNDLE) ?: return

        supportActionBar?.run {
            elevation = 0f
            title = getString(R.string.order_basket_title)
            setDisplayHomeAsUpEnabled(true)
        }

        setupAdapters()
        setupListeners()
        setupObserver()
    }

    private fun setupAdapters() {
        drugAdapter = DrugOrderBasketRecyclerViewAdapter(
            emptyList(),
            onItemClick = { item ->
                startActivity(DrugOrderFormActivity.newIntentForExistingItem(this, patientId, item.id))
            },
            onRemoveClick = { item -> viewModel.removeDrugItem(item.id) }
        )
        with(binding.drugOrdersRecyclerView) {
            layoutManager = LinearLayoutManager(this@OrderBasketActivity)
            adapter = this@OrderBasketActivity.drugAdapter
        }

        labAdapter = TestOrderBasketRecyclerViewAdapter(
            emptyList(),
            onItemClick = { item ->
                startActivity(TestOrderFormActivity.newIntentForExistingItem(this, patientId, item.id))
            },
            onRemoveClick = { item -> viewModel.removeLabItem(item.id) }
        )
        with(binding.labOrdersRecyclerView) {
            layoutManager = LinearLayoutManager(this@OrderBasketActivity)
            adapter = this@OrderBasketActivity.labAdapter
        }
    }

    private fun setupListeners() = with(binding) {
        addDrugOrderLink.setOnClickListener {
            startActivity(DrugSearchActivity.newIntent(this@OrderBasketActivity, patientId))
        }
        addLabOrderLink.setOnClickListener {
            startActivity(TestSearchActivity.newIntent(this@OrderBasketActivity, patientId))
        }
        drugOrdersChevron.setOnClickListener {
            if (drugAdapter.itemCount > 0) toggleDrugOrdersExpanded()
        }
        labOrdersChevron.setOnClickListener {
            if (labAdapter.itemCount > 0) toggleLabOrdersExpanded()
        }
        cancelButton.setOnClickListener { finish() }
        signAndCloseButton.setOnClickListener { signAndClose() }
    }

    private fun setupObserver() {
        viewModel.drugOrders.observe(this, Observer { items -> showDrugOrders(items) })
        viewModel.labOrders.observe(this, Observer { items -> showLabOrders(items) })
    }

    private fun showDrugOrders(items: List<DrugOrderBasketItem>) = with(binding) {
        lastDrugItems = items
        drugAdapter.updateList(items)
        drugOrdersSectionTitle.text = getString(R.string.drug_orders_section, items.size)
        drugOrdersChevron.alpha = if (items.isEmpty()) 0.4f else 1f

        if (items.isEmpty() && isDrugOrdersExpanded) isDrugOrdersExpanded = false
        drugOrdersRecyclerView.visibility = if (isDrugOrdersExpanded) android.view.View.VISIBLE else android.view.View.GONE
        drugOrdersChevron.rotation = if (isDrugOrdersExpanded) 180f else 0f

        updateSignAndCloseState()
    }

    private fun showLabOrders(items: List<TestOrderBasketItem>) = with(binding) {
        lastLabItems = items
        labAdapter.updateList(items)
        labOrdersSectionTitle.text = getString(R.string.lab_orders_section, items.size)
        labOrdersChevron.alpha = if (items.isEmpty()) 0.4f else 1f

        if (items.isEmpty() && isLabOrdersExpanded) isLabOrdersExpanded = false
        labOrdersRecyclerView.visibility = if (isLabOrdersExpanded) android.view.View.VISIBLE else android.view.View.GONE
        labOrdersChevron.rotation = if (isLabOrdersExpanded) 180f else 0f

        updateSignAndCloseState()
    }

    private fun updateSignAndCloseState() = with(binding) {
        val hasItems = lastDrugItems.isNotEmpty() || lastLabItems.isNotEmpty()
        val allComplete = lastDrugItems.none { it.isOrderIncomplete } && lastLabItems.none { it.isOrderIncomplete }
        val canSignAndClose = hasItems && allComplete
        signAndCloseButton.isEnabled = canSignAndClose
        signAndCloseButton.alpha = if (canSignAndClose) 1f else 0.4f
    }

    private fun toggleDrugOrdersExpanded() {
        isDrugOrdersExpanded = !isDrugOrdersExpanded
        with(binding) {
            drugOrdersRecyclerView.visibility = if (isDrugOrdersExpanded) android.view.View.VISIBLE else android.view.View.GONE
            drugOrdersChevron.rotation = if (isDrugOrdersExpanded) 180f else 0f
        }
    }

    private fun toggleLabOrdersExpanded() {
        isLabOrdersExpanded = !isLabOrdersExpanded
        with(binding) {
            labOrdersRecyclerView.visibility = if (isLabOrdersExpanded) android.view.View.VISIBLE else android.view.View.GONE
            labOrdersChevron.rotation = if (isLabOrdersExpanded) 180f else 0f
        }
    }

    private fun signAndClose() {
        showLoading(true)
        viewModel.signAndClose().observeOnce(this, Observer { result ->
            showLoading(false)
            when (result) {
                ResultType.DrugOrderCreateSuccess -> finish()
                else -> ToastUtil.error(getString(R.string.order_basket_submit_error))
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

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) finish()
        else super.onOptionsItemSelected(item)
        return true
    }

    companion object {
        fun newIntent(context: Context, patientId: Long) =
            Intent(context, OrderBasketActivity::class.java).apply {
                putExtra(PATIENT_ID_BUNDLE, patientId)
            }
    }
}
