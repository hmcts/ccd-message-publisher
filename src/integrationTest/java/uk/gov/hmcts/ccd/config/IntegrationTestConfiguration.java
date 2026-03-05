package uk.gov.hmcts.ccd.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

@TestConfiguration
@ComponentScan("uk.gov.hmcts.ccd")
public class IntegrationTestConfiguration {

    @Bean
    @Primary
    public Clock testClock() {
        // Use a fixed clock for testing
        return Clock.fixed(Instant.parse("2024-01-15T10:00:00Z"), ZoneOffset.UTC);
    }
}
