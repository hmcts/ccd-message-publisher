package uk.gov.hmcts.ccd.messagepublisher.befta;

import uk.gov.hmcts.befta.BeftaTestDataLoader;
import uk.gov.hmcts.befta.DefaultBeftaTestDataLoader;
import uk.gov.hmcts.befta.DefaultTestAutomationAdapter;
import uk.gov.hmcts.befta.dse.ccd.DataLoaderToDefinitionStore;
import uk.gov.hmcts.befta.player.BackEndFunctionalTestScenarioContext;

public class MessagePublisherTestAutomationAdapter extends DefaultTestAutomationAdapter {

    private DataLoaderToDefinitionStore loader = new DataLoaderToDefinitionStore(this);

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
                MessagePublisherTestAutomationAdapter.this.loader.addCcdRoles();
                MessagePublisherTestAutomationAdapter.this.loader.importDefinitions();
            }
        };
    }

}
