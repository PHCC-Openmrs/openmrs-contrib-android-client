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
package org.openmrs.mobile.activities.patientdashboard.appointments

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.tabs.TabLayout
import com.openmrs.android_sdk.library.models.Appointment
import com.openmrs.android_sdk.library.models.OperationType.AppointmentCancelling
import com.openmrs.android_sdk.library.models.OperationType.PatientAppointmentsFetching
import com.openmrs.android_sdk.library.models.Result
import com.openmrs.android_sdk.utilities.ApplicationConstants.BundleKeys.APPOINTMENT_BUNDLE
import com.openmrs.android_sdk.utilities.ApplicationConstants.BundleKeys.PATIENT_ID_BUNDLE
import com.openmrs.android_sdk.utilities.ApplicationConstants.Privileges.MANAGE_APPOINTMENTS
import com.openmrs.android_sdk.utilities.ToastUtil.error
import dagger.hilt.android.AndroidEntryPoint
import org.openmrs.mobile.R
import org.openmrs.mobile.activities.BaseFragment
import org.openmrs.mobile.activities.addeditappointment.AddEditAppointmentActivity
import org.openmrs.mobile.databinding.FragmentPatientAppointmentsBinding
import org.openmrs.mobile.utilities.makeGone
import org.openmrs.mobile.utilities.makeVisible
import java.util.Calendar

/**
 * Mirrors O3's patient-chart Appointments widget: an Upcoming/Today/Past switcher over a single
 * fetched list, split client-side exactly like usePatientAppointments (excludes Cancelled,
 * buckets by calendar day against "now").
 */
@AndroidEntryPoint
class PatientAppointmentsFragment : BaseFragment() {
    private var _binding: FragmentPatientAppointmentsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PatientDashboardAppointmentsViewModel by viewModels()

    private lateinit var adapter: PatientAppointmentsRecyclerViewAdapter
    private var allAppointments: List<Appointment> = emptyList()
    private var selectedTab = AppointmentTab.UPCOMING

    private enum class AppointmentTab { UPCOMING, TODAY, PAST }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPatientAppointmentsBinding.inflate(inflater, null, false)

        setupAdapter()
        setupTabs()
        setupObserver()

        return binding.root
    }

    override fun onResume() {
        super.onResume()
        viewModel.fetchAppointments()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        if (hasPrivilege(MANAGE_APPOINTMENTS)) {
            inflater.inflate(R.menu.patient_appointments_tab_menu, menu)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.actionAddAppointment) {
            launchAppointmentForm()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun setupAdapter() {
        adapter = PatientAppointmentsRecyclerViewAdapter(
            emptyList(),
            onCancelClicked = { appointment -> confirmCancel(appointment) },
            onEditClicked = { appointment -> launchAppointmentForm(appointment) }
        )
        with(binding.patientAppointmentsRecyclerView) {
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(activity)
            adapter = this@PatientAppointmentsFragment.adapter
        }
    }

    /** Launches the Add/Edit appointment screen; [appointment] non-null means edit an existing one. */
    private fun launchAppointmentForm(appointment: Appointment? = null) {
        val patientId = requireArguments().getLong(PATIENT_ID_BUNDLE)
        startActivity(Intent(requireContext(), AddEditAppointmentActivity::class.java).apply {
            putExtra(PATIENT_ID_BUNDLE, patientId)
            putExtra(APPOINTMENT_BUNDLE, appointment)
        })
    }

    private fun setupTabs() {
        with(binding.appointmentsTabLayout) {
            addTab(newTab().setText(R.string.appointments_tab_upcoming))
            addTab(newTab().setText(R.string.appointments_tab_today))
            addTab(newTab().setText(R.string.appointments_tab_past))
            addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab) {
                    selectedTab = AppointmentTab.values()[tab.position]
                    showAppointmentsForSelectedTab()
                }

                override fun onTabUnselected(tab: TabLayout.Tab) = Unit
                override fun onTabReselected(tab: TabLayout.Tab) = Unit
            })
        }
    }

    private fun setupObserver() {
        viewModel.result.observe(viewLifecycleOwner, Observer { result ->
            when (result) {
                is Result.Success -> if (result.operationType == PatientAppointmentsFetching) {
                    allAppointments = result.data
                    showAppointmentsForSelectedTab()
                }
                is Result.Error -> if (result.operationType == AppointmentCancelling) {
                    error(getString(R.string.appointment_cancel_error))
                } else if (result.operationType == PatientAppointmentsFetching) {
                    allAppointments = emptyList()
                    showAppointmentsForSelectedTab()
                }
                else -> Unit
            }
        })
    }

    private fun showAppointmentsForSelectedTab() {
        val appointments = splitByTab(allAppointments).getValue(selectedTab)
        adapter.updateList(appointments)
        with(binding) {
            if (appointments.isEmpty()) {
                patientAppointmentsRecyclerView.makeGone()
                emptyAppointmentsList.makeVisible()
            } else {
                patientAppointmentsRecyclerView.makeVisible()
                emptyAppointmentsList.makeGone()
            }
        }
    }

    /** Mirrors O3's usePatientAppointments split: excludes Cancelled, buckets by calendar day. */
    private fun splitByTab(appointments: List<Appointment>): Map<AppointmentTab, List<Appointment>> {
        val startOfToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val endOfToday = startOfToday + DAY_IN_MILLIS - 1

        val active = appointments.filter { it.status != Appointment.Status.CANCELLED && it.startDateTime != null }
        return mapOf(
            AppointmentTab.PAST to active.filter { it.startDateTime!! < startOfToday }
                .sortedByDescending { it.startDateTime },
            AppointmentTab.TODAY to active.filter { it.startDateTime!! in startOfToday..endOfToday }
                .sortedBy { it.startDateTime },
            AppointmentTab.UPCOMING to active.filter { it.startDateTime!! > endOfToday }
                .sortedBy { it.startDateTime }
        )
    }

    private fun confirmCancel(appointment: Appointment) {
        val appointmentUuid = appointment.uuid ?: return
        AlertDialog.Builder(requireContext(), R.style.AlertDialogTheme)
            .setTitle(R.string.appointment_cancel_confirm_title)
            .setMessage(R.string.appointment_cancel_confirm_message)
            .setPositiveButton(R.string.mark_patient_deceased_proceed) { dialog, _ ->
                dialog.dismiss()
                viewModel.cancelAppointment(appointmentUuid)
            }
            .setNegativeButton(R.string.dialog_button_cancel) { dialog, _ -> dialog.dismiss() }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val DAY_IN_MILLIS = 24 * 60 * 60 * 1000L

        fun newInstance(patientId: Long): PatientAppointmentsFragment {
            val fragment = PatientAppointmentsFragment()
            fragment.arguments = bundleOf(Pair(PATIENT_ID_BUNDLE, patientId))
            return fragment
        }
    }
}
