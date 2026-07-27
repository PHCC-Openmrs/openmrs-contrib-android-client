package org.openmrs.mobile.activities

import androidx.fragment.app.Fragment
import org.openmrs.mobile.utilities.PrivilegeUtils

abstract class BaseFragment : Fragment() {
    val isActive: Boolean get() = isAdded

    /**
     * @see ACBaseActivity.hasPrivilege
     */
    protected fun hasPrivilege(privilegeName: String): Boolean = PrivilegeUtils.hasPrivilege(privilegeName)

    protected fun hasAnyPrivilege(vararg privilegeNames: String): Boolean =
        PrivilegeUtils.hasAnyPrivilege(*privilegeNames)
}
