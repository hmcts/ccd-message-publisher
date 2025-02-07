package uk.gov.hmcts.ccd.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import uk.gov.hmcts.ccd.data.MessageQueueCandidateRepository;
import uk.gov.hmcts.ccd.service.MessagePublisherRunnable;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.test.util.ReflectionTestUtils;

class SchedulingConfigurationTest {

    private static final String SCHEDULE_1 = "*/10 * * * * *";
    private static final String SCHEDULE_2 = "* 20 * * * *";
    private static final String SCHEDULE_3 = "* * 0 * * ?";

    @InjectMocks
    private SchedulingConfiguration schedulingConfiguration;

    @Spy
    private ScheduledTaskRegistrar scheduledTaskRegistrar;
    @Mock
    private MessagePublisherParams messagePublisherParams;
    @Mock
    private MessageQueueCandidateRepository messageQueueCandidateRepository;
    @Mock
    private JmsTemplate jmsTemplate;
    @Captor
    private ArgumentCaptor<CronTask> cronTaskCaptor;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void shouldConfigureTasks() {
        List<PublishMessageTask> tasks = List.of(
            PublishMessageTask.builder().schedule(SCHEDULE_1).build(),
            PublishMessageTask.builder().schedule(SCHEDULE_2).build(),
            PublishMessageTask.builder().schedule(SCHEDULE_3).build()
        );
        when(messagePublisherParams.getEnabledTasks()).thenReturn(tasks);

        schedulingConfiguration.configureTasks(scheduledTaskRegistrar);

        assertThat(((ThreadPoolTaskScheduler)scheduledTaskRegistrar.getScheduler()).getPoolSize(), is(3));
        verify(scheduledTaskRegistrar, times(3)).scheduleCronTask(cronTaskCaptor.capture());

        cronTaskCaptor.getAllValues().forEach(cronTask -> {
            Object runnable = ReflectionTestUtils.getField(cronTask.getRunnable(), "runnable");
            assertThat(runnable, instanceOf(MessagePublisherRunnable.class));
        });

        assertAll(
            () -> assertThat(cronTaskCaptor.getAllValues().get(0).getExpression(), is(SCHEDULE_1)),
            () -> assertThat(cronTaskCaptor.getAllValues().get(1).getExpression(), is(SCHEDULE_2)),
            () -> assertThat(cronTaskCaptor.getAllValues().get(2).getExpression(), is(SCHEDULE_3))
        );
    }
}
