package org.openmrs.mobile.activities.formdisplay

import androidx.lifecycle.SavedStateHandle
import com.openmrs.android_sdk.library.api.repository.LocationRepository
import com.openmrs.android_sdk.library.dao.PatientDAO
import com.openmrs.android_sdk.library.databases.entities.LocationEntity
import com.openmrs.android_sdk.library.models.Answer
import com.openmrs.android_sdk.library.models.Page
import com.openmrs.android_sdk.library.models.Patient
import com.openmrs.android_sdk.library.models.Question
import rx.Observable
import com.openmrs.android_sdk.utilities.ApplicationConstants.BundleKeys.FORM_FIELDS_BUNDLE
import com.openmrs.android_sdk.utilities.ApplicationConstants.BundleKeys.FORM_PAGE_BUNDLE
import com.openmrs.android_sdk.utilities.ApplicationConstants.BundleKeys.PATIENT_ID_BUNDLE
import com.openmrs.android_sdk.utilities.ApplicationConstants.PersonAttributeTypes.PHONE_NUMBER_UUID
import com.openmrs.android_sdk.utilities.DateField
import com.openmrs.android_sdk.utilities.InputField
import com.openmrs.android_sdk.utilities.SelectMultipleField
import com.openmrs.android_sdk.utilities.SelectOneField
import com.openmrs.android_sdk.utilities.TextField
import dagger.hilt.android.lifecycle.HiltViewModel
import org.openmrs.mobile.activities.BaseViewModel
import org.openmrs.mobile.bundle.FormFieldsWrapper
import javax.inject.Inject
import kotlin.math.abs

@HiltViewModel
class FormDisplayPageViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val locationRepository: LocationRepository,
    private val patientDAO: PatientDAO
) : BaseViewModel<Unit>() {

    val page: Page = savedStateHandle.get(FORM_PAGE_BUNDLE)!!
    private val patientId: Long? = savedStateHandle.get(PATIENT_ID_BUNDLE)
    private val patient: Patient? = patientId?.let { patientDAO.findPatientByID(it) }
    var inputFields = mutableListOf<InputField>()
    var selectOneFields = mutableListOf<SelectOneField>()
    var selectMultipleFields = mutableListOf<SelectMultipleField>()
    var dateFields = mutableListOf<DateField>()
    var textFields = mutableListOf<TextField>()

    init {
        val formFieldsWrapper: FormFieldsWrapper? = savedStateHandle.get(FORM_FIELDS_BUNDLE)
        formFieldsWrapper?.let {
            inputFields = it.inputFields as MutableList
            selectOneFields = it.selectOneFields as MutableList
            selectMultipleFields = it.selectMultipleFields as MutableList
            dateFields = it.dateFields as MutableList
            textFields = it.textFields as MutableList
        }
    }

    fun getOrCreateInputField(concept: String): InputField {
        var inputField = findInputFieldByConcept(concept)
        if (inputField == null) {
            inputField = InputField(concept)
            inputFields.add(inputField)
        }
        return inputField
    }

    fun findInputFieldByConcept(concept: String): InputField? {
        inputFields.forEach {
            if (it.id == abs(concept.hashCode())) return it
        }
        return null
    }

    fun findSelectOneFieldById(concept: String): SelectOneField? {
        selectOneFields.forEach {
            if (it.concept == concept) return it
        }
        return null
    }

    fun findSelectMultipleFieldById(concept: String): SelectMultipleField? {
        selectMultipleFields.forEach {
            if (it.concept == concept) return it
        }
        return null
    }

    fun findDateFieldById(concept: String): DateField? {
        dateFields.forEach {
            if (it.concept == concept) return it
        }
        return null
    }

    fun findTextFieldById(concept: String): TextField? {
        textFields.forEach {
            if (it.concept == concept) return it
        }
        return null
    }

    fun getLocations(tag: String): Observable<List<LocationEntity>> = locationRepository.getLocations(tag)

    /**
     * Auto-fill value for a free-text question (e.g. "Participant Name", "Phone Number"),
     * matched by the question's label. Returns null (leaving the field blank) when the label
     * doesn't match a known pattern or the patient has no data for it.
     */
    fun autoFillTextValue(question: Question): String? {
        val label = question.label ?: return null
        val active = patient ?: return null
        return when {
            PARTICIPANT_NAME_PATTERN.containsMatchIn(label) -> active.name?.nameString
            PHONE_PATTERN.containsMatchIn(label) -> active.getAttributeValue(PHONE_NUMBER_UUID)
            else -> null
        }
    }

    /**
     * Auto-fill value for a numeric question matching an "age" label, computed from the
     * patient's birthdate. Returns null when the label isn't an age field or age is unknown.
     */
    fun autoFillAgeValue(question: Question): Double? {
        val label = question.label ?: return null
        if (!AGE_PATTERN.containsMatchIn(label)) return null
        return patient?.ageInYears?.toDouble()
    }

    /**
     * Auto-fill answer index for a select/radio question matching a "gender" label, by finding
     * the answer option whose label corresponds to the patient's gender ("M"/"F"). Returns null
     * when the label isn't a gender field, gender is unknown, or no matching answer is found.
     */
    fun autoFillGenderAnswerIndex(question: Question): Int? {
        val label = question.label ?: return null
        if (!GENDER_PATTERN.containsMatchIn(label)) return null
        val gender = patient?.gender ?: return null
        val answers = question.questionOptions?.answers ?: return null
        val index = answers.indexOfFirst { matchesGender(it, gender) }
        return index.takeIf { it >= 0 }
    }

    private fun matchesGender(answer: Answer, genderCode: String): Boolean {
        val answerLabel = (answer.label ?: answer.concept ?: "").trim()
        return when (genderCode.uppercase()) {
            "M" -> answerLabel.equals("M", true) || answerLabel.contains("male", true) && !answerLabel.contains("female", true)
            "F" -> answerLabel.equals("F", true) || answerLabel.contains("female", true)
            else -> false
        }
    }

    companion object {
        private val PARTICIPANT_NAME_PATTERN = Regex("participant.*name|patient.*name", RegexOption.IGNORE_CASE)
        private val GENDER_PATTERN = Regex("\\bgender\\b|\\bsex\\b", RegexOption.IGNORE_CASE)
        private val AGE_PATTERN = Regex("\\bage\\b", RegexOption.IGNORE_CASE)
        private val PHONE_PATTERN = Regex("phone|mobile", RegexOption.IGNORE_CASE)
    }
}
