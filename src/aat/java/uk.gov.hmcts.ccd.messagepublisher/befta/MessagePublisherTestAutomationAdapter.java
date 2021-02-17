package uk.gov.hmcts.ccd.messagepublisher.befta;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import uk.gov.hmcts.befta.BeftaTestDataLoader;
import uk.gov.hmcts.befta.DefaultBeftaTestDataLoader;
import uk.gov.hmcts.befta.DefaultTestAutomationAdapter;
import uk.gov.hmcts.befta.dse.ccd.TestDataLoaderToDefinitionStore;
import uk.gov.hmcts.befta.player.BackEndFunctionalTestScenarioContext;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;


public class MessagePublisherTestAutomationAdapter extends DefaultTestAutomationAdapter {

    private static final Logger logger = LoggerFactory.getLogger(MessagePublisherTestAutomationAdapter.class);

    private TestDataLoaderToDefinitionStore loader = new TestDataLoaderToDefinitionStore(this);

    // JDBC driver name and database URL
    static final String JDBC_DRIVER = "org.postgresql.Driver";
    static final String DB_URL = "jdbc:mysql://localhost/STUDENTS";

    String connectionStringTemp = "jdbc:postgresql://"
        + System.getenv("DATA_STORE_DB_HOST")
        + ":"
        + System.getenv("DATA_STORE_DB_PORT")
        + "/"
        + System.getenv("DATA_STORE_DB_NAME")
        + System.getenv("DATA_STORE_DB_OPTIONS")
        + ":?stringtype=unspecified}";

    @Value("${spring.datasource.datasource}")
    String connectionString;
    //  Database credentials
    @Value("${spring.datasource.username}")
    String user;
    @Value("${spring.datasource.password}")
    String pass;

    private void populateTable() {
        Connection conn = null;
        Statement stmt = null;
        try {
            //STEP 2: Register JDBC driver
            Class.forName(JDBC_DRIVER);

            //STEP 3: Open a connection
            logger.info("Connection string is: " + connectionString);
            logger.info("Connection string temp is: " + connectionStringTemp);
            logger.info("Connecting to a selected database...");
            conn = DriverManager.getConnection(connectionString, user, pass);
            logger.info("Connected database successfully...");

            //STEP 4: Execute a query
            logger.info("Inserting records into the table...");
            stmt = conn.createStatement();

            String sql = "INSERT INTO Registration "
                + "VALUES (100, 'Zara', 'Ali', 18)";
            stmt.executeUpdate(sql);
            sql = "INSERT INTO Registration "
                + "VALUES (101, 'Mahnaz', 'Fatma', 25)";
            stmt.executeUpdate(sql);
            sql = "INSERT INTO Registration "
                + "VALUES (102, 'Zaid', 'Khan', 30)";
            stmt.executeUpdate(sql);
            sql = "INSERT INTO Registration "
                + "VALUES(103, 'Sumit', 'Mittal', 28)";
            stmt.executeUpdate(sql);
            logger.info("Inserted records into the table...");

        } catch (SQLException se) {
            //Handle errors for JDBC
            se.printStackTrace();
        } catch (Exception e) {
            //Handle errors for Class.forName
            e.printStackTrace();
        } finally {
            //finally block used to close resources
            try {
                if (stmt != null) {
                    conn.close();
                }
            } catch (SQLException se) {
                se.printStackTrace();
            } // do nothing

            try {
                if (conn != null) {
                    conn.close();
                }
            } catch (SQLException se) {
                se.printStackTrace();
            } //end finally try
        } //end try
        logger.info("Goodbye!");
    }

    @Override
    public Object calculateCustomValue(BackEndFunctionalTestScenarioContext scenarioContext, Object key) {
        if (key.toString().startsWith("no_dynamic_injection_")) {
            return key.toString().replace("no_dynamic_injection_", "");
        }
        return super.calculateCustomValue(scenarioContext, key);
    }


    @Override
    protected BeftaTestDataLoader buildTestDataLoader() {
        return new DefaultBeftaTestDataLoader() {
            @Override
            public void doLoadTestData() {
                populateTable();
                MessagePublisherTestAutomationAdapter.this.loader.addCcdRoles();
                MessagePublisherTestAutomationAdapter.this.loader.importDefinitions();
            }
        };
    }

}
