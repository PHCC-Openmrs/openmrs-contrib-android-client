package com.openmrs.android_sdk.library.api.repository

import com.openmrs.android_sdk.library.databases.AppDatabaseHelper
import com.openmrs.android_sdk.library.databases.entities.FormResourceEntity
import com.openmrs.android_sdk.library.models.Form
import com.openmrs.android_sdk.library.models.FormData
import com.openmrs.android_sdk.utilities.FormUtils
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
     * Proactively resolves and caches every locally-known form's schema (including
     * clobdata-backed ones), so all forms are available for offline filling rather than only
     * the ones a user happened to open once while online. Meant to be run alongside another
     * online "prep for offline use" action (e.g. the concept dictionary download), not on every
     * Form List screen open.
     */
    fun resolveAllFormSchemas(): Observable<Unit> {
        return AppDatabaseHelper.createObservableIO(Callable {
            db.formResourceDAO().getFormResourceList().forEach { formResource ->
                try {
                    resolveFormFieldsJson(formResource)
                } catch (e: Exception) {
                    // Best-effort - one form's resolution failure shouldn't block the rest.
                }
            }
        })
    }

    companion object {
        private val CLOBDATA_UUID_PATTERN: Pattern =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
    }
}
