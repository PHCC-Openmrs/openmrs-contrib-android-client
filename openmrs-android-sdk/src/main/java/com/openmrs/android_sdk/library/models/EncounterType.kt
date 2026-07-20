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

import androidx.room.Entity

/**
 * Encounter type
 *
 * @constructor Create empty Encounter type
 */
@Entity(tableName = "encounterType")
class EncounterType : Resource {

    constructor(display: String?) {
        this.display = display
    }

    constructor(uuid: String?, display: String?, links: List<Link>){
        this.uuid = uuid
        this.display = display
        this.links = links
    }

    companion object {

        const val VITALS = "Vitals"
        const val VISIT_NOTE = "Visit Note"
        const val DISCHARGE = "Discharge"
        const val ADMISSION = "Admission"
        const val ATTACHMENT_UPLOAD = "Attachment Upload"
        const val CHECK_IN = "Check In"
        const val CHECK_OUT = "Check Out"
        const val TRANSFER = "Transfer"
        const val TRANSFER_REQUEST = "Transfer Request"
        const val WARD_ADMISSION = "Ward Admission"
        const val WARD_DISCHARGE = "Ward Discharge"
        const val ADULT_VISIT = "Adult Visit"
        const val BED_ASSIGNMENT = "Bed Assignment"
        const val CONSULTATION = "Consultation"
        const val IMMUNIZATIONS = "Immunizations"
        const val INPATIENT_NOTE = "Inpatient Note"
        const val INTRA_HOSPITAL_TRANSFER = "Intra-Hospital Transfer"
        const val LAB_RESULTS = "Lab Results"
        const val MENTAL_HEALTH_ASSESSMENT = "Mental Health Assessment"
        const val ORDER = "Order"
        const val SURGICAL_OPERATION = "Surgical Operation"
        const val REFERRAL = "Referral"
        const val COVID_19 = "Covid 19"
        const val OUTPATIENT_DEPT = "Outpatient Department"
        const val SOAP_NOTE = "SOAP Note"
        const val NCD_PATIENT_CARD = "NCD Patient Card"
    }
}
