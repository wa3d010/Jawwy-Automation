package com.jawwy.automation.api.callbacks;

import com.jawwy.automation.api.ApiSupport;
import com.jawwy.automation.payload.PayloadLoader;
import com.jawwy.automation.payload.TemplateEngine;
import com.jawwy.automation.reporting.ActionLogger;
import io.restassured.response.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;

public class MnpAckCallbackClient extends ApiSupport {

    private static final Logger LOGGER = LoggerFactory.getLogger(MnpAckCallbackClient.class);

    public void send(String portReqFormId) {
        ActionLogger.step(LOGGER, "Sending MNP-Ack callback for portReqFormID " + portReqFormId);

        String template = PayloadLoader.load("payloads/callbacks/MNP-Ack.json");
        Map<String, String> replacements = new HashMap<>();
        replacements.put("portReqFormID", portReqFormId);
        String body = TemplateEngine.apply(template, replacements);

        String baseUrl = config.mnpCallbackBaseUrl();
        String app = config.applicationContext();

        AssertionError lastFailure = null;

        for (int attempt = 1; attempt <= config.mnpAckRetries(); attempt++) {
            try {
                Response response = given()
                        .spec(requestSpec())
                        .basePath(baseUrl)
                        .pathParam("app", app)
                        .body(body)
                        .when()
                        .post("/{app}/mnp/port");

                int actualStatus = response.statusCode();
                if (actualStatus == 200 || actualStatus == 204) {
                    ActionLogger.step(LOGGER, "MNP-Ack callback sent successfully for portReqFormID " + portReqFormId);
                    return;
                }

                String responseBody = safeResponseBody(response);
                throw new AssertionError("Expected status code <200> but was <" + actualStatus + ">."
                        + " MNP-Ack response body: " + responseBody);
            } catch (AssertionError failure) {
                lastFailure = failure;
                if (attempt == config.mnpAckRetries()) {
                    throw failure;
                }

                ActionLogger.warn(LOGGER,
                        "MNP-Ack callback attempt " + attempt + "/" + config.mnpAckRetries()
                                + " failed for portReqFormID " + portReqFormId + ". " + failure.getMessage() + " Retrying...");
                sleep(config.mnpAckRetryIntervalMs(), "Interrupted while retrying MNP-Ack callback.");
            }
        }

        if (lastFailure != null) {
            throw lastFailure;
        }
    }

    private String safeResponseBody(Response response) {
        try {
            String body = response.getBody().asString();
            return body == null || body.isBlank() ? "<empty>" : body;
        } catch (RuntimeException exception) {
            LOGGER.warn("Unable to read MNP-Ack response body", exception);
            return "<unavailable>";
        }
    }

    private void sleep(long delayMs, String interruptionMessage) {
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interruptionMessage, exception);
        }
    }
}
