package org.openmrs.mobile.activities.groupentry

import com.openmrs.android_sdk.library.models.Form
import com.openmrs.android_sdk.library.models.Patient
import com.openmrs.android_sdk.library.models.PersonAttribute
import com.openmrs.android_sdk.library.models.PersonAttributeType
import com.openmrs.android_sdk.library.models.PersonName
import com.openmrs.android_sdk.library.models.Resource
import com.openmrs.android_sdk.utilities.ApplicationConstants.PersonAttributeTypes.PHONE_NUMBER_UUID
import com.openmrs.android_sdk.utilities.FormUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openmrs.mobile.activities.formdisplay.ObservationBuilder
import org.openmrs.mobile.activities.formdisplay.PatientField
import org.openmrs.mobile.activities.formdisplay.PatientFieldAutoFill

/** Group Entry against the Health Promotion Session form (v1.1) as configured in the Form Builder. */
class GroupEntryFormsTest {

    @Test
    fun `group entry flags are read from the form json`() {
        val form = hpsForm()
        assertTrue(form.isGroupEntryEnabled)
        assertFalse(FormUtils.getForm(HPS_JSON.replace("\"groupEntryForm\": { \"enabled\": true },", "")).isGroupEntryEnabled)
    }

    @Test
    fun `only shared questions are shown, empty sections dropped`() {
        val pages = GroupEntryForms.sharedPages(hpsForm())

        assertEquals(1, pages.size)
        assertEquals(listOf("Session Details"), pages[0].sections.map { it.label })
        assertEquals(listOf("sessionDate", "sessionType", "topic", "chwName"), pages[0].sections[0].questions.map { it.id })
    }

    @Test
    fun `hps has no blocking question - its required phone validator isn't a schema required flag`() {
        assertTrue(GroupEntryForms.blockingQuestions(hpsForm()).isEmpty())
    }

    @Test
    fun `a required question that is neither shared nor a patient field blocks the form`() {
        val json = HPS_JSON.replace("\"id\": \"topic\", \"label\": \"Topic\", \"type\": \"obs\", \"groupShared\": true,",
            "\"id\": \"topic\", \"label\": \"Topic\", \"type\": \"obs\",")

        assertEquals(listOf("Topic"), GroupEntryForms.blockingQuestions(FormUtils.getForm(json)))
    }

    @Test
    fun `chw name is the current user - by the web's currentUserName() expression, or by label`() {
        val chwName = FormUtils.getForm(HPS_V12_JSON).pages[0].sections[1].questions.single { it.id == "chwName" }
        assertEquals(PatientField.CURRENT_USER, PatientFieldAutoFill.fieldOf(chwName.apply { label = "Facilitator" }))

        val byLabel = hpsForm().pages[0].sections[1].questions.single { it.id == "chwName" }
        assertEquals(PatientField.CURRENT_USER, PatientFieldAutoFill.fieldOf(byLabel))
        assertTrue(PatientFieldAutoFill.isFixedPatientField(byLabel))
    }

    @Test
    fun `chw name is filled with the logged-in user's name, with or without a patient`() {
        val chwName = FormUtils.getForm(HPS_V12_JSON).pages[0].sections[1].questions.single { it.id == "chwName" }

        assertEquals("Huda Nasser", PatientFieldAutoFill.textValue(chwName, null) { "Huda Nasser" })
        assertEquals(null, PatientFieldAutoFill.textValue(chwName, patient(phone = null)) { "" })
    }

    @Test
    fun `a non-shared chw name is saved with every patient's answers`() {
        val form = FormUtils.getForm(HPS_V12_JSON)
        assertTrue(GroupEntryForms.blockingQuestions(form).isEmpty())

        val answers = GroupEntryForms.patientAnswers(form, patient(phone = "0599123456")) { "Huda Nasser" }

        assertEquals(listOf("Amal Saleh", "0599123456", "Huda Nasser"), answers.textFields.map { it.value })
    }

    @Test
    fun `patient fields are recognised by label, or by groupPatientField when set`() {
        val questions = hpsForm().pages[0].sections[0].questions
        assertEquals(listOf(PatientField.NAME, PatientField.GENDER, PatientField.AGE, PatientField.PHONE),
            questions.map { PatientFieldAutoFill.fieldOf(it) })

        val relabelled = questions[0].apply { label = "Full name of attendee"; groupPatientField = "name" }
        assertEquals(PatientField.NAME, PatientFieldAutoFill.fieldOf(relabelled))
    }

    @Test
    fun `each patient gets their own demographics`() {
        val answers = GroupEntryForms.patientAnswers(hpsForm(), patient(phone = "0599123456"))

        assertEquals(listOf("Amal Saleh", "0599123456"), answers.textFields.map { it.value })
        assertEquals("da38e7f7-ea53-48b7-8caa-daf90cef95a7", answers.selectOneFields.single().chosenAnswer?.concept)
        assertFalse(answers.isPhoneOmitted)
    }

    @Test
    fun `a missing or invalid phone is left out and reported`() {
        listOf(null, "12345").forEach { phone ->
            val answers = GroupEntryForms.patientAnswers(hpsForm(), patient(phone = phone))

            assertEquals(listOf("Amal Saleh"), answers.textFields.map { it.value })
            assertTrue(answers.isPhoneOmitted)
        }
    }

    @Test
    fun `observations carry the given person`() {
        val answers = GroupEntryForms.patientAnswers(hpsForm(), patient(phone = "0599123456"))
        val obs = ObservationBuilder.build(answers.inputFields, answers.selectOneFields, emptyList(), emptyList(),
            answers.textFields, "patient-uuid")

        assertEquals(3, obs.size)
        assertTrue(obs.all { it.person == "patient-uuid" })
    }

    private fun hpsForm(): Form = FormUtils.getForm(HPS_JSON)

    private fun patient(phone: String?): Patient {
        val name = PersonName().apply { givenName = "Amal"; familyName = "Saleh" }
        val attributes = listOfNotNull(phone?.let {
            PersonAttribute().apply {
                attributeType = PersonAttributeType().apply { uuid = PHONE_NUMBER_UUID }
                value = it
            }
        })
        return Patient(1L, "", emptyList(), listOf(name), "F", null, false, emptyList(), attributes, null, Resource(), false)
    }

    companion object {
        /** v1.2: CHW Name filled the way the web does it, and no longer shared. */
        private val HPS_V12_JSON by lazy {
            HPS_JSON
                .replace("\"id\": \"chwName\", \"label\": \"CHW Name\", \"type\": \"obs\", \"groupShared\": true,",
                    "\"id\": \"chwName\", \"label\": \"CHW Name\", \"type\": \"obs\", \"readonly\": true,")
                .replace("\"concept\": \"8f129f81-e078-4664-be97-649b66162a50\" }",
                    "\"concept\": \"8f129f81-e078-4664-be97-649b66162a50\", " +
                        "\"calculate\": { \"calculateExpression\": \"mode === 'enter' ? currentUserName() : ''\" } }")
                .also { check("currentUserName" in it && "\"CHW Name\", \"type\": \"obs\", \"readonly\"" in it) }
        }

        private val HPS_JSON = """
            {
              "name": "Health Promotion Session Form",
              "groupEntryForm": { "enabled": true },
              "pages": [ { "label": "Health Promotion Session", "sections": [
                { "label": "Participant Information", "questions": [
                  { "id": "participantName", "label": "Participant Name", "type": "obs", "readonly": true,
                    "questionOptions": { "rendering": "text", "concept": "893db5a7-418e-4e6c-8098-f9d06d98a7c1" } },
                  { "id": "gender", "label": "Gender", "type": "obs",
                    "questionOptions": { "rendering": "select", "concept": "117b8efb-3de0-48a1-8f9d-edda88942e8c", "answers": [
                      { "label": "Male", "concept": "65af055d-1f15-46bb-a24e-fdaaed0c407b" },
                      { "label": "Female", "concept": "da38e7f7-ea53-48b7-8caa-daf90cef95a7" } ] } },
                  { "id": "age", "label": "Age (in Years)", "type": "obs",
                    "questionOptions": { "rendering": "number", "concept": "ea3c8357-2587-491b-8a8e-ddf7b481e265" } },
                  { "id": "phoneNumber", "label": "Phone Number", "type": "obs",
                    "questionOptions": { "rendering": "text", "concept": "7c6718e0-9d0d-4e80-b979-33f6b0ca8a63" },
                    "validators": [ { "type": "required", "message": "Phone number is required." } ] }
                ] },
                { "label": "Session Details", "questions": [
                  { "id": "sessionDate", "label": "Session Date", "type": "obs", "groupShared": true, "required": true,
                    "questionOptions": { "rendering": "date", "concept": "ceaca505-6dff-4940-8a43-8c060a0924d7" } },
                  { "id": "sessionType", "label": "Session Type", "type": "obs", "groupShared": true, "required": true,
                    "questionOptions": { "rendering": "select", "concept": "c271aba7-1feb-4215-afde-6e37f2ef1800", "answers": [
                      { "label": "Group Session", "concept": "ab7fc550-ca52-42b9-b34b-fe1ba04222d4" },
                      { "label": "Individual Session", "concept": "67979f74-e38f-41ea-9fcd-d0b2d4a8f71a" } ] } },
                  { "id": "topic", "label": "Topic", "type": "obs", "groupShared": true, "required": true,
                    "questionOptions": { "rendering": "text", "concept": "c55c3c6b-9ca4-441a-b9a3-471caae0e2cc" } },
                  { "id": "chwName", "label": "CHW Name", "type": "obs", "groupShared": true,
                    "questionOptions": { "rendering": "text", "concept": "8f129f81-e078-4664-be97-649b66162a50" } }
                ] }
              ] } ]
            }
        """.trimIndent()
    }
}
