package com.jawwy.automation.models;

import java.util.ArrayList;
import java.util.List;

public class OrderContext {

    private String orderId;
    private String runDuration;
    private String failureReason;
    private final List<String[]> stepLog = new ArrayList<>();

    public OrderContext(String orderId, String runDuration) {
        this.orderId = orderId;
        this.runDuration = runDuration;
        this.failureReason = null;
    }

    public void addStep(String stepName, String status) {
        stepLog.add(new String[]{stepName, status});
    }

    public void setFailureReason(String reason) {
        this.failureReason = reason;
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

    public List<String[]> getStepLog() {
        return stepLog;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public void setRunDuration(String runDuration) {
        this.runDuration = runDuration;
    }
}
