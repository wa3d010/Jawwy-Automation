package com.jawwy.automation.reporting;

import com.jawwy.automation.models.OrderContext;
import java.util.List;

public class HtmlReportBuilder {

    public static String build(ReportData data) {
        int total     = data.getRuns();
        int completed = data.getCompleted().size();
        int failed    = data.getFailed().size();
        int rate      = total > 0 ? (completed * 100 / total) : 0;

        return "<!DOCTYPE html><html><head><meta charset='UTF-8'>"
                + "<meta name='viewport' content='width=device-width, initial-scale=1'>"
                + "<title>Jawwy Report</title></head>"
                + "<body style='font-family:Arial,sans-serif;background:#f0f2f5;color:#1a1a2e;padding:24px;margin:0'>"
                + header(data)
                + allureButton(data.getAllureReportUrl())
                + statsGrid(total, completed, failed, rate)
                + progressSection(completed, failed, rate)
                + completedTable(data.getCompleted(), data.getFlow())
                + failedTable(data.getFailed(), data.getFlow())
                + timeline(data.getCompleted(), data.getFailed(), data.getFlow())
                + footer()
                + "</body></html>";
    }

    /* ================= HEADER ================= */

    private static String header(ReportData data) {
        return "<div style='background:#1a1a2e;color:white;padding:28px 32px;"
                + "border-radius:12px;margin-bottom:24px;display:flex;"
                + "justify-content:space-between;align-items:center'>"
                + "<div>"
                + "<div style='font-size:22px;font-weight:700;margin-bottom:4px'>Jawwy Automation Report</div>"
                + "<div style='font-size:13px;opacity:0.7'>Generated: "
                + data.getTimestamp()
                + "</div>"
                + "</div>"
                + "<div style='display:flex;gap:10px;align-items:center'>"
                + "<span style='background:rgba(255,255,255,0.15);padding:6px 16px;border-radius:20px;"
                + "font-size:13px;font-weight:600'>"
                + data.getFlow()
                + "</span>"
                + "<span style='background:#185FA5;padding:6px 16px;border-radius:20px;"
                + "font-size:13px;font-weight:600'>"
                + data.getEnvironment()
                + "</span>"
                + "</div>"
                + "</div>";
    }

    /* ================= STATS ================= */

    private static String statsGrid(int total, int completed, int failed, int rate) {
        return "<div style='display:grid;grid-template-columns:repeat(4,1fr);gap:16px;margin-bottom:24px'>"
                + statCard(String.valueOf(total), "Total Runs", "Requested runs", "#185FA5")
                + statCard(String.valueOf(completed), "Completed", "Successfully done", "#3B6D11")
                + statCard(String.valueOf(failed), "Failed", "Needs attention", "#A32D2D")
                + statCard(rate + "%", "Success Rate", "This execution", "#5F5E5A")
                + "</div>";
    }

    private static String statCard(String value, String label, String sub, String color) {
        return "<div style='background:white;border-radius:12px;padding:20px;border:1px solid #e8e8e8'>"
                + "<div style='font-size:12px;color:#888;text-transform:uppercase;margin-bottom:8px'>"
                + label
                + "</div>"
                + "<div style='font-size:32px;font-weight:700;color:" + color + "'>" + value + "</div>"
                + "<div style='font-size:12px;color:#888;margin-top:6px'>" + sub + "</div>"
                + "</div>";
    }

    /* ================= PROGRESS ================= */

    private static String progressSection(int completed, int failed, int rate) {
        return "<div style='background:white;border-radius:12px;padding:20px;margin-bottom:24px;border:1px solid #e8e8e8'>"
                + "<div style='font-size:13px;color:#888;margin-bottom:12px'>Execution progress</div>"
                + "<div style='background:#f0f2f5;border-radius:99px;height:12px;overflow:hidden;margin-bottom:8px'>"
                + "<div style='width:" + rate + "%;height:100%;background:#3B6D11'></div>"
                + "</div>"
                + "<div style='display:flex;justify-content:space-between;font-size:12px'>"
                + "<span style='color:#3B6D11'>" + completed + " completed</span>"
                + "<span style='color:#888'>" + failed + " failed</span>"
                + "</div></div>";
    }

    /* ================= COMPLETED ================= */

    private static String completedTable(List<OrderContext> orders, String flow) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div style='background:white;border-radius:12px;padding:20px;margin-bottom:20px;border:1px solid #e8e8e8'>")
          .append("<div style='font-size:15px;font-weight:600;margin-bottom:16px'>Completed orders (")
          .append(orders.size()).append(")</div>")
          .append("<table style='width:100%;border-collapse:collapse;font-size:13px'>");

        for (int i = 0; i < orders.size(); i++) {
            OrderContext ctx = orders.get(i);
            sb.append("<tr style='border-bottom:1px solid #f0f0f0'>")
              .append("<td>").append(i + 1).append("</td>")
              .append("<td><b>").append(ctx.getOrderId()).append("</b></td>")
              .append("<td>").append(flow).append("</td>")
              .append("<td>").append(ctx.getRunDuration()).append("</td>")
              .append("<td><span style='background:#EAF3DE;color:#3B6D11;padding:4px 10px;border-radius:20px'>SUCCESS</span></td>")
              .append("<td>").append(stepsExpander(ctx, i, "c")).append("</td>")
              .append("</tr>");
        }
        sb.append("</table></div>");
        return sb.toString();
    }

    /* ================= FAILED ================= */

    private static String failedTable(List<OrderContext> orders, String flow) {
        if (orders.isEmpty()) {
            return "<div style='background:white;border-radius:12px;padding:20px;border:1px solid #e8e8e8'>"
                    + "<div>No failed orders</div></div>";
        }
        return "";
    }

    /* ================= STEPS ================= */

    private static String stepsExpander(OrderContext ctx, int index, String prefix) {
    if (ctx.getStepLog().isEmpty()) return "-";

    String id = "steps-" + prefix + index;
    StringBuilder steps = new StringBuilder();

    for (OrderContext.StepExecution step : ctx.getStepLog()) {

        boolean success = step.getStatus() != null &&
                (step.getStatus().equalsIgnoreCase("PASSED")
              || step.getStatus().equalsIgnoreCase("SUCCESS"));

        String icon = success ? "✅" : "❌";
        String bg   = success ? "#EAF3DE" : "#FCEBEB";
        String fg   = success ? "#3B6D11" : "#A32D2D";

        steps.append("<div style='display:flex;justify-content:space-between;"
                + "align-items:center;padding:8px 12px;border-bottom:1px solid #f0f0f0;"
                + "font-size:12px'>")
             .append("<span>").append(icon).append(" ").append(step.getStepName()).append("</span>")
             .append("<span style='background:").append(bg)
             .append(";color:").append(fg)
             .append(";padding:2px 8px;border-radius:10px;font-weight:600'>")
             .append(step.getStatus()).append("</span>")
             .append("</div>");
    }

    return
        // ✅ Styled Expand button
        "<button onclick=\"var d=document.getElementById('" + id + "');"
      + "d.style.display=d.style.display==='none'?'block':'none'\""
      + " style='background:#f8f9fa;"
      + "border:1px solid #dcdcdc;"
      + "color:#1a1a2e;"
      + "border-radius:999px;"
      + "padding:6px 14px;"
      + "font-size:12px;"
      + "font-weight:600;"
      + "cursor:pointer;"
      + "transition:all .15s ease'>"
      + "Expand</button>"

      // ✅ Steps container
      + "<div id='" + id + "' style='display:none;margin-top:10px;"
      + "border:1px solid #e8e8e8;border-radius:10px;"
      + "overflow:hidden;background:white'>"
      + steps
      + "</div>";
}


    /* ================= TIMELINE ================= */

    private static String timeline(List<OrderContext> completed, List<OrderContext> failed, String flow) {
        return "";
    }

    /* ================= FOOTER ================= */

    private static String footer() {
        return "<div style='text-align:center;font-size:12px;color:#aaa;margin-top:16px'>"
                + "Jawwy Automation Suite — Powered by Qeema</div>";
    }

    /* ================= ALLURE ================= */

    private static String allureButton(String url) {
        if (url == null || url.trim().isEmpty()) return "";
        return "<div style='text-align:right;margin-bottom:20px'>"
                + "<a href='" + url + "' target='_blank'>"
                + "<span style='background:#185FA5;color:white;padding:10px 18px;border-radius:999px'>"
                + "View Allure Report</span></a></div>";
    }
}