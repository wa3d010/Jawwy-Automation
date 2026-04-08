package com.jawwy.automation.api.callbacks;

import com.jawwy.automation.api.ApiSupport;
import com.jawwy.automation.payload.PayloadLoader;
import com.jawwy.automation.reporting.ActionLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.restassured.RestAssured.given;

public class LmdCallbackClient extends ApiSupport {

    private static final Logger LOGGER = LoggerFactory.getLogger(LmdCallbackClient.class);

    public void send(String lmdOrderId) {
        ActionLogger.step(LOGGER, "Sending LMD106 callback for LMD ID " + lmdOrderId);
        String payload = PayloadLoader.load("payloads/callbacks/LMD106.json");

        given()
                .spec(requestSpec())
                .body(payload)
                .when()
                .patch("/{app}/updateOrder/lmdUpdate/{lmdOrderId}",
                        config.applicationContext(),
                        lmdOrderId)
                .then()
                .statusCode(200);
    }
}
