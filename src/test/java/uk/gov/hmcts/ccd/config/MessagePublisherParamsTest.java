package uk.gov.hmcts.ccd.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertAll;

class MessagePublisherParamsTest {

    private MessagePublisherParams messagePublisherParams;

    private PublishMessageTask task1;
    private PublishMessageTask task2;
    private PublishMessageTask task3;

    @BeforeEach
    void setUp() {
        messagePublisherParams = new MessagePublisherParams();
    }

    @Test
    void shouldGetEnabledTasks() {
        task1 = PublishMessageTask.builder().enabled(true).build();
        task2 = PublishMessageTask.builder().enabled(false).build();
        task3 = PublishMessageTask.builder().enabled(true).build();
        messagePublisherParams.setTasks(List.of(task1, task2, task3));

        List<PublishMessageTask> result = messagePublisherParams.getEnabledTasks();

        assertAll(
            () -> assertThat(result.size(), is(2)),
            () -> assertThat(result.get(0), is(task1)),
            () -> assertThat(result.get(1), is(task3))
        );
    }
}
