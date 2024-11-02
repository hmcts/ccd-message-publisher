package uk.gov.hmcts.ccd.data;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;


@Table(name = "message_queue_candidates")
@Entity
@Data
public class MessageQueueCandidateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String messageType;
    private LocalDateTime timeStamp;
    private LocalDateTime published;
    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode messageInformation;
}
