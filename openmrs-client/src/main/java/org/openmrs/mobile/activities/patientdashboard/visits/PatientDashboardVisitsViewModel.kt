package org.openmrs.mobile.activities.patientdashboard.visits

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import com.openmrs.android_sdk.library.api.repository.VisitRepository
import com.openmrs.android_sdk.library.dao.PatientDAO
import com.openmrs.android_sdk.library.dao.VisitDAO
import com.openmrs.android_sdk.library.models.OperationType.PatientVisitStarting
import com.openmrs.android_sdk.library.models.OperationType.PatientVisitsFetching
import com.openmrs.android_sdk.library.models.Patient
import com.openmrs.android_sdk.library.models.Visit
import com.openmrs.android_sdk.utilities.ApplicationConstants.BundleKeys.PATIENT_ID_BUNDLE
import com.openmrs.android_sdk.utilities.NetworkUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import org.openmrs.mobile.activities.BaseViewModel
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import javax.inject.Inject

@HiltViewModel
class PatientDashboardVisitsViewModel @Inject constructor(
        private val patientDAO: PatientDAO,
        private val visitDAO: VisitDAO,
        private val visitRepository: VisitRepository,
        private val savedStateHandle: SavedStateHandle
) : BaseViewModel<List<Visit>>() {

    private val patientId: Long = savedStateHandle.get(PATIENT_ID_BUNDLE)!!

    fun getPatient(): Patient = patientDAO.findPatientByID(patientId)

    /**
     * Refreshes this patient's visits and shows them. The Visits tab previously only ever read
     * from the local cache - a visit created elsewhere (the web app, another device) would never
     * appear here until something else happened to trigger a sync (e.g. reopening the whole
     * Patient Dashboard, which independently refreshes visits via `syncPatientData()`). This pulls
     * from the server first, when possible, so opening/returning to this tab is itself enough.
     * Falls back to the local cache if we're offline, the patient isn't synced yet, or the
     * server refresh fails - it never blocks showing whatever is already known locally.
     */
    fun fetchVisitsData() {
        setLoading(PatientVisitsFetching)
        val patient = getPatient()

        val refreshFromServer: Observable<Unit> =
            if (NetworkUtils.isOnline() && !patient.uuid.isNullOrEmpty()) {
                visitRepository.syncVisitsData(patient)
                    .map { Unit }
                    .onErrorReturn { Unit }
            } else {
                Observable.just(Unit)
            }

        addSubscription(refreshFromServer
                .flatMap { visitDAO.getVisitsByPatientID(patientId) }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        { visits: List<Visit> -> setContent(visits, PatientVisitsFetching) },
                        { setError(it, PatientVisitsFetching) }
                ))
    }

    fun hasActiveVisit(): LiveData<Boolean> {
        val liveData = MutableLiveData<Boolean>()
        addSubscription(visitDAO.getActiveVisitByPatientId(patientId)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { visit: Visit? -> liveData.value = visit != null })
        return liveData
    }

    fun startVisit() {
        setLoading(PatientVisitStarting)
        val patient = patientDAO.findPatientByID(patientId)
        addSubscription(visitRepository.startVisit(patient)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        { visit: Visit -> setContent(listOf(visit), PatientVisitStarting) },
                        { setError(it, PatientVisitStarting) }
                ))
    }
}
