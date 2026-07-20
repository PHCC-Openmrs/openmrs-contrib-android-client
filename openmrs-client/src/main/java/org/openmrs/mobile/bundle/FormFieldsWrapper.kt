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
package org.openmrs.mobile.bundle

import com.openmrs.android_sdk.library.models.Answer
import com.openmrs.android_sdk.library.models.Encounter
import com.openmrs.android_sdk.library.models.Observation
import com.openmrs.android_sdk.library.models.Question
import com.openmrs.android_sdk.utilities.DateField
import com.openmrs.android_sdk.utilities.InputField
import com.openmrs.android_sdk.utilities.SelectMultipleField
import com.openmrs.android_sdk.utilities.SelectOneField
import com.openmrs.android_sdk.utilities.TextField
import java.io.Serializable
import java.util.ArrayList
import java.util.LinkedList

class FormFieldsWrapper : Serializable {

    lateinit var inputFields: List<InputField>
    lateinit var selectOneFields: List<SelectOneField>
    var selectMultipleFields: List<SelectMultipleField> = mutableListOf()
    var dateFields: List<DateField> = mutableListOf()
    var textFields: List<TextField> = mutableListOf()

    companion object {
        fun create(encounter: Encounter): ArrayList<FormFieldsWrapper> {
            val formFieldsWrapperList = ArrayList<FormFieldsWrapper>()
            val pages = encounter.form?.pages ?: return formFieldsWrapperList
            for (page in pages) {
                val formFieldsWrapper = FormFieldsWrapper()
                val inputFieldList = mutableListOf<InputField>()
                val selectOneFieldList = mutableListOf<SelectOneField>()
                val selectMultipleFieldList = mutableListOf<SelectMultipleField>()
                val dateFieldList = mutableListOf<DateField>()
                val textFieldList = mutableListOf<TextField>()
                
                for (section in page.sections) {
                    for (question in section.questions) {
                        extractFieldsRecursive(question, encounter.observations, inputFieldList, selectOneFieldList, selectMultipleFieldList, dateFieldList, textFieldList)
                    }
                }
                
                formFieldsWrapper.inputFields = inputFieldList
                formFieldsWrapper.selectOneFields = selectOneFieldList
                formFieldsWrapper.selectMultipleFields = selectMultipleFieldList
                formFieldsWrapper.dateFields = dateFieldList
                formFieldsWrapper.textFields = textFieldList
                formFieldsWrapperList.add(formFieldsWrapper)
            }
            return formFieldsWrapperList
        }

        private fun extractFieldsRecursive(
            question: Question,
            observations: List<Observation>,
            inputFieldList: MutableList<InputField>,
            selectOneFieldList: MutableList<SelectOneField>,
            selectMultipleFieldList: MutableList<SelectMultipleField>,
            dateFieldList: MutableList<DateField>,
            textFieldList: MutableList<TextField>
        ) {
            val options = question.questionOptions
            if (options?.rendering == "group") {
                question.questions.forEach { 
                    extractFieldsRecursive(it, observations, inputFieldList, selectOneFieldList, selectMultipleFieldList, dateFieldList, textFieldList) 
                }
            } else {
                val conceptUuid = options?.concept ?: return
                when (options.rendering) {
                    "number" -> {
                        val field = InputField(conceptUuid)
                        field.value = getValue(observations, conceptUuid)
                        inputFieldList.add(field)
                    }
                    "select", "radio", "ui-select-extended" -> {
                        val field = SelectOneField(options.answers ?: emptyList(), conceptUuid)
                        // TODO: Map existing observations if needed
                        selectOneFieldList.add(field)
                    }
                    "checkbox" -> {
                        val field = SelectMultipleField(options.answers ?: emptyList(), conceptUuid)
                        selectMultipleFieldList.add(field)
                    }
                    "date" -> {
                        val field = DateField(conceptUuid)
                        dateFieldList.add(field)
                    }
                    "text", "textarea" -> {
                        val field = TextField(conceptUuid)
                        textFieldList.add(field)
                    }
                }
            }
        }

        private fun getValue(observations: List<Observation>, conceptUuid: String?): Double {
            for (observation in observations) {
                if (observation.concept!!.uuid == conceptUuid) {
                    return observation.displayValue!!.toDouble()
                }
            }
            return InputField.DEFAULT_VALUE
        }
    }
}
