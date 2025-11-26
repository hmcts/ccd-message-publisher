package uk.gov.hmcts.ccd.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import uk.gov.hmcts.ccd.BaseTest;
import uk.gov.hmcts.ccd.data.MessageQueueCandidateEntity;
import uk.gov.hmcts.ccd.data.MessageQueueCandidateRepository;

import java.time.LocalDateTime;
import java.util.Arrays;

import static org.springframework.boot.actuate.health.Status.UP;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class MessagePublisherLivenessHealthIndicatorWebIT extends BaseTest {

    private static final String CLEANUP_SCRIPT = "classpath:sql/cleanup-message-queue-candidates.sql";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MessageQueueCandidateRepository repository;

    @Test
    @Sql(scripts = {CLEANUP_SCRIPT}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    @DirtiesContext
    void shouldReportUpWhenNoMessagesExist() throws Exception {
        assertLivenessHealthStatus(UP);
    }

    @Test
    @Sql(scripts = {CLEANUP_SCRIPT}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    @DirtiesContext
    void shouldReportUpWhenRecentUnpublishedMessageExists() throws Exception {
        // create a recent unpublished message (within 5 minutes window)
        MessageQueueCandidateEntity msg = createMessageEntity(
            LocalDateTime.now().minusMinutes(1),
            "UNPUBLISHED_RECENT",
            "{\"test\": \"recent\"}"
        );
        repository.saveAll(Arrays.asList(msg));

        assertLivenessHealthStatus(UP);
    }

    private void assertLivenessHealthStatus(org.springframework.boot.actuate.health.Status status) throws Exception {
        mockMvc.perform(get("/health/liveness"))
            .andExpect(
                jsonPath("$.components.messagePublisherLiveness.status")
                    .value(status.toString())
            );
    }

    private MessageQueueCandidateEntity createMessageEntity(LocalDateTime timestamp,
                                                            String messageType,
                                                            String messageInfo) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode messageInformation = objectMapper.readTree(messageInfo);

            MessageQueueCandidateEntity entity = new MessageQueueCandidateEntity();
            entity.setTimeStamp(timestamp);
            entity.setMessageType(messageType);
            entity.setMessageInformation(messageInformation);
            return entity;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create message entity", e);
        }
    }
}


