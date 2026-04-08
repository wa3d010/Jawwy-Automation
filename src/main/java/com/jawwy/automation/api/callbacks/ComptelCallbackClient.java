package com.jawwy.automation.api.callbacks;

import com.jawwy.automation.api.ApiSupport;
import com.jawwy.automation.payload.PayloadLoader;
import com.jawwy.automation.payload.TemplateEngine;
import com.jawwy.automation.reporting.ActionLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static io.restassured.RestAssured.given;

public class ComptelCallbackClient extends ApiSupport {

    private static final Logger LOGGER = LoggerFactory.getLogger(ComptelCallbackClient.class);

    public void send(String comptelOrderId) {
        ActionLogger.step(LOGGER, "Sending Comptel callback for Comptel ID " + comptelOrderId);
        String template = PayloadLoader.load("payloads/callbacks/Comptel70.json");
        String body = TemplateEngine.apply(template, Map.of("comptelOrderId", comptelOrderId));

        int statusCode = given()
                .spec(requestSpec())
                .pathParam("app", config.applicationContext())
                .pathParam("comptelOrderId", comptelOrderId)
                .body(body)
                .when()
                .post("/{app}/updateOrder/comptelUpdate/{comptelOrderId}")
                .then()
                .extract()
                .statusCode();

        if (statusCode != 204) {
            throw ActionLogger.failure(LOGGER,
                    "Comptel callback failed for ID " + comptelOrderId + " with status " + statusCode);
        }
    }
}
