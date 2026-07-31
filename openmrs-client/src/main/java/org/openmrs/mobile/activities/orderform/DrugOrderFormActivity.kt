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
package org.openmrs.mobile.activities.orderform

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import com.openmrs.android_sdk.library.models.DrugSearchResult
import com.openmrs.android_sdk.utilities.ApplicationConstants.BundleKeys.DRUG_ORDER_BASKET_ITEM_ID_BUNDLE
import com.openmrs.android_sdk.utilities.ApplicationConstants.BundleKeys.DRUG_SEARCH_RESULT_BUNDLE
import com.openmrs.android_sdk.utilities.ApplicationConstants.BundleKeys.PATIENT_ID_BUNDLE
import dagger.hilt.android.AndroidEntryPoint
import org.openmrs.mobile.R
import org.openmrs.mobile.activities.ACBaseActivity
import org.openmrs.mobile.activities.orderform.DrugOrderFormViewModel.Companion.NO_BASKET_ITEM_ID

@AndroidEntryPoint
class DrugOrderFormActivity : ACBaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_drug_order_form)

        supportActionBar?.run {
            elevation = 0f
            setDisplayHomeAsUpEnabled(true)
        }

        val patientId = intent.extras?.getLong(PATIENT_ID_BUNDLE) ?: return
        val basketItemId = intent.extras?.getLong(DRUG_ORDER_BASKET_ITEM_ID_BUNDLE, NO_BASKET_ITEM_ID) ?: NO_BASKET_ITEM_ID

        var fragment = supportFragmentManager.findFragmentById(R.id.drugOrderFormFrame) as DrugOrderFormFragment?
        if (fragment == null) {
            fragment = if (basketItemId != NO_BASKET_ITEM_ID) {
                DrugOrderFormFragment.newInstanceForExistingItem(patientId, basketItemId)
            } else {
                val drug = intent.extras?.getSerializable(DRUG_SEARCH_RESULT_BUNDLE) as? DrugSearchResult ?: return
                DrugOrderFormFragment.newInstanceForNewItem(patientId, drug)
            }
        }
        if (!fragment.isActive) {
            addFragmentToActivity(supportFragmentManager, fragment, R.id.drugOrderFormFrame)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) finish()
        else super.onOptionsItemSelected(item)
        return true
    }

    companion object {
        fun newIntentForNewItem(context: Context, patientId: Long, drug: DrugSearchResult) =
            Intent(context, DrugOrderFormActivity::class.java).apply {
                putExtra(PATIENT_ID_BUNDLE, patientId)
                putExtra(DRUG_SEARCH_RESULT_BUNDLE, drug)
            }

        fun newIntentForExistingItem(context: Context, patientId: Long, basketItemId: Long) =
            Intent(context, DrugOrderFormActivity::class.java).apply {
                putExtra(PATIENT_ID_BUNDLE, patientId)
                putExtra(DRUG_ORDER_BASKET_ITEM_ID_BUNDLE, basketItemId)
            }
    }
}
