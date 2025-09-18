package uk.gov.hmcts.ccd.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.Clock;

@Configuration
public class TimeConfiguration {

    @Bean
    @Primary
    public Clock getClock() {
        return Clock.systemDefaultZone();
    }
}
