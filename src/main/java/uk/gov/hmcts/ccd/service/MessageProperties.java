package uk.gov.hmcts.ccd.service;

public enum MessageProperties {
    JURISDICTION_ID("JurisdictionId", "jurisdiction_id"),
    CASE_TYPE_ID("CaseTypeId", "case_type_id"),
    CASE_ID("CaseId", "case_id"),
    SESSION_ID("CaseId", "JMSXGroupID"),
    EVENT_ID("EventId", "event_id");


    private final String propertySourceId;
    private final String propertyId;

    MessageProperties(String propertySourceId, String propertyId) {
        this.propertyId = propertyId;
        this.propertySourceId = propertySourceId;
    }

    public String getPropertyId() {
        return propertyId;
    }

    public String getPropertySourceId() {
        return propertySourceId;
    }

}
