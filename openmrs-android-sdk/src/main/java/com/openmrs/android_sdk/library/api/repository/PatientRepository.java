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

import static com.openmrs.android_sdk.utilities.ApplicationConstants.PRIMARY_KEY_ID;

import com.google.gson.Gson;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import rx.Observable;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;

import com.openmrs.android_sdk.R;
import com.openmrs.android_sdk.library.OpenmrsAndroid;
import com.openmrs.android_sdk.library.OpenMRSLogger;
import com.openmrs.android_sdk.library.api.RestApi;
import com.openmrs.android_sdk.library.api.RestServiceBuilder;
import com.openmrs.android_sdk.library.api.workers.UpdatePatientWorker;
import com.openmrs.android_sdk.library.dao.EncounterCreateRoomDAO;
import com.openmrs.android_sdk.library.databases.entities.LocationEntity;
import com.openmrs.android_sdk.library.dao.PatientDAO;
import com.openmrs.android_sdk.library.databases.AppDatabaseHelper;
import com.openmrs.android_sdk.library.models.Encountercreate;
import com.openmrs.android_sdk.library.models.IdGenPatientIdentifiers;
import com.openmrs.android_sdk.library.models.IdentifierType;
import com.openmrs.android_sdk.library.models.Module;
import com.openmrs.android_sdk.library.models.Patient;
import com.openmrs.android_sdk.library.models.PatientDto;
import com.openmrs.android_sdk.library.models.PersonAttribute;
import com.openmrs.android_sdk.library.models.PersonAttributeType;
import com.openmrs.android_sdk.library.models.PatientDtoUpdate;
import com.openmrs.android_sdk.library.models.PatientIdentifier;
import com.openmrs.android_sdk.library.models.PatientPhoto;
import com.openmrs.android_sdk.library.models.ResultType;
import com.openmrs.android_sdk.library.models.Results;
import com.openmrs.android_sdk.library.models.SystemProperty;
import com.openmrs.android_sdk.utilities.ApplicationConstants;
import com.openmrs.android_sdk.utilities.ModuleUtils;
import com.openmrs.android_sdk.utilities.NetworkUtils;
import com.openmrs.android_sdk.utilities.PatientComparator;
import com.openmrs.android_sdk.utilities.SyncedPatientCleanupUtil;
import com.openmrs.android_sdk.utilities.ToastUtil;

/**
 * The type Patient repository.
 */
@Singleton
public class PatientRepository extends BaseRepository {
    private final PatientDAO patientDAO;
    private final LocationRepository locationRepository;
    private final EncounterRepository encounterRepository;
    private final RestApi restApi;
    private final OpenMRSLogger logger;

    /**
     * Instantiates a new Patient repository.
     */
    @Inject
    public PatientRepository(PatientDAO patientDAO, LocationRepository locationRepository,
                             EncounterRepository encounterRepository, RestApi restApi, OpenMRSLogger logger) {
        this.patientDAO = patientDAO;
        this.locationRepository = locationRepository;
        this.encounterRepository = encounterRepository;
        this.restApi = restApi;
        this.logger = logger;
    }

    /**
     * Uploads a patient to the server.
     *
     * @param patient the patient to be registered in the server
     */
    public Observable<Patient> syncPatient(final Patient patient) {
        return AppDatabaseHelper.createObservableIO(() -> {
            try {
                // Identifiers are built up from two independent sources: the OpenMRS ID, which
                // this app always auto-generates via idgen, and any other identifiers already
                // attached to the patient (e.g. a user-entered National ID from the registration
                // form) - those are looked up by type rather than by list position, since the
                // OpenMRS ID is no longer guaranteed to be the only (or first) identifier.
                final List<PatientIdentifier> identifiers = new ArrayList<>(patient.getIdentifiers());
                PatientIdentifier openmrsIdIdentifier = patient.getIdentifierByType(ApplicationConstants.IdentifierSource.DEFAULT_IDENTIFIER_TYPE_UUID);

                LocationEntity location = locationRepository.getLocation();
                if (location == null || location.getUuid() == null || location.getUuid().isEmpty()) {
                    throw new IOException("Location UUID is required for registration. Please check your login location.");
                }

                if (openmrsIdIdentifier == null || openmrsIdIdentifier.getIdentifier() == null || openmrsIdIdentifier.getIdentifier().isEmpty()) {
                    logger.i("Generating new OpenMRS ID for patient...");
                    String generatedId = getIdGenPatientIdentifier();
                    if (generatedId == null || generatedId.isEmpty()) {
                        throw new IOException("Failed to generate identifier from server");
                    }
                    openmrsIdIdentifier = new PatientIdentifier();
                    openmrsIdIdentifier.setIdentifier(generatedId);
                    openmrsIdIdentifier.setIdentifierType(getPatientIdentifierType());
                    openmrsIdIdentifier.setLocation(location);
                    openmrsIdIdentifier.setPreferred(true);
                    // Index 0 is treated elsewhere (e.g. Patient#getIdentifier) as the primary identifier.
                    identifiers.add(0, openmrsIdIdentifier);
                    logger.i("Generated OpenMRS ID: " + generatedId);
                } else {
                    logger.i("Existing OpenMRS ID found: " + openmrsIdIdentifier.getIdentifier() + ". Ensuring type and location are set.");
                    if (openmrsIdIdentifier.getLocation() == null) {
                        openmrsIdIdentifier.setLocation(location);
                    }
                    openmrsIdIdentifier.setPreferred(true);
                }

                // Any other identifiers (e.g. National ID) just need a location filled in if missing.
                for (PatientIdentifier otherIdentifier : identifiers) {
                    if (otherIdentifier != openmrsIdIdentifier && otherIdentifier.getLocation() == null) {
                        otherIdentifier.setLocation(location);
                    }
                }

                patient.setIdentifiers(identifiers);

                logger.i("Using Birthdate: " + patient.getBirthdate());

                PatientDto patientDto = patient.getPatientDto();
                if (patient.getUuid() != null && !patient.getUuid().isEmpty()) {
                    patientDto.setUuid(patient.getUuid());
                    if (patientDto.getPerson() != null) {
                        patientDto.getPerson().setUuid(patient.getUuid());
                    }
                }

                try {
                    String payload = new Gson().toJson(patientDto);
                    logger.i("Full Registration Payload: " + payload);
                } catch (Exception e) {
                    logger.w("Failed to log payload JSON: " + e.getMessage());
                }

                logger.i("Sending registration request for patient: " + (patient.getName() != null ? patient.getName().getNameString() : "ID " + patient.getId()));
                Response<PatientDto> response = restApi.createPatient(patientDto).execute();
                if (response.isSuccessful()) {
                    PatientDto returnedPatientDto = response.body();
                    logger.i("Server registration successful. UUID: " + returnedPatientDto.getUuid());

                    patient.setUuid(returnedPatientDto.getUuid());
                    // Deliberately NOT replacing patient.identifiers with returnedPatientDto's:
                    // the create-patient response uses the default representation, whose
                    // identifiers only carry {uuid, display, links} - no identifier value and no
                    // identifierType - so overwriting here would wipe out the (correct, just-sent)
                    // identifiers we already have, including their types, causing them to be
                    // dropped on the next local save/reload. What we already hold is exactly what
                    // the server just accepted, so it needs no updating.

                    if (patient.getPhoto() != null) {
                        uploadPatientPhoto(patient);
                    }

                    boolean updated = patientDAO.updatePatient(patient.getId(), patient);
                    logger.i("Local DB update successful: " + updated);
                    
                    if (patient.getEncounters() != null && !patient.getEncounters().isEmpty()) {
                        addEncounters(patient);
                    }

                    SyncedPatientCleanupUtil.checkAndCleanupIfFullySynced(patient.getId());
                    return patient;
                } else {
                    String errorMsg = response.errorBody() != null ? response.errorBody().string() : response.message();
                    logger.e("syncPatient server error: " + errorMsg);

                    // The server reports a duplicate identifier as a plain global error - e.g.
                    // "Identifier 734567811 already in use by another patient" - rather than the
                    // "PatientIdentifier.error.duplicateIdentifier" code some older/other OpenMRS
                    // versions use, so both shapes are checked here. Whichever identifier the
                    // message names (not necessarily the OpenMRS ID) is the one searched for below.
                    java.util.regex.Matcher duplicateMatcher = java.util.regex.Pattern
                            .compile("[Ii]dentifier ([^\"]+?) already in use by another patient")
                            .matcher(errorMsg);
                    boolean isDuplicateIdentifier = duplicateMatcher.find() || errorMsg.contains("PatientIdentifier.error.duplicateIdentifier");

                    if (isDuplicateIdentifier) {
                        String patientIdentifierStr = duplicateMatcher.groupCount() > 0 && duplicateMatcher.group(1) != null
                                ? duplicateMatcher.group(1)
                                : patient.getIdentifier().getIdentifier();
                        logger.i("Duplicate identifier detected (" + patientIdentifierStr + "). Verifying server record...");
                        // Linked purely by identifier, not by name: names entered on a field
                        // registration form can legitimately differ from the server's record
                        // (spelling/translation/transliteration, nicknames, missing middle names),
                        // and the server's National ID is the one value a field worker can be
                        // expected to have gotten right. The search itself (?q=<identifier>) can
                        // return multiple loosely-matching results though, so this still verifies
                        // the matched patient's OWN identifiers contain an exact match for the
                        // identifier in question - it doesn't just trust the first search result.
                        Response<Results<PatientDto>> searchResponse = restApi.getPatientsDto(patientIdentifierStr, "full").execute();
                        PatientDto matchedPatientDto = null;
                        if (searchResponse.isSuccessful() && searchResponse.body() != null) {
                            for (PatientDto candidate : searchResponse.body().getResults()) {
                                if (candidate.getIdentifiers() == null) continue;
                                for (PatientIdentifier candidateIdentifier : candidate.getIdentifiers()) {
                                    if (candidateIdentifier.getIdentifier() != null
                                            && candidateIdentifier.getIdentifier().trim().equalsIgnoreCase(patientIdentifierStr.trim())) {
                                        matchedPatientDto = candidate;
                                        break;
                                    }
                                }
                                if (matchedPatientDto != null) break;
                            }
                        }

                        if (matchedPatientDto != null) {
                            logger.i("Identifier match confirmed. Linking local patient to existing server record (UUID: " + matchedPatientDto.getUuid() + ")");
                            patient.setUuid(matchedPatientDto.getUuid());
                            if (matchedPatientDto.getIdentifiers() != null && !matchedPatientDto.getIdentifiers().isEmpty()) {
                                patient.setIdentifiers(matchedPatientDto.getIdentifiers());
                            }
                            // This local row's OTHER demographic fields (name, address, phone,
                            // patient status, etc.) are just whatever was needed to pass validation
                            // on this registration form - not a trustworthy description of the real
                            // patient - so flag it: the automatic dashboard sync must never push
                            // them over the real patient's data, only pull. A deliberate edit later
                            // clears this (see PatientRepository#updatePatient).
                            patient.setIdentityLinkedOnly(true);
                            patientDAO.updatePatient(patient.getId(), patient);
                            SyncedPatientCleanupUtil.checkAndCleanupIfFullySynced(patient.getId());
                            return patient;
                        }
                        throw new Exception("This ID (" + patientIdentifierStr + ") is already registered to another patient. Please verify the ID and try again.");
                    } else if (errorMsg.contains("PatientIdentifier.error.insufficientPrivilege")) {
                        logger.e("Sync failed: The logged-in user does not have permission to assign identifiers. Please check OpenMRS user privileges (Add Patient Identifier).");
                        throw new Exception("Sync failed: Insufficient privileges to register patient. Please contact your administrator.");
                    }
                    throw new Exception("syncPatient server error: " + errorMsg);
                }
            } catch (Exception e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                logger.e("Error during syncPatient: " + cause.getClass().getSimpleName() + " - " + cause.getMessage(), cause);
                throw e;
            }
        });
    }

    private void uploadPatientPhoto(final Patient patient) {
        PatientPhoto patientPhoto = new PatientPhoto();
        patientPhoto.setPhoto(patient.getPhoto());
        patientPhoto.setPerson(patient);
        Call<PatientPhoto> personPhotoCall =
                restApi.uploadPatientPhoto(patient.getUuid(), patientPhoto);
        personPhotoCall.enqueue(new Callback<PatientPhoto>() {
            @Override
            public void onResponse(@NonNull Call<PatientPhoto> call, @NonNull Response<PatientPhoto> response) {
                if (!response.isSuccessful()) {
                    getLogger().e(response.message());
                    //string resource added "patient_photo_update_unsuccessful"
                    ToastUtil.error("Patient photo cannot be synced due to server error " + response.message());
                }
            }

            @Override
            public void onFailure(@NonNull Call<PatientPhoto> call, @NonNull Throwable t) {
                getLogger().e(t.getMessage());
                //string resource added "patient_photo_update_unsuccessful"
                ToastUtil.error("Patient photo cannot be synced due to server error " + t.toString());
            }
        });
    }

    /**
     * Registers a patient locally or to the server, according to network state.
     *
     * @param patient the patient to be registered
     * @return Observable result type of registration process
     */
    public Observable<Patient> registerPatient(final Patient patient) {
        logger.i("registerPatient called for: " + (patient.getName() != null ? patient.getName().getNameString() : "unknown"));
        return AppDatabaseHelper.createObservableIO(() -> {
            try {
                Long id = patientDAO.savePatient(patient).single().toBlocking().first();
                patient.setId(id);
                if (NetworkUtils.isOnline()) {
                    try {
                        syncPatient(patient).single().toBlocking().first();
                    } catch (Exception e) {
                        logger.w("Initial sync failed, but patient is saved locally: " + e.getMessage());
                    }
                }
                return patient;
            } catch (Exception e) {
                logger.e("Error in registerPatient", e);
                throw e;
            }
        });
    }

    /**
     * Updates patient locally and remotely.
     *
     * @param patient the patient
     * @return Observable result type
     */
    public Observable<ResultType> updatePatient(final Patient patient) {
        return AppDatabaseHelper.createObservableIO(() -> {
            // A patient can already have a local row (isUpdatePatient) yet still have no uuid, if
            // their original registration was saved locally but never actually created on the
            // server - most commonly because it failed with a duplicate identifier. Editing such a
            // patient (e.g. to correct that identifier) must retry it as a create via syncPatient()
            // - a PUT to /patient/{uuid} below can't work with a null uuid, and would otherwise
            // fail immediately online, or, offline, get queued as a worker that retries forever
            // without ever reaching the server.
            boolean isPreviouslySynced = patient.isSynced();

            if (NetworkUtils.isOnline()) {
                if (!isPreviouslySynced) {
                    syncPatient(patient).single().toBlocking().first();
                    return ResultType.PatientUpdateSuccess;
                }

                // A deliberate edit-and-submit of an already-synced patient is a genuine,
                // intentional description of them from now on - even if their identity had
                // previously been established via syncPatient()'s duplicate-merge path, that no
                // longer applies once the user has explicitly reviewed and resubmitted their
                // details, so the automatic dashboard sync can trust (and push) this data going
                // forward.
                patient.setIdentityLinkedOnly(false);

                Call<PatientDto> call = restApi.updatePatient(
                        patient.getUpdatedPatientDto(), patient.getUuid(), "full");
                Response<PatientDto> response = call.execute();

                if (response.isSuccessful()) {
                    PatientDto patientDto = response.body();
                    patient.setBirthdate(patientDto.getPerson().getBirthdate());
                    patient.setUuid(patientDto.getUuid());

                    if (patient.getPhoto() != null) uploadPatientPhoto(patient);

                    patientDAO.updatePatient(patient.getId(), patient);

                    SyncedPatientCleanupUtil.checkAndCleanupIfFullySynced(patient.getId());
                    return ResultType.PatientUpdateSuccess;
                } else {
                    throw new Exception("updatePatient error: " + response.message());
                }
            } else {
                patient.setIdentityLinkedOnly(false);
                patientDAO.updatePatient(patient.getId(), patient);

                if (isPreviouslySynced) {
                    Data data = new Data.Builder().putString(PRIMARY_KEY_ID, patient.getId().toString()).build();
                    Constraints constraints = new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
                    getWorkManager().enqueue(new OneTimeWorkRequest.Builder(UpdatePatientWorker.class).setConstraints(constraints).setInputData(data).build());
                }
                // else: this patient was never synced - it's already picked up by the existing
                // unsynced-patient registration retry path (PatientService, run on connectivity
                // restore or manual Synchronize), which calls syncPatient() by uuid-null lookup, so
                // no separate PUT-based worker is needed (or would even work) here.

                return ResultType.PatientUpdateLocalSuccess;
            }
        });
    }

    /**
     * Update matching patient.
     *
     * @param patient the locally merged patient
     */
    public Observable<Patient> updateMatchingPatient(final Patient patient) {
        return AppDatabaseHelper.createObservableIO(() -> {

            PatientDtoUpdate patientDto = patient.getUpdatedPatientDto();

            Call<PatientDto> call = restApi.updatePatient(patientDto, patient.getUuid(), ApplicationConstants.API.FULL);
            Response<PatientDto> response = call.execute();

            if (response.isSuccessful()) return patient;
            else throw new IOException(response.message());
        });
    }

    /**
     * Download patient by uuid.
     *
     * @param uuid patient uuid
     * @return Patient observable
     */
    public Observable<Patient> downloadPatientByUuid(@NonNull final String uuid) {
        if (uuid == null || uuid.isEmpty()) {
            return Observable.error(new IllegalArgumentException("UUID cannot be null or empty"));
        }
        return AppDatabaseHelper.createObservableIO(() -> {
            Call<PatientDto> call = restApi.getPatientByUUID(uuid, "full");
            Response<PatientDto> response = call.execute();
            if (response.isSuccessful() && response.body() != null) {
                final PatientDto newPatientDto = response.body();
                if (newPatientDto.getIdentifiers() != null) {
                    logger.i("Downloaded patient identifiers. Count: " + newPatientDto.getIdentifiers().size());
                    for (PatientIdentifier id : newPatientDto.getIdentifiers()) {
                        logger.i(" > ID: '" + id.getIdentifier() + "', Type: " + (id.getIdentifierType() != null ? id.getIdentifierType().getDisplay() : "null"));
                    }
                }

                Bitmap photo = null;
                try {
                    photo = downloadPatientPhotoByUuid(newPatientDto.getUuid()).toBlocking().first();
                } catch (Exception e) {
                    logger.w("Failed to download patient photo: " + e.getMessage());
                }
                if (photo != null) newPatientDto.getPerson().setPhoto(photo);

                return newPatientDto.getPatient();
            } else {
                String errorMsg = response.errorBody() != null ? response.errorBody().string() : response.message();
                throw new IOException("Error with downloading patient: " + errorMsg);
            }
        });
    }

    /**
     * Download patient photo by uuid.
     *
     * @param uuid patient uuid
     * @return Photo bitmap or null bitmap observable
     */
    public Observable<Bitmap> downloadPatientPhotoByUuid(String uuid) {
        return AppDatabaseHelper.createObservableIO(() -> {
            Call<ResponseBody> call = restApi.downloadPatientPhoto(uuid);
            Response<ResponseBody> response = call.execute();

            if (response.isSuccessful()) {
                try {
                    InputStream inputStream = response.body().byteStream();
                    Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                    inputStream.close();
                    return bitmap;
                } catch (Exception e) {
                    getLogger().e(e.getMessage());
                }
            }
            return null;
        });
    }

    /**
     * Add encounters.
     *
     * @param patient the patient
     */
    public void addEncounters(Patient patient) {
        EncounterCreateRoomDAO dao = db.encounterCreateRoomDAO();
        String enc = patient.getEncounters();
        List<Long> list = new ArrayList<>();
        for (String s : enc.split(","))
            list.add(Long.parseLong(s));

        for (long id : list) {
            Encountercreate encountercreate = dao.getCreatedEncountersByID(id);
            encountercreate.setPatient(patient.getUuid());
            encountercreate.setSynced(false);
            encounterRepository.updateEncounterCreate(encountercreate);
        }
    }

    /**
     * Gets id gen patient identifier.
     *
     * @return the id gen patient identifier
     */
    public String getIdGenPatientIdentifier() throws IOException {
        Response<ResponseBody> response = restApi.generatePatientIdentifier(ApplicationConstants.IdentifierSource.DEFAULT_SOURCE_UUID, new HashMap<String, String>()).execute();
        if (response.isSuccessful() && response.body() != null) {
            String json = response.body().string();
            logger.i("Identifier response: " + json);
            try {
                Map<?, ?> map = new Gson().fromJson(json, Map.class);
                if (map != null && map.get("identifier") != null) {
                    return map.get("identifier").toString();
                }
            } catch (Exception e) {
                logger.e("Failed to parse identifier JSON", e);
            }
            if (json.startsWith("\"") && json.endsWith("\"")) {
                json = json.substring(1, json.length() - 1);
            }
            return json;
        } else {
            String errorMsg = response.errorBody() != null ? response.errorBody().string() : response.message();
            logger.e("Failed to generate identifier: " + errorMsg);
            throw new IOException("Failed to generate identifier: " + errorMsg);
        }
    }

    /**
     * Gets patient identifier type (only has uuid).
     *
     * @return the patient identifier type
     */
    public IdentifierType getPatientIdentifierType() {
        IdentifierType identifierType = new IdentifierType();
        identifierType.setUuid(ApplicationConstants.IdentifierSource.DEFAULT_IDENTIFIER_TYPE_UUID);
        identifierType.setDisplay("OpenMRS ID");
        return identifierType;
    }

    /**
     * Gets the National ID identifier type (only has uuid), required by the server alongside the
     * OpenMRS ID.
     *
     * @return the National ID identifier type
     */
    public IdentifierType getNationalIdIdentifierType() {
        IdentifierType identifierType = new IdentifierType();
        identifierType.setUuid(ApplicationConstants.IdentifierSource.NATIONAL_ID_IDENTIFIER_TYPE_UUID);
        identifierType.setDisplay("National ID");
        return identifierType;
    }

    /**
     * Builds a National ID {@link PatientIdentifier} from a user-entered value, ready to be
     * attached to a patient's identifier list before registration/update.
     *
     * @param nationalIdValue the National ID value entered on the registration form
     * @return the National ID identifier
     */
    public PatientIdentifier buildNationalIdIdentifier(String nationalIdValue) {
        PatientIdentifier identifier = new PatientIdentifier();
        identifier.setIdentifier(nationalIdValue);
        identifier.setIdentifierType(getNationalIdIdentifierType());
        identifier.setPreferred(false);
        return identifier;
    }

    /**
     * Builds a {@link PersonAttribute} (e.g. Phone Number, Patient Status) from a user-entered
     * value, ready to be attached to a patient's attribute list before registration/update.
     *
     * @param attributeTypeUuid the uuid of the {@link PersonAttributeType} this value is for
     * @param value             the attribute value entered on the registration form
     * @return the person attribute
     */
    public PersonAttribute buildAttribute(String attributeTypeUuid, String value) {
        PersonAttributeType attributeType = new PersonAttributeType();
        attributeType.setUuid(attributeTypeUuid);
        PersonAttribute attribute = new PersonAttribute();
        attribute.setAttributeType(attributeType);
        attribute.setValue(value);
        return attribute;
    }

    /**
     * Find patients.
     *
     * @param query patient query string
     * @return observable list of patients with matching query
     */
    public Observable<List<Patient>> findPatients(String query) {
        return AppDatabaseHelper.createObservableIO(() -> {
            Call<Results<Patient>> call = restApi.getPatients(query, ApplicationConstants.API.FULL);
            Response<Results<Patient>> response = call.execute();
            if (response.isSuccessful()) {
                return response.body().getResults();
            } else {
                throw new Exception("Error with finding patients: " + response.message());
            }
        });
    }

    /**
     * Load more patients.
     *
     * @param limit      the limit
     * @param startIndex the start index
     * @return observable list of last viewed patients
     */
    public Observable<Results<Patient>> loadMorePatients(int limit, int startIndex) {
        return AppDatabaseHelper.createObservableIO(() -> {
            Call<Results<Patient>> call = restApi.getLastViewedPatients(limit, startIndex);
            Response<Results<Patient>> response = call.execute();
            if (response.isSuccessful()) {
                return response.body();
            } else {
                throw new Exception("Error with loading last viewed patients: " + response.message());
            }
        });
    }

    /**
     * Gets cause of death global id.
     *
     * @return Observable string UUID for cause of death Concept
     */
    public Observable<String> getCauseOfDeathGlobalConceptID() {
        return AppDatabaseHelper.createObservableIO(() -> {
            Call<Results<SystemProperty>> call = restApi.getSystemProperty(ApplicationConstants.CAUSE_OF_DEATH, ApplicationConstants.API.FULL);
            Response<Results<SystemProperty>> response = call.execute();
            if (response.isSuccessful()) {
                return response.body().getResults().get(0).getConceptUUID();
            } else {
                throw new Exception("Error with fetching Cause of Death Concept: " + response.message());
            }
        });
    }

    /**
     * Finds locally-stored patients (previously registered offline, or downloaded for offline
     * use) that already carry the given identifier value under the given identifier type - e.g.
     * checking a just-entered National ID against every patient this device already knows about.
     * Purely a local DB scan, so it works fully offline, unlike server-side duplicate detection
     * (only possible once online, at sync time).
     *
     * @param identifierTypeUuid the identifier type to match (e.g. National ID)
     * @param identifierValue    the identifier value to match
     * @return observable list of locally-stored patients carrying a matching identifier
     */
    public Observable<List<Patient>> findLocalPatientsByIdentifier(final String identifierTypeUuid, final String identifierValue) {
        return AppDatabaseHelper.createObservableIO(() -> {
            List<Patient> matches = new ArrayList<>();
            if (identifierValue == null || identifierValue.trim().isEmpty()) {
                return matches;
            }
            String trimmedValue = identifierValue.trim();
            List<Patient> localPatients = patientDAO.getAllPatients().toBlocking().first();
            for (Patient candidate : localPatients) {
                if (candidate.getIdentifiers() == null) continue;
                for (PatientIdentifier identifier : candidate.getIdentifiers()) {
                    if (identifier.getIdentifierType() != null
                            && identifierTypeUuid.equals(identifier.getIdentifierType().getUuid())
                            && identifier.getIdentifier() != null
                            && identifier.getIdentifier().trim().equalsIgnoreCase(trimmedValue)) {
                        matches.add(candidate);
                        break;
                    }
                }
            }
            return matches;
        });
    }

    /**
     * Fetches similar patients by different strategies:
     * <br> 1. Fetch similar patients from server directly using an API.
     * <br> 2. Fetch patients with similar names, then compare their other similarities locally.
     * <br> 3. Fetch locally saved patients, then compare their similarities.
     *
     * @param patient to find similar patients to
     * @return Observable list of similar patients
     */
    public Observable<List<Patient>> fetchSimilarPatients(final Patient patient) {
        return AppDatabaseHelper.createObservableIO(() -> {
            try {
                if (!NetworkUtils.isOnline()) {
                    List<Patient> localPatients = patientDAO.getAllPatients().toBlocking().first();
                    return new PatientComparator().findSimilarPatient(localPatients, patient);
                }

                Call<Results<Module>> moduleCall = restApi.getModules(ApplicationConstants.API.FULL);
                Response<Results<Module>> response = moduleCall.execute();

                if (!response.isSuccessful()) return fetchSimilarPatientsAndCalculateLocally(patient);

                if (ModuleUtils.isRegistrationCore1_7orAbove(response.body().getResults())) {
                    return fetchSimilarPatientsFromServer(patient);
                } else {
                    return fetchSimilarPatientsAndCalculateLocally(patient);
                }
            } catch (Exception e) {
                logger.e("Error fetching similar patients: " + e.getMessage());
                try {
                    return fetchSimilarPatientsAndCalculateLocally(patient);
                } catch (Exception ex) {
                    logger.e("Fallback similarity check failed: " + ex.getMessage());
                    return new ArrayList<>();
                }
            }
        });
    }

    /**
     * Fetches similar patients directly from server.
     *
     * @param patient the patient to fetch similar patient to
     * @return list of similar patients
     */
    private List<Patient> fetchSimilarPatientsFromServer(final Patient patient) throws Exception {
        Map<String, String> queryMap = patient.toMap();
        if (queryMap.isEmpty()) {
            return new ArrayList<>();
        }
        Call<Results<Patient>> call = restApi.getSimilarPatients(queryMap);
        Response<Results<Patient>> response = call.execute();
        if (response.isSuccessful() && response.body() != null) return response.body().getResults();
        else return fetchSimilarPatientsAndCalculateLocally(patient);
    }

    /**
     * Fetches patients with similar names from server, then calculates other similarities locally.
     *
     * @param patient the patient to fetch similar patient to
     * @return list of similar patients
     */
    private List<Patient> fetchSimilarPatientsAndCalculateLocally(final Patient patient) throws Exception {
        String givenName = (patient.getName() != null) ? patient.getName().getGivenName() : null;
        if (givenName == null || givenName.isEmpty()) {
            return new ArrayList<>();
        }
        Call<Results<PatientDto>> call = restApi.getPatientsDto(givenName, ApplicationConstants.API.FULL);
        Response<Results<PatientDto>> response = call.execute();
        if (response.isSuccessful() && response.body() != null) {
            List<Patient> patientList = new ArrayList<>();
            for (PatientDto p : response.body().getResults()) patientList.add(p.getPatient());
            return new PatientComparator().findSimilarPatient(patientList, patient);
        } else {
            throw new Exception("fetchSimilarPatientAndCalculateLocally error: " + response.message());
        }
    }
}
