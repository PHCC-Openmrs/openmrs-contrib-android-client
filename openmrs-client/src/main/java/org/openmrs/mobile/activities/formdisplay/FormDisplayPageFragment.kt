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
import com.openmrs.android_sdk.utilities.DateField
import com.openmrs.android_sdk.utilities.InputField
import com.openmrs.android_sdk.utilities.RangeEditText
import com.openmrs.android_sdk.utilities.SelectMultipleField
import com.openmrs.android_sdk.utilities.SelectOneField
import com.openmrs.android_sdk.utilities.TextField
import com.openmrs.android_sdk.utilities.ToastUtil
import dagger.hilt.android.AndroidEntryPoint
// import org.adw.library.widgets.discreteseekbar.DiscreteSeekBar
import com.google.android.material.slider.Slider
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

        viewModel.addSubscription(viewModel.getLocations(tag).subscribe { locations ->
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
            val answerLabels = answers.map { it.label ?: it.concept }

            activity?.runOnUiThread {
                spinner.adapter = ArrayAdapter(requireActivity(), android.R.layout.simple_spinner_item, answerLabels)
                if (selectOneField.chosenAnswerPosition != -1) {
                    spinner.setSelection(selectOneField.chosenAnswerPosition)
                }
                setOnItemSelectedListener(spinner, selectOneField)
            }
        })
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

        val options = question.questionOptions!!
        if (options.min != null && options.max != null && !options.isAllowDecimal) {
            val from = options.min!!.toFloat()
            val to = options.max!!.toFloat()
            // Clamp value to [from, to] to prevent Slider crash if initial value is -1.0
            val currentVal = if (inputField.value.toFloat() < from) from else if (inputField.value.toFloat() > to) to else inputField.value.toFloat()

            val slider = Slider(requireContext()).apply {
                valueFrom = from
                valueTo = to
                stepSize = 1.0f
                id = inputField.id
                value = currentVal
            }
            sectionContainer.addView(slider, lp)
            setOnSliderChangeListener(slider, inputField)
        } else {
            val ed = RangeEditText(activity).apply {
                name = getLabel(question).toString()
                hint = getLabel(question).toString()
                isSingleLine = true
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                inputType = if (options.isAllowDecimal) {
                    InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                } else {
                    InputType.TYPE_CLASS_NUMBER
                }
                id = inputField.id
            }
            if (inputField.hasValue) {
                ed.setText(inputField.value.toString())
                ed.setSelection(ed.length())
            }
            sectionContainer.addView(ed, lp)
            setOnTextChangedListener(ed, inputField)
        }
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

        val answerLabels = ArrayList<String?>()
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
                spinner.setSelection(selectOneField.chosenAnswerPosition)
            }
            setOnItemSelectedListener(spinner, selectOneField)
        } else {
            setOnItemSelectedListener(spinner, spinnerField)
            viewModel.selectOneFields.add(spinnerField)
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
            setOnCheckedChangeListener(radioGroup, radioGroupField)
            viewModel.selectOneFields.add(radioGroupField)
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

        val editText = EditText(activity).apply {
            hint = getLabel(question).toString()
            setText(fieldToUse.value)
            if (isTextArea) {
                minLines = 3
                gravity = Gravity.TOP or Gravity.START
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
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
        }
        sectionContainer.addView(editText)
    }

    private fun setOnSliderChangeListener(slider: Slider, inputField: InputField) {
        slider.addOnChangeListener { _, value, _ ->
            inputField.value = value.toDouble()
        }
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

    private fun setOnItemSelectedListener(spinner: Spinner, spinnerField: SelectOneField) {
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(adapterView: AdapterView<*>?, view: View, i: Int, l: Long) {
                spinnerField.setAnswer(i)
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
            try {
                val ed: RangeEditText = requireActivity().findViewById(field.id)
                if (!isEmpty(ed)) {
                    allEmpty = false
                    if (!ed.validInput || ed.outOfRange) {
                        ed.setTextColor(ContextCompat.getColor(requireContext(), R.color.red))
                        valid = false
                    }
                }
            } catch (e: Exception) {
                // Check if it's a Slider
                val view = requireActivity().findViewById<View>(field.id)
                if (view is Slider) {
                    if (view.value > view.valueFrom) allEmpty = false
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
        fun newInstance(page: Page, formFieldsWrapper: FormFieldsWrapper?) = FormDisplayPageFragment().apply {
            arguments = bundleOf(
                    FORM_PAGE_BUNDLE to page,
                    FORM_FIELDS_BUNDLE to formFieldsWrapper
            )
        }
    }
}
