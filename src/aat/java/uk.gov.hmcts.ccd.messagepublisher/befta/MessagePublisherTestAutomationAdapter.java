package uk.gov.hmcts.ccd.messagepublisher.befta;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import uk.gov.hmcts.befta.BeftaTestDataLoader;
import uk.gov.hmcts.befta.DefaultBeftaTestDataLoader;
import uk.gov.hmcts.befta.DefaultTestAutomationAdapter;
import uk.gov.hmcts.befta.dse.ccd.TestDataLoaderToDefinitionStore;
import uk.gov.hmcts.befta.player.BackEndFunctionalTestScenarioContext;

public class MessagePublisherTestAutomationAdapter extends DefaultTestAutomationAdapter {

    private static final Logger logger = LoggerFactory.getLogger(MessagePublisherTestAutomationAdapter.class);

    private TestDataLoaderToDefinitionStore loader = new TestDataLoaderToDefinitionStore(this);

    @Override
    public Object calculateCustomValue(BackEndFunctionalTestScenarioContext scenarioContext, Object key) {
        if (key.toString().startsWith("no_dynamic_injection_")) {
            return key.toString().replace("no_dynamic_injection_","");
        }
        return super.calculateCustomValue(scenarioContext, key);
    }


    @Override
    protected BeftaTestDataLoader buildTestDataLoader() {
        return new DefaultBeftaTestDataLoader() {
            @Override
            public void doLoadTestData() {
                logger.info("SERVICE BUS CONNECTION STRING: " + System.getenv("CONNECTION_STRING"));
//                logger.info("SERVICE BUS CONNECTION STRING 1: " + System.getenv("CONNECTION_STRING"));
//                logger.info("SERVICE BUS CONNECTION STRING 2: " + System.getenv("ccd-servicebus-connection-string"));
//                logger.info("DATA STORE: " + System.getenv("DATA_STORE_POSTGRES_PASS"));
//                logger.info("DATA STORE 1: " + System.getenv("data-store-api-POSTGRES-PASS"));
//                logger.info("APP KEY: " + System.getenv("APP_IN_KEY"));
//                logger.info("APP KEY 1: " + System.getenv("AppInsightsInstrumentationKey"));
                MessagePublisherTestAutomationAdapter.this.loader.addCcdRoles();
                MessagePublisherTestAutomationAdapter.this.loader.importDefinitions();
            }
        };
    }

}
