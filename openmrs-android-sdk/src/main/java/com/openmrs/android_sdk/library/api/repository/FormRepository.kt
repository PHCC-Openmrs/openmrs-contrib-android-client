package com.openmrs.android_sdk.library.api.repository

import com.openmrs.android_sdk.library.databases.AppDatabaseHelper
import com.openmrs.android_sdk.library.databases.entities.FormResourceEntity
import com.openmrs.android_sdk.library.models.Form
import com.openmrs.android_sdk.library.models.FormData
import com.openmrs.android_sdk.utilities.FormUtils
import org.json.JSONObject
import rx.Observable
import javax.inject.Inject
import javax.inject.Singleton
import java.util.concurrent.Callable
import java.util.regex.Pattern

@Singleton
class FormRepository @Inject constructor() : BaseRepository() {

    /**
     * Fetches forms as a list of resources.
     *
     * @return observable list of form resources
     */
    fun fetchFormResourceList(): Observable<List<FormResourceEntity>> {
        return AppDatabaseHelper.createObservableIO(Callable {
            return@Callable db.formResourceDAO().getFormResourceList()
        })
    }

    /**
     * Fetches a resource form by the form's name.
     *
     * @param name the form name
     * @return an observable form resource entity
     */
    fun fetchFormResourceByName(name: String): Observable<FormResourceEntity> {
        return AppDatabaseHelper.createObservableIO(Callable {
            return@Callable db.formResourceDAO().getFormResourceByName(name)
        })
    }

    /**
     * fetches a form by its UUID.
     *
     * @param uuid UUID of the form
     * @return observable form object or null if no form found
     */
    fun fetchFormByUuid(uuid: String): Observable<Form?> {
        return AppDatabaseHelper.createObservableIO(Callable {
            val formResourceEntity: FormResourceEntity? = db.formResourceDAO().getFormByUuid(uuid)
            formResourceEntity?.resources?.forEach {
                if ("json" == it.name) {
                    val valueRefString = it.valueReference
                    return@Callable FormUtils.getForm(valueRefString).apply {
                        valueReference = valueRefString
                        name = formResourceEntity.name
                    }
                }
            }
            return@Callable null
        })
    }

    /**
     * Resolves a clob-backed resource value (a form's "json"/"JSON schema" resource whose
     * valueReference is a UUID pointing at out-of-line clob storage rather than inline JSON).
     *
     * @param uuid the clobdata UUID
     * @return the resolved raw content, or null if it couldn't be fetched
     */
    fun fetchClobData(uuid: String): String? {
        return try {
            val response = restApi.getClobData(uuid).execute()
            if (response.isSuccessful) response.body()?.string() else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Persists a form resource that was mutated in memory (e.g. after [fetchClobData] resolved
     * one of its resources' valueReference) - without this, a resolved clobdata value is lost the
     * moment this object is garbage collected, and has to be re-fetched from the network (or, if
     * offline by then, is simply unavailable) on every subsequent app session.
     *
     * @param formResourceEntity the form resource entity to persist, with its resources already
     *   updated in memory
     */
    fun updateFormResource(formResourceEntity: FormResourceEntity) {
        db.formResourceDAO().updateFormResource(formResourceEntity)
    }

    /**
     * Creates a form.
     *
     * @param uuid UUID of the form resource
     * @param formData form data that will be created
     * @return observable boolean true if operation is successful
     */
    fun createForm(uuid: String, formData: FormData): Observable<Boolean> {
        return AppDatabaseHelper.createObservableIO(Callable {
            restApi.formCreate(uuid, formData).execute().run {
                if (isSuccessful && body()!!.name == "json") return@run true
                else throw Exception("Error creating forms: ${message()}")
            }
        })
    }

    /**
     * Fetches the form list from the server and replaces the local list with it - the single
     * source of truth for which forms exist locally, which [resolveAllFormSchemas] depends on
     * already being populated (it only resolves schemas for rows that already exist; it never
     * creates rows itself). Synchronous/blocking - callers on a background thread already (e.g.
     * an `IntentService`) can call this directly; callers needing this off the calling thread
     * should wrap it themselves. Makes no assumption about network state or privileges - callers
     * are responsible for their own gating, matching every other method in this class.
     *
     * @return the synced form list, or null if the fetch failed or was unsuccessful
     */
    fun syncFormList(): List<FormResourceEntity>? {
        return try {
            val response = restApi.getForms().execute()
            if (!response.isSuccessful || response.body() == null) return null
            val formResourceList = response.body()!!.results
            formResourceList.forEach { formResourceEntity ->
                if (formResourceEntity.name == null) {
                    formResourceEntity.name = "Unnamed Form"
                }
                val encounterTypeUuid = formResourceEntity.encounterTypeResource?.uuid
                if (!encounterTypeUuid.isNullOrEmpty()) {
                    formResourceEntity.encounterTypeUuid = encounterTypeUuid
                }
            }
            // A single transaction, not separate delete-then-insert calls: two syncs can run
            // concurrently on a fresh device's first login (FormListService and
            // ConceptDownloadService both call this), and un-transacted calls could interleave -
            // see FormResourceDAO.replaceAllForms.
            db.formResourceDAO().replaceAllForms(formResourceList)
            formResourceList
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Resolves a form's "json"/"JSON schema" resource value. Some OpenMRS servers store this
     * value out-of-line as clob data, in which case valueReference is just a bare UUID rather
     * than the JSON itself (this is what O3 detects and resolves via a `clobdata/{uuid}` call).
     * Without this resolution these forms silently vanish from the form list, since their
     * valueReference never looks like JSON. Resolved values are cached back onto the resource
     * and persisted to the local DB (not just in memory) so a later offline session has
     * something to fall back on, rather than needing to refetch (which fails offline).
     */
    fun resolveFormFieldsJson(formResource: FormResourceEntity): String? {
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

            if (CLOBDATA_UUID_PATTERN.matcher(value).matches()) {
                val resolved = fetchClobData(value)?.trim()
                if (!resolved.isNullOrBlank() && resolved.startsWith("{") && resolved.endsWith("}")) {
                    resource.valueReference = resolved
                    try {
                        updateFormResource(formResource)
                    } catch (e: Exception) {
                        // Not fatal - resolution still succeeded for this session via the
                        // in-memory update above, it just won't survive to the next one.
                    }
                    return resolved
                }
            }
        }
        return null
    }

    /**
     * Resolves and caches a form's label translations - the "<form name>_translations_<locale>"
     * resources O3 keeps next to the JSON schema, each holding `{"translations": {English label:
     * translated label}}`. The web client gets these merged into the form by the o3forms module's
     * `o3/forms/{uuid}` endpoint, but only for the session's locale; this app starts a fresh
     * Basic-auth session per request, so that endpoint would only ever return the user's server
     * default language. Fetching the resources directly - every locale, cached like the schema -
     * lets the form render in whichever language the app is set to, offline too.
     *
     * Needs the network for resources not yet resolved; call it off the main thread.
     */
    fun resolveFormTranslations(formResource: FormResourceEntity) {
        var resolvedAny = false
        formResource.resources.filter { isTranslationsResource(it) }.forEach { resource ->
            val value = resource.valueReference?.trim() ?: return@forEach
            if (!CLOBDATA_UUID_PATTERN.matcher(value).matches()) return@forEach
            val resolved = fetchClobData(value)?.trim()
            if (!resolved.isNullOrBlank() && resolved.startsWith("{") && resolved.endsWith("}")) {
                resource.valueReference = resolved
                resolvedAny = true
            }
        }
        if (resolvedAny) {
            try {
                updateFormResource(formResource)
            } catch (e: Exception) {
                // Not fatal - see resolveFormFieldsJson.
            }
        }
    }

    /**
     * Gets a form's label translations for a language, from what [resolveFormTranslations] has
     * already cached - never touches the network, so it's safe on the main thread.
     *
     * @param languageTag the app's language, e.g. "ar" or "en-GB"
     * @return English label to translated label; empty when the form has none for that language
     */
    fun getFormTranslations(formResource: FormResourceEntity, languageTag: String): Map<String, String> {
        val wanted = normalizeLocale(languageTag)
        val candidates = formResource.resources.filter { isTranslationsResource(it) }
        // An exact locale first (ar_SY for ar_SY), then any resource for the same language.
        val resource = candidates.firstOrNull { resourceLocale(it) == wanted }
                ?: candidates.firstOrNull { resourceLocale(it).substringBefore('_') == wanted.substringBefore('_') }
                ?: return emptyMap()

        val value = resource.valueReference?.trim()
        if (value.isNullOrEmpty() || !value.startsWith("{")) return emptyMap()
        return try {
            val json = JSONObject(value)
            val entries = json.optJSONObject("translations") ?: json
            val translations = HashMap<String, String>()
            entries.keys().forEach { key ->
                val translated = entries.optString(key)
                if (translated.isNotBlank()) translations[key] = translated
            }
            translations
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun isTranslationsResource(resource: FormResourceEntity) =
            resource.name?.contains(TRANSLATIONS_RESOURCE_MARKER) == true

    private fun resourceLocale(resource: FormResourceEntity) =
            normalizeLocale(resource.name!!.substringAfterLast(TRANSLATIONS_RESOURCE_MARKER))

    private fun normalizeLocale(locale: String) = locale.trim().replace('-', '_').lowercase()

    /**
     * Proactively resolves and caches every locally-known form's schema (including
     * clobdata-backed ones) and its label translations, so all forms are available for offline
     * filling rather than only the ones a user happened to open once while online. Meant to be
     * run alongside another online "prep for offline use" action (e.g. the concept dictionary
     * download), not on every Form List screen open.
     */
    fun resolveAllFormSchemas(): Observable<Unit> {
        return AppDatabaseHelper.createObservableIO(Callable {
            db.formResourceDAO().getFormResourceList().forEach { formResource ->
                try {
                    resolveFormFieldsJson(formResource)
                    resolveFormTranslations(formResource)
                } catch (e: Exception) {
                    // Best-effort - one form's resolution failure shouldn't block the rest.
                }
            }
        })
    }

    companion object {
        private val CLOBDATA_UUID_PATTERN: Pattern =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

        /** How O3 names a form's translation resources: "<form name>_translations_<locale>". */
        private const val TRANSLATIONS_RESOURCE_MARKER = "_translations_"
    }
}
