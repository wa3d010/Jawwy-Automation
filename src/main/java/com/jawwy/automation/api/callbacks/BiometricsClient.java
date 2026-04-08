package com.jawwy.automation.api.callbacks;

import com.jawwy.automation.api.ApiSupport;
import com.jawwy.automation.payload.PayloadLoader;
import com.jawwy.automation.payload.TemplateEngine;
import com.jawwy.automation.reporting.ActionLogger;
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

        AssertionError lastFailure = null;

        for (int attempt = 1; attempt <= config.biometricsRetries(); attempt++) {
            try {
                given()
                        .spec(requestSpec())
                        .pathParam("app", config.applicationContext())
                        .pathParam("orderId", orderId)
                        .body(body)
                        .when()
                        .post("/{app}/validate/biometrics/{orderId}")
                        .then()
                        .statusCode(200);
                return;
            } catch (AssertionError failure) {
                lastFailure = failure;
                if (attempt == config.biometricsRetries()) {
                    throw failure;
                }

                ActionLogger.warn(LOGGER,
                        "Biometrics callback attempt " + attempt + "/" + config.biometricsRetries()
                                + " failed for order " + orderId + ". Retrying...");
                sleepBeforeRetry();
            }
        }

        if (lastFailure != null) {
            throw lastFailure;
        }
    }

    private void sleepBeforeRetry() {
        try {
            Thread.sleep(config.biometricsRetryIntervalMs());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while retrying biometrics callback.", exception);
        }
    }
}
