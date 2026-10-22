package uk.gov.hmcts.ccd.functional;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

/** Records the coverage gap while a framework-based approach with verified isolation is investigated. */
@Disabled("Deployed SQL/subscription tests withdrawn; replacement publishing coverage needs verified isolation")
class PublishingTest {

    @Test
    void deployedPublishingCoverageIsWithdrawn() {
        fail("Publishing functional coverage is pending; retain @Disabled until replacement checks are implemented");
    }
}
