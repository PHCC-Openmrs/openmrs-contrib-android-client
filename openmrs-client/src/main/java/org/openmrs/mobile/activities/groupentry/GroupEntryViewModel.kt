package org.openmrs.mobile.activities.groupentry

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import com.openmrs.android_sdk.library.api.repository.EncounterRepository
import com.openmrs.android_sdk.library.api.repository.FormRepository
import com.openmrs.android_sdk.library.dao.VisitDAO
import com.openmrs.android_sdk.library.models.Encountercreate
import com.openmrs.android_sdk.library.models.Form
import com.openmrs.android_sdk.library.models.Patient
import com.openmrs.android_sdk.library.models.ResultType
import com.openmrs.android_sdk.utilities.ApplicationConstants.BundleKeys.ENCOUNTERTYPE
import com.openmrs.android_sdk.utilities.ApplicationConstants.BundleKeys.FORM_FIELDS_BUNDLE
import com.openmrs.android_sdk.utilities.ApplicationConstants.BundleKeys.FORM_NAME
import com.openmrs.android_sdk.utilities.ApplicationConstants.BundleKeys.VALUEREFERENCE
import com.openmrs.android_sdk.utilities.DateUtils
import com.openmrs.android_sdk.utilities.FormUtils
import com.openmrs.android_sdk.utilities.execute
import dagger.hilt.android.lifecycle.HiltViewModel
import org.openmrs.mobile.activities.BaseViewModel
import org.openmrs.mobile.activities.formdisplay.ObservationBuilder
import org.openmrs.mobile.bundle.FormFieldsWrapper
import org.openmrs.mobile.utilities.FilterUtil
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import javax.inject.Inject

/**
 * Group Entry's attendee step: lists every patient with an active visit, keeps the user's
 * selection, and saves the form once per selected patient - the shared answers filled in on the
 * previous screen plus that patient's own auto-filled fields (see [GroupEntryForms]).
 */
@HiltViewModel
class GroupEntryViewModel @Inject constructor(
    private val visitDAO: VisitDAO,
    private val formRepository: FormRepository,
    private val encounterRepository: EncounterRepository,
    savedStateHandle: SavedStateHandle
) : BaseViewModel<List<Patient>>() {

    private val form: Form = FormUtils.getForm(savedStateHandle.get<String>(VALUEREFERENCE)!!)
    private val formName: String = savedStateHandle.get(FORM_NAME)!!
    private val encounterType: String = savedStateHandle.get(ENCOUNTERTYPE)!!
    private val sharedAnswers: FormFieldsWrapper = savedStateHandle.get(FORM_FIELDS_BUNDLE)!!

    private var allPatients: List<Patient> = emptyList()
    var query: String? = null
        private set

    /** Local ids of the ticked patients - kept across searches, so filtering never unticks anyone. */
    val selectedIds = linkedSetOf<Long>()
    private val _selectedCount = MutableLiveData(0)
    val selectedCount: LiveData<Int> get() = _selectedCount

    private val _submission = MutableLiveData<Submission>()
    val submission: LiveData<Submission> get() = _submission
    val isSubmitting: Boolean get() = _submission.value is Submission.Running

    init {
        loadPatients()
    }

    fun loadPatients() {
        setLoading()
        addSubscription(visitDAO.getActiveVisits()
            .map { visits -> visits.mapNotNull { it.patient }.filter { it.id != null }.distinctBy { it.id } }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ patients ->
                allPatients = patients
                // Drop selections for patients whose visit has ended since.
                selectedIds.retainAll(patients.mapNotNull { it.id }.toSet())
                _selectedCount.value = selectedIds.size
                publishList()
            }, { setError(it) })
        )
    }

    fun search(query: String?) {
        this.query = query
        publishList()
    }

    private fun publishList() {
        val q = query
        setContent(if (q.isNullOrBlank()) allPatients else FilterUtil.getPatientsFilteredByQuery(allPatients, q))
    }

    fun setSelected(patient: Patient, selected: Boolean) {
        val id = patient.id ?: return
        if (selected) selectedIds += id else selectedIds -= id
        _selectedCount.value = selectedIds.size
    }

    /** Ticks every patient currently listed (i.e. matching the search), keeping earlier ticks. */
    fun selectAllListed(listed: List<Patient>) {
        listed.mapNotNullTo(selectedIds) { it.id }
        _selectedCount.value = selectedIds.size
    }

    fun clearSelection() {
        selectedIds.clear()
        _selectedCount.value = 0
    }

    /**
     * Saves one encounter per selected patient, one after another, reporting progress as it goes.
     * A patient that fails doesn't stop the rest - every outcome ends up in the [Submission.Done]
     * summary. Each save goes through [EncounterRepository.saveEncounter], exactly like the
     * single-patient form, so offline saves queue and sync the same way.
     */
    fun submit() {
        if (isSubmitting) return
        val patients = allPatients.filter { it.id in selectedIds }
        if (patients.isEmpty()) return
        _submission.value = Submission.Running(0, patients.size)

        addSubscription(Observable.fromCallable {
            val formUuid = try {
                formRepository.fetchFormResourceByName(formName).execute()?.uuid
            } catch (e: Exception) {
                null
            }
            val summary = Summary()
            patients.forEachIndexed { index, patient ->
                val patientAnswers = GroupEntryForms.patientAnswers(form, patient)
                val result = try {
                    encounterRepository.saveEncounter(createEncounter(patient, patientAnswers, formUuid)).execute()
                } catch (e: Exception) {
                    ResultType.EncounterSubmissionError
                }
                val name = patient.name?.nameString ?: patient.identifier?.identifier ?: ""
                when (result) {
                    ResultType.EncounterSubmissionSuccess -> summary.uploaded++
                    ResultType.EncounterSubmissionLocalSuccess -> summary.savedOffline++
                    else -> summary.failed += name
                }
                if (result != ResultType.EncounterSubmissionError && patientAnswers.isPhoneOmitted) {
                    summary.withoutPhone++
                }
                _submission.postValue(Submission.Running(index + 1, patients.size))
            }
            summary
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { _submission.value = Submission.Done(it) },
                { _submission.value = Submission.Done(Summary().apply { failed += patients.map { p -> p.name?.nameString ?: "" } }) }
            )
        )
    }

    private fun createEncounter(patient: Patient, patientAnswers: GroupEntryForms.PatientAnswers, formUuid: String?) =
        Encountercreate().apply {
            patientId = patient.id
            this.patient = patient.uuid
            this.encounterType = this@GroupEntryViewModel.encounterType
            formname = formName
            this.formUuid = formUuid
            // Same as the single-patient form: the fill time, with an explicit timezone offset
            // (see FormDisplayMainViewModel.submitForm).
            encounterDatetime = DateUtils.convertTime(System.currentTimeMillis(), DateUtils.OPEN_MRS_REQUEST_FORMAT)
            observations = ObservationBuilder.build(
                sharedAnswers.inputFields + patientAnswers.inputFields,
                sharedAnswers.selectOneFields + patientAnswers.selectOneFields,
                sharedAnswers.selectMultipleFields,
                sharedAnswers.dateFields,
                sharedAnswers.textFields + patientAnswers.textFields,
                patient.uuid
            )
        }

    class Summary {
        var uploaded = 0
        var savedOffline = 0
        var withoutPhone = 0
        val failed = mutableListOf<String>()
    }

    sealed class Submission {
        data class Running(val done: Int, val total: Int) : Submission()
        data class Done(val summary: Summary) : Submission()
    }
}
