package uk.gov.hmcts.ccd.service;

public enum MessageProperties {
    JURISDICTION_ID("jurisdiction_id", "jurisdictionid"),
    CASE_TYPE_ID("case_type_id", "casetypeid"),
    CASE_ID("case_id", "caseid"),
    EVENT_ID("event_id", "eventid");


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
