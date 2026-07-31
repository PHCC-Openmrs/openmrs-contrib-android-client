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
 * Response shape of `GET concept/{uuid}?v=custom:(uuid,display,name,datatype,set,answers,
 * hiNormal,hiAbsolute,hiCritical,lowNormal,lowAbsolute,lowCritical,units,allowDecimal)`, verified
 * against a live O3 deployment (the "Add result" form's datatype/reference-range lookup).
 */
class ConceptDetails : Serializable {
    @SerializedName("uuid")
    @Expose
    var uuid: String? = null

    @SerializedName("display")
    @Expose
    var display: String? = null

    @SerializedName("datatype")
    @Expose
    var datatype: ConceptRef? = null

    @SerializedName("answers")
    @Expose
    var answers: List<ConceptRef> = emptyList()

    @SerializedName("hiNormal")
    @Expose
    var hiNormal: Double? = null

    @SerializedName("hiAbsolute")
    @Expose
    var hiAbsolute: Double? = null

    @SerializedName("hiCritical")
    @Expose
    var hiCritical: Double? = null

    @SerializedName("lowNormal")
    @Expose
    var lowNormal: Double? = null

    @SerializedName("lowAbsolute")
    @Expose
    var lowAbsolute: Double? = null

    @SerializedName("lowCritical")
    @Expose
    var lowCritical: Double? = null

    @SerializedName("units")
    @Expose
    var units: String? = null

    @SerializedName("allowDecimal")
    @Expose
    var allowDecimal: Boolean = false

    companion object {
        const val DATATYPE_NUMERIC = "Numeric"
        const val DATATYPE_CODED = "Coded"
    }
}
