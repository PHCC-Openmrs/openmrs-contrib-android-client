/*
 * The contents of this file are subject to the OpenMRS Public License
 * Version 1.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://license.openmrs.org
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations
 * under the License.
 *
 * Copyright (C) OpenMRS, LLC.  All Rights Reserved.
 */
package org.openmrs.mobile.activities.startvisit

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import com.openmrs.android_sdk.library.OpenmrsAndroid
import com.openmrs.android_sdk.library.api.repository.ProgramRepository
import com.openmrs.android_sdk.library.api.repository.VisitAttributeTypeRepository
import com.openmrs.android_sdk.library.api.repository.VisitRepository
import com.openmrs.android_sdk.library.dao.LocationDAO
import com.openmrs.android_sdk.library.dao.PatientDAO
import com.openmrs.android_sdk.library.databases.entities.LocationEntity
import com.openmrs.android_sdk.library.databases.entities.ProgramEntity
import com.openmrs.android_sdk.library.models.Patient
import com.openmrs.android_sdk.library.models.Visit
import com.openmrs.android_sdk.library.models.VisitAttribute
import com.openmrs.android_sdk.library.models.VisitAttributeType
import com.openmrs.android_sdk.utilities.ApplicationConstants.BundleKeys.PATIENT_ID_BUNDLE
import com.openmrs.android_sdk.utilities.ApplicationConstants.VisitAttributeTypes
import dagger.hilt.android.lifecycle.HiltViewModel
import org.openmrs.mobile.activities.BaseViewModel
import rx.android.schedulers.AndroidSchedulers
import javax.inject.Inject

/**
 * Backs the "Start visit" screen, mirroring the web client's start-visit form: the location the
 * visit takes place at, the service(s) it is for, and the extra questions configured for a visit
 * (Punctuality, ...).
 *
 * The visit's start date/time is not a field on this form - it is recorded as the moment the user
 * actually submits (see [startVisit]), not whenever the form happened to be opened, so a visit
 * started at 13:15 and submitted at 13:17 is timestamped 13:17.
 *
 * Every list the form offers is read network-first and cached, so the same form with the same
 * choices is available offline; submitting always saves locally first and pushes afterwards, so a
 * visit - services, attributes and all - can be started with no connectivity at all.
 */
@HiltViewModel
class StartVisitViewModel @Inject constructor(
    private val patientDAO: PatientDAO,
    private val locationDAO: LocationDAO,
    private val visitRepository: VisitRepository,
    private val programRepository: ProgramRepository,
    private val visitAttributeTypeRepository: VisitAttributeTypeRepository,
    private val savedStateHandle: SavedStateHandle
) : BaseViewModel<Unit>() {

    private val patientId: Long = savedStateHandle.get(PATIENT_ID_BUNDLE)!!

    val patient: Patient by lazy { patientDAO.findPatientByID(patientId) }

    /** Login locations, keyed by the text shown in the picker. */
    var locations: LinkedHashMap<String, LocationEntity> = linkedMapOf()
        private set

    /** The services offered at [selectedLocation], in the order the server lists them. */
    var servicePrograms: List<ProgramEntity> = emptyList()
        private set

    /** The extra questions configured for a visit, with the answers of the coded ones. */
    var visitAttributeTypes: List<VisitAttributeType> = emptyList()
        private set

    var locationListPosition: Int = 0
        private set

    val selectedLocation: LocationEntity?
        get() = locations.values.toList().getOrNull(locationListPosition)

    /** The selected services, as program uuids. At least one is required, as in the web client. */
    val selectedServiceUuids: MutableSet<String> = linkedSetOf()

    /** Answers to the extra questions so far, keyed by visit attribute type uuid. */
    val attributeValues: MutableMap<String, String> = mutableMapOf()

    private val _serviceProgramsUpdated = MutableLiveData<Boolean>()

    /** Emits whenever [servicePrograms] has been reloaded for a newly-chosen location. */
    val serviceProgramsUpdated: LiveData<Boolean> get() = _serviceProgramsUpdated

    init {
        loadFormFields()
    }

    /**
     * Loads the pickers' contents: the locations first, since the default one decides which
     * services are on offer, then those services and the visit attribute types.
     */
    private fun loadFormFields() {
        setLoading()
        addSubscription(locationDAO.getLocations()
            .map { locationList ->
                locations = LinkedHashMap<String, LocationEntity>().apply {
                    locationList.forEach { put(it.display ?: it.name.orEmpty(), it) }
                }
                // Default to the location the user logged in at, matching the web client's
                // defaulting to the session location.
                val sessionLocation = OpenmrsAndroid.getLocation()
                locations.keys.toList().indexOf(sessionLocation).takeIf { it >= 0 }
                    ?.let { locationListPosition = it }
                Unit
            }
            .flatMap { programRepository.getServicePrograms(selectedLocation?.uuid) }
            .map { programs ->
                servicePrograms = programs
                Unit
            }
            .flatMap { visitAttributeTypeRepository.getFormVisitAttributeTypes() }
            .map { types ->
                visitAttributeTypes = types
                Unit
            }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { setContent(Unit) },
                { setError(it) }
            )
        )
    }

    /**
     * Points the form at another location and reloads the services offered there, dropping any
     * already-selected service that is not among them - the web client's Service field does the
     * same rather than silently submitting a service the new location does not offer.
     *
     * @param listPosition the position picked in the location picker
     */
    fun selectLocation(listPosition: Int) {
        // The picker reports its initial binding as a selection too; the services for that
        // position were already loaded by loadFormFields, so an unchanged position is a no-op.
        if (listPosition == locationListPosition) return
        locationListPosition = listPosition

        addSubscription(programRepository.getServicePrograms(selectedLocation?.uuid)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { programs ->
                    servicePrograms = programs
                    selectedServiceUuids.retainAll(programs.mapNotNull { it.uuid }.toSet())
                    _serviceProgramsUpdated.value = true
                },
                {
                    servicePrograms = emptyList()
                    selectedServiceUuids.clear()
                    _serviceProgramsUpdated.value = true
                }
            )
        )
    }

    /**
     * Starts the visit with everything the form collected.
     *
     * The Service selection travels as a single comma-separated attribute value because the
     * Service attribute type allows only one occurrence - exactly how the web client stores it, so
     * both clients read each other's visits.
     *
     * @return the started visit, or an error; the visit has no uuid yet when it was saved offline
     */
    fun startVisit(): LiveData<Visit?> {
        // Reported through its own LiveData rather than through `result`, which stands for the
        // state of the form itself: re-emitting there would have the observer rebuild every field
        // from scratch just as the visit is being submitted.
        val startedVisit = MutableLiveData<Visit?>()

        val attributes = mutableListOf<VisitAttribute>()
        if (selectedServiceUuids.isNotEmpty()) {
            attributes.add(
                VisitAttribute(
                    VisitAttributeTypes.SERVICE_UUID,
                    selectedServiceUuids.joinToString(VisitAttributeTypes.SERVICE_VALUE_SEPARATOR)
                )
            )
        }
        visitAttributeTypes.forEach { type ->
            val value = attributeValues[type.uuid]
            if (!value.isNullOrEmpty()) attributes.add(VisitAttribute(type.uuid, value))
        }

        // A null start datetime has VisitRepository.startVisit stamp the visit with the moment
        // this actually runs, i.e. right now - not whenever the form was opened.
        addSubscription(visitRepository.startVisit(
            patient,
            selectedLocation,
            null,
            attributes
        )
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { visit -> startedVisit.value = visit },
                { startedVisit.value = null }
            )
        )

        return startedVisit
    }
}
