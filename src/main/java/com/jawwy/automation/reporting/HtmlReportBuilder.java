package com.jawwy.automation.reporting;

import com.jawwy.automation.models.OrderContext;
import com.jawwy.automation.models.OrderContext.StepExecution;
import java.util.List;

public class HtmlReportBuilder {

    public static String build(ReportData data) {
        int total = data.getRuns();
        int completed = data.getCompleted().size();
        int failed = data.getFailed().size();
        int rate = total > 0 ? (completed * 100 / total) : 0;

        return "<!DOCTYPE html><html lang='en'><head><meta charset='UTF-8'>"
                + "<meta name='viewport' content='width=device-width, initial-scale=1'>"
                + "<title>Jawwy Automation Report</title>"
                + "<link rel='stylesheet' href='execution-report.css'>"
                + "</head><body><main class='page'>"
                + header(data)
                + allureButton(data.getAllureReportUrl())
                + statsGrid(total, completed, failed, rate)
                + ordersTable("Completed orders", data.getCompleted(), data.getFlow(), true)
                + ordersTable("Failed orders", data.getFailed(), data.getFlow(), false)
                + footer()
                + "</main></body></html>";
    }

    public static String css() {
        return String.join("\n",
                ":root{",
                "  --bg:#f5f7fb;",
                "  --surface:#ffffff;",
                "  --surface-soft:#f9fafb;",
                "  --text:#111827;",
                "  --muted:#6b7280;",
                "  --line:#e5e7eb;",
                "  --good:#15803d;",
                "  --good-bg:#ecfdf3;",
                "  --bad:#b91c1c;",
                "  --bad-bg:#fef2f2;",
                "  --warn:#a16207;",
                "  --warn-bg:#fefce8;",
                "}",
                "*{box-sizing:border-box}",
                "body{margin:0;background:var(--bg);color:var(--text);font:14px/1.5 Arial,Helvetica,sans-serif}",
                ".page{max-width:1180px;margin:0 auto;padding:28px 18px 42px}",
                ".header{background:#1f2937;color:#fff;border-radius:8px;padding:22px;display:flex;justify-content:space-between;gap:18px;align-items:flex-start}",
                ".title{font-size:24px;font-weight:700;margin-bottom:4px}",
                ".generated{font-size:12px;color:#d1d5db}",
                ".meta{display:flex;gap:8px;flex-wrap:wrap;justify-content:flex-end}",
                ".allure{display:flex;justify-content:flex-end;margin:16px 0}",
                ".stats{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:14px;margin:18px 0}",
                ".card{background:var(--surface);border:1px solid var(--line);border-radius:8px;padding:16px;margin-bottom:18px}",
                ".stat-label{font-size:12px;color:var(--muted);margin-bottom:6px}",
                ".stat-value{font-size:28px;font-weight:700}",
                ".section-title{font-size:17px;font-weight:700;margin-bottom:12px}",
                ".empty{color:var(--muted)}",
                ".table-wrap{overflow-x:auto}",
                "table{width:100%;border-collapse:collapse;font-size:13px}",
                "th{background:var(--surface-soft);color:var(--muted);font-size:12px;text-align:left;text-transform:uppercase;padding:10px;border-bottom:1px solid var(--line)}",
                "td{padding:12px 10px;border-bottom:1px solid var(--line);vertical-align:top}",
                "tr:last-child td{border-bottom:0}",
                ".order-id{font-weight:700;white-space:nowrap}",
                ".reason{color:var(--bad);font-weight:700;min-width:220px}",
                ".pill{display:inline-flex;align-items:center;border-radius:999px;padding:4px 10px;font-size:12px;font-weight:700;text-decoration:none;border:1px solid transparent;white-space:nowrap}",
                ".pill-success{background:var(--good-bg);color:var(--good);border-color:#bbf7d0}",
                ".pill-fail{background:var(--bad-bg);color:var(--bad);border-color:#fecaca}",
                ".pill-muted{background:#f3f4f6;color:#374151;border-color:#e5e7eb}",
                ".pill-warn{background:var(--warn-bg);color:var(--warn);border-color:#fde68a}",
                ".steps{display:grid;gap:7px;min-width:260px}",
                ".step{display:grid;grid-template-columns:minmax(160px,1fr) auto;gap:10px;align-items:center;background:var(--surface-soft);border:1px solid var(--line);border-radius:8px;padding:8px 10px}",
                ".step-name{font-weight:600}",
                ".footer{text-align:center;font-size:12px;color:var(--muted);margin-top:10px}",
                "@media (max-width:760px){",
                "  .page{padding:18px 12px 30px}",
                "  .header{display:block}",
                "  .meta{justify-content:flex-start;margin-top:12px}",
                "  .stats{grid-template-columns:repeat(2,minmax(0,1fr))}",
                "  .stat-value{font-size:23px}",
                "  .step{grid-template-columns:1fr}",
                "}",
                "@media (max-width:480px){.stats{grid-template-columns:1fr}}"
        );
    }

    private static String header(ReportData data) {
        return "<section class='header'>"
                + "<div><div class='title'>Jawwy Automation Report</div>"
                + "<div class='generated'>Generated: " + escape(data.getTimestamp()) + "</div></div>"
                + "<div class='meta'><span class='pill pill-muted'>" + escape(data.getFlow()) + "</span>"
                + "<span class='pill pill-muted'>" + escape(data.getEnvironment()) + "</span></div>"
                + "</section>";
    }

    private static String statsGrid(int total, int completed, int failed, int rate) {
        return "<section class='stats'>"
                + stat("Total Runs", total)
                + stat("Completed", completed)
                + stat("Failed", failed)
                + stat("Success Rate", rate + "%")
                + "</section>";
    }

    private static String stat(String label, Object value) {
        return "<div class='card'><div class='stat-label'>" + escape(label)
                + "</div><div class='stat-value'>" + escape(String.valueOf(value)) + "</div></div>";
    }

    private static String ordersTable(String title, List<OrderContext> orders, String flow, boolean success) {
        StringBuilder sb = new StringBuilder();
        sb.append("<section class='card'><div class='section-title'>")
                .append(escape(title)).append(" (").append(orders.size()).append(")</div>");

        if (orders.isEmpty()) {
            sb.append("<div class='empty'>None</div>");
        } else {
            sb.append("<div class='table-wrap'><table><thead><tr>")
                    .append("<th>Order</th><th>Flow</th><th>Duration</th><th>Status</th><th>Stages</th>");
            if (!success) {
                sb.append("<th>Failure Reason</th>");
            }
            sb.append("</tr></thead><tbody>");

            for (OrderContext ctx : orders) {
                sb.append("<tr>")
                        .append("<td class='order-id'>").append(escape(valueOrDefault(ctx.getOrderId(), "N/A"))).append("</td>")
                        .append("<td>").append(escape(flow)).append("</td>")
                        .append("<td>").append(escape(valueOrDefault(ctx.getRunDuration(), "-"))).append("</td>")
                        .append("<td>").append(statusPill(success ? "PASS" : "FAIL")).append("</td>")
                        .append("<td>").append(steps(ctx)).append("</td>");
                if (!success) {
                    sb.append("<td class='reason'>").append(escape(valueOrDefault(ctx.getFailureReason(), "-"))).append("</td>");
                }
                sb.append("</tr>");
            }
            sb.append("</tbody></table></div>");
        }
        sb.append("</section>");
        return sb.toString();
    }

    private static String steps(OrderContext ctx) {
        if (ctx.getStepLog().isEmpty()) {
            return "<div class='empty'>No stages were recorded</div>";
        }

        StringBuilder sb = new StringBuilder("<div class='steps'>");
        for (StepExecution step : ctx.getStepLog()) {
            sb.append("<div class='step'><div class='step-name'>")
                    .append(escape(step.getStepName()))
                    .append("</div><div>")
                    .append(statusPill(step.getStatus()))
                    .append("</div></div>");
        }
        sb.append("</div>");
        return sb.toString();
    }

    private static String statusPill(String status) {
        String normalized = status == null ? "UNKNOWN" : status.trim().toUpperCase();
        String cssClass;
        if ("PASSED".equals(normalized) || "PASS".equals(normalized)) {
            cssClass = "pill-success";
        } else if ("FAILED".equals(normalized) || "FAIL".equals(normalized)) {
            cssClass = "pill-fail";
        } else if ("SKIPPED".equals(normalized)) {
            cssClass = "pill-warn";
        } else {
            cssClass = "pill-muted";
        }
        return "<span class='pill " + cssClass + "'>" + escape(normalized) + "</span>";
    }

    private static String footer() {
        return "<footer class='footer'>Jawwy Automation Suite - Powered by Qeema</footer>";
    }

    private static String allureButton(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        return "<div class='allure'><a class='pill pill-success' href='" + escapeAttribute(url)
                + "' target='_blank'>View Allure Report</a></div>";
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

    private static String escapeAttribute(String value) {
        return escape(value).replace("`", "&#96;");
    }
}
