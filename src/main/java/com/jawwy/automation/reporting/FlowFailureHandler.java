package com.jawwy.automation.reporting;

import com.jawwy.automation.models.OrderContext;

public final class FlowFailureHandler {

    private FlowFailureHandler() {
    }

    public static void applyFailure(int run, OrderContext ctx, Throwable failure, String stepStatuses) {
        String stage = detectStage(stepStatuses);
        String rawReason = rawReason(failure);
        String rootCause = rootCause(stage, rawReason);
        String fix = recommendedFix(stage, rawReason);

        ctx.setFailureReason(rootCause);
        ctx.setRecommendedFix(fix);

        String failedId = ctx.getOrderId() != null ? ctx.getOrderId() : "N/A";
        System.out.println("END RUN #" + run + " -> FAILED | ORDER ID: " + failedId);
        System.out.println("  Failure Stage  : " + stage);
        System.out.println("  Detected Error : " + rawReason);
        System.out.println("  Root Cause     : " + rootCause);
        System.out.println("  Recommended Fix: " + fix);
    }

    public static String rawReason(Throwable failure) {
        Throwable root = rootCauseThrowable(failure);
        String message = root.getMessage();
        return message != null && !message.isBlank()
                ? message
                : root.getClass().getSimpleName() + ": Unknown error";
    }

    private static String rootCause(String stage, String reason) {
        if ("ENVIRONMENT_VALIDATION".equals(stage)) {
            return "Environment validation failed - local EOC or Mockoon service is not listening on the configured port.";
        }
        if ("ORDER_CREATION".equals(stage)) {
            return "Order creation failed - DB tunnel, EOC server, or Mockoon API endpoint may not be running.";
        }
        if (contains(reason, "UPDATE_ORDER_SATATE_ERROR") || contains(reason, "UPDATE_ORDER_STATE_ERROR")) {
            return "Order reached ERROR state - Mockoon is not started or mock endpoints are misconfigured.";
        }
        if ("ESB_POLLING".equals(stage)) {
            return "Expected ESB step was not received - Mockoon may not be started or the collection is not loaded.";
        }
        if ("CALLBACKS".equals(stage)) {
            return "Callback failed - check that Mockoon is running and the LMD, Biometrics, Comptel, or MNP mocks are active.";
        }
        if ("PROVISIONING".equals(stage)) {
            return "Provisioning timeout - ESB_027 was not received. Check the Mockoon provisioning mock.";
        }
        if ("SKIP_MANUAL_TASK".equals(stage) && contains(reason, "input[type='password']")) {
            return "EOC UI is not reachable - login page timed out. EOC server may be down.";
        }
        if ("SKIP_MANUAL_TASK".equals(stage)) {
            return "UI task skip failed - SHARING_LIMITS_COMP stage was not found or the UI action failed.";
        }
        if ("DB_COMPLETION_CHECK".equals(stage)) {
            return "Order did not reach CLOSED.COMPLETED - the manual task may not have completed correctly.";
        }
        if (contains(reason, "Mockoon is not started") || contains(reason, "nothing is listening on localhost:8081")) {
            return "Mockoon or local EOC service is not running on the configured localhost port.";
        }
        return "Unknown failure - check the full Jenkins console log.";
    }

    private static String recommendedFix(String stage, String reason) {
        if ("ENVIRONMENT_VALIDATION".equals(stage) || "ORDER_CREATION".equals(stage)) {
            return "Open MobaXterm and verify the DB tunnel, EOC server, and local Mockoon/API port are running.";
        }
        if (contains(reason, "UPDATE_ORDER_SATATE_ERROR") || contains(reason, "UPDATE_ORDER_STATE_ERROR")) {
            return "Start Mockoon and verify the mock collection is loaded with all endpoints active.";
        }
        if ("ESB_POLLING".equals(stage)) {
            return "Start Mockoon and verify the collection is running on the configured port.";
        }
        if ("CALLBACKS".equals(stage)) {
            return "Start Mockoon and verify LMD, Biometrics, Comptel, and MNP callback endpoints are configured.";
        }
        if ("PROVISIONING".equals(stage)) {
            return "Start Mockoon and verify the provisioning mock endpoint is active.";
        }
        if ("SKIP_MANUAL_TASK".equals(stage) && contains(reason, "input[type='password']")) {
            return "Verify EOC is running and reachable, then rerun the batch.";
        }
        if ("SKIP_MANUAL_TASK".equals(stage)) {
            return "Verify the EOC worklist UI is accessible and the SHARING_LIMITS_COMP task exists.";
        }
        if ("DB_COMPLETION_CHECK".equals(stage)) {
            return "Check EOC worklist and DB state, then rerun after the manual task completes.";
        }
        if (contains(reason, "Mockoon is not started") || contains(reason, "nothing is listening on localhost:8081")) {
            return "Start Mockoon or the local EOC service on the configured port, then rerun.";
        }
        return "Rerun the workflow and inspect the full console output.";
    }

    private static String detectStage(String stepStatuses) {
        if (stepStatuses == null || stepStatuses.isBlank()) {
            return "UNKNOWN";
        }
        String normalized = stepStatuses.toUpperCase();
        if (normalized.contains("ENVIRONMENT VALIDATION=FAILED")) {
            return "ENVIRONMENT_VALIDATION";
        }
        if (normalized.contains("CREATE ORDER=FAILED")) {
            return "ORDER_CREATION";
        }
        if (normalized.contains("RESOLVE LMD ID=FAILED")
                || normalized.contains("RESOLVE PORTREQFORMID=FAILED")
                || normalized.contains("COMPTEL CALLBACKS=FAILED")) {
            return "ESB_POLLING";
        }
        if (normalized.contains("BIOMETRICS CALLBACK=FAILED")
                || normalized.contains("LMD106 CALLBACK=FAILED")
                || normalized.contains("MNP-ACK CALLBACK=FAILED")
                || normalized.contains("MNP-ACCEPT CALLBACK=FAILED")) {
            return "CALLBACKS";
        }
        if (normalized.contains("PROVISIONING CALLBACK=FAILED")) {
            return "PROVISIONING";
        }
        if (normalized.contains("MANUAL TASK UI=FAILED")) {
            return "SKIP_MANUAL_TASK";
        }
        if (normalized.contains("MANUAL TASK DB VERIFICATION=FAILED")) {
            return "DB_COMPLETION_CHECK";
        }
        return "UNKNOWN";
    }

    private static Throwable rootCauseThrowable(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private static boolean contains(String value, String needle) {
        return value != null && value.contains(needle);
    }
}
