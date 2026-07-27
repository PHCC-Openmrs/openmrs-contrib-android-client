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
package org.openmrs.mobile.activities.patientdashboard

import android.content.Context
import android.util.SparseArray
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import com.openmrs.android_sdk.utilities.ApplicationConstants.Privileges.GET_ALLERGIES
import com.openmrs.android_sdk.utilities.ApplicationConstants.Privileges.GET_DIAGNOSES
import com.openmrs.android_sdk.utilities.ApplicationConstants.Privileges.GET_ENCOUNTERS
import com.openmrs.android_sdk.utilities.ApplicationConstants.Privileges.GET_OBSERVATIONS
import com.openmrs.android_sdk.utilities.ApplicationConstants.Privileges.GET_VISITS
import org.openmrs.mobile.R
import org.openmrs.mobile.activities.patientdashboard.allergy.PatientAllergyFragment
import org.openmrs.mobile.activities.patientdashboard.charts.PatientChartsFragment
import org.openmrs.mobile.activities.patientdashboard.details.PatientDetailsFragment
import org.openmrs.mobile.activities.patientdashboard.diagnosis.PatientDiagnosisFragment
import org.openmrs.mobile.activities.patientdashboard.visits.PatientVisitsFragment
import org.openmrs.mobile.activities.patientdashboard.vitals.PatientVitalsFragment
import org.openmrs.mobile.utilities.PrivilegeUtils

class PatientDashboardPagerAdapter(private val fm: FragmentManager,
                                   private val context: Context,
                                   private val mPatientId: Long
) : FragmentPagerAdapter(fm) {

    enum class TabType { DETAILS, ALLERGY, DIAGNOSIS, VISITS, VITALS, CHARTS }

    private data class TabSpec(val type: TabType, val titleRes: Int)

    private val registeredFragments = SparseArray<Fragment>()

    /**
     * Tabs the current user's role has privilege to see, in display order. Details is always
     * included since reaching this screen already required Get Patients. Fails open: while
     * privilege data hasn't been fetched yet, every tab is included exactly as before this check
     * existed.
     */
    private val visibleTabs: List<TabSpec> = buildList {
        add(TabSpec(TabType.DETAILS, R.string.patient_scroll_tab_details_label))
        if (PrivilegeUtils.hasPrivilege(GET_ALLERGIES)) {
            add(TabSpec(TabType.ALLERGY, R.string.patient_scroll_tab_allergy_label))
        }
        if (PrivilegeUtils.hasPrivilege(GET_DIAGNOSES)) {
            add(TabSpec(TabType.DIAGNOSIS, R.string.patient_scroll_tab_diagnosis_label))
        }
        if (PrivilegeUtils.hasPrivilege(GET_VISITS)) {
            add(TabSpec(TabType.VISITS, R.string.patient_scroll_tab_visits_label))
        }
        if (PrivilegeUtils.hasPrivilege(GET_OBSERVATIONS)) {
            add(TabSpec(TabType.VITALS, R.string.patient_scroll_tab_vitals_label))
        }
        if (PrivilegeUtils.hasAnyPrivilege(GET_ENCOUNTERS, GET_OBSERVATIONS)) {
            add(TabSpec(TabType.CHARTS, R.string.patient_scroll_tab_charts_label))
        }
    }

    /** Identifies which tab is at [position] without relying on a fixed numeric index. */
    fun tabTypeAt(position: Int): TabType = visibleTabs[position].type

    override fun getItem(i: Int): Fragment {
        return when (visibleTabs[i].type) {
            TabType.DETAILS -> PatientDetailsFragment.newInstance(mPatientId)
            TabType.ALLERGY -> PatientAllergyFragment.newInstance(mPatientId)
            TabType.DIAGNOSIS -> PatientDiagnosisFragment.newInstance(mPatientId)
            TabType.VISITS -> PatientVisitsFragment.newInstance(mPatientId)
            TabType.VITALS -> PatientVitalsFragment.newInstance(mPatientId)
            TabType.CHARTS -> PatientChartsFragment.newInstance(mPatientId)
        }
    }

    override fun getPageTitle(position: Int): CharSequence = context.getString(visibleTabs[position].titleRes)

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        val fragment = super.instantiateItem(container, position) as Fragment
        registeredFragments.put(position, fragment)
        return fragment
    }

    override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
        registeredFragments.remove(position)
        super.destroyItem(container, position, `object`)
    }

    override fun getCount(): Int = visibleTabs.size
}
