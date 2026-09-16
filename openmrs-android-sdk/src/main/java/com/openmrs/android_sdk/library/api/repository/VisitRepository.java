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

package com.openmrs.android_sdk.library.api.repository;

import static com.openmrs.android_sdk.utilities.DateUtils.OPEN_MRS_REQUEST_FORMAT;
import static com.openmrs.android_sdk.utilities.DateUtils.convertTime;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import retrofit2.Call;
import retrofit2.Response;
import rx.Observable;

import androidx.annotation.NonNull;

import com.openmrs.android_sdk.library.OpenmrsAndroid;
import com.openmrs.android_sdk.library.dao.EncounterDAO;
import com.openmrs.android_sdk.library.dao.LocationDAO;
import com.openmrs.android_sdk.library.dao.VisitDAO;
import com.openmrs.android_sdk.library.databases.AppDatabaseHelper;
import com.openmrs.android_sdk.library.databases.entities.LocationEntity;
import com.openmrs.android_sdk.library.models.Encounter;
import com.openmrs.android_sdk.library.models.Patient;
import com.openmrs.android_sdk.library.models.Results;
import com.openmrs.android_sdk.library.models.Visit;
import com.openmrs.android_sdk.library.models.VisitAttribute;
import com.openmrs.android_sdk.library.models.VisitType;
import com.openmrs.android_sdk.utilities.ApplicationConstants;
import com.openmrs.android_sdk.utilities.DateUtils;
import com.openmrs.android_sdk.utilities.NetworkUtils;
import com.openmrs.android_sdk.utilities.SyncedPatientCleanupUtil;


/**
 * The type Visit repository.
 */
@Singleton
public class VisitRepository extends BaseRepository {

    public LocationDAO locationDAO;
    public VisitDAO visitDAO;
    public EncounterDAO encounterDAO;
    public ProgramEnrollmentRepository programEnrollmentRepository;

    // Visits carry their attributes down with them so a visit started on the web (or on another
    // device) still says which service(s) it is for once it reaches this device - which is what
    // ending it has to know in order to complete the matching enrollment episodes.
    String representation = "custom:(uuid,location:ref,visitType:ref,startDatetime,stopDatetime,attributes:(uuid,value,attributeType:(uuid,display)),encounters:full)";

    /**
     * Instantiates a new Visit repository.
     */
    @Inject
    public VisitRepository(VisitDAO visitDAO, EncounterDAO encounterDAO, LocationDAO locationDAO,
                           ProgramEnrollmentRepository programEnrollmentRepository) {
        this.visitDAO = visitDAO;
        this.encounterDAO = encounterDAO;
        this.locationDAO = locationDAO;
        this.programEnrollmentRepository = programEnrollmentRepository;
    }

    /**
     * Executes a Retrofit request
     *
     * @param call    the interface call
     * @param message the error message to display
     * @param <T>     the response type
     * @return T
     * @throws Exception if an error occurs during the request
     */
    public <T> T executeRequest(Call<T> call, String message) throws Exception {
        Response<T> response = call.execute();

        if (response.isSuccessful() && response.body() != null) {
            return response.body();
        } else {
            logger.e(message + response.message());
            throw new Exception(response.message());
        }
    }

    public List<Visit> fetchVisitsAndSave(Call<Results<Visit>> call, Patient patient) throws IOException {
        Response<Results<Visit>> response = call.execute();

        if (response.isSuccessful()) {
            List<Visit> visits = response.body().getResults();
            for (Visit visit : visits) {
                visitDAO.saveOrUpdate(visit, patient.getId()).toBlocking().subscribe();
            }
            return visits;
        } else {
            throw new IOException("Error with fetching visits by patient UUID: " + response.message());
        }
    }

    /**
     * This method downloads visits data asynchronously from the server.
     *
     * @param patient the patient
     */
    public Observable<List<Visit>> syncVisitsData(@NonNull final Patient patient) {
        if (patient.getUuid() == null || patient.getUuid().isEmpty()) {
            return Observable.error(new IllegalArgumentException("Patient UUID cannot be null or empty"));
        }
        return AppDatabaseHelper.createObservableIO(() -> {
            Call<Results<Visit>> call = restApi.findVisitsByPatientUUID(patient.getUuid(), representation);
            return fetchVisitsAndSave(call, patient);
        });
    }

    /**
     * Get a particular Visit of a patient from the server
     *
     * @param visitUuid the UUID of the visit.
     */
    public Observable<Visit> getVisit(String visitUuid){
        if (visitUuid == null || visitUuid.isEmpty()) {
            return Observable.error(new IllegalArgumentException("Visit UUID cannot be null or empty"));
        }
        return AppDatabaseHelper.createObservableIO(() -> {
            Call<Visit> call = restApi.getVisitFromUuid(visitUuid);
            Response<Visit> response = call.execute();

            if (response.isSuccessful()) {
                Visit visit = response.body();
                return visit;
            } else {
                throw new IOException("Error with fetching visit by visit uuid: " + response.message());
            }
        });
    }


    /**
     * This method is used for fetching VisitType asynchronously.
     *
     * @return Observable VisitType object or null
     * @see VisitType
     */
    public Observable<VisitType> getVisitType() {
        return AppDatabaseHelper.createObservableIO(() -> {
            Response<Results<VisitType>> response = restApi.getVisitType().execute();
            if (response.isSuccessful() && response.body() != null && !response.body().getResults().isEmpty()) return response.body().getResults().get(0);
            else return null;
        });
    }


    /**
     * This method is used to sync Vitals of a patient in a visit
     *
     * @param patientUuid Patient UUID to get vitals from
     * @return Encounter observable containing last vitals
     */
    public Observable<Encounter> syncLastVitals(final String patientUuid) {
        if (patientUuid == null || patientUuid.isEmpty()) {
            return Observable.error(new IllegalArgumentException("Patient UUID cannot be null or empty"));
        }
        return AppDatabaseHelper.createObservableIO(() -> {
            Call<Results<Encounter>> call = restApi.getLastVitals(patientUuid, ApplicationConstants.EncounterTypes.VITALS, "full", 1, "desc");
            Response<Results<Encounter>> response = call.execute();

            if (response.isSuccessful() && response.body() != null) {
                if (!response.body().getResults().isEmpty()) {
                    Encounter encounter = response.body().getResults().get(0);
                    encounterDAO.saveLastVitalsEncounter(encounter, patientUuid);
                    return encounter;
                }
                return new Encounter();
            } else {
                throw new IOException("Error with fetching last vitals: " + response.message());
            }
        });
    }

    /**
     * This method ends an active visit of a patient.
     *
     * @param visit visit to be ended
     * @return observable boolean true if operation is successful
     * @see Visit
     */
    public Observable<Boolean> endVisit(Visit visit) {
        return AppDatabaseHelper.createObservableIO(() -> {
            // Don't pass the full visit to the API as it will return an error, instead create an empty visit.
            Visit emptyVisitWithStopDate = new Visit();
            emptyVisitWithStopDate.setStopDatetime(convertTime(System.currentTimeMillis(), OPEN_MRS_REQUEST_FORMAT));

            Response<Visit> response = restApi.endVisitByUUID(visit.getUuid(), emptyVisitWithStopDate).execute();
            if (response.isSuccessful()) {
                visit.setStopDatetime(emptyVisitWithStopDate.getStopDatetime());
                visitDAO.saveOrUpdate(visit, visit.patient.getId()).single().toBlocking().first();
                // A visit ending closes the service enrollment episode(s) it opened - the same
                // thing the web client's `visit-ended` listener does. Deliberately after the visit
                // itself is ended and saved, and never allowed to throw: the visit has ended, and
                // must not be reopened over a failure to complete an enrollment.
                completeServiceEnrollments(visit, emptyVisitWithStopDate.getStopDatetime());
                return true;
            } else {
                throw new Exception("endVisitByUuid error: " + response.message());
            }
        });
    }

    /**
     * Completes the program-enrolment episodes a visit opened, as of when it ended. Swallows any
     * failure: the visit has already ended, and an enrollment left open is retried on the next
     * sync pass, whereas a thrown exception here would report the whole end-visit as failed.
     *
     * @param visit        the visit that has just ended
     * @param stopDatetime when it ended
     */
    private void completeServiceEnrollments(Visit visit, String stopDatetime) {
        try {
            programEnrollmentRepository.completeEnrollmentsForVisit(visit, stopDatetime);
        } catch (Exception e) {
            getLogger().e("Could not complete this visit's service enrollments: " + e.getMessage());
        }
    }

    /**
     * Start visit for a patient.
     *
     * <p>Always saves the visit locally first (mirrors {@code PatientRepository#registerPatient}),
     * so it's immediately usable - e.g. so a form can be filled in - even before, or without, the
     * server confirming it. See {@link #syncStartedVisit} for when the server push actually
     * happens.
     *
     * @param patient the patient to start a visit for
     * @return observable visit that has been started (locally, and on the server if possible)
     */
    public Observable<Visit> startVisit(final Patient patient) {
        return startVisit(patient, null, null, Collections.emptyList());
    }

    /**
     * Start a visit for a patient, with the details the start-visit form collected.
     *
     * <p>Mirrors the web client's start-visit form: the visit is recorded at the chosen location,
     * from the chosen date and time, carrying the answers to the form's extra questions - which
     * service(s) it is for, punctuality, and whatever else is configured. Starting a visit for a
     * service also opens a program enrolment episode for that service, completed when the visit
     * ends.
     *
     * <p>Everything is saved locally first, so a visit can be started - form-fillable, services
     * and all - with no connectivity at all, and pushed whole once there is some.
     *
     * @param patient       the patient to start a visit for
     * @param location      where the visit takes place; the session location when null
     * @param startDatetime when the visit starts, OpenMRS-formatted; now when null
     * @param attributes    the answers to the form's extra questions, the Service selection among
     *                      them; may be empty
     * @return observable visit that has been started (locally, and on the server if possible)
     */
    public Observable<Visit> startVisit(final Patient patient,
                                        final LocationEntity location,
                                        final String startDatetime,
                                        final List<VisitAttribute> attributes) {
        return AppDatabaseHelper.createObservableIO(() -> {
            try {
                final Visit visit = new Visit();
                visit.setStartDatetime(startDatetime != null ? startDatetime
                        : DateUtils.convertTime(System.currentTimeMillis(), DateUtils.OPEN_MRS_REQUEST_FORMAT));
                visit.setPatient(patient);
                visit.setLocation(location != null ? location : sessionLocation());

                if (attributes != null && !attributes.isEmpty()) {
                    visit.setAttributes(new ArrayList<>(attributes));
                }

                VisitType visitType = new VisitType();
                visitType.setUuid(OpenmrsAndroid.getVisitTypeUUID());
                visit.setVisitType(visitType);

                long visitId = visitDAO.saveNewVisitLocally(visit, patient.getId()).toBlocking().first();
                visit.setId(visitId);

                Visit startedVisit = syncStartedVisit(visit, patient);

                // The enrollment episodes are opened from the visit's own Service attribute rather
                // than from a separate argument, so a visit carries its services with it wherever
                // it was built - and a visit with no Service attribute opens none, leaving visits
                // that predate this feature untouched, exactly as in the web client.
                List<String> servicePrograms = visit.serviceProgramUuids();
                if (!servicePrograms.isEmpty()) {
                    programEnrollmentRepository.enrollForVisit(patient, visitId, servicePrograms,
                            visit.getStartDatetime(), visit.getLocation().getUuid());
                }

                return startedVisit;
            } catch (Exception e) {
                getLogger().e("Error saving visit locally: " + e.getMessage(), e);
                throw e;
            }
        });
    }

    /**
     * The location the user is logged in at, as a full {@link LocationEntity} whenever it is one
     * of the cached login locations.
     *
     * <p>findLocationByName can return null (only when the stored location name itself is null -
     * see {@link OpenmrsAndroid#getLocation}); Visit.location is a non-null Kotlin property, so
     * passing null through would throw. Falls back to a display-only LocationEntity, same as
     * AppDatabaseHelper#convert(VisitEntity) already does.
     *
     * @return the session location
     */
    private LocationEntity sessionLocation() {
        String locationName = OpenmrsAndroid.getLocation();
        LocationEntity location = locationDAO.findLocationByName(locationName);
        if (location == null) {
            location = new LocationEntity(locationName == null ? "" : locationName);
        }
        return location;
    }

    /**
     * Pushes a visit that already exists locally (started offline, or before its patient was
     * synced) to the server, and updates the local record on success. Safe to call repeatedly -
     * e.g. by {@code VisitService} on every reconnect - until it succeeds: if we're offline, or
     * the patient still has no server uuid to attach the visit to, the visit is returned
     * unchanged rather than failing, so it stays queued for the next attempt.
     *
     * @param visit   a visit already saved locally (has a local id)
     * @param patient the visit's patient - the visit is only pushed once this has a server uuid
     * @return the visit, updated with a server uuid if the push succeeded, unchanged otherwise
     */
    public Visit syncStartedVisit(final Visit visit, final Patient patient) {
        if (!NetworkUtils.isOnline() || !patient.isSynced()) {
            return visit;
        }
        try {
            visit.setPatient(patient);

            // AppDatabaseHelper#convert(VisitEntity) reconstructs visitType.uuid from the local
            // "visit_type" column, but that column only ever stores the visit type's *display*
            // text (see convert(Visit): VisitEntity) - so a visit reloaded from the local DB (as
            // happens here on every retry) never actually carries a real visit type uuid. Re-derive
            // it from the app's configured visit type rather than trust the reloaded value.
            if (visit.getVisitType() == null || visit.getVisitType().getUuid() == null
                    || visit.getVisitType().getUuid().isEmpty()) {
                VisitType visitType = new VisitType();
                visitType.setUuid(OpenmrsAndroid.getVisitTypeUUID());
                visit.setVisitType(visitType);
            }

            Response<Visit> response = restApi.startVisit(visit).execute();
            if (response.isSuccessful() && response.body() != null) {
                Visit syncedVisit = response.body();
                syncedVisit.setId(visit.getId());
                // The response is returned in whatever representation the server defaults to, which
                // need not include the attributes we just sent. Keep the ones we know about rather
                // than let a thinner response blank them out locally - ending the visit reads its
                // services back from here.
                if (syncedVisit.getAttributes() == null || syncedVisit.getAttributes().isEmpty()) {
                    syncedVisit.setAttributes(visit.getAttributes());
                }
                visitDAO.saveOrUpdate(syncedVisit, patient.getId()).toBlocking().first();
                SyncedPatientCleanupUtil.checkAndCleanupIfFullySynced(patient.getId());
                return syncedVisit;
            } else {
                getLogger().e("Error starting a visit: " + describeError(response));

                // The patient may already have an active visit on the server - most commonly
                // right after a duplicate-identifier patient merge (see
                // PatientRepository#syncPatient) links this local patient to an existing server
                // patient who already had an ongoing visit there, which the server then refuses
                // to duplicate. Rather than parsing server-specific error text (which varies
                // across OpenMRS versions/modules), check directly whether the patient already
                // has an active visit - if so, treat it as stale (this workflow has no legitimate
                // way to have started a second, genuinely concurrent visit), end it, and retry
                // creating the locally-queued visit fresh. A harmless no-op for any other kind of
                // failure (nothing found, falls through unchanged below, exactly as before this
                // check existed).
                Visit existingActiveVisit = findExistingActiveVisit(patient);
                if (existingActiveVisit != null && endExistingActiveVisit(existingActiveVisit, visit.getStartDatetime())) {
                    Response<Visit> retryResponse = restApi.startVisit(visit).execute();
                    if (retryResponse.isSuccessful() && retryResponse.body() != null) {
                        Visit syncedVisit = retryResponse.body();
                        syncedVisit.setId(visit.getId());
                        if (syncedVisit.getAttributes() == null || syncedVisit.getAttributes().isEmpty()) {
                            syncedVisit.setAttributes(visit.getAttributes());
                        }
                        visitDAO.saveOrUpdate(syncedVisit, patient.getId()).toBlocking().first();
                        SyncedPatientCleanupUtil.checkAndCleanupIfFullySynced(patient.getId());
                        return syncedVisit;
                    } else {
                        getLogger().e("Error starting a visit even after ending the existing active one: " + describeError(retryResponse));
                    }
                }
            }
        } catch (Exception e) {
            getLogger().e("Error starting a visit, will retry when back online: " + e.getMessage());
        }
        return visit;
    }

    /**
     * Builds a diagnosable message from a failed response - {@code Response#message()} alone is
     * only the generic HTTP status line (e.g. "Bad Request"), not the server's actual reason.
     */
    private String describeError(Response<?> response) {
        try {
            String body = response.errorBody() != null ? response.errorBody().string() : null;
            return (body != null && !body.isEmpty()) ? body : response.message();
        } catch (Exception e) {
            return response.message();
        }
    }

    /**
     * Looks up the patient's current active visit on the server, if any - used by
     * {@link #syncStartedVisit} to recover when it fails to create a new visit because the
     * patient already has one ongoing there. Best-effort: any failure here is swallowed, since
     * this already runs inside another method's own failure-handling path.
     *
     * @return the patient's active visit, or null if there isn't one (or the lookup itself failed)
     */
    private Visit findExistingActiveVisit(Patient patient) {
        try {
            Response<Results<Visit>> response = restApi.findActiveVisitsByPatientUuid(patient.getUuid(), representation).execute();
            if (response.isSuccessful() && response.body() != null && !response.body().getResults().isEmpty()) {
                return response.body().getResults().get(0);
            }
        } catch (Exception e) {
            getLogger().e("Error checking for an existing active visit: " + e.getMessage());
        }
        return null;
    }

    /**
     * Ends a patient's existing active visit found by {@link #findExistingActiveVisit}, so a
     * locally-queued new visit can be created in its place. Deliberately does not go through the
     * public {@link #endVisit} (and its service-enrollment completion) - that method needs a
     * locally-tracked visit with a known patient/local id, which this "foreign" server visit
     * (never previously known to this device) doesn't have. Best-effort: swallows its own
     * failures, since this already runs inside another method's own failure-handling path.
     *
     * <p>Ends it just before the NEW visit's own start time, not "now" (real sync time) - the new
     * visit was started earlier (offline, then queued until this reconnect), so ending the old one
     * at "now" would still leave its [start, now] window overlapping the new visit's start time,
     * and the server rejects that as "Visit.visitCannotOverlapAnotherVisitOfTheSamePatient" -
     * confirmed via a real device log during testing.
     *
     * @param newVisitStartDatetime the locally-queued visit's own start time
     *                              ({@link com.openmrs.android_sdk.utilities.DateUtils#OPEN_MRS_REQUEST_FORMAT}), used to
     *                              pick a stop time for the old visit that can never overlap it
     * @return true if the visit was ended successfully, false otherwise
     */
    private boolean endExistingActiveVisit(Visit existingActiveVisit, String newVisitStartDatetime) {
        try {
            Long newVisitStartMillis = newVisitStartDatetime != null
                    ? convertTime(newVisitStartDatetime, OPEN_MRS_REQUEST_FORMAT)
                    : null;
            long stopMillis = newVisitStartMillis != null
                    ? newVisitStartMillis - 60000
                    : System.currentTimeMillis();

            Visit emptyVisitWithStopDate = new Visit();
            emptyVisitWithStopDate.setStopDatetime(convertTime(stopMillis, OPEN_MRS_REQUEST_FORMAT));
            Response<Visit> response = restApi.endVisitByUUID(existingActiveVisit.getUuid(), emptyVisitWithStopDate).execute();
            if (response.isSuccessful()) {
                getLogger().i("Ended existing active visit (UUID: " + existingActiveVisit.getUuid() + ") to make way for the locally-queued one.");
                return true;
            }
            getLogger().e("Failed to end existing active visit: " + describeError(response));
        } catch (Exception e) {
            getLogger().e("Error ending existing active visit: " + e.getMessage());
        }
        return false;
    }

    /**
     * This method fetches visits for a particular location and saves them locally
     *
     * @param patient
     * @param locationUuid
     *
     * @return the vist list
     */
    public Observable<List<Visit>> getVisitsByLocationAndSaveLocally(@NonNull final Patient patient,
                                                                     String locationUuid) {
        return AppDatabaseHelper.createObservableIO(() -> {
            Call<Results<Visit>> call =
                    restApi.findActiveVisitsByPatientAndLocation(patient.getUuid(), locationUuid,representation);
            return fetchVisitsAndSave(call, patient);
        });
    }

    /**
     * This method fetches visits for a particular location and from a start date
     * and saves them locally
     *
     * @param patient
     * @param locationUuid
     * @param fromStartDate
     *
     * @return the vist list
     */
    public Observable<List<Visit>>
    getVisitsByLocationAndDateAndSaveLocally(@NonNull final Patient patient, String locationUuid, String fromStartDate) {

        return AppDatabaseHelper.createObservableIO(() -> {
            Call<Results<Visit>> call =
                    restApi.findVisitsByPatientAndLocationAndDate(patient.getUuid(), locationUuid, fromStartDate, representation);
            return fetchVisitsAndSave(call, patient);
        });
    }

    /**
     * This method fetches visits from a start date and saves them locally
     *
     * @param patient
     * @param fromStartDate
     *
     * @return the vist list
     */
    public Observable<List<Visit>>
    getVisitsByDateAndSaveLocally(@NonNull final Patient patient, String fromStartDate) {

        return AppDatabaseHelper.createObservableIO(() -> {
            Call<Results<Visit>> call =
                    restApi.findVisitsByPatientAndDate(patient.getUuid(), fromStartDate, representation);
            return fetchVisitsAndSave(call, patient);
        });
    }

    /**
     * This method fetches active visits from a given date and saves them locally
     *
     * @param patient
     * @param fromStartDate
     *
     * @return the active vist list
     */
    public Observable<List<Visit>>
    getActiveVisitsByDateAndSaveLocally(@NonNull final Patient patient, String fromStartDate) {

        return AppDatabaseHelper.createObservableIO(() -> {
            Call<Results<Visit>> call =
                    restApi.findActiveVisitsByPatientAndDate(patient.getUuid(), fromStartDate, representation);
            return fetchVisitsAndSave(call, patient);
        });
    }

    /**
     * This method fetches active visits for a location and saves them locally
     *
     * @param patient
     * @param locationUuid
     *
     * @return the active vist list
     */
    public Observable<List<Visit>>
    getActiveVisitsByLocationAndSaveLocally(@NonNull final Patient patient, String locationUuid) {

        return AppDatabaseHelper.createObservableIO(() -> {
            Call<Results<Visit>> call =
                    restApi.findActiveVisitsByPatientAndLocation(patient.getUuid(), locationUuid, representation);
            return fetchVisitsAndSave(call, patient);
        });
    }

    /**
     * This method fetches active visits for a location and from a start date
     * and saves them locally
     *
     * @param patient
     * @param fromStartDate
     * @param locationUuid
     *
     * @return the active vist list
     */
    public Observable<List<Visit>>
    getActiveVisitsByLocationAndDateAndSaveLocally(@NonNull final Patient patient, String locationUuid, String fromStartDate) {

        return AppDatabaseHelper.createObservableIO(() -> {
            Call<Results<Visit>> call =
                    restApi.findActiveVisitsByPatientAndLocationAndDate(patient.getUuid(), locationUuid, fromStartDate, representation);
            return fetchVisitsAndSave(call, patient);
        });
    }
}
