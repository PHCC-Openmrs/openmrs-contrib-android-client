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
package com.openmrs.android_sdk.utilities

import com.openmrs.android_sdk.library.models.EncounterType


object ApplicationConstants {
    const val UUID_LENGTH = 36
    const val EMPTY_STRING = ""
    const val SERVER_URL = "server_url"
    const val SESSION_TOKEN = "session_id"
    const val AUTHORIZATION_TOKEN = "authorisation"
    const val SECRET_KEY = "secretKey"
    const val LOCATION = "location"
    const val FIRST = true
    const val VISIT_TYPE_UUID = "visit_type_uuid"
    const val LAST_SESSION_TOKEN = "last_session_id"
    const val LAST_LOGIN_SERVER_URL = "last_login_server_url"
    const val DEFAULT_OPEN_MRS_URL = "http://192.168.5.228/openmrs"
    const val DB_NAME = "openmrs.db"
    const val DB_PASSWORD_BCRYPT_PEPPER = "$2a$08\$iUp3M1VapYpjcAXQBNX6uu"
    const val DB_PASSWORD_LITERAL_PEPPER = "Open Sesame"
    const val DEFAULT_VISIT_TYPE_UUID = "7b0f5697-27e3-40c4-8bae-f4049abfb4ed"
    const val DEFAULT_BCRYPT_ROUND = 8
    const val SPLASH_TIMER = 3500
    const val PACKAGE_NAME = "org.openmrs.mobile"
    const val USER_GUIDE = "https://openmrs.github.io/openmrs-android-client-user-guide/getting-started.html"
    const val MESSAGE_RFC_822 = "message/rfc822"
    const val FLAG = "flag"
    const val ERROR = "error"
    const val URI_FILE = "file://"
    const val URI_IMAGE = "image/*"
    const val IMAGE_JPEG = "image/jpeg"
    const val INTENT_KEY_PHOTO = "photo"
    const val INTENT_KEY_NAME = "name"
    const val READ_MODE = "r"
    const val MIME_TYPE_MAILTO = "mailto:"
    const val OPENMRS_PREF_FILE = "OpenMRSPrefFile"
    const val VITAL_NAME = "vitalName"
    const val BUNDLE = "bundle"
    const val URI_CONTENT = "content://"
    const val MIME_TYPE_VND = "vnd"
    const val ASPECT_RATIO_FOR_CROPPING = 5f
    const val CAUSE_OF_DEATH = "concept.causeOfDeath"
    const val MALE = "M";
    const val EMPTY_DASH_REPRESENTATION = "---"
    const val COMMA_WITH_SPACE = ", "
    const val PRIMARY_KEY_ID = "_id"
    const val MIN_NUMBER_OF_PATIENTS_TO_SHOW = 7;
    const val ABOUT_OPENMRS_URL = "https://openmrs.org/about/"

    object OpenMRSSharedPreferenceNames {
        const val SHARED_PREFERENCES_NAME = "shared_preferences"
    }

    object API {
        const val REST_ENDPOINT = "/ws/rest/v1/"
        const val FULL = "full"
        const val TAG_ADMISSION_LOCATION = "Admission Location"
        const val FHIR2_LOCATION_ENDPOINT = "/ws/fhir2/R4/Location"
    }

    object RBAC {
        // matches org.openmrs.util.RoleConstants.SUPERUSER in openmrs-core
        const val SUPERUSER_ROLE_NAME = "System Developer"
    }

    /** OpenMRS core privilege names (see https://wiki.openmrs.org/display/docs/Privileges) used to gate UI elements. */
    object Privileges {
        const val GET_PATIENTS = "Get Patients"
        const val ADD_PATIENTS = "Add Patients"
        const val EDIT_PATIENTS = "Edit Patients"
        const val DELETE_PATIENTS = "Delete Patients"
        const val GET_VISITS = "Get Visits"
        const val ADD_VISITS = "Add Visits"
        const val EDIT_VISITS = "Edit Visits"
        const val ADD_ENCOUNTERS = "Add Encounters"
        const val GET_ENCOUNTERS = "Get Encounters"
        const val GET_OBSERVATIONS = "Get Observations"
        const val ADD_OBSERVATIONS = "Add Observations"
        const val EDIT_OBSERVATIONS = "Edit Observations"
        const val GET_DIAGNOSES = "Get Diagnoses"
        const val FORM_ENTRY = "Form Entry"
        const val GET_ALLERGIES = "Get Allergies"
        const val ADD_ALLERGIES = "Add Allergies"
        const val EDIT_ALLERGIES = "Edit Allergies"
        const val REMOVE_ALLERGIES = "Remove Allergies"
        const val GET_PROVIDERS = "Get Providers"
        const val MANAGE_PROVIDERS = "Manage Providers"
        const val GET_ENCOUNTER_ROLES = "Get Encounter Roles"
        /** From the `appointments` module, not core OpenMRS - confirmed from a live server's 401 body. */
        const val VIEW_APPOINTMENTS = "View Appointments"
        const val MANAGE_APPOINTMENTS = "Manage Appointments"
        const val GET_ORDERS = "Get Orders"
        const val ADD_ORDERS = "Add Orders"
    }

    object UserKeys {
        const val USER_NAME = "username"
        const val PASSWORD = "password"
        const val HASHED_PASSWORD = "hashedPassword"
        const val USER_PERSON_NAME = "userDisplay"
        const val USER_UUID = "userUUID"
        const val LOGIN = "login"
        const val FIRST_TIME = "firstTime"
    }

    object DialogTAG {
        const val LOGOUT_DIALOG_TAG = "logoutDialog"
        const val END_VISIT_DIALOG_TAG = "endVisitDialogTag"
        const val START_VISIT_DIALOG_TAG = "startVisitDialog"
        const val START_VISIT_IMPOSSIBLE_DIALOG_TAG = "startVisitImpossibleDialog"
        const val WARNING_LOST_DATA_DIALOG_TAG = "warningLostDataDialog"
        const val SIMILAR_PATIENTS_TAG = "similarPatientsDialogTag"
        const val DELETE_PATIENT_DIALOG_TAG = "deletePatientDialogTag"
        const val DELETE_PROVIDER_DIALOG_TAG = "deleteProviderDialogTag"
        const val LOCATION_DIALOG_TAG = "locationDialogTag"
        const val CREDENTIAL_CHANGED_DIALOG_TAG = "locationDialogTag"
        const val MULTI_DELETE_PATIENT_DIALOG_TAG = "multiDeletePatientDialogTag"
    }

    object RegisterPatientRequirements {
        const val MAX_PATIENT_AGE = 120
    }

    object IdentifierSource {
        const val DEFAULT_SOURCE_UUID = "8549f706-7e85-4c1d-9424-217d50a2988b"
        const val DEFAULT_IDENTIFIER_TYPE_UUID = "05a29f94-c0ed-11e2-94be-8c13b969e334"
        const val NATIONAL_ID_IDENTIFIER_TYPE_UUID = "e7d4b9a1-6f3c-4d2e-9b8a-1c5f6e7d8a9b"
        const val NATIONAL_ID_FORMAT_REGEX = "^[4789]\\d{8}$"
    }

    object PersonAttributeTypes {
        const val PHONE_NUMBER_UUID = "14d4f066-15f5-102d-96e4-000c29c2a5d7"
    }

    object BundleKeys {
        const val CUSTOM_DIALOG_BUNDLE = "customDialogBundle"
        const val PATIENT_ID_BUNDLE = "patientID"
        const val COUNTRIES_BUNDLE = "countries_list"
        const val VISIT_ID = "visitID"
        const val ENCOUNTER_UUID = "encounterUuid"
        const val ENCOUNTERTYPE = "encounterType"
        const val ENCOUNTERTYPE_NAME = "encounterTypeName"
        const val VALUEREFERENCE = "valueReference"
        const val FORM_NAME = "formName"
        const val CALCULATED_LOCALLY = "CALCULATED_LOCALLY"
        const val PATIENTS_AND_MATCHES = "PATIENTS_AND_MATCHES"
        const val FORM_FIELDS_BUNDLE = "formFieldsBundle"
        const val FORM_FIELDS_LIST_BUNDLE = "formFieldsListBundle"
        const val FORM_PAGE_BUNDLE = "formPageBundle"
        const val PATIENT_QUERY_BUNDLE = "patientQuery"
        const val PATIENTS_START_INDEX = "patientsStartIndex"
        const val PROVIDER_BUNDLE = "providerID"
        const val ALLERGY_UUID = "allergy_uuid"
        const val PATIENT_UUID = "patient_uuid"
        const val APPOINTMENT_BUNDLE = "appointment_bundle"
        const val DRUG_SEARCH_RESULT_BUNDLE = "drug_search_result_bundle"
        const val DRUG_ORDER_BASKET_ITEM_ID_BUNDLE = "drug_order_basket_item_id_bundle"
        const val TEST_SEARCH_RESULT_BUNDLE = "test_search_result_bundle"
        const val TEST_ORDER_BASKET_ITEM_ID_BUNDLE = "test_order_basket_item_id_bundle"
        const val ORDER_BUNDLE = "order_bundle"
    }

    object ServiceActions {
        const val START_CONCEPT_DOWNLOAD_ACTION = "com.openmrs.mobile.services.conceptdownloadservice.action.startforeground"
        const val STOP_CONCEPT_DOWNLOAD_ACTION = "com.openmrs.mobile.services.conceptdownloadservice.action.stopforeground"
    }

    object BroadcastActions {
        const val CONCEPT_DOWNLOAD_BROADCAST_INTENT_ID = "com.openmrs.mobile.services.conceptdownloadservice.action.broadcastintent"
        const val CONCEPT_DOWNLOAD_BROADCAST_INTENT_KEY_COUNT = "com.openmrs.mobile.services.conceptdownloadservice.broadcastintent.key.count"
        const val AUTHENTICATION_CHECK_BROADCAST_ACTION = "org.openmrs.mobile.services.AuthenticateCheckService"
    }

    object ServiceNotificationId {
        const val CONCEPT_DOWNLOADFOREGROUND_SERVICE = 101
    }

    object SystemSettingKeys {
        const val WS_REST_MAX_RESULTS_ABSOLUTE = "webservices.rest.maxResultsAbsolute"
    }

    object EncounterTypes {
        const val VITALS = "67a71486-1a54-468f-ac3e-7091a9a79584"

        @JvmField
        var ENCOUNTER_TYPES_DISPLAYS = arrayOf(
            EncounterType.VITALS,
            EncounterType.ADMISSION,
            EncounterType.DISCHARGE,
            EncounterType.VISIT_NOTE,
            EncounterType.WARD_ADMISSION,
            EncounterType.WARD_DISCHARGE,
            EncounterType.TRANSFER_REQUEST,
            EncounterType.ADULT_VISIT,
            EncounterType.BED_ASSIGNMENT,
            EncounterType.CHECK_IN,
            EncounterType.CHECK_OUT,
            EncounterType.CONSULTATION,
            EncounterType.IMMUNIZATIONS,
            EncounterType.INPATIENT_NOTE,
            EncounterType.INTRA_HOSPITAL_TRANSFER,
            EncounterType.LAB_RESULTS,
            EncounterType.MENTAL_HEALTH_ASSESSMENT,
            EncounterType.ORDER,
            EncounterType.TRANSFER,
            EncounterType.SURGICAL_OPERATION,
            EncounterType.REFERRAL,
            EncounterType.COVID_19,
            EncounterType.OUTPATIENT_DEPT,
            EncounterType.SOAP_NOTE,
            EncounterType.NCD_PATIENT_CARD
        )
    }

    object RequestCodes {
        const val START_SETTINGS_REQ_CODE = 102
        const val IMAGE_REQUEST = 1
        const val GALLERY_IMAGE_REQUEST = 2
    }

    object OpenMRSThemes {
        const val KEY_DARK_MODE = "key_dark_mode"
    }

    object OpenMRSlanguage {
        const val KEY_LANGUAGE_MODE = "key_language_mode"
        val LANGUAGE_LIST = arrayOf("English", "हिन्दी")
        val LANGUAGE_CODE = arrayOf("en", "hi")
    }

    object ShowCaseViewConstants {
        const val SHOW_FIND_PATIENT = 1
        const val SHOW_ACTIVE_VISITS = 2
        const val SHOW_REGISTER_PATIENT = 3
        const val SHOW_FORM_ENTRY = 4
        const val SHOW_MANAGE_PROVIDERS = 5
    }

    object TypeFacePathConstants {
        const val MONTSERRAT = "fonts/Roboto/Montserrat.ttf"
        const val ROBOTO_LIGHT = "fonts/Roboto/Roboto-Light.ttf"
        const val ROBOTO_LIGHT_ITALIC = "fonts/Roboto/Roboto-LightItalic.ttf"
        const val ROBOTO_MEDIUM = "fonts/Roboto/Roboto-Medium.ttf"
        const val ROBOTO_MEDIUM_ITALIC = "fonts/Roboto/Roboto-MediumItalic.ttf"
        const val ROBOTO_REGULAR = "fonts/Roboto/Roboto-Regular.ttf"
    }

    object PatientDashboardTabs {
        const val DETAILS_TAB_POS = 0
        const val ALLERGY_TAB_POS = 1
        const val DIAGNOSIS_TAB_POS = 2
        const val VISITS_TAB_POS = 3
        const val VITALS_TAB_POS = 4
        const val CHARTS_TAB_POS = 5
        const val TAB_COUNT = 6
    }

    object ConceptDownloadService {
        const val CHANNEL_ID = "conceptCount"
        const val CHANNEL_DESC = "This channel receives new concept count notifications"
        const val CHANNEL_NAME = "Concepts Channel"
    }

    object AllergyModule {
        const val CONCEPT_ALLERGEN_DRUG = "allergy.concept.allergen.drug"
        const val CONCEPT_ALLERGEN_ENVIRONMENT = "allergy.concept.allergen.environment"
        const val CONCEPT_ALLERGEN_FOOD = "allergy.concept.allergen.food"
        const val CONCEPT_REACTION = "allergy.concept.reactions"
        const val CONCEPT_SEVERITY_MILD = "allergy.concept.severity.mild"
        const val CONCEPT_SEVERITY_MODERATE = "allergy.concept.severity.moderate"
        const val CONCEPT_SEVERITY_SEVERE = "allergy.concept.severity.severe"
        const val PROPERTY_FOOD = "FOOD"
        const val PROPERTY_DRUG = "DRUG"
        const val PROPERTY_OTHER = "OTHER"
        const val PROPERTY_MILD = "Mild"
        const val PROPERTY_MODERATE = "Moderate"
        const val PROPERTY_SEVERE = "Severe"
        const val SELECT_ALLERGEN = "Select Allergen"
        const val SELECT_REACTION = "Reactions (you can select multiple)"
    }
}
