
package org.openmrs.mobile.activities.formlist

import com.openmrs.android_sdk.library.OpenmrsAndroid
import com.openmrs.android_sdk.library.PrivilegeChecker
import com.openmrs.android_sdk.library.api.repository.FormRepository
import com.openmrs.android_sdk.library.dao.EncounterDAO
import com.openmrs.android_sdk.library.databases.entities.FormResourceEntity
import com.openmrs.android_sdk.library.models.EncounterType
import com.openmrs.android_sdk.library.models.FormData
import com.openmrs.android_sdk.utilities.ApplicationConstants.Privileges.GET_ENCOUNTER_ROLES
import com.openmrs.android_sdk.utilities.execute
import dagger.hilt.android.lifecycle.HiltViewModel
import org.json.JSONException
import org.json.JSONObject
import org.openmrs.mobile.activities.BaseViewModel
import rx.android.schedulers.AndroidSchedulers
import javax.inject.Inject
import java.io.IOException
import java.nio.charset.StandardCharsets
import kotlin.math.abs

@HiltViewModel
class FormListViewModel @Inject constructor(
        private val encounterDAO: EncounterDAO,
        private val formRepository: FormRepository
) : BaseViewModel<Array<String>>() {

    private val formResourceList = mutableListOf<FormResourceEntity>()

    init {
        loadFormResourceList()
    }

    private fun loadFormResourceList() {
        setLoading()
        addSubscription(formRepository.fetchFormResourceList()
                .map {
                    val currentForms = mutableListOf<FormResourceEntity>()
                    for (formResource in it) {
                        var valueRefString: String? = null
                        for (resource in formResource.resources) {
                            if (resource.name == "json" || resource.name == "JSON schema") {
                                val value = resource.valueReference
                                if (!value.isNullOrBlank() && value.trim().startsWith("{") && value.trim().endsWith("}")) {
                                    valueRefString = value
                                }
                            }
                        }
                        if (!valueRefString.isNullOrBlank()) {
                            currentForms.add(formResource)
                        } else {
                            val formData = createFormDataFromAsset(formResource.name?.toLowerCase() ?: "")
                            formData?.let { data ->
                                formRepository.createForm(formResource.uuid!!, data).execute()
                                val resource = FormResourceEntity()
                                resource.name = "json"
                                resource.valueReference = data.valueReference
                                formResource.resources = listOf(resource)
                                currentForms.add(formResource)
                            }
                        }
                    }

                    // Inject Virtual Forms for O3 compatibility
                    injectVirtualForm(currentForms, EncounterType.VITALS, "vitals1.json")
                    // Visit Note is a native screen (VisitNoteActivity), not a JSON-schema form,
                    // so it needs a list entry but no asset-backed form fields.
                    injectVirtualForm(currentForms, EncounterType.VISIT_NOTE, null)

                    // Admission and Visit Note both resolve an encounter role (to attribute the
                    // encounter to a provider) before they can be submitted - hide them if the
                    // user's role lacks that server privilege, rather than letting the form open
                    // and then fail on submit.
                    val visibleForms = if (PrivilegeChecker.hasPrivilege(GET_ENCOUNTER_ROLES)) {
                        currentForms
                    } else {
                        currentForms.filterNot { formResource ->
                            resolveEncounterName(formResource) in FORMS_REQUIRING_ENCOUNTER_ROLE
                        }
                    }

                    formResourceList.clear()
                    formResourceList.addAll(visibleForms)

                    val forms = ArrayList<String>(formResourceList.size)
                    for (form in formResourceList) forms += form.name!!

                    return@map forms.toTypedArray()
                }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ setContent(it) }, { setError(it) })
        )
    }

    private fun injectVirtualForm(list: MutableList<FormResourceEntity>, formName: String, assetName: String?) {
        val alreadyExists = list.any { it.name?.contains(formName, ignoreCase = true) == true }
        if (alreadyExists) return

        val virtualForm = FormResourceEntity()
        virtualForm.name = formName
        virtualForm.uuid = "virtual-" + abs(formName.hashCode()).toString()

        val encounterType = try { encounterDAO.getEncounterTypeByFormName(formName) } catch(e: Exception) { null }
        virtualForm.encounterTypeUuid = encounterType?.uuid ?: when(formName) {
            EncounterType.VITALS -> "67a71486-1a54-468f-ac3e-7091a9a79584"
            EncounterType.VISIT_NOTE -> "d7151f82-c1f3-4152-a605-2f9ea7414a79"
            else -> null
        }

        if (assetName == null) {
            // No JSON schema needed - this entry is handled by a native screen.
            list.add(virtualForm)
            return
        }

        val formData = parseFormDataFromAsset(assetName)
        formData?.let {
            val resource = FormResourceEntity()
            resource.name = "json"
            resource.valueReference = it.valueReference
            virtualForm.resources = listOf(resource)
            list.add(virtualForm)
        }
    }

    /**
     * Resolves a form's encounter name: from its JSON schema's "encounter" field if present,
     * else derived from the form's display name (e.g. "Vitals (v2)" -> "Vitals").
     */
    private fun resolveEncounterName(formResource: FormResourceEntity): String? {
        val formName = formResource.name?.takeIf { it.isNotBlank() } ?: return null

        val formFieldsJson = formResource.resources.firstOrNull {
            (it.name == "json" || it.name == "JSON schema") &&
                !it.valueReference.isNullOrBlank() &&
                it.valueReference!!.trim().startsWith("{") &&
                it.valueReference!!.trim().endsWith("}")
        }?.valueReference

        if (!formFieldsJson.isNullOrBlank()) {
            try {
                val json = JSONObject(formFieldsJson)
                if (json.has("encounter")) return json.getString("encounter")
            } catch (e: Exception) {}
        }

        return formName.split("\\(".toRegex()).toTypedArray()[0].trim { it <= ' ' }
    }

    private fun createFormDataFromAsset(formName: String): FormData? {
        var formData: FormData? = null
        if (formName.contains("admission")) {
            formData = parseFormDataFromAsset("admission.json")
        } else if (formName.contains("vitals")) {
            formData = parseFormDataFromAsset("vitals1.json")
                    ?: parseFormDataFromAsset("vitals2.json")
        }
        return formData
    }

    private fun parseFormDataFromAsset(filename: String): FormData? {
        val json: String?
        json = try {
            val stream = OpenmrsAndroid.getInstance()!!.assets.open("forms/$filename")
            val buffer = ByteArray(stream.available())
            stream.read(buffer)
            stream.close()
            String(buffer, StandardCharsets.UTF_8)
        } catch (ex: IOException) {
            ex.printStackTrace()
            return null
        }
        val obj: JSONObject?
        try {
            obj = JSONObject(json)
            val data = FormData()
            data.name = obj.getString("name")
            data.dataType = obj.getString("dataType")
            data.valueReference = obj.getString("valueReference")
            return data
        } catch (e: JSONException) {
            e.printStackTrace()
        }
        return null
    }

    inner class SelectedForm(private val position: Int) {
        var formName: String? = null
            private set
        var encounterName: String? = null
            private set
        var encounterType: String? = null
            private set
        var formFieldsJson: String? = null
            private set

        init {
            click()
        }

        private fun click() {
            val formResource = formResourceList[position]
            formName = formResource.name
            if (formName.isNullOrBlank()) {
                encounterName = ""
                encounterType = null
                return
            }

            formFieldsJson = formResource.resources.firstOrNull {
                (it.name == "json" || it.name == "JSON schema") &&
                    !it.valueReference.isNullOrBlank() &&
                    it.valueReference!!.trim().startsWith("{") &&
                    it.valueReference!!.trim().endsWith("}")
            }?.valueReference

            encounterName = resolveEncounterName(formResource)

            encounterType = formResource.encounterTypeUuid
            if (encounterType.isNullOrBlank()) {
                encounterType = try { encounterDAO.getEncounterTypeByFormName(encounterName!!)?.uuid } catch(e: Exception) { null }
            }
        }
    }

    companion object {
        private val FORMS_REQUIRING_ENCOUNTER_ROLE = setOf(EncounterType.ADMISSION, EncounterType.VISIT_NOTE)
    }
}
