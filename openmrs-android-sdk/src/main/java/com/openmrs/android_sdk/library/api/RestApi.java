/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */

package com.openmrs.android_sdk.library.api;

import java.util.List;
import java.util.Map;

import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.Field;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;
import retrofit2.http.QueryMap;
import retrofit2.http.Url;

import com.openmrs.android_sdk.library.databases.entities.ConceptEntity;
import com.openmrs.android_sdk.library.databases.entities.FormResourceEntity;
import com.openmrs.android_sdk.library.databases.entities.LocationEntity;
import com.openmrs.android_sdk.library.models.Allergy;
import com.openmrs.android_sdk.library.models.AllergyCreate;
import com.openmrs.android_sdk.library.models.Appointment;
import com.openmrs.android_sdk.library.models.AppointmentConflictRequest;
import com.openmrs.android_sdk.library.models.AppointmentCreateRequest;
import com.openmrs.android_sdk.library.models.AppointmentSearchRequest;
import com.openmrs.android_sdk.library.models.AppointmentServiceInfo;
import com.openmrs.android_sdk.library.models.AppointmentStatusChangeRequest;
import com.openmrs.android_sdk.library.models.AddObsRequest;
import com.openmrs.android_sdk.library.models.ConceptDetails;
import com.openmrs.android_sdk.library.models.FulfillerDetailsRequest;
import com.openmrs.android_sdk.library.models.ConceptAnswers;
import com.openmrs.android_sdk.library.models.ConceptMembers;
import com.openmrs.android_sdk.library.models.ConceptSearchResult;
import com.openmrs.android_sdk.library.models.Drug;
import com.openmrs.android_sdk.library.models.DrugCreate;
import com.openmrs.android_sdk.library.models.DrugOrderCreateRequest;
import com.openmrs.android_sdk.library.models.DrugOrderDetails;
import com.openmrs.android_sdk.library.models.DrugSearchResult;
import com.openmrs.android_sdk.library.models.Encounter;
import com.openmrs.android_sdk.library.models.EncounterType;
import com.openmrs.android_sdk.library.models.Encountercreate;
import com.openmrs.android_sdk.library.models.FhirLocationBundle;
import com.openmrs.android_sdk.library.models.FormCreate;
import com.openmrs.android_sdk.library.models.FormData;
import com.openmrs.android_sdk.library.models.IdGenPatientIdentifiers;
import com.openmrs.android_sdk.library.models.IdentifierType;
import com.openmrs.android_sdk.library.models.Module;
import com.openmrs.android_sdk.library.models.Obscreate;
import com.openmrs.android_sdk.library.models.Observation;
import com.openmrs.android_sdk.library.models.OrderCreate;
import com.openmrs.android_sdk.library.models.OrderEntryConfig;
import com.openmrs.android_sdk.library.models.OrderGet;
import com.openmrs.android_sdk.library.models.OrderTypeInfo;
import com.openmrs.android_sdk.library.models.Patient;
import com.openmrs.android_sdk.library.models.PatientDiagnosisResponse;
import com.openmrs.android_sdk.library.models.PatientDto;
import com.openmrs.android_sdk.library.models.PatientDtoUpdate;
import com.openmrs.android_sdk.library.models.PatientPhoto;
import com.openmrs.android_sdk.library.models.ProgramCreate;
import com.openmrs.android_sdk.library.models.ProgramGet;
import com.openmrs.android_sdk.library.models.Provider;
import com.openmrs.android_sdk.library.models.RecurringAppointmentPayload;
import com.openmrs.android_sdk.library.models.Resource;
import com.openmrs.android_sdk.library.models.Results;
import com.openmrs.android_sdk.library.models.Session;
import com.openmrs.android_sdk.library.models.SystemProperty;
import com.openmrs.android_sdk.library.models.TestOrderCreateRequest;
import com.openmrs.android_sdk.library.models.TestSearchResult;
import com.openmrs.android_sdk.library.models.SystemSetting;
import com.openmrs.android_sdk.library.models.User;
import com.openmrs.android_sdk.library.models.Visit;
import com.openmrs.android_sdk.library.models.VisitType;

/**
 * The interface Rest api.
 */
public interface RestApi {
    /**
     * Gets forms.
     *
     * @return the forms
     */
    @GET("form?v=custom:(uuid,name,encounterType:(uuid,display),resources:(uuid,name,valueReference))")
    Call<Results<FormResourceEntity>> getForms();

    /**
     * Gets locations.
     *
     * @param representation the representation
     * @return the locations
     */
    @GET("location?tag=Login%20Location")
    Call<Results<LocationEntity>> getLocations(@Query("v") String representation);

    /**
     * Gets locations.
     *
     * @param url            the url
     * @param tag            the tag
     * @param representation the representation
     * @return the locations
     */
    @GET()
    Call<Results<LocationEntity>> getLocations(@Url String url,
                                               @Query("tag") String tag,
                                               @Query("v") String representation);

    /**
     * Gets locations tagged with a given location tag via the FHIR2 module, the same API the
     * OpenMRS O3 web app uses for its login-location picker. Used instead of the legacy
     * {@link #getLocations(String, String, String)} tag search, which can return empty results
     * for a tag search that the FHIR endpoint correctly resolves (a known quirk/bug in the
     * legacy REST webservices module's tag-based location search on some OpenMRS instances).
     *
     * @param url   the full ".../ws/fhir2/R4/Location" URL
     * @param tag   the location tag name (e.g. "Login Location")
     * @param count max results per page
     * @return the FHIR searchset Bundle
     */
    @GET()
    Call<FhirLocationBundle> getFhirLocationsByTag(@Url String url,
                                                    @Query("_tag") String tag,
                                                    @Query("_summary") String summary,
                                                    @Query("_count") int count);

    /**
     * Fetches a subsequent page of a FHIR Bundle via its own absolute "next" link URL.
     *
     * @param url the full page URL, taken from a previous {@link FhirLocationBundle}'s "next" link
     * @return the FHIR searchset Bundle page
     */
    @GET()
    Call<FhirLocationBundle> getFhirBundlePage(@Url String url);

    /**
     * Gets system property.
     *
     * @param property       the property
     * @param representation the representation
     * @return the system property
     */
    @GET("systemsetting")
    Call<Results<SystemProperty>> getSystemProperty(@Query("q") String property,
                                                    @Query("v") String representation);

    /**
     * Gets identifier types.
     *
     * @return the identifier types
     */
    @GET("patientidentifiertype")
    Call<Results<IdentifierType>> getIdentifierTypes();

    /**
     * Gets patient identifiers.
     *
     * @param username the username
     * @param password the password
     * @return the patient identifiers
     */
    @GET("module/idgen/generateIdentifier.form?source=1")
    Call<IdGenPatientIdentifiers> getPatientIdentifiers(@Query("username") String username,
                                                        @Query("password") String password);

    /**
     * Generate patient identifier.
     *
     * @param sourceUuid the source uuid
     * @param body       the body
     * @return the call
     */
    @POST("idgen/identifiersource/{uuid}/identifier")
    Call<ResponseBody> generatePatientIdentifier(@Path("uuid") String sourceUuid, @Body Map<String, String> body);

    /**
     * Gets patient by uuid.
     *
     * @param uuid           the uuid
     * @param representation the representation
     * @return the patient by uuid
     */
    @GET("patient/{uuid}")
    Call<PatientDto> getPatientByUUID(@Path("uuid") String uuid,
                                      @Query("v") String representation);

    /**
     * Gets last viewed patients.
     *
     * @param limit      the limit
     * @param startIndex the start index
     * @return the last viewed patients
     */
    @GET("patient?lastviewed&v=full")
    Call<Results<Patient>> getLastViewedPatients(@Query("limit") Integer limit,
                                                 @Query("startIndex") Integer startIndex);

    /**
     * Create patient call.
     *
     * @param patientDto the patient dto
     * @return the call
     */
    @POST("patient")
    Call<PatientDto> createPatient(@Body PatientDto patientDto);

    /**
     * Gets patients.
     *
     * @param searchQuery    the search query
     * @param representation the representation
     * @return the patients
     */
    @GET("patient")
    Call<Results<Patient>> getPatients(@Query("q") String searchQuery,
                                       @Query("v") String representation);

    /**
     * Gets patients.
     *
     * @param searchQuery    the search query
     * @param representation the representation
     * @return the patients
     */

    @GET("patient")
    Call<Results<PatientDto>> getPatientsDto(@Query("q") String searchQuery,
                                             @Query("v") String representation);

    /**
     * Upload patient photo call.
     *
     * @param uuid         the uuid
     * @param patientPhoto the patient photo
     * @return the call
     */
    @POST("personimage/{uuid}")
    Call<PatientPhoto> uploadPatientPhoto(@Path("uuid") String uuid,
                                          @Body PatientPhoto patientPhoto);

    /**
     * Download patient photo call.
     *
     * @param uuid the uuid
     * @return the call
     */
    @GET("personimage/{uuid}")
    Call<ResponseBody> downloadPatientPhoto(@Path("uuid") String uuid);

    /**
     * Gets similar patients.
     *
     * @param patientData the patient data
     * @return the similar patients
     */
    @GET("patient?matchSimilar=true&v=full")
    Call<Results<Patient>> getSimilarPatients(@QueryMap Map<String, String> patientData);

    /**
     * Gets Observation by uuid
     *
     * @param obsUuid the uuid of the observation
     * @return the Call<Observation>
     */
    @GET("obs/{obsUuid}")
    Call<Observation> getObservationByUuid(@Path("obsUuid") String obsUuid);

    /**
     * Create obs call.
     *
     * @param obscreate the obscreate
     * @return the call
     */
    @POST("obs")
    Call<Observation> createObs(@Body Obscreate obscreate);

    /**
     * Create obs on the server.
     *
     * @param observation the Observation to create
     * @return the call
     */
    @POST("obs")
    Call<Observation> createObservation(@Body Observation observation);

    /**
     * Get all observations for a patient
     *
     * @param patientUuid the patient uuid
     * @return Observation Resource List
     */
    @GET("obs")
    Call<Results<Resource>> getObservationsByPatientUuid(@Query("patient") String patientUuid);

    /**
     * Get all observations for a patient
     *
     * @param encounterUuid the encounter uuid
     * @return Observation Resource List
     */
    @GET("obs")
    Call<Results<Resource>> getObservationsByEncounterUuid(@Query("encounter") String encounterUuid);

    /**
     * Get all observations for a patient
     *
     * @param patientUuid patient uuid
     * @param conceptUuid the concept uuid
     * @return Observation Resource List
     */
    @GET("obs")
    Call<Results<Resource>> getObservationsByConceptUuid(@Query("patient") String patientUuid,
                                                         @Query("concept") String conceptUuid);

    /**
     * Delete Observation from server by uuid
     *
     * @param obsUuid the observation uuid
     * @return the response body
     */
    @DELETE("obs/{obsUuid}")
    Call<ResponseBody> deleteObservation(@Path("obsUuid") String obsUuid);

    /**
     * Update Observation object on the server.
     *
     * @param obsUuid the uuid of the observation
     * @param observation the Observation object
     * @return the updated observation
     */
    @POST("obs/{obsUuid}")
    Call<Observation> updateObservation(@Path("obsUuid") String obsUuid,
                                        @Body Observation observation);

    /**
     * Create encounter call.
     *
     * @param encountercreate the encountercreate
     * @return the call
     */
    @POST("encounter?v=full")
    Call<Encounter> createEncounter(@Body Encountercreate encountercreate);

    /**
     * Updates an encounter.
     *
     * @param uuid the UUID of the encounter
     * @param encountercreate the encountercreate containing the updates
     * @return the call
     */
    @POST("encounter/{uuid}?v=full")
    Call<Encounter> updateEncounter(@Path("uuid") String uuid, @Body Encountercreate encountercreate);

    /**
     * Get all encounter resources for a patient.
     *
     * @param uuid the UUID of the patient
     * @return the encounter resource list
     */
    @GET("encounter")
    Call<Results<Resource>> getAllEncountersForPatientByPatientUuid(@Query("patient") String uuid);

    /**
     * Get Encounter from Uuid
     *
     * @param encounterUuid the UUID of the patient
     * @return the encounter
     */
    @GET("encounter/{encounterUuid}?v=full")
    Call<Encounter> getEncounterByUuid(@Path("encounterUuid") String encounterUuid);

    /**
     * Get Encounter Resources from Patient uuid and EncounterType uuid
     *
     * @param patient_uuid the UUID of the Patient
     * @param encounterType_uuid the UUID of the Encounter type
     * @return the encounter resource list
     */
    @GET("encounter")
    Call<Results<Resource>> getEncounterResourcesByEncounterType(@Query("patient") String patient_uuid,
                                                  @Query("encounterType") String encounterType_uuid);

    /**
     * Get Encounter Resources from Patient uuid and OrderType uuid
     *
     * @param patient_uuid the UUID of the Patient
     * @param orderType_uuid the UUID of the Order type
     * @return the encounter resource list
     */
    @GET("encounter")
    Call<Results<Resource>> getEncounterResourcesByOrderType(@Query("patient") String patient_uuid,
                                                  @Query("orderType") String orderType_uuid);

    /**
     * Get Encounter Resources from Patient uuid and starting from the given date
     *
     * @param patient_uuid the UUID of the Patient
     * @param fromDate the String representation of Date in 'YYYY-MM-DD' format
     * @return the encounter resource list
     */
    @GET("encounter")
    Call<Results<Resource>> getEncounterResourcesFromDate(@Query("patient") String patient_uuid,
                                                  @Query("fromdate") String fromDate);

    /**
     * Get Encounter Resources from Patient uuid and Visit uuid
     *
     * @param patient_uuid the UUID of the Patient
     * @param visit_uuid the UUID of the visit
     * @return the encounter resource list
     */
    @GET("encounter")
    Call<Results<Resource>> getEncounterResourcesByVisit(@Query("patient") String patient_uuid,
                                                  @Query("fromdate") String visit_uuid);

    /**
     * Gets encounter types.
     *
     * @return the encounter types
     */
    @GET("encountertype")
    Call<Results<EncounterType>> getEncounterTypes();

    /**
     * Gets encounter roles.
     *
     * @return the encounter roles
     */
    @GET("encounterrole")
    Call<Results<Resource>> getEncounterRoles();

    /**
     * Gets session.
     *
     * @return the session
     */
    @GET("session")
    Call<Session> getSession();

    /**
     * Ends a visit by its uuid.
     *
     * @param uuid              the visit uuid to be ended
     * @param visitWithStopDate An empty visit containing the stop date and time
     * @return the call
     */
    @POST("visit/{uuid}")
    Call<Visit> endVisitByUUID(@Path("uuid") String uuid, @Body Visit visitWithStopDate);

    /**
     * Start visit call.
     *
     * @param visit the visit
     * @return the call
     */
    @POST("visit")
    Call<Visit> startVisit(@Body Visit visit);

    /**
     * Find visits by patient uuid call.
     *
     * @param patientUUID    the patient uuid
     * @param representation the representation
     * @return the call
     */
    @GET("visit")
    Call<Results<Visit>> findVisitsByPatientUUID(@Query("patient") String patientUUID,
                                                 @Query("v") String representation);

    /**
     * Get a Visit by visit uuid
     *
     * @param visitUuid the patient uuid
     *
     * @return the Visit
     */

    @GET("visit/{visitUuid}")
    Call<Visit> getVisitFromUuid(@Path("visitUuid") String visitUuid);

    /**
     * Gets visit type.
     *
     * @return the visit type
     */
    @GET("visittype")
    Call<Results<VisitType>> getVisitType();

    /**
     * Fetch visits by patient uuid and location
     *
     * @param patientUUID    the patient uuid
     * @param locationUUID    the loation uuid
     * @param representation the required representation
     * @return the list of visits
     */
    @GET("visit")
    Call<Results<Visit>> findVisitsByPatientAndLocation(@Query("patient") String patientUUID,
                                                        @Query("location") String locationUUID,
                                                        @Query("v") String representation);

    /**
     * Fetch visits by patient uuid and start date
     *
     * @param patientUUID    the patient uuid
     * @param fromStartDate  the start date
     * @param representation the required representation
     * @return the list of visits
     */
    @GET("visit")
    Call<Results<Visit>> findVisitsByPatientAndDate(@Query("patient") String patientUUID,
                                                    @Query("fromStartDate") String fromStartDate,
                                                    @Query("v") String representation);

    /**
     * Fetch visits by patient uuid, location and fromStartDate
     *
     * @param patientUUID    the patient uuid
     * @param locationUUID    the loation uuid
     * @param fromStartDate    the start date
     * @param representation the required representation
     * @return the list of visits
     */
    @GET("visit")
    Call<Results<Visit>> findVisitsByPatientAndLocationAndDate(@Query("patient") String patientUUID,
                                                               @Query("location") String locationUUID,
                                                               @Query("fromStartDate") String fromStartDate,
                                                               @Query("v") String representation);


    /**
     * Fetch visit resources by patient uuid
     *
     * @param patientUUID    the patient uuid
     * @return the list of visit resources
     */
    @GET("visit")
    Call<Results<Resource>> findVisitResourcesByPatientUUID(@Query("patient") String patientUUID);

    /**
     * Fetch visit resources by patient uuid and location uuid
     *
     * @param patientUUID    the patient uuid
     * @param locationUUID   the location uuid
     *
     * @return the list of visit resources
     */
    @GET("visit")
    Call<Results<Resource>> findVisitResourcesByPatientAndLocation(@Query("patient") String patientUUID,
                                                                   @Query("location") String locationUUID);

    /**
     * Fetch visit resources by patient uuid, location uuid and fromStartDate
     *
     * @param patientUUID    the patient uuid
     * @param locationUUID   the location uuid
     * @param fromStartDate  starting date of the visit
     *
     * @return the list of visit resources
     */
    @GET("visit")
    Call<Results<Resource>> findVisitResourcesByPatientAndLocationAndDate(@Query("patient") String patientUUID,
                                                                          @Query("location") String locationUUID,
                                                                          @Query("fromStartDate") String fromStartDate);

    /**
     * Fetch visit resources by patient uuid and fromStartDate
     *
     * @param patientUUID    the patient uuid
     * @param fromStartDate  starting date of the visit
     *
     * @return the list of visit resources
     */
    @GET("visit")
    Call<Results<Resource>> findVisitResourcesByPatientAndDate(@Query("patient") String patientUUID,
                                                               @Query("fromStartDate") String fromStartDate);

    /**
     * Fetch active visits by patient uuid
     *
     * @param patientUUID    the patient uuid
     * @param representation the required representation
     * @return the list of visits
     */
    @GET("visit?includeInactive=false")
    Call<Results<Visit>> findActiveVisitsByPatientUuid(@Query("patient") String patientUUID,
                                                       @Query("v") String representation);

    /**
     * Fetch active visits by patient uuid and location
     *
     * @param patientUUID    the patient uuid
     * @param locationUUID    the loation uuid
     * @param representation the required representation
     * @return the list of visits
     */
    @GET("visit?includeInactive=false")
    Call<Results<Visit>> findActiveVisitsByPatientAndLocation(@Query("patient") String patientUUID,
                                                              @Query("location") String locationUUID,
                                                              @Query("v") String representation);

    /**
     * Fetch active visits by patient uuid, location and fromStartDate
     *
     * @param patientUUID    the patient uuid
     * @param locationUUID    the loation uuid
     * @param fromStartDate    the start date
     * @param representation the required representation
     * @return the list of visits
     */
    @GET("visit?includeInactive=false")
    Call<Results<Visit>> findActiveVisitsByPatientAndLocationAndDate(@Query("patient") String patientUUID,
                                                                     @Query("location") String locationUUID,
                                                                     @Query("fromStartDate") String fromStartDate,
                                                                     @Query("v") String representation);

    /**
     * Fetch active visits by patient uuid and fromStartDate
     *
     * @param patientUUID    the patient uuid
     * @param fromStartDate    the start date
     * @param representation the required representation
     * @return the list of visits
     */
    @GET("visit?includeInactive=false")
    Call<Results<Visit>> findActiveVisitsByPatientAndDate(@Query("patient") String patientUUID,
                                                          @Query("fromStartDate") String fromStartDate,
                                                          @Query("v") String representation);

    /**
     * Create Visit
     *
     * @param visit the Visit
     * @return the call
     */
    @POST("visit")
    Call<Visit> createVisit(@Body Visit visit);

    /**
     * Update Visit
     *
     * @param uuid the uuid of the visit
     * @param visit the Visit
     * @return the call
     */
    @POST("visit/{uuid}")
    Call<Visit> createVisit(@Path("uuid") String uuid, @Body Visit visit);

    /**
     * Delete Visit
     *
     * @param uuid the uuid of the visit
     * @return the call
     */
    @DELETE("visit/{uuid}")
    Call<ResponseBody> deleteVisit(@Path("uuid") String uuid);

    /**
     * Gets last vitals.
     *
     * @param patientUUID    the patient uuid
     * @param encounterType  the encounter type
     * @param representation the representation
     * @param limit          the limit
     * @param order          the order
     * @return the last vitals
     */
    @GET("encounter")
    Call<Results<Encounter>> getLastVitals(@Query("patient") String patientUUID,
                                           @Query("encounterType") String encounterType,
                                           @Query("v") String representation,
                                           @Query("limit") int limit,
                                           @Query("order") String order);

    /**
     * Update patient call.
     *
     * @param patientDto     the patient dto
     * @param uuid           the uuid
     * @param representation the representation
     * @return the call
     */
    @POST("patient/{uuid}")
    Call<PatientDto> updatePatient(@Body PatientDtoUpdate patientDto, @Path("uuid") String uuid,
                                   @Query("v") String representation);

    /**
     * Gets modules.
     *
     * @param representation the representation
     * @return the modules
     */
    @GET("module")
    Call<Results<Module>> getModules(@Query("v") String representation);

    /**
     * Gets user info.
     *
     * @param username the username
     * @return the user info
     */
    @GET("user")
    Call<Results<User>> getUserInfo(@Query("q") String username);

    /**
     * Gets full user info.
     *
     * @param uuid           the uuid
     * @param representation the custom representation (e.g. roles/privileges), may be null
     * @return the full user info
     */
    @GET("user/{uuid}")
    Call<User> getFullUserInfo(@Path("uuid") String uuid, @Query("v") String representation);

    /**
     * Gets concepts.
     *
     * @param limit      the limit
     * @param startIndex the start index
     * @return the concepts
     */
    @GET("concept")
    Call<Results<ConceptEntity>> getConcepts(@Query("limit") int limit, @Query("startIndex") int startIndex);

    /**
     * Fuzzy-searches concepts by name within a given concept class (e.g. Diagnosis).
     *
     * @param name         the search text
     * @param searchType   the search type, e.g. "fuzzy"
     * @param conceptClass the UUID of the concept class to restrict results to
     * @param representation the custom representation, e.g. "custom:(uuid,display)"
     * @return the matching concepts
     */
    @GET("concept")
    Call<Results<ConceptSearchResult>> searchConceptsByClass(@Query("name") String name,
                                                              @Query("searchType") String searchType,
                                                              @Query("class") String conceptClass,
                                                              @Query("v") String representation);

    /**
     * Creates a patient diagnosis linked to an encounter.
     *
     * The body is sent as a raw {@link RequestBody} (built with org.json in {@code DiagnosisRepository})
     * rather than a Gson-serialized model: the server requires the "condition" key to be present
     * (even as JSON null), but the app's shared Gson instance is configured to omit null fields.
     *
     * @param patientDiagnosisCreate the JSON request body
     * @return the created diagnosis
     */
    @POST("patientdiagnoses")
    Call<PatientDiagnosisResponse> createPatientDiagnosis(@Body RequestBody patientDiagnosisCreate);

    /**
     * Gets concept from uuid.
     *
     * @param uuid the uuid
     * @return the concept from uuid
     */
    @GET("concept/{uuid}")
    Call<ConceptAnswers> getConceptFromUUID(@Path("uuid") String uuid);

    /**
     * Gets concept members from uuid.
     *
     * @param uuid the uuid
     * @return the concept members from uuid
     */
    @GET("concept/{uuid}")
    Call<ConceptMembers> getConceptMembersFromUUID(@Path("uuid") String uuid);

    /**
     * Gets system settings by query.
     *
     * @param query          the query
     * @param representation the representation
     * @return the system settings by query
     */
    @GET("systemsetting")
    Call<Results<SystemSetting>> getSystemSettingsByQuery(@Query("q") String query,
                                                          @Query("v") String representation);

    /**
     * Form create call.
     *
     * @param uuid the uuid
     * @param obj  the obj
     * @return the call
     */
    @POST("form/{uuid}/resource")
    Call<FormCreate> formCreate(@Path("uuid") String uuid,
                                @Body FormData obj);

    /**
     * Gets provider list.
     *
     * @return the provider list
     */
    @GET("provider?v=default")
    Call<Results<Provider>> getProviderList();

    /**
     * Delete provider call.
     *
     * @param uuid the uuid
     * @return the call
     */
    @DELETE("provider/{uuid}?!purge")
    Call<ResponseBody> deleteProvider(@Path("uuid") String uuid);

    /**
     * Add provider call.
     *
     * @param provider the provider
     * @return the call
     */
    @POST("provider")
    Call<Provider> addProvider(@Body Provider provider);

    /**
     * Update provider call.
     *
     * @param uuid     the uuid
     * @param provider the provider
     * @return the call
     */
    @POST("provider/{uuid}")
    Call<Provider> updateProvider(@Path("uuid") String uuid,
                                  @Body Provider provider);

    /**
     * Gets allergies.
     *
     * @param uuid the uuid
     * @return the allergies
     */
    @GET("patient/{uuid}/allergy")
    Call<Results<Allergy>> getAllergies(@Path("uuid") String uuid);

    /**
     * Delete allergy call.
     *
     * @param patientUuid the patient uuid
     * @param allergyUuid the allergy uuid
     * @return the call
     */
    @DELETE("patient/{patientUuid}/allergy/{allergyUuid}")
    Call<ResponseBody> deleteAllergy(@Path("patientUuid") String patientUuid,
                                     @Path("allergyUuid") String allergyUuid);

    /**
     * Create allergy call.
     *
     * @param uuid          the uuid
     * @param allergyCreate the allergy create
     * @return the call
     */
    @POST("patient/{uuid}/allergy")
    Call<Allergy> createAllergy(@Path("uuid") String uuid,
                                @Body AllergyCreate allergyCreate);

    /**
     * Update allergy call.
     *
     * @param patientUuid   the patient uuid
     * @param allergyUuid   the allergy uuid
     * @param allergyCreate the allergy create
     * @return the call
     */
    @POST("patient/{patientUuid}/allergy/{allergyUuid}")
    Call<Allergy> updateAllergy(@Path("patientUuid") String patientUuid,
                                @Path("allergyUuid") String allergyUuid,
                                @Body AllergyCreate allergyCreate);

    /**
     * Searches a patient's appointments. Talks to the `appointments` (Bahmni-origin) module's
     * singular `/appointment/search` endpoint - the same one O3's esm-appointments-app calls
     * (verified against a live O3 deployment) - NOT the legacy `appointmentscheduling` module.
     *
     * @param searchRequest the patient uuid and search start date
     * @return the matching appointments
     */
    @POST("appointment/search")
    Call<List<Appointment>> searchAppointments(@Body AppointmentSearchRequest searchRequest);

    /**
     * Changes an appointment's status (e.g. cancelling it). This one action still goes through
     * the older plural `/appointments/{uuid}/status-change` route, as confirmed from O3's source
     * (changeAppointmentStatus in patient-appointments.resource.ts).
     *
     * @param appointmentUuid the appointment uuid
     * @param statusChangeRequest the new status and timestamp
     * @return the call
     */
    @POST("appointments/{uuid}/status-change")
    Call<ResponseBody> changeAppointmentStatus(@Path("uuid") String appointmentUuid,
                                                @Body AppointmentStatusChangeRequest statusChangeRequest);

    /**
     * Creates a new appointment. Matches saveAppointment() in O3's appointments-form.resource.ts.
     *
     * @param request the appointment details
     * @return the created appointment
     */
    @POST("appointment")
    Call<Appointment> createAppointment(@Body AppointmentCreateRequest request);

    /**
     * Checks whether an appointment would conflict with an existing one (double-booking or
     * outside service hours). Matches checkAppointmentConflict() in O3: a 204 response means no
     * conflict, a 200 response means a conflict was found.
     *
     * @param request the appointment details to check
     * @return the call
     */
    @POST("appointments/conflicts")
    Call<ResponseBody> checkAppointmentConflicts(@Body AppointmentConflictRequest request);

    /**
     * Creates a recurring series of appointments. Matches saveRecurringAppointments() in O3.
     *
     * @param request the appointment details plus the recurrence pattern
     * @return the call
     */
    @POST("recurring-appointments")
    Call<ResponseBody> createRecurringAppointments(@Body RecurringAppointmentPayload request);

    /**
     * Gets the appointment services configured on the server (for the service picker), sorted
     * server-side; matches useAppointmentServices() in O3.
     *
     * @return the appointment services
     */
    @GET("appointmentService/all/default")
    Call<List<AppointmentServiceInfo>> getAppointmentServices();

    /**
     * Create an Order
     *
     * @param orderCreate the orderCreate type object
     * @return the call
     */
    @POST("order")
    Call<OrderGet> createOrder(@Body OrderCreate orderCreate);

    /**
     * Creates a new drug order. Field names match the standard, documented OpenMRS core
     * DrugOrder REST contract - NOT verified against a captured save request from this specific
     * server (only read/config endpoints were captured for this feature).
     *
     * @param request the drug order details
     * @return the created order
     */
    @POST("order")
    Call<OrderGet> createDrugOrder(@Body DrugOrderCreateRequest request);

    /**
     * Creates, revises, or discontinues a test (lab) order, depending on `request.action`.
     * Verified end-to-end against a live server for all three actions.
     *
     * @param request the test order details
     * @return the created order
     */
    @POST("order")
    Call<OrderGet> createTestOrder(@Body TestOrderCreateRequest request);

    /**
     * Gets a concept's datatype, answers, and reference range, e.g. to decide how to render the
     * "Add result" form for a lab order. Verified against a live server.
     *
     * @param conceptUuid the concept uuid
     * @param representation the response representation
     * @return the concept details
     */
    @GET("concept/{uuid}")
    Call<ConceptDetails> getConceptDetails(@Path("uuid") String conceptUuid, @Query("v") String representation);

    /**
     * Adds a test result as an obs into an order's own existing encounter. Verified against a
     * live server.
     *
     * @param encounterUuid the order's own encounter uuid
     * @param request the obs to add
     * @return the updated encounter
     */
    @POST("encounter/{uuid}")
    Call<Encounter> addObsToEncounter(@Path("uuid") String encounterUuid, @Body AddObsRequest request);

    /**
     * Marks an order's fulfiller status, e.g. COMPLETED after a test result is recorded. Verified
     * against a live server.
     *
     * @param orderUuid the order uuid
     * @param request the fulfiller details
     * @return the response
     */
    @POST("order/{uuid}/fulfillerdetails/")
    Call<ResponseBody> updateFulfillerDetails(@Path("uuid") String orderUuid, @Body FulfillerDetailsRequest request);

    /**
     * Get all orders for a patient
     *
     * @param patientUuid the patient uuid
     * @param representation the response representation
     *
     * @return the call
     */
    @GET("order")
    Call<Results<OrderGet>> getOrdersForPatient(@Query("patient") String patientUuid,
                                       @Query("v") String representation);

    /**
     * Get all orders for a patient and caresetting
     *
     * @param patientUuid the patient uuid
     * @param careSetting the caresetting string
     * @param representation the response representation
     *
     * @return the call
     */
    @GET("order")
    Call<Results<OrderGet>> getOrdersForPatient(@Query("patient") String patientUuid,
                                       @Query("careSetting") String careSetting,
                                       @Query("v") String representation);

    /**
     * Get all orders for a patient and ordertype
     *
     * @param patientUuid the patient uuid
     * @param ordertype the theordertype string
     * @param representation the response representation
     *
     * @return the call
     */
    @GET("order")
    Call<Results<OrderGet>> getOrdersForPatientWithOrderType(@Query("patient") String patientUuid,
                                                    @Query("ordertype") String ordertype,
                                                    @Query("v") String representation);

    /**
     * Get all orders for a patient and ordertype and caresetting
     *
     * @param patientUuid the patient uuid
     * @param ordertype the theordertype string
     * @param careSetting the caresetting string
     * @param representation the response representation
     *
     * @return the call
     */
    @GET("order")
    Call<Results<OrderGet>> getOrdersForPatient(@Query("patient") String patientUuid,
                                       @Query("ordertype") String ordertype,
                                       @Query("careSetting") String careSetting,
                                       @Query("v") String representation);

    /**
     * Get all orders for a patient from a given date
     *
     * @param patientUuid the patient uuid
     * @param activatedOnOrAfterDate the starting date
     * @param representation the response representation
     *
     * @return the call
     */
    @GET("order")
    Call<Results<OrderGet>> getOrdersForPatientFromDate(@Query("patient") String patientUuid,
                                               @Query("activatedOnOrAfterDate") String activatedOnOrAfterDate,
                                               @Query("v") String representation);

    /**
     * Get all orders for a patient and ordertype and caresetting and from a given date
     *
     * @param patientUuid the patient uuid
     * @param ordertype the theordertype string
     * @param careSetting the caresetting string
     * @param activatedOnOrAfterDate the starting date
     * @param representation the response representation
     *
     * @return the call
     */
    @GET("order")
    Call<Results<OrderGet>> getOrdersForPatient(@Query("patient") String patientUuid,
                                       @Query("ordertype") String ordertype,
                                       @Query("careSetting") String careSetting,
                                       @Query("activatedOnOrAfterDate") String activatedOnOrAfterDate,
                                       @Query("v") String representation);

    /**
     * Get all orders for a patient, active on a given day, matching the exact query O3's Orders
     * chart tab makes (verified against a live deployment).
     *
     * @param patientUuid the patient uuid
     * @param careSetting the care setting uuid
     * @param representation the response representation
     * @param activatedOnOrAfterDate the start of the day to check (yyyy-MM-dd)
     * @param activatedOnOrBeforeDate the end of the day to check (yyyy-MM-dd)
     * @param excludeDiscontinueOrders whether to exclude DISCONTINUE action orders
     * @param excludeCanceledAndExpired whether to exclude cancelled/expired orders
     * @param orderTypes the order type uuid to filter to (e.g. Drug Order vs Test Order) -
     *                    verified to filter server-side on this server
     * @return the call
     */
    @GET("order")
    Call<Results<OrderGet>> getOrdersForPatientOnDate(@Query("patient") String patientUuid,
                                       @Query("careSetting") String careSetting,
                                       @Query("v") String representation,
                                       @Query("activatedOnOrAfterDate") String activatedOnOrAfterDate,
                                       @Query("activatedOnOrBeforeDate") String activatedOnOrBeforeDate,
                                       @Query("excludeDiscontinueOrders") boolean excludeDiscontinueOrders,
                                       @Query("excludeCanceledAndExpired") boolean excludeCanceledAndExpired,
                                       @Query("orderTypes") String orderTypes);

    /**
     * Get all non-discontinued orders for a patient, care setting, and order type - matches O3's
     * Medications widget query (`useMedicationOrders`), which fetches every drug order regardless
     * of date and buckets them into active/upcoming/past client-side instead of filtering by day.
     *
     * @param patientUuid the patient uuid
     * @param careSetting the care setting uuid
     * @param orderTypes the order type uuid to filter to
     * @param representation the response representation
     * @param excludeDiscontinueOrders whether to exclude DISCONTINUE action orders
     * @return the call
     */
    @GET("order")
    Call<Results<OrderGet>> getOrdersForPatientExcludingDiscontinued(@Query("patient") String patientUuid,
                                       @Query("careSetting") String careSetting,
                                       @Query("orderTypes") String orderTypes,
                                       @Query("v") String representation,
                                       @Query("excludeDiscontinueOrders") boolean excludeDiscontinueOrders);

    /**
     * Gets full details for a single drug order, including the DrugOrder-only fields (drug, dose,
     * route, frequency, etc.) the list-level `order` representation deliberately omits - needed to
     * pre-fill Modify/Renew and to discontinue a drug order (which needs the drug uuid).
     *
     * @param orderUuid the Order uuid
     * @param representation the response representation
     * @return the call
     */
    @GET("order/{uuid}")
    Call<DrugOrderDetails> getDrugOrderDetails(@Path("uuid") String orderUuid, @Query("v") String representation);

    /**
     * Delete an order
     *
     * @param orderUuid the Order uuid
     * @return the call
     */
    @DELETE("order/{uuid}")
    Call<ResponseBody> deleteOrder(@Path("uuid") String orderUuid);
  
    /**
     * Get all the available Drugs
     *
     * @param representation the representation to return
     * @return the call
     */
    @GET("drug")
    Call<Results<Drug>> getAllDrugs(@Query("v") String representation);

    /**
     * Get a Drug by UUID
     *
     * @param uuid the uuid of the drug
     * @param representaion the representation to return
     * @return the call
     */
    @GET("drug/{uuid}")
    Call<Drug> getDrugByUuid(@Path("uuid") String uuid, @Query("v") String representaion);

    /**
     * Create a Drug by UUID
     *
     * @param drug the object of type DrugCreate object to create
     * @return the call
     */
    @POST("drug")
    Call<Drug> createDrug(@Body DrugCreate drug);

    /**
     * Update a Drug by UUID
     *
     * @param uuid the uuid of the Drug
     * @param drug the object of type DrugCreate object to create
     * @return the call
     */
    @POST("drug/{uuid}")
    Call<Drug> updateDrug(@Path("uuid") String uuid, @Body DrugCreate drug);

    /**
     * Delete a Drug
     *
     * @param uuid the uuid of the Drug
     * @return the call
     */
    @DELETE("drug/{uuid}")
    Call<Drug> deleteDrug(@Path("uuid") String uuid);

    /**
     * Searches drugs by name, for the drug order form's autocomplete. Verified against a live O3
     * deployment's `GET drug?q=...` request.
     *
     * @param query the search text
     * @param representation the response representation
     * @return the matching drugs
     */
    @GET("drug")
    Call<Results<DrugSearchResult>> searchDrugs(@Query("q") String query, @Query("v") String representation);

    /**
     * Searches concepts by name, for the lab order form's test search. The server does not
     * support filtering this by concept class, so results must be filtered client-side.
     *
     * @param query the search text
     * @param representation the response representation
     * @return the matching concepts
     */
    @GET("concept")
    Call<Results<TestSearchResult>> searchConcepts(@Query("q") String query, @Query("v") String representation);

    /**
     * Gets an order type by uuid (e.g. to resolve/display "Drug Order" vs "Test Order" for an
     * existing order). Verified against a live O3 deployment.
     *
     * @param uuid the order type uuid
     * @return the order type
     */
    @GET("ordertype/{uuid}")
    Call<OrderTypeInfo> getOrderType(@Path("uuid") String uuid);

    /**
     * Gets the configuration needed to build a drug order form: routes, dosing/dispensing units,
     * duration units, and order frequencies. Verified against a live O3 deployment's
     * `GET orderentryconfig` request.
     *
     * @return the order entry config
     */
    @GET("orderentryconfig")
    Call<OrderEntryConfig> getOrderEntryConfig();

    /**
     * Get all the available Programs
     *
     * @param representation the representation to return
     * @return the call
     */
    @GET("program")
    Call<Results<ProgramGet>> getAllPrograms(@Query("v") String representation);

    /**
     * Get a Program by UUID
     *
     * @param uuid the uuid of the Program
     * @param representaion the representation to return
     * @return the call
     */
    @GET("program/{uuid}")
    Call<ProgramGet> getProgramByUuid(@Path("uuid") String uuid, @Query("v") String representaion);

    /**
     * Create a new Program
     *
     * @param program the object of type ProgramCreate
     * @return the call
     */
    @POST("program")
    Call<ProgramGet> createProgram(@Body ProgramCreate program);

    /**
     * Update a Program by UUID
     *
     * @param uuid the uuid of the Program
     * @param updatedProgram the object of type ProgramCreate
     * @return the call
     */
    @POST("program/{uuid}")
    Call<ProgramGet> updateProgram(@Path("uuid") String uuid, @Body ProgramCreate updatedProgram);

    /**
     * Delete a Program
     *
     * @param uuid the uuid of the Program to delete
     * @return the call
     */
    @DELETE("program/{uuid}")
    Call<ProgramGet> deleteProgram(@Path("uuid") String uuid);
}
