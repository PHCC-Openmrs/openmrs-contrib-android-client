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
package org.openmrs.mobile.activities.patientdashboard.details

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import com.openmrs.android_sdk.library.models.OperationType.PatientFetching
import com.openmrs.android_sdk.library.models.Patient
import com.openmrs.android_sdk.library.models.Result
import com.openmrs.android_sdk.utilities.ApplicationConstants
import com.openmrs.android_sdk.utilities.ApplicationConstants.BundleKeys.PATIENT_ID_BUNDLE
import com.openmrs.android_sdk.utilities.DateUtils.convertTime
import com.openmrs.android_sdk.utilities.StringUtils.notEmpty
import com.openmrs.android_sdk.utilities.StringUtils.notNull
import com.openmrs.android_sdk.utilities.ToastUtil.error
import dagger.hilt.android.AndroidEntryPoint
import org.openmrs.mobile.R
import org.openmrs.mobile.activities.BaseFragment
import org.openmrs.mobile.activities.patientdashboard.PatientDashboardActivity
import org.openmrs.mobile.databinding.FragmentPatientDetailsBinding
import org.openmrs.mobile.utilities.ImageUtils.showPatientPhoto
import org.openmrs.mobile.utilities.makeGone
import org.openmrs.mobile.utilities.makeVisible

@AndroidEntryPoint
class PatientDetailsFragment : BaseFragment() {
    private var _binding: FragmentPatientDetailsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PatientDashboardDetailsViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPatientDetailsBinding.inflate(inflater, null, false)

        setupObserver()

        return binding.root
    }

    override fun onResume() {
        super.onResume()
        // Re-reads the patient from the local DB every time this tab becomes visible again -
        // notably, when returning from AddEditPatientActivity after saving an edit. The pager
        // hosting this fragment keeps it alive across tab switches, so onCreateView() only runs
        // once; without this, an offline edit (which only writes to Room, with no network
        // round-trip to trigger a re-render) would never show up here.
        fetchPatientDetails()
    }

    private fun setupObserver() {
        viewModel.result.observe(viewLifecycleOwner, Observer { result ->
            when (result) {
                is Result.Loading -> {
                }
                is Result.Success -> {
                    when (result.operationType) {
                        PatientFetching -> showPatientDetails(result.data)
                        else -> {
                        }
                    }
                }
                is Result.Error -> {
                    when (result.operationType) {
                        PatientFetching -> error(getString(R.string.get_patient_from_database_error))
                        else -> {
                        }
                    }
                }
                else -> throw IllegalStateException()
            }

        })
    }

    private fun fetchPatientDetails() {
        viewModel.fetchPatientData()
    }

    private fun showPatientDetails(patient: Patient) {
        with(binding) {
            var displayId = patient.identifier?.identifier
            if (displayId.isNullOrEmpty() || displayId.equals("null", ignoreCase = true)) {
                displayId = patient.id.toString()
            }

            setMenuTitle(patient.name.nameString, displayId)
            if (isAdded) {
                if ("M" == patient.gender) {
                    patientDetailsGender.text = getString(R.string.male)
                } else {
                    patientDetailsGender.text = getString(R.string.female)
                }
            }
            if (patient.photo != null) {
                val photo = patient.resizedPhoto
                val patientName = patient.name.nameString
                patientPhoto.setImageBitmap(photo)
                patientPhoto.setOnClickListener { showPatientPhoto(requireContext(), photo, patientName) }
            }
            val fullName = patient.name?.nameString ?: patient.display ?: ""
            com.openmrs.android_sdk.library.OpenmrsAndroid.getOpenMRSLogger().i("[UI-Display] Details Name: '$fullName'")
            patientDetailsName.text = fullName
            val longTime = convertTime(patient.birthdate)
            if (longTime != null) {
                patientDetailsBirthDate.text = convertTime(longTime)
            }
            patient.address?.let {
                if (notNull(it.address1) && notEmpty(it.address1)) {
                    addressDetailsStreet.text = it.address1
                } else {
                    addressDetailsStreet.makeGone()
                }
                showAddressDetailsViewElement(addressDetailsStateLabel, addressDetailsState, it.stateProvince)
                showAddressDetailsViewElement(addressDetailsCountryLabel, addressDetailsCountry, it.country)
                showAddressDetailsViewElement(addressDetailsPostalCodeLabel, addressDetailsPostalCode, it.postalCode)
                showAddressDetailsViewElement(addressDetailsCityLabel, addressDetailsCity, it.cityVillage)
            }
            val nationalId = patient.getIdentifierByType(ApplicationConstants.IdentifierSource.NATIONAL_ID_IDENTIFIER_TYPE_UUID)?.identifier
            showAddressDetailsViewElement(patientDetailsNationalIdLabel, patientDetailsNationalId, nationalId)

            val phoneNumber = patient.getAttributeValue(ApplicationConstants.PersonAttributeTypes.PHONE_NUMBER_UUID)
            showAddressDetailsViewElement(patientDetailsPhoneNumberLabel, patientDetailsPhoneNumber, phoneNumber)

            val patientStatusUuid = patient.getAttributeValue(ApplicationConstants.PersonAttributeTypes.PATIENT_STATUS_UUID)
            showAddressDetailsViewElement(patientDetailsStatusLabel, patientDetailsStatus, patientStatusLabelForUuid(patientStatusUuid))

            if (patient.isDeceased) {
                deceasedView.makeVisible()
                deceasedView.text = getString(R.string.marked_patient_deceased_successfully, patient.causeOfDeath.display)
            }
        }
    }

    private fun patientStatusLabelForUuid(uuid: String?): String? = when (uuid) {
        ApplicationConstants.PatientStatusAnswers.RESIDENT_UUID -> ApplicationConstants.PatientStatusAnswers.RESIDENT_LABEL
        ApplicationConstants.PatientStatusAnswers.IDP_UUID -> ApplicationConstants.PatientStatusAnswers.IDP_LABEL
        else -> null
    }

    private fun showAddressDetailsViewElement(detailsViewLabel: TextView, detailsView: TextView, detailsText: String?) {
        if (notNull(detailsText) && notEmpty(detailsText)) {
            detailsView.text = detailsText
        } else {
            detailsView.makeGone()
            detailsViewLabel.makeGone()
        }
    }

    private fun setMenuTitle(nameString: String, identifier: String) {
        (activity as PatientDashboardActivity).supportActionBar?.apply {
            title = nameString
            subtitle = "#$identifier"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(patientId: Long): PatientDetailsFragment {
            val fragment = PatientDetailsFragment()
            fragment.arguments = bundleOf(Pair(PATIENT_ID_BUNDLE, patientId))
            return fragment
        }
    }
}
