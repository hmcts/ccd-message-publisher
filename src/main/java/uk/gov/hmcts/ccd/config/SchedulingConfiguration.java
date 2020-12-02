package uk.gov.hmcts.ccd.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import uk.gov.hmcts.ccd.data.MessageQueueCandidateRepository;
import uk.gov.hmcts.ccd.service.messaging.MessagePublisherParams;
import uk.gov.hmcts.ccd.service.messaging.MessagePublisherRunnable;
import uk.gov.hmcts.ccd.service.messaging.PublishMessageTask;

import java.util.List;
import java.util.stream.Collectors;

@Configuration
public class SchedulingConfiguration implements SchedulingConfigurer {

    private MessagePublisherParams messagePublisherParams;
    private MessageQueueCandidateRepository messageQueueCandidateRepository;
    private JmsTemplate jmsTemplate;

    public SchedulingConfiguration(MessageQueueCandidateRepository messageQueueCandidateRepository,
                                   MessagePublisherParams messagePublisherParams,
                                   JmsTemplate jmsTemplate) {
        this.messageQueueCandidateRepository = messageQueueCandidateRepository;
        this.messagePublisherParams = messagePublisherParams;
        this.jmsTemplate = jmsTemplate;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        List<PublishMessageTask> enabledTasks = messagePublisherParams.getTasks().stream()
            .filter(PublishMessageTask::isEnabled)
            .collect(Collectors.toList());

        ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();
        taskScheduler.setPoolSize(Math.max(enabledTasks.size(), 1));
        taskScheduler.initialize();
        taskRegistrar.setTaskScheduler(taskScheduler);

        enabledTasks.forEach(task -> scheduleCronTask(task, taskRegistrar));
    }

    private void scheduleCronTask(PublishMessageTask task, ScheduledTaskRegistrar taskRegistrar) {
        Runnable runnableTask =
            new MessagePublisherRunnable(messageQueueCandidateRepository, jmsTemplate, task);
        CronTask cronTask = new CronTask(runnableTask, task.getSchedule());
        taskRegistrar.scheduleCronTask(cronTask);
    }
}
