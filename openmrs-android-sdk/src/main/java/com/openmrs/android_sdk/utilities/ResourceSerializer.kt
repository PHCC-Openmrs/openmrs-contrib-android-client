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
package com.openmrs.android_sdk.utilities

import android.util.Log
import com.openmrs.android_sdk.library.models.Resource
import com.google.gson.*
import com.google.gson.annotations.Expose
import java.lang.reflect.Type

class ResourceSerializer : JsonSerializer<Resource> {

    override fun serialize(src: Resource, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
        val myGson = gson
        val srcJson = JsonObject()
        
        // Get all fields including those from superclasses (like 'uuid' in Resource)
        val allFields = mutableListOf<java.lang.reflect.Field>()
        var currentClass: Class<*>? = src.javaClass
        while (currentClass != null && currentClass != Object::class.java) {
            allFields.addAll(currentClass.declaredFields)
            currentClass = currentClass.superclass
        }

        for (field in allFields) {
            if (field.getAnnotation(Expose::class.java) != null) {
                field.isAccessible = true
                val fieldName = field.name
                val fieldVal = field[src]
                
                if (Resource::class.java.isAssignableFrom(field.type)) {
                    try {
                        if (fieldVal != null) {
                            srcJson.add(fieldName, serializeField(fieldVal as Resource, context))
                        }
                    } catch (e: IllegalAccessException) {
                        Log.e(RESOURCE_SERIALIZER, EXCEPTION, e)
                    }
                } else if (MutableCollection::class.java.isAssignableFrom(field.type)) {
                    try {
                        val collection = fieldVal as? Collection<*>
                        if (collection != null && !collection.isEmpty()) {
                            if (isResourceCollection(collection)) {
                                val jsonArray = JsonArray()
                                for (resource in collection) {
                                    jsonArray.add(serializeField(resource as Resource, context))
                                }
                                srcJson.add(fieldName, jsonArray)
                            } else {
                                val jsonArray = JsonArray()
                                for (`object` in collection) {
                                    jsonArray.add(myGson.toJsonTree(`object`))
                                }
                                srcJson.add(fieldName, jsonArray)
                            }
                        }
                    } catch (e: IllegalAccessException) {
                        Log.e(RESOURCE_SERIALIZER, EXCEPTION, e)
                    }
                } else {
                    try {
                        if (fieldVal != null) {
                            srcJson.add(fieldName, myGson.toJsonTree(fieldVal))
                        }
                    } catch (e: IllegalAccessException) {
                        Log.e(RESOURCE_SERIALIZER, EXCEPTION, e)
                    }
                }
            }
        }
        return srcJson
    }

    private val gson: Gson
        get() {
            val gsonBuilder = GsonBuilder()
            return gsonBuilder
                    .excludeFieldsWithoutExposeAnnotation()
                    .create()
        }

    private fun serializeField(src: Resource, context: JsonSerializationContext): JsonElement {
        return if (src.uuid != null) {
            JsonPrimitive(src.uuid)
        } else {
            context.serialize(src)
        }
    }

    private fun isResourceCollection(collection: Collection<*>): Boolean {
        return Resource::class.java.isAssignableFrom(collection.toTypedArray()[0]!!.javaClass)
    }

    companion object {
        const val RESOURCE_SERIALIZER = "RESOURCE_SERIALIZER"
        const val EXCEPTION = "exception"
    }
}