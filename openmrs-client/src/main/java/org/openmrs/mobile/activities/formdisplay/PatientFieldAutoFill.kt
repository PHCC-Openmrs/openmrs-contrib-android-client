package org.openmrs.mobile.activities.formdisplay

import com.openmrs.android_sdk.library.OpenmrsAndroid
import com.openmrs.android_sdk.library.models.Answer
import com.openmrs.android_sdk.library.models.Patient
import com.openmrs.android_sdk.library.models.Question
import com.openmrs.android_sdk.utilities.ApplicationConstants.IdentifierSource.NATIONAL_ID_IDENTIFIER_TYPE_UUID
import com.openmrs.android_sdk.utilities.ApplicationConstants.PersonAttributeTypes.PHONE_NUMBER_UUID

/**
 * A value a form question can be auto-filled from - the patient's record, or (CURRENT_USER) the
 * logged-in user, e.g. a session's "CHW Name".
 */
enum class PatientField(val key: String) {
    NAME("name"),
    NATIONAL_ID("nationalId"),
    PHONE("phone"),
    NEIGHBOURHOOD("neighbourhood"),
    GOVERNORATE("governorate"),
    AGE("age"),
    GENDER("gender"),
    CURRENT_USER("currentUser");

    companion object {
        fun fromKey(key: String?): PatientField? = values().firstOrNull { it.key.equals(key?.trim(), ignoreCase = true) }
    }
}

/**
 * Auto-fills form questions from a patient's record - shared by the single-patient form
 * (FormDisplayPageViewModel) and Group Entry, so both fill the same questions the same way.
 *
 * A question is recognised by its `"groupPatientField"` key when the form sets one, then by a
 * well-known web `calculateExpression` (only `currentUserName()` so far), otherwise by its English
 * label (the schema's labels are never translated, see FormDisplayPageFragment).
 */
object PatientFieldAutoFill {

    /** Which patient value [question] is filled from, or null for any other question. */
    fun fieldOf(question: Question): PatientField? {
        PatientField.fromKey(question.groupPatientField)?.let { return it }
        val expression = question.questionOptions?.calculate?.calculateExpression
        if (expression != null && CURRENT_USER_EXPRESSION.containsMatchIn(expression)) return PatientField.CURRENT_USER
        val label = question.label ?: return null
        return when {
            CHW_NAME_PATTERN.containsMatchIn(label) -> PatientField.CURRENT_USER
            PARTICIPANT_NAME_PATTERN.containsMatchIn(label) -> PatientField.NAME
            ID_PATTERN.containsMatchIn(label) -> PatientField.NATIONAL_ID
            PHONE_PATTERN.containsMatchIn(label) -> PatientField.PHONE
            NEIGHBOURHOOD_PATTERN.containsMatchIn(label) -> PatientField.NEIGHBOURHOOD
            GOVERNORATE_PATTERN.containsMatchIn(label) -> PatientField.GOVERNORATE
            AGE_PATTERN.containsMatchIn(label) -> PatientField.AGE
            GENDER_PATTERN.containsMatchIn(label) -> PatientField.GENDER
            else -> null
        }
    }

    /**
     * Auto-fill value for a free-text question (e.g. "Participant Name", "Phone Number", "CHW
     * Name"). Returns null (leaving the field blank) when the question isn't an auto-filled one or
     * there is no data for it. [currentUserName] is only called for a CURRENT_USER question.
     */
    fun textValue(
        question: Question,
        patient: Patient?,
        currentUserName: () -> String? = OpenmrsAndroid::getCurrentUserPersonName
    ): String? {
        val field = fieldOf(question)
        // Not the patient's - also filled where there is no patient (Group Entry's shared page).
        if (field == PatientField.CURRENT_USER) return currentUserName()?.takeIf { it.isNotBlank() }
        val active = patient ?: return null
        return when (field) {
            PatientField.NAME -> active.name?.nameString
            PatientField.NATIONAL_ID -> active.getIdentifierByType(NATIONAL_ID_IDENTIFIER_TYPE_UUID)?.identifier
            PatientField.PHONE -> active.getAttributeValue(PHONE_NUMBER_UUID)
            PatientField.NEIGHBOURHOOD -> active.address?.cityVillage
            PatientField.GOVERNORATE -> active.address?.stateProvince
            else -> null
        }
    }

    /** Auto-fill value for an age question, from the patient's birthdate; null when unknown. */
    fun ageValue(question: Question, patient: Patient?): Double? {
        if (fieldOf(question) != PatientField.AGE) return null
        return patient?.ageInYears?.toDouble()
    }

    /**
     * Auto-fill answer index for a select/radio question - gender (by the answer label matching
     * the patient's "M"/"F") or governorate (by the answer label matching the stored
     * PersonAddress.stateProvince). Null when not such a question or no answer matches.
     */
    fun answerIndex(question: Question, patient: Patient?): Int? {
        val answers = question.questionOptions?.answers ?: return null
        val index = when (fieldOf(question)) {
            PatientField.GENDER -> {
                val gender = patient?.gender ?: return null
                answers.indexOfFirst { matchesGender(it, gender) }
            }
            PatientField.GOVERNORATE -> {
                val governorate = patient?.address?.stateProvince?.trim()?.takeIf { it.isNotEmpty() } ?: return null
                answers.indexOfFirst { (it.label ?: it.concept)?.trim().equals(governorate, ignoreCase = true) }
            }
            else -> return null
        }
        return index.takeIf { it >= 0 }
    }

    /**
     * True for a patient-demographic field (name, age, gender, national ID, governorate,
     * neighbourhood) or the current user's name - auto-populated and locked against manual
     * editing, per product requirement. Phone is auto-filled but stays editable.
     */
    fun isFixedPatientField(question: Question): Boolean {
        val field = fieldOf(question) ?: return false
        return field != PatientField.PHONE
    }

    fun isPhoneNumberField(question: Question): Boolean = fieldOf(question) == PatientField.PHONE

    private fun matchesGender(answer: Answer, genderCode: String): Boolean {
        val answerLabel = (answer.label ?: answer.concept ?: "").trim()
        return when (genderCode.uppercase()) {
            "M" -> answerLabel.equals("M", true) || answerLabel.contains("male", true) && !answerLabel.contains("female", true)
            "F" -> answerLabel.equals("F", true) || answerLabel.contains("female", true)
            else -> false
        }
    }

    // Caregiver name is included because in the IYCF form the "caregiver" is the patient
    // themself, so that field must resolve to the patient's own name like participant/patient
    // name fields do.
    // The web form fills "CHW Name" with `mode === 'enter' ? currentUserName() : ''`. The app only
    // ever enters new forms, so the expression's mode check needs no counterpart here.
    private val CURRENT_USER_EXPRESSION = Regex("\\bcurrentUserName\\s*\\(")
    private val CHW_NAME_PATTERN = Regex("\\bchw\\b.*name", RegexOption.IGNORE_CASE)
    private val PARTICIPANT_NAME_PATTERN = Regex("participant.*name|patient.*name|caregiver.*name", RegexOption.IGNORE_CASE)
    private val GENDER_PATTERN = Regex("\\bgender\\b|\\bsex\\b", RegexOption.IGNORE_CASE)
    private val AGE_PATTERN = Regex("\\bage\\b", RegexOption.IGNORE_CASE)
    private val PHONE_PATTERN = Regex("phone|mobile", RegexOption.IGNORE_CASE)
    private val ID_PATTERN = Regex("national.*id|id\\s*number|\\bidentifier\\b", RegexOption.IGNORE_CASE)
    private val NEIGHBOURHOOD_PATTERN = Regex("neighbou?rhood", RegexOption.IGNORE_CASE)
    private val GOVERNORATE_PATTERN = Regex("governorate", RegexOption.IGNORE_CASE)
}
