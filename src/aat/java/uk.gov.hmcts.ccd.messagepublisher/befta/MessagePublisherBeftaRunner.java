package uk.gov.hmcts.ccd.messagepublisher.befta;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;

import io.cucumber.junit.Cucumber;
import io.cucumber.junit.CucumberOptions;
import uk.gov.hmcts.befta.BeftaMain;
import uk.gov.hmcts.ccd.messagepublisher.befta.publishing.PreviewPublishingConfiguration;

import java.net.URI;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

@RunWith(Cucumber.class)
@CucumberOptions(plugin = "json:target/cucumber.json",
    glue = {"uk.gov.hmcts.befta.player", "uk.gov.hmcts.ccd.messagepublisher.befta.publishing"},
    features = { "classpath:features" },
    tags = "@Functional and @Publishing and not @Smoke and not @Ignore")
public class MessagePublisherBeftaRunner {

    private MessagePublisherBeftaRunner() {
      // Hide Utility Class Constructor : Utility classes should not have a public or default constructor (squid:S1118)
    }

    @BeforeClass
    public static void setUp() {
        String testUrl = System.getenv("TEST_URL");
        assertNotNull("TEST_URL is required to identify the publishing preview", testUrl);
        String host = URI.create(testUrl).getHost();
        assertNotNull("TEST_URL must be an absolute URL with a hostname", host);
        assumeTrue("Publishing E2E is preview-only; shared deployed environments are excluded",
            host.endsWith(".preview.platform.hmcts.net"));
        assertTrue("Preview publishing E2E is not configured. Verify isolated topic routing and CI bindings, "
            + "then set PUBLISHING_E2E_ENABLED=true. No case has been created.",
            "true".equals(System.getenv("PUBLISHING_E2E_ENABLED")));
        new PreviewPublishingConfiguration(System.getenv());
        BeftaMain.setUp(new MessagePublisherTestAutomationAdapter());
    }

    @AfterClass
    public static void tearDown() {
        BeftaMain.tearDown();
    }

}
