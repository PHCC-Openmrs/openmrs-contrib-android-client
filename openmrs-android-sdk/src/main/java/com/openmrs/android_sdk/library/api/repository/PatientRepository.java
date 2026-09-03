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

                    return patient;
                } else {
                    String errorMsg = response.errorBody() != null ? response.errorBody().string() : response.message();
                    logger.e("syncPatient server error: " + errorMsg);
                    
                    if (errorMsg.contains("PatientIdentifier.error.duplicateIdentifier")) {
                        logger.i("Duplicate identifier detected. Verifying server record...");
                        String patientIdentifierStr = patient.getIdentifier().getIdentifier();
                        Response<Results<Patient>> searchResponse = restApi.getPatients(patientIdentifierStr, "full").execute();
                        if (searchResponse.isSuccessful() && searchResponse.body() != null && !searchResponse.body().getResults().isEmpty()) {
                            Patient serverPatient = searchResponse.body().getResults().get(0);
                            
                            // Only link if names match to prevent incorrect merging due to server-side ID reuse
                            String serverGiven = (serverPatient.getName() != null && serverPatient.getName().getGivenName() != null) ? serverPatient.getName().getGivenName() : "";
                            String serverFamily = (serverPatient.getName() != null && serverPatient.getName().getFamilyName() != null) ? serverPatient.getName().getFamilyName() : "";
                            String localGiven = (patient.getName() != null && patient.getName().getGivenName() != null) ? patient.getName().getGivenName() : "";
                            String localFamily = (patient.getName() != null && patient.getName().getFamilyName() != null) ? patient.getName().getFamilyName() : "";

                            if (serverGiven.equalsIgnoreCase(localGiven) && serverFamily.equalsIgnoreCase(localFamily)) {
                                logger.i("Names match. Linking local patient to existing server record (UUID: " + serverPatient.getUuid() + ")");
                                patient.setUuid(serverPatient.getUuid());
                                if (serverPatient.getIdentifiers() != null && !serverPatient.getIdentifiers().isEmpty()) {
                                    patient.setIdentifiers(serverPatient.getIdentifiers());
                                }
                                patientDAO.updatePatient(patient.getId(), patient);
                                return patient;
                            } else {
                                logger.e("Duplicate ID found on server, but NAMES DO NOT MATCH. Server: " + serverPatient.getName().getNameString() + ", Local: " + patient.getName().getNameString());
                                throw new Exception("Sync failed: The ID " + patientIdentifierStr + " is already assigned to a different patient on the server (" + serverPatient.getName().getNameString() + "). Please check your server's ID generator.");
                            }
                        }
                    }
else if (errorMsg.contains("PatientIdentifier.error.insufficientPrivilege")) {
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
            if (NetworkUtils.isOnline()) {
                Call<PatientDto> call = restApi.updatePatient(
                        patient.getUpdatedPatientDto(), patient.getUuid(), "full");
                Response<PatientDto> response = call.execute();

                if (response.isSuccessful()) {
                    PatientDto patientDto = response.body();
                    patient.setBirthdate(patientDto.getPerson().getBirthdate());
                    patient.setUuid(patientDto.getUuid());

                    if (patient.getPhoto() != null) uploadPatientPhoto(patient);

                    patientDAO.updatePatient(patient.getId(), patient);

                    return ResultType.PatientUpdateSuccess;
                } else {
                    throw new Exception("updatePatient error: " + response.message());
                }
            } else {
                patientDAO.updatePatient(patient.getId(), patient);

                Data data = new Data.Builder().putString(PRIMARY_KEY_ID, patient.getId().toString()).build();
                Constraints constraints = new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
                getWorkManager().enqueue(new OneTimeWorkRequest.Builder(UpdatePatientWorker.class).setConstraints(constraints).setInputData(data).build());

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
