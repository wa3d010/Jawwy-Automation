package com.jawwy.automation.tests.orders;

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

        LOGGER.info(
                "Starting SP batch: env={}, flow={}, requestedOrders={}",
                targetEnv, orderFlow, requestedOrderCount
        );

        for (int iteration = 1; iteration <= requestedOrderCount; iteration++) {

            JawwyOrderJourney journey = new JawwyOrderJourney();
            Instant startedAt = Instant.now();

            try {
                String orderId = journey.runFullFlow();

                ExecutionResult result = ExecutionResult.passed(
                        iteration,
                        startedAt,
                        Duration.between(startedAt, Instant.now()),
                        orderId,
                        summarizeStepStatuses(journey)
                );

                results.add(result);
                writeReports(requestedOrderCount, results);

            } catch (Exception ex) {

                ExecutionResult result = ExecutionResult.failed(
                        iteration,
                        startedAt,
                        Duration.between(startedAt, Instant.now()),
                        journey.getCreatedOrderId(),
                        summarizeStepStatuses(journey) + " | failure=" + summarizeFailure(ex)
                );

                results.add(result);
                writeReports(requestedOrderCount, results);
                throw ex;
            }
        }

        Allure.addAttachment(
                "Business Summary (SP Batch)",
                "text/markdown",
                buildMarkdownSummary(requestedOrderCount, results)
        );
    }

    /* ================= Report Writers ================= */

    private void writeReports(int requestedOrderCount, List<ExecutionResult> results) throws IOException {
        Files.createDirectories(REPORT_DIRECTORY);
        Files.createDirectories(DASHBOARD_DIRECTORY);

        Files.writeString(SUMMARY_REPORT, buildMarkdownSummary(requestedOrderCount, results));
        Files.writeString(CSV_REPORT, buildCsvSummary(results));
        Files.writeString(DASHBOARD_INDEX, buildHtmlDashboard(requestedOrderCount, results));
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
            .append("<style>")
            .append("body{margin:0;background:#f6f8fb;font:14px system-ui}")
            .append(".main{max-width:1200px;margin:auto;padding:24px}")
            .append(".card{background:#fff;border:1px solid #ddd;border-radius:12px;padding:16px}")
            .append(".grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(220px,1fr));gap:16px}")
            .append(".pill{padding:8px 14px;border-radius:20px;margin:12px 0;display:inline-block}")
            .append(".pill.good{color:#16a34a}.pill.bad{color:#ef4444}")
            .append("table{width:100%;border-collapse:collapse}")
            .append("th,td{padding:12px;border-bottom:1px solid #ddd;text-align:left}")
            .append(".badge{padding:6px 10px;border-radius:12px;font-weight:700}")
            .append(".badge.good{color:#16a34a}.badge.bad{color:#ef4444}")
            .append("details summary{cursor:pointer;font-weight:700}")
            .append(".steps{margin-top:8px}")
            .append(".step{display:flex;justify-content:space-between;padding:6px 0}")
            .append("</style></head><body><main class=\"main\">");

        html.append("<h1>Order Flow Report</h1>")
            .append("<div>Flow <b>").append(escapeHtml(orderFlow))
            .append("</b> on <b>").append(escapeHtml(targetEnv)).append("</b></div>")
            .append("<div class=\"pill ").append(overallClass).append("\">Overall: ")
            .append(overall).append("</div>");

        html.append("<div class=\"grid\">")
            .append(metric("Environment", targetEnv))
            .append(metric("Flow", orderFlow))
            .append(metric("Orders", String.valueOf(requestedOrderCount)))
            .append(metric("Passed", String.valueOf(passedCount)))
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

    private String metric(String label, String value) {
        return "<div class=\"card\"><div>" + escapeHtml(label) +
               "</div><div><b>" + escapeHtml(value) + "</b></div></div>";
    }

    /* ================= Step Parsing ================= */

    private StepBreakdown parseSteps(String notes) {
        List<StepItem> steps = new ArrayList<>();

        if (!isBlank(notes)) {
            for (String part : notes.split("\\s*;\\s*")) {
                String[] kv = part.split("=");
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

    private String summarizeFailure(Exception ex) {
        return ex.getClass().getSimpleName() + ": " + ex.getMessage();
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

        md.append("| # | Status | Order ID | Duration | Notes |\n");
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
