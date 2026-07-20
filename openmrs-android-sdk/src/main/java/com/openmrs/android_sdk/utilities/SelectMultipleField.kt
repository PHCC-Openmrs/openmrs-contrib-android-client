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
package com.openmrs.android_sdk.utilities

import com.openmrs.android_sdk.library.models.Answer
import java.io.Serializable

data class SelectMultipleField(var answerList: List<Answer>, var concept: String) : Serializable {
    var selectedAnswers: MutableList<Answer> = mutableListOf()

    fun setAnswer(answerPosition: Int, isChecked: Boolean) {
        if (answerPosition < answerList.size) {
            val answer = answerList[answerPosition]
            if (isChecked) {
                if (!selectedAnswers.contains(answer)) {
                    selectedAnswers.add(answer)
                }
            } else {
                selectedAnswers.remove(answer)
            }
        }
    }

    fun isAnswerSelected(answerPosition: Int): Boolean {
        if (answerPosition < answerList.size) {
            return selectedAnswers.contains(answerList[answerPosition])
        }
        return false
    }
}
