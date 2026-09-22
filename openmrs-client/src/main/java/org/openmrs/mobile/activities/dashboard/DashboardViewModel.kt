package org.openmrs.mobile.activities.dashboard

import com.openmrs.android_sdk.library.api.repository.ConceptRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import org.openmrs.mobile.activities.BaseViewModel
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
        private val conceptRepository: ConceptRepository
) : BaseViewModel<Unit>() {

    /**
     * True when this device has no locally-cached concepts yet - i.e. "Download Concepts" (which
     * also primes every form's schema for offline use) has never been run. Used to decide whether
     * opening the dashboard right after a successful login should kick that off automatically,
     * rather than requiring the user to remember to visit Settings before their first time going
     * offline.
     */
    fun hasNoConceptsDownloaded(): Boolean = conceptRepository.getConceptCountFromDb() == 0L
}
