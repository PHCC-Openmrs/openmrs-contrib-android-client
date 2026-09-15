package com.openmrs.android_sdk.library.databases.entities;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;

import com.openmrs.android_sdk.library.models.Resource;

/**
 * The type Visit entity.
 */
@Entity(tableName = "visits")
public class VisitEntity extends Resource {
    @NonNull
    @ColumnInfo(name = "patient_id")
    private long patientKeyID;
    @ColumnInfo(name = "visit_type")
    private String visitType;
    @ColumnInfo(name = "visit_place")
    private String visitPlace;
    @NonNull
    @ColumnInfo(name = "start_date")
    private String startDate;
    @ColumnInfo(name = "stop_date")
    private String stopDate;
    /**
     * UUID of the visit's location. {@link #visitPlace} only ever held the location's display
     * text, which is enough to show a visit but not to post one, nor to enrol the patient at that
     * location - both need the uuid, including for a visit started offline and pushed later.
     */
    @ColumnInfo(name = "visit_location_uuid")
    private String visitLocationUuid;
    /**
     * The visit's attributes (Service, Punctuality, ...) as a JSON array of
     * {@code {"attributeType": uuid, "value": string}} objects - see
     * {@code AppDatabaseHelper#serializeVisitAttributes}. Held as one column rather than a table
     * of its own: they are only ever read and written together with the visit itself, and they go
     * to the server inline in the visit's own payload.
     */
    @ColumnInfo(name = "attributes")
    private String attributes;

    /**
     * Instantiates a new Visit entity.
     */
    public VisitEntity() {
    }

    /**
     * Sets patient key id.
     *
     * @param patientKeyID the patient key id
     */
    public void setPatientKeyID(long patientKeyID) {
        this.patientKeyID = patientKeyID;
    }

    /**
     * Sets visit type.
     *
     * @param visitType the visit type
     */
    public void setVisitType(String visitType) {
        this.visitType = visitType;
    }

    /**
     * Sets visit place.
     *
     * @param visitPlace the visit place
     */
    public void setVisitPlace(String visitPlace) {
        this.visitPlace = visitPlace;
    }

    /**
     * Sets start date.
     *
     * @param startDate the start date
     */
    public void setStartDate(String startDate) {
        this.startDate = startDate;
    }

    /**
     * Sets stop date.
     *
     * @param stopDate the stop date
     */
    public void setStopDate(String stopDate) {
        this.stopDate = stopDate;
    }

    /**
     * Sets visit location uuid.
     *
     * @param visitLocationUuid the visit location uuid
     */
    public void setVisitLocationUuid(String visitLocationUuid) {
        this.visitLocationUuid = visitLocationUuid;
    }

    /**
     * Gets visit location uuid.
     *
     * @return the visit location uuid
     */
    public String getVisitLocationUuid() {
        return visitLocationUuid;
    }

    /**
     * Sets the visit's attributes, as a JSON array.
     *
     * @param attributes the attributes
     */
    public void setAttributes(String attributes) {
        this.attributes = attributes;
    }

    /**
     * Gets the visit's attributes, as a JSON array.
     *
     * @return the attributes
     */
    public String getAttributes() {
        return attributes;
    }

    /**
     * Gets patient key id.
     *
     * @return the patient key id
     */
    public long getPatientKeyID() {
        return patientKeyID;
    }

    /**
     * Gets visit type.
     *
     * @return the visit type
     */
    public String getVisitType() {
        return visitType;
    }

    /**
     * Gets visit place.
     *
     * @return the visit place
     */
    public String getVisitPlace() {
        return visitPlace;
    }

    /**
     * Gets start date.
     *
     * @return the start date
     */
    @NonNull
    public String getStartDate() {
        return startDate;
    }

    /**
     * Is start date string.
     *
     * @return the string
     */
    public String isStartDate() {
        return startDate;
    }

    /**
     * Gets stop date.
     *
     * @return the stop date
     */
    public String getStopDate() {
        return stopDate;
    }
}
