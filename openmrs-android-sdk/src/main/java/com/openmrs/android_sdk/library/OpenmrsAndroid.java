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

package com.openmrs.android_sdk.library;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import androidx.annotation.Nullable;

import com.openmrs.android_sdk.library.models.EncounterProviderCreate;
import com.openmrs.android_sdk.library.models.Session;
import com.openmrs.android_sdk.utilities.ApplicationConstants;

import org.mindrot.jbcrypt.BCrypt;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static android.content.Context.MODE_PRIVATE;

/**
 * The type Openmrs android.
 */
public class OpenmrsAndroid {
    private volatile static Context instance;
    private static final String OPENMRS_DIR_NAME = "OpenMRS";
    private static final String OPENMRS_DIR_PATH = File.separator + OPENMRS_DIR_NAME;
    private static String externalDirectoryPath;
    private static String secretKey;

    private static OpenMRSLogger logger = new OpenMRSLogger();

    private OpenmrsAndroid() {
    }

    /**
     * Initialize sdk.
     *
     * @param applicationContext the application context
     */
    public static void initializeSdk(Context applicationContext) {
        if (instance == null) {
            synchronized (OpenmrsAndroid.class) {
                if (instance == null) {
                    instance = applicationContext;
                    if (externalDirectoryPath == null) {
                        externalDirectoryPath = applicationContext.getExternalFilesDir(null).toString();
                    }
                    System.out.println(instance.toString());
                }
            }
        }
    }

    /**
     * Gets instance.
     *
     * @return the instance
     */
    public static @Nullable
    Context getInstance() {
        return instance;
    }

    /**
     * Gets open mrs dir.
     *
     * @return the open mrs dir
     */
    public static String getOpenMRSDir() {
        return externalDirectoryPath + OPENMRS_DIR_PATH;
    }

    /**
     * Gets open mrs logger.
     *
     * @return the open mrs logger
     */
    public static OpenMRSLogger getOpenMRSLogger() {
        return logger;
    }

    /**
     * Delete secret key.
     */
    public static void deleteSecretKey() {
        secretKey = null;
    }

    /**
     * Gets open mrs shared preferences.
     *
     * @return the open mrs shared preferences
     */
    public static SharedPreferences getOpenMRSSharedPreferences() {
        return instance.getSharedPreferences(ApplicationConstants.OpenMRSSharedPreferenceNames.SHARED_PREFERENCES_NAME,
                MODE_PRIVATE);
    }

    /**
     * Remembers the locale configuration a /session response carried, so the language picker
     * works offline and between the moments the app actually talks to the server.
     * <p>
     * The allowed list is server configuration, so it's kept even from an unauthenticated
     * response; the locale is only kept from an authenticated one, since only then does it
     * reflect the user's own default. A response without the fields (an older server) leaves
     * what was cached before untouched.
     *
     * @param session the session returned by the server
     */
    public static void cacheSessionLocales(@Nullable Session session) {
        if (session == null) {
            return;
        }
        SharedPreferences.Editor editor = getOpenMRSSharedPreferences().edit();
        List<String> allowedLocales = session.getAllowedLocales();
        if (allowedLocales != null) {
            StringBuilder joined = new StringBuilder();
            for (String locale : allowedLocales) {
                // Locale tags never contain commas, so a comma is a safe separator.
                if (locale == null || locale.trim().isEmpty()) {
                    continue;
                }
                if (joined.length() > 0) {
                    joined.append(',');
                }
                joined.append(locale.trim());
            }
            editor.putString(ApplicationConstants.OpenMRSlanguage.KEY_SERVER_ALLOWED_LOCALES, joined.toString());
        }
        if (session.isAuthenticated() && session.getLocale() != null) {
            editor.putString(ApplicationConstants.OpenMRSlanguage.KEY_SERVER_LOCALE, session.getLocale());
        }
        editor.apply();
    }

    /**
     * Gets the locales the server allows, as last reported by it.
     *
     * @return the allowed locale tags, exactly as the server sent them; empty if never received
     */
    public static List<String> getServerAllowedLocales() {
        String joined = getOpenMRSSharedPreferences()
                .getString(ApplicationConstants.OpenMRSlanguage.KEY_SERVER_ALLOWED_LOCALES, "");
        List<String> locales = new ArrayList<>();
        for (String locale : joined.split(",")) {
            if (!locale.isEmpty()) {
                locales.add(locale);
            }
        }
        return locales;
    }

    /**
     * Gets the server's locale for the logged-in user, as last reported by it.
     *
     * @return the locale tag, or null if never received
     */
    public static @Nullable
    String getServerLocale() {
        return getOpenMRSSharedPreferences()
                .getString(ApplicationConstants.OpenMRSlanguage.KEY_SERVER_LOCALE, null);
    }

    /**
     * Sets password and hashed password.
     *
     * @param password the password
     */
    public static void setPasswordAndHashedPassword(String password) {
        SharedPreferences.Editor editor = getOpenMRSSharedPreferences().edit();
        String salt = BCrypt.gensalt(ApplicationConstants.DEFAULT_BCRYPT_ROUND);
        String hashedPassword = BCrypt.hashpw(password, salt);
        editor.putString(ApplicationConstants.UserKeys.PASSWORD, password);
        editor.putString(ApplicationConstants.UserKeys.HASHED_PASSWORD, hashedPassword);
        editor.apply();
    }

    /**
     * Gets first time.
     *
     * @return the first time
     */
    public static Boolean getFirstTime() {
        SharedPreferences prefs = getOpenMRSSharedPreferences();
        return prefs.getBoolean(ApplicationConstants.UserKeys.FIRST_TIME, ApplicationConstants.FIRST);
    }

    /**
     * Sets user first time.
     *
     * @param firstLogin the first login
     */
    public static void setUserFirstTime(boolean firstLogin) {
        SharedPreferences.Editor editor = OpenmrsAndroid.getOpenMRSSharedPreferences().edit();
        editor.putBoolean(ApplicationConstants.UserKeys.FIRST_TIME, firstLogin);
        editor.apply();
    }

    /**
     * Gets password.
     *
     * @return the password
     */
    public static String getPassword() {
        SharedPreferences prefs = getOpenMRSSharedPreferences();
        return prefs.getString(ApplicationConstants.UserKeys.PASSWORD, ApplicationConstants.EMPTY_STRING);
    }

    /**
     * Sets password.
     *
     * @param password the password
     */
    public static void setPassword(String password) {
        SharedPreferences.Editor editor = getOpenMRSSharedPreferences().edit();
        editor.putString(ApplicationConstants.UserKeys.PASSWORD, password);
        editor.apply();
    }

    /**
     * Gets hashed password.
     *
     * @return the hashed password
     */
    public static String getHashedPassword() {
        SharedPreferences prefs = getOpenMRSSharedPreferences();
        return prefs.getString(ApplicationConstants.UserKeys.HASHED_PASSWORD, ApplicationConstants.EMPTY_STRING);
    }

    /**
     * Sets hashed password.
     *
     * @param hashedPassword the hashed password
     */
    public static void setHashedPassword(String hashedPassword) {
        SharedPreferences.Editor editor = getOpenMRSSharedPreferences().edit();
        editor.putString(ApplicationConstants.UserKeys.HASHED_PASSWORD, hashedPassword);
        editor.apply();
    }

    /**
     * Gets username.
     *
     * @return the username
     */
    public static String getUsername() {
        SharedPreferences prefs = getOpenMRSSharedPreferences();
        return prefs.getString(ApplicationConstants.UserKeys.USER_NAME, ApplicationConstants.EMPTY_STRING);
    }

    /**
     * Sets username.
     *
     * @param username the username
     */
    public static void setUsername(String username) {
        SharedPreferences.Editor editor = getOpenMRSSharedPreferences().edit();
        editor.putString(ApplicationConstants.UserKeys.USER_NAME, username);
        editor.apply();
    }

    /**
     * Is user logged online boolean.
     *
     * @return the boolean
     */
    public static boolean isUserLoggedOnline() {
        SharedPreferences prefs = getOpenMRSSharedPreferences();
        return prefs.getBoolean(ApplicationConstants.UserKeys.LOGIN, false);
    }

    /**
     * Sets user logged online.
     *
     * @param firstLogin the first login
     */
    public static void setUserLoggedOnline(boolean firstLogin) {
        SharedPreferences.Editor editor = getOpenMRSSharedPreferences().edit();
        editor.putBoolean(ApplicationConstants.UserKeys.LOGIN, firstLogin);
        editor.apply();
    }

    /**
     * Gets server url.
     *
     * @return the server url
     */
    public static String getServerUrl() {
        SharedPreferences prefs = getOpenMRSSharedPreferences();
        return prefs.getString(ApplicationConstants.SERVER_URL, ApplicationConstants.DEFAULT_OPEN_MRS_URL);
    }

    /**
     * Sets s
     *
     * erver url.
     *1
     * @param serverUrl the server url
     */
    public static void setServerUrl(String serverUrl) {
        SharedPreferences.Editor editor = getOpenMRSSharedPreferences().edit();
        editor.putString(ApplicationConstants.SERVER_URL, serverUrl);
        editor.apply();
    }

    /**
     * Gets last login server url.
     *
     * @return the last login server url
     */
    public static String getLastLoginServerUrl() {
        SharedPreferences prefs = getOpenMRSSharedPreferences();
        return prefs.getString(ApplicationConstants.LAST_LOGIN_SERVER_URL, ApplicationConstants.EMPTY_STRING);
    }

    /**
     * Sets last login server url.
     *
     * @param url the url
     */
    public static void setLastLoginServerUrl(String url) {
        SharedPreferences.Editor editor = getOpenMRSSharedPreferences().edit();
        editor.putString(ApplicationConstants.LAST_LOGIN_SERVER_URL, url);
        editor.apply();
    }

    /**
     * Gets session token.
     *
     * @return the session token
     */
    public static String getSessionToken() {
        SharedPreferences prefs = getOpenMRSSharedPreferences();
        return prefs.getString(ApplicationConstants.SESSION_TOKEN, ApplicationConstants.EMPTY_STRING);
    }

    /**
     * Sets session token.
     *
     * @param serverUrl the server url
     */
    public static void setSessionToken(String serverUrl) {
        SharedPreferences.Editor editor = getOpenMRSSharedPreferences().edit();
        editor.putString(ApplicationConstants.SESSION_TOKEN, serverUrl);
        editor.apply();
    }

    /**
     * Gets last session token.
     *
     * @return the last session token
     */
    public static String getLastSessionToken() {
        SharedPreferences prefs = getOpenMRSSharedPreferences();
        return prefs.getString(ApplicationConstants.LAST_SESSION_TOKEN, ApplicationConstants.EMPTY_STRING);
    }

    /**
     * Gets location.
     *
     * @return the location
     */
    public static String getLocation() {
        SharedPreferences prefs = getOpenMRSSharedPreferences();
        return prefs.getString(ApplicationConstants.LOCATION, ApplicationConstants.EMPTY_STRING);
    }

    /**
     * Sets location.
     *
     * @param location the location
     */
    public static void setLocation(String location) {
        SharedPreferences.Editor editor = getOpenMRSSharedPreferences().edit();
        editor.putString(ApplicationConstants.LOCATION, location);
        editor.apply();
    }

    /**
     * Gets visit type uuid.
     *
     * @return the visit type uuid
     */
    public static String getVisitTypeUUID() {
        SharedPreferences prefs = getOpenMRSSharedPreferences();
        return prefs.getString(ApplicationConstants.VISIT_TYPE_UUID, ApplicationConstants.EMPTY_STRING);
    }

    /**
     * Sets visit type uuid.
     *
     * @param visitTypeUUID the visit type uuid
     */
    public static void setVisitTypeUUID(String visitTypeUUID) {
        SharedPreferences.Editor editor = getOpenMRSSharedPreferences().edit();
        editor.putString(ApplicationConstants.VISIT_TYPE_UUID, visitTypeUUID);
        editor.apply();
    }

    private void createSecretKey() {
        secretKey = BCrypt.hashpw(OpenmrsAndroid.getUsername() + ApplicationConstants.DB_PASSWORD_LITERAL_PEPPER + OpenmrsAndroid.getPassword(), ApplicationConstants.DB_PASSWORD_BCRYPT_PEPPER);
    }

    /**
     * Gets secret key.
     *
     * @return the secret key
     */
    public String getSecretKey() {
        if (secretKey == null) {
            createSecretKey();
        }
        return secretKey;
    }

    /**
     * Whether the user has manually paused sync (via the toolbar/login sync toggle). Held
     * in-memory only, for the current process lifetime - deliberately NOT persisted to disk.
     *
     * This used to be a SharedPreferences boolean ("sync") that several unrelated code paths
     * wrote to for different reasons (this manual toggle, a connectivity-restored auto-reset, and
     * a "pause sync" side effect of backing out of the matching-patients screen) - any of which
     * could leave a stale "false" saved across app restarts (or app updates) with no way for the
     * user to know why sync had silently stopped working, since nothing but the toggle itself
     * ever explains its own state. Scoping it to the process instead means a stale value never
     * outlives the session that set it.
     *
     * Starts enabled only for a device's first login, which needs the server. Otherwise offline is
     * the default: the app sets it off at launch and after login, and it is switched back off once
     * a sync has uploaded everything (the app's SyncModeManager).
     */
    private static volatile boolean syncEnabled = true;

    /**
     * Gets sync state.
     *
     * @return the sync state
     */
    public static boolean getSyncState() {
        return syncEnabled;
    }


    /**
     * Sets sync state.
     *
     * @param enabled the enabled
     */
    public static void setSyncState(boolean enabled) {
        syncEnabled = enabled;
    }

    /**
     * Sets current user information.
     *
     * @param userInformation the user information
     */
    public static void setCurrentUserInformation(Map<String, String> userInformation) {
        SharedPreferences.Editor editor = getOpenMRSSharedPreferences().edit();
        for (Map.Entry<String, String> entry : userInformation.entrySet()) {
            editor.putString(entry.getKey(), entry.getValue());
        }
        editor.apply();
    }

    /**
     * Gets current logged in user info.
     *
     * @return the current logged in user info
     */
    public Map<String, String> getCurrentLoggedInUserInfo() {
        SharedPreferences prefs = getOpenMRSSharedPreferences();
        Map<String, String> infoMap = new HashMap<>();
        infoMap.put(ApplicationConstants.UserKeys.USER_PERSON_NAME, prefs.getString(ApplicationConstants.UserKeys.USER_PERSON_NAME, ApplicationConstants.EMPTY_STRING));
        infoMap.put(ApplicationConstants.UserKeys.USER_UUID, prefs.getString(ApplicationConstants.UserKeys.USER_UUID, ApplicationConstants.EMPTY_STRING));
        return infoMap;
    }

    /**
     * Gets the encounter providers a form encounter is saved with: the logged in user's provider
     * in the encounter role chosen at login, as the web form engine does. Kept across logout like
     * the user's name, so offline-filled forms get them too.
     *
     * @return the encounter providers, or an empty list when the user has no linked provider
     */
    public static List<EncounterProviderCreate> getCurrentEncounterProviders() {
        SharedPreferences prefs = getOpenMRSSharedPreferences();
        String providerUuid = prefs.getString(ApplicationConstants.UserKeys.CURRENT_PROVIDER_UUID, ApplicationConstants.EMPTY_STRING);
        if (providerUuid.isEmpty()) return new ArrayList<>();
        String roleUuid = prefs.getString(ApplicationConstants.UserKeys.ENCOUNTER_ROLE_UUID, ApplicationConstants.EMPTY_STRING);
        if (roleUuid.isEmpty()) roleUuid = ApplicationConstants.UNKNOWN_ENCOUNTER_ROLE_UUID;
        List<EncounterProviderCreate> providers = new ArrayList<>();
        providers.add(new EncounterProviderCreate(providerUuid, roleUuid));
        return providers;
    }

    /**
     * Sets the logged in user's provider - see {@link #getCurrentEncounterProviders()}.
     *
     * @param providerUuid the provider UUID, or null when the user has no linked provider
     */
    public static void setCurrentProviderUuid(String providerUuid) {
        getOpenMRSSharedPreferences().edit()
                .putString(ApplicationConstants.UserKeys.CURRENT_PROVIDER_UUID, providerUuid == null ? ApplicationConstants.EMPTY_STRING : providerUuid)
                .apply();
    }

    /**
     * Sets the encounter role form encounters are saved with - see {@link #getCurrentEncounterProviders()}.
     *
     * @param roleUuid the encounter role UUID
     */
    public static void setEncounterRoleUuid(String roleUuid) {
        getOpenMRSSharedPreferences().edit()
                .putString(ApplicationConstants.UserKeys.ENCOUNTER_ROLE_UUID, roleUuid)
                .apply();
    }

    /**
     * Gets the logged in user's person name (e.g. for a form's "CHW Name"), as of their last
     * online login - kept across logout so it is still there after an offline login.
     *
     * @return the person name, or an empty string when not known
     */
    public static String getCurrentUserPersonName() {
        return getOpenMRSSharedPreferences().getString(ApplicationConstants.UserKeys.USER_PERSON_NAME, ApplicationConstants.EMPTY_STRING);
    }

    /**
     * Sets the logged in user's person name - see {@link #getCurrentUserPersonName()}.
     *
     * @param name the person name
     */
    public static void setCurrentUserPersonName(String name) {
        getOpenMRSSharedPreferences().edit()
                .putString(ApplicationConstants.UserKeys.USER_PERSON_NAME, name == null ? ApplicationConstants.EMPTY_STRING : name)
                .apply();
    }

    /**
     * Clear current logged in user info.
     */
    public static void clearCurrentLoggedInUserInfo() {
        SharedPreferences prefs = OpenmrsAndroid.getOpenMRSSharedPreferences();
        SharedPreferences.Editor editor = prefs.edit();
        editor.remove(ApplicationConstants.UserKeys.USER_PERSON_NAME);
        editor.remove(ApplicationConstants.UserKeys.USER_UUID);
        editor.apply();
    }

    /**
     * Clear user preferences data.
     */
    public static void clearUserPreferencesData() {
        SharedPreferences prefs = OpenmrsAndroid.getOpenMRSSharedPreferences();
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(ApplicationConstants.LAST_SESSION_TOKEN,
                prefs.getString(ApplicationConstants.SESSION_TOKEN, ApplicationConstants.EMPTY_STRING));
        editor.remove(ApplicationConstants.SESSION_TOKEN);
        editor.remove(ApplicationConstants.AUTHORIZATION_TOKEN);
        // The current user's info is kept: only this same user can log back in offline (anyone
        // else has to log in online, which replaces it), and forms fill "CHW Name" from it.
        editor.remove(ApplicationConstants.UserKeys.PASSWORD);
        OpenmrsAndroid.deleteSecretKey();
        editor.apply();
        // The login screen starts offline too once someone has logged in online on this device,
        // so that user logs back in offline; only a device's first login needs the server.
        // (Anyone else who can't log in offline falls back to online login - LoginViewModel.)
        syncEnabled = !isUserLoggedOnline();
    }

    /**
     * Sets default form load id.
     *
     * @param xFormName the x form name
     * @param xFormID   the x form id
     */
    public void setDefaultFormLoadID(String xFormName, String xFormID) {
        SharedPreferences.Editor editor = getOpenMRSSharedPreferences().edit();
        editor.putString(xFormName, xFormID);
        editor.apply();
    }

    /**
     * Gets default form load id.
     *
     * @param xFormName the x form name
     * @return the default form load id
     */
    public String getDefaultFormLoadID(String xFormName) {
        SharedPreferences prefs = getOpenMRSSharedPreferences();
        return prefs.getString(xFormName, ApplicationConstants.EMPTY_STRING);
    }

    /**
     * Gets authorization token.
     *
     * @return the authorization token
     */
    public String getAuthorizationToken() {
        SharedPreferences prefs = getOpenMRSSharedPreferences();
        return prefs.getString(ApplicationConstants.AUTHORIZATION_TOKEN, ApplicationConstants.EMPTY_STRING);
    }

    /**
     * Sets authorization token.
     *
     * @param authorization the authorization
     */
    public void setAuthorizationToken(String authorization) {
        SharedPreferences.Editor editor = getOpenMRSSharedPreferences().edit();
        editor.putString(ApplicationConstants.AUTHORIZATION_TOKEN, authorization);
        editor.apply();
    }



}
