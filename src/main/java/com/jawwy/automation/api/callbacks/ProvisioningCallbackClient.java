package com.jawwy.automation.api.callbacks;

import com.jawwy.automation.api.ApiSupport;
import com.jawwy.automation.payload.PayloadLoader;
import com.jawwy.automation.payload.TemplateEngine;
import com.jawwy.automation.reporting.ActionLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;

import static io.restassured.RestAssured.given;

public class ProvisioningCallbackClient extends ApiSupport {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProvisioningCallbackClient.class);

    public void send(String orderId, Instant requestStartDate) {
        ActionLogger.step(LOGGER, "Sending provisioning callback for order " + orderId);
        String template = PayloadLoader.load("payloads/callbacks/OrderProvisioning.json");
        String body = TemplateEngine.apply(template, Map.of("requestStartDate", requestStartDate.toString()));

        int statusCode = given()
                .spec(requestSpec())
                .pathParam("app", config.applicationContext())
                .pathParam("orderId", orderId)
                .body(body)
                .when()
                .post("/{app}/updateOrder/trigOrderProv/{orderId}")
                .then()
                .extract()
                .statusCode();

        if (statusCode != 204) {
            throw ActionLogger.failure(LOGGER,
                    "Order Provisioning callback failed for order " + orderId + " with status " + statusCode);
        }
    }
}
