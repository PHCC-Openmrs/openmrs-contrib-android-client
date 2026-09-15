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
package com.openmrs.android_sdk.library.databases.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A visit attribute type cached locally, so the start-visit form can ask the same extra questions
 * (e.g. Punctuality) offline that it asks online.
 *
 * Keyed by uuid rather than a generated id: the server's uuid is the identity the form's
 * configuration refers to, and re-caching a type must replace it rather than pile up duplicates.
 */
@Entity(tableName = "visitattributetypes")
class VisitAttributeTypeEntity {
    @PrimaryKey
    @ColumnInfo(name = "uuid")
    var uuid: String = ""

    @ColumnInfo(name = "display")
    var display: String? = null

    @ColumnInfo(name = "datatype_classname")
    var datatypeClassname: String? = null

    @ColumnInfo(name = "datatype_config")
    var datatypeConfig: String? = null

    /**
     * The coded answers of this attribute type, as a JSON array of `{uuid, display}` objects.
     * Stored denormalised rather than in a table of its own: a handful of answers per type is
     * only ever read and written as a whole, together with the type itself.
     */
    @ColumnInfo(name = "answers")
    var answers: String? = null
}
