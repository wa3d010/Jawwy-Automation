package com.jawwy.automation.tests.orders;

import com.jawwy.automation.tests.BaseEocTest;
import com.jawwy.automation.workflow.JawwyOrderJourney;
import io.qameta.allure.Allure;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

@Listeners({io.qameta.allure.testng.AllureTestNg.class})
@Epic("EOC Automation")
public class SpTest extends BaseEocTest {

    private final JawwyOrderJourney journey = new JawwyOrderJourney(resolveOrderFlow());
    private final String orderFlow = journey.getOrderFlow();

    @BeforeClass(alwaysRun = true)
    public void skipDuringBatchMode() {
        if (Boolean.parseBoolean(System.getProperty("batch.mode", "false"))) {
            throw new SkipException("SpTest is skipped when Jenkins batch mode is enabled.");
        }
    }

    @Test(description = "Create New Activation Order")
    @Feature("Order Creation")
    public void createOrderTest() throws Exception {
        journey.createOrder();
    }

    @Test(dependsOnMethods = "createOrderTest", description = "Send biometrics callback")
    @Feature("Callbacks")
    public void sendBiometricsTest() {
        journey.sendBiometrics();
    }

    @Test(dependsOnMethods = "sendBiometricsTest", description = "Send LMD106 callback")
    @Feature("Callbacks")
    public void sendLmd106Test() {
        journey.sendLmd106();
    }

    @Test(dependsOnMethods = "sendLmd106Test", description = "Send Comptel callbacks")
    @Feature("Callbacks")
    public void sendComptel70CallbackTest() throws Exception {
        journey.sendComptelCallbacks();
    }

    @Test(dependsOnMethods = "sendComptel70CallbackTest", description = "Send provisioning callback")
    @Feature("Callbacks")
    public void sendOrderProvisioningTest() throws Exception {
        journey.sendProvisioningIfAvailable();
    }

    @Test(dependsOnMethods = "sendOrderProvisioningTest", description = "Handle EOC manual task")
    @Feature("Manual Task")
    public void handleManualTaskUI() {
        journey.handleManualTask();
    }

    @AfterClass(alwaysRun = true)
    public void attachBusinessSummary() {
        String orderId = journey.getCreatedOrderId();
        String steps = String.join(" ; ", journey.getStepStatuses());
        String markdown = "# SP Flow Summary\n\n"
                + "- Order flow: " + orderFlow + "\n"
                + "- Order ID: " + (orderId == null ? "-" : orderId) + "\n"
                + "- Steps: " + (steps.isBlank() ? "-" : steps) + "\n";
        Allure.addAttachment("Business Summary (SP)", "text/markdown", markdown);
    }

    private String resolveOrderFlow() {
        String flow = System.getProperty("order.flow");
        if (flow == null || flow.trim().isEmpty()) {
            flow = System.getenv("ORDER_FLOW");
        }
        return (flow == null || flow.trim().isEmpty()) ? "New Activation Online" : flow.trim();
    }
}
