package org.openmrs.mobile.activities.groupentry

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.openmrs.android_sdk.library.models.Patient
import org.openmrs.mobile.R

/** Group Entry's attendee list: one checkable row per patient with an active visit. */
class GroupEntryPatientAdapter(
    private val isSelected: (Patient) -> Boolean,
    private val onToggle: (Patient, Boolean) -> Unit
) : RecyclerView.Adapter<GroupEntryPatientAdapter.ViewHolder>() {

    var items: List<Patient> = emptyList()
        private set

    /** Rows can't be toggled while the batch is saving. */
    var isLocked = false

    fun updateList(patients: List<Patient>) {
        items = patients
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.row_group_entry_patient, parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val patient = items[position]
        holder.name.text = patient.name?.nameString
        holder.identifier.text = patient.identifier?.identifier?.let { "#$it" }
        holder.check.isChecked = isSelected(patient)
        holder.itemView.setOnClickListener {
            if (isLocked) return@setOnClickListener
            val selected = !holder.check.isChecked
            holder.check.isChecked = selected
            onToggle(patient, selected)
        }
    }

    override fun getItemCount() = items.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val check: CheckBox = itemView.findViewById(R.id.groupEntryPatientCheck)
        val name: TextView = itemView.findViewById(R.id.groupEntryPatientName)
        val identifier: TextView = itemView.findViewById(R.id.groupEntryPatientIdentifier)
    }
}
