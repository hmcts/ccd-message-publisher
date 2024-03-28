package uk.gov.hmcts.ccd.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import uk.gov.hmcts.ccd.data.MessageQueueCandidateRepository;
import uk.gov.hmcts.ccd.service.MessagePublisherRunnable;

import java.util.List;
import javax.jms.ConnectionFactory;

@Slf4j
@EnableScheduling
@Configuration
public class SchedulingConfiguration implements SchedulingConfigurer {

    private MessagePublisherParams messagePublisherParams;
    private MessageQueueCandidateRepository messageQueueCandidateRepository;
    private JmsTemplate jmsTemplate;
    private ConnectionFactory connectionFactory;

    public SchedulingConfiguration(MessageQueueCandidateRepository messageQueueCandidateRepository,
                                   MessagePublisherParams messagePublisherParams,
                                   JmsTemplate jmsTemplate, ConnectionFactory connectionFactory) {
        this.messageQueueCandidateRepository = messageQueueCandidateRepository;
        this.messagePublisherParams = messagePublisherParams;
        this.jmsTemplate = jmsTemplate;
        this.connectionFactory = connectionFactory;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        List<PublishMessageTask> enabledTasks = messagePublisherParams.getEnabledTasks();

        ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();
        taskScheduler.setPoolSize(Math.max(enabledTasks.size(), 1));
        taskScheduler.initialize();
        taskRegistrar.setTaskScheduler(taskScheduler);

        enabledTasks.forEach(task -> scheduleCronTask(task, taskRegistrar));
    }

    private void scheduleCronTask(PublishMessageTask task, ScheduledTaskRegistrar taskRegistrar) {
        Runnable runnableTask =
            new MessagePublisherRunnable(messageQueueCandidateRepository, jmsTemplate, task, connectionFactory);
        CronTask cronTask = new CronTask(runnableTask, task.getSchedule());
        taskRegistrar.scheduleCronTask(cronTask);
        log.info(String.format("Task scheduled: %s", task));
    }
}
