package uk.gov.hmcts.ccd.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.test.web.servlet.MockMvc;
import uk.gov.hmcts.ccd.BaseTest;

import static org.springframework.boot.actuate.health.Status.OUT_OF_SERVICE;
import static org.springframework.boot.actuate.health.Status.UP;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class MessagePublisherReadinessHealthIndicatorWebIT extends BaseTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void shouldReportUpWhenApplicationIsReady() throws Exception {
        assertReadinessUp();
    }

    @Test
    void shouldReportUnavailableWhenRefusingTrafficAndRecoverWhenReady() throws Exception {
        try {
            AvailabilityChangeEvent.publish(applicationContext, ReadinessState.REFUSING_TRAFFIC);

            mockMvc.perform(get("/health/readiness"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(OUT_OF_SERVICE.toString()))
                .andExpect(jsonPath("$.components.readinessState.status").value(OUT_OF_SERVICE.toString()));

            AvailabilityChangeEvent.publish(applicationContext, ReadinessState.ACCEPTING_TRAFFIC);

            assertReadinessUp();
        } finally {
            AvailabilityChangeEvent.publish(applicationContext, ReadinessState.ACCEPTING_TRAFFIC);
        }
    }

    private void assertReadinessUp() throws Exception {
        mockMvc.perform(get("/health/readiness"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(UP.toString()))
            .andExpect(jsonPath("$.components.readinessState.status").value(UP.toString()));
    }
}
