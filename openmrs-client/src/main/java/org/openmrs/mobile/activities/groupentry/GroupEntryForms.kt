package org.openmrs.mobile.activities.groupentry

import com.openmrs.android_sdk.library.OpenmrsAndroid
import com.openmrs.android_sdk.library.models.Form
import com.openmrs.android_sdk.library.models.Page
import com.openmrs.android_sdk.library.models.Patient
import com.openmrs.android_sdk.library.models.Question
import com.openmrs.android_sdk.library.models.Section
import com.openmrs.android_sdk.utilities.InputField
import com.openmrs.android_sdk.utilities.SelectOneField
import com.openmrs.android_sdk.utilities.TextField
import org.openmrs.mobile.activities.formdisplay.PatientField
import org.openmrs.mobile.activities.formdisplay.PatientFieldAutoFill

/**
 * Group Entry: a form enabled with `"groupEntryForm": { "enabled": true }` is filled in once and
 * saved as one encounter per selected patient. Every question in such a form is one of:
 * - shared (`"groupShared": true`, or inside a shared group) - asked once, same answer for all;
 * - a patient field (see [PatientFieldAutoFill]) - filled from each patient's own record (or,
 *   for the current-user field such as "CHW Name", the logged-in user's name);
 * - anything else - left blank. A required one of those blocks the form from Group Entry, since
 *   nobody would ever get to answer it.
 *
 * Nothing here knows any particular form: enabling another form is only a Form Builder edit.
 */
object GroupEntryForms {

    /** The form's pages cut down to only its shared questions; empty sections and pages dropped. */
    fun sharedPages(form: Form): List<Page> = form.pages.mapNotNull { page ->
        val sections = page.sections.mapNotNull { section ->
            val questions = sharedQuestions(section.questions)
            if (questions.isEmpty()) null else Section().apply {
                label = section.label
                this.questions = questions
            }
        }
        if (sections.isEmpty()) null else Page().apply {
            label = page.label
            this.sections = sections
        }
    }

    private fun sharedQuestions(questions: List<Question>): List<Question> = questions.mapNotNull { question ->
        when {
            question.isGroupShared -> question
            isGroup(question) -> {
                val children = sharedQuestions(question.questions)
                if (children.isEmpty()) null else copyGroup(question, children)
            }
            else -> null
        }
    }

    private fun copyGroup(group: Question, children: List<Question>) = Question().apply {
        type = group.type
        id = group.id
        label = group.label
        isRequired = group.isRequired
        questionOptions = group.questionOptions
        isGroupShared = group.isGroupShared
        groupPatientField = group.groupPatientField
        questions = children
    }

    /**
     * Labels of required questions that are neither shared nor filled from the patient record -
     * the form can't be used for Group Entry until the form's config covers them.
     */
    fun blockingQuestions(form: Form): List<String> {
        val blocking = mutableListOf<String>()
        fun visit(questions: List<Question>) {
            questions.forEach { question ->
                when {
                    question.isGroupShared -> Unit
                    isGroup(question) -> visit(question.questions)
                    question.isRequired && PatientFieldAutoFill.fieldOf(question) == null ->
                        blocking += question.label ?: question.id ?: question.questionOptions?.concept ?: ""
                }
            }
        }
        form.pages.forEach { page -> page.sections.forEach { visit(it.questions) } }
        return blocking
    }

    /** One patient's own answers to a form's (non-shared) patient-field questions. */
    class PatientAnswers {
        val inputFields = mutableListOf<InputField>()
        val selectOneFields = mutableListOf<SelectOneField>()
        val textFields = mutableListOf<TextField>()

        /** True when the form asks for a phone number but this patient has no valid one. */
        var isPhoneOmitted = false
    }

    /**
     * [patient]'s answers to [form]'s patient-field questions, auto-filled as the single-patient
     * form would. A phone number is only kept when it is exactly 10 digits (the form's own
     * rule); otherwise the form is saved without it and [PatientAnswers.isPhoneOmitted] is set.
     */
    fun patientAnswers(
        form: Form,
        patient: Patient,
        currentUserName: () -> String? = OpenmrsAndroid::getCurrentUserPersonName
    ): PatientAnswers {
        val answers = PatientAnswers()
        fun visit(questions: List<Question>) {
            questions.forEach { question ->
                if (question.isGroupShared) return@forEach
                if (isGroup(question)) {
                    visit(question.questions)
                    return@forEach
                }
                val field = PatientFieldAutoFill.fieldOf(question) ?: return@forEach
                val options = question.questionOptions ?: return@forEach
                val concept = options.concept ?: return@forEach
                when (options.rendering) {
                    "number" -> PatientFieldAutoFill.ageValue(question, patient)?.let {
                        answers.inputFields += InputField(concept).apply { value = it }
                    }
                    "select", "radio" -> PatientFieldAutoFill.answerIndex(question, patient)?.let {
                        answers.selectOneFields += SelectOneField(options.answers.orEmpty(), concept).apply { setAnswer(it) }
                    }
                    "text", "textarea" -> {
                        val value = PatientFieldAutoFill.textValue(question, patient, currentUserName)?.trim()
                        if (field == PatientField.PHONE && (value == null || !PHONE_NUMBER_REGEX.matches(value))) {
                            answers.isPhoneOmitted = true
                        } else if (!value.isNullOrEmpty()) {
                            answers.textFields += TextField(concept).apply { this.value = value }
                        }
                    }
                }
            }
        }
        form.pages.forEach { page -> page.sections.forEach { visit(it.questions) } }
        return answers
    }

    private fun isGroup(question: Question) = question.questionOptions?.rendering == "group"

    private val PHONE_NUMBER_REGEX = Regex("^\\d{10}$")
}
