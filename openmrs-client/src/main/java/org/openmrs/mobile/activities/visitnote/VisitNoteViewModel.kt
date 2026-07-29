/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.mobile.activities.visitnote

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import com.openmrs.android_sdk.library.api.repository.DiagnosisRepository
import com.openmrs.android_sdk.library.api.repository.EncounterRepository
import com.openmrs.android_sdk.library.api.repository.ProviderRepository
import com.openmrs.android_sdk.library.dao.PatientDAO
import com.openmrs.android_sdk.library.models.ConceptSearchResult
import com.openmrs.android_sdk.library.models.EncounterProviderCreate
import com.openmrs.android_sdk.library.models.Encountercreate
import com.openmrs.android_sdk.library.models.Obscreate
import com.openmrs.android_sdk.library.models.Patient
import com.openmrs.android_sdk.library.models.ResultType
import com.openmrs.android_sdk.utilities.ApplicationConstants.BundleKeys.ENCOUNTERTYPE
import com.openmrs.android_sdk.utilities.ApplicationConstants.BundleKeys.PATIENT_ID_BUNDLE
import dagger.hilt.android.lifecycle.HiltViewModel
import org.openmrs.mobile.activities.BaseViewModel
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import javax.inject.Inject

data class SelectedDiagnosis(
    val conceptUuid: String,
    val display: String,
    var certainty: String = DiagnosisRepository.CERTAINTY_PRESUMED
)

class NoProviderLinkedException : Exception()

@HiltViewModel
class VisitNoteViewModel @Inject constructor(
    private val patientDAO: PatientDAO,
    private val encounterRepository: EncounterRepository,
    private val providerRepository: ProviderRepository,
    private val diagnosisRepository: DiagnosisRepository,
    private val savedStateHandle: SavedStateHandle
) : BaseViewModel<Unit>() {

    private val patientId: Long = savedStateHandle.get(PATIENT_ID_BUNDLE)!!
    private val encounterType: String = savedStateHandle.get(ENCOUNTERTYPE)!!

    val patient: Patient = patientDAO.findPatientByID(patientId)

    private val selectedDiagnoses = mutableListOf<SelectedDiagnosis>()

    private val searchResultsLiveData = MutableLiveData<List<ConceptSearchResult>>()
    val searchResults: LiveData<List<ConceptSearchResult>> get() = searchResultsLiveData

    private val selectedDiagnosesLiveData = MutableLiveData<List<SelectedDiagnosis>>(emptyList())
    val selectedDiagnosesList: LiveData<List<SelectedDiagnosis>> get() = selectedDiagnosesLiveData

    /** Set right before [ResultType.EncounterSubmissionError] is posted from [save], so the
     * Activity can inspect it synchronously from its result observer to show a specific message. */
    var lastSaveError: Throwable? = null
        private set

    fun search(query: String) {
        if (query.isBlank()) {
            searchResultsLiveData.value = emptyList()
            return
        }
        addSubscription(
            diagnosisRepository.searchDiagnoses(query)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ searchResultsLiveData.value = it }, { searchResultsLiveData.value = emptyList() })
        )
    }

    fun addDiagnosis(result: ConceptSearchResult) {
        val uuid = result.uuid ?: return
        val display = result.display ?: return
        if (selectedDiagnoses.any { it.conceptUuid == uuid }) return
        selectedDiagnoses.add(SelectedDiagnosis(uuid, display))
        selectedDiagnosesLiveData.value = selectedDiagnoses.toList()
        searchResultsLiveData.value = emptyList()
    }

    fun removeDiagnosisAt(index: Int) {
        if (index !in selectedDiagnoses.indices) return
        selectedDiagnoses.removeAt(index)
        selectedDiagnosesLiveData.value = selectedDiagnoses.toList()
    }

    fun toggleCertaintyAt(index: Int) {
        if (index !in selectedDiagnoses.indices) return
        val diagnosis = selectedDiagnoses[index]
        diagnosis.certainty =
            if (diagnosis.certainty == DiagnosisRepository.CERTAINTY_CONFIRMED) {
                DiagnosisRepository.CERTAINTY_PRESUMED
            } else {
                DiagnosisRepository.CERTAINTY_CONFIRMED
            }
        selectedDiagnosesLiveData.value = selectedDiagnoses.toList()
    }

    fun save(noteText: String): LiveData<ResultType> {
        val resultLiveData = MutableLiveData<ResultType>()
        val diagnosesSnapshot = selectedDiagnoses.toList()
        val patientUuid = patient.uuid!!

        addSubscription(
            providerRepository.getCurrentProvider()
                .flatMap { provider ->
                    if (provider?.uuid == null) throw NoProviderLinkedException()
                    providerRepository.getEncounterRoles().map { roles -> provider to roles }
                }
                .flatMap { (provider, roles) ->
                    val role = roles.firstOrNull { it.display?.equals("Clinician", ignoreCase = true) == true }
                        ?: roles.firstOrNull()
                        ?: throw IllegalStateException("No encounter roles configured on the server.")

                    val enc = Encountercreate().apply {
                        patient = patientUuid
                        patientId = this@VisitNoteViewModel.patientId
                        encounterType = this@VisitNoteViewModel.encounterType
                        formUuid = VISIT_NOTE_FORM_UUID
                        observations = if (noteText.isNotBlank()) {
                            listOf(Obscreate().apply {
                                concept = NOTE_CONCEPT_UUID
                                value = noteText
                                person = patientUuid
                            })
                        } else emptyList()
                        encounterProvider = listOf(EncounterProviderCreate(provider!!.uuid!!, role.uuid!!))
                    }
                    encounterRepository.createEncounterOnline(enc)
                }
                .flatMap { encounter ->
                    if (diagnosesSnapshot.isEmpty()) {
                        Observable.just(Unit)
                    } else {
                        Observable.from(diagnosesSnapshot.withIndex().toList())
                            .concatMap { (index, diagnosis) ->
                                diagnosisRepository.createDiagnosis(
                                    encounter.uuid!!,
                                    patientUuid,
                                    diagnosis.conceptUuid,
                                    diagnosis.certainty,
                                    index + 1
                                )
                            }
                            .toList()
                            .map { Unit }
                    }
                }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { resultLiveData.value = ResultType.EncounterSubmissionSuccess },
                    { error ->
                        lastSaveError = error
                        resultLiveData.value = ResultType.EncounterSubmissionError
                    }
                )
        )

        return resultLiveData
    }

    companion object {
        private const val VISIT_NOTE_FORM_UUID = "c75f120a-04ec-11e3-8780-2b40bef9a44b"
        private const val NOTE_CONCEPT_UUID = "162169AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
    }
}
