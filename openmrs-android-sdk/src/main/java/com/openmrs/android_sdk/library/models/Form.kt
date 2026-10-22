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

/**
 * Form
 *
 * <p> More about Forms https://rest.openmrs.org/#forms </p>
 * @constructor Create empty Form
 */
class Form : Resource(), Serializable {


    @SerializedName("name")
    @Expose
    var name: String? = null


    @SerializedName("processor")
    @Expose
    var processor: String? = null


    @SerializedName("pages")
    @Expose
    var pages: List<Page> = ArrayList()

    @SerializedName("valueReference")
    @Expose
    var valueReference: String? = null

    /**
     * Group Entry settings: `"groupEntryForm": { "enabled": true }` lets this form be filled in
     * once for many patients with an active visit. Not part of the O3 schema - set in the Form
     * Builder's JSON editor, ignored by the web form. Null for every other form.
     */
    @SerializedName("groupEntryForm")
    @Expose
    var groupEntryForm: GroupEntryFormConfig? = null

    val isGroupEntryEnabled: Boolean get() = groupEntryForm?.isEnabled == true

}

/** The form-level `"groupEntryForm"` block - see [Form.groupEntryForm]. */
class GroupEntryFormConfig : Serializable {

    @SerializedName("enabled")
    @Expose
    var isEnabled: Boolean = false

}
