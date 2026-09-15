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

package com.openmrs.android_sdk.library.models

import androidx.room.Entity
import com.google.gson.annotations.Expose
import com.openmrs.android_sdk.library.databases.entities.LocationEntity
import com.openmrs.android_sdk.utilities.ApplicationConstants

/**
 * Visit
 *
 * <p> More on Visits https://rest.openmrs.org/#visits </p>
 * @constructor Create empty Visit
 */
@Entity
class Visit : Resource() {

    override var id: Long? = null

    @Expose
    lateinit var patient: Patient

    @Expose
    lateinit var visitType: VisitType

    @Expose
    lateinit var location: LocationEntity

    @Expose
    lateinit var startDatetime: String

    @Expose
    var stopDatetime: String? = null

    // Not lateinit: a Visit built locally rather than deserialized from a server response (the
    // offline-first "start visit" flow) never has this set, and VisitDAO reads it unconditionally
    // when saving - a lateinit var would throw there instead of just seeing an empty list.
    @Expose
    var encounters: List<Encounter> = emptyList()

    // The visit's extra questions - which service(s) it is for, punctuality, ... Left null rather
    // than empty when there are none, so ResourceSerializer omits the key entirely: the server
    // rejects an "attributes" key on a visit *edit* (its maxOccurs check runs even for an empty
    // list), while a visit *create* takes them inline, atomically with the visit itself.
    @Expose
    var attributes: List<VisitAttribute>? = null

    fun isActiveVisit() = stopDatetime.isNullOrEmpty()

    /**
     * The visit's patient, or null when this Visit was built without one - reading [patient]
     * directly would throw there, since it is lateinit.
     */
    fun patientOrNull(): Patient? = if (this::patient.isInitialized) patient else null

    /**
     * The uuids of the programs ("services") this visit is for, as stored under the single Service
     * attribute - the attribute type allows only one occurrence, so several services share one
     * comma-separated value. Empty for a visit predating the Service field, or created elsewhere.
     */
    fun serviceProgramUuids(): List<String> {
        val value = attributes
            ?.firstOrNull { it.attributeType == ApplicationConstants.VisitAttributeTypes.SERVICE_UUID }
            ?.value
        return value.orEmpty()
            .split(ApplicationConstants.VisitAttributeTypes.SERVICE_VALUE_SEPARATOR)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }
}
