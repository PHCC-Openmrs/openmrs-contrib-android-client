package org.openmrs.mobile.activities.addeditpatient

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import com.google.android.libraries.places.api.net.PlacesClient
import com.openmrs.android_sdk.library.api.repository.ConceptRepository
import com.openmrs.android_sdk.library.api.repository.PatientRepository
import com.openmrs.android_sdk.library.dao.PatientDAO
import com.openmrs.android_sdk.library.models.ConceptAnswers
import com.openmrs.android_sdk.library.models.OperationType
import com.openmrs.android_sdk.library.models.OperationType.PatientRegistering
import com.openmrs.android_sdk.library.models.Patient
import com.openmrs.android_sdk.library.models.ResultType
import com.openmrs.android_sdk.utilities.ApplicationConstants
import com.openmrs.android_sdk.utilities.ApplicationConstants.BundleKeys.PATIENT_ID_BUNDLE
import com.openmrs.android_sdk.utilities.PatientValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import org.joda.time.DateTime
import org.openmrs.mobile.activities.BaseViewModel
import rx.android.schedulers.AndroidSchedulers
import javax.inject.Inject
import java.io.File

@HiltViewModel
class AddEditPatientViewModel @Inject constructor(
        private val patientDAO: PatientDAO,
        private val patientRepository: PatientRepository,
        private val conceptRepository: ConceptRepository,
        private val savedStateHandle: SavedStateHandle
) : BaseViewModel<Patient>() {

    private val _similarPatientsLiveData = MutableLiveData<List<Patient>>()
    val similarPatientsLiveData: LiveData<List<Patient>> get() = _similarPatientsLiveData

    private val _patientUpdateLiveData = MutableLiveData<ResultType>()
    val patientUpdateLiveData: LiveData<ResultType> get() = _patientUpdateLiveData

    private val _duplicateNationalIdLiveData = MutableLiveData<Patient?>()
    val duplicateNationalIdLiveData: LiveData<Patient?> get() = _duplicateNationalIdLiveData

    var patientValidator: PatientValidator

    var isUpdatePatient = false
        private set

    lateinit var patient: Patient
        private set

    var placesClient: PlacesClient? = null
    var dateHolder: DateTime? = null
    var capturedPhotoFile: File? = null

    init {
        // Initialize patient state
        val patientId: Long? = savedStateHandle.get(PATIENT_ID_BUNDLE)
        val foundPatient = patientDAO.findPatientByID(patientId)
        if (foundPatient != null) {
            isUpdatePatient = true
            patient = foundPatient
        } else {
            resetPatient()
        }

        // Initialize patient data validator
        patientValidator = PatientValidator(patient)
    }

    fun resetPatient() {
        isUpdatePatient = false
        capturedPhotoFile = null
        dateHolder = null
        patient = Patient()
    }

    /**
     * Gets the National ID value currently attached to the patient, if any.
     */
    fun getNationalId(): String? =
            patient.getIdentifierByType(ApplicationConstants.IdentifierSource.NATIONAL_ID_IDENTIFIER_TYPE_UUID)?.identifier

    /**
     * Attaches (or replaces) the patient's National ID identifier.
     */
    fun setNationalId(value: String) {
        val identifiers = patient.identifiers.filterNot {
            it.identifierType?.uuid == ApplicationConstants.IdentifierSource.NATIONAL_ID_IDENTIFIER_TYPE_UUID
        }.toMutableList()
        identifiers.add(patientRepository.buildNationalIdIdentifier(value))
        patient.identifiers = identifiers
    }

    /**
     * Gets the Phone Number value currently attached to the patient, if any.
     */
    fun getPhoneNumber(): String? =
            patient.getAttributeValue(ApplicationConstants.PersonAttributeTypes.PHONE_NUMBER_UUID)

    /**
     * Attaches (or replaces) the patient's Phone Number attribute. Optional - passing a blank
     * value removes the attribute rather than attaching an empty one.
     */
    fun setPhoneNumber(value: String) = setAttribute(ApplicationConstants.PersonAttributeTypes.PHONE_NUMBER_UUID, value)

    /**
     * Gets the Patient Status (Resident/IDP) value currently attached to the patient, if any.
     */
    fun getPatientStatus(): String? =
            patient.getAttributeValue(ApplicationConstants.PersonAttributeTypes.PATIENT_STATUS_UUID)

    /**
     * Attaches (or replaces) the patient's Patient Status attribute. Optional - passing a blank
     * value removes the attribute rather than attaching an empty one.
     */
    fun setPatientStatus(value: String) = setAttribute(ApplicationConstants.PersonAttributeTypes.PATIENT_STATUS_UUID, value)

    private fun setAttribute(attributeTypeUuid: String, value: String) {
        val attributes = patient.attributes.filterNot {
            it.attributeType?.uuid == attributeTypeUuid
        }.toMutableList()
        if (value.isNotBlank()) {
            attributes.add(patientRepository.buildAttribute(attributeTypeUuid, value))
        }
        patient.attributes = attributes
    }

    fun confirmPatient() {
        val logger = com.openmrs.android_sdk.library.OpenmrsAndroid.getOpenMRSLogger()
        logger.i("Confirm patient called")
        if (!patientValidator.validate()) {
            logger.w("Patient validation failed in confirmPatient")
            return
        }
        if (isUpdatePatient) updatePatient()
        else registerPatient()
    }

    /**
     * Checks whether the National ID just entered already belongs to a patient this device
     * already knows about (previously registered offline, or downloaded for offline use) - a
     * check that's only meaningful/possible locally, since there's no way to ask the server about
     * it while offline. Emits the matching local patient via [duplicateNationalIdLiveData], or
     * null if there's no local duplicate. When editing an existing patient, excludes that same
     * patient's own row from matching itself.
     */
    fun checkLocalDuplicateNationalId() {
        val nationalId = getNationalId()
        if (nationalId.isNullOrBlank()) {
            _duplicateNationalIdLiveData.value = null
            return
        }
        addSubscription(patientRepository.findLocalPatientsByIdentifier(
                ApplicationConstants.IdentifierSource.NATIONAL_ID_IDENTIFIER_TYPE_UUID, nationalId)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        { matches ->
                            _duplicateNationalIdLiveData.value = matches.firstOrNull { candidate ->
                                !isUpdatePatient || candidate.id != patient.id
                            }
                        },
                        { _duplicateNationalIdLiveData.value = null }
                )
        )
    }

    fun fetchSimilarPatients() {
        val logger = com.openmrs.android_sdk.library.OpenmrsAndroid.getOpenMRSLogger()
        logger.i("Fetch similar patients called")
        if (!patientValidator.validate()) {
            logger.w("Patient validation failed in fetchSimilarPatients")
            return
        }
        setLoading(OperationType.PatientSearching)
        logger.i("Starting fetchSimilarPatients API call")
        addSubscription(patientRepository.fetchSimilarPatients(patient)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        { 
                            logger.i("Fetch similar patients successful, found ${it.size} matches")
                            _similarPatientsLiveData.value = it 
                        },
                        { 
                            logger.e("Fetch similar patients failed", it)
                            setError(it, OperationType.PatientSearching) 
                        }
                )
        )
    }

    fun fetchCausesOfDeath(): LiveData<ConceptAnswers> {
        val liveData = MutableLiveData<ConceptAnswers>()
        addSubscription(patientRepository.getCauseOfDeathGlobalConceptID()
                .flatMap { conceptRepository.getConceptByUuid(it) }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        { causesOfDeath: ConceptAnswers -> liveData.value = causesOfDeath },
                        { throwable -> liveData.value = ConceptAnswers() }
                )
        )
        return liveData
    }

    private fun registerPatient() {
        setLoading()
        val logger = com.openmrs.android_sdk.library.OpenmrsAndroid.getOpenMRSLogger()
        logger.i("Starting patient registration")
        addSubscription(patientRepository.registerPatient(patient)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        { 
                            logger.i("Registration successful")
                            setContent(it, PatientRegistering) 
                        },
                        { 
                            logger.e("Registration failed", it)
                            setError(it, PatientRegistering) 
                        }
                )
        )
    }

    private fun updatePatient() {
        setLoading()
        addSubscription(patientRepository.updatePatient(patient)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        { resultType -> _patientUpdateLiveData.value = resultType },
                        { _patientUpdateLiveData.value = ResultType.PatientUpdateError }
                )
        )
    }
}
