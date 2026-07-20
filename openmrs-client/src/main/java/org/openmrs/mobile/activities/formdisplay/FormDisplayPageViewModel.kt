package org.openmrs.mobile.activities.formdisplay

import androidx.lifecycle.SavedStateHandle
import com.openmrs.android_sdk.library.api.repository.LocationRepository
import com.openmrs.android_sdk.library.databases.entities.LocationEntity
import com.openmrs.android_sdk.library.models.Page
import rx.Observable
import com.openmrs.android_sdk.utilities.ApplicationConstants.BundleKeys.FORM_FIELDS_BUNDLE
import com.openmrs.android_sdk.utilities.ApplicationConstants.BundleKeys.FORM_PAGE_BUNDLE
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
    private val locationRepository: LocationRepository
) : BaseViewModel<Unit>() {

    val page: Page = savedStateHandle.get(FORM_PAGE_BUNDLE)!!
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
}
