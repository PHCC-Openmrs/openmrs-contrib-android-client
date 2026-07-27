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
package org.openmrs.mobile.listeners.watcher

import android.graphics.Color
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import android.widget.AdapterView.OnItemSelectedListener
import com.openmrs.android_sdk.library.OpenmrsAndroid
import com.openmrs.android_sdk.utilities.StringUtils.notEmpty
import org.openmrs.mobile.activities.login.LocationArrayAdapter

//Class used to extract view validation logic
class LoginValidatorWatcher(private val mUrl: EditText,
                            private val mUsername: EditText,
                            private val mPassword: EditText,
                            private val mLocation: Spinner,
                            private val mLoginButton: Button,
                            private val mContinueButton: Button) : TextWatcher, OnItemSelectedListener {
    var isUrlChanged = false
    var isLocationErrorOccurred = false

    override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {
        // This method is intentionally empty
    }

    override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {
        // This method is intentionally empty
    }

    override fun afterTextChanged(editable: Editable) {
        // If URL text changed
        if (editable.toString().hashCode() == mUrl.text.toString().hashCode()) {
            urlChanged(editable)
        }
        mLoginButton.isEnabled = isAllDataValid
        mContinueButton.isEnabled = validateNotEmpty(mUsername) && validateNotEmpty(mPassword)
    }

    private fun urlChanged(editable: Editable) {
        if (OpenmrsAndroid.getServerUrl() != editable.toString() && notEmpty(editable.toString())) {
            isUrlChanged = true
        } else if (OpenmrsAndroid.getServerUrl() == editable.toString()) {
            isUrlChanged = false
        }
    }

    override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
        if (position >= 0 && id >= 1) {
            (parent.adapter as LocationArrayAdapter).notifyDataSetChanged()
            //Set Text Color to black once option selected
            val currentText = parent.getChildAt(0) as TextView
            currentText.setTextColor(Color.BLACK)
            mLoginButton.isEnabled = isAllDataValid
        }
    }

    override fun onNothingSelected(adapterView: AdapterView<*>?) {
        // This method is intentionally empty
    }

    // Location spinner visibility is owned by LoginFragment's credentials/location step toggle
    // (it only ever exists once a fetch has actually succeeded) - this just reports validity.
    private val isAllDataValid: Boolean
        get() = validateNotEmpty(mUsername) && validateNotEmpty(mPassword) && !isUrlChanged && validateLocation()

    private fun validateNotEmpty(editText: EditText): Boolean {
        return notEmpty(editText.text.toString())
    }

    /**
     * Get length of mLocation and check whatever it's empty or not.
     *
     * @return True if a location is selected or no location needed for the OpenMRS instance used.
     */
    private fun validateLocation(): Boolean {
        if (mLocation.adapter != null) {
            // Position 0 is always the "Session Location*" placeholder prompt (see
            // LoginFragment.getLocationStringList), so a count of 1 means there are no real
            // locations to choose from - in that case a location isn't required to log in.
            if (mLocation.adapter.count > 1) {
                return mLocation.selectedItemId != 0L
            }
        }
        return true
    }

    init {
        mUrl.addTextChangedListener(this)
        mUsername.addTextChangedListener(this)
        mPassword.addTextChangedListener(this)

        //setting the default selection manually so spinner won't call onItemSelected listener as selection is already chosen
        mLocation.setSelection(0, false)

        mLocation.onItemSelectedListener = this
    }
}