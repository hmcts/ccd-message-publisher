package uk.gov.hmcts.ccd.data;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@SuppressWarnings("java:S1948")
public class MessageDTO implements Serializable {

    private String messageType;
    private LocalDateTime timeStamp;
    private JsonNode messageInformation;
}
