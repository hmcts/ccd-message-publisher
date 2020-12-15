package uk.gov.hmcts.ccd;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(classes = {
    MessagePublisherApplication.class
})
@ActiveProfiles("itest")
public class BaseTest {
}
