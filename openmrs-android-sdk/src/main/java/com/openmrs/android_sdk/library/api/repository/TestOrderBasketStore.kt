/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package com.openmrs.android_sdk.library.api.repository

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.openmrs.android_sdk.library.models.TestOrderBasketItem
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory, per-patient lab order basket, mirroring [DrugOrderBasketStore] for test orders:
 * items persist across screens (basket, test search, test order form) for as long as the app
 * process is alive, until explicitly submitted or removed.
 */
@Singleton
class TestOrderBasketStore @Inject constructor() {

    private val itemsByPatient = ConcurrentHashMap<String, MutableList<TestOrderBasketItem>>()
    private val liveDataByPatient = ConcurrentHashMap<String, MutableLiveData<List<TestOrderBasketItem>>>()
    private val idGenerator = AtomicLong(1)

    fun nextId(): Long = idGenerator.getAndIncrement()

    fun liveItems(patientUuid: String): LiveData<List<TestOrderBasketItem>> = liveDataFor(patientUuid)

    fun addItem(patientUuid: String, item: TestOrderBasketItem) {
        listFor(patientUuid).add(item)
        publish(patientUuid)
    }

    fun updateItem(patientUuid: String, id: Long, item: TestOrderBasketItem) {
        val list = listFor(patientUuid)
        val index = list.indexOfFirst { it.id == id }
        if (index >= 0) list[index] = item else list.add(item)
        publish(patientUuid)
    }

    fun removeItem(patientUuid: String, id: Long) {
        listFor(patientUuid).removeAll { it.id == id }
        publish(patientUuid)
    }

    fun getItem(patientUuid: String, id: Long): TestOrderBasketItem? =
        listFor(patientUuid).firstOrNull { it.id == id }

    fun clear(patientUuid: String) {
        listFor(patientUuid).clear()
        publish(patientUuid)
    }

    private fun listFor(patientUuid: String): MutableList<TestOrderBasketItem> =
        itemsByPatient.getOrPut(patientUuid) { mutableListOf() }

    private fun liveDataFor(patientUuid: String): MutableLiveData<List<TestOrderBasketItem>> =
        liveDataByPatient.getOrPut(patientUuid) { MutableLiveData(listFor(patientUuid).toList()) }

    private fun publish(patientUuid: String) {
        liveDataFor(patientUuid).postValue(listFor(patientUuid).toList())
    }
}
