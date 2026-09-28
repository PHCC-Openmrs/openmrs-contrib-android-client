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
package org.openmrs.mobile.utilities

import android.content.Context
import com.openmrs.android_sdk.utilities.ApplicationConstants.PatientStatusAnswers
import org.openmrs.mobile.R

/**
 * Converts between the Patient Status labels shown to the user and the concept uuids the server
 * stores. The labels are translated (R.array.patient_status_options), so the conversion goes by
 * position in that array rather than by matching English text.
 */
object PatientStatusLabels {

    /** In the same order as R.array.patient_status_options. */
    private val UUIDS = listOf(PatientStatusAnswers.RESIDENT_UUID, PatientStatusAnswers.IDP_UUID)

    /** The label for a saved status, in the app's language; null for an unknown/blank uuid. */
    @JvmStatic
    fun labelFor(context: Context, uuid: String?): String? {
        val index = UUIDS.indexOf(uuid)
        return if (index >= 0) labels(context).getOrNull(index) else null
    }

    /**
     * The uuid for a selected label; an empty string for a blank/unrecognised one. The English
     * labels are accepted too, e.g. text still in the field from before a language change.
     */
    @JvmStatic
    fun uuidFor(context: Context, label: String?): String {
        val text = label?.trim() ?: return ""
        val index = labels(context).indexOf(text)
        if (index >= 0) return UUIDS.getOrElse(index) { "" }
        return when (text) {
            PatientStatusAnswers.RESIDENT_LABEL -> PatientStatusAnswers.RESIDENT_UUID
            PatientStatusAnswers.IDP_LABEL -> PatientStatusAnswers.IDP_UUID
            else -> ""
        }
    }

    private fun labels(context: Context): List<String> =
            context.resources.getStringArray(R.array.patient_status_options).map { it.trim() }
}
