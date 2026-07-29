package org.openmrs.mobile.activities.patientdashboard.diagnosis

import androidx.lifecycle.SavedStateHandle
import com.openmrs.android_sdk.library.api.repository.EncounterRepository
import com.openmrs.android_sdk.library.dao.EncounterDAO
import com.openmrs.android_sdk.library.dao.PatientDAO
import com.openmrs.android_sdk.library.models.Encounter
import com.openmrs.android_sdk.library.models.EncounterType
import com.openmrs.android_sdk.library.models.EncounterType.Companion.VISIT_NOTE
import com.openmrs.android_sdk.utilities.ApplicationConstants.BundleKeys.PATIENT_ID_BUNDLE
import dagger.hilt.android.lifecycle.HiltViewModel
import org.openmrs.mobile.activities.BaseViewModel
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import javax.inject.Inject
import java.util.ArrayList

@HiltViewModel
class PatientDashboardDiagnosisViewModel @Inject constructor(
        private val encounterDAO: EncounterDAO,
        private val patientDAO: PatientDAO,
        private val encounterRepository: EncounterRepository,
        private val savedStateHandle: SavedStateHandle
) : BaseViewModel<List<String>>() {

    private val patientId: Long = savedStateHandle.get(PATIENT_ID_BUNDLE)!!

    fun fetchDiagnoses() {
        setLoading()
        addSubscription(encounterDAO.getAllEncountersByType(patientId, EncounterType(VISIT_NOTE))
                .map { encounters -> loadDiagnosesFromEncounters(encounters) }
                .flatMap { legacyDiagnoses ->
                    // Diagnoses saved via the native Visit Note screen live on the encounter's
                    // `diagnoses` array (the /patientdiagnoses resource), not as obs - only the
                    // server has this, so merge it in on top of the locally cached legacy list.
                    fetchDiagnosesFromServer()
                        .map { serverDiagnoses -> (legacyDiagnoses + serverDiagnoses).distinct() }
                        .onErrorReturn { legacyDiagnoses }
                }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { diagnoses -> setContent(diagnoses) }
        )
    }

    private fun fetchDiagnosesFromServer(): Observable<List<String>> {
        val patientUuid = patientDAO.findPatientByID(patientId)?.uuid
            ?: return Observable.just(emptyList())

        return encounterRepository
            .getAllEncountersByPatientUuidAndEncounterTypeAndSaveLocally(patientUuid, VISIT_NOTE_ENCOUNTER_TYPE_UUID)
            .map { encounters ->
                encounters
                    .flatMap { it.diagnoses }
                    .filter { it.voided != true }
                    .mapNotNull { it.diagnosis?.coded?.display }
            }
    }

    private fun loadDiagnosesFromEncounters(encounters: List<Encounter>): List<String> {
        val diagnoses = ArrayList<String>()
        for (encounter in encounters) {
            for (obs in encounter.observations) {
                if (!obs.diagnosisList.isNullOrEmpty() && !diagnoses.contains(obs.diagnosisList!!)) {
                    diagnoses.add(obs.diagnosisList!!)
                }
            }
        }
        return diagnoses
    }

    companion object {
        private const val VISIT_NOTE_ENCOUNTER_TYPE_UUID = "d7151f82-c1f3-4152-a605-2f9ea7414a79"
    }
}
