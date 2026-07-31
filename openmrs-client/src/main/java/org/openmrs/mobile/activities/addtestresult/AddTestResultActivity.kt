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
package org.openmrs.mobile.activities.addtestresult

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import com.openmrs.android_sdk.library.models.OrderGet
import com.openmrs.android_sdk.utilities.ApplicationConstants.BundleKeys.ORDER_BUNDLE
import dagger.hilt.android.AndroidEntryPoint
import org.openmrs.mobile.R
import org.openmrs.mobile.activities.ACBaseActivity

@AndroidEntryPoint
class AddTestResultActivity : ACBaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_test_result)

        supportActionBar?.run {
            elevation = 0f
            setDisplayHomeAsUpEnabled(true)
        }

        val order = intent.extras?.getSerializable(ORDER_BUNDLE) as? OrderGet ?: return

        var fragment = supportFragmentManager.findFragmentById(R.id.addTestResultFrame) as AddTestResultFragment?
        if (fragment == null) {
            fragment = AddTestResultFragment.newInstance(order)
        }
        if (!fragment.isActive) {
            addFragmentToActivity(supportFragmentManager, fragment, R.id.addTestResultFrame)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) finish()
        else super.onOptionsItemSelected(item)
        return true
    }

    companion object {
        fun newIntent(context: Context, order: OrderGet) =
            Intent(context, AddTestResultActivity::class.java).apply {
                putExtra(ORDER_BUNDLE, order)
            }
    }
}
