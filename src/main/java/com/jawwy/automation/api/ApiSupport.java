package com.jawwy.automation.api;

import com.jawwy.automation.config.FrameworkConfig;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.specification.RequestSpecification;

public abstract class ApiSupport {

    protected final FrameworkConfig config = FrameworkConfig.getInstance();

    protected RequestSpecification requestSpec() {
        return new RequestSpecBuilder()
                .setBaseUri(config.apiBaseUrl())
                .setRelaxedHTTPSValidation()
                .addHeader("Content-Type", "application/json")
                .build();
    }
}
