package com.openmrs.android_sdk.utilities

import com.openmrs.android_sdk.library.models.Patient
import com.openmrs.android_sdk.utilities.StringUtils.ILLEGAL_ADDRESS_CHARACTERS
import com.openmrs.android_sdk.utilities.StringUtils.ILLEGAL_CHARACTERS
import com.openmrs.android_sdk.utilities.StringUtils.validateText

/**
 * This utility class validates patient's data presence and legality for registering.
 *
 * @param patient the patient to validate
 * @param isPatientUnidentified whether the patient being registered is unidentified or not
 * @param countriesList the available countries that can be picked from as the patient's country
 */
class PatientValidator(private val patient: Patient,
                       var isPatientUnidentified: Boolean,
                       private val countriesList: List<String>) {

    /**
     * Validates legality and presence of the necessary data of the patient object passed in the constructor
     */
    fun validate(): Boolean = patient.run {
        val logger = com.openmrs.android_sdk.library.OpenmrsAndroid.getOpenMRSLogger()
        /* Checks for identified or unidentified patient */
        if (gender.isNullOrBlank()) {
            logger.w("Patient validation failed: gender is empty")
            return false
        }
        if (birthdate.isNullOrBlank()) {
            logger.w("Patient validation failed: birthdate is empty")
            return false
        }
        if (isPatientUnidentified) return true

        /* Additional checks for identified patient */

        // Validate names
        val patientName = name
        if (patientName == null) {
            logger.w("Patient validation failed: name is null")
            return false
        }
        with(patientName) {
            if (givenName.isNullOrBlank()) {
                logger.w("Patient validation failed: givenName is empty")
                return false
            }
            if (!validateText(givenName!!, ILLEGAL_CHARACTERS)) {
                logger.w("Patient validation failed: givenName contains illegal characters")
                return false
            }
            // Middle name can be left empty
            if (middleName != null && !validateText(middleName!!, ILLEGAL_CHARACTERS)) {
                logger.w("Patient validation failed: middleName contains illegal characters")
                return false
            }
            if (familyName.isNullOrBlank()) {
                logger.w("Patient validation failed: familyName is empty")
                return false
            }
            if (!validateText(familyName!!, ILLEGAL_CHARACTERS)) {
                logger.w("Patient validation failed: familyName contains illegal characters")
                return false
            }
        }

        // Validate addresses
        val patientAddress = address
        if (patientAddress == null) {
            logger.w("Patient validation failed: address is null")
            return false
        }
        with(patientAddress) {
            if (address1.isNullOrBlank() && address2.isNullOrBlank()) {
                logger.w("Patient validation failed: both address1 and address2 are empty")
                return false
            }
            if (!validateText(address1 ?: "", ILLEGAL_ADDRESS_CHARACTERS)) {
                logger.w("Patient validation failed: address1 contains illegal characters")
                return false
            }
            if (!validateText(address2 ?: "", ILLEGAL_ADDRESS_CHARACTERS)) {
                logger.w("Patient validation failed: address2 contains illegal characters")
                return false
            }
            if (country != null && !countriesList.isNullOrEmpty() && !countriesList.contains(country!!)) {
                logger.w("Patient validation failed: country '$country' not in list. List size: ${countriesList.size}")
                // return false // Let it pass even if not in list for now to debug
            }
        }

        // Validate National ID - required by the server alongside the OpenMRS ID
        val nationalId = patient.getIdentifierByType(ApplicationConstants.IdentifierSource.NATIONAL_ID_IDENTIFIER_TYPE_UUID)?.identifier
        if (nationalId.isNullOrBlank()) {
            logger.w("Patient validation failed: National ID is empty")
            return false
        }
        if (!Regex(ApplicationConstants.IdentifierSource.NATIONAL_ID_FORMAT_REGEX).matches(nationalId)) {
            logger.w("Patient validation failed: National ID format invalid")
            return false
        }

        return true
    }
}
