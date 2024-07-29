package uk.gov.hmcts.ccd.config;

import static org.junit.Assert.assertNotNull;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.support.converter.MessageConverter;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSException;

public class JacksonConfigurationTest {

    public static JacksonConfiguration jacksonConfiguration = new JacksonConfiguration();

    @Test
    void objectMapperCreates() throws IOException, JMSException {
        
        ObjectMapper objectMapper = jacksonConfiguration.defaultObjectMapper();
        assertNotNull(objectMapper);
    }

    @Test
    void connectionFactoryCreates() throws IOException, JMSException {
        
        ConnectionFactory factory = jacksonConfiguration.connectionFactory("vm://localhost?broker.persistent=false");

        assertNotNull(factory);
    }

    @Test
    void shouldMapToBytesMessageForActiveMQMessage() throws IOException, JMSException {
        
        ConnectionFactory factory = jacksonConfiguration.connectionFactory("vm://localhost?broker.persistent=false");
        JmsTemplate jmsTemplate = jacksonConfiguration.jmsTemplate(factory);

        assertNotNull(jmsTemplate);
    }

    @Test
    void jacksonJmsMessageConverterCreates() throws IOException, JMSException {
        
        MessageConverter messageConverter = jacksonConfiguration.jacksonJmsMessageConverter();

        assertNotNull(messageConverter);
    }
    
}
