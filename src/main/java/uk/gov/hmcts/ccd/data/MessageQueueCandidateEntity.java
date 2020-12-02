package uk.gov.hmcts.ccd.data;

import com.fasterxml.jackson.databind.JsonNode;
import com.vladmihalcea.hibernate.type.json.JsonBinaryType;
import lombok.Data;
import org.hibernate.annotations.TypeDef;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.LocalDateTime;


@Table(name = "message_queue_candidates")
@Entity
@TypeDef(
    typeClass = JsonBinaryType.class,
    defaultForType = JsonNode.class
)
@Data
public class MessageQueueCandidateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String messageType;
    private LocalDateTime timeStamp;
    private LocalDateTime published;
    @Column(columnDefinition = "jsonb")
    private JsonNode messageInformation;
}
