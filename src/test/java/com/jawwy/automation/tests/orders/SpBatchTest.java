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
                        summarizeStepStatuses(journey) + " ; failure=" + summarizeFailure(ex)
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
        Files.writeString(DASHBOARD_CSS, buildDashboardCss());
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