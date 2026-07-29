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
package com.openmrs.android_sdk.library.databases

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.openmrs.android_sdk.library.OpenmrsAndroid
import com.openmrs.android_sdk.library.api.repository.FormRepository
import com.openmrs.android_sdk.library.dao.EncounterDAO
import com.openmrs.android_sdk.library.dao.EncounterRoomDAO
import com.openmrs.android_sdk.library.dao.ObservationDAO
import com.openmrs.android_sdk.library.dao.PatientDAO
import com.openmrs.android_sdk.library.databases.entities.AllergyEntity
import com.openmrs.android_sdk.library.databases.entities.EncounterEntity
import com.openmrs.android_sdk.library.databases.entities.ObservationEntity
import com.openmrs.android_sdk.library.databases.entities.StandaloneEncounterEntity
import com.openmrs.android_sdk.library.databases.entities.LocationEntity
import com.openmrs.android_sdk.library.databases.entities.VisitEntity
import com.openmrs.android_sdk.library.databases.entities.PatientEntity
import com.openmrs.android_sdk.library.databases.entities.StandaloneObservationEntity
import com.openmrs.android_sdk.library.databases.entities.AppointmentEntity
import com.openmrs.android_sdk.library.databases.entities.DosageFormEntity
import com.openmrs.android_sdk.library.databases.entities.DrugConceptEntity
import com.openmrs.android_sdk.library.databases.entities.DrugEntity
import com.openmrs.android_sdk.library.databases.entities.OrderEntity
import com.openmrs.android_sdk.library.databases.entities.ProgramEntity
import com.openmrs.android_sdk.library.di.entrypoints.RepositoryEntryPoint
import com.openmrs.android_sdk.library.models.Allergen
import com.openmrs.android_sdk.library.models.Allergy
import com.openmrs.android_sdk.library.models.Encounter
import com.openmrs.android_sdk.library.models.EncounterType
import com.openmrs.android_sdk.library.models.Observation
import com.openmrs.android_sdk.library.models.Patient
import com.openmrs.android_sdk.library.models.PatientIdentifier
import com.openmrs.android_sdk.library.models.PersonAddress
import com.openmrs.android_sdk.library.models.PersonName
import com.openmrs.android_sdk.library.models.Resource
import com.openmrs.android_sdk.library.models.Visit
import com.openmrs.android_sdk.library.models.VisitType
import com.openmrs.android_sdk.library.models.Appointment
import com.openmrs.android_sdk.library.models.AppointmentLocationInfo
import com.openmrs.android_sdk.library.models.AppointmentServiceInfo
import com.openmrs.android_sdk.library.models.Person
import com.openmrs.android_sdk.library.models.ConceptClass
import com.openmrs.android_sdk.library.models.Drug
import com.openmrs.android_sdk.library.models.OrderGet
import com.openmrs.android_sdk.library.models.ProgramGet
import com.openmrs.android_sdk.utilities.ApplicationConstants
import com.openmrs.android_sdk.utilities.DateUtils
import com.openmrs.android_sdk.utilities.DateUtils.convertTime
import com.openmrs.android_sdk.utilities.execute
import dagger.hilt.android.EntryPointAccessors
import rx.Observable
import rx.schedulers.Schedulers
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.Callable

object AppDatabaseHelper {

    @JvmStatic
    fun convert(obs: Observation, encounterID: Long): ObservationEntity {
        val observationEntity = ObservationEntity()
        observationEntity.id = obs.id
        observationEntity.uuid = obs.uuid
        observationEntity.display = obs.display
        observationEntity.encounterKeyID = encounterID
        observationEntity.displayValue = obs.displayValue
        observationEntity.diagnosisOrder = obs.diagnosisOrder
        observationEntity.diagnosisList = obs.diagnosisList
        observationEntity.diagnosisCertainty = obs.diagnosisCertainty
        observationEntity.diagnosisNote = obs.diagnosisNote
        observationEntity.conceptuuid = obs.concept?.uuid
        observationEntity.patientUuid = obs.person?.uuid
        observationEntity.obsDateTime = obs.obsDatetime
        return observationEntity
    }

    @JvmStatic
    fun convert(obs: ObservationEntity): Observation {
        val encounterRoomDAO: EncounterRoomDAO = AppDatabase.getDatabase(
            OpenmrsAndroid.getInstance()!!.applicationContext
        ).encounterRoomDAO()

        val observation = Observation()

        val encounter = Encounter()
        encounter.uuid = encounterRoomDAO.getEncounterUuidByID(obs.encounterKeyID).toString()

        val person = Person()
        person.uuid = obs.patientUuid

        val concept = ConceptClass()
        concept.uuid = obs.conceptuuid

        observation.person = person
        observation.value = obs.displayValue
        observation.displayValue = obs.displayValue
        observation.concept = concept
        observation.uuid = obs.uuid
        observation.display = obs.display
        observation.encounter = encounter
        observation.diagnosisOrder = obs.diagnosisOrder
        observation.diagnosisList = obs.diagnosisList
        observation.diagnosisCertainty = obs.diagnosisCertainty
        observation.diagnosisNote = obs.diagnosisNote
        observation.obsDatetime = obs.obsDateTime

        return observation
    }

    @JvmStatic
    fun convert(observationEntityList: List<ObservationEntity>): List<Observation> {
        val observationList: MutableList<Observation> = ArrayList()
        for (entity in observationEntityList) {
            val obs = Observation()
            obs.id = entity.id
            obs.encounterID = entity.encounterKeyID
            obs.uuid = entity.uuid
            obs.display = entity.display
            obs.displayValue = entity.displayValue
            obs.diagnosisOrder = entity.diagnosisOrder
            obs.diagnosisList = entity.diagnosisList
            obs.setDiagnosisCertanity(entity.diagnosisCertainty)
            obs.diagnosisNote = entity.diagnosisNote
            val concept = ConceptClass()
            concept.uuid = entity.conceptuuid
            obs.concept = concept
            observationList.add(obs)
        }
        return observationList
    }

    @JvmStatic
    fun convert(encounter: Encounter, visitID: Long?): EncounterEntity {
        val encounterEntity = EncounterEntity()
        encounterEntity.id = encounter.id
        encounterEntity.display = encounter.display
        encounterEntity.uuid = encounter.uuid
        if (visitID != null) {
            encounterEntity.visitKeyId = visitID.toString()
        }
        encounterEntity.visitUuid = encounter.visit?.uuid ?: encounter.visitID?.toString()
        
        // Prefer the raw date string from the server to avoid local parsing errors
        encounterEntity.encounterDateTime = if (!encounter.encounterDate.isNullOrBlank()) {
            encounter.encounterDate!!
        } else if (encounter.encounterDatetime != null) {
            DateUtils.convertTime(encounter.encounterDatetime!!, DateUtils.OPEN_MRS_REQUEST_FORMAT)
        } else {
            DateUtils.getCurrentDateTime()
        }
        
        encounterEntity.encounterType = encounter.encounterType?.display ?: ""
        encounterEntity.patientUuid = encounter.patient?.uuid ?: encounter.patientUUID
        encounterEntity.formUuid = encounter.formUuid
        if (null == encounter.location) {
            encounterEntity.locationUuid = null
        } else {
            encounterEntity.locationUuid = encounter.location!!.uuid
        }
        if (encounter.encounterProviders.isEmpty()) {
            encounterEntity.encounterProviderUuid = null
        } else {
            encounterEntity.encounterProviderUuid = encounter.encounterProviders[0].uuid
        }
        return encounterEntity
    }

    @JvmStatic
    fun convertToStandalone(encounter: Encounter): StandaloneEncounterEntity {
        return StandaloneEncounterEntity(
            display = encounter.display,
            uuid = encounter.uuid,
            encounterDateTime = encounter.encounterDate,
            encounterType = encounter.encounterType?.uuid,
            patientUuid = encounter.patientUUID,
            formUuid = encounter.formUuid,
            locationUuid = encounter.location?.uuid,
            encounterProviderUuid = if(encounter.encounterProviders.isEmpty()) null
                                    else encounter.encounterProviders[0].uuid,
            visitUuid = encounter.visit?.uuid
        )
    }

    @JvmStatic
    fun convert(entity: EncounterEntity): Encounter {
        val encounter = Encounter()
        if (null != entity.encounterType) {
            encounter.encounterType = EncounterType(entity.encounterType)
        }
        encounter.id = entity.id
        if (null != entity.visitKeyId) {
            encounter.visitID = entity.visitKeyId.toLong()
        }
        encounter.uuid = entity.uuid
        encounter.display = entity.display
        encounter.setEncounterDatetime(entity.encounterDateTime)
        if (entity.visitUuid != null) {
            val visit = Visit()
            visit.uuid = entity.visitUuid
            encounter.visit = visit
        }
        encounter.observations = ObservationDAO().findObservationByEncounterID(entity.id!!)
        encounter.patient = PatientDAO().findPatientByUUID(entity.patientUuid)
        val location: LocationEntity? = try {
            AppDatabase
                    .getDatabase(OpenmrsAndroid.getInstance()?.applicationContext)
                    .locationRoomDAO()
                    .findLocationByUUID(entity.locationUuid)
                    .blockingGet()
        } catch (e: Exception) {
            null
        }
        encounter.location = location
        val formRepository: FormRepository = EntryPointAccessors.fromApplication(
                OpenmrsAndroid.getInstance()!!.applicationContext,
                RepositoryEntryPoint::class.java
        ).provideFormRepository()
        if (entity.formUuid != null) {
            encounter.form = formRepository.fetchFormByUuid(entity.formUuid!!).execute()
        }
        return encounter
    }

    @JvmStatic
    fun convert(visitEntity: VisitEntity): Visit {
        val visit = Visit()
        visit.id = visitEntity.id
        visit.uuid = visitEntity.uuid
        visit.display = visitEntity.display
        val visitType = VisitType()
        visitType.uuid = visitEntity.visitType
        visit.visitType = visitType
        try {
            val locationEntity = AppDatabase
                    .getDatabase(OpenmrsAndroid.getInstance()?.applicationContext)
                    .locationRoomDAO()
                    .findLocationByName(visitEntity.visitPlace)
                    .blockingGet()
            visit.location = locationEntity
        } catch (e: Exception) {
            visit.location = LocationEntity(visitEntity.visitPlace)
        }
        visit.startDatetime = visitEntity.startDate
        visit.stopDatetime = visitEntity.stopDate
        visit.encounters = EncounterDAO().findEncountersByVisitID(visitEntity.id)
        visit.patient = PatientDAO().findPatientByID(visitEntity.patientKeyID)
        return visit
    }

    @JvmStatic
    fun convert(visit: Visit): VisitEntity {
        val visitEntity = VisitEntity()
        visitEntity.id = visit.id
        visitEntity.uuid = visit.uuid
        visitEntity.patientKeyID = visit.patient.id!!
        visitEntity.visitType = visit.visitType.display
        visitEntity.visitPlace = visit.location.display
        visitEntity.isStartDate = visit.startDatetime
        visitEntity.stopDate = visit.stopDatetime
        return visitEntity
    }

    @JvmStatic
    fun convert(patientEntity: PatientEntity): Patient {
        val logger = com.openmrs.android_sdk.library.OpenmrsAndroid.getOpenMRSLogger()
        val patient = Patient(patientEntity.id, patientEntity.encounters, null)
        patient.display = patientEntity.display
        patient.uuid = patientEntity.uuid
        val patientIdentifier = PatientIdentifier()
        patientIdentifier.identifier = patientEntity.identifier
        if (patient.identifiers == null) {
            patient.identifiers = ArrayList()
        }
        patient.identifiers.add(patientIdentifier)
        val personName = PersonName().apply {
            givenName = patientEntity.givenName
            middleName = patientEntity.middleName
            familyName = patientEntity.familyName
        }
        patient.names = listOf(personName)
        
        logger.i("[DB-Load] Patient ID: ${patient.id}, Name: '${personName.nameString}', Middle: '${personName.middleName}'")

        patient.gender = patientEntity.gender
        patient.birthdate = patientEntity.birthDate
        val photoByteArray = patientEntity.photo
        if (photoByteArray != null) {
            patient.photo = byteArrayToBitmap(photoByteArray)
        }
        val personAddress = PersonAddress()
        personAddress.address1 = patientEntity.address_1
        personAddress.address2 = patientEntity.address_2
        personAddress.postalCode = patientEntity.postalCode
        personAddress.country = patientEntity.country
        personAddress.stateProvince = patientEntity.state
        personAddress.cityVillage = patientEntity.city
        patient.addresses.add(personAddress)
        if (patientEntity.causeOfDeath != null) {
            patient.causeOfDeath = Resource(ApplicationConstants.EMPTY_STRING, patientEntity.causeOfDeath, ArrayList(), 0)
        }
        patient.isDeceased = patientEntity.deceased == "true"
        return patient
    }

    @JvmStatic
    fun convert(patient: Patient): PatientEntity {
        val patientEntity = PatientEntity()
        val logger = com.openmrs.android_sdk.library.OpenmrsAndroid.getOpenMRSLogger()
        
        val pName = patient.name
        if (pName != null) {
            val nameStr = pName.nameString
            logger.i("[DB-Save] Patient Name: '$nameStr', Middle: '${pName.middleName}'")
            patientEntity.display = nameStr
            patientEntity.givenName = pName.givenName
            patientEntity.middleName = pName.middleName
            patientEntity.familyName = pName.familyName
        } else {
            patientEntity.display = ""
            logger.w("[DB-Save] Patient Name is null!")
        }
        
        patientEntity.uuid = patient.uuid
        
        val identifiers = patient.identifiers
        if (identifiers != null && !identifiers.isEmpty()) {
            // Try to find the primary/preferred identifier or just the first one that has a string
            val idObj = identifiers.find { it.preferred == true && it.identifier != null } 
                       ?: identifiers.find { it.identifier != null }
                       ?: identifiers[0]
            
            patientEntity.identifier = idObj.identifier
            logger.i("[DB-Save] Patient Identifier list size: ${identifiers.size}, Selected: '${patientEntity.identifier}'")
        } else {
            logger.w("[DB-Save] Patient has NO identifiers!")
        }

        patientEntity.gender = patient.gender
        patientEntity.birthDate = patient.birthdate
        patientEntity.deathDate = null
        if (null != patient.causeOfDeath) {
            if (patient.causeOfDeath.display == null) {
                patientEntity.causeOfDeath = null
            } else {
                patientEntity.causeOfDeath = patient.causeOfDeath.display
            }
        } else {
            patientEntity.causeOfDeath = null
        }
        patientEntity.age = null
        if (patient.photo != null) {
            patientEntity.photo = bitmapToByteArray(patient.photo)
        } else {
            patientEntity.photo = null
        }
        if (null != patient.address) {
            patientEntity.address_1 = patient.address.address1
            patientEntity.address_2 = patient.address.address2
            patientEntity.postalCode = patient.address.postalCode
            patientEntity.country = patient.address.country
            patientEntity.state = patient.address.stateProvince
            patientEntity.city = patient.address.cityVillage
        }
        patientEntity.encounters = patient.encounters
        patientEntity.deceased = (patient.isDeceased ?: false).toString()
        return patientEntity
    }

    @JvmStatic
    fun convert(allergy: Allergy, patientID: String?): AllergyEntity {
        val allergyEntity = AllergyEntity()
        allergyEntity.uuid = allergy.uuid
        allergyEntity.patientId = patientID
        allergyEntity.comment = allergy.comment
        if (allergy.severity != null) {
            allergyEntity.severityDisplay = allergy.severity!!.display
            allergyEntity.severityUUID = allergy.severity!!.uuid
        }
        allergyEntity.allergenDisplay = allergy.allergen!!.codedAllergen!!.display
        allergyEntity.allergenUUID = allergy.allergen!!.codedAllergen!!.uuid
        allergyEntity.allergenType = allergy.allergen!!.allergenType
        allergyEntity.allergyReactions = allergy.reactions
        return allergyEntity
    }

    @JvmStatic
    @JvmName("convertTo")
    fun convert(entities: List<AllergyEntity>): List<Allergy> {
        val allergies = ArrayList<Allergy>()
        for (allergyEntity in entities) {
            allergies.add(convert(allergyEntity))
        }
        return allergies
    }

    @JvmStatic
    fun convert(allergyEntity: AllergyEntity): Allergy {
        val allergy = Allergy()
        allergy.id = allergyEntity.id
        allergy.uuid = allergyEntity.uuid
        allergy.comment = allergyEntity.comment
        if (allergyEntity.allergyReactions != null) {
            allergy.reactions = allergyEntity.allergyReactions!!
        } else {
            allergy.reactions = ArrayList()
        }
        val allergen = Allergen()
        allergen.allergenType = allergyEntity.allergenType
        allergen.codedAllergen = Resource(allergyEntity.allergenUUID!!, allergyEntity.allergenDisplay!!, ArrayList(), 1)
        allergy.allergen = allergen
        if (allergyEntity.severityDisplay != null) {
            allergy.severity = Resource(allergyEntity.severityUUID!!, allergyEntity.severityDisplay!!, ArrayList(), 1)
        }
        return allergy
    }

    @JvmStatic
    fun convertToStandalone(observation: Observation): StandaloneObservationEntity {
        return StandaloneObservationEntity(
            uuid = observation.uuid,
            display = observation.display,
            encounterUuid = observation.encounter?.uuid,
            patientUuid = observation.person?.uuid,
            locationUuid = observation.location?.uuid,
            value = observation.value.toString(),
            status = observation.status,
            obsDateTime = observation.obsDatetime,
            interpretation = observation.interpretation,
            conceptuuid = observation.concept?.uuid,
            order = observation.order,
            comment = observation.comment
        )
    }

    private fun bitmapToByteArray(image: Bitmap): ByteArray {
        val outputStream = ByteArrayOutputStream()
        image.compress(Bitmap.CompressFormat.PNG, 0, outputStream)
        return outputStream.toByteArray()
    }

    private fun byteArrayToBitmap(imageByteArray: ByteArray): Bitmap {
        val inputStream = ByteArrayInputStream(imageByteArray)
        return BitmapFactory.decodeStream(inputStream)
    }

    @JvmStatic
    fun <T> createObservableIO(func: Callable<T>?): Observable<T> {
        return Observable.fromCallable(func)
                .subscribeOn(Schedulers.io())
    }

    fun convert(appointment: Appointment, patientUuid: String): AppointmentEntity {
        val appointmentEntity = AppointmentEntity()
        appointmentEntity.uuid = appointment.uuid ?: ""
        appointmentEntity.patientUuid = patientUuid
        appointmentEntity.appointmentNumber = appointment.appointmentNumber
        appointmentEntity.serviceName = appointment.service?.name
        appointmentEntity.locationName = appointment.location?.name
        appointmentEntity.startDateTime = appointment.startDateTime
        appointmentEntity.endDateTime = appointment.endDateTime
        appointmentEntity.appointmentKind = appointment.appointmentKind
        appointmentEntity.status = appointment.status
        appointmentEntity.comments = appointment.comments
        return appointmentEntity
    }

    fun convert(appointmentEntity: AppointmentEntity): Appointment {
        val appointment = Appointment()
        appointment.uuid = appointmentEntity.uuid
        appointment.appointmentNumber = appointmentEntity.appointmentNumber
        appointment.service = AppointmentServiceInfo().apply { name = appointmentEntity.serviceName }
        appointment.location = AppointmentLocationInfo().apply { name = appointmentEntity.locationName }
        appointment.startDateTime = appointmentEntity.startDateTime
        appointment.endDateTime = appointmentEntity.endDateTime
        appointment.appointmentKind = appointmentEntity.appointmentKind
        appointment.status = appointmentEntity.status
        appointment.comments = appointmentEntity.comments
        return appointment
    }

    /**
     * Convert a retrofit model response to Room Entity
     *
     * @param orderGet the deserialized response from JSON
     *
     * @return the type OrderEntity
     */
    fun convert(orderGet: OrderGet): OrderEntity {
        val orderEntity = OrderEntity().apply {
            uuid = orderGet.uuid
            display = orderGet.display
            action = orderGet.action
            accessionNumber = orderGet.accessionNumber
            autoExpireDate = orderGet.autoExpireDate
            careSettingName = orderGet.careSettingName
            conceptUuid = orderGet.concept.uuid
            dateActivated = orderGet.dateActivated
            dateStopped = orderGet.dateStopped
            doseUnits = orderGet.doseUnits
            urgency = orderGet.urgency
            orderer.display = orderGet.orderer.display
            orderer.uuid = orderGet.orderer.uuid
            dosingType = orderGet.dosingType
            drug = orderGet.drug
            fulfillerStatus = orderGet.fulfillerStatus
            specimenSource = orderGet.specimenSource
            instructions = orderGet.instructions
            type = orderGet.type
            orderType.uuid = orderGet.orderType.uuid
            orderType.display = orderGet.orderType.display
            quantity = orderGet.quantity
            dosingInstructions = orderGet.dosingInstructions
            encounterUuid = orderGet.encounter.uuid
            fulfillerComment = orderGet.fulfillerComment
            scheduledDate = orderGet.scheduledDate
            numberOfRepeats = orderGet.numberOfRepeats
            orderReason = orderGet.orderReason
            duration = orderGet.duration
            orderNumber = orderGet.orderNumber
        }
        return orderEntity
    }

    /**
     * Convert a retrofit model response to Room Entity
     *
     * @param drugList the list of type Drug
     *
     * @return the list of type DrugEntity
     */
    fun convertDrugListToEntityList(drugList: List<Drug>): List<DrugEntity> {
        val drugEnitityList = mutableListOf<DrugEntity>()

        for(drug in drugList){
            val dosageFormConverted = DosageFormEntity().apply{
                uuid = drug.dosageForm!!.uuid!!
                display = drug.dosageForm!!.display!!
            }
            val conceptConverted = DrugConceptEntity().apply {
                uuid = drug.concept!!.uuid!!
                display = drug.dosageForm!!.display!!
            }
            val drugEntity = DrugEntity().apply {
                uuid = drug.uuid!!
                display = drug.display!!
                description = drug.description ?: "No Description"
                combination = drug.combination!!
                maximumDailyDose = drug.maximumDailyDose ?: 0
                minimumDailyDose = drug.minimumDailyDose ?: 0
                concept = conceptConverted
                dosageForm = dosageFormConverted
                drugReferenceMaps = drug.drugReferenceMaps
                ingredients = drug.ingredients
                name = drug.name!!
                retired = drug.retired!!
                strength = drug.strength ?: "NA"
                resourceVersion = drug.resourceVersion!!
            }
            drugEnitityList.add(drugEntity)
        }
        return drugEnitityList
    }

    /**
     * Convert a retrofit model response to Room Entity
     *
     * @param drug the type Drug
     *
     * @return the type DrugEntity
     */
    fun convert(drug: Drug): DrugEntity {
        val dosageFormConverted = DosageFormEntity().apply {
            uuid = drug.dosageForm!!.uuid!!
            display = drug.dosageForm!!.display!!
        }
        val conceptConverted = DrugConceptEntity().apply {
            uuid = drug.concept!!.uuid!!
            display = drug.dosageForm!!.display!!
        }
        val drugEntity = DrugEntity().apply {
            uuid = drug.uuid!!
            display = drug.display!!
            description = drug.description ?: "No Description"
            combination = drug.combination!!
            maximumDailyDose = drug.maximumDailyDose ?: 0
            minimumDailyDose = drug.minimumDailyDose ?: 0
            concept = conceptConverted
            dosageForm = dosageFormConverted
            drugReferenceMaps = drug.drugReferenceMaps
            ingredients = drug.ingredients
            name = drug.name!!
            retired = drug.retired!!
            strength = drug.strength ?: "NA"
            resourceVersion = drug.resourceVersion!!
        }
        return drugEntity
    }
    
    
    /**
     * Convert a retrofit model response to Room Entity
     *
     * @param programList the list of type ProgramGet
     *
     * @return the list of type ProgramEntity
     */
    fun convertProgramListToEntityList(programList: List<ProgramGet>): List<ProgramEntity> {
        val programEnitityList = mutableListOf<ProgramEntity>()

        for(program in programList){
            val programEntity = ProgramEntity().apply {
                uuid = program.uuid
                name = program.name
                allWorkflows = program.allWorkflows
            }
            programEnitityList.add(programEntity)
        }
        return programEnitityList
    }

    /**
     * Convert a retrofit model response to Room Entity
     *
     * @param program the type ProgramGet
     *
     * @return the type ProgramEntity
     */
    fun convert(program: ProgramGet): ProgramEntity {
        val programEntity = ProgramEntity().apply {
            uuid = program.uuid
            name = program.name
            allWorkflows = program.allWorkflows
        }
        return programEntity
    }
}
