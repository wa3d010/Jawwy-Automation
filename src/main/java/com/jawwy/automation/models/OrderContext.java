package com.jawwy.automation.models;

import java.util.ArrayList;
import java.util.List;

public class OrderContext {

    private String orderId;
    private String runDuration;
    private String failureReason;
    private String recommendedFix;
    private final List<StepExecution> stepLog = new ArrayList<>();

    public OrderContext(String orderId, String runDuration) {
        this.orderId = orderId;
        this.runDuration = runDuration;
        this.failureReason = null;
        this.recommendedFix = null;
    }

    public void addStep(String stepName, String status) {
        addStep(stepName, status, "0ms");
    }

    public void addStep(String stepName, String status, String duration) {
        stepLog.add(new StepExecution(stepName, status, duration));
    }

    public void setFailureReason(String reason) {
        this.failureReason = reason;
    }

    public void setRecommendedFix(String recommendedFix) {
        this.recommendedFix = recommendedFix;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getRunDuration() {
        return runDuration;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public String getRecommendedFix() {
        return recommendedFix;
    }

    public List<StepExecution> getStepLog() {
        return stepLog;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public void setRunDuration(String runDuration) {
        this.runDuration = runDuration;
    }

    // Inner class for step execution details
    public static class StepExecution {
        private final String stepName;
        private final String status;
        private final String duration;

        public StepExecution(String stepName, String status, String duration) {
            this.stepName = stepName;
            this.status = status;
            this.duration = duration;
        }

        public String getStepName() { return stepName; }
        public String getStatus() { return status; }
        public String getDuration() { return duration; }
    }
}
