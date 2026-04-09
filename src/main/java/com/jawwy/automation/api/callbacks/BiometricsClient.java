package com.jawwy.automation.api.callbacks;

import com.jawwy.automation.api.ApiSupport;
import com.jawwy.automation.payload.PayloadLoader;
import com.jawwy.automation.payload.TemplateEngine;
import com.jawwy.automation.reporting.ActionLogger;
import io.restassured.response.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static io.restassured.RestAssured.given;

public class BiometricsClient extends ApiSupport {

    private static final Logger LOGGER = LoggerFactory.getLogger(BiometricsClient.class);

    public void send(String orderId) {
        ActionLogger.step(LOGGER, "Sending Biometrics callback for order " + orderId);
        String template = PayloadLoader.load("payloads/callbacks/Biometrics.json");
        String body = TemplateEngine.apply(template, Map.of("orderId", orderId));

        sleep(config.biometricsInitialDelayMs(), "Interrupted before sending biometrics callback.");

        AssertionError lastFailure = null;

        for (int attempt = 1; attempt <= config.biometricsRetries(); attempt++) {
            try {
                Response response = given()
                        .spec(requestSpec())
                        .pathParam("app", config.applicationContext())
                        .pathParam("orderId", orderId)
                        .body(body)
                        .when()
                        .post("/{app}/validate/biometrics/{orderId}");

                int actualStatus = response.statusCode();
                if (actualStatus == 200) {
                    return;
                }

                String responseBody = safeResponseBody(response);
                throw new AssertionError("Expected status code <200> but was <" + actualStatus + ">."
                        + " Biometrics response body: " + responseBody);
            } catch (AssertionError failure) {
                lastFailure = failure;
                if (attempt == config.biometricsRetries()) {
                    throw failure;
                }

                ActionLogger.warn(LOGGER,
                        "Biometrics callback attempt " + attempt + "/" + config.biometricsRetries()
                                + " failed for order " + orderId + ". " + failure.getMessage() + " Retrying...");
                sleep(config.biometricsRetryIntervalMs(), "Interrupted while retrying biometrics callback.");
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
            LOGGER.warn("Unable to read biometrics response body", exception);
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
