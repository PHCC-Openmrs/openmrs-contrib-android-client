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
package com.openmrs.android_sdk.library.api.repository

import com.openmrs.android_sdk.library.api.RestApi
import com.openmrs.android_sdk.library.dao.AppointmentRoomDAO
import com.openmrs.android_sdk.library.models.Appointment
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.mockk.verify
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.buffer
import okio.source
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Inject

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class AppointmentRepositoryTest {

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    lateinit var mockWebServer: MockWebServer
    lateinit var appointmentApi: RestApi

    @Inject
    lateinit var appointmentRepository: AppointmentRepository

    @Inject
    lateinit var appointmentRoomDAO: AppointmentRoomDAO

    val patientUuid = "c7ec7d3d-cd3f-4f64-8566-e59bb678a362"
    val startDate = "2026-01-29T00:00:00.000+0530"

    @Before
    fun setup() {
        hiltRule.inject()
        mockWebServer = MockWebServer()
        mockWebServer.start()

        val retrofit = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        appointmentApi = retrofit.create(RestApi::class.java)
        appointmentRepository.restApi = appointmentApi
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun searchAppointmentsAndSave_serverReturnsOtherPatients_filtersToRequestedPatient() {
        // The fixture includes a second appointment for a different patient, mirroring a real
        // server response observed to ignore the patient filter entirely.
        enqueueMockResponse("mocked_responses/AppointmentRepository/AppointmentSearch-success.json")

        val result = appointmentRepository.searchAppointmentsAndSave(patientUuid, startDate).toBlocking().first()

        assertEquals(1, result.size)
        assertEquals("6dbbd1d9-ba68-4260-a4c7-9b98859abf8c", result[0].uuid)
        assertEquals("General Medicine service", result[0].service?.name)
        assertEquals(Appointment.Status.SCHEDULED, result[0].status)
        verify { appointmentRoomDAO.addOrUpdateAll(any()) }
    }

    @Test
    fun cancelAppointment_success_updatesLocalStatus() {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        appointmentRepository.cancelAppointment("6dbbd1d9-ba68-4260-a4c7-9b98859abf8c").toBlocking().first()

        verify {
            appointmentRoomDAO.updateStatus("6dbbd1d9-ba68-4260-a4c7-9b98859abf8c", Appointment.Status.CANCELLED)
        }
    }

    fun enqueueMockResponse(fileName: String) {
        javaClass.classLoader?.let {
            val inputStream = it.getResourceAsStream(fileName)
            val source = inputStream.source().buffer()
            val mockResponse = MockResponse()
            mockResponse.setBody(source.readString(Charsets.UTF_8))
            mockResponse.setResponseCode(200)
            mockWebServer.enqueue(mockResponse)
        }
    }
}
