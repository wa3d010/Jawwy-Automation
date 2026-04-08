package com.jawwy.automation.tests.orders;

import com.jawwy.automation.tests.BaseEocTest;
import com.jawwy.automation.workflow.JawwyOrderJourney;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    @Test(description = "Run SP new activation flow in Jenkins batch mode")
    @Feature("Jenkins Batch")
    public void runBatch() throws Exception {
        int requestedOrderCount = resolveRequestedOrderCount();
        List<ExecutionResult> results = new ArrayList<>();

        for (int iteration = 1; iteration <= requestedOrderCount; iteration++) {
            JawwyOrderJourney journey = new JawwyOrderJourney();
            Instant startedAt = Instant.now();

            try {
                String orderId = journey.runFullFlow();
                ExecutionResult result = ExecutionResult.passed(
                        iteration,
                        startedAt,
                        Duration.between(startedAt, Instant.now()),
                        orderId
                );
                results.add(result);
                writeReportsOrThrow(requestedOrderCount, results);
                LOGGER.info("Completed SP batch iteration {}/{} with order {}", iteration, requestedOrderCount, orderId);
            } catch (Exception exception) {
                ExecutionResult result = ExecutionResult.failed(
                        iteration,
                        startedAt,
                        Duration.between(startedAt, Instant.now()),
                        journey.getCreatedOrderId(),
                        summarizeFailure(exception)
                );
                results.add(result);
                safeWriteReports(requestedOrderCount, results);
                LOGGER.error("SP batch iteration {}/{} failed", iteration, requestedOrderCount, exception);
                throw exception;
            }
        }

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
        Files.writeString(SUMMARY_REPORT, buildMarkdownSummary(requestedOrderCount, results));
        Files.writeString(CSV_REPORT, buildCsvSummary(results));
    }

    private void safeWriteReports(int requestedOrderCount, List<ExecutionResult> results) {
        try {
            writeReportsOrThrow(requestedOrderCount, results);
        } catch (IOException exception) {
            LOGGER.warn("Unable to write SP batch summary report", exception);
        }
    }

    private String buildMarkdownSummary(int requestedOrderCount, List<ExecutionResult> results) {
        long passedCount = results.stream().filter(ExecutionResult::passed).count();
        long failedCount = results.stream().filter(result -> !result.passed()).count();
        String overallStatus = failedCount > 0 ? "FAILED" : "PASSED";

        StringBuilder builder = new StringBuilder();
        builder.append("# SP Batch Summary").append(System.lineSeparator()).append(System.lineSeparator());
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

        private static ExecutionResult passed(int iteration, Instant startedAt, Duration duration, String orderId) {
            return new ExecutionResult(iteration, "PASSED", startedAt, duration, orderId, "Flow completed successfully");
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
