package uk.gov.hmcts.ccd.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@ConfigurationProperties(prefix = "message-publisher")
@Data
public class MessagePublisherParams {

    private List<PublishMessageTask> tasks;

    public List<PublishMessageTask> getEnabledTasks() {
        return getTasks().stream()
            .filter(PublishMessageTask::isEnabled)
            .collect(Collectors.toList());
    }
}
