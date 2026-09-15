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
import java.io.Serializable

/**
 * One value of one visit attribute type on a visit (e.g. Punctuality = "On time", or the Service
 * the visit is for) - the visit counterpart of [PersonAttribute].
 *
 * Deliberately *not* a [Resource]: `ResourceSerializer` collapses any nested Resource down to its
 * own uuid, which would turn each attribute into a bare string instead of the
 * `{"attributeType": ..., "value": ...}` pair the server expects when posting a visit.
 *
 * On the wire `attributeType` is a uuid string when written and a nested object when read back,
 * and `value` is a plain string for most datatypes but a nested concept object for coded ones;
 * [com.openmrs.android_sdk.utilities.VisitAttributeDeserializer] flattens both back into these
 * fields, exactly as `PersonAttributeDeserializer` does for person attributes.
 *
 * @constructor Create empty Visit attribute
 */
class VisitAttribute() : Serializable {

    /** UUID of the visit attribute type this value belongs to. */
    @Expose
    var attributeType: String? = null

    /** The value itself: free text, a number/date rendered as text, or a concept uuid when coded. */
    @Expose
    var value: String? = null

    /** UUID of this attribute instance on the server. Only set when read back; never sent. */
    var attributeUuid: String? = null

    /** Display text of the attribute type, e.g. "Punctuality". Only set when read back; never sent. */
    var attributeTypeDisplay: String? = null

    /** Display text of the value, e.g. "On time". Only set when read back; never sent. */
    var valueDisplay: String? = null

    constructor(attributeType: String?, value: String?) : this() {
        this.attributeType = attributeType
        this.value = value
    }
}
