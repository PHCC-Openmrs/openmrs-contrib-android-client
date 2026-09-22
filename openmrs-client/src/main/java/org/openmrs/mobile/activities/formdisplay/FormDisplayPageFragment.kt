/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.mobile.activities.formdisplay

import android.app.DatePickerDialog
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import com.openmrs.android_sdk.library.databases.entities.LocationEntity
import com.openmrs.android_sdk.library.models.Answer
import com.openmrs.android_sdk.library.models.Page
import com.openmrs.android_sdk.library.models.Question
import com.openmrs.android_sdk.library.models.Section
import com.openmrs.android_sdk.utilities.ApplicationConstants.BundleKeys.FORM_FIELDS_BUNDLE
import com.openmrs.android_sdk.utilities.ApplicationConstants.BundleKeys.FORM_PAGE_BUNDLE
import com.openmrs.android_sdk.utilities.ApplicationConstants.BundleKeys.PATIENT_ID_BUNDLE
import com.openmrs.android_sdk.utilities.DateField
import com.openmrs.android_sdk.utilities.InputField
import com.openmrs.android_sdk.utilities.RangeEditText
import com.openmrs.android_sdk.utilities.SelectMultipleField
import com.openmrs.android_sdk.utilities.SelectOneField
import com.openmrs.android_sdk.utilities.TextField
import com.openmrs.android_sdk.utilities.ToastUtil
import dagger.hilt.android.AndroidEntryPoint
import org.openmrs.mobile.R
import org.openmrs.mobile.activities.BaseFragment
import org.openmrs.mobile.bundle.FormFieldsWrapper
import org.openmrs.mobile.databinding.FragmentFormDisplayBinding
import org.openmrs.mobile.utilities.ViewUtils.isEmpty
import java.util.ArrayList
import java.util.Calendar
import kotlin.math.roundToInt

@AndroidEntryPoint
class FormDisplayPageFragment : BaseFragment() {
    private var _binding: FragmentFormDisplayBinding? = null
    private val binding get() = _binding!!

    private val viewModel: FormDisplayPageViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentFormDisplayBinding.inflate(inflater, container, false)

        requireActivity().window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)

        createFormViews()

        return binding.root
    }

    private fun createFormViews() {
        val pageLabel = viewModel.page.label
        if (!pageLabel.isNullOrEmpty()) {
            val pageHeader = createSectionLayout(pageLabel, true)
            binding.sectionsParentContainer.addView(pageHeader)
        }
        viewModel.page.sections.forEach { addSection(it) }
    }

    private fun addSection(section: Section) {
        val sectionContainer: LinearLayout = createSectionLayout(section.label, false)
        binding.sectionsParentContainer.addView(sectionContainer)
        section.questions.forEach { addQuestion(it, sectionContainer) }
    }

    private fun createSectionLayout(sectionLabel: String?, isPageHeader: Boolean): LinearLayout {
        val sectionContainer = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            if (isPageHeader) {
                setPadding(0, 20, 0, 20)
            } else {
                setPadding(0, 40, 0, 20)
            }
        }
        val layoutParams = getAndAdjustLinearLayoutParams(sectionContainer)
        val labelTextView = TextView(activity).apply {
            text = sectionLabel
            setTextSize(TypedValue.COMPLEX_UNIT_SP, if (isPageHeader) 24f else 20f)
            setTextColor(ContextCompat.getColor(requireActivity(), R.color.primary))
            setTypeface(null, Typeface.BOLD)
            if (isPageHeader) {
                gravity = Gravity.START
                setPadding(20, 0, 0, 0)
            }
        }

        if (!sectionLabel.isNullOrEmpty()) {
            sectionContainer.addView(labelTextView, layoutParams)
            if (isPageHeader) {
                val lineView = View(activity).apply {
                    val lineParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 2).apply {
                        setMargins(0, 10, 0, 10)
                    }
                    this.layoutParams = lineParams
                    setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.grey))
                }
                sectionContainer.addView(lineView)
            }
        }
        return sectionContainer
    }

    private fun addQuestion(question: Question, sectionContainer: LinearLayout) {
        when (question.questionOptions?.rendering) {
            "group" -> {
                val questionGroupContainer: LinearLayout = createQuestionGroupLayout(question)
                sectionContainer.addView(questionGroupContainer)
                question.questions.forEach { subQuestion -> addQuestion(subQuestion, questionGroupContainer) }
            }
            "number" -> createAndAttachNumericQuestionEditText(question, sectionContainer)
            "select" -> createAndAttachSelectQuestionDropdown(question, sectionContainer)
            "radio" -> createAndAttachSelectQuestionRadioButton(question, sectionContainer)
            "checkbox" -> createAndAttachCheckboxQuestion(question, sectionContainer)
            "date" -> createAndAttachDateQuestion(question, sectionContainer)
            "text" -> createAndAttachTextQuestion(question, sectionContainer, false)
            "textarea" -> createAndAttachTextQuestion(question, sectionContainer, true)
            "ui-select-extended" -> createAndAttachExtendedSelectQuestion(question, sectionContainer)
        }
    }

    private fun createAndAttachExtendedSelectQuestion(question: Question, sectionContainer: LinearLayout) {
        val textView = TextView(activity).apply {
            setPadding(20, 10, 0, 0)
            text = getLabel(question)
            setTypeface(null, Typeface.BOLD)
        }

        val questionLinearLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        questionLinearLayout.addView(textView)

        val spinner = layoutInflater.inflate(R.layout.form_dropdown, null) as Spinner
        questionLinearLayout.addView(spinner)
        sectionContainer.addView(questionLinearLayout)

        val conceptUuid = question.questionOptions?.concept ?: ""
        val dataSource = question.questionOptions?.datasource
        val tag = dataSource?.config?.tag ?: ""

        val selectOneField = viewModel.findSelectOneFieldById(conceptUuid)
                ?: SelectOneField(emptyList(), conceptUuid).also { viewModel.selectOneFields.add(it) }

        viewModel.addSubscription(viewModel.getLocations(tag).subscribe({ locations ->
            var finalLocations = locations
            if (finalLocations.isEmpty() && tag == "Admission Location") {
                finalLocations = listOf("Inpatient ward", "Ward 1", "Ward 2", "Ward 3").map {
                    LocationEntity(it).apply { uuid = it }
                }
            }
            val answers = finalLocations.map {
                Answer().apply {
                    label = it.display ?: it.uuid
                    concept = it.uuid
                }
            }
            selectOneField.answerList = answers
            // See createAndAttachSelectQuestionDropdown for why this placeholder is required, not
            // cosmetic - Spinner always defaults to (and reports) position 0 as selected.
            val answerLabels = listOf(getString(R.string.select_an_option)) + answers.map { it.label ?: it.concept }

            activity?.runOnUiThread {
                spinner.adapter = ArrayAdapter(requireActivity(), android.R.layout.simple_spinner_item, answerLabels)
                if (selectOneField.chosenAnswerPosition != -1) {
                    spinner.setSelection(selectOneField.chosenAnswerPosition + 1)
                }
                setOnItemSelectedListenerWithPlaceholder(spinner, selectOneField)
            }
        }, { error ->
            // A location fetch failure (e.g. a server that 404s for this tag) must not crash the
            // whole form - just leave this dropdown empty rather than taking down the activity.
            error.printStackTrace()
        }))
    }

    private fun createQuestionGroupLayout(question: Question): LinearLayout {
        val questionGroupContainer = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.light_grey_for_solid))
            setPadding(40, 40, 40, 40)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(10, 20, 10, 20)
            }
            layoutParams = lp
        }

        val labelTextView = TextView(activity).apply {
            text = getLabel(question)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(ContextCompat.getColor(requireActivity(), R.color.dark_grey_8x))
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 20)
        }

        if (labelTextView.text.isNotEmpty()) {
            questionGroupContainer.addView(labelTextView)
        }
        return questionGroupContainer
    }

    private fun createAndAttachNumericQuestionEditText(question: Question, sectionContainer: LinearLayout) {
        val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        )
        sectionContainer.addView(generateTextView(getLabel(question), true))

        val inputField = viewModel.getOrCreateInputField(question.questionOptions!!.concept!!)
        if (!inputField.hasValue) {
            viewModel.autoFillAgeValue(question)?.let { inputField.value = it }
        }

        val options = question.questionOptions!!
        val ed = RangeEditText(activity).apply {
            name = getLabel(question).toString()
            hint = if (options.min != null && options.max != null) {
                "${getLabel(question)} (${options.min} - ${options.max})"
            } else {
                getLabel(question).toString()
            }
            isSingleLine = true
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            inputType = if (options.isAllowDecimal) {
                InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            } else {
                InputType.TYPE_CLASS_NUMBER
            }
            id = inputField.id
            options.min?.toDoubleOrNull()?.let { lowerlimit = it }
            options.max?.toDoubleOrNull()?.let { upperlimit = it }
        }
        if (inputField.hasValue) {
            ed.setText(inputField.value.toString())
            ed.setSelection(ed.length())
        }
        if (viewModel.isFixedPatientField(question)) {
            ed.isEnabled = false
            ed.isFocusable = false
            ed.setTextColor(Color.BLACK)
        }
        sectionContainer.addView(ed, lp)
        setOnTextChangedListener(ed, inputField)
    }

    private fun createAndAttachSelectQuestionDropdown(question: Question, sectionContainer: LinearLayout) {
        val textView = TextView(activity).apply {
            setPadding(20, 10, 0, 0)
            text = getLabel(question)
            setTypeface(null, Typeface.BOLD)
        }

        val questionLinearLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // A leading placeholder is required, not cosmetic: an Android Spinner always has *some*
        // item selected (defaulting to position 0) and fires onItemSelected for it as soon as the
        // listener is attached below, even though the user never touched the dropdown - without
        // this placeholder that default selection was the first real answer, silently recording
        // it as the user's choice. The web form's <select> starts blank the same way this does.
        val answerLabels = ArrayList<String?>()
        answerLabels.add(getString(R.string.select_an_option))
        question.questionOptions!!.answers!!.forEach {
            answerLabels.add(it.label ?: conceptLabelMapping[it.concept] ?: it.concept)
        }

        val spinner = layoutInflater.inflate(R.layout.form_dropdown, null) as Spinner
        spinner.adapter = ArrayAdapter(requireActivity(), android.R.layout.simple_spinner_item, answerLabels as List<Any?>)

        val spinnerField = SelectOneField(question.questionOptions!!.answers!!, question.questionOptions!!.concept!!)

        questionLinearLayout.addView(textView)
        questionLinearLayout.addView(spinner)
        sectionContainer.addView(questionLinearLayout)

        val selectOneField = viewModel.findSelectOneFieldById(spinnerField.concept)
        if (selectOneField != null) {
            if (selectOneField.chosenAnswerPosition != -1) {
                spinner.setSelection(selectOneField.chosenAnswerPosition + 1)
            }
            setOnItemSelectedListenerWithPlaceholder(spinner, selectOneField)
        } else {
            (viewModel.autoFillGenderAnswerIndex(question) ?: viewModel.autoFillGovernorateAnswerIndex(question))?.let {
                spinnerField.setAnswer(it)
                spinner.setSelection(it + 1)
            }
            setOnItemSelectedListenerWithPlaceholder(spinner, spinnerField)
            viewModel.selectOneFields.add(spinnerField)
        }

        if (viewModel.isFixedPatientField(question)) {
            spinner.isEnabled = false
            spinner.post { (spinner.selectedView as? TextView)?.setTextColor(Color.BLACK) }
        }
    }

    private fun createAndAttachSelectQuestionRadioButton(question: Question, sectionContainer: LinearLayout) {
        val textView = TextView(activity).apply {
            setPadding(20, 10, 0, 0)
            text = getLabel(question)
            setTypeface(null, Typeface.BOLD)
        }
        val radioGroup = RadioGroup(activity).apply {
            setPadding(20, 0, 0, 0)
        }
        question.questionOptions!!.answers!!.forEach {
            val radioButton = RadioButton(activity)
            radioButton.text = it.label ?: conceptLabelMapping[it.concept] ?: it.concept
            radioGroup.addView(radioButton)
        }
        val radioGroupField = SelectOneField(question.questionOptions!!.answers!!, question.questionOptions!!.concept!!)

        val questionLinearLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        questionLinearLayout.addView(textView)
        questionLinearLayout.addView(radioGroup)
        sectionContainer.addView(questionLinearLayout)

        val selectOneField = viewModel.findSelectOneFieldById(radioGroupField.concept)
        if (selectOneField != null) {
            if (selectOneField.chosenAnswerPosition != -1) {
                val radioButton = radioGroup.getChildAt(selectOneField.chosenAnswerPosition) as RadioButton
                radioButton.isChecked = true
            }
            setOnCheckedChangeListener(radioGroup, selectOneField)
        } else {
            (viewModel.autoFillGenderAnswerIndex(question) ?: viewModel.autoFillGovernorateAnswerIndex(question))?.let {
                radioGroupField.setAnswer(it)
                (radioGroup.getChildAt(it) as? RadioButton)?.isChecked = true
            }
            setOnCheckedChangeListener(radioGroup, radioGroupField)
            viewModel.selectOneFields.add(radioGroupField)
        }

        if (viewModel.isFixedPatientField(question)) {
            for (i in 0 until radioGroup.childCount) {
                val child = radioGroup.getChildAt(i)
                child.isEnabled = false
                (child as? RadioButton)?.setTextColor(Color.BLACK)
            }
        }
    }

    private fun createAndAttachCheckboxQuestion(question: Question, sectionContainer: LinearLayout) {
        sectionContainer.addView(generateTextView(getLabel(question), true))
        val selectMultipleField = SelectMultipleField(question.questionOptions!!.answers!!, question.questionOptions!!.concept!!)

        val existingField = viewModel.findSelectMultipleFieldById(selectMultipleField.concept)
        val fieldToUse = existingField ?: selectMultipleField.also { viewModel.selectMultipleFields.add(it) }

        question.questionOptions!!.answers!!.forEachIndexed { index, answer ->
            val checkBox = CheckBox(activity).apply {
                setPadding(20, 0, 0, 0)
                text = answer.label ?: conceptLabelMapping[answer.concept] ?: answer.concept
                isChecked = fieldToUse.isAnswerSelected(index)
                setOnCheckedChangeListener { _, isChecked ->
                    fieldToUse.setAnswer(index, isChecked)
                }
            }
            sectionContainer.addView(checkBox)
        }
    }

    private fun createAndAttachDateQuestion(question: Question, sectionContainer: LinearLayout) {
        sectionContainer.addView(generateTextView(getLabel(question), true))
        val dateField = DateField(question.questionOptions!!.concept!!)

        val existingField = viewModel.findDateFieldById(dateField.concept)
        val fieldToUse = existingField ?: dateField.also { viewModel.dateFields.add(it) }

        val dateEditText = EditText(activity).apply {
            isFocusable = false
            isClickable = true
            hint = "Select Date"
            setText(fieldToUse.date)
            setOnClickListener {
                val calendar = Calendar.getInstance()
                val year = calendar.get(Calendar.YEAR)
                val month = calendar.get(Calendar.MONTH)
                val day = calendar.get(Calendar.DAY_OF_MONTH)

                DatePickerDialog(requireContext(), { _, selectedYear, selectedMonth, selectedDay ->
                    val dateString = "$selectedDay/${selectedMonth + 1}/$selectedYear"
                    setText(dateString)
                    fieldToUse.date = dateString
                }, year, month, day).show()
            }
        }
        sectionContainer.addView(dateEditText)
    }

    private fun createAndAttachTextQuestion(question: Question, sectionContainer: LinearLayout, isTextArea: Boolean) {
        sectionContainer.addView(generateTextView(getLabel(question), true))
        val textField = TextField(question.questionOptions!!.concept!!)

        val existingField = viewModel.findTextFieldById(textField.concept)
        val fieldToUse = existingField ?: textField.also { viewModel.textFields.add(it) }

        if (fieldToUse.value.isNullOrBlank()) {
            viewModel.autoFillTextValue(question)?.let { fieldToUse.value = it }
        }

        val editText = EditText(activity).apply {
            hint = getLabel(question).toString()
            setText(fieldToUse.value)
            if (isTextArea) {
                minLines = 3
                gravity = Gravity.TOP or Gravity.START
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            } else if (viewModel.isPhoneNumberField(question)) {
                // Matches the web form's phone number field: digits only, exactly 10 of them
                // (see findInvalidPhoneNumberQuestions for the length check enforced at submit).
                isSingleLine = true
                inputType = InputType.TYPE_CLASS_NUMBER
                filters = arrayOf(InputFilter.LengthFilter(PHONE_NUMBER_LENGTH))
            } else {
                isSingleLine = true
                inputType = InputType.TYPE_CLASS_TEXT
            }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    fieldToUse.value = s.toString()
                }
            })
            if (viewModel.isFixedPatientField(question)) {
                isEnabled = false
                setTextColor(Color.BLACK)
            }
        }
        sectionContainer.addView(editText)
    }

    private fun setOnTextChangedListener(et: EditText, inputField: InputField) {
        et.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                // No override uses
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // No override uses
            }

            override fun afterTextChanged(s: Editable?) {
                inputField.value = if (!s.isNullOrEmpty()) s.toString().toDouble() else InputField.DEFAULT_VALUE
            }

        })
    }

    /**
     * Wires a spinner whose adapter has a leading "Select an option" placeholder at position 0
     * (see the two createAndAttach*Select* builders above): position 0 means no answer chosen,
     * position i>=1 is [SelectOneField.answerList]'s index i-1.
     */
    private fun setOnItemSelectedListenerWithPlaceholder(spinner: Spinner, spinnerField: SelectOneField) {
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(adapterView: AdapterView<*>?, view: View, i: Int, l: Long) {
                spinnerField.setAnswer(i - 1)
            }

            override fun onNothingSelected(adapterView: AdapterView<*>?) {
                spinnerField.setAnswer(-1)
            }
        }
    }

    private fun setOnCheckedChangeListener(radioGroup: RadioGroup, radioGroupField: SelectOneField) {
        radioGroup.setOnCheckedChangeListener { radioGroup1: RadioGroup, i: Int ->
            val radioButton = radioGroup1.findViewById<View>(i)
            val idx = radioGroup1.indexOfChild(radioButton)
            radioGroupField.setAnswer(idx)
        }
    }

    private fun getAndAdjustLinearLayoutParams(linearLayout: LinearLayout): LinearLayout.LayoutParams {
        val layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
        )
        linearLayout.orientation = LinearLayout.VERTICAL
        val margin = TypedValue
                .applyDimension(TypedValue.COMPLEX_UNIT_DIP, 5f, resources.displayMetrics)
                .roundToInt()
        layoutParams.setMargins(margin, margin, margin, margin)
        return layoutParams
    }

    private fun generateTextView(text: CharSequence?, isBold: Boolean): View {
        val layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        layoutParams.setMargins(20, 10, 0, 0)
        val textView = TextView(activity)
        textView.text = text
        if (isBold) {
            textView.setTypeface(null, Typeface.BOLD)
        }
        textView.layoutParams = layoutParams
        return textView
    }

    fun checkInputFields(): Boolean {
        var allEmpty = true
        var valid = true
        for (field in viewModel.inputFields) {
            val ed = requireActivity().findViewById<View>(field.id) as? RangeEditText ?: continue
            if (!isEmpty(ed)) {
                allEmpty = false
                if (!ed.validInput || ed.outOfRange) {
                    ed.setTextColor(ContextCompat.getColor(requireContext(), R.color.red))
                    valid = false
                }
            }
        }
        for (radioGroupField in viewModel.selectOneFields) {
            if (radioGroupField.chosenAnswer != null) allEmpty = false
        }
        for (checkboxField in viewModel.selectMultipleFields) {
            if (checkboxField.selectedAnswers.isNotEmpty()) allEmpty = false
        }
        for (dateField in viewModel.dateFields) {
            if (!dateField.date.isNullOrEmpty()) allEmpty = false
        }
        for (textField in viewModel.textFields) {
            if (!textField.value.isNullOrBlank()) allEmpty = false
        }

        if (allEmpty) ToastUtil.error(getString(R.string.all_fields_empty_error_message))
        else if (!valid) ToastUtil.error(getString(R.string.invalid_inputs))

        return !allEmpty && valid
    }

    /**
     * Checks every question on this page marked `"required": true` by the form schema against
     * the field the user actually filled in (matched by concept, the same lookup
     * [addQuestion]'s builders use). [checkInputFields] only catches an entirely blank page or an
     * out-of-range number - it never looks at `Question.isRequired`, so a form with nine required
     * questions and one filled-in optional one used to submit successfully with the nine blank.
     *
     * @return the labels of required questions still unanswered, empty when the page is complete
     */
    fun findUnansweredRequiredQuestions(): List<String> {
        val unanswered = mutableListOf<String>()
        fun visit(questions: List<Question>) {
            questions.forEach { question ->
                if (question.questionOptions?.rendering == "group") {
                    visit(question.questions)
                } else if (question.isRequired && !isQuestionAnswered(question)) {
                    unanswered.add(question.label ?: question.id ?: "")
                }
            }
        }
        viewModel.page.sections.forEach { visit(it.questions) }
        return unanswered
    }

    /**
     * Checks every phone number question on this page whose field has a value against
     * [PHONE_NUMBER_REGEX] - a blank, optional phone field is fine (that's [checkInputFields]'s
     * job), but a filled-in one that isn't exactly 10 digits is not, matching the web form.
     *
     * @return the labels of phone questions with an invalid value, empty when all are valid
     */
    fun findInvalidPhoneNumberQuestions(): List<String> {
        val invalid = mutableListOf<String>()
        fun visit(questions: List<Question>) {
            questions.forEach { question ->
                if (question.questionOptions?.rendering == "group") {
                    visit(question.questions)
                    return@forEach
                }
                if (!viewModel.isPhoneNumberField(question)) return@forEach
                val concept = question.questionOptions?.concept ?: return@forEach
                val value = viewModel.findTextFieldById(concept)?.value?.trim()
                if (!value.isNullOrEmpty() && !PHONE_NUMBER_REGEX.matches(value)) {
                    invalid.add(question.label ?: question.id ?: "")
                }
            }
        }
        viewModel.page.sections.forEach { visit(it.questions) }
        return invalid
    }

    private fun isQuestionAnswered(question: Question): Boolean {
        val concept = question.questionOptions?.concept ?: return true
        return when (question.questionOptions?.rendering) {
            "number" -> viewModel.findInputFieldByConcept(concept)?.hasValue == true
            "select", "radio", "ui-select-extended" -> viewModel.findSelectOneFieldById(concept)?.chosenAnswer != null
            "checkbox" -> viewModel.findSelectMultipleFieldById(concept)?.selectedAnswers?.isNotEmpty() == true
            "date" -> !viewModel.findDateFieldById(concept)?.date.isNullOrEmpty()
            "text", "textarea" -> !viewModel.findTextFieldById(concept)?.value.isNullOrBlank()
            else -> true
        }
    }

    fun getInputFields() = viewModel.inputFields

    fun getSelectOneFields() = viewModel.selectOneFields

    fun getSelectMultipleFields() = viewModel.selectMultipleFields

    fun getDateFields() = viewModel.dateFields

    fun getTextFields() = viewModel.textFields

    private val conceptLabelMapping = mapOf(
        "CIEL:169405" to "Inpatient disposition construct",
        "CIEL:169402" to "Inpatient patient disposition",
        "CIEL:168619" to "Admit to hospital",
        "CIEL:169403" to "Admitted to location (text/code)"
    )

    private fun getLabel(question: Question): CharSequence {
        val concept = question.questionOptions?.concept
        val label = question.label ?: conceptLabelMapping[concept] ?: formatId(question.id) ?: concept ?: ""
        if (!question.isRequired) return label

        val builder = SpannableStringBuilder(label)
        builder.append(" *")
        builder.setSpan(ForegroundColorSpan(Color.RED), builder.length - 1, builder.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        return builder
    }

    private fun formatId(id: String?): String? {
        if (id == null) return null
        val result = StringBuilder()
        for (i in id.indices) {
            val c = id[i]
            if (i == 0) {
                result.append(c.uppercaseChar())
            } else if (c.isUpperCase()) {
                result.append(" ")
                result.append(c.lowercaseChar())
            } else {
                result.append(c)
            }
        }
        return result.toString()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val PHONE_NUMBER_LENGTH = 10
        private val PHONE_NUMBER_REGEX = Regex("^\\d{$PHONE_NUMBER_LENGTH}$")

        fun newInstance(page: Page, formFieldsWrapper: FormFieldsWrapper?, patientId: Long) = FormDisplayPageFragment().apply {
            arguments = bundleOf(
                    FORM_PAGE_BUNDLE to page,
                    FORM_FIELDS_BUNDLE to formFieldsWrapper,
                    PATIENT_ID_BUNDLE to patientId
            )
        }
    }
}
