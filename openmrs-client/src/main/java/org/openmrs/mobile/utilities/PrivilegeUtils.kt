package org.openmrs.mobile.utilities

import com.openmrs.android_sdk.library.PrivilegeChecker

/**
 * Single entry point for UI-layer privilege checks, usable from Hilt-managed
 * Activities/Fragments as well as legacy classes instantiated with `new`
 * (e.g. RecyclerView adapters) since [PrivilegeChecker] is a plain, globally
 * accessible object rather than something that needs to be injected.
 */
object PrivilegeUtils {
    @JvmStatic
    fun hasPrivilege(privilege: String): Boolean = PrivilegeChecker.hasPrivilege(privilege)

    @JvmStatic
    fun hasAnyPrivilege(vararg privileges: String): Boolean =
        privileges.isEmpty() || privileges.any(PrivilegeChecker::hasPrivilege)
}
