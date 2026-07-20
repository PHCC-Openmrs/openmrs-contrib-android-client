/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.mobile.activities.formdisplay

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import com.openmrs.android_sdk.library.api.repository.EncounterRepository
import com.openmrs.android_sdk.library.api.repository.FormRepository
import com.openmrs.android_sdk.library.dao.PatientDAO
import com.openmrs.android_sdk.library.models.Encountercreate
import com.openmrs.android_sdk.library.models.Obscreate
import com.openmrs.android_sdk.library.models.Patient
import com.openmrs.android_sdk.library.models.ResultType
import com.openmrs.android_sdk.utilities.ApplicationConstants
import com.openmrs.android_sdk.utilities.ToastUtil
import com.openmrs.android_sdk.utilities.ApplicationConstants.BundleKeys.ENCOUNTERTYPE
import com.openmrs.android_sdk.utilities.ApplicationConstants.BundleKeys.ENCOUNTER_UUID
import com.openmrs.android_sdk.utilities.ApplicationConstants.BundleKeys.FORM_NAME
import com.openmrs.android_sdk.utilities.ApplicationConstants.BundleKeys.PATIENT_ID_BUNDLE
import com.openmrs.android_sdk.utilities.DateField
import com.openmrs.android_sdk.utilities.InputField
import com.openmrs.android_sdk.utilities.SelectMultipleField
import com.openmrs.android_sdk.utilities.SelectOneField
import com.openmrs.android_sdk.utilities.TextField
import com.openmrs.android_sdk.utilities.execute
import dagger.hilt.android.lifecycle.HiltViewModel
import org.joda.time.LocalDateTime
import org.openmrs.mobile.activities.BaseViewModel
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import javax.inject.Inject

@HiltViewModel
class FormDisplayMainViewModel @Inject constructor(
        private val patientDAO: PatientDAO,
        private val formRepository: FormRepository,
        private val encounterRepository: EncounterRepository,
        private val savedStateHandle: SavedStateHandle
) : BaseViewModel<Unit>() {

    private val patientId: Long = savedStateHandle.get(PATIENT_ID_BUNDLE)!!
    private val encounterType: String = savedStateHandle.get(ENCOUNTERTYPE)!!
    private val encounterTypeName: String? = savedStateHandle.get(ApplicationConstants.BundleKeys.ENCOUNTERTYPE_NAME)
    private val formName: String = savedStateHandle.get(FORM_NAME)!!
    private val encounterUuid: String? = savedStateHandle.get(ENCOUNTER_UUID)
    private val isUpdateEncounter = !encounterUuid.isNullOrEmpty()

    val patient: Patient = patientDAO.findPatientByID(patientId.toString())

    fun submitForm(
        inputFields: List<InputField>,
        radioGroupFields: List<SelectOneField>,
        checkboxFields: List<SelectMultipleField>,
        dateFields: List<DateField>,
        textFields: List<TextField>
    ): LiveData<ResultType> {
        val enc = Encountercreate()
        enc.patientId = patientId
        enc.formname = encounterTypeName ?: formName
        enc.observations = createObservationsFromInputFields(inputFields) +
                createObservationsFromRadioGroupFields(radioGroupFields) +
                createObservationsFromCheckboxFields(checkboxFields) +
                createObservationsFromDateFields(dateFields) +
                createObservationsFromTextFields(textFields)

        return if (isUpdateEncounter) updateRecords(encounterUuid!!, enc) else createRecords(enc)
    }

    private fun createRecords(enc: Encountercreate): LiveData<ResultType> {
        val resultLiveData = MutableLiveData<ResultType>()

        addSubscription(Observable.fromCallable {
            enc.patient = patient.uuid
            enc.encounterType = encounterType
            enc.formname = formName
            val form = try {
                formRepository.fetchFormResourceByName(formName).execute() 
            } catch (e: Exception) { 
                null 
            }
            enc.formUuid = form?.uuid
            return@fromCallable enc
        }
                .flatMap { encounterCreate -> encounterRepository.saveEncounter(encounterCreate) }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        { 
                            if (it == ResultType.EncounterSubmissionError) {
                                ToastUtil.error("Submission failed: No active visit found for this patient locally.")
                            }
                            resultLiveData.value = it 
                        },
                        { 
                            ToastUtil.error("Submission error: " + it.message)
                            resultLiveData.value = ResultType.EncounterSubmissionError 
                        }
                )
        )

        return resultLiveData
    }

    private fun updateRecords(encounterUuid: String, enc: Encountercreate): LiveData<ResultType> {
        val resultLiveData = MutableLiveData<ResultType>()

        addSubscription(encounterRepository.updateEncounter(encounterUuid, enc)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        { resultLiveData.value = ResultType.EncounterSubmissionSuccess },
                        { resultLiveData.value = ResultType.EncounterSubmissionError }
                )
        )
        return resultLiveData
    }

    private fun createObservationsFromInputFields(inputFields: List<InputField>): List<Obscreate> {
        val observations = mutableListOf<Obscreate>()

        for (input in inputFields) {
            if (input.value != InputField.DEFAULT_VALUE) {
                observations += Obscreate().apply {
                    concept = input.concept
                    value = input.value.toString()
                    obsDatetime = LocalDateTime().toString()
                    person = patient.uuid
                }
            }
        }

        return observations
    }

    private fun createObservationsFromRadioGroupFields(radioGroupFields: List<SelectOneField>): List<Obscreate> {
        val observations = mutableListOf<Obscreate>()

        for (radioGroupField in radioGroupFields) {
            if (radioGroupField.chosenAnswer != null) {
                observations += Obscreate().apply {
                    concept = radioGroupField.concept
                    value = radioGroupField.chosenAnswer!!.concept
                    obsDatetime = LocalDateTime().toString()
                    person = patient.uuid
                }
            }
        }

        return observations
    }

    private fun createObservationsFromCheckboxFields(checkboxFields: List<SelectMultipleField>): List<Obscreate> {
        val observations = mutableListOf<Obscreate>()

        for (checkboxField in checkboxFields) {
            for (answer in checkboxField.selectedAnswers) {
                observations += Obscreate().apply {
                    concept = checkboxField.concept
                    value = answer.concept
                    obsDatetime = LocalDateTime().toString()
                    person = patient.uuid
                }
            }
        }

        return observations
    }

    private fun createObservationsFromDateFields(dateFields: List<DateField>): List<Obscreate> {
        val observations = mutableListOf<Obscreate>()

        for (dateField in dateFields) {
            if (!dateField.date.isNullOrEmpty()) {
                // Convert DD/MM/YYYY to YYYY-MM-DD
                val parts = dateField.date!!.split("/")
                val formattedDate = if (parts.size == 3) {
                    "${parts[2]}-${parts[1].padStart(2, '0')}-${parts[0].padStart(2, '0')}"
                } else {
                    dateField.date
                }
                observations += Obscreate().apply {
                    concept = dateField.concept
                    value = formattedDate
                    obsDatetime = LocalDateTime().toString()
                    person = patient.uuid
                }
            }
        }

        return observations
    }

    private fun createObservationsFromTextFields(textFields: List<TextField>): List<Obscreate> {
        val observations = mutableListOf<Obscreate>()

        for (textField in textFields) {
            if (!textField.value.isNullOrBlank()) {
                observations += Obscreate().apply {
                    concept = textField.concept
                    value = textField.value
                    obsDatetime = LocalDateTime().toString()
                    person = patient.uuid
                }
            }
        }

        return observations
    }


}
