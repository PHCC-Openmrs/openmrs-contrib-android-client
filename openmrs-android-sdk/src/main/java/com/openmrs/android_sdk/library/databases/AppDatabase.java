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

package com.openmrs.android_sdk.library.databases;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.openmrs.android_sdk.library.dao.AllergyRoomDAO;
import com.openmrs.android_sdk.library.dao.AppointmentRoomDAO;
import com.openmrs.android_sdk.library.dao.ConceptRoomDAO;
import com.openmrs.android_sdk.library.dao.DrugRoomDAO;
import com.openmrs.android_sdk.library.dao.EncounterCreateRoomDAO;
import com.openmrs.android_sdk.library.dao.EncounterRoomDAO;
import com.openmrs.android_sdk.library.dao.EncounterTypeRoomDAO;
import com.openmrs.android_sdk.library.dao.FormResourceDAO;
import com.openmrs.android_sdk.library.dao.LocationRoomDAO;
import com.openmrs.android_sdk.library.dao.ObservationRoomDAO;
import com.openmrs.android_sdk.library.dao.OrderRoomDAO;
import com.openmrs.android_sdk.library.dao.PatientRoomDAO;
import com.openmrs.android_sdk.library.dao.PrivilegeCacheRoomDAO;
import com.openmrs.android_sdk.library.dao.ProgramRoomDAO;
import com.openmrs.android_sdk.library.dao.ProviderRoomDAO;
import com.openmrs.android_sdk.library.dao.VisitRoomDAO;
import com.openmrs.android_sdk.library.databases.entities.AllergyEntity;
import com.openmrs.android_sdk.library.databases.entities.AppointmentEntity;
import com.openmrs.android_sdk.library.databases.entities.ConceptEntity;
import com.openmrs.android_sdk.library.databases.entities.DrugEntity;
import com.openmrs.android_sdk.library.databases.entities.EncounterEntity;
import com.openmrs.android_sdk.library.databases.entities.FormResourceEntity;
import com.openmrs.android_sdk.library.databases.entities.LocationEntity;
import com.openmrs.android_sdk.library.databases.entities.ObservationEntity;
import com.openmrs.android_sdk.library.databases.entities.OrderEntity;
import com.openmrs.android_sdk.library.databases.entities.PatientEntity;
import com.openmrs.android_sdk.library.databases.entities.PrivilegeCacheEntity;
import com.openmrs.android_sdk.library.databases.entities.ProgramEntity;
import com.openmrs.android_sdk.library.databases.entities.StandaloneEncounterEntity;
import com.openmrs.android_sdk.library.databases.entities.StandaloneObservationEntity;
import com.openmrs.android_sdk.library.databases.entities.VisitEntity;
import com.openmrs.android_sdk.library.models.EncounterType;
import com.openmrs.android_sdk.library.models.Encountercreate;
import com.openmrs.android_sdk.library.models.Provider;
import com.openmrs.android_sdk.utilities.ApplicationConstants;

/**
 * The type App database.
 */
@Database(entities = {ConceptEntity.class,
        EncounterEntity.class,
        StandaloneEncounterEntity.class,
        LocationEntity.class,
        ObservationEntity.class,
        StandaloneObservationEntity.class,
        PatientEntity.class,
        VisitEntity.class,
        Provider.class,
        FormResourceEntity.class,
        EncounterType.class,
        Encountercreate.class,
        AllergyEntity.class,
        AppointmentEntity.class,
        OrderEntity.class,
        ProgramEntity.class,
        DrugEntity.class,
        PrivilegeCacheEntity.class},
        version = 8)
@TypeConverters({StringListConverter.class, WorkflowConverter.class})
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase INSTANCE;

    /**
     * Adds the privilege_cache_table only, so existing installs upgrading to version 5 keep
     * their already-synced patients/visits/concepts/etc. instead of falling back to a full wipe.
     */
    private static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `privilege_cache_table` (" +
                    "`id` INTEGER NOT NULL, " +
                    "`privilege_names` TEXT NOT NULL, " +
                    "`role_names` TEXT NOT NULL, " +
                    "`is_super_user` INTEGER NOT NULL, " +
                    "`cached_at` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`id`))");
        }
    };

    /**
     * Replaces the appointments table with the schema for the real O3 `appointments` module
     * (flat service/location/status fields) instead of the legacy block/time-slot model it used
     * to hold. No shipped UI ever populated that table, so dropping just this table (rather than
     * writing a column migration, or letting fallbackToDestructiveMigration wipe every table) is
     * safe and keeps existing installs' synced patients/visits/etc. intact.
     */
    private static final Migration MIGRATION_5_6 = new Migration(5, 6) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("DROP TABLE IF EXISTS `appointments`");
            database.execSQL("CREATE TABLE IF NOT EXISTS `appointments` (" +
                    "`uuid` TEXT NOT NULL, " +
                    "`patient_uuid` TEXT, " +
                    "`appointment_number` TEXT, " +
                    "`service_name` TEXT, " +
                    "`location_name` TEXT, " +
                    "`start_date_time` INTEGER, " +
                    "`end_date_time` INTEGER, " +
                    "`appointment_kind` TEXT, " +
                    "`status` TEXT, " +
                    "`comments` TEXT, " +
                    "PRIMARY KEY(`uuid`))");
        }
    };

    /**
     * Replaces the orders table with the schema matching the verified real `order` list
     * representation (dropping DrugOrder-only columns like frequency/drug/dose that were never
     * populated correctly, and fixing the careSetting/commentToFulfiller field mismatches). No
     * shipped UI ever populated this table, so dropping just this table is safe.
     */
    private static final Migration MIGRATION_6_7 = new Migration(6, 7) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("DROP TABLE IF EXISTS `orders`");
            database.execSQL("CREATE TABLE IF NOT EXISTS `orders` (" +
                    "`uuid` TEXT NOT NULL, " +
                    "`display` TEXT, " +
                    "`encounterUuid` TEXT NOT NULL, " +
                    "`instructions` TEXT NOT NULL, " +
                    "`careSettingName` TEXT NOT NULL, " +
                    "`urgency` TEXT NOT NULL, " +
                    "`dateStopped` TEXT NOT NULL, " +
                    "`dateActivated` TEXT NOT NULL, " +
                    "`orderNumber` TEXT NOT NULL, " +
                    "`accessionNumber` TEXT NOT NULL, " +
                    "`patientUuid` TEXT NOT NULL, " +
                    "`conceptUuid` TEXT NOT NULL, " +
                    "`conceptDisplay` TEXT NOT NULL, " +
                    "`action` TEXT NOT NULL, " +
                    "`scheduledDate` TEXT NOT NULL, " +
                    "`autoExpireDate` TEXT NOT NULL, " +
                    "`orderReason` TEXT NOT NULL, " +
                    "`fulfillerStatus` TEXT NOT NULL, " +
                    "`commentToFulfiller` TEXT NOT NULL, " +
                    "`orderer_uuid` TEXT NOT NULL, " +
                    "`orderer_display` TEXT NOT NULL, " +
                    "`orderType_uuid` TEXT NOT NULL, " +
                    "`orderType_display` TEXT NOT NULL, " +
                    "PRIMARY KEY(`uuid`))");
        }
    };

    /**
     * Gets database.
     *
     * @param context the context
     * @return the database
     */
//TODO remove this public and refactor the packages of classes to incorporate allDAOs under this repository
    public static AppDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                            AppDatabase.class, ApplicationConstants.DB_NAME)
                            .allowMainThreadQueries()
                            .addMigrations(MIGRATION_4_5, MIGRATION_5_6)
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }

    /**
     * Location room dao location room dao.
     *
     * @return the location room dao
     */
    public abstract LocationRoomDAO locationRoomDAO();

    /**
     * Visit room dao visit room dao.
     *
     * @return the visit room dao
     */
    public abstract VisitRoomDAO visitRoomDAO();

    /**
     * Patient room dao patient room dao.
     *
     * @return the patient room dao
     */
    public abstract PatientRoomDAO patientRoomDAO();

    /**
     * Observation room dao observation room dao.
     *
     * @return the observation room dao
     */
    public abstract ObservationRoomDAO observationRoomDAO();

    /**
     * Encounter room dao encounter room dao.
     *
     * @return the encounter room dao
     */
    public abstract EncounterRoomDAO encounterRoomDAO();

    /**
     * Concept room dao concept room dao.
     *
     * @return the concept room dao
     */
    public abstract ConceptRoomDAO conceptRoomDAO();

    /**
     * Provider room dao provider room dao.
     *
     * @return the provider room dao
     */
    public abstract ProviderRoomDAO providerRoomDAO();

    /**
     * Form resource dao form resource dao.
     *
     * @return the form resource dao
     */
    public abstract FormResourceDAO formResourceDAO();

    /**
     * Encounter type room dao encounter type room dao.
     *
     * @return the encounter type room dao
     */
    public abstract EncounterTypeRoomDAO encounterTypeRoomDAO();

    /**
     * Encounter create room dao encounter create room dao.
     *
     * @return the encounter create room dao
     */
    public abstract EncounterCreateRoomDAO encounterCreateRoomDAO();

    /**
     * Allergy room dao allergy room dao.
     *
     * @return the allergy room dao
     */
    public abstract AllergyRoomDAO allergyRoomDAO();

    /**
     * Appointment Room DAO
     *
     * @return the Appointment Room DAO
     */
    public abstract AppointmentRoomDAO appointmentRoomDAO();

    /**
     * Order Room DAO
     *
     * @return the Order Room DAO
     */
    public abstract OrderRoomDAO orderRoomDAO();

    /**
     * Program Room DAO
     *
     * @return the Program Room DAO
     */
    public abstract ProgramRoomDAO programRoomDAO();

     /** Drug Room DAO
     *
     * @return the Drug Room DAO
     */
    public abstract DrugRoomDAO drugRoomDAO();

    /**
     * Privilege cache room dao.
     *
     * @return the privilege cache room dao
     */
    public abstract PrivilegeCacheRoomDAO privilegeCacheRoomDAO();
}
