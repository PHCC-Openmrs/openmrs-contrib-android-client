package org.openmrs.mobile.utilities

import android.content.Context
import android.content.Intent
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.openmrs.android_sdk.library.OpenmrsAndroid
import com.openmrs.android_sdk.library.dao.EncounterCreateRoomDAO
import com.openmrs.android_sdk.library.dao.PatientDAO
import com.openmrs.android_sdk.library.dao.ProgramEnrollmentDAO
import com.openmrs.android_sdk.library.dao.VisitDAO
import com.openmrs.android_sdk.library.databases.AppDatabase
import com.openmrs.android_sdk.library.models.Encountercreate
import com.openmrs.android_sdk.library.models.Patient
import com.openmrs.android_sdk.utilities.ToastUtil
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.MockedStatic
import org.mockito.Mockito.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class SyncModeManagerTest {

    private val context = mock(Context::class.java)
    private val patientDAO = mock(PatientDAO::class.java)
    private val visitDAO = mock(VisitDAO::class.java)
    private val programEnrollmentDAO = mock(ProgramEnrollmentDAO::class.java)
    private val encounterCreateRoomDAO = mock(EncounterCreateRoomDAO::class.java)
    private val broadcastManager = mock(LocalBroadcastManager::class.java)

    private lateinit var openmrsAndroid: MockedStatic<OpenmrsAndroid>
    private lateinit var toastUtil: MockedStatic<ToastUtil>
    private lateinit var appDatabase: MockedStatic<AppDatabase>
    private lateinit var localBroadcastManager: MockedStatic<LocalBroadcastManager>

    private val manager = SyncModeManager(patientDAO, visitDAO, programEnrollmentDAO).apply { switchDelayMs = 0 }

    @Before
    fun setUp() {
        openmrsAndroid = mockStatic(OpenmrsAndroid::class.java)
        toastUtil = mockStatic(ToastUtil::class.java)
        appDatabase = mockStatic(AppDatabase::class.java)
        localBroadcastManager = mockStatic(LocalBroadcastManager::class.java)

        val database = mock(AppDatabase::class.java)
        `when`(database.encounterCreateRoomDAO()).thenReturn(encounterCreateRoomDAO)
        appDatabase.`when`<AppDatabase> { AppDatabase.getDatabase(context) }.thenReturn(database)
        localBroadcastManager.`when`<LocalBroadcastManager> { LocalBroadcastManager.getInstance(context) }.thenReturn(broadcastManager)
        openmrsAndroid.`when`<Boolean> { OpenmrsAndroid.getSyncState() }.thenReturn(true)

        `when`(patientDAO.unSyncedPatients).thenReturn(emptyList())
        `when`(visitDAO.getUnsyncedVisits()).thenReturn(emptyList())
        `when`(programEnrollmentDAO.getUnsyncedEnrollments()).thenReturn(emptyList())
        `when`(encounterCreateRoomDAO.getAllCreatedEncounters()).thenReturn(listOf(Encountercreate().apply { synced = true }))
    }

    @After
    fun tearDown() {
        openmrsAndroid.close()
        toastUtil.close()
        appDatabase.close()
        localBroadcastManager.close()
    }

    @Test
    fun `switches to offline once nothing is left to upload`() {
        manager.switchOfflineIfFullySynced(context)

        openmrsAndroid.verify { OpenmrsAndroid.setSyncState(false) }
        verify(broadcastManager).sendBroadcast(any(Intent::class.java))
    }

    @Test
    fun `stays online while a patient is still unsynced`() {
        `when`(patientDAO.unSyncedPatients).thenReturn(listOf(Patient()))

        manager.switchOfflineIfFullySynced(context)

        assertStayedOnline()
    }

    @Test
    fun `stays online while a form is still queued`() {
        `when`(encounterCreateRoomDAO.getAllCreatedEncounters()).thenReturn(listOf(Encountercreate()))

        manager.switchOfflineIfFullySynced(context)

        assertStayedOnline()
    }

    @Test
    fun `stays online when pending data can't be checked`() {
        `when`(visitDAO.getUnsyncedVisits()).thenThrow(RuntimeException("db closed"))

        manager.switchOfflineIfFullySynced(context)

        assertStayedOnline()
    }

    @Test
    fun `does nothing when already offline`() {
        openmrsAndroid.`when`<Boolean> { OpenmrsAndroid.getSyncState() }.thenReturn(false)

        manager.switchOfflineIfFullySynced(context)

        assertStayedOnline()
    }

    private fun assertStayedOnline() {
        openmrsAndroid.verify({ OpenmrsAndroid.setSyncState(anyBoolean()) }, times(0))
        verify(broadcastManager, never()).sendBroadcast(any(Intent::class.java))
    }
}
