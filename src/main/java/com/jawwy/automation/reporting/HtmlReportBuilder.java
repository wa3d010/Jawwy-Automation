package com.jawwy.automation.reporting;

import com.jawwy.automation.models.OrderContext;
import java.util.List;

public class HtmlReportBuilder {

    public static String build(ReportData data) {
        int total = data.getRuns();
        int completed = data.getCompleted().size();
        int failed = data.getFailed().size();
        int rate = total > 0 ? (completed * 100 / total) : 0;

        return "<!DOCTYPE html><html><head><meta charset='UTF-8'>"
                + "<meta name='viewport' content='width=device-width, initial-scale=1'>"
                + "<title>Jawwy Automation Report</title>"
                + "<style>" + css() + "</style>"
                + "</head><body>"
                + header(data)
                + allureButton(data.getAllureReportUrl())
                + statsGrid(total, completed, failed, rate)
                + completedTable(data.getCompleted(), data.getFlow())
                + failedTable(data.getFailed(), data.getFlow())
                + footer()
                + "</body></html>";
    }

    /* ================= CSS ================= */

  private static String css() {
    return ""
        + "body { font-family: Arial, sans-serif; background:#f0f2f5; margin:0; padding:24px; color:#1a1a2e; }\n"
        + ".card { background:white; border-radius:12px; padding:20px; margin-bottom:20px; border:1px solid #e8e8e8 }\n"
        + ".header { background:#1a1a2e; color:white; padding:28px; border-radius:12px; display:flex; justify-content:space-between }\n"
        + ".stats { display:grid; grid-template-columns:repeat(4,1fr); gap:16px; margin-bottom:24px }\n"
        + ".pill-success { background:#EAF3DE; color:#3B6D11; padding:4px 12px; border-radius:20px }\n"
        + ".pill-fail { background:#FCEBEB; color:#A32D2D; padding:4px 12px; border-radius:20px }\n"
        + "table { width:100%; border-collapse:collapse; font-size:13px }\n"
        + "td { padding:10px; border-bottom:1px solid #eee }\n"
        + ".error { color:#A32D2D; font-weight:600 }\n"
        + "button { cursor:pointer; }\n";
}


    /* ================= HEADER ================= */

    private static String header(ReportData data) {
        return "<div class='header'>"
                + "<div><div style='font-size:22px;font-weight:700'>Jawwy Automation Report</div>"
                + "<div style='font-size:12px;opacity:0.7'>Generated: " + data.getTimestamp() + "</div></div>"
                + "<div><span class='pill-success'>" + data.getFlow() + "</span> "
                + "<span class='pill-success'>" + data.getEnvironment() + "</span></div>"
                + "</div>";
    }

    /* ================= STATS ================= */

    private static String statsGrid(int total, int completed, int failed, int rate) {
        return "<div class='stats'>"
                + stat("Total Runs", total)
                + stat("Completed", completed)
                + stat("Failed", failed)
                + stat("Success Rate", rate + "%")
                + "</div>";
    }

    private static String stat(String label, Object value) {
        return "<div class='card'><div style='font-size:12px;color:#777'>" + label +
                "</div><div style='font-size:28px;font-weight:700'>" + value + "</div></div>";
    }

    /* ================= COMPLETED ================= */

    private static String completedTable(List<OrderContext> orders, String flow) {
        return renderTable("Completed orders", orders, flow, true);
    }

    private static String failedTable(List<OrderContext> orders, String flow) {
        return renderTable("Failed orders", orders, flow, false);
    }

    private static String renderTable(String title, List<OrderContext> orders, String flow, boolean success) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class='card'><div style='font-size:16px;font-weight:600'>")
                .append(title).append(" (").append(orders.size()).append(")</div>");

        if (orders.isEmpty()) {
            sb.append("<div style='color:#777;margin-top:10px'>None</div>");
        } else {
            sb.append("<table>");
            for (OrderContext ctx : orders) {
                sb.append("<tr>")
                        .append("<td>").append(ctx.getOrderId() != null ? ctx.getOrderId() : "N/A").append("</td>")
                        .append("<td>").append(flow).append("</td>")
                        .append("<td>").append(ctx.getRunDuration()).append("</td>")
                        .append("<td>")
                        .append(success
                                ? "<span class='pill-success'>PASS</span>"
                                : "<span class='pill-fail'>FAIL</span>")
                        .append("</td>")
                        .append("<td class='error'>")
                        .append(success ? "" : escape(ctx.getFailureReason()))
                        .append("</td>")
                        .append("</tr>");
            }
            sb.append("</table>");
        }
        sb.append("</div>");
        return sb.toString();
    }

    /* ================= FOOTER ================= */

    private static String footer() {
        return "<div style='text-align:center;font-size:12px;color:#aaa;margin-top:16px'>"
                + "Jawwy Automation Suite — Powered by Qeema</div>";
    }

    /* ================= ALLURE ================= */

    private static String allureButton(String url) {
        if (url == null || url.isBlank()) return "";
        return "<div style='text-align:right;margin-bottom:20px'>"
                + "<a href='" + url + "' target='_blank'>"
                + "<span class='pill-success'>View Allure Report</span></a></div>";
    }

    /* ================= ESCAPE ================= */

    private static String escape(String v) {
        if (v == null) return "";
        return v.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }
}