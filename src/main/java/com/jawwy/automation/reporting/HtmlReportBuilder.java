package com.jawwy.automation.reporting;

import com.jawwy.automation.models.OrderContext;
import com.jawwy.automation.models.OrderContext.StepExecution;
import java.util.List;

public class HtmlReportBuilder {

    public static String build(ReportData data) {
        int total = data.getRuns();
        int completed = data.getCompleted().size();
        int failed = data.getFailed().size();
        int processed = completed + failed;
        int rate = total > 0 ? (completed * 100 / total) : 0;
        int progress = total > 0 ? Math.min(100, processed * 100 / total) : 0;

        return "<!DOCTYPE html><html lang='en'><head><meta charset='UTF-8'>"
                + "<meta name='viewport' content='width=device-width, initial-scale=1'>"
                + "<title>Jawwy Automation Report</title>"
                + "<link rel='stylesheet' href='execution-report.css'>"
                + "</head><body><main class='page'>"
                + header(data)
                + statsGrid(total, completed, failed, rate)
                + progressBar(completed, failed, progress)
                + ordersSection("Completed orders", data.getCompleted(), data.getFlow(), true)
                + ordersSection("Failed orders", data.getFailed(), data.getFlow(), false)
                + footer()
                + "</main></body></html>";
    }

    public static String css() {
        return String.join("\n",
                ":root{",
                "  --page:#eef0f3;",
                "  --panel:#ffffff;",
                "  --header:#1f2035;",
                "  --text:#030712;",
                "  --muted:#6b7280;",
                "  --line:#dfe3e8;",
                "  --blue:#075aa8;",
                "  --green:#2f6f16;",
                "  --green-bg:#eaf3de;",
                "  --red:#aa2f2f;",
                "  --red-bg:#fcebeb;",
                "  --chip:#e5e7eb;",
                "}",
                "*{box-sizing:border-box}",
                "body{margin:0;background:var(--page);color:var(--text);font:14px/1.45 Arial,Helvetica,sans-serif}",
                ".page{width:100%;padding:24px}",
                ".hero{background:var(--header);color:#fff;border-radius:8px;padding:28px 32px;display:flex;align-items:center;justify-content:space-between;gap:18px;margin-bottom:24px}",
                ".title{font-size:24px;font-weight:700;margin-bottom:2px}",
                ".generated{color:#d1d5db;font-size:12px}",
                ".hero-meta{display:flex;gap:10px;flex-wrap:wrap;justify-content:flex-end}",
                ".hero-chip{border-radius:999px;background:rgba(255,255,255,.14);padding:7px 18px;font-weight:700;font-size:12px}",
                ".hero-chip.env{background:#0b62b2}",
                ".stats{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:16px;margin-bottom:24px}",
                ".stat{background:var(--panel);border:1px solid var(--line);border-radius:10px;padding:20px}",
                ".stat-label{color:var(--muted);font-size:12px;text-transform:uppercase;margin-bottom:6px}",
                ".stat-value{font-size:34px;font-weight:700;line-height:1;color:#5b4633}",
                ".stat-value.total{color:var(--blue)}",
                ".stat-value.good{color:var(--green)}",
                ".stat-value.bad{color:var(--red)}",
                ".stat-help{color:var(--muted);font-size:12px;margin-top:8px}",
                ".progress-card{background:var(--panel);border:1px solid var(--line);border-radius:10px;padding:18px 20px;margin-bottom:24px}",
                ".progress-title{color:var(--muted);margin-bottom:10px}",
                "progress.progress-track{width:100%;height:12px;appearance:none;border:0;background:#eceff3;border-radius:999px;overflow:hidden}",
                "progress.progress-track::-webkit-progress-bar{background:#eceff3;border-radius:999px}",
                "progress.progress-track::-webkit-progress-value{background:var(--green);border-radius:999px}",
                "progress.progress-track::-moz-progress-bar{background:var(--green);border-radius:999px}",
                ".progress-labels{display:flex;justify-content:space-between;color:var(--muted);font-size:12px;margin-top:8px}",
                ".progress-labels .good{color:var(--green)}",
                ".progress-labels .bad{color:var(--red)}",
                ".section{background:var(--panel);border:1px solid var(--line);border-radius:10px;padding:20px;margin-bottom:24px}",
                ".section-title{font-size:16px;font-weight:700;margin-bottom:12px}",
                ".empty{color:var(--muted);padding:10px 0}",
                "table{width:100%;border-collapse:collapse;table-layout:fixed}",
                "td{border-bottom:1px solid #e8ebef;padding:10px 2px;vertical-align:middle}",
                "tr:last-child td{border-bottom:0}",
                ".col-num{width:44px}",
                ".col-order{width:18%;font-weight:700}",
                ".col-flow{width:22%}",
                ".col-duration{width:110px}",
                ".col-status{width:140px}",
                ".col-steps{width:auto}",
                ".badge{display:inline-flex;align-items:center;justify-content:center;min-width:84px;border-radius:999px;padding:4px 11px;font-size:12px;font-weight:700}",
                ".badge.success{background:var(--green-bg);color:var(--green)}",
                ".badge.fail{background:var(--red-bg);color:var(--red)}",
                ".badge.skip{background:#fef3c7;color:#92400e}",
                ".badge.neutral{background:var(--chip);color:#374151}",
                "details{width:100%}",
                "summary{display:inline-flex;align-items:center;justify-content:center;min-width:74px;border:1px solid #d1d5db;border-radius:999px;background:#f9fafb;padding:5px 16px;cursor:pointer;font-weight:700;font-size:12px;list-style:none}",
                "summary::-webkit-details-marker{display:none}",
                ".steps{margin-top:10px;border:1px solid var(--line);border-radius:8px;overflow:hidden;background:#fff}",
                ".step{display:grid;grid-template-columns:24px minmax(180px,1fr) auto;gap:8px;align-items:center;padding:8px 12px;border-bottom:1px solid #edf0f2}",
                ".step:last-child{border-bottom:0}",
                ".mark{width:13px;height:13px;border-radius:2px;display:inline-flex;align-items:center;justify-content:center;color:#fff;font-size:10px;font-weight:700}",
                ".mark.pass{background:#52c878}",
                ".mark.fail{background:var(--red)}",
                ".mark.skip{background:#d69e2e}",
                ".reason{margin-top:10px;color:var(--red);font-weight:700;line-height:1.5}",
                ".fix{margin-top:6px;color:#374151;font-weight:700;line-height:1.5}",
                ".footer{text-align:center;color:var(--muted);font-size:12px;margin-top:12px}",
                "@media(max-width:900px){",
                "  .page{padding:14px}",
                "  .hero{display:block;padding:22px}",
                "  .hero-meta{justify-content:flex-start;margin-top:14px}",
                "  .stats{grid-template-columns:repeat(2,minmax(0,1fr))}",
                "  table,tr,td{display:block;width:100%}",
                "  tr{border-bottom:1px solid #e8ebef;padding:12px 0}",
                "  td{border-bottom:0;padding:5px 0}",
                "  .col-num,.col-order,.col-flow,.col-duration,.col-status,.col-steps{width:100%}",
                "}",
                "@media(max-width:520px){.stats{grid-template-columns:1fr}.stat-value{font-size:28px}}"
        );
    }

    private static String header(ReportData data) {
        return "<section class='hero'><div><div class='title'>Jawwy Automation Report</div>"
                + "<div class='generated'>Generated: " + escape(data.getTimestamp()) + "</div></div>"
                + "<div class='hero-meta'><span class='hero-chip'>" + escape(data.getFlow()) + "</span>"
                + "<span class='hero-chip env'>" + escape(data.getEnvironment()) + "</span></div></section>";
    }

    private static String statsGrid(int total, int completed, int failed, int rate) {
        return "<section class='stats'>"
                + stat("Total Runs", total, "Requested runs", "total")
                + stat("Completed", completed, "Successfully done", "good")
                + stat("Failed", failed, "Needs attention", "bad")
                + stat("Success Rate", rate + "%", "This execution", "")
                + "</section>";
    }

    private static String stat(String label, Object value, String help, String cssClass) {
        return "<div class='stat'><div class='stat-label'>" + escape(label) + "</div>"
                + "<div class='stat-value " + cssClass + "'>" + escape(String.valueOf(value)) + "</div>"
                + "<div class='stat-help'>" + escape(help) + "</div></div>";
    }

    private static String progressBar(int completed, int failed, int progress) {
        return "<section class='progress-card'><div class='progress-title'>Execution progress</div>"
                + "<progress class='progress-track' value='" + progress + "' max='100'></progress>"
                + "<div class='progress-labels'><span class='good'>" + completed + " completed</span>"
                + "<span class='bad'>" + failed + " failed</span></div></section>";
    }

    private static String ordersSection(String title, List<OrderContext> orders, String flow, boolean success) {
        StringBuilder sb = new StringBuilder();
        sb.append("<section class='section'><div class='section-title'>")
                .append(escape(title)).append(" (").append(orders.size()).append(")</div>");

        if (orders.isEmpty()) {
            sb.append("<div class='empty'>None</div></section>");
            return sb.toString();
        }

        sb.append("<table><tbody>");
        int index = 1;
        for (OrderContext ctx : orders) {
            sb.append("<tr>")
                    .append("<td class='col-num'>").append(index++).append("</td>")
                    .append("<td class='col-order'>").append(escape(valueOrDefault(ctx.getOrderId(), "N/A"))).append("</td>")
                    .append("<td class='col-flow'>").append(escape(flow)).append("</td>")
                    .append("<td class='col-duration'>").append(escape(valueOrDefault(ctx.getRunDuration(), "-"))).append("</td>")
                    .append("<td class='col-status'>").append(statusBadge(success ? "SUCCESS" : "FAILED")).append("</td>")
                    .append("<td class='col-steps'>").append(details(ctx, !success)).append("</td>")
                    .append("</tr>");
        }
        sb.append("</tbody></table></section>");
        return sb.toString();
    }

    private static String details(OrderContext ctx, boolean open) {
        StringBuilder sb = new StringBuilder();
        sb.append("<details").append(open ? " open" : "").append("><summary>Expand</summary>");
        if (ctx.getStepLog().isEmpty()) {
            sb.append("<div class='empty'>No stages were recorded</div>");
        } else {
            sb.append("<div class='steps'>");
            for (StepExecution step : ctx.getStepLog()) {
                String normalized = normalizeStatus(step.getStatus());
                sb.append("<div class='step'>")
                        .append("<span class='mark ").append(markClass(normalized)).append("'>").append(markText(normalized)).append("</span>")
                        .append("<span>").append(escape(step.getStepName())).append("</span>")
                        .append(statusBadge(normalized))
                        .append("</div>");
            }
            sb.append("</div>");
        }
        if (ctx.getFailureReason() != null && !ctx.getFailureReason().isBlank()) {
            sb.append("<div class='reason'>").append(escape(ctx.getFailureReason())).append("</div>");
        }
        if (ctx.getRecommendedFix() != null && !ctx.getRecommendedFix().isBlank()) {
            sb.append("<div class='fix'>Recommended Fix: ").append(escape(ctx.getRecommendedFix())).append("</div>");
        }
        sb.append("</details>");
        return sb.toString();
    }

    private static String statusBadge(String status) {
        String normalized = normalizeStatus(status);
        String cssClass;
        if ("PASSED".equals(normalized) || "PASS".equals(normalized) || "SUCCESS".equals(normalized)) {
            cssClass = "success";
        } else if ("FAILED".equals(normalized) || "FAIL".equals(normalized)) {
            cssClass = "fail";
        } else if ("SKIPPED".equals(normalized)) {
            cssClass = "skip";
        } else {
            cssClass = "neutral";
        }
        return "<span class='badge " + cssClass + "'>" + escape(normalized) + "</span>";
    }

    private static String normalizeStatus(String status) {
        return status == null || status.isBlank() ? "UNKNOWN" : status.trim().toUpperCase();
    }

    private static String markClass(String status) {
        if ("PASSED".equals(status) || "PASS".equals(status) || "SUCCESS".equals(status)) {
            return "pass";
        }
        if ("FAILED".equals(status) || "FAIL".equals(status)) {
            return "fail";
        }
        return "skip";
    }

    private static String markText(String status) {
        if ("PASSED".equals(status) || "PASS".equals(status) || "SUCCESS".equals(status)) {
            return "P";
        }
        if ("FAILED".equals(status) || "FAIL".equals(status)) {
            return "!";
        }
        return "-";
    }

    private static String footer() {
        return "<footer class='footer'>Jawwy Automation Suite - Powered by Qeema</footer>";
    }

    private static String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
