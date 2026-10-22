/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */

package com.openmrs.android_sdk.library.models

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import java.io.Serializable
import java.util.*


/**
 * Question
 *
 * @constructor Create empty Question
 */
class Question : Serializable {

    @SerializedName("type")
    @Expose
    var type: String? = null

    @SerializedName("id")
    @Expose
    var id: String? = null

    @SerializedName("label")
    @Expose
    var label: String? = null

    @SerializedName("required")
    @Expose
    var isRequired: Boolean = false

    @SerializedName("questionOptions")
    @Expose
    var questionOptions: QuestionOptions? = null

    @SerializedName("questions")
    @Expose
    var questions: List<Question> = ArrayList()

    /**
     * Group Entry (see [Form.groupEntryForm]): true when this question is filled in once for the
     * whole group and the same answer is saved for every selected patient (e.g. session date).
     * Not part of the O3 schema - set in the Form Builder's JSON editor, ignored by the web form.
     */
    @SerializedName("groupShared")
    @Expose
    var isGroupShared: Boolean = false

    /**
     * Optional: which patient-record value auto-fills this question - one of "name", "age",
     * "gender", "nationalId", "phone", "governorate", "neighbourhood". When absent the app falls
     * back to recognising the field by its label.
     */
    @SerializedName("groupPatientField")
    @Expose
    var groupPatientField: String? = null

}
