package com.jawwy.automation.reporting;

import io.qameta.allure.Allure;
import org.slf4j.Logger;

public final class ActionLogger {

    private ActionLogger() {
    }

    public static void step(Logger logger, String message) {
        logger.info(message);
        Allure.step(message);
    }

    public static void warn(Logger logger, String message) {
        logger.warn(message);
        Allure.step("WARNING: " + message);
    }

    public static RuntimeException failure(Logger logger, String message) {
        logger.error(message);
        Allure.step("FAILED: " + message);
        return new RuntimeException(message);
    }
}
