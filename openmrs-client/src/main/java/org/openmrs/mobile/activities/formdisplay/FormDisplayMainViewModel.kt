/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.mobile.activities.formdisplay
import com.openmrs.android_sdk.library.OpenmrsAndroid
import org.openmrs.mobile.R

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import com.openmrs.android_sdk.library.api.repository.EncounterRepository
import com.openmrs.android_sdk.library.api.repository.FormRepository
import com.openmrs.android_sdk.library.dao.PatientDAO
import com.openmrs.android_sdk.library.models.Encountercreate
import com.openmrs.android_sdk.library.models.Patient
import com.openmrs.android_sdk.library.models.ResultType
import com.openmrs.android_sdk.utilities.ApplicationConstants
import com.openmrs.android_sdk.utilities.ToastUtil
import com.openmrs.android_sdk.utilities.ApplicationConstants.BundleKeys.ENCOUNTERTYPE
import com.openmrs.android_sdk.utilities.ApplicationConstants.BundleKeys.ENCOUNTER_UUID
import com.openmrs.android_sdk.utilities.ApplicationConstants.BundleKeys.FORM_NAME
import com.openmrs.android_sdk.utilities.ApplicationConstants.BundleKeys.PATIENT_ID_BUNDLE
import com.openmrs.android_sdk.utilities.DateField
import com.openmrs.android_sdk.utilities.DateUtils
import com.openmrs.android_sdk.utilities.InputField
import com.openmrs.android_sdk.utilities.SelectMultipleField
import com.openmrs.android_sdk.utilities.SelectOneField
import com.openmrs.android_sdk.utilities.TextField
import com.openmrs.android_sdk.utilities.execute
import dagger.hilt.android.lifecycle.HiltViewModel
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

    val patient: Patient = patientDAO.findPatientByID(patientId)

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
        // Captured now, at fill/submit time - same moment as each observation's own obsDatetime -
        // so an offline-filled form keeps the time it was actually filled in rather than the
        // server defaulting it to whenever the encounter eventually gets synced. Must carry an
        // explicit timezone offset (unlike obsDatetime's bare LocalDateTime, which has none): the
        // server otherwise has to guess a timezone for this string when comparing it against "now"
        // to enforce Encounter.datetimeShouldBeBeforeCurrent, and can easily guess wrong and reject
        // an offline submission as being "in the future".
        enc.encounterDatetime = DateUtils.convertTime(System.currentTimeMillis(), DateUtils.OPEN_MRS_REQUEST_FORMAT)
        enc.observations = ObservationBuilder.build(inputFields, radioGroupFields, checkboxFields, dateFields, textFields, patient.uuid)
        // The logged-in user as the encounter's provider, as the web form engine does.
        enc.encounterProvider = OpenmrsAndroid.getCurrentEncounterProviders()

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
                                ToastUtil.error(R.string.form_submission_no_active_visit)
                            }
                            resultLiveData.value = it 
                        },
                        { 
                            ToastUtil.error(R.string.form_submission_error, it.message ?: "")
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
}
