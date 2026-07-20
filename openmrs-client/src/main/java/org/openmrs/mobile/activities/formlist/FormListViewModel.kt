
package org.openmrs.mobile.activities.formlist

import com.openmrs.android_sdk.library.OpenmrsAndroid
import com.openmrs.android_sdk.library.api.repository.FormRepository
import com.openmrs.android_sdk.library.dao.EncounterDAO
import com.openmrs.android_sdk.library.databases.entities.FormResourceEntity
import com.openmrs.android_sdk.library.models.EncounterType
import com.openmrs.android_sdk.library.models.FormData
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
                    injectVirtualForm(currentForms, EncounterType.VISIT_NOTE, "visit_note.json")

                    formResourceList.clear()
                    formResourceList.addAll(currentForms)

                    val forms = ArrayList<String>(formResourceList.size)
                    for (form in formResourceList) forms += form.name!!

                    return@map forms.toTypedArray()
                }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ setContent(it) }, { setError(it) })
        )
    }

    private fun injectVirtualForm(list: MutableList<FormResourceEntity>, formName: String, assetName: String) {
        val alreadyExists = list.any { it.name?.contains(formName, ignoreCase = true) == true }
        if (!alreadyExists) {
            val formData = parseFormDataFromAsset(assetName)
            formData?.let {
                val virtualForm = FormResourceEntity()
                virtualForm.name = formName
                virtualForm.uuid = "virtual-" + abs(formName.hashCode()).toString()
                val resource = FormResourceEntity()
                resource.name = "json"
                resource.valueReference = it.valueReference
                virtualForm.resources = listOf(resource)
                
                val encounterType = try { encounterDAO.getEncounterTypeByFormName(formName) } catch(e: Exception) { null }
                virtualForm.encounterTypeUuid = encounterType?.uuid ?: when(formName) {
                    EncounterType.VITALS -> "67a71486-1a54-468f-ac3e-7091a9a79584"
                    EncounterType.VISIT_NOTE -> "d7151f82-c1f3-4152-a605-2f9ea7414a79"
                    else -> null
                }
                
                list.add(virtualForm)
            }
        }
    }

    private fun createFormDataFromAsset(formName: String): FormData? {
        var formData: FormData? = null
        if (formName.contains("admission")) {
            formData = parseFormDataFromAsset("admission.json")
        } else if (formName.contains("vitals")) {
            formData = parseFormDataFromAsset("vitals1.json")
                    ?: parseFormDataFromAsset("vitals2.json")
        } else if (formName.contains("visit note")) {
            formData = parseFormDataFromAsset("visit_note.json")
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

            formResource.resources.forEach {
                if (it.name == "json" || it.name == "JSON schema") {
                    val value = it.valueReference
                    if (!value.isNullOrBlank() && value.trim().startsWith("{") && value.trim().endsWith("}")) {
                        formFieldsJson = value
                    }
                }
            }

            // Try to extract encounter type from JSON schema first
            if (!formFieldsJson.isNullOrBlank()) {
                try {
                    val json = JSONObject(formFieldsJson)
                    if (json.has("encounter")) {
                        encounterName = json.getString("encounter")
                    }
                } catch (e: Exception) {}
            }

            if (encounterName.isNullOrBlank()) {
                encounterName = formName!!.split("\\(".toRegex()).toTypedArray()[0].trim { it <= ' ' }
            }

            encounterType = formResource.encounterTypeUuid
            if (encounterType.isNullOrBlank()) {
                encounterType = try { encounterDAO.getEncounterTypeByFormName(encounterName!!)?.uuid } catch(e: Exception) { null }
            }
        }
    }
}
