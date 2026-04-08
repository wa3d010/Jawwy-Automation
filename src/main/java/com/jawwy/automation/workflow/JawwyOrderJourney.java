package com.jawwy.automation.workflow;

import com.jawwy.automation.api.OrdersApiClient;
import com.jawwy.automation.api.callbacks.BiometricsClient;
import com.jawwy.automation.api.callbacks.ComptelCallbackClient;
import com.jawwy.automation.api.callbacks.LmdCallbackClient;
import com.jawwy.automation.api.callbacks.ProvisioningCallbackClient;
import com.jawwy.automation.config.FrameworkConfig;
import com.jawwy.automation.db.OrderMessageRepository;
import com.jawwy.automation.reporting.ActionLogger;
import com.jawwy.automation.ui.ManualTaskProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class JawwyOrderJourney {

    private static final Logger LOGGER = LoggerFactory.getLogger(JawwyOrderJourney.class);

    private final FrameworkConfig config = FrameworkConfig.getInstance();
    private final OrdersApiClient ordersApiClient = new OrdersApiClient();
    private final BiometricsClient biometricsClient = new BiometricsClient();
    private final LmdCallbackClient lmdCallbackClient = new LmdCallbackClient();
    private final ComptelCallbackClient comptelCallbackClient = new ComptelCallbackClient();
    private final ProvisioningCallbackClient provisioningCallbackClient = new ProvisioningCallbackClient();
    private final OrderMessageRepository orderMessageRepository = new OrderMessageRepository();
    private final ManualTaskProcessor manualTaskProcessor = new ManualTaskProcessor();

    private String createdOrderId;
    private String extractedLmdId;

    public String runFullFlow() throws Exception {
        createOrder();
        sendBiometrics();
        sendLmd106();
        sendComptelCallbacks();
        sendProvisioningIfAvailable();
        handleManualTask();
        return requireCreatedOrderId();
    }

    public String createOrder() throws Exception {
        createdOrderId = ordersApiClient.createNewActivationOrder();
        extractedLmdId = orderMessageRepository.extractLmdId(createdOrderId);

        if (extractedLmdId == null) {
            throw ActionLogger.failure(LOGGER, "LMD ID was not found for order " + createdOrderId);
        }

        ActionLogger.step(LOGGER, "Resolved LMD ID " + extractedLmdId + " for order " + createdOrderId);
        manualTaskProcessor.startSharingLimitsTaskAsync(createdOrderId);
        return createdOrderId;
    }

    public void sendBiometrics() {
        biometricsClient.send(requireCreatedOrderId());
        ActionLogger.step(LOGGER, "Biometrics callback completed");
    }

    public void sendLmd106() {
        lmdCallbackClient.send(requireExtractedLmdId());
        ActionLogger.step(LOGGER, "LMD106 callback completed");
    }

    public void sendComptelCallbacks() throws Exception {
        ActionLogger.step(LOGGER, "Waiting for Comptel IDs");
        Set<String> sentIds = new LinkedHashSet<>();
        long startedAt = System.currentTimeMillis();

        while (sentIds.size() < 2 && System.currentTimeMillis() - startedAt < config.comptelTimeoutMs()) {
            List<String> ids = orderMessageRepository.extractComptelIds(requireCreatedOrderId());
            for (String id : ids) {
                if (!sentIds.contains(id)) {
                    comptelCallbackClient.send(id);
                    sentIds.add(id);
                    ActionLogger.step(LOGGER, "Comptel callback sent for ID " + id);
                    break;
                }
            }

            if (sentIds.size() < 2) {
                Thread.sleep(config.comptelRetryIntervalMs());
            }
        }

        if (sentIds.size() != 2) {
            throw ActionLogger.failure(LOGGER, "Expected 2 Comptel IDs but found " + sentIds);
        }
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
            return;
        }

        provisioningCallbackClient.send(requireCreatedOrderId(), Instant.now());
        ActionLogger.step(LOGGER, "Provisioning callback completed");
    }

    public void handleManualTask() {
        manualTaskProcessor.processSharingLimitsTask(requireCreatedOrderId());
        ActionLogger.step(LOGGER, "Manual task processed successfully");
    }

    public String getCreatedOrderId() {
        return createdOrderId;
    }

    public String getExtractedLmdId() {
        return extractedLmdId;
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
}
