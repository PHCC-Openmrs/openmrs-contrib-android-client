package org.openmrs.mobile.utilities

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.openmrs.android_sdk.library.OpenmrsAndroid
import com.openmrs.android_sdk.library.dao.PatientDAO
import com.openmrs.android_sdk.library.dao.ProgramEnrollmentDAO
import com.openmrs.android_sdk.library.dao.VisitDAO
import com.openmrs.android_sdk.library.databases.AppDatabase
import com.openmrs.android_sdk.utilities.ToastUtil
import org.openmrs.mobile.R
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Offline is the app's default mode for a logged-in user (see OpenMRS.onCreate): the user - or
 * connectivity coming back - turns sync on, everything is uploaded, and the app then drops back
 * to offline by itself. This is that last step, called once the whole
 * PatientService -> VisitService -> ProgramEnrollmentService -> EncounterService chain has run.
 */
@Singleton
class SyncModeManager @Inject constructor(
    private val patientDAO: PatientDAO,
    private val visitDAO: VisitDAO,
    private val programEnrollmentDAO: ProgramEnrollmentDAO
) {

    /** How long sync stays on after everything uploaded, before switching to offline. */
    internal var switchDelayMs = SWITCH_DELAY_MS

    /**
     * Switches to offline mode if nothing is left to upload. Anything still pending (a failed
     * upload, or patients waiting on a possible-duplicate decision) keeps the app online, so the
     * user can see it's not done and retry. Activities refresh their sync icon from
     * [ACTION_SYNC_STATE_CHANGED].
     */
    fun switchOfflineIfFullySynced(context: Context) {
        if (!OpenmrsAndroid.getSyncState() || hasPendingData(context)) return

        // A short pause before switching, so the user sees that a sync actually happened rather
        // than the cloud icon flipping straight back - then checked again, in case anything was
        // queued (or sync was switched off) in the meantime. Called on a sync service's
        // background thread, so the wait blocks nothing the user sees.
        if (switchDelayMs > 0) Thread.sleep(switchDelayMs)
        if (!OpenmrsAndroid.getSyncState() || hasPendingData(context)) return

        OpenmrsAndroid.setSyncState(false)
        ToastUtil.notify(R.string.sync_completed_switched_offline)
        LocalBroadcastManager.getInstance(context).sendBroadcast(Intent(ACTION_SYNC_STATE_CHANGED))
    }

    private fun hasPendingData(context: Context): Boolean = try {
        patientDAO.unSyncedPatients.isNotEmpty() ||
            visitDAO.getUnsyncedVisits().isNotEmpty() ||
            programEnrollmentDAO.getUnsyncedEnrollments().isNotEmpty() ||
            AppDatabase.getDatabase(context).encounterCreateRoomDAO().getAllCreatedEncounters().any { !it.synced }
    } catch (e: Exception) {
        // Can't tell - staying online is the safe side.
        Log.e(TAG, "Could not check for pending sync data, staying online", e)
        true
    }

    companion object {
        const val ACTION_SYNC_STATE_CHANGED = "org.openmrs.mobile.intent.action.SYNC_STATE_CHANGED"
        private const val SWITCH_DELAY_MS = 3000L
        private const val TAG = "SyncModeManager"
    }
}
