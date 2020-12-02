package uk.gov.hmcts.ccd.service.messaging;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PublishMessageTask {

    private String messageType;
    private String destination;
    private String schedule;
    private int batchSize;
    private int publishedRetentionDays;
    private boolean enabled;
}
