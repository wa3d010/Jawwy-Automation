package com.jawwy.automation.reporting;

import com.jawwy.automation.models.OrderContext;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class ReportWriter {

    private static final String TARGET_DIR = "target";
    private static final String TXT_PATH = TARGET_DIR + "/execution-report.txt";
    private static final String HTML_PATH = TARGET_DIR + "/execution-report.html";
    private static final String CSS_PATH = TARGET_DIR + "/execution-report.css";

    public static void write(ReportData data) throws Exception {
        Files.createDirectories(Paths.get(TARGET_DIR));
        writeTxt(data);
        writeHtml(data);
    }

    private static void writeTxt(ReportData data) throws Exception {
        String content = buildTxt(data);
        Files.writeString(Paths.get(TXT_PATH), content, StandardCharsets.UTF_8);
        System.out.println(content);
    }

    private static void writeHtml(ReportData data) throws Exception {
        Files.writeString(Paths.get(HTML_PATH), HtmlReportBuilder.build(data), StandardCharsets.UTF_8);
        Files.writeString(Paths.get(CSS_PATH), HtmlReportBuilder.css(), StandardCharsets.UTF_8);
    }

    private static String buildTxt(ReportData data) {
        StringBuilder sb = new StringBuilder();
        sb.append("========================================\n");
        sb.append("Flow            : ").append(data.getFlow()).append("\n");
        sb.append("Environment     : ").append(data.getEnvironment()).append("\n");
        sb.append("Runs Requested  : ").append(data.getRuns()).append("\n");
        sb.append("Completed       : ").append(data.getCompleted().size()).append("\n");
        sb.append("Failed          : ").append(data.getFailed().size()).append("\n");

        sb.append("Completed IDs   : ");
        if (data.getCompleted().isEmpty()) {
            sb.append("None\n");
        } else {
            sb.append("\n");
            for (OrderContext ctx : data.getCompleted()) {
                sb.append("                  ").append(ctx.getOrderId())
                        .append(" | Duration: ").append(ctx.getRunDuration()).append("\n");
                appendSteps(sb, ctx);
            }
        }

        sb.append("Failed IDs      : ");
        if (data.getFailed().isEmpty()) {
            sb.append("None\n");
        } else {
            sb.append("\n");
            for (OrderContext ctx : data.getFailed()) {
                sb.append("                  ")
                        .append(ctx.getOrderId() != null ? ctx.getOrderId() : "N/A")
                        .append(" | Duration: ").append(ctx.getRunDuration())
                        .append(" | Reason: ").append(ctx.getFailureReason() != null ? ctx.getFailureReason() : "-")
                        .append("\n");
                appendSteps(sb, ctx);
            }
        }

        sb.append("Time            : ").append(data.getTimestamp()).append("\n");
        sb.append("========================================\n");
        return sb.toString();
    }

    private static void appendSteps(StringBuilder sb, OrderContext ctx) {
        if (ctx.getStepLog().isEmpty()) {
            sb.append("                    Steps: No stages were recorded\n");
            return;
        }

        sb.append("                    Steps:\n");
        for (OrderContext.StepExecution step : ctx.getStepLog()) {
            sb.append("                      - ")
                    .append(step.getStepName())
                    .append(": ")
                    .append(step.getStatus())
                    .append("\n");
        }
    }
}
