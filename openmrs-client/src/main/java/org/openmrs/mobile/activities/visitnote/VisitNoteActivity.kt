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
package org.openmrs.mobile.activities.visitnote

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.viewModels
import androidx.lifecycle.Observer
import com.openmrs.android_sdk.library.api.repository.DiagnosisRepository
import com.openmrs.android_sdk.library.models.ConceptSearchResult
import com.openmrs.android_sdk.library.models.ResultType
import com.openmrs.android_sdk.utilities.ToastUtil
import dagger.hilt.android.AndroidEntryPoint
import org.openmrs.mobile.R
import org.openmrs.mobile.activities.ACBaseActivity
import org.openmrs.mobile.databinding.ActivityVisitNoteBinding
import org.openmrs.mobile.utilities.makeGone
import org.openmrs.mobile.utilities.makeVisible
import org.openmrs.mobile.utilities.observeOnce

@AndroidEntryPoint
class VisitNoteActivity : ACBaseActivity() {

    private lateinit var binding: ActivityVisitNoteBinding
    private val viewModel: VisitNoteViewModel by viewModels()

    private val searchHandler = Handler(Looper.getMainLooper())
    private var pendingSearch: Runnable? = null
    private var latestSuggestions: List<ConceptSearchResult> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVisitNoteBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.run {
            setDisplayHomeAsUpEnabled(true)
            setTitle(R.string.visit_note)
        }

        setupDiagnosisSearch()
        setupObservers()

        binding.saveButton.setOnClickListener { save() }
    }

    private fun setupDiagnosisSearch() {
        binding.diagnosisSearchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                pendingSearch?.let { searchHandler.removeCallbacks(it) }
                val query = s?.toString().orEmpty()
                val runnable = Runnable { viewModel.search(query) }
                pendingSearch = runnable
                searchHandler.postDelayed(runnable, SEARCH_DEBOUNCE_MS)
            }
        })

        binding.diagnosisSuggestionsListView.setOnItemClickListener { _, _, position, _ ->
            latestSuggestions.getOrNull(position)?.let {
                viewModel.addDiagnosis(it)
                binding.diagnosisSearchEditText.setText("")
            }
        }
    }

    private fun setupObservers() {
        viewModel.searchResults.observe(this, Observer { results ->
            latestSuggestions = results
            if (results.isEmpty()) {
                binding.diagnosisSuggestionsListView.makeGone()
            } else {
                binding.diagnosisSuggestionsListView.adapter = ArrayAdapter(
                    this,
                    android.R.layout.simple_list_item_1,
                    results.map { it.display ?: it.uuid.orEmpty() }
                )
                binding.diagnosisSuggestionsListView.makeVisible()
            }
        })

        viewModel.selectedDiagnosesList.observe(this, Observer { diagnoses -> renderSelectedDiagnoses(diagnoses) })
    }

    private fun renderSelectedDiagnoses(diagnoses: List<SelectedDiagnosis>) {
        binding.selectedDiagnosesContainer.removeAllViews()
        diagnoses.forEachIndexed { index, diagnosis ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 12, 0, 12)
            }

            val label = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                val rankLabel = getString(
                    if (index == 0) R.string.visit_note_primary else R.string.visit_note_secondary
                )
                text = "${diagnosis.display} ($rankLabel)"
            }
            row.addView(label)

            val certaintyToggle = TextView(this).apply {
                setPadding(20, 0, 20, 0)
                text = getString(
                    if (diagnosis.certainty == DiagnosisRepository.CERTAINTY_CONFIRMED) {
                        R.string.visit_note_confirmed
                    } else {
                        R.string.visit_note_presumed
                    }
                )
                setOnClickListener { viewModel.toggleCertaintyAt(index) }
            }
            row.addView(certaintyToggle)

            val removeButton = TextView(this).apply {
                text = "✕"
                setPadding(20, 0, 0, 0)
                setOnClickListener { viewModel.removeDiagnosisAt(index) }
            }
            row.addView(removeButton)

            binding.selectedDiagnosesContainer.addView(row)
        }
    }

    private fun save() {
        val noteText = binding.noteEditText.text?.toString().orEmpty()
        showLoading(true)
        viewModel.save(noteText).observeOnce(this, Observer { result ->
            showLoading(false)
            when (result) {
                ResultType.EncounterSubmissionSuccess -> {
                    ToastUtil.success(getString(R.string.visit_note_saved_successfully))
                    finish()
                }
                else -> {
                    val message = if (viewModel.lastSaveError is NoProviderLinkedException) {
                        getString(R.string.visit_note_no_provider_error)
                    } else {
                        getString(R.string.visit_note_save_error)
                    }
                    ToastUtil.error(message)
                }
            }
        })
    }

    private fun showLoading(loading: Boolean) = with(binding) {
        if (loading) {
            transparentScreen.makeVisible()
            progressBar.makeVisible()
        } else {
            transparentScreen.makeGone()
            progressBar.makeGone()
        }
    }

    companion object {
        private const val SEARCH_DEBOUNCE_MS = 350L
    }
}
