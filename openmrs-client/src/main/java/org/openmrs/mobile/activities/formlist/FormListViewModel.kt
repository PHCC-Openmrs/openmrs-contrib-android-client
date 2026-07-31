
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

    fun refresh() {
        loadFormResourceList()
    }

    private fun loadFormResourceList() {
        setLoading()
        addSubscription(formRepository.fetchFormResourceList()
                .map {
                    val currentForms = mutableListOf<FormResourceEntity>()
                    for (formResource in it) {
                        // published/retired are null for virtual/asset-backed entries (they have
                        // no server-side publish state) - only exclude a form when the server
                        // explicitly says it's unpublished or retired, matching O3's behavior.
                        if (formResource.published == false || formResource.retired == true) continue

                        val valueRefString = resolveFormFieldsJson(formResource)
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

                    // The native Admission and Visit Note screens both resolve an encounter role
                    // (to attribute the encounter to a provider) before they can be submitted -
                    // hide them if the user's role lacks that server privilege, rather than
                    // letting the form open and then fail on submit. This must only apply to
                    // those two native (virtual) entries: custom JSON-schema forms can freely
                    // target the "Admission"/"Visit Note" encounter type too (e.g. a "SOAP Note
                    // Template" form against the Visit Note encounter type), and must not be
                    // swept up by this check just because resolveEncounterName() returns the same
                    // display name for them.
                    val visibleForms = if (PrivilegeChecker.hasPrivilege(GET_ENCOUNTER_ROLES)) {
                        currentForms
                    } else {
                        currentForms.filterNot { formResource ->
                            isNativeForm(formResource) && resolveEncounterName(formResource) in FORMS_REQUIRING_ENCOUNTER_ROLE
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
        virtualForm.uuid = VIRTUAL_FORM_UUID_PREFIX + abs(formName.hashCode()).toString()

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
     * True for the two native (virtual) form entries injected by [injectVirtualForm] - i.e. the
     * ones backed by a real Android screen (FormAdmissionActivity/VisitNoteActivity) rather than
     * the generic JSON-schema renderer. Identified by uuid prefix rather than by encounter name,
     * since a custom JSON form can legitimately target the same encounter type/display name.
     */
    private fun isNativeForm(formResource: FormResourceEntity): Boolean =
        formResource.uuid?.startsWith(VIRTUAL_FORM_UUID_PREFIX) == true

    /**
     * Resolves a form's "json"/"JSON schema" resource value. Some OpenMRS servers store this
     * value out-of-line as clob data, in which case valueReference is just a bare UUID rather
     * than the JSON itself (this is what O3 detects and resolves via a `clobdata/{uuid}` call).
     * Without this resolution these forms silently vanish from the list, since their
     * valueReference never looks like JSON. Resolved values are cached back onto the resource
     * so repeated lookups (encounter-name resolution, click-to-open) don't refetch.
     */
    private fun resolveFormFieldsJson(formResource: FormResourceEntity): String? {
        // Some forms carry both a "JSON schema" (clobdata) resource and a plain "json" one, and
        // they aren't always the same content - a form can have a stale/placeholder "json"
        // resource left over from testing while the real, current schema only lives in the
        // clobdata-backed "JSON schema" resource. Always try "JSON schema" first regardless of
        // the order the server returns resources in, falling back to "json" only if it's absent
        // or fails to resolve, rather than trusting API resource order to pick the right one.
        val orderedResources = formResource.resources.sortedByDescending { it.name == "JSON schema" }
        for (resource in orderedResources) {
            if (resource.name != "json" && resource.name != "JSON schema") continue
            val value = resource.valueReference?.trim() ?: continue
            if (value.isBlank()) continue

            if (value.startsWith("{") && value.endsWith("}")) {
                return value
            }

            if (CLOBDATA_UUID_REGEX.matches(value)) {
                val resolved = formRepository.fetchClobData(value)?.trim()
                if (!resolved.isNullOrBlank() && resolved.startsWith("{") && resolved.endsWith("}")) {
                    resource.valueReference = resolved
                    return resolved
                }
            }
        }
        return null
    }

    /**
     * Resolves a form's encounter name: from its JSON schema's "encounter" field if present,
     * else derived from the form's display name (e.g. "Vitals (v2)" -> "Vitals").
     */
    private fun resolveEncounterName(formResource: FormResourceEntity): String? {
        val formName = formResource.name?.takeIf { it.isNotBlank() } ?: return null

        val formFieldsJson = resolveFormFieldsJson(formResource)

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
        var isNativeForm: Boolean = false
            private set

        init {
            click()
        }

        private fun click() {
            val formResource = formResourceList[position]
            formName = formResource.name
            isNativeForm = isNativeForm(formResource)
            if (formName.isNullOrBlank()) {
                encounterName = ""
                encounterType = null
                return
            }

            formFieldsJson = resolveFormFieldsJson(formResource)

            encounterName = resolveEncounterName(formResource)

            encounterType = formResource.encounterTypeUuid
            if (encounterType.isNullOrBlank()) {
                encounterType = try { encounterDAO.getEncounterTypeByFormName(encounterName!!)?.uuid } catch(e: Exception) { null }
            }
        }
    }

    companion object {
        private const val VIRTUAL_FORM_UUID_PREFIX = "virtual-"
        private val FORMS_REQUIRING_ENCOUNTER_ROLE = setOf(EncounterType.ADMISSION, EncounterType.VISIT_NOTE)
        private val CLOBDATA_UUID_REGEX =
            Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
    }
}
