package com.openmrs.android_sdk.library.api.repository

import com.google.gson.GsonBuilder
import com.openmrs.android_sdk.library.OpenMRSLogger
import com.openmrs.android_sdk.library.OpenmrsAndroid
import com.openmrs.android_sdk.library.api.RestApi
import com.openmrs.android_sdk.library.dao.PatientDAO
import com.openmrs.android_sdk.library.databases.entities.LocationEntity
import com.openmrs.android_sdk.library.models.IdentifierType
import com.openmrs.android_sdk.library.models.Patient
import com.openmrs.android_sdk.library.models.PatientIdentifier
import com.openmrs.android_sdk.library.models.Resource
import com.openmrs.android_sdk.utilities.ApplicationConstants
import com.openmrs.android_sdk.utilities.ResourceSerializer
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.verify
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Covers how an offline-registered patient is uploaded: a National ID that already exists on the
 * server links to that patient (no similarity prompt, no new OpenMRS ID burned), anything else
 * goes on to be created.
 */
@RunWith(RobolectricTestRunner::class)
class PatientRepositorySyncTest {

    private lateinit var server: MockWebServer
    private val requestedPaths = mutableListOf<String>()
    private val patientDAO: PatientDAO = mockk(relaxed = true)
    private val locationRepository: LocationRepository = mockk()
    private val encounterRepository: EncounterRepository = mockk(relaxed = true)
    private val logger: OpenMRSLogger = mockk(relaxed = true)
    private lateinit var repository: PatientRepository

    @Before
    fun setUp() {
        mockkStatic(OpenmrsAndroid::class)
        // Auto-delete reads shared preferences; relaxed prefs report it off, the default.
        every { OpenmrsAndroid.getOpenMRSSharedPreferences() } returns mockk(relaxed = true)
        every { locationRepository.location } returns LocationEntity("Clinic").apply { uuid = "location-uuid" }

        server = MockWebServer()
        val gson = GsonBuilder()
            .excludeFieldsWithoutExposeAnnotation()
            .registerTypeHierarchyAdapter(Resource::class.java, ResourceSerializer())
            .create()
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(RestApi::class.java)
        repository = PatientRepository(patientDAO, locationRepository, encounterRepository, api, logger)
    }

    @After
    fun tearDown() {
        clearAllMocks()
        server.shutdown()
    }

    private fun serverRespondsToSearchWith(json: String) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requestedPaths += request.path.orEmpty()
                return if (request.path.orEmpty().startsWith("/patient")) MockResponse().setBody(json)
                else MockResponse().setResponseCode(500)
            }
        }
    }

    private fun offlinePatient(nationalId: String) = Patient().apply {
        id = 7L
        identifiers = listOf(PatientIdentifier().apply {
            identifier = nationalId
            identifierType = IdentifierType("National ID").apply {
                uuid = ApplicationConstants.IdentifierSource.NATIONAL_ID_IDENTIFIER_TYPE_UUID
            }
        })
    }

    @Test
    fun `national id already on the server links to that patient without generating an OpenMRS ID`() {
        serverRespondsToSearchWith(
            """{"results":[{"uuid":"server-uuid","identifiers":[{"identifier":"900000019","identifierType":{"uuid":"${ApplicationConstants.IdentifierSource.NATIONAL_ID_IDENTIFIER_TYPE_UUID}"}}]}]}"""
        )
        val patient = offlinePatient("900000019")
        val saved = slot<Patient>()
        every { patientDAO.updatePatient(any(), capture(saved)) } returns true

        val result = repository.syncPatient(patient).toBlocking().first()

        assertEquals("server-uuid", result.uuid)
        assertTrue(saved.captured.isIdentityLinkedOnly)
        // Only the lookup was made: nothing hit idgen and nothing tried to create the patient.
        assertEquals(1, requestedPaths.size)
        assertTrue(requestedPaths[0].startsWith("/patient"))
        assertFalse(requestedPaths.any { it.contains("idgen") })
        verify(exactly = 1) { patientDAO.updatePatient(7L, any()) }
    }

    @Test
    fun `a search hit whose identifiers do not contain the exact national id is not linked`() {
        // The ?q= search is loose; a result for a different ID must not be treated as the patient.
        serverRespondsToSearchWith(
            """{"results":[{"uuid":"someone-else","identifiers":[{"identifier":"900000118","identifierType":{"uuid":"x"}}]}]}"""
        )

        // With no match the sync carries on to create the patient, which starts by generating an
        // OpenMRS ID; the stub server fails that call, so the sync ends in an error.
        try {
            repository.syncPatient(offlinePatient("900000019")).toBlocking().first()
        } catch (expected: Exception) {
        }

        assertTrue(requestedPaths[0].startsWith("/patient"))
        assertTrue("creation should have started", requestedPaths.any { it.contains("idgen") })
        verify(exactly = 0) { patientDAO.updatePatient(any(), any()) }
    }
}
