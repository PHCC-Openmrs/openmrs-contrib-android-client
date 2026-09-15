package org.openmrs.mobile.test.viewmodels

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import com.openmrs.android_sdk.library.api.repository.VisitRepository
import com.openmrs.android_sdk.library.dao.PatientDAO
import com.openmrs.android_sdk.library.dao.VisitDAO
import com.openmrs.android_sdk.library.models.Patient
import com.openmrs.android_sdk.library.models.Result
import com.openmrs.android_sdk.library.models.Visit
import com.openmrs.android_sdk.utilities.ApplicationConstants.BundleKeys.PATIENT_ID_BUNDLE
import com.openmrs.android_sdk.utilities.NetworkUtils
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertIterableEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mock
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.openmrs.mobile.activities.patientdashboard.visits.PatientDashboardVisitsViewModel
import org.openmrs.mobile.test.ACUnitTestBaseRx
import rx.Observable

@RunWith(JUnit4::class)
class PatientDashboardVisitsViewModelTest : ACUnitTestBaseRx() {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @Mock
    lateinit var patientDAO: PatientDAO

    @Mock
    lateinit var visitDAO: VisitDAO

    @Mock
    lateinit var visitRepository: VisitRepository

    lateinit var savedStateHandle: SavedStateHandle

    lateinit var viewModel: PatientDashboardVisitsViewModel

    private lateinit var networkUtilsMock: MockedStatic<NetworkUtils>

    lateinit var patient: Patient

    lateinit var visitList: List<Visit>

    @Before
    override fun setUp() {
        super.setUp()
        savedStateHandle = SavedStateHandle().apply { set(PATIENT_ID_BUNDLE, PATIENT_ID) }
        viewModel = PatientDashboardVisitsViewModel(patientDAO, visitDAO, visitRepository, savedStateHandle)
        patient = createPatient(PATIENT_ID)
        visitList = createVisitList()
        // The ViewModel resolves the patient on every fetch and asserts non-null.
        Mockito.`when`(patientDAO.findPatientByID(PATIENT_ID)).thenReturn(patient)
        // fetchVisitsData() consults NetworkUtils, which reaches for a real ConnectivityManager.
        // Offline keeps these tests on the local-cache path they assert against.
        networkUtilsMock = Mockito.mockStatic(NetworkUtils::class.java)
        Mockito.`when`(NetworkUtils.isOnline()).thenReturn(false)
    }

    @After
    fun closeStaticMocks() {
        networkUtilsMock.close()
    }

    @Test
    fun fetchVisitsData_success() {
        Mockito.`when`(visitDAO.getVisitsByPatientID(PATIENT_ID)).thenReturn(Observable.just(visitList))

        viewModel.fetchVisitsData()

        val actualResult = (viewModel.result.value as Result.Success).data

        assertIterableEquals(visitList, actualResult)
    }

    @Test
    fun fetchVisitsData_error() {
        val errorMsg = "Error message!"
        val throwable = Throwable(errorMsg)
        Mockito.`when`(visitDAO.getVisitsByPatientID(PATIENT_ID)).thenReturn(Observable.error(throwable))


        viewModel.fetchVisitsData()

        val actualResult = (viewModel.result.value as Result.Error).throwable.message

        assertEquals(errorMsg, actualResult)
    }

    @Test
    fun hasActiveVisit_shouldBeTrue() {
        val visit = visitList[0]
        Mockito.`when`(visitDAO.getActiveVisitByPatientId(PATIENT_ID)).thenReturn(Observable.just(visit))

        viewModel.hasActiveVisit().observeForever { hasActiveVisit -> assertTrue(hasActiveVisit) }
    }

    @Test
    fun hasActiveVisit_shouldBeFalse() {
        val visit = null
        Mockito.`when`(visitDAO.getActiveVisitByPatientId(PATIENT_ID)).thenReturn(Observable.just(visit))

        viewModel.hasActiveVisit().observeForever { hasActiveVisit -> assertFalse(hasActiveVisit) }
    }

    companion object {
        const val PATIENT_ID = 1L
    }
}
