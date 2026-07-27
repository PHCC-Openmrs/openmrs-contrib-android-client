package org.openmrs.mobile.activities.patientdashboard

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import com.openmrs.android_sdk.library.api.repository.AllergyRepository
import com.openmrs.android_sdk.library.api.repository.PatientRepository
import com.openmrs.android_sdk.library.api.repository.VisitRepository
import com.openmrs.android_sdk.library.dao.PatientDAO
import com.openmrs.android_sdk.library.dao.VisitDAO
import com.openmrs.android_sdk.library.models.OperationType
import com.openmrs.android_sdk.library.models.OperationType.PatientDeleting
import com.openmrs.android_sdk.library.models.OperationType.PatientSynchronizing
import com.openmrs.android_sdk.library.models.Patient
import com.openmrs.android_sdk.utilities.ApplicationConstants.BundleKeys.PATIENT_ID_BUNDLE
import dagger.hilt.android.lifecycle.HiltViewModel
import org.openmrs.mobile.activities.BaseViewModel
import rx.android.schedulers.AndroidSchedulers
import javax.inject.Inject


@HiltViewModel
class PatientDashboardMainViewModel @Inject constructor(
        private val patientDAO: PatientDAO,
        private val visitDAO: VisitDAO,
        private val patientRepository: PatientRepository,
        private val visitRepository: VisitRepository,
        private val allergyRepository: AllergyRepository,
        private val savedStateHandle: SavedStateHandle
) : BaseViewModel<Unit>() {

    val patientId: Long = savedStateHandle.get<Long>(PATIENT_ID_BUNDLE)!!
    private var patient: Patient? = patientDAO.findPatientByID(patientId)

    /**
     * False when [patientId] doesn't correspond to any locally stored patient (e.g. a stale
     * "recently viewed"/restored-activity reference to a patient that's no longer cached
     * locally). The Activity checks this right after construction and finishes instead of
     * proceeding, since every other method here assumes a loaded patient.
     */
    val isPatientFound: Boolean get() = patient != null

    private var runningSyncs = 0

    fun deletePatient() {
        setLoading(PatientDeleting)
        patientDAO.deletePatient(patientId)
        addSubscription(visitDAO.deleteVisitsByPatientId(patientId)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        { setContent(Unit, PatientDeleting) },
                        { setError(it, PatientDeleting) }
                )
        )
    }

    fun syncPatientData() {
        val currentPatient = patient ?: return
        setLoading(PatientSynchronizing)
        if (currentPatient.uuid.isNullOrEmpty()) {
            syncUnsyncedPatient(currentPatient)
        } else {
            syncAllData(currentPatient)
        }
    }

    private fun syncUnsyncedPatient(unsyncedPatient: Patient) {
        runningSyncs++
        addSubscription(patientRepository.syncPatient(unsyncedPatient)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        { syncedPatient ->
                            runningSyncs--
                            // Update our local reference with the newly synced patient (it now has a UUID)
                            patient = syncedPatient
                            syncAllData(syncedPatient)
                        },
                        { setError(it, PatientSynchronizing) }
                )
        )
    }

    private fun syncAllData(patientToSync: Patient) {
        syncDetails(patientToSync.uuid!!)
        syncVisits(patientToSync)
        syncAllergies(patientToSync)
        syncVitals(patientToSync.uuid!!)
    }

    private fun syncDetails(uuid: String) {
        runningSyncs++
        addSubscription(patientRepository.downloadPatientByUuid(uuid)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        { downloadedPatient ->
                            downloadedPatient.id = patientId
                            patientDAO.updatePatient(patientId, downloadedPatient)
                            setContent(Unit, PatientSynchronizing)
                        },
                        { setError(it, PatientSynchronizing) }
                )
        )
    }

    private fun syncVisits(patientToSync: Patient) {
        runningSyncs++
        addSubscription(visitRepository.syncVisitsData(patientToSync)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        { setContent(Unit, PatientSynchronizing) },
                        { setError(it, PatientSynchronizing) }
                ))
    }

    private fun syncAllergies(patientToSync: Patient) {
        runningSyncs++
        addSubscription(allergyRepository.syncAllergies(patientToSync)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        { setContent(Unit, PatientSynchronizing) },
                        { setError(it, PatientSynchronizing) }
                )
        )
    }

    private fun syncVitals(uuid: String) {
        runningSyncs++
        addSubscription(visitRepository.syncLastVitals(uuid)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        { setContent(Unit, PatientSynchronizing) },
                        { setError(it, PatientSynchronizing) }
                )
        )
    }

    override fun setContent(data: Unit, operationType: OperationType) {
        if (operationType == PatientSynchronizing) {
            runningSyncs--
            // Check if no syncs are still running
            if (runningSyncs == 0) super.setContent(data, operationType)
        } else {
            super.setContent(data, operationType)
        }
    }

    override fun setError(t: Throwable, operationType: OperationType) {
        Log.d("GeneralLogKey", " setError: ${t.message}")
        if (operationType == PatientSynchronizing) clearSubscriptions()
        super.setError(t, operationType)
    }
}
