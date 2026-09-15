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
package org.openmrs.mobile.activities.startvisit

import android.os.Bundle
import android.view.MenuItem
import com.openmrs.android_sdk.utilities.ApplicationConstants
import dagger.hilt.android.AndroidEntryPoint
import org.openmrs.mobile.R
import org.openmrs.mobile.activities.ACBaseActivity

/**
 * Hosts the "Start visit" form, the mobile counterpart of the web client's start-visit workspace.
 */
@AndroidEntryPoint
class StartVisitActivity : ACBaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_start_visit)

        supportActionBar?.run {
            elevation = 0f
            setDisplayHomeAsUpEnabled(true)
        }

        val patientId = intent.extras?.getLong(ApplicationConstants.BundleKeys.PATIENT_ID_BUNDLE) ?: return

        var fragment = supportFragmentManager.findFragmentById(R.id.startVisitFrame) as StartVisitFragment?
        if (fragment == null) {
            fragment = StartVisitFragment.newInstance(patientId)
        }
        if (!fragment.isActive) {
            addFragmentToActivity(supportFragmentManager, fragment, R.id.startVisitFrame)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) finish()
        else super.onOptionsItemSelected(item)
        return true
    }
}
