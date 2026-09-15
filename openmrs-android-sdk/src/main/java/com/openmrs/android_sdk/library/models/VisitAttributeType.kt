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

/**
 * A visit attribute type - the definition of one extra question the start-visit form asks about a
 * visit (e.g. "Punctuality"), together with the datatype that decides how it is rendered.
 *
 * <p> More on visit attribute types https://rest.openmrs.org/#visit-attribute-types </p>
 *
 * @constructor Create empty Visit attribute type
 */
class VisitAttributeType : Resource() {

    @Expose
    var name: String? = null

    /**
     * Fully-qualified name of the OpenMRS custom datatype backing this attribute type, e.g.
     * `org.openmrs.customdatatype.datatype.ConceptDatatype`. See
     * [com.openmrs.android_sdk.utilities.ApplicationConstants.VisitAttributeDatatypes].
     */
    @Expose
    var datatypeClassname: String? = null

    /**
     * Configuration for [datatypeClassname]. For a concept datatype this is the uuid of the
     * concept whose answers are the allowed values.
     */
    @Expose
    var datatypeConfig: String? = null

    /**
     * The allowed values for a coded (concept) attribute type. Not part of the visit attribute
     * type resource itself - filled in separately from the concept named by [datatypeConfig], and
     * cached alongside the type so the start-visit form can offer them offline.
     */
    var answers: List<ConceptAnswer> = emptyList()
}

/**
 * One allowed value of a coded visit attribute type, e.g. "On time" for Punctuality.
 */
data class ConceptAnswer(val uuid: String, val display: String)
