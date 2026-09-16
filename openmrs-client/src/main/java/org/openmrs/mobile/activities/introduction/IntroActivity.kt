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
package org.openmrs.mobile.activities.introduction

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.openmrs.android_sdk.library.OpenmrsAndroid
import com.github.appintro.AppIntro2
import com.github.appintro.AppIntroFragment
import org.openmrs.mobile.R
import org.openmrs.mobile.activities.dashboard.DashboardActivity

class IntroActivity : AppIntro2() {

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        applyBottomInset()

        addSlide(AppIntroFragment.newInstance(
                title = getString(R.string.intro_welcome),
                imageDrawable = R.drawable.openmrs_logo,
                titleColor = Color.BLACK,
                descriptionColor = Color.BLACK,
                backgroundColor = Color.WHITE
        ))
        addSlide(AppIntroFragment.newInstance(
                title = getString(R.string.intro_register),
                description = getString(R.string.intro_register_desc),
                imageDrawable = R.drawable.ico_registry,
                backgroundColor = Color.parseColor("#F8793B")
        ))
        addSlide(AppIntroFragment.newInstance(
                title = getString(R.string.intro_find),
                description = getString(R.string.intro_find_desc),
                imageDrawable = R.drawable.ico_search,
                backgroundColor = Color.parseColor("#009384")
        ))
        addSlide(AppIntroFragment.newInstance(
                title = getString(R.string.intro_monitor),
                description = getString(R.string.intro_monitor_desc),
                imageDrawable = R.drawable.ico_visits,
                backgroundColor = Color.parseColor("#F0A815")
        ))
        // Manage Providers slide removed - the Provider management feature is already hidden
        // from the dashboard, so an onboarding page advertising it would be misleading.
        addSlide(AppIntroFragment.newInstance(
                title = getString(R.string.intro_location),
                description = getString(R.string.intro_location_desc),
                imageDrawable = R.drawable.ic_location_big,
                backgroundColor = Color.parseColor("#009384")
        ))
        addSlide(AppIntroFragment.newInstance(
                title = getString(R.string.intro_settings),
                description = getString(R.string.intro_settings_desc),
                imageDrawable = R.drawable.ic_settings_big,
                backgroundColor = Color.parseColor("#F0A815")
        ))

        if (!OpenmrsAndroid.getFirstTime()) {
            startActivity(Intent(this, DashboardActivity::class.java))
            finish()
        }
    }

    /**
     * AppIntro's own bottom bar (back/next/done buttons, page indicator dots) is pinned to the
     * literal bottom of its root layout with fitsSystemWindows="false" and no inset handling of
     * its own - under edge-to-edge that root extends behind the system navigation bar, so those
     * buttons render partially hidden underneath it. Pads the content view's bottom (and
     * left/right, for a side cutout in landscape) by the system bars' inset to push everything
     * back above it - the same fix ACBaseActivity applies for its own screens, which
     * IntroActivity can't inherit since it extends AppIntro2, not ACBaseActivity. Deliberately
     * leaves top padding untouched: AppIntro already pads each slide's own top correctly via a
     * fixed status-bar-height dimension, so padding here too would double it up.
     */
    private fun applyBottomInset() {
        val decor = window.decorView
        ViewCompat.setOnApplyWindowInsetsListener(decor) { _, windowInsets ->
            val content = findViewById<View>(android.R.id.content)
            if (content != null) {
                val bars = windowInsets.getInsets(
                        WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
                content.setPadding(bars.left, content.paddingTop, bars.right, bars.bottom)
            }
            windowInsets
        }
        decor.post { ViewCompat.requestApplyInsets(decor) }
    }

    override fun onSkipPressed(currentFragment: Fragment?) {
        super.onSkipPressed(currentFragment)
        startActivity(Intent(this, DashboardActivity::class.java))
        OpenmrsAndroid.setUserFirstTime(false)
        finish()
    }

    override fun onDonePressed(currentFragment: Fragment?) {
        super.onDonePressed(currentFragment)
        startActivity(Intent(this, DashboardActivity::class.java))
        OpenmrsAndroid.setUserFirstTime(false)
        finish()
    }
}