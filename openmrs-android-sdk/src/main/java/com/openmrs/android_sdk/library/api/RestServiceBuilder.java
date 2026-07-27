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

import java.util.concurrent.TimeUnit;
import javax.inject.Singleton;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import android.util.Base64;

import com.chuckerteam.chucker.api.ChuckerInterceptor;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.openmrs.android_sdk.library.OpenmrsAndroid;
import com.openmrs.android_sdk.library.models.Observation;
import com.openmrs.android_sdk.library.models.Resource;
import com.openmrs.android_sdk.utilities.ApplicationConstants;
import com.openmrs.android_sdk.utilities.ObservationDeserializer;
import com.openmrs.android_sdk.utilities.ResourceSerializer;

/**
 * The type Rest service builder.
 */
@Singleton
public class RestServiceBuilder {
    private static String API_BASE_URL = OpenmrsAndroid.getServerUrl() + ApplicationConstants.API.REST_ENDPOINT;

    // Immutable base client (timeouts + connection pool/dispatcher only, no interceptors).
    // Each createService() call derives its OWN client via newBuilder() (which keeps the same
    // connection pool/dispatcher) with EXACTLY the one auth interceptor that call needs, instead
    // of mutating a single shared Builder. Mutating a shared static Builder meant every call ever
    // made (Hilt singleton resolution, login attempts, the periodic AuthenticateCheckService
    // check, ...) permanently appended another interceptor to it, so a request could pick up
    // stale/wrong credentials baked in by some earlier, unrelated call.
    private static final OkHttpClient BASE_HTTP_CLIENT = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build();

    private static Retrofit.Builder builder;

    static {
        builder =
                new Retrofit.Builder()
                        .baseUrl(API_BASE_URL)
                        .addConverterFactory(buildGsonConverter());
    }

    /**
     * Create service s.
     *
     * @param <S>          the type parameter
     * @param serviceClass the service class
     * @param username     the username
     * @param password     the password
     * @return the s
     */
    public static <S> S createService(Class<S> serviceClass, String username, String password) {
        // Prefer the credentials explicitly passed by the caller (e.g. LoginRepository passes
        // the just-typed username/password, which haven't been persisted to SharedPreferences
        // yet at this point - see LoginViewModel.login()). Only fall back to a live
        // OpenmrsAndroid read when no explicit credentials were given, which covers the
        // Dagger/Hilt @Singleton path (createService(Class) below): that instance can be built
        // before login (before any credentials exist), so it must re-check SharedPreferences at
        // request time rather than have a fixed (possibly empty) value baked in at construction.
        final String explicitUsername = username;
        final String explicitPassword = password;

        OkHttpClient client = BASE_HTTP_CLIENT.newBuilder()
                .addNetworkInterceptor(chain -> {
                    Request original = chain.request();

                    Request.Builder requestBuilder = original.newBuilder()
                            .header("Accept", "application/json")
                            .method(original.method(), original.body());

                    String currentUsername = (explicitUsername != null && !explicitUsername.isEmpty())
                            ? explicitUsername : OpenmrsAndroid.getUsername();
                    String currentPassword = (explicitPassword != null && !explicitPassword.isEmpty())
                            ? explicitPassword : OpenmrsAndroid.getPassword();

                    if (currentUsername != null && !currentUsername.isEmpty()
                            && currentPassword != null && !currentPassword.isEmpty()) {
                        String credentials = currentUsername + ":" + currentPassword;
                        String basic = "Basic " + Base64.encodeToString(credentials.getBytes(), Base64.NO_WRAP);
                        requestBuilder.header("Authorization", basic);
                    }

                    return chain.proceed(requestBuilder.build());
                })
                .addInterceptor(new ChuckerInterceptor(OpenmrsAndroid.getInstance()))
                .build();

        Retrofit retrofit = builder.client(client).build();
        return retrofit.create(serviceClass);
    }

    /**
     * Create service s.
     *
     * @param <S>          the type parameter
     * @param serviceClass the service class
     * @return the s
     */
    public static <S> S createService(Class<S> serviceClass) {
        String username = OpenmrsAndroid.getUsername();
        String password = OpenmrsAndroid.getPassword();
        return createService(serviceClass, username, password);
    }

    private static GsonConverterFactory buildGsonConverter() {
        GsonBuilder gsonBuilder = new GsonBuilder();
        Gson myGson = gsonBuilder
                .excludeFieldsWithoutExposeAnnotation()
                .registerTypeHierarchyAdapter(Resource.class, new ResourceSerializer())
                .registerTypeHierarchyAdapter(Observation.class, new ObservationDeserializer())
                .create();

        return GsonConverterFactory.create(myGson);
    }

    /**
     * Create service for patient identifier s.
     *
     * @param <S>   the type parameter
     * @param clazz the clazz
     * @return the s
     */
    public static <S> S createServiceForPatientIdentifier(Class<S> clazz) {
        return new Retrofit.Builder()
                .baseUrl(OpenmrsAndroid.getServerUrl() + '/')
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(clazz);
    }

    /**
     * Change base url.
     *
     * @param newServerUrl the new server url
     */
    public static void changeBaseUrl(String newServerUrl) {
        API_BASE_URL = newServerUrl + ApplicationConstants.API.REST_ENDPOINT;

        builder = new Retrofit.Builder()
                .baseUrl(API_BASE_URL)
                .addConverterFactory(buildGsonConverter());
    }
}
