package com.jawwy.automation.api;

import com.jawwy.automation.payload.PayloadLoader;
import com.jawwy.automation.payload.TemplateEngine;
import com.jawwy.automation.reporting.ActionLogger;
import io.qameta.allure.Step;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;

public class OrdersApiClient extends ApiSupport {

    private static final Logger LOGGER = LoggerFactory.getLogger(OrdersApiClient.class);

    @Step("Create Jawwy activation order")
    public String createNewActivationOrder() {
        ActionLogger.step(LOGGER, "Loading NewAct-Online order payload");

        String template = PayloadLoader.load("payloads/orders/NewAct-Online.json");
        String shoppingCartId = UUID.randomUUID().toString();
        ZonedDateTime nowUtc = ZonedDateTime.now(ZoneOffset.UTC);
        ZonedDateTime requestStart = nowUtc.minusMinutes(5).truncatedTo(ChronoUnit.MILLIS);
        ZonedDateTime requestCompletion = requestStart.plusDays(1);

        Map<String, String> replacements = new HashMap<>();
        replacements.put("shoppingCartId", shoppingCartId);
        replacements.put("requestStartDate", requestStart.format(DateTimeFormatter.ISO_INSTANT));
        replacements.put("requestCompletedDate", requestCompletion.format(DateTimeFormatter.ISO_INSTANT));

        String body = TemplateEngine.apply(template, replacements);

        ActionLogger.step(LOGGER, "Creating order through EOC order API");
        String orderId = given()
                .spec(requestSpec())
                .pathParam("app", config.applicationContext())
                .pathParam("version", "v1")
                .queryParam("expand", "orderItems")
                .body(body)
                .when()
                .post("/{app}/om/{version}/order")
                .then()
                .statusCode(201)
                .extract()
                .path("id");

        ActionLogger.step(LOGGER, "Order created successfully with ID: " + orderId);
        return orderId;
    }
}
