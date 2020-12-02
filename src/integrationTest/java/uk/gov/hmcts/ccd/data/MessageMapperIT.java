package uk.gov.hmcts.ccd.data;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uk.gov.hmcts.ccd.BaseTest;

import java.time.LocalDateTime;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertAll;

class MessageMapperIT extends BaseTest {

    @Autowired
    private MessageMapper messageMapper;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Test
    void shouldMapMessageQueueCandidateEntityToMessageDto() throws JsonProcessingException {
        MessageQueueCandidateEntity entity = new MessageQueueCandidateEntity();
        entity.setId(1L);
        entity.setTimeStamp(LocalDateTime.of(2020, 12, 30, 18, 45, 00));
        entity.setPublished(LocalDateTime.now());
        entity.setMessageType("MESSAGE_TYPE");
        entity.setMessageInformation(objectMapper.readTree("{\"key\":\"value\"}"));

        MessageDTO result = messageMapper.toMessageDto(entity);

        assertAll(
            () -> assertThat(result.getTimeStamp(), is(entity.getTimeStamp())),
            () -> assertThat(result.getMessageType(), is(entity.getMessageType())),
            () -> assertThat(result.getMessageInformation(), is(entity.getMessageInformation()))
        );
    }
}