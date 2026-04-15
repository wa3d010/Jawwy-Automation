package com.jawwy.automation.workflow;

import com.jawwy.automation.api.OrdersApiClient;
import com.jawwy.automation.api.callbacks.BiometricsClient;
import com.jawwy.automation.api.callbacks.ComptelCallbackClient;
import com.jawwy.automation.api.callbacks.LmdCallbackClient;
import com.jawwy.automation.api.callbacks.MnpAckCallbackClient;
import com.jawwy.automation.api.callbacks.MnpAcceptCallbackClient;
import com.jawwy.automation.api.callbacks.ProvisioningCallbackClient;
import com.jawwy.automation.config.FrameworkConfig;
import com.jawwy.automation.db.OrderMessageRepository;
import com.jawwy.automation.reporting.ActionLogger;
import com.jawwy.automation.ui.ManualTaskProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class JawwyOrderJourney {

    private static final Logger LOGGER = LoggerFactory.getLogger(JawwyOrderJourney.class);

    private final FrameworkConfig config = FrameworkConfig.getInstance();
    private final OrdersApiClient ordersApiClient = new OrdersApiClient();
    private final BiometricsClient biometricsClient = new BiometricsClient();
    private final LmdCallbackClient lmdCallbackClient = new LmdCallbackClient();
    private final MnpAckCallbackClient mnpAckCallbackClient = new MnpAckCallbackClient();
    private final MnpAcceptCallbackClient mnpAcceptCallbackClient = new MnpAcceptCallbackClient();
    private final ComptelCallbackClient comptelCallbackClient = new ComptelCallbackClient();
    private final ProvisioningCallbackClient provisioningCallbackClient = new ProvisioningCallbackClient();
    private final OrderMessageRepository orderMessageRepository = new OrderMessageRepository();
    private final ManualTaskProcessor manualTaskProcessor = new ManualTaskProcessor();

    private String orderFlow;
    private String createdOrderId;
    private String extractedLmdId;
    private final List<String> stepStatuses = new ArrayList<>();

    public JawwyOrderJourney() {
        this.orderFlow = resolveOrderFlow();
    }

    public JawwyOrderJourney(String orderFlow) {
        this.orderFlow = orderFlow != null ? orderFlow : resolveOrderFlow();
    }

    public String runFullFlow() throws Exception {
        createOrder();
        sendBiometrics();
        sendLmd106();
        
        // MNP-specific flow
        if (isMnpFlow()) {
            sendMnpAck();
        }
        
        sendComptelCallbacks();
        sendProvisioningIfAvailable();
        handleManualTask();
        return requireCreatedOrderId();
    }

    public String createOrder() throws Exception {
        if (isMnpFlow()) {
            createdOrderId = ordersApiClient.createMnpPortInOfflineOrder();
        } else {
            createdOrderId = ordersApiClient.createNewActivationOrder();
        }
        extractedLmdId = null;
        recordStep("Create Order", "PASSED");
        return createdOrderId;
    }

    public void sendBiometrics() {
        biometricsClient.send(requireCreatedOrderId());
        ActionLogger.step(LOGGER, "Biometrics callback completed");
        recordStep("Biometrics Callback", "PASSED");
    }

    public void sendLmd106() {
        if (extractedLmdId == null) {
            try {
                extractedLmdId = orderMessageRepository.extractLmdId(requireCreatedOrderId(), orderFlow);
            } catch (Exception exception) {
                throw new IllegalStateException("Unable to resolve LMD ID for order " + requireCreatedOrderId(), exception);
            }

            if (extractedLmdId == null) {
                recordStep("Resolve LMD ID", "FAILED");
                throw ActionLogger.failure(LOGGER, "LMD ID was not found for order " + requireCreatedOrderId());
            }

            ActionLogger.step(LOGGER, "Resolved LMD ID " + extractedLmdId + " for order " + requireCreatedOrderId());
            recordStep("Resolve LMD ID", "PASSED");
        }

        lmdCallbackClient.send(requireExtractedLmdId());
        ActionLogger.step(LOGGER, "LMD106 callback completed");
        recordStep("LMD106 Callback", "PASSED");
    }

    public void sendComptelCallbacks() throws Exception {
        ActionLogger.step(LOGGER, "Waiting for Comptel IDs");
        Set<String> sentIds = new LinkedHashSet<>();
        long startedAt = System.currentTimeMillis();

        while (sentIds.size() < 2 && System.currentTimeMillis() - startedAt < config.comptelTimeoutMs()) {
            List<String> ids = orderMessageRepository.extractComptelIds(requireCreatedOrderId());
            boolean sentNewId = false;

            for (String id : ids) {
                if (!sentIds.contains(id)) {
                    comptelCallbackClient.send(id);
                    sentIds.add(id);
                    ActionLogger.step(LOGGER, "Comptel callback sent for ID " + id);
                    sentNewId = true;
                    break;
                }
            }

            if (sentIds.size() < 2) {
                if (!ids.isEmpty() && !sentNewId) {
                    LOGGER.info("Current Comptel IDs for order {} are {} but a second unique ID is still pending",
                            requireCreatedOrderId(), ids);
                }
                Thread.sleep(config.comptelRetryIntervalMs());
            }
        }

        if (sentIds.size() != 2) {
            recordStep("Comptel Callbacks", "FAILED");
            throw ActionLogger.failure(LOGGER, "Expected 2 Comptel IDs but found " + sentIds);
        }

        recordStep("Comptel Callbacks", "PASSED");
    }

    public void sendProvisioningIfAvailable() throws Exception {
        ActionLogger.step(LOGGER, "Waiting for ESB_027 before provisioning callback");
        Thread.sleep(config.provisioningInitialDelayMs());

        long startedAt = System.currentTimeMillis();
        boolean found = false;
        while (System.currentTimeMillis() - startedAt < config.provisioningTimeoutMs()) {
            if (orderMessageRepository.hasEsb027(requireCreatedOrderId())) {
                found = true;
                break;
            }
            Thread.sleep(config.provisioningRetryIntervalMs());
        }

        if (!found) {
            ActionLogger.warn(LOGGER, "ESB_027 was not found. Provisioning callback will be skipped.");
            recordStep("Provisioning Callback", "SKIPPED");
            return;
        }

        provisioningCallbackClient.send(requireCreatedOrderId(), Instant.now());
        ActionLogger.step(LOGGER, "Provisioning callback completed");
        recordStep("Provisioning Callback", "PASSED");
    }

    public void handleManualTask() {
        String orderId = requireCreatedOrderId();
        manualTaskProcessor.processSharingLimitsTask(orderId);
        ActionLogger.step(LOGGER,
                "Manual task UI actions completed. Verifying backend state.");

        if (waitForClosedCompletedState(orderId)) {
            recordStep("Manual Task UI", "PASSED");
            recordStep("Manual Task DB Verification", "PASSED");
            ActionLogger.step(LOGGER, "Manual task processed successfully and verified in DB");
            return;
        }

        recordStep("Manual Task UI", "FAILED");
        recordStep("Manual Task DB Verification", "FAILED");
        failManualTaskDbVerification(orderId);
    }



    public String getCreatedOrderId() {
        return createdOrderId;
    }

    public String getExtractedLmdId() {
        return extractedLmdId;
    }

    public String getOrderFlow() {
        return orderFlow;
    }

    public List<String> getStepStatuses() {
        return List.copyOf(stepStatuses);
    }

    private String requireCreatedOrderId() {
        if (createdOrderId == null) {
            throw new IllegalStateException("Order has not been created yet.");
        }
        return createdOrderId;
    }

    private String requireExtractedLmdId() {
        if (extractedLmdId == null) {
            throw new IllegalStateException("LMD ID has not been resolved yet.");
        }
        return extractedLmdId;
    }

    private boolean waitForClosedCompletedState(String orderId) {
        long startedAt = System.currentTimeMillis();

        while (System.currentTimeMillis() - startedAt < config.manualTaskVerificationTimeoutMs()) {
            try {
                if (orderMessageRepository.hasClosedCompletedState(orderId)) {
                    ActionLogger.step(LOGGER, "DB verification succeeded: order reached CLOSED.COMPLETED");
                    return true;
                }
                Thread.sleep(config.manualTaskVerificationPollIntervalMs());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw ActionLogger.failure(LOGGER, "Interrupted while verifying manual task completion in DB for order "
                        + orderId);
            } catch (Exception exception) {
                throw new IllegalStateException("Unable to verify manual task completion in DB for order "
                        + orderId, exception);
            }
        }
        return false;
    }

    private void failManualTaskDbVerification(String orderId) {
        try {
            List<String> stepNames = orderMessageRepository.getStepNames(orderId);
            throw ActionLogger.failure(LOGGER,
                    "Manual task UI completed, but the backend did not reach CLOSED.COMPLETED within "
                            + config.manualTaskVerificationTimeoutMs() + " ms. Current DB step names: " + stepNames);
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Manual task UI completed, but DB verification failed for order "
                    + orderId, exception);
        }
    }

    private void recordStep(String stepName, String status) {
        String entry = stepName + "=" + status;
        if (!stepStatuses.contains(entry)) {
            stepStatuses.add(entry);
        }
    }

    public void sendMnpAck() {
        if (isMnpFlow()) {
            String portReqFormId = null;
            try {
                portReqFormId = orderMessageRepository.extractPortReqFormId(requireCreatedOrderId());
            } catch (Exception exception) {
                throw new IllegalStateException("Unable to resolve portReqFormID for MNP order " + requireCreatedOrderId(), exception);
            }

            if (portReqFormId == null) {
                recordStep("MNP-Ack Callback", "FAILED");
                throw ActionLogger.failure(LOGGER, "portReqFormID was not found for MNP order " + requireCreatedOrderId());
            }

            ActionLogger.step(LOGGER, "Resolved portReqFormID " + portReqFormId + " for MNP order " + requireCreatedOrderId());
            recordStep("Resolve portReqFormID", "PASSED");

            // Send MNP-Ack callback
            mnpAckCallbackClient.send(portReqFormId);
            ActionLogger.step(LOGGER, "MNP-Ack callback completed");
            recordStep("MNP-Ack Callback", "PASSED");

            // Send MNP-Accept callback with the same portReqFormID
            mnpAcceptCallbackClient.send(portReqFormId);
            ActionLogger.step(LOGGER, "MNP-Accept callback completed");
            recordStep("MNP-Accept Callback", "PASSED");
        }
    }

    private boolean isMnpFlow() {
        return orderFlow != null && orderFlow.toLowerCase().contains("mnp");
    }

    private String resolveOrderFlow() {
        String flow = System.getProperty("order.flow");
        if (flow == null || flow.trim().isEmpty()) {
            flow = System.getenv("ORDER_FLOW");
        }
        return (flow == null || flow.trim().isEmpty()) ? "New Activation Online" : flow.trim();
    }
}
