package uk.gov.hmcts.ccd.controller;

import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import uk.gov.hmcts.ccd.BaseTest;

import java.sql.SQLException;
import javax.sql.DataSource;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class DependencyHealthWebIT extends BaseTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoSpyBean
    private DataSource dataSource;

    @MockitoSpyBean
    private ConnectionFactory connectionFactory;

    @Test
    void shouldReportDatabaseFailureAndRecover() throws Exception {
        assertHealthy();
        try {
            doThrow(new SQLException("Simulated database connection failure")).when(dataSource).getConnection();

            assertDependencyDown("db");
        } finally {
            reset(dataSource);
        }
        assertHealthy();
    }

    @Test
    void shouldReportBrokerFailureAndRecover() throws Exception {
        assertHealthy();
        try {
            doThrow(new JMSException("Simulated broker connection failure"))
                .when(connectionFactory).createConnection();

            assertDependencyDown("jms");
        } finally {
            reset(connectionFactory);
        }
        assertHealthy();
    }

    private void assertDependencyDown(String component) throws Exception {
        mockMvc.perform(get("/health"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.status").value("DOWN"))
            .andExpect(jsonPath("$.components." + component + ".status").value("DOWN"));

        // Readiness deliberately contains only the application readiness state.
        mockMvc.perform(get("/health/readiness"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"));
    }

    private void assertHealthy() throws Exception {
        mockMvc.perform(get("/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.components.db.status").value("UP"))
            .andExpect(jsonPath("$.components.jms.status").value("UP"));
    }
}
