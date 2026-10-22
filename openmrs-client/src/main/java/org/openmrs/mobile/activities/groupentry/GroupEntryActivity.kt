package org.openmrs.mobile.activities.groupentry

import android.app.Activity
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.LinearLayoutManager
import com.openmrs.android_sdk.library.models.Patient
import com.openmrs.android_sdk.library.models.Result
import com.openmrs.android_sdk.utilities.ApplicationConstants.BundleKeys.FORM_NAME
import com.openmrs.android_sdk.utilities.ToastUtil
import dagger.hilt.android.AndroidEntryPoint
import org.openmrs.mobile.R
import org.openmrs.mobile.activities.ACBaseActivity
import org.openmrs.mobile.databinding.ActivityGroupEntryBinding
import org.openmrs.mobile.utilities.makeGone
import org.openmrs.mobile.utilities.makeVisible

/**
 * Group Entry, second step: the session details were filled in on the form screen
 * (FormDisplayActivity in group mode, which starts this for a result) - here the user ticks
 * the attendees and saves the form for all of them at once. Finishes with [Activity.RESULT_OK]
 * once anything was saved, so the form screen closes too.
 */
@AndroidEntryPoint
class GroupEntryActivity : ACBaseActivity() {
    private lateinit var binding: ActivityGroupEntryBinding
    private val viewModel: GroupEntryViewModel by viewModels()

    private val adapter = GroupEntryPatientAdapter(
        isSelected = { it.id in viewModel.selectedIds },
        onToggle = { patient, selected -> viewModel.setSelected(patient, selected) }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGroupEntryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.run {
            elevation = 0f
            setTitle(R.string.group_entry_title)
            subtitle = intent.getStringExtra(FORM_NAME)
        }

        with(binding) {
            groupEntryPatients.layoutManager = LinearLayoutManager(this@GroupEntryActivity)
            groupEntryPatients.adapter = adapter
            groupEntrySearch.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String): Boolean {
                    groupEntrySearch.clearFocus()
                    return true
                }

                override fun onQueryTextChange(query: String): Boolean {
                    viewModel.search(query)
                    return true
                }
            })
            groupEntrySelectAll.setOnClickListener {
                viewModel.selectAllListed(adapter.items)
                adapter.notifyDataSetChanged()
            }
            groupEntryClear.setOnClickListener {
                viewModel.clearSelection()
                adapter.notifyDataSetChanged()
            }
            groupEntrySave.setOnClickListener { confirmSave() }
        }

        // Leaving mid-batch would hide how far it got - the summary at the end is the record.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (viewModel.isSubmitting) {
                    ToastUtil.notify(getString(R.string.group_entry_wait_for_save))
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        observeViewModel()
    }

    override fun onSupportNavigateUp(): Boolean {
        if (viewModel.isSubmitting) {
            ToastUtil.notify(getString(R.string.group_entry_wait_for_save))
            return false
        }
        finish()
        return true
    }

    private fun observeViewModel() {
        viewModel.result.observe(this) { result ->
            when (result) {
                is Result.Loading -> showLoading()
                is Result.Success -> showPatients(result.data)
                is Result.Error -> showPatients(emptyList())
                else -> Unit
            }
        }
        viewModel.selectedCount.observe(this) { count ->
            binding.groupEntrySelectedCount.text = getString(R.string.group_entry_selected_count, count)
            binding.groupEntrySave.text = getString(R.string.group_entry_save_button, count)
            binding.groupEntrySave.isEnabled = count > 0 && !viewModel.isSubmitting
        }
        viewModel.submission.observe(this) { submission ->
            when (submission) {
                is GroupEntryViewModel.Submission.Running -> showProgress(submission.done, submission.total)
                is GroupEntryViewModel.Submission.Done -> showSummary(submission.summary)
            }
        }
    }

    private fun showLoading() = with(binding) {
        groupEntryLoading.makeVisible()
        groupEntryPatients.makeGone()
        groupEntryEmpty.makeGone()
    }

    private fun showPatients(patients: List<Patient>) = with(binding) {
        groupEntryLoading.makeGone()
        adapter.updateList(patients)
        if (patients.isEmpty()) {
            groupEntryEmpty.text = if (viewModel.query.isNullOrBlank()) getString(R.string.group_entry_no_patients)
            else getString(R.string.search_patient_no_result_for_query, viewModel.query)
            groupEntryEmpty.makeVisible()
            groupEntryPatients.makeGone()
        } else {
            groupEntryEmpty.makeGone()
            groupEntryPatients.makeVisible()
        }
    }

    private fun confirmSave() {
        val count = viewModel.selectedIds.size
        if (count == 0) return
        AlertDialog.Builder(this, R.style.AlertDialogTheme)
            .setTitle(getString(R.string.group_entry_confirm_title, count))
            .setMessage(R.string.group_entry_confirm_message)
            .setPositiveButton(R.string.group_entry_confirm_save) { dialog, _ ->
                dialog.dismiss()
                viewModel.submit()
            }
            .setNegativeButton(R.string.dialog_button_cancel) { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun showProgress(done: Int, total: Int) = with(binding) {
        adapter.isLocked = true
        groupEntrySearch.isEnabled = false
        groupEntrySelectAll.isEnabled = false
        groupEntryClear.isEnabled = false
        groupEntrySave.isEnabled = false
        groupEntryProgressPanel.makeVisible()
        groupEntryProgressBar.max = total
        groupEntryProgressBar.progress = done
        groupEntryProgressText.text = getString(R.string.group_entry_progress, done, total)
    }

    private fun showSummary(summary: GroupEntryViewModel.Summary) {
        binding.groupEntryProgressPanel.makeGone()
        val lines = mutableListOf<String>()
        if (summary.uploaded > 0) lines += getString(R.string.group_entry_summary_uploaded, summary.uploaded)
        if (summary.savedOffline > 0) lines += getString(R.string.group_entry_summary_offline, summary.savedOffline)
        if (summary.withoutPhone > 0) lines += getString(R.string.group_entry_summary_without_phone, summary.withoutPhone)
        if (summary.failed.isNotEmpty()) {
            lines += getString(R.string.group_entry_summary_failed, summary.failed.size, summary.failed.joinToString(", "))
        }
        val anySaved = summary.uploaded + summary.savedOffline > 0

        AlertDialog.Builder(this, R.style.AlertDialogTheme)
            .setTitle(R.string.group_entry_summary_title)
            .setMessage(lines.joinToString("\n\n"))
            .setCancelable(false)
            .setPositiveButton(R.string.dialog_button_ok) { dialog, _ ->
                dialog.dismiss()
                if (anySaved) setResult(Activity.RESULT_OK)
                finish()
            }
            .show()
    }
}
