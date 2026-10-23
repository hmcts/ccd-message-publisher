package uk.gov.hmcts.ccd.messagepublisher.befta;

import uk.gov.hmcts.befta.BeftaTestDataLoader;
import uk.gov.hmcts.befta.DefaultBeftaTestDataLoader;
import uk.gov.hmcts.befta.DefaultTestAutomationAdapter;
import uk.gov.hmcts.befta.dse.ccd.CcdEnvironment;
import uk.gov.hmcts.befta.player.BackEndFunctionalTestScenarioContext;

import java.util.UUID;

public class MessagePublisherTestAutomationAdapter extends DefaultTestAutomationAdapter {

    private String publishingMarker;
    private BackEndFunctionalTestScenarioContext publishingContext;

    public synchronized void resetPublishingScenario() {
        publishingMarker = "publisher-e2e-" + UUID.randomUUID();
        publishingContext = null;
    }

    public BackEndFunctionalTestScenarioContext publishingContext() {
        if (publishingContext == null || publishingContext.getTheResponse() == null) {
            throw new IllegalStateException("BEFTA case-creation response is not available");
        }
        return publishingContext;
    }

    public String publishingMarker() {
        return publishingMarker;
    }

    @Override
    public synchronized Object calculateCustomValue(BackEndFunctionalTestScenarioContext scenarioContext, Object key) {
        if ("publishing_marker".equals(key)) {
            publishingContext = scenarioContext;
            return publishingMarker;
        }
        if (key.toString().startsWith("no_dynamic_injection_")) {
            return key.toString().replace("no_dynamic_injection_","");
        }
        return super.calculateCustomValue(scenarioContext, key);
    }


    @Override
    protected BeftaTestDataLoader buildTestDataLoader() {
        return new DefaultBeftaTestDataLoader(CcdEnvironment.AAT);
    }

}
