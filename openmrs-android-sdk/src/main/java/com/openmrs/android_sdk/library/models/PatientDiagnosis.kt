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
 * A single entry of an encounter's `diagnoses` array, as returned by the `/patientdiagnoses`
 * resource (e.g. embedded in `GET /encounter/{uuid}?v=full`).
 */
class PatientDiagnosis : Serializable {

    @SerializedName("uuid")
    @Expose
    var uuid: String? = null

    @SerializedName("diagnosis")
    @Expose
    var diagnosis: DiagnosisValue? = null

    @SerializedName("certainty")
    @Expose
    var certainty: String? = null

    @SerializedName("rank")
    @Expose
    var rank: Int? = null

    @SerializedName("voided")
    @Expose
    var voided: Boolean? = null
}

class DiagnosisValue : Serializable {

    @SerializedName("coded")
    @Expose
    var coded: ConceptSearchResult? = null
}
