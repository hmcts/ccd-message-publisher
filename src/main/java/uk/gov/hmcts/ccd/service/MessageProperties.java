package uk.gov.hmcts.ccd.service;

public enum MessageProperties {
    JURISDICTION_ID("jurisdiction_id", "jurisdiction_id"),
    CASE_TYPE_ID("case_type_id", "case_type_id"),
    CASE_ID("case_id", "case_id"),
    EVENT_ID("event_id", "event_id");


    private final String property;
    private final String value;

    MessageProperties(String property, String value) {
        this.value = value;
        this.property = property;
    }

    public String value() {
        return value;
    }

    public String propertyString() {
        return property;
    }

}
