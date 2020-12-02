package uk.gov.hmcts.ccd.data;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.ccd.BaseTest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Transactional
class MessageQueueCandidateRepositoryIT extends BaseTest {

    private static final String INSERT_DATA_SCRIPT = "classpath:sql/insert-message-queue-candidates.sql";
    private static final String MESSAGE_TYPE = "FIRST_MESSAGE_TYPE";

    @Autowired
    private MessageQueueCandidateRepository repository;

    @Test
    @Sql(INSERT_DATA_SCRIPT)
    void shouldFindAllUnpublishedMessagesForMessageType() {
        Slice<MessageQueueCandidateEntity> result = repository
            .findUnpublishedMessages(MESSAGE_TYPE, Pageable.unpaged());

        assertAll(
            () -> assertThat(result.getContent().size(), is(5)),
            () -> assertOrderedByTimestamp(result.getContent()),
            () -> assertTrue(result.get().allMatch(entity -> entity.getMessageType().equals(MESSAGE_TYPE)))
        );
    }

    @Test
    @Sql(INSERT_DATA_SCRIPT)
    void shouldFindPaginatedUnpublishedMessages() {
        Slice<MessageQueueCandidateEntity> result = repository
            .findUnpublishedMessages(MESSAGE_TYPE, PageRequest.of(1, 2));

        assertAll(
            () -> assertThat(result.getContent().size(), is(2)),
            () -> assertTrue(result.hasPrevious()),
            () -> assertTrue(result.hasNext()),
            () -> assertThat(result.getContent().get(0).getTimeStamp(),
                is(LocalDateTime.of(2020, 11, 28, 18, 0))),
            () -> assertThat(result.getContent().get(1).getTimeStamp(),
                is(LocalDateTime.of(2020, 11, 29, 18, 0)))
        );
    }

    @Test
    @Sql(INSERT_DATA_SCRIPT)
    void shouldReturnAllDataInEntity() {
        Slice<MessageQueueCandidateEntity> result = repository
            .findUnpublishedMessages(MESSAGE_TYPE, PageRequest.of(0, 1));

        assertAll(
            () -> assertThat(result.getContent().size(), is(1)),
            () -> assertThat(result.getContent().get(0).getMessageType(), is(MESSAGE_TYPE)),
            () -> assertThat(result.getContent().get(0).getTimeStamp(),
                is(LocalDateTime.of(2020, 11, 20, 18, 00))),
            () -> assertThat(result.getContent().get(0).getPublished(), is(nullValue())),
            () -> assertThat(result.getContent().get(0).getMessageInformation().toString(),
                is("{\"key\":\"1\"}"))
        );
    }

    @Test
    @Sql(INSERT_DATA_SCRIPT)
    void shouldDeleteMessagesWithRetentionPeriod() {
        int result = repository.deletePublishedMessages(LocalDateTime.now().minusDays(30), MESSAGE_TYPE);

        assertAll(
            () -> assertThat(result, is(2))
        );
    }

    private void assertOrderedByTimestamp(List<MessageQueueCandidateEntity> list) {
        IntStream.range(0, list.size() - 1).forEach(i ->
            assertTrue(list.get(i).getTimeStamp().isBefore(list.get(i + 1).getTimeStamp())));
    }
}
