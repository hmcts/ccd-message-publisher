package uk.gov.hmcts.ccd.service;

public enum MessageProperties {
    JURISDICTION_ID("jurisdiction_id", "jurisdiction_id"),
    CASE_TYPE_ID("case_type_id", "case_type_id"),
    CASE_ID("case_id", "case_id"),
    SESSION_ID("session_id", "session_id"),
    EVENT_ID("event_id", "event_id");


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
