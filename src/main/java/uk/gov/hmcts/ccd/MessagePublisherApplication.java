package uk.gov.hmcts.ccd;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.jms.annotation.EnableJms;

@SpringBootApplication
@EnableJms
@SuppressWarnings("HideUtilityClassConstructor") // Spring needs a constructor, its not a utility class
public class MessagePublisherApplication {

    public static void main(final String[] args) {
        SpringApplication.run(MessagePublisherApplication.class, args);
    }
}
