package com.jawwy.automation.reporting;

import com.jawwy.automation.models.OrderContext;
import java.util.List;

public class HtmlReportBuilder {

    public static String build(ReportData data) {
        int total     = data.getRuns();
        int completed = data.getCompleted().size();
        int failed    = data.getFailed().size();
        int rate      = total > 0 ? (completed * 100 / total) : 0;

        return "<!DOCTYPE html><html><head><meta charset='UTF-8'><title>Jawwy Report</title></head>"
                + "<body style='font-family:Arial,sans-serif;background:#f0f2f5;color:#1a1a2e;padding:24px;margin:0'>"
                + header(data)
                + statsGrid(total, completed, failed, rate)
                + progressSection(completed, failed, rate)
                + completedTable(data.getCompleted(), data.getFlow())
                + failedTable(data.getFailed(), data.getFlow())
                + timeline(data.getCompleted(), data.getFailed(), data.getFlow())
                + footer()
                + "</body></html>";
    }

    private static String header(ReportData data) {
        return "<div style='background:#1a1a2e;color:white;padding:28px 32px;border-radius:12px;margin-bottom:24px;display:flex;justify-content:space-between;align-items:center'>"
                + "<div><div style='font-size:22px;font-weight:700;margin-bottom:4px'>Jawwy Automation Report</div>"
                + "<div style='font-size:13px;opacity:0.7'>Generated: " + data.getTimestamp() + "</div></div>"
                + "<div style='display:flex;gap:10px;align-items:center'>"
                + "<span style='background:rgba(255,255,255,0.15);color:white;padding:6px 16px;border-radius:20px;font-size:13px;font-weight:600'>" + data.getFlow() + "</span>"
                + "<span style='background:#185FA5;color:white;padding:6px 16px;border-radius:20px;font-size:13px;font-weight:600'>" + data.getEnvironment() + "</span>"
                + "</div></div>";
    }

    private static String statsGrid(int total, int completed, int failed, int rate) {
        return "<div style='display:grid;grid-template-columns:repeat(4,1fr);gap:16px;margin-bottom:24px'>"
                + statCard(String.valueOf(total),     "Total Runs",   "Requested runs",    "#185FA5")
                + statCard(String.valueOf(completed), "Completed",    "Successfully done", "#3B6D11")
                + statCard(String.valueOf(failed),    "Failed",       "Needs attention",   "#A32D2D")
                + statCard(rate + "%",                "Success Rate", "This execution",    "#5F5E5A")
                + "</div>";
    }

    private static String statCard(String value, String label, String sub, String color) {
        return "<div style='background:white;border-radius:12px;padding:20px;border:1px solid #e8e8e8'>"
                + "<div style='font-size:12px;color:#888;text-transform:uppercase;letter-spacing:0.5px;margin-bottom:8px'>" + label + "</div>"
                + "<div style='font-size:32px;font-weight:700;line-height:1;color:" + color + "'>" + value + "</div>"
                + "<div style='font-size:12px;color:#888;margin-top:6px'>" + sub + "</div>"
                + "</div>";
    }

    private static String progressSection(int completed, int failed, int rate) {
        return "<div style='background:white;border-radius:12px;padding:20px;margin-bottom:24px;border:1px solid #e8e8e8'>"
                + "<div style='font-size:13px;color:#888;text-transform:uppercase;letter-spacing:0.5px;margin-bottom:12px'>Execution progress</div>"
                + "<div style='background:#f0f2f5;border-radius:99px;height:12px;overflow:hidden;margin-bottom:8px'>"
                + "<div style='width:" + rate + "%;height:100%;border-radius:99px;background:#3B6D11'></div></div>"
                + "<div style='display:flex;justify-content:space-between;font-size:12px'>"
                + "<span style='color:#3B6D11;font-weight:600'>" + completed + " completed</span>"
                + "<span style='color:#888'>" + failed + " failed</span>"
                + "</div></div>";
    }

    private static String completedTable(List<OrderContext> orders, String flow) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div style='background:white;border-radius:12px;padding:20px;margin-bottom:20px;border:1px solid #e8e8e8'>")
                .append("<div style='font-size:15px;font-weight:600;margin-bottom:16px;display:flex;align-items:center;gap:8px'>")
                .append("<span style='width:10px;height:10px;border-radius:50%;background:#3B6D11;display:inline-block'></span>")
                .append("Completed orders (").append(orders.size()).append(")</div>")
                .append("<table style='width:100%;border-collapse:collapse;font-size:13px'>")
                .append("<tr style='background:#f8f9fa'>")
                .append("<th style='color:#666;font-weight:600;padding:10px 14px;text-align:left;font-size:12px;text-transform:uppercase'>#</th>")
                .append("<th style='color:#666;font-weight:600;padding:10px 14px;text-align:left;font-size:12px;text-transform:uppercase'>Order ID</th>")
                .append("<th style='color:#666;font-weight:600;padding:10px 14px;text-align:left;font-size:12px;text-transform:uppercase'>Flow</th>")
                .append("<th style='color:#666;font-weight:600;padding:10px 14px;text-align:left;font-size:12px;text-transform:uppercase'>Duration</th>")
                .append("<th style='color:#666;font-weight:600;padding:10px 14px;text-align:left;font-size:12px;text-transform:uppercase'>Status</th>")
                .append("<th style='color:#666;font-weight:600;padding:10px 14px;text-align:left;font-size:12px;text-transform:uppercase'>Steps</th>")
                .append("</tr>");

        if (orders.isEmpty()) {
            sb.append("<tr><td colspan='6' style='text-align:center;color:#aaa;padding:24px'>No completed orders</td></tr>");
        } else {
            for (int i = 0; i < orders.size(); i++) {
                OrderContext ctx = orders.get(i);
                sb.append("<tr style='border-bottom:1px solid #f0f0f0'>")
                        .append("<td style='padding:12px 14px;color:#888'>").append(i + 1).append("</td>")
                        .append("<td style='padding:12px 14px'><b>").append(ctx.getOrderId()).append("</b></td>")
                        .append("<td style='padding:12px 14px'>").append(flow).append("</td>")
                        .append("<td style='padding:12px 14px;color:#555'>").append(ctx.getRunDuration()).append("</td>")
                        .append("<td style='padding:12px 14px'><span style='background:#EAF3DE;color:#3B6D11;padding:4px 10px;border-radius:20px;font-size:12px;font-weight:600'>SUCCESS</span></td>")
                        .append("<td style='padding:12px 14px'>").append(stepsExpander(ctx, i, "c")).append("</td>")
                        .append("</tr>");
            }
        }
        sb.append("</table></div>");
        return sb.toString();
    }

    private static String failedTable(List<OrderContext> orders, String flow) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div style='background:white;border-radius:12px;padding:20px;margin-bottom:20px;border:1px solid #e8e8e8'>")
                .append("<div style='font-size:15px;font-weight:600;margin-bottom:16px;display:flex;align-items:center;gap:8px'>")
                .append("<span style='width:10px;height:10px;border-radius:50%;background:#A32D2D;display:inline-block'></span>")
                .append("Failed orders (").append(orders.size()).append(")</div>")
                .append("<table style='width:100%;border-collapse:collapse;font-size:13px'>")
                .append("<tr style='background:#f8f9fa'>")
                .append("<th style='color:#666;font-weight:600;padding:10px 14px;text-align:left;font-size:12px;text-transform:uppercase'>#</th>")
                .append("<th style='color:#666;font-weight:600;padding:10px 14px;text-align:left;font-size:12px;text-transform:uppercase'>Order ID</th>")
                .append("<th style='color:#666;font-weight:600;padding:10px 14px;text-align:left;font-size:12px;text-transform:uppercase'>Flow</th>")
                .append("<th style='color:#666;font-weight:600;padding:10px 14px;text-align:left;font-size:12px;text-transform:uppercase'>Duration</th>")
                .append("<th style='color:#666;font-weight:600;padding:10px 14px;text-align:left;font-size:12px;text-transform:uppercase'>Status</th>")
                .append("<th style='color:#666;font-weight:600;padding:10px 14px;text-align:left;font-size:12px;text-transform:uppercase'>Failure Reason</th>")
                .append("<th style='color:#666;font-weight:600;padding:10px 14px;text-align:left;font-size:12px;text-transform:uppercase'>Steps</th>")
                .append("</tr>");

        if (orders.isEmpty()) {
            sb.append("<tr><td colspan='7' style='text-align:center;color:#aaa;padding:24px'>No failed orders</td></tr>");
        } else {
            for (int i = 0; i < orders.size(); i++) {
                OrderContext ctx    = orders.get(i);
                String       reason = ctx.getFailureReason() != null ? ctx.getFailureReason() : "-";
                sb.append("<tr style='border-bottom:1px solid #f0f0f0'>")
                        .append("<td style='padding:12px 14px;color:#888'>").append(i + 1).append("</td>")
                        .append("<td style='padding:12px 14px'><b>").append(ctx.getOrderId() != null ? ctx.getOrderId() : "N/A").append("</b></td>")
                        .append("<td style='padding:12px 14px'>").append(flow).append("</td>")
                        .append("<td style='padding:12px 14px;color:#555'>").append(ctx.getRunDuration()).append("</td>")
                        .append("<td style='padding:12px 14px'><span style='background:#FCEBEB;color:#A32D2D;padding:4px 10px;border-radius:20px;font-size:12px;font-weight:600'>FAILED</span></td>")
                        .append("<td style='padding:12px 14px;color:#A32D2D;font-size:12px'>").append(reason).append("</td>")
                        .append("<td style='padding:12px 14px'>").append(stepsExpander(ctx, i, "f")).append("</td>")
                        .append("</tr>");
            }
        }
        sb.append("</table></div>");
        return sb.toString();
    }

    private static String stepsExpander(OrderContext ctx, int index, String prefix) {
        if (ctx.getStepLog().isEmpty()) return "-";
        String id = "steps-" + prefix + index;

        StringBuilder steps = new StringBuilder();
        for (String[] step : ctx.getStepLog()) {
            boolean success = step[1].equals("SUCCESS");
            String  color   = success ? "#3B6D11" : "#A32D2D";
            String  bg      = success ? "#EAF3DE"  : "#FCEBEB";
            String  icon    = success ? "✅" : "❌";
            steps.append("<div style='display:flex;justify-content:space-between;align-items:center;padding:6px 10px;border-bottom:1px solid #f0f0f0;font-size:12px'>")
                    .append("<span>").append(icon).append(" ").append(step[0]).append("</span>")
                    .append("<span style='background:").append(bg).append(";color:").append(color)
                    .append(";padding:2px 8px;border-radius:10px;font-weight:600'>").append(step[1]).append("</span>")
                    .append("</div>");
        }

        return "<button onclick=\"var d=document.getElementById('" + id + "');"
                + "d.style.display=d.style.display==='none'?'block':'none'\""
                + " style='background:#f0f2f5;border:1px solid #ddd;border-radius:6px;padding:4px 10px;font-size:12px;cursor:pointer'>View Steps</button>"
                + "<div id='" + id + "' style='display:none;margin-top:8px;border:1px solid #e8e8e8;border-radius:8px;overflow:hidden'>"
                + steps
                + "</div>";
    }

    private static String timeline(List<OrderContext> completed, List<OrderContext> failed, String flow) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div style='background:white;border-radius:12px;padding:20px;margin-bottom:20px;border:1px solid #e8e8e8'>")
                .append("<div style='font-size:15px;font-weight:600;margin-bottom:16px'>Run timeline</div>")
                .append("<div>");

        int run = 1;
        for (OrderContext ctx : completed) {
            sb.append("<div style='display:flex;gap:16px;padding:14px 0;border-bottom:1px solid #f0f0f0'>")
                    .append("<div style='width:28px;height:28px;border-radius:50%;background:#EAF3DE;color:#3B6D11;display:flex;align-items:center;justify-content:center;font-size:12px;font-weight:700;flex-shrink:0'>").append(run++).append("</div>")
                    .append("<div><div style='font-size:14px;font-weight:600;color:#1a1a2e;margin-bottom:4px'>Order ").append(ctx.getOrderId()).append("</div>")
                    .append("<div style='font-size:12px;color:#888'>Flow: ").append(flow)
                    .append(" &nbsp;|&nbsp; Duration: ").append(ctx.getRunDuration())
                    .append(" &nbsp;|&nbsp; Result: Completed successfully</div>")
                    .append("</div></div>");
        }
        for (OrderContext ctx : failed) {
            sb.append("<div style='display:flex;gap:16px;padding:14px 0;border-bottom:1px solid #f0f0f0'>")
                    .append("<div style='width:28px;height:28px;border-radius:50%;background:#FCEBEB;color:#A32D2D;display:flex;align-items:center;justify-content:center;font-size:12px;font-weight:700;flex-shrink:0'>").append(run++).append("</div>")
                    .append("<div><div style='font-size:14px;font-weight:600;color:#1a1a2e;margin-bottom:4px'>Order ").append(ctx.getOrderId() != null ? ctx.getOrderId() : "N/A").append("</div>")
                    .append("<div style='font-size:12px;color:#888'>Flow: ").append(flow)
                    .append(" &nbsp;|&nbsp; Duration: ").append(ctx.getRunDuration())
                    .append(" &nbsp;|&nbsp; Result: Failed</div>")
                    .append("<div style='font-size:12px;color:#A32D2D;margin-top:4px'>").append(ctx.getFailureReason() != null ? ctx.getFailureReason() : "-").append("</div>")
                    .append("</div></div>");
        }

        sb.append("</div></div>");
        return sb.toString();
    }

    private static String footer() {
        return "<div style='text-align:center;font-size:12px;color:#aaa;margin-top:8px'>Jawwy Automation Suite &mdash; Powered by Qeema</div>";
    }
}
