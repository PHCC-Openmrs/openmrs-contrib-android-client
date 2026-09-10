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
        if (patient == null) return
        // Don't let a second call (e.g. a rapid re-tap, or onCreate firing again) start another
        // registration attempt while one for this same patient is still in flight.
        if (runningSyncs > 0) return
        // Re-read from the DB rather than trusting the field captured at ViewModel construction:
        // a background sync (e.g. the auto-sync-on-reconnect batch) can assign this patient a
        // uuid at any time this screen is open, and syncing here on a stale null-uuid snapshot
        // would re-POST a duplicate registration that collides with the one that already
        // succeeded.
        val currentPatient = patientDAO.findPatientByID(patientId) ?: return
        patient = currentPatient
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
        syncDetails(patientToSync)
        syncVisits(patientToSync)
        syncAllergies(patientToSync)
        syncVitals(patientToSync.uuid!!)
    }

    /**
     * Pushes any locally-pending edit for this patient (e.g. one made while offline, still queued
     * for UpdatePatientWorker to eventually pick up) BEFORE pulling from the server. Pulling first
     * would unconditionally overwrite the local record with the server's still-stale copy (see
     * [pullPatientDetails]), silently destroying the local edit before it ever reached the server
     * - which made this screen's own auto-sync-on-open (and the "Synchronize" action, which calls
     * the same method) actively work against the fix that's supposed to deliver a pending offline
     * edit: simply reopening the dashboard to check whether it synced could wipe it out first.
     * Still pulls even if the push itself fails, so the screen at least reflects current server
     * truth rather than getting stuck.
     *
     * Skips the push entirely when [Patient.isIdentityLinkedOnly] is set - this patient's local
     * row only exists because a duplicate-identifier registration got auto-linked to an
     * already-existing server patient by name match (see PatientRepository#syncPatient); its other
     * demographic fields are just whatever was needed to pass validation on that registration
     * form, not a trustworthy description of the real patient, and must never be pushed over the
     * real patient's data - only the identity link and any visit/form data should ever reach the
     * server for such a patient. A deliberate edit via Add/Edit Patient clears this flag, at which
     * point automatic pushes resume as normal.
     */
    private fun syncDetails(patientToSync: Patient) {
        runningSyncs++
        if (patientToSync.isIdentityLinkedOnly) {
            pullPatientDetails(patientToSync.uuid!!)
            return
        }
        addSubscription(patientRepository.updatePatient(patientToSync)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        { pullPatientDetails(patientToSync.uuid!!) },
                        { pullPatientDetails(patientToSync.uuid!!) }
                )
        )
    }

    private fun pullPatientDetails(uuid: String) {
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
