package org.openmrs.mobile.test.viewmodels

import android.content.Context
import android.content.res.AssetManager
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.google.gson.Gson
import com.openmrs.android_sdk.library.OpenmrsAndroid
import com.openmrs.android_sdk.library.api.repository.FormRepository
import com.openmrs.android_sdk.library.dao.EncounterDAO
import com.openmrs.android_sdk.library.databases.entities.FormResourceEntity
import com.openmrs.android_sdk.library.models.EncounterType
import com.openmrs.android_sdk.library.models.Result
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mock
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.openmrs.mobile.activities.formlist.FormListViewModel
import org.openmrs.mobile.test.ACUnitTestBaseRx
import rx.Observable
import java.io.IOException

@RunWith(JUnit4::class)
class FormListViewModelTest : ACUnitTestBaseRx() {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @Mock
    lateinit var encounterDAO: EncounterDAO

    @Mock
    lateinit var formRepository: FormRepository

    lateinit var viewModel: FormListViewModel

    private lateinit var openmrsAndroidMock: MockedStatic<OpenmrsAndroid>

    @Before
    fun mockAssetAccess() {
        // The ViewModel injects virtual forms by reading bundled JSON assets. With no Android
        // context that path throws an NPE rather than failing gracefully, so stand in a context
        // whose assets behave like the file is missing.
        val assetManager = mock(AssetManager::class.java)
        `when`(assetManager.open(anyString())).thenThrow(IOException())
        val context = mock(Context::class.java)
        `when`(context.assets).thenReturn(assetManager)
        openmrsAndroidMock = Mockito.mockStatic(OpenmrsAndroid::class.java)
        `when`(OpenmrsAndroid.getInstance()).thenReturn(context)

        // The ViewModel delegates schema resolution to the repository, so the mock has to behave
        // like the real one: hand back the "json"/"JSON schema" resource's inline JSON object.
        `when`(formRepository.resolveFormFieldsJson(any())).thenAnswer { invocation ->
            invocation.getArgument<FormResourceEntity>(0).resources
                    ?.firstOrNull { it.name == "json" || it.name == "JSON schema" }
                    ?.valueReference
                    ?.takeIf { it.startsWith("{") && it.endsWith("}") }
        }
    }

    @After
    fun closeStaticMocks() {
        openmrsAndroidMock.close()
    }

    @Test
    fun `ViewModel should load only forms provided with json valueReference (form fields)`() {
        // Names must survive the ViewModel's allow-list, which admits only Health Promotion
        // Session and IYCF Session forms - so the json valueReference is what decides here.
        val formName1 = "Health Promotion Session one"
        val formName2 = "Health Promotion Session two"
        val formName3 = "IYCF Session three"
        val formName4 = "IYCF Session four"
        val formResourceList = listOf(
                createFormResource(formName1),
                createFormResource(formName2),
                createFormResource(formName3, "json"),
                createFormResource(formName4, "NOT json")
        )

        val expectedFormsArray = arrayOf(formName3)
        `when`(formRepository.fetchFormResourceList()).thenReturn(Observable.just(formResourceList))


        viewModel = FormListViewModel(encounterDAO, formRepository)

        val result = viewModel.result.value as Result.Success
        assertArrayEquals(expectedFormsArray, result.data)
    }

    @Test
    fun `click on a form should get data for that form`() {
        // Both names must be on the ViewModel's allow-list or the forms never reach the list.
        val formName1 = "Health Promotion Session (Simple)"
        val formName2 = "IYCF Session"
        // No "encounter" key in the fixture json, so the encounter name is derived from the form
        // name with any parenthetical suffix stripped.
        val encounterName1 = "Health Promotion Session"
        val encounterName2 = "IYCF Session"
        val encounterTypeUuid1 = "15789573219881759238790"
        val encounterTypeUuid2 = "45425454534354534354354"
        val encounterType1 = EncounterType(encounterName1).apply { uuid = encounterTypeUuid1 }
        val encounterType2 = EncounterType(encounterName2).apply { uuid = encounterTypeUuid2 }
        val formResource1 = createFormResource(formName1, "json")
        val formResource2 = createFormResource(formName2, "json")
        val formResourceList: List<FormResourceEntity> = listOf(formResource1, formResource2)

        `when`(formRepository.fetchFormResourceList()).thenReturn(Observable.just(formResourceList))
        `when`(encounterDAO.getEncounterTypeByFormName(encounterName1)).thenReturn(encounterType1)
        `when`(encounterDAO.getEncounterTypeByFormName(encounterName2)).thenReturn(encounterType2)

        viewModel = FormListViewModel(encounterDAO, formRepository)

        val selectedForm1 = viewModel.SelectedForm(0)
        assertEquals(formName1, selectedForm1.formName)
        assertEquals(encounterName1, selectedForm1.encounterName)
        assertEquals(encounterType1.uuid, selectedForm1.encounterType)
        assertEquals(formResource1.resources[0].valueReference, selectedForm1.formFieldsJson)

        val selectedForm2 = viewModel.SelectedForm(1)
        assertEquals(formName2, selectedForm2.formName)
        assertEquals(encounterName2, selectedForm2.encounterName)
        assertEquals(encounterType2.uuid, selectedForm2.encounterType)
        assertEquals(formResource2.resources[0].valueReference, selectedForm2.formFieldsJson)
    }

    private fun createFormResource(formName: String, subResourceName: String? = null): FormResourceEntity {
        val exampleJson = getExampleFormResourceJson(formName)
        val formResourceEntity = Gson().fromJson(exampleJson, FormResourceEntity::class.java).apply {
            valueReference = exampleJson
            if (subResourceName != null) {
                val subResource = getExampleFormResourceJson(subResourceName)
                resources = listOf(Gson().fromJson(subResource, FormResourceEntity::class.java))
            }

        }
        return formResourceEntity
    }

    private fun getExampleFormResourceJson(name: String): String {
        return "{" +
                "\"display\":\"json\"," +
                "\"name\":\"" + name + "\"," +
                "\"valueReference\":\"" +
                "{" +
                "\\\"name\\\":\\\"Some Form\\\"," +
                "\\\"uuid\\\":\\\"77174d67-954f-45c4-a782-d157e70d59f4\\\"" +
                "}\"" +
                "}"
    }
}
