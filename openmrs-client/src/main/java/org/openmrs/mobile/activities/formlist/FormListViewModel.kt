
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
import org.openmrs.mobile.utilities.LanguageUtils
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
                            // Cached for offline use alongside the schema - see
                            // FormRepository.resolveFormTranslations.
                            try {
                                formRepository.resolveFormTranslations(formResource)
                            } catch (e: Exception) {
                                // The form still works, in the language its schema is written in.
                            }
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

                    // Only Health Promotion Session and IYCF Session forms should appear in the
                    // form entry menu per product request - every other form is filtered out here
                    // rather than removed from the source list so it can be re-enabled later.
                    val allowedForms = visibleForms.filter { formResource ->
                        val name = formResource.name?.trim()?.lowercase() ?: return@filter false
                        ALLOWED_FORM_NAMES.any { allowed -> name.contains(allowed) }
                    }

                    formResourceList.clear()
                    formResourceList.addAll(allowedForms)

                    val forms = ArrayList<String>(formResourceList.size)
                    for (form in formResourceList) forms += displayName(form)

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
     * The form's name in the app's language, when the form's translations include it - O3
     * translation maps are keyed by the schema's English text, which names the form without
     * the trailing " Form" the server-side form name often carries.
     */
    private fun displayName(formResource: FormResourceEntity): String {
        val name = formResource.name!!
        val translations = getTranslations(formResource)
        return translations[name] ?: translations[name.removeSuffix(" Form").trim()] ?: name
    }

    // Translations are optional - if they can't be looked up for any reason, the form must still
    // list and open, just untranslated, rather than failing the whole form list.
    private fun getTranslations(formResource: FormResourceEntity): Map<String, String> =
        runCatching { formRepository.getFormTranslations(formResource, LanguageUtils.getLanguage()) }
            .getOrDefault(emptyMap())

    /**
     * True for the two native (virtual) form entries injected by [injectVirtualForm] - i.e. the
     * ones backed by a real Android screen (FormAdmissionActivity/VisitNoteActivity) rather than
     * the generic JSON-schema renderer. Identified by uuid prefix rather than by encounter name,
     * since a custom JSON form can legitimately target the same encounter type/display name.
     */
    private fun isNativeForm(formResource: FormResourceEntity): Boolean =
        formResource.uuid?.startsWith(VIRTUAL_FORM_UUID_PREFIX) == true

    /**
     * Resolves a form's "json"/"JSON schema" resource value, including clobdata-backed schemas
     * that need a network fetch. See [FormRepository.resolveFormFieldsJson] for why some servers
     * store this out-of-line.
     */
    private fun resolveFormFieldsJson(formResource: FormResourceEntity): String? =
        formRepository.resolveFormFieldsJson(formResource)

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
        /** The form's label translations for the app's language, English label to translated. */
        var translations: HashMap<String, String> = HashMap()
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
            translations = HashMap(getTranslations(formResource))

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
        private val ALLOWED_FORM_NAMES = setOf("health promotion session", "iycf session")
    }
}
