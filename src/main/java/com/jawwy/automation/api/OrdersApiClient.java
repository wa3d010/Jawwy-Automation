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
        return createOrderByPayload("payloads/orders/NewAct-Online.json");
    }

    @Step("Create MNP Port In Offline order")
    public String createMnpPortInOfflineOrder() {
        return createOrderByPayload("payloads/orders/MNP-IN-Off.json");
    }

    public String createOrderByFlow(String orderFlow) {
        String payloadPath = isMnpFlow(orderFlow)
                ? "payloads/orders/MNP-IN-Off.json"
                : "payloads/orders/NewAct-Online.json";

        return createOrderByPayload(payloadPath);
    }

    private boolean isMnpFlow(String orderFlow) {
        return orderFlow != null && orderFlow.toLowerCase().contains("mnp");
    }

    @Step("Create order through EOC order API")
    private String createOrderByPayload(String payloadPath) {
        ActionLogger.step(LOGGER, "Loading order payload: " + payloadPath);

        String template = PayloadLoader.load(payloadPath);
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
