package com.jawwy.automation.tests.orders;

import com.jawwy.automation.models.OrderContext;
import com.jawwy.automation.reporting.ReportData;
import com.jawwy.automation.reporting.ReportWriter;
import com.jawwy.automation.tests.BaseEocTest;
import com.jawwy.automation.workflow.JawwyOrderJourney;
import io.qameta.allure.Allure;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Listeners({io.qameta.allure.testng.AllureTestNg.class})
@Epic("EOC Automation")
public class SpBatchTest extends BaseEocTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(SpBatchTest.class);

    private static final Path REPORT_DIRECTORY = Paths.get("target", "jenkins");
    private static final Path SUMMARY_REPORT = REPORT_DIRECTORY.resolve("sp-batch-summary.md");
    private static final Path CSV_REPORT = REPORT_DIRECTORY.resolve("sp-batch-summary.csv");
    private static final Path DASHBOARD_DIRECTORY = REPORT_DIRECTORY.resolve("dashboard");
    private static final Path DASHBOARD_INDEX = DASHBOARD_DIRECTORY.resolve("index.html");
    private static final Path DASHBOARD_CSS = DASHBOARD_DIRECTORY.resolve("style.css");

    private final String orderFlow = resolveOrderFlow();
    private final String targetEnv = resolveTargetEnv();

    @BeforeClass(alwaysRun = true)
    public void skipOutsideBatchMode() {
        if (!Boolean.parseBoolean(System.getProperty("batch.mode", "false"))) {
            throw new SkipException("SpBatchTest runs only when batch.mode=true.");
        }
    }

    @Test(description = "Run order flow in Jenkins batch mode")
    @Feature("Jenkins Batch")
    public void runBatch() throws Exception {

        int requestedOrderCount = resolveRequestedOrderCount();
        List<ExecutionResult> results = new ArrayList<>();

        // Create report data for new reporting system
        ReportData reportData = new ReportData(orderFlow, requestedOrderCount, targetEnv, resolveAllureReportUrl());

        LOGGER.info(
                "Starting SP batch: env={}, flow={}, requestedOrders={}",
                targetEnv, orderFlow, requestedOrderCount
        );

        try {
            for (int iteration = 1; iteration <= requestedOrderCount; iteration++) {

                JawwyOrderJourney journey = new JawwyOrderJourney(orderFlow);
                Instant startedAt = Instant.now();
                boolean orderSucceeded = false;

                try {
                    String orderId = journey.runFullFlow();
                    Duration executionDuration = Duration.between(startedAt, Instant.now());

                    ExecutionResult result = ExecutionResult.passed(
                            iteration,
                            startedAt,
                            executionDuration,
                            orderId,
                            summarizeStepStatuses(journey)
                    );

                    results.add(result);

                    // Add to new report data
                    OrderContext ctx = new OrderContext(orderId, formatDuration(executionDuration));
                    for (String step : journey.getStepStatuses()) {
                        String[] parts = step.split("=", 2);
                        if (parts.length == 2) {
                            ctx.addStep(parts[0].trim(), parts[1].trim());
                        }
                    }
                    reportData.addCompleted(ctx);
                    orderSucceeded = true;

                } catch (Throwable ex) {
                    if (ex instanceof VirtualMachineError || ex instanceof ThreadDeath) {
                        throw ex;
                    }
                    Duration executionDuration = Duration.between(startedAt, Instant.now());
                    String orderId = null;
                    String stepStatuses = "";
                    String failureMsg = summarizeFailure(ex);

                    // Safely extract order info even if journey failed early
                    try {
                        orderId = journey.getCreatedOrderId();
                    } catch (Exception e) {
                        LOGGER.warn("Could not get orderId after failure: {}", e.getMessage());
                    }

                    try {
                        stepStatuses = summarizeStepStatuses(journey);
                    } catch (Exception e) {
                        LOGGER.warn("Could not get step statuses after failure: {}", e.getMessage());
                        stepStatuses = "Create Order=UNKNOWN";
                    }

                    ExecutionResult result = ExecutionResult.failed(
                            iteration,
                            startedAt,
                            executionDuration,
                            orderId,
                            stepStatuses + " ; failure=" + failureMsg
                    );

                    // ALWAYS add to results FIRST (this is our source of truth)
                    results.add(result);
                    LOGGER.info("Added failed result to results list (iteration: {}, orderId: {})", iteration, orderId != null ? orderId : "N/A");

                    // Add to report data - ensure failed orders are ALWAYS tracked
                    try {
                        OrderContext ctx = new OrderContext(orderId, formatDuration(executionDuration));
                        // Add all steps that were recorded before failure
                        if (stepStatuses != null && !stepStatuses.isEmpty()) {
                            for (String step : stepStatuses.split("\\s*;\\s*")) {
                                String[] parts = step.split("=", 2);
                                if (parts.length == 2) {
                                    ctx.addStep(parts[0].trim(), parts[1].trim());
                                }
                            }
                        }
                        ctx.setFailureReason(failureMsg);
                        reportData.addFailed(ctx);
                        LOGGER.info("Added failed order {} to report data (orderId: {}, steps: {})",
                                iteration, orderId != null ? orderId : "N/A", ctx.getStepLog().size());
                        LOGGER.info("ReportData now has: {} completed, {} failed",
                                reportData.getCompleted().size(), reportData.getFailed().size());
                    } catch (Exception reportEx) {
                        LOGGER.error("Failed to add order {} to report data: {}", iteration, reportEx.getMessage());
                        reportEx.printStackTrace();
                    }

                    LOGGER.error("Order {} failed: {}", iteration, failureMsg);
                    orderSucceeded = false;
                    // Continue with next order - DO NOT stop on failure
                }

                // Log progress after each order
                int completedCount = reportData.getCompleted().size();
                int failedCount = reportData.getFailed().size();
                LOGGER.info("Progress after order {}: {} completed, {} failed, {} total processed",
                        iteration, completedCount, failedCount, completedCount + failedCount);

                // Write reports after each order (incremental updates)
                try {
                    writeReports(requestedOrderCount, results);
                } catch (Exception writeEx) {
                    LOGGER.error("Failed to write incremental reports: {}", writeEx.getMessage());
                    // Continue even if report writing fails
                }
            }
        } finally {
            // ALWAYS generate final reports, even if loop threw an exception
            try {
                // Fail-safe 1: If reportData is empty but we have results, rebuild from results
                if (reportData.getCompleted().isEmpty() && reportData.getFailed().isEmpty() && !results.isEmpty()) {
                    LOGGER.warn("ReportData is empty but we have {} results. Rebuilding from results.", results.size());
                    for (ExecutionResult r : results) {
                        OrderContext ctx = new OrderContext(r.orderId(), formatDuration(r.duration()));
                        if (r.notes() != null) {
                            for (String step : r.notes().split("\\s*;\\s*")) {
                                String[] parts = step.split("=", 2);
                                if (parts.length == 2) {
                                    if ("failure".equalsIgnoreCase(parts[0].trim())) {
                                        continue;
                                    }
                                    ctx.addStep(parts[0].trim(), parts[1].trim());
                                }
                            }
                        }
                        if (!r.passed()) {
                            String notes = r.notes();
                            int failIdx = notes.indexOf("failure=");
                            if (failIdx >= 0) {
                                ctx.setFailureReason(notes.substring(failIdx + 8));
                            } else {
                                ctx.setFailureReason(notes);
                            }
                            reportData.addFailed(ctx);
                        } else {
                            reportData.addCompleted(ctx);
                        }
                    }
                    LOGGER.info("Rebuilt reportData from results: {} completed, {} failed",
                            reportData.getCompleted().size(), reportData.getFailed().size());
                }
                
                // Fail-safe 2: If still empty, add a placeholder
                if (reportData.getCompleted().isEmpty() && reportData.getFailed().isEmpty()) {
                    LOGGER.warn("ReportData and results are both empty. Adding placeholder.");
                    OrderContext ctx = new OrderContext("N/A", "0s");
                    ctx.setFailureReason("Test execution failed before any orders could be processed");
                    ctx.addStep("Execution", "FAILED");
                    reportData.addFailed(ctx);
                }
                
                // Force write the report
                LOGGER.info("Writing final report: {} completed, {} failed, {} total requested",
                        reportData.getCompleted().size(), reportData.getFailed().size(), requestedOrderCount);
                ReportWriter.write(reportData);
                LOGGER.info("Final execution report written successfully");
                
                // Verify the report was written
                java.nio.file.Path reportPath = Paths.get("target", "execution-report.txt");
                if (Files.exists(reportPath)) {
                    LOGGER.info("Report file exists at: {}", reportPath.toAbsolutePath());
                } else {
                    LOGGER.error("Report file was NOT created at: {}", reportPath.toAbsolutePath());
                }
            } catch (Exception reportEx) {
                LOGGER.error("Failed to generate final execution report: {}", reportEx.getMessage());
                reportEx.printStackTrace();
                // Last resort: Write a minimal report directly from results
                try {
                    StringBuilder sb = new StringBuilder();
                    sb.append("════════════════════════════════════════\n");
                    sb.append("Flow            : ").append(orderFlow).append("\n");
                    sb.append("Environment     : ").append(targetEnv).append("\n");
                    sb.append("Runs Requested  : ").append(requestedOrderCount).append("\n");
                    sb.append("Completed       : 0\n");
                    sb.append("Failed          : ").append(results.size()).append("\n");
                    sb.append("Failed Orders   : \n");
                    for (ExecutionResult r : results) {
                        sb.append("  - Order ").append(r.iteration()).append(": ").append(r.notes()).append("\n");
                    }
                    sb.append("════════════════════════════════════════\n");
                    Files.writeString(Paths.get("target", "execution-report.txt"), sb.toString());
                    Files.writeString(Paths.get("target", "execution-report.html"),
                            "<html><head><link rel=\"stylesheet\" href=\"execution-report.css\"></head><body><main class=\"page\"><section class=\"card\"><h1>Test Execution Failed</h1><pre>"
                                    + sb.toString().replace("<", "&lt;").replace(">", "&gt;")
                                    + "</pre></section></main></body></html>");
                    Files.writeString(Paths.get("target", "execution-report.css"),
                            "body{font-family:Arial,Helvetica,sans-serif;background:#f5f7fb;color:#111827}.page{max-width:1180px;margin:0 auto;padding:28px 18px}.card{background:#fff;border:1px solid #e5e7eb;border-radius:8px;padding:18px}pre{white-space:pre-wrap}");
                    LOGGER.info("Minimal report written as fallback");
                } catch (Exception e) {
                    LOGGER.error("Failed to write minimal report: {}", e.getMessage());
                }
            }
        }

        // ALWAYS add business summary to Allure, even if there were failures
        try {
            Allure.addAttachment(
                    "Business Summary (SP Batch)",
                    "text/markdown",
                    buildMarkdownSummary(requestedOrderCount, results)
            );
        } catch (Exception allureEx) {
            LOGGER.error("Failed to add business summary to Allure: {}", allureEx.getMessage());
        }
    }

    /* ================= Report Writers ================= */

    private void writeReports(int requestedOrderCount, List<ExecutionResult> results) throws IOException {
        Files.createDirectories(REPORT_DIRECTORY);
        Files.createDirectories(DASHBOARD_DIRECTORY);

        Files.writeString(SUMMARY_REPORT, buildMarkdownSummary(requestedOrderCount, results));
        Files.writeString(CSV_REPORT, buildCsvSummary(results));
        Files.writeString(DASHBOARD_CSS, buildDashboardCss());
        Files.writeString(DASHBOARD_INDEX, buildHtmlDashboard(requestedOrderCount, results));
    }

    private String resolveAllureReportUrl() {
        String customUrl = System.getProperty("allure.report.url");
        if (isNotBlank(customUrl)) {
            return customUrl.trim();
        }

        String envUrl = System.getenv("ALLURE_REPORT_URL");
        if (isNotBlank(envUrl)) {
            return envUrl.trim();
        }

        if (Files.exists(Paths.get("target", "allure-report", "index.html"))) {
            return "allure-report/index.html";
        }

        if (Files.exists(Paths.get("target", "site", "allure-maven-plugin", "index.html"))) {
            return "site/allure-maven-plugin/index.html";
        }

        if (Files.exists(Paths.get("target", "allure-results"))) {
            return "allure-results";
        }

        return "";
    }

    private static boolean isNotBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }

    /* ================= Dashboard ================= */

    private String buildHtmlDashboard(int requestedOrderCount, List<ExecutionResult> results) {

        long passedCount = results.stream().filter(ExecutionResult::passed).count();
        long failedCount = results.size() - passedCount;
        String overall = failedCount > 0 ? "FAILED" : "PASSED";
        String overallClass = failedCount > 0 ? "bad" : "good";

        double avgDurationSec = results.stream()
                .mapToLong(r -> r.duration().toMillis())
                .average()
                .orElse(0) / 1000.0;

        StringBuilder html = new StringBuilder();

        html.append("<!doctype html>")
            .append("<html lang=\"en\"><head>")
            .append("<meta charset=\"utf-8\">")
            .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
            .append("<title>Order Flow Dashboard</title>")
            .append("<link rel=\"stylesheet\" href=\"style.css\">")
            .append("</head><body><main class=\"main\">");

        html.append("<h1>Order Flow Report</h1>")
            .append("<p class=\"subtitle\">Flow <b>").append(escapeHtml(orderFlow))
            .append("</b> on <b>").append(escapeHtml(targetEnv)).append("</b></p>")
            .append("<div class=\"pill ").append(overallClass).append("\">Overall: ")
            .append(overall).append("</div>");

        html.append("<div class=\"grid\">")
            .append(metric("Environment", targetEnv))
            .append(metric("Flow", orderFlow))
            .append(metric("Orders", String.valueOf(requestedOrderCount)))
            .append(metric("Passed", String.valueOf(passedCount)))
            .append(metric("Failed", String.valueOf(failedCount)))
            .append(metric("Avg Duration", String.format("%.2fs", avgDurationSec)))
            .append("</div>");

        html.append("<table><thead><tr>")
            .append("<th>#</th><th>Status</th><th>Order</th><th>Duration</th><th>Steps</th>")
            .append("</tr></thead><tbody>");

        for (ExecutionResult r : results) {

            boolean passed = r.passed();
            StepBreakdown breakdown = parseSteps(r.notes());

            html.append("<tr>")
                .append("<td>").append(r.iteration()).append("</td>")
                .append("<td><span class=\"badge ").append(passed ? "good" : "bad").append("\">")
                .append(passed ? "PASSED" : "FAILED").append("</span></td>")
                .append("<td>").append(escapeHtml(valueOrDash(r.orderId()))).append("</td>")
                .append("<td>").append(String.format("%.2fs", r.duration().toMillis() / 1000.0)).append("</td>")
                .append("<td><details ").append(passed ? "" : "open").append(">")
                .append("<summary>Expand</summary><div class=\"steps\">");

            for (StepItem s : breakdown.steps) {
                html.append("<div class=\"step\">")
                    .append("<div>").append(escapeHtml(s.name)).append("</div>")
                    .append("<div>").append(escapeHtml(s.statusLabel)).append("</div>")
                    .append("</div>");
            }

            html.append("</div></details></td></tr>");
        }

        html.append("</tbody></table>")
            .append("</main></body></html>");

        return html.toString();
    }

    private String buildDashboardCss() {
    return String.join("\n",
        ":root{",
        "  --bg:#f5f7fb;",
        "  --surface:#ffffff;",
        "  --surface-2:#f9fafb;",
        "  --text:#111827;",
        "  --muted:#6b7280;",
        "  --line:#e5e7eb;",
        "  --good:#16a34a;",
        "  --bad:#dc2626;",
        "  --shadow:0 10px 30px rgba(17,24,39,0.06);",
        "  --radius:16px;",
        "}",
        "",
        "*{",
        "  box-sizing:border-box;",
        "}",
        "",
        "html{",
        "  -webkit-font-smoothing:antialiased;",
        "  -moz-osx-font-smoothing:grayscale;",
        "}",
        "",
        "body{",
        "  margin:0;",
        "  background:linear-gradient(180deg,#f8fafc 0%,#f5f7fb 100%);",
        "  color:var(--text);",
        "  font:14px/1.5 Inter,system-ui,-apple-system,BlinkMacSystemFont,\"Segoe UI\",sans-serif;",
        "}",
        "",
        ".main{",
        "  max-width:1180px;",
        "  margin:0 auto;",
        "  padding:40px 20px 56px;",
        "}",
        "",
        "h1{",
        "  margin:0 0 8px;",
        "  font-size:32px;",
        "  line-height:1.15;",
        "  font-weight:700;",
        "  letter-spacing:-0.02em;",
        "}",
        "",
        ".subtitle{",
        "  color:var(--muted);",
        "  font-size:15px;",
        "  margin:0 0 18px;",
        "}",
        "",
        ".card{",
        "  background:var(--surface);",
        "  border:1px solid rgba(229,231,235,0.9);",
        "  border-radius:var(--radius);",
        "  padding:18px 18px 16px;",
        "  box-shadow:var(--shadow);",
        "}",
        "",
        ".grid{",
        "  display:grid;",
        "  grid-template-columns:repeat(auto-fit,minmax(180px,1fr));",
        "  gap:14px;",
        "  margin:22px 0 26px;",
        "}",
        "",
        ".card > div:first-child{",
        "  color:var(--muted);",
        "  font-size:13px;",
        "  margin-bottom:8px;",
        "}",
        "",
        ".card b{",
        "  font-size:22px;",
        "  font-weight:700;",
        "  letter-spacing:-0.02em;",
        "}",
        "",
        ".pill{",
        "  display:inline-flex;",
        "  align-items:center;",
        "  gap:8px;",
        "  margin:10px 0 6px;",
        "  padding:8px 14px;",
        "  border-radius:999px;",
        "  font-size:13px;",
        "  font-weight:700;",
        "  letter-spacing:0.02em;",
        "  border:1px solid transparent;",
        "}",
        "",
        ".pill.good{",
        "  color:var(--good);",
        "  background:rgba(22,163,74,0.08);",
        "  border-color:rgba(22,163,74,0.14);",
        "}",
        "",
        ".pill.bad{",
        "  color:var(--bad);",
        "  background:rgba(220,38,38,0.08);",
        "  border-color:rgba(220,38,38,0.14);",
        "}",
        "",
        "table{",
        "  width:100%;",
        "  border-collapse:separate;",
        "  border-spacing:0;",
        "  background:var(--surface);",
        "  border:1px solid rgba(229,231,235,0.9);",
        "  border-radius:var(--radius);",
        "  overflow:hidden;",
        "  box-shadow:var(--shadow);",
        "}",
        "",
        "thead th{",
        "  background:var(--surface-2);",
        "  color:var(--muted);",
        "  font-size:12px;",
        "  font-weight:700;",
        "  text-transform:uppercase;",
        "  letter-spacing:0.06em;",
        "  padding:14px 16px;",
        "  border-bottom:1px solid var(--line);",
        "  text-align:left;",
        "}",
        "",
        "tbody td{",
        "  padding:16px;",
        "  border-bottom:1px solid var(--line);",
        "  vertical-align:top;",
        "}",
        "",
        "tbody tr:last-child td{",
        "  border-bottom:0;",
        "}",
        "",
        "tbody tr:hover{",
        "  background:rgba(249,250,251,0.8);",
        "}",
        "",
        ".badge{",
        "  display:inline-flex;",
        "  align-items:center;",
        "  justify-content:center;",
        "  min-width:88px;",
        "  padding:7px 12px;",
        "  border-radius:999px;",
        "  font-size:12px;",
        "  font-weight:700;",
        "  letter-spacing:0.03em;",
        "  border:1px solid transparent;",
        "}",
        "",
        ".badge.good{",
        "  color:var(--good);",
        "  background:rgba(22,163,74,0.08);",
        "  border-color:rgba(22,163,74,0.14);",
        "}",
        "",
        ".badge.bad{",
        "  color:var(--bad);",
        "  background:rgba(220,38,38,0.08);",
        "  border-color:rgba(220,38,38,0.14);",
        "}",
        "",
        "details{",
        "  background:var(--surface-2);",
        "  border:1px solid var(--line);",
        "  border-radius:12px;",
        "  padding:10px 12px;",
        "}",
        "",
        "details summary{",
        "  cursor:pointer;",
        "  list-style:none;",
        "  font-weight:600;",
        "  color:var(--text);",
        "  user-select:none;",
        "}",
        "",
        "details summary::-webkit-details-marker{",
        "  display:none;",
        "}",
        "",
        "details summary::after{",
        "  content:\"+\";",
        "  float:right;",
        "  color:var(--muted);",
        "  font-weight:700;",
        "}",
        "",
        "details[open] summary::after{",
        "  content:\"–\";",
        "}",
        "",
        ".steps{",
        "  margin-top:12px;",
        "  padding-top:10px;",
        "  border-top:1px solid var(--line);",
        "}",
        "",
        ".step{",
        "  display:flex;",
        "  align-items:center;",
        "  justify-content:space-between;",
        "  gap:16px;",
        "  padding:8px 0;",
        "  color:var(--text);",
        "}",
        "",
        ".step + .step{",
        "  border-top:1px dashed rgba(229,231,235,0.8);",
        "}",
        "",
        ".step div:last-child{",
        "  color:var(--muted);",
        "  font-weight:600;",
        "  white-space:nowrap;",
        "}",
        "",
        "b{",
        "  font-weight:700;",
        "}",
        "",
        "@media (max-width:760px){",
        "  .main{",
        "    padding:28px 14px 40px;",
        "  }",
        "",
        "  h1{",
        "    font-size:26px;",
        "  }",
        "",
        "  table,",
        "  thead,",
        "  tbody,",
        "  th,",
        "  td,",
        "  tr{",
        "    display:block;",
        "  }",
        "",
        "  thead{",
        "    display:none;",
        "  }",
        "",
        "  tbody tr{",
        "    background:var(--surface);",
        "    border:1px solid var(--line);",
        "    border-radius:14px;",
        "    box-shadow:var(--shadow);",
        "    margin-bottom:14px;",
        "    overflow:hidden;",
        "  }",
        "",
        "  tbody td{",
        "    border-bottom:1px solid var(--line);",
        "    padding:12px 14px;",
        "  }",
        "",
        "  tbody td:last-child{",
        "    border-bottom:0;",
        "  }",
        "}"
    );
}
  
    private String metric(String label, String value) {
        return "<div class=\"card\"><div>" + escapeHtml(label) +
               "</div><div><b>" + escapeHtml(value) + "</b></div></div>";
    }

    /* ================= Step Parsing ================= */

    private StepBreakdown parseSteps(String notes) {
        List<StepItem> steps = new ArrayList<>();

        if (!isBlank(notes)) {
            for (String part : notes.split("\\s*;\\s*")) {
                String[] kv = part.split("=", 2);
                String name = kv[0].trim();
                String status = kv.length > 1 ? kv[1].trim() : "UNKNOWN";
                steps.add(new StepItem(name, status));
            }
        }
        return new StepBreakdown(steps);
    }

    private static final class StepBreakdown {
        private final List<StepItem> steps;
        private StepBreakdown(List<StepItem> steps) {
            this.steps = steps;
        }
    }

    private static final class StepItem {
        private final String name;
        private final String statusLabel;
        private StepItem(String name, String statusLabel) {
            this.name = name;
            this.statusLabel = statusLabel;
        }
    }

    /* ================= Execution Result ================= */

    private static final class ExecutionResult {
        private final int iteration;
        private final Instant startedAt;
        private final Duration duration;
        private final String orderId;
        private final String notes;
        private final boolean passed;

        private ExecutionResult(int iteration, Instant startedAt, Duration duration, String orderId, String notes, boolean passed) {
            this.iteration = iteration;
            this.startedAt = startedAt;
            this.duration = duration;
            this.orderId = orderId;
            this.notes = notes;
            this.passed = passed;
        }

        public static ExecutionResult passed(int iteration, Instant startedAt, Duration duration, String orderId, String notes) {
            return new ExecutionResult(iteration, startedAt, duration, orderId, notes, true);
        }

        public static ExecutionResult failed(int iteration, Instant startedAt, Duration duration, String orderId, String notes) {
            return new ExecutionResult(iteration, startedAt, duration, orderId, notes, false);
        }

        public boolean passed() { return passed; }
        public int iteration() { return iteration; }
        public Instant startedAt() { return startedAt; }
        public Duration duration() { return duration; }
        public String orderId() { return orderId; }
        public String notes() { return notes; }
    }

    /* ================= Utility Methods ================= */

    private String resolveOrderFlow() {
        String flow = System.getProperty("order.flow");
        if (flow == null || flow.trim().isEmpty()) {
            flow = System.getenv("ORDER_FLOW");
        }
        return (flow == null || flow.trim().isEmpty()) ? "New Activation Online" : flow.trim();
    }

    private String resolveTargetEnv() {
        String env = System.getProperty("target.env");
        if (env == null || env.trim().isEmpty()) {
            env = System.getenv("TARGET_ENV");
        }
        return (env == null || env.trim().isEmpty()) ? "local" : env.trim();
    }

    private int resolveRequestedOrderCount() {
        String count = System.getProperty("order.count");
        if (count == null || count.trim().isEmpty()) {
            count = System.getenv("ORDER_COUNT");
        }
        try {
            int parsed = Integer.parseInt(count != null ? count.trim() : "1");
            return Math.max(1, parsed);
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private String summarizeStepStatuses(JawwyOrderJourney journey) {
        return String.join(" ; ", journey.getStepStatuses());
    }

    private String summarizeFailure(Throwable ex) {
        Throwable failure = rootCause(ex);
        String message = failure.getMessage();
        
        // Extract the key failure information
        if (message != null) {
            // Check for server errors
            if (message.contains("Mockoon is not started")) {
                return message;
            }
            
            // Extract the failing step from stack trace if available
            String[] lines = message.split("\\n");
            if (lines.length > 0) {
                String firstLine = lines[0];
                // Return the key error message
                if (firstLine.contains("Expected status code")) {
                    return firstLine;
                }
            }
        }
        
        return failure.getClass().getSimpleName() + ": " + (message != null ? message : "Unknown error");
    }

    private Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private String formatDuration(Duration duration) {
        long millis = duration.toMillis();
        return String.format("%.2fs", millis / 1000.0);
    }

    private String buildMarkdownSummary(int requestedOrderCount, List<ExecutionResult> results) {
        long passedCount = results.stream().filter(ExecutionResult::passed).count();
        long failedCount = results.size() - passedCount;
        double avgDurationSec = results.stream()
                .mapToLong(r -> r.duration().toMillis())
                .average()
                .orElse(0) / 1000.0;

        StringBuilder md = new StringBuilder();
        md.append("# SP Batch Summary\n\n");
        md.append("- Environment: ").append(targetEnv).append("\n");
        md.append("- Flow: ").append(orderFlow).append("\n");
        md.append("- Requested orders: ").append(requestedOrderCount).append("\n");
        md.append("- Passed: ").append(passedCount).append("\n");
        md.append("- Failed: ").append(failedCount).append("\n");
        md.append("- Average duration: ").append(String.format("%.2fs", avgDurationSec)).append("\n\n");

        if (failedCount > 0) {
            md.append("## Failed Orders Details\n\n");
            for (ExecutionResult r : results) {
                if (!r.passed()) {
                    md.append("### Order ").append(r.iteration()).append(" - ").append(valueOrDash(r.orderId())).append("\n");
                    md.append("- Duration: ").append(String.format("%.2fs", r.duration().toMillis() / 1000.0)).append("\n");
                    md.append("- Failure: ").append(r.notes()).append("\n\n");
                }
            }
        }

        md.append("| # | Status | Order ID | Duration | Steps |\n");
        md.append("|---|--------|----------|----------|-------|\n");

        for (ExecutionResult r : results) {
            md.append("| ").append(r.iteration()).append(" | ")
              .append(r.passed() ? "PASSED" : "FAILED").append(" | ")
              .append(valueOrDash(r.orderId())).append(" | ")
              .append(String.format("%.2fs", r.duration().toMillis() / 1000.0)).append(" | ")
              .append(valueOrDash(r.notes())).append(" |\n");
        }

        return md.toString();
    }

    private String buildCsvSummary(List<ExecutionResult> results) {
        StringBuilder csv = new StringBuilder();
        csv.append("Iteration,Status,OrderId,DurationSec,Notes\n");

        for (ExecutionResult r : results) {
            csv.append(r.iteration()).append(",")
              .append(r.passed() ? "PASSED" : "FAILED").append(",")
              .append(escapeCsv(r.orderId())).append(",")
              .append(String.format("%.2f", r.duration().toMillis() / 1000.0)).append(",")
              .append(escapeCsv(r.notes())).append("\n");
        }

        return csv.toString();
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private boolean isBlank(String str) {
        return str == null || str.trim().isEmpty();
    }

    private String escapeHtml(String str) {
        if (str == null) return "";
        return str.replace("&", "&amp;")
                  .replace("<", "&lt;")
                  .replace(">", "&gt;")
                  .replace("\"", "&quot;")
                  .replace("'", "&#39;");
    }

    private String valueOrDash(String value) {
        return isBlank(value) ? "-" : value;
    }
}
