package com.openmrs.android_sdk.utilities

import com.openmrs.android_sdk.library.models.Patient
import com.openmrs.android_sdk.utilities.StringUtils.ILLEGAL_ADDRESS_CHARACTERS
import com.openmrs.android_sdk.utilities.StringUtils.ILLEGAL_CHARACTERS
import com.openmrs.android_sdk.utilities.StringUtils.validateText

/**
 * This utility class validates patient's data presence and legality for registering.
 *
 * @param patient the patient to validate
 */
class PatientValidator(private val patient: Patient) {

    /**
     * Validates legality and presence of the necessary data of the patient object passed in the constructor
     */
    fun validate(): Boolean = patient.run {
        val logger = com.openmrs.android_sdk.library.OpenmrsAndroid.getOpenMRSLogger()
        if (gender.isNullOrBlank()) {
            logger.w("Patient validation failed: gender is empty")
            return false
        }
        if (birthdate.isNullOrBlank()) {
            logger.w("Patient validation failed: birthdate is empty")
            return false
        }

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
            if (middleName.isNullOrBlank()) {
                logger.w("Patient validation failed: middleName is empty")
                return false
            }
            if (!validateText(middleName!!, ILLEGAL_CHARACTERS)) {
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

        // Validate address - Full Address (address1) is optional, matching the web app's
        // registration form; Neighbourhood/Governorate are the actually-required address fields
        // (enforced at the UI layer, same as before). address2/country/postalCode are not
        // collected at all.
        val patientAddress = address
        if (patientAddress == null) {
            logger.w("Patient validation failed: address is null")
            return false
        }
        with(patientAddress) {
            if (!address1.isNullOrBlank() && !validateText(address1 ?: "", ILLEGAL_ADDRESS_CHARACTERS)) {
                logger.w("Patient validation failed: address1 contains illegal characters")
                return false
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
        if (!StringUtils.isValidLuhn(nationalId)) {
            logger.w("Patient validation failed: National ID fails Luhn checksum")
            return false
        }

        return true
    }
}
