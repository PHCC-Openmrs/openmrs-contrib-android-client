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
 * Role
 *
 * <p> More on roles https://rest.openmrs.org/#role </p>
 * @property name
 * @property privileges privileges assigned directly to this role
 * @property inheritedRoles roles this role inherits privileges from
 * @property retired
 * @constructor Create empty Role
 */
data class Role(
    @Expose var name: String? = null,
    @Expose var privileges: List<Privilege>? = null,
    @Expose var inheritedRoles: List<Role>? = null,
    @Expose var retired: Boolean? = null
) : Resource()
