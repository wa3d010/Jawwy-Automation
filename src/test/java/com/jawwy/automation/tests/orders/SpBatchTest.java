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
    private static final String DASHBOARD_LOGO_RESOURCE = "report-assets/qeema-logo.svg";
    private static final String DASHBOARD_LOGO_FILE = "qeema-logo.svg";
    private final String orderFlow = resolveOrderFlow();
    private final String targetEnv = resolveTargetEnv();

    @BeforeClass(alwaysRun = true)
    public void skipOutsideBatchMode() {
        if (!Boolean.parseBoolean(System.getProperty("batch.mode", "false"))) {
            throw new SkipException("SpBatchTest runs only when batch.mode=true.");
        }
    }

    @Test(description = "Run SP new activation flow in Jenkins batch mode")
    @Feature("Jenkins Batch")
    public void runBatch() throws Exception {
        int requestedOrderCount = resolveRequestedOrderCount();
        List<ExecutionResult> results = new ArrayList<>();

        LOGGER.info("Starting SP batch: env={}, flow={}, requestedOrders={}", targetEnv, orderFlow, requestedOrderCount);

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
                writeReportsOrThrow(requestedOrderCount, results);
                LOGGER.info("ORDER {} SUMMARY: status=PASSED, duration={}s, orderId={}, steps={}",
                        iteration,
                        String.format("%.2f", result.duration().toMillis() / 1000.0),
                        orderId,
                        result.notes());
                LOGGER.info("Completed SP batch iteration {}/{} with order {}", iteration, requestedOrderCount, orderId);
            } catch (Exception exception) {
                ExecutionResult result = ExecutionResult.failed(
                        iteration,
                        startedAt,
                        Duration.between(startedAt, Instant.now()),
                        journey.getCreatedOrderId(),
                        summarizeStepStatuses(journey) + " | failure=" + summarizeFailure(exception)
                );
                results.add(result);
                safeWriteReports(requestedOrderCount, results);
                LOGGER.error("ORDER {} SUMMARY: status=FAILED, duration={}s, orderId={}, steps={}",
                        iteration,
                        String.format("%.2f", result.duration().toMillis() / 1000.0),
                        valueOrDash(result.orderId()),
                        result.notes());
                LOGGER.error("SP batch iteration {}/{} failed", iteration, requestedOrderCount, exception);
                throw exception;
            }
        }

        logBatchSummary(results);
        Allure.addAttachment("Business Summary (SP Batch)", "text/markdown", buildMarkdownSummary(requestedOrderCount, results));
        LOGGER.info("SP batch finished successfully. Summary report: {}", SUMMARY_REPORT.toAbsolutePath());
    }

    private int resolveRequestedOrderCount() {
        String configuredValue = System.getProperty("order.count");
        if (isBlank(configuredValue)) {
            configuredValue = System.getenv("ORDER_COUNT");
        }
        if (isBlank(configuredValue)) {
            return 1;
        }

        try {
            int parsedValue = Integer.parseInt(configuredValue.trim());
            if (parsedValue < 1) {
                throw new IllegalArgumentException("order.count must be greater than zero.");
            }
            return parsedValue;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("order.count must be a valid integer.", exception);
        }
    }

    private void writeReportsOrThrow(int requestedOrderCount, List<ExecutionResult> results) throws IOException {
        Files.createDirectories(REPORT_DIRECTORY);
        Files.createDirectories(DASHBOARD_DIRECTORY);
        Files.writeString(SUMMARY_REPORT, buildMarkdownSummary(requestedOrderCount, results));
        Files.writeString(CSV_REPORT, buildCsvSummary(results));
        writeDashboardAssets();
        Files.writeString(DASHBOARD_INDEX, buildHtmlDashboard(requestedOrderCount, results));
    }

    private void safeWriteReports(int requestedOrderCount, List<ExecutionResult> results) {
        try {
            writeReportsOrThrow(requestedOrderCount, results);
        } catch (IOException exception) {
            LOGGER.warn("Unable to write SP batch summary report", exception);
        }
    }

    private void logBatchSummary(List<ExecutionResult> results) {
        StringBuilder builder = new StringBuilder("BATCH SUMMARY:");
        for (ExecutionResult result : results) {
            builder.append(System.lineSeparator())
                    .append(" - order ")
                    .append(result.iteration())
                    .append(": status=").append(result.status())
                    .append(", orderId=").append(valueOrDash(result.orderId()))
                    .append(", duration=").append(String.format("%.2f", result.duration().toMillis() / 1000.0)).append("s")
                    .append(", steps=").append(result.notes());
        }
        LOGGER.info(builder.toString());
    }

    private String buildMarkdownSummary(int requestedOrderCount, List<ExecutionResult> results) {
        long passedCount = results.stream().filter(ExecutionResult::passed).count();
        long failedCount = results.stream().filter(result -> !result.passed()).count();
        String overallStatus = failedCount > 0 ? "FAILED" : "PASSED";

        StringBuilder builder = new StringBuilder();
        builder.append("# SP Batch Summary").append(System.lineSeparator()).append(System.lineSeparator());
        builder.append("- Environment: ").append(targetEnv).append(System.lineSeparator());
        builder.append("- Order flow: ").append(orderFlow).append(System.lineSeparator());
        builder.append("- Requested orders: ").append(requestedOrderCount).append(System.lineSeparator());
        builder.append("- Executed iterations: ").append(results.size()).append(System.lineSeparator());
        builder.append("- Passed: ").append(passedCount).append(System.lineSeparator());
        builder.append("- Failed: ").append(failedCount).append(System.lineSeparator());
        builder.append("- Overall status: ").append(overallStatus).append(System.lineSeparator()).append(System.lineSeparator());
        builder.append("| Iteration | Status | Order ID | Started At (UTC) | Duration (s) | Notes |")
                .append(System.lineSeparator());
        builder.append("| --- | --- | --- | --- | ---: | --- |").append(System.lineSeparator());

        for (ExecutionResult result : results) {
            builder.append("| ").append(result.iteration())
                    .append(" | ").append(result.status())
                    .append(" | ").append(valueOrDash(result.orderId()))
                    .append(" | ").append(result.startedAt())
                    .append(" | ").append(String.format("%.2f", result.duration().toMillis() / 1000.0))
                    .append(" | ").append(escapePipes(result.notes()))
                    .append(" |")
                    .append(System.lineSeparator());
        }

        return builder.toString();
    }

    private String buildHtmlDashboard(int requestedOrderCount, List<ExecutionResult> results) {
        long passedCount = results.stream().filter(ExecutionResult::passed).count();
        long failedCount = results.size() - passedCount;
        String overall = failedCount > 0 ? "FAILED" : "PASSED";
        String overallClass = failedCount > 0 ? "bad" : "good";

        double maxDurationSec = results.stream()
                .mapToDouble(result -> result.duration().toMillis() / 1000.0)
                .max()
                .orElse(1.0);

        StringBuilder html = new StringBuilder();
        html.append("<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\">")
                .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
                .append("<title>qeema | Order Flow Report</title>")
                .append("<style>")
                .append(":root{")
                .append("--bg:#f6f8fb;--panel:#ffffff;--panel2:#f2f5fb;--text:#0f172a;--muted:#64748b;")
                .append("--line:#e5eaf3;--shadow:0 10px 22px rgba(16,24,40,.08);")
                .append("--brand:#16a34a;--brand2:#22c55e;--brandSoft:rgba(34,197,94,.14);")
                .append("--good:#16a34a;--bad:#ef4444;--warn:#f59e0b;")
                .append("}")
                .append("body{margin:0;background:linear-gradient(180deg,#ffffff 0%, var(--bg) 50%, #eef3fb 100%);color:var(--text);font:14px/1.45 ui-sans-serif,system-ui,-apple-system,\"Segoe UI\",Roboto,Arial;}")
                .append(".layout{display:flex;min-height:100vh;}")
                .append(".side{width:260px;background:linear-gradient(180deg,#071b12 0%, #052014 55%, #04160f 100%);color:#eaf7ef;position:sticky;top:0;height:100vh;}")
                .append(".side .brand{display:flex;gap:12px;align-items:center;padding:18px 18px 14px;border-bottom:1px solid rgba(255,255,255,.08)}")
                .append(".side .brand img{width:44px;height:44px;border-radius:10px;background:rgba(255,255,255,.08);padding:6px}")
                .append(".side .brand .t{font-weight:900;letter-spacing:.3px}")
                .append(".side .brand .s{font-size:12px;opacity:.78;margin-top:2px}")
                .append(".nav{padding:14px 10px}")
                .append(".nav a{display:flex;gap:10px;align-items:center;padding:10px 12px;margin:6px 8px;border-radius:12px;color:#eaf7ef;text-decoration:none;opacity:.9}")
                .append(".nav a.active{background:rgba(34,197,94,.18);border:1px solid rgba(34,197,94,.22)}")
                .append(".nav a span{font-weight:800}")
                .append(".main{flex:1;padding:26px 26px 40px;}")
                .append(".topbar{display:flex;justify-content:space-between;gap:14px;align-items:flex-start;flex-wrap:wrap}")
                .append(".title h1{margin:0;font-size:22px;letter-spacing:.2px}")
                .append(".title p{margin:6px 0 0;color:var(--muted)}")
                .append(".pill{display:inline-flex;align-items:center;gap:8px;border-radius:999px;padding:8px 12px;background:var(--panel);border:1px solid var(--line);box-shadow:var(--shadow);font-weight:900}")
                .append(".dot{width:10px;height:10px;border-radius:50%}")
                .append(".dot.good{background:var(--good)}.dot.bad{background:var(--bad)}")
                .append(".grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:14px;margin-top:16px}")
                .append("@media (max-width:1100px){.grid{grid-template-columns:repeat(2,minmax(0,1fr));}.side{display:none}.main{padding:20px}}")
                .append(".card{background:var(--panel);border:1px solid var(--line);border-radius:18px;box-shadow:var(--shadow)}")
                .append(".metric{padding:14px 14px 12px}")
                .append(".metric .k{color:var(--muted);font-size:12px}")
                .append(".metric .v{font-size:22px;font-weight:1000;margin-top:4px}")
                .append(".metric.good .v{color:var(--good)}.metric.bad .v{color:var(--bad)}")
                .append(".banner{margin-top:16px;padding:14px 16px;border-radius:18px;background:linear-gradient(135deg, rgba(34,197,94,.18) 0%, rgba(34,197,94,.08) 55%, rgba(15,23,42,.02) 100%);border:1px solid rgba(34,197,94,.18)}")
                .append(".banner b{color:#0b3a1f}")
                .append("table{width:100%;border-collapse:separate;border-spacing:0}")
                .append("thead th{position:sticky;top:0;background:var(--panel);z-index:1}")
                .append("th,td{padding:12px 14px;border-bottom:1px solid var(--line);vertical-align:top}")
                .append("th{color:var(--muted);font-weight:900;text-align:left;font-size:12px;letter-spacing:.3px;text-transform:uppercase}")
                .append("tr:hover td{background:#fbfcff}")
                .append(".badge{display:inline-flex;align-items:center;gap:8px;border-radius:999px;padding:6px 10px;font-weight:1000;letter-spacing:.2px;border:1px solid var(--line)}")
                .append(".badge.good{background:var(--brandSoft);color:var(--good);border-color:rgba(34,197,94,.25)}")
                .append(".badge.bad{background:rgba(239,68,68,.12);color:var(--bad);border-color:rgba(239,68,68,.25)}")
                .append(".id{font-weight:1000}")
                .append(".small{color:var(--muted);font-size:12px}")
                .append(".bar{height:10px;border-radius:999px;background:#eef2f7;border:1px solid #e6ebf3;overflow:hidden;margin-top:8px}")
                .append(".bar > span{display:block;height:100%;border-radius:999px;background:linear-gradient(90deg, var(--brand2), var(--brand))}")
                .append("details{margin-top:8px}")
                .append("summary{cursor:pointer;color:var(--brand);font-weight:1000}")
                .append(".steps{margin-top:10px;display:grid;gap:8px}")
                .append(".step{display:flex;justify-content:space-between;gap:12px;align-items:center;padding:10px 12px;border-radius:14px;border:1px solid var(--line);background:#ffffff}")
                .append(".step .name{font-weight:900}")
                .append(".step .status{font-weight:1000;font-size:12px;padding:6px 10px;border-radius:999px;border:1px solid var(--line)}")
                .append(".step .status.pass{color:var(--good);border-color:rgba(34,197,94,.25);background:rgba(34,197,94,.10)}")
                .append(".step .status.fail{color:var(--bad);border-color:rgba(239,68,68,.25);background:rgba(239,68,68,.10)}")
                .append(".step .status.skip{color:#475569;border-color:#e5eaf3;background:#f5f7fb}")
                .append(".step.highlight{border-color:rgba(34,197,94,.35);box-shadow:0 0 0 4px rgba(34,197,94,.10)}")
                .append("</style></head><body><div class=\"layout\">");

        html.append("<aside class=\"side\">")
                .append("<div class=\"brand\">")
                .append("<img src=\"").append(escapeHtml(DASHBOARD_LOGO_FILE)).append("\" alt=\"qeema\"/>")
                .append("<div><div class=\"t\">qeema</div><div class=\"s\">Automation Dashboard</div></div>")
                .append("</div>")
                .append("<nav class=\"nav\">")
                .append("<a class=\"active\" href=\"#orders\"><span>Dashboard</span></a>")
                .append("<a href=\"#summary\"><span>Summary</span></a>")
                .append("<a href=\"#reports\"><span>Artifacts</span></a>")
                .append("</nav>")
                .append("</aside>");

        html.append("<main class=\"main\">");
        html.append("<div class=\"topbar\">");
        html.append("<div class=\"title\">")
                .append("<h1>Order Flow Report</h1>")
                .append("<p id=\"summary\">Flow <b>").append(escapeHtml(orderFlow)).append("</b> ran on <b>")
                .append(escapeHtml(targetEnv)).append("</b> and created <b>").append(requestedOrderCount)
                .append("</b> order(s).</p>")
                .append("</div>");
        html.append("<div class=\"pill\"><span class=\"dot ").append(overallClass).append("\"></span>")
                .append("<span>Overall: ").append(overall).append("</span></div>");
        html.append("</div>");

        html.append("<div class=\"grid\">")
                .append(metricCard("Environment", escapeHtml(targetEnv), "neutral"))
                .append(metricCard("Flow", escapeHtml(orderFlow), "neutral"))
                .append(metricCard("Orders Created", String.valueOf(requestedOrderCount), "neutral"))
                .append(metricCard("Passed", String.valueOf(passedCount), "good"))
                .append("</div>");

        if (failedCount > 0) {
            html.append("<div class=\"banner\"><b>Attention:</b> Some orders failed. Expand a failed order to see which business step failed.</div>");
        } else {
            html.append("<div class=\"banner\"><b>Success:</b> All orders completed. Expand an order to see the executed steps.</div>");
        }

        html.append("<div id=\"orders\" class=\"card\" style=\"margin-top:16px;overflow:hidden\">")
                .append("<div style=\"padding:14px 16px;border-bottom:1px solid var(--line);display:flex;justify-content:space-between;align-items:center;gap:10px;flex-wrap:wrap\">")
                .append("<div style=\"font-weight:1000;letter-spacing:.2px\">Orders</div>")
                .append("<div class=\"small\" id=\"reports\">Open in Jenkins: <b>Artifacts</b> then <b>target/jenkins/dashboard/index.html</b></div>")
                .append("</div>")
                .append("<table><thead><tr>")
                .append("<th>#</th><th>Status</th><th>Order ID</th><th>Started (UTC)</th><th>Duration</th><th>Steps</th>")
                .append("</tr></thead><tbody>");

        for (ExecutionResult result : results) {
            boolean passed = result.passed();
            String badgeClass = passed ? "good" : "bad";
            double seconds = result.duration().toMillis() / 1000.0;
            int width = (int) Math.max(3, Math.min(100, Math.round((seconds / maxDurationSec) * 100)));
            StepBreakdown breakdown = parseSteps(result.notes());

            html.append("<tr>");
            html.append("<td>").append(result.iteration()).append("</td>");
            html.append("<td><span class=\"badge ").append(badgeClass).append("\">")
                    .append(passed ? "PASSED" : "FAILED")
                    .append("</span></td>");
            html.append("<td><div class=\"id\">").append(escapeHtml(valueOrDash(result.orderId()))).append("</div></td>");
            html.append("<td class=\"small\">").append(escapeHtml(result.startedAt().toString())).append("</td>");
            html.append("<td><div style=\"font-weight:900\">").append(String.format("%.2f", seconds)).append("s</div>")
                    .append("<div class=\"bar\"><span style=\"width:").append(width).append("%\"></span></div></td>");
            html.append("<td>");
            html.append("<details><summary>Expand</summary>");
            html.append("<div class=\"steps\">");
            for (StepItem step : breakdown.steps) {
                boolean highlight = step.isDbClosedCompletedStep;
                html.append("<div class=\"step").append(highlight ? " highlight" : "").append("\">");
                html.append("<div class=\"name\">").append(escapeHtml(step.name)).append("</div>");
                html.append("<div class=\"status ").append(escapeHtml(step.cssClass)).append("\">")
                        .append(escapeHtml(step.statusLabel)).append("</div>");
                html.append("</div>");
            }
            html.append("</div>");
            if (breakdown.hasDbClosedCompletedPass) {
                html.append("<div class=\"small\" style=\"margin-top:10px\">DB check: <b style=\"color:var(--good)\">CLOSED.COMPLETED</b> confirmed.</div>");
            } else {
                html.append("<div class=\"small\" style=\"margin-top:10px\">DB check: CLOSED.COMPLETED not confirmed yet.</div>");
            }
            html.append("</details>");
            html.append("</td>");
            html.append("</tr>");
        }

        html.append("</tbody></table></div>");
        html.append("<div class=\"small\" style=\"margin:14px 2px 0\">Generated by JawwyAutomation. This report is business-focused: flow, orders, time, and outcomes.</div>");
        html.append("</main></div></body></html>");
        return html.toString();
    }

    private String metricCard(String label, String value, String style) {
        String klass = "metric";
        if ("good".equals(style)) {
            klass += " good";
        } else if ("bad".equals(style)) {
            klass += " bad";
        }
        return "<div class=\"card " + escapeHtml(klass) + "\"><div class=\"k\">" + escapeHtml(label)
                + "</div><div class=\"v\">" + value + "</div></div>";
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private StepBreakdown parseSteps(String notes) {
        List<StepItem> steps = new ArrayList<>();
        boolean hasDbPass = false;

        String raw = notes == null ? "" : notes.trim();
        if (!raw.isEmpty()) {
            String[] parts = raw.split("\\s*;\\s*");
            for (String part : parts) {
                String normalized = part.trim();
                if (normalized.isEmpty()) {
                    continue;
                }

                String name = normalized;
                String status = "UNKNOWN";
                int eq = normalized.lastIndexOf('=');
                if (eq > 0 && eq < normalized.length() - 1) {
                    name = normalized.substring(0, eq).trim();
                    status = normalized.substring(eq + 1).trim();
                }

                String css;
                String label;
                if ("PASSED".equalsIgnoreCase(status)) {
                    css = "pass";
                    label = "PASSED";
                } else if ("FAILED".equalsIgnoreCase(status)) {
                    css = "fail";
                    label = "FAILED";
                } else if ("SKIPPED".equalsIgnoreCase(status)) {
                    css = "skip";
                    label = "SKIPPED";
                } else {
                    css = "skip";
                    label = status;
                }

                boolean isDbClosedCompletedStep = name.toLowerCase().contains("db verification")
                        || name.toLowerCase().contains("closed.completed");

                if (isDbClosedCompletedStep && "PASSED".equalsIgnoreCase(status)) {
                    hasDbPass = true;
                }

                steps.add(new StepItem(name, label, css, isDbClosedCompletedStep));
            }
        }

        return new StepBreakdown(steps, hasDbPass);
    }

    private void writeDashboardAssets() throws IOException {
        try (var stream = SpBatchTest.class.getClassLoader().getResourceAsStream(DASHBOARD_LOGO_RESOURCE)) {
            if (stream == null) {
                return;
            }
            byte[] bytes = stream.readAllBytes();
            Files.write(DASHBOARD_DIRECTORY.resolve(DASHBOARD_LOGO_FILE), bytes);
        }
    }

    private String buildCsvSummary(List<ExecutionResult> results) {
        StringBuilder builder = new StringBuilder("iteration,status,order_id,started_at_utc,duration_seconds,notes")
                .append(System.lineSeparator());

        for (ExecutionResult result : results) {
            builder.append(result.iteration()).append(',')
                    .append(csvEscape(result.status())).append(',')
                    .append(csvEscape(valueOrDash(result.orderId()))).append(',')
                    .append(csvEscape(result.startedAt().toString())).append(',')
                    .append(String.format("%.2f", result.duration().toMillis() / 1000.0)).append(',')
                    .append(csvEscape(result.notes()))
                    .append(System.lineSeparator());
        }

        return builder.toString();
    }

    private String summarizeFailure(Exception exception) {
        Throwable current = exception;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        if (isBlank(message)) {
            message = current.getClass().getSimpleName();
        }
        return message.replace('\r', ' ').replace('\n', ' ').trim();
    }

    private String summarizeStepStatuses(JawwyOrderJourney journey) {
        List<String> stepStatuses = journey.getStepStatuses();
        return stepStatuses.isEmpty() ? "No completed steps recorded" : String.join(" ; ", stepStatuses);
    }

    private String csvEscape(String value) {
        String normalized = value == null ? "" : value.replace("\"", "\"\"");
        return "\"" + normalized + "\"";
    }

    private String escapePipes(String value) {
        return valueOrDash(value).replace("|", "\\|");
    }

    private String valueOrDash(String value) {
        return isBlank(value) ? "-" : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String resolveOrderFlow() {
        String flow = System.getProperty("order.flow");
        if (isBlank(flow)) {
            flow = System.getenv("ORDER_FLOW");
        }
        return isBlank(flow) ? "New Activation Online" : flow.trim();
    }

    private String resolveTargetEnv() {
        String env = System.getProperty("env");
        if (isBlank(env)) {
            env = System.getenv("JAWWY_ENV");
        }
        return isBlank(env) ? "local" : env.trim();
    }

    private static final class StepBreakdown {
        private final List<StepItem> steps;
        private final boolean hasDbClosedCompletedPass;

        private StepBreakdown(List<StepItem> steps, boolean hasDbClosedCompletedPass) {
            this.steps = steps;
            this.hasDbClosedCompletedPass = hasDbClosedCompletedPass;
        }
    }

    private static final class StepItem {
        private final String name;
        private final String statusLabel;
        private final String cssClass;
        private final boolean isDbClosedCompletedStep;

        private StepItem(String name, String statusLabel, String cssClass, boolean isDbClosedCompletedStep) {
            this.name = name;
            this.statusLabel = statusLabel;
            this.cssClass = cssClass;
            this.isDbClosedCompletedStep = isDbClosedCompletedStep;
        }
    }

    private static final class ExecutionResult {
        private final int iteration;
        private final String status;
        private final Instant startedAt;
        private final Duration duration;
        private final String orderId;
        private final String notes;

        private ExecutionResult(int iteration, String status, Instant startedAt, Duration duration, String orderId, String notes) {
            this.iteration = iteration;
            this.status = status;
            this.startedAt = startedAt;
            this.duration = duration;
            this.orderId = orderId;
            this.notes = notes;
        }

        private static ExecutionResult passed(int iteration, Instant startedAt, Duration duration, String orderId, String notes) {
            return new ExecutionResult(iteration, "PASSED", startedAt, duration, orderId, notes);
        }

        private static ExecutionResult failed(int iteration, Instant startedAt, Duration duration, String orderId, String notes) {
            return new ExecutionResult(iteration, "FAILED", startedAt, duration, orderId, notes);
        }

        private int iteration() {
            return iteration;
        }

        private boolean passed() {
            return "PASSED".equals(status);
        }

        private String status() {
            return status;
        }

        private Instant startedAt() {
            return startedAt;
        }

        private Duration duration() {
            return duration;
        }

        private String orderId() {
            return orderId;
        }

        private String notes() {
            return notes;
        }
    }
}
