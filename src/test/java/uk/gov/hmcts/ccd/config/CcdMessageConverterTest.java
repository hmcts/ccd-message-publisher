package uk.gov.hmcts.ccd.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.activemq.command.ActiveMQBytesMessage;
import org.apache.qpid.jms.message.JmsBytesMessage;
import org.apache.qpid.jms.message.facade.JmsBytesMessageFacade;
import org.apache.qpid.jms.provider.amqp.message.AmqpJmsBytesMessageFacade;
import org.apache.qpid.jms.provider.amqp.message.AmqpJmsMessageFacade;
import org.apache.qpid.proton.amqp.Symbol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import jakarta.jms.BytesMessage;
import jakarta.jms.JMSException;
import jakarta.jms.Session;
import java.io.IOException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

class CcdMessageConverterTest {

    private CcdMessageConverter messageConverter;

    @Mock
    private Session session;

    @Mock
    private JsonNode input;

    private BytesMessage message;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        messageConverter = new CcdMessageConverter();
    }

    @Test
    void shouldMapToBytesMessageForActiveMQMessage() throws IOException, JMSException {
        message = new ActiveMQBytesMessage();
        Mockito.when(session.createBytesMessage()).thenReturn(message);

        BytesMessage result = messageConverter.mapToBytesMessage(input, session, objectMapper.writer());

        assertAll(
            () -> assertThat(result, is(message))
        );
    }

    @Test
    void shouldMapToBytesMessageForQpidMessage() throws IOException, JMSException {
        JmsBytesMessageFacade facade = new AmqpJmsBytesMessageFacade();
        message = new JmsBytesMessage(facade);
        Mockito.when(session.createBytesMessage()).thenReturn(message);

        BytesMessage result = messageConverter.mapToBytesMessage(input, session, objectMapper.writer());

        assertAll(
            () -> assertThat(result, is(message)),
            () -> assertThat(((AmqpJmsMessageFacade) ((JmsBytesMessage) result).getFacade()).getContentType(),
                             is(Symbol.valueOf(APPLICATION_JSON_VALUE)))
        );
    }
}
