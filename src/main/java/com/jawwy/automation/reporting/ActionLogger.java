package com.jawwy.automation.reporting;

import io.qameta.allure.Allure;
import org.slf4j.Logger;

/**
 * Centralized logging utilities.
 * This class MUST NOT control batch flow.
 */
public final class ActionLogger {

    private ActionLogger() {}

    public static void step(Logger logger, String msg) {
        logger.info(msg);
        Allure.step(msg);
    }

    /**
     * Records a failure and RETURNS an exception.
     * The caller decides whether to throw or continue.
     */
    public static RuntimeException failure(Logger logger, String msg) {
        logger.error(msg);
        Allure.step("FAILED: " + msg);
        return new RuntimeException(msg);
    }

    public static void warn(Logger logger, String msg) {
        logger.warn(msg);
        Allure.step("WARNING: " + msg);
    }
}