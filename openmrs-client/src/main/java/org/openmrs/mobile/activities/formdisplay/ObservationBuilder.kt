package org.openmrs.mobile.activities.formdisplay

import com.openmrs.android_sdk.library.models.Obscreate
import com.openmrs.android_sdk.utilities.DateField
import com.openmrs.android_sdk.utilities.InputField
import com.openmrs.android_sdk.utilities.SelectMultipleField
import com.openmrs.android_sdk.utilities.SelectOneField
import com.openmrs.android_sdk.utilities.TextField
import org.joda.time.LocalDateTime

/**
 * Turns a form's filled-in fields into the observations sent with its encounter. [personUuid] is
 * the only patient-specific part of an observation, which is what lets Group Entry reuse one set
 * of answers for many patients.
 */
object ObservationBuilder {

    fun build(
        inputFields: List<InputField>,
        selectOneFields: List<SelectOneField>,
        selectMultipleFields: List<SelectMultipleField>,
        dateFields: List<DateField>,
        textFields: List<TextField>,
        personUuid: String?
    ): List<Obscreate> {
        val observations = mutableListOf<Obscreate>()

        for (input in inputFields) {
            if (input.value != InputField.DEFAULT_VALUE) {
                observations += obs(input.concept, input.value.toString(), personUuid)
            }
        }
        for (selectOneField in selectOneFields) {
            selectOneField.chosenAnswer?.let { observations += obs(selectOneField.concept, it.concept, personUuid) }
        }
        for (selectMultipleField in selectMultipleFields) {
            for (answer in selectMultipleField.selectedAnswers) {
                observations += obs(selectMultipleField.concept, answer.concept, personUuid)
            }
        }
        for (dateField in dateFields) {
            if (!dateField.date.isNullOrEmpty()) {
                observations += obs(dateField.concept, toRequestDate(dateField.date!!), personUuid)
            }
        }
        for (textField in textFields) {
            if (!textField.value.isNullOrBlank()) {
                observations += obs(textField.concept, textField.value, personUuid)
            }
        }

        return observations
    }

    /** The date picker's d/M/yyyy to the yyyy-MM-dd the server expects. */
    private fun toRequestDate(date: String): String {
        val parts = date.split("/")
        return if (parts.size == 3) {
            "${parts[2]}-${parts[1].padStart(2, '0')}-${parts[0].padStart(2, '0')}"
        } else {
            date
        }
    }

    private fun obs(concept: String, value: String?, personUuid: String?) = Obscreate().apply {
        this.concept = concept
        this.value = value
        obsDatetime = LocalDateTime().toString()
        person = personUuid
    }
}
