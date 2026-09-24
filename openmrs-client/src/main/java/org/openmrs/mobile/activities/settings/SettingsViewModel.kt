package org.openmrs.mobile.activities.settings

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import com.openmrs.android_sdk.library.OpenMRSLogger
import com.openmrs.android_sdk.library.OpenmrsAndroid
import com.openmrs.android_sdk.library.api.repository.ConceptRepository
import com.openmrs.android_sdk.utilities.ApplicationConstants.PACKAGE_NAME
import dagger.hilt.android.lifecycle.HiltViewModel
import org.openmrs.mobile.R
import org.openmrs.mobile.activities.BaseViewModel
import org.openmrs.mobile.utilities.LanguageUtils
import javax.inject.Inject
import java.io.File

@HiltViewModel
class SettingsViewModel @Inject constructor(
        private val conceptRepository: ConceptRepository,
        private val openMRSLogger: OpenMRSLogger
) : BaseViewModel<Unit>() {

    val logsFileName = OpenmrsAndroid.getOpenMRSDir() + File.separator + openMRSLogger.logFilename
    var logSize: Long = 0
    val appMarketUri: Uri = Uri.parse("market://details?id=${PACKAGE_NAME}")
    val appLinkUri: Uri = Uri.parse("http://play.google.com/store/apps/details?id=$PACKAGE_NAME")
    val languageOptions: List<LanguageUtils.LanguageOption> by lazy { LanguageUtils.getSelectableLanguages() }

    val languageListPosition: Int
        get() = LanguageUtils.indexOfLanguage(languageOptions, LanguageUtils.getLanguage())

    fun onLanguageSelected(position: Int) {
        // The spinner reports its initial selection as soon as it's laid out. Saving that would
        // turn "never chose a language" into an explicit choice just by opening this screen,
        // and the app would stop following the server's locale for the user.
        if (position == languageListPosition) return
        LanguageUtils.setLanguage(languageOptions[position].tag)
    }


    init {
        getLogsFileInfo()
    }

    private fun getLogsFileInfo() {
        try {
            val file = File(logsFileName)
            logSize = file.length()
            logSize /= ONE_KB
            openMRSLogger.i("File Path :${file.path} , File size: $logSize KB")
        } catch (e: Exception) {
            openMRSLogger.w("File not found")
        }
    }

    fun getBuildVersionInfo(context: Context): String {
        var versionName = ""
        var buildVersion = 0
        val packageManager = context.packageManager
        val packageName = context.packageName
        try {
            versionName = packageManager.getPackageInfo(packageName, 0).versionName ?: ""
            val ai = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
            buildVersion = ai.metaData.getInt("buildVersion")
        } catch (e: PackageManager.NameNotFoundException) {
            openMRSLogger.e("Failed to load meta-data, NameNotFound: ${e.message}")
        } catch (e: NullPointerException) {
            openMRSLogger.e("Failed to load meta-data, NullPointer: ${e.message}")
        }

        return versionName + context.getString(R.string.frag_settings_build) + buildVersion
    }

    fun getConceptsCount(): String = conceptRepository.getConceptCountFromDb().toString()

    companion object {
        private const val ONE_KB = 1024
    }
}
