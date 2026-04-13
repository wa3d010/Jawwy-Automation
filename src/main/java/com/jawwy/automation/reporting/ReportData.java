package com.jawwy.automation.reporting;

import com.jawwy.automation.models.OrderContext;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class ReportData {

    private final String flow;
    private final int    runs;
    private final String timestamp;
    private final String environment;
    private final String allureReportUrl;

    private final List<OrderContext> completedOrders = new ArrayList<>();
    private final List<OrderContext> failedOrders    = new ArrayList<>();

    public ReportData(String flow, int runs, String environment) {
        this(flow, runs, environment, "");
    }

    public ReportData(String flow, int runs, String environment, String allureReportUrl) {
        this.flow            = flow;
        this.runs            = runs;
        this.environment     = environment;
        this.allureReportUrl = isNotBlank(allureReportUrl) ? allureReportUrl.trim() : "";
        this.timestamp       = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    public void addCompleted(OrderContext ctx) { completedOrders.add(ctx); }
    public void addFailed(OrderContext ctx)    { failedOrders.add(ctx);    }

    public String getFlow()        { return flow;            }
    public int getRuns()           { return runs;            }
    public String getTimestamp()   { return timestamp;       }
    public String getEnvironment() { return environment;     }
    public String getAllureReportUrl() { return allureReportUrl; }
    public List<OrderContext> getCompleted()  { return completedOrders; }
    public List<OrderContext> getFailed()     { return failedOrders;    }

    private static boolean isNotBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
