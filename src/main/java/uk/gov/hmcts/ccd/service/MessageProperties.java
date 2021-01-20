package uk.gov.hmcts.ccd.service;

public enum MessageProperties {
    JURISDICTION_ID("jurisdiction_id", "jurisdiction_id"),
    CASE_TYPE_ID("case_type_id", "case_type_id"),
    CASE_ID("case_id", "case_id"),
    EVENT_ID("event_id", "event_id");


    private final String propertyId;
    private final String propertySourceId;

    MessageProperties(String propertyId, String propertySourceId) {
        this.propertySourceId = propertySourceId;
        this.propertyId = propertyId;
    }

    public String getpropertySourceId() {
        return propertySourceId;
    }

    public String getPropertyId() {
        return propertyId;
    }

}
