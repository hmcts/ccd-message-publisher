package uk.gov.hmcts.ccd.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConfigurationProperties(prefix = "message-publisher")
@Data
public class MessagePublisherParams {

    private List<PublishMessageTask> tasks;
}
