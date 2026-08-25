package com.fda.automation.reporting;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Generates a self-contained HTML report (screenshots embedded as Base64).
 * Output written to target/surefire-reports/fda-report.html by TestListener.
 */
public final class HtmlReportGenerator {
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private HtmlReportGenerator() {}

    public static String generate(List<TestRecord> records, long suiteStartMs, long suiteEndMs) {
        int total   = records.size();
        int passed  = (int) records.stream().filter(r -> "PASS".equals(r.getStatus())).count();
        int failed  = (int) records.stream().filter(r -> "FAIL".equals(r.getStatus())).count();
        int skipped = total - passed - failed;
        double passPercent = total > 0 ? (passed  * 100.0 / total) : 0;
        double failPercent = total > 0 ? (failed  * 100.0 / total) : 0;

        long   secs     = (suiteEndMs - suiteStartMs) / 1000;
        String duration = String.format("%02d:%02d:%02d", secs / 3600, (secs % 3600) / 60, secs % 60);

        StringBuilder sb = new StringBuilder(131072);
        sb.append(HTML_HEAD);

        // ---- Header ----
        sb.append("<div class='hdr'>\n");
        sb.append("  <h1>FDA Test Automation Report</h1>\n");
        sb.append("  <p>Generated: ").append(LocalDateTime.now().format(DT_FMT)).append("</p>\n");
        sb.append("</div>\n");

        // ---- Summary cards ----
        sb.append("<div class='cards'>\n");
        card(sb, "Total",   String.valueOf(total),   "total");
        card(sb, "Passed",  String.valueOf(passed),  "pass");
        card(sb, "Failed",  String.valueOf(failed),  "fail");
        card(sb, "Skipped", String.valueOf(skipped), "skip");
        card(sb, "Pass %",  String.format("%.0f%%", passPercent), "pass");
        card(sb, "Fail %",  String.format("%.0f%%", failPercent), "fail");
        sb.append("</div>\n");

        // ---- Timing bar ----
        sb.append("<div class='timing'>");
        sb.append("<span><b>Start:</b> ").append(fmtMs(suiteStartMs)).append("</span>");
        sb.append("<span><b>End:</b> ").append(fmtMs(suiteEndMs)).append("</span>");
        sb.append("<span><b>Duration:</b> ").append(duration).append("</span>");
        sb.append("</div>\n");

        // ---- Overview table ----
        sb.append("<h2>Test Case Summary</h2>\n");
        sb.append("<table class='ov-tbl'>\n");
        sb.append("<thead><tr><th>Test ID</th><th>Description</th><th>Status</th><th>Failed Step</th><th>Duration</th></tr></thead>\n");
        sb.append("<tbody>\n");
        for (TestRecord r : records) {
            StepRecord fs       = r.getFailedStep();
            String failedStepTx = fs != null ? String.format("Step %02d &mdash; %s", fs.getNumber(), esc(fs.getDescription())) : "N/A";
            sb.append("<tr class='row-").append(r.getStatus().toLowerCase()).append("'>\n");
            sb.append("  <td><a href='#").append(r.getTestId()).append("'>").append(esc(r.getTestId())).append("</a></td>\n");
            sb.append("  <td>").append(esc(r.getDescription())).append("</td>\n");
            sb.append("  <td>").append(badge(r.getStatus())).append("</td>\n");
            sb.append("  <td>").append(failedStepTx).append("</td>\n");
            sb.append("  <td>").append(r.getFormattedDuration()).append("</td>\n");
            sb.append("</tr>\n");
        }
        sb.append("</tbody></table>\n");

        // ---- Individual test cards ----
        sb.append("<h2>Test Case Details</h2>\n");
        for (TestRecord r : records) {
            sb.append(testCard(r));
        }

        sb.append("<div class='footer'>FDA Test Automation &mdash; ").append(LocalDateTime.now().format(DT_FMT)).append("</div>\n");
        sb.append("</body></html>\n");
        return sb.toString();
    }

    // =========================================================================
    // Test card
    // =========================================================================

    private static String testCard(TestRecord r) {
        StringBuilder sb = new StringBuilder(8192);
        String sc = r.getStatus().toLowerCase();

        sb.append("<details class='tc ").append(sc).append("' id='").append(r.getTestId()).append("'");
        if ("FAIL".equals(r.getStatus())) sb.append(" open");
        sb.append(">\n");

        // Summary (clickable header)
        sb.append("  <summary class='tc-hdr'>\n");
        sb.append("    <span class='tc-id'>").append(esc(r.getTestId())).append("</span>\n");
        sb.append("    ").append(badge(r.getStatus())).append("\n");
        sb.append("    <span class='tc-dur'>&#9201; ").append(r.getFormattedDuration()).append("</span>\n");
        sb.append("  </summary>\n");

        // Body
        sb.append("  <div class='tc-body'>\n");

        // Meta
        sb.append("    <div class='meta-grid'>\n");
        sb.append("      <div><b>Description:</b> ").append(esc(r.getDescription())).append("</div>\n");
        sb.append("      <div><b>Method:</b> <code>").append(esc(r.getMethodName())).append("</code></div>\n");
        sb.append("      <div><b>Start:</b> ").append(r.getStartTime()).append("</div>\n");
        sb.append("      <div><b>End:</b> ").append(r.getEndTime()).append("</div>\n");
        sb.append("    </div>\n");

        // Steps
        sb.append("    <h3>Execution Steps</h3>\n");
        if (r.getSteps().isEmpty()) {
            sb.append("    <p class='no-steps'>No steps recorded.</p>\n");
        } else {
            sb.append("    <table class='steps-tbl'>\n");
            sb.append("      <thead><tr><th>#</th><th>Description</th><th>Status</th><th>Time</th><th>Duration</th></tr></thead>\n");
            sb.append("      <tbody>\n");
            for (StepRecord step : r.getSteps()) {
                String ssc = step.getStatus().name().toLowerCase();
                sb.append("        <tr class='s-").append(ssc).append("'>\n");
                sb.append("          <td>").append(String.format("%02d", step.getNumber())).append("</td>\n");
                sb.append("          <td>").append(stepIcon(step.getStatus())).append(" ").append(esc(step.getDescription())).append("</td>\n");
                sb.append("          <td>").append(badgeSm(step.getStatus().name())).append("</td>\n");
                sb.append("          <td>").append(step.getTimestamp()).append("</td>\n");
                sb.append("          <td>").append(step.getDurationMs()).append(" ms</td>\n");
                sb.append("        </tr>\n");

                // Failure detail row
                if (step.getStatus() == StepStatus.FAIL) {
                    sb.append("        <tr class='s-fail-detail'><td colspan='5'>\n");
                    sb.append("          <div class='fail-box'>\n");
                    if (step.getExpected() != null)     sb.append("            <p><b>Expected:</b> <code>").append(esc(step.getExpected())).append("</code></p>\n");
                    if (step.getActual() != null)       sb.append("            <p><b>Actual:</b> <code>").append(esc(step.getActual())).append("</code></p>\n");
                    if (step.getErrorMessage() != null) sb.append("            <p><b>Exception:</b> <code class='err'>").append(esc(step.getErrorMessage())).append("</code></p>\n");
                    if (step.getScreenshotBase64() != null) {
                        sb.append("            <div class='ss-section'><b>Failure Screenshot:</b><br>\n");
                        sb.append("            <img src='data:image/png;base64,").append(step.getScreenshotBase64()).append("' class='ss'/></div>\n");
                    }
                    sb.append("          </div>\n");
                    sb.append("        </td></tr>\n");
                }
            }
            sb.append("      </tbody>\n");
            sb.append("    </table>\n");
        }

        // Failure summary block (below steps for quick reference)
        if ("FAIL".equals(r.getStatus())) {
            StepRecord fs = r.getFailedStep();
            if (fs != null) {
                sb.append("    <div class='fail-summary'>\n");
                sb.append("      <h3>&#9888; Failure Summary</h3>\n");
                sb.append("      <table class='fsm-tbl'>\n");
                sb.append("        <tr><th>Failed Step</th><td>Step ").append(String.format("%02d", fs.getNumber()))
                  .append(" &mdash; ").append(esc(fs.getDescription())).append("</td></tr>\n");
                if (fs.getExpected()     != null) sb.append("        <tr><th>Expected</th><td>").append(esc(fs.getExpected())).append("</td></tr>\n");
                if (fs.getActual()       != null) sb.append("        <tr><th>Actual</th><td>").append(esc(fs.getActual())).append("</td></tr>\n");
                if (fs.getErrorMessage() != null) sb.append("        <tr><th>Exception</th><td><code class='err'>").append(esc(fs.getErrorMessage())).append("</code></td></tr>\n");
                if (r.getScreenshotPath()!= null) sb.append("        <tr><th>Screenshot Path</th><td><code>").append(esc(r.getScreenshotPath())).append("</code></td></tr>\n");
                sb.append("      </table>\n");
                if (r.getScreenshotBase64() != null) {
                    sb.append("      <div class='ss-section'><b>Failure Screenshot:</b><br>\n");
                    sb.append("      <img src='data:image/png;base64,").append(r.getScreenshotBase64()).append("' class='ss'/></div>\n");
                }
                sb.append("    </div>\n");
            }
        }

        // Final screenshot for passed test
        if ("PASS".equals(r.getStatus()) && r.getScreenshotBase64() != null) {
            sb.append("    <div class='ss-section'>\n");
            sb.append("      <h3>Final Screenshot</h3>\n");
            sb.append("      <img src='data:image/png;base64,").append(r.getScreenshotBase64()).append("' class='ss'/>\n");
            sb.append("    </div>\n");
        }

        sb.append("  </div>\n"); // tc-body
        sb.append("</details>\n\n");
        return sb.toString();
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static void card(StringBuilder sb, String label, String value, String cls) {
        sb.append("  <div class='card ").append(cls).append("'>\n");
        sb.append("    <div class='cv'>").append(value).append("</div>\n");
        sb.append("    <div class='cl'>").append(label).append("</div>\n");
        sb.append("  </div>\n");
    }

    private static String badge(String status) {
        return "<span class='badge " + status.toLowerCase() + "'>" + status + "</span>";
    }

    private static String badgeSm(String status) {
        return "<span class='badge-sm " + status.toLowerCase() + "'>" + status + "</span>";
    }

    private static String stepIcon(StepStatus s) {
        return switch (s) {
            case PASS    -> "&#10003;";
            case FAIL    -> "&#10007;";
            case SKIP    -> "&#8904;";
            default      -> "&#8226;";
        };
    }

    private static String fmtMs(long ms) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault()).format(DT_FMT);
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    // =========================================================================
    // HTML head + CSS
    // =========================================================================

    private static final String HTML_HEAD = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
              <meta charset="UTF-8">
              <meta name="viewport" content="width=device-width,initial-scale=1.0">
              <title>FDA Test Automation Report</title>
              <style>
                *{box-sizing:border-box;margin:0;padding:0}
                body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;
                     background:#f0f2f5;color:#1a1a2e;font-size:14px;line-height:1.5}

                /* Header */
                .hdr{background:linear-gradient(135deg,#1a1a2e 0%,#16213e 55%,#0f3460 100%);
                     color:#fff;padding:28px 40px}
                .hdr h1{font-size:26px;font-weight:700;margin-bottom:4px}
                .hdr p{opacity:.65;font-size:12px}

                /* Summary cards */
                .cards{display:flex;flex-wrap:wrap;gap:14px;padding:22px 40px}
                .card{background:#fff;border-radius:12px;padding:18px 22px;flex:1;min-width:100px;
                      box-shadow:0 2px 8px rgba(0,0,0,.08);text-align:center}
                .cv{font-size:34px;font-weight:700;margin-bottom:2px}
                .cl{font-size:11px;text-transform:uppercase;letter-spacing:.5px;opacity:.55}
                .card.pass .cv{color:#22c55e}.card.fail .cv{color:#ef4444}
                .card.skip .cv{color:#f59e0b}.card.total .cv{color:#3b82f6}

                /* Timing */
                .timing{background:#fff;margin:0 40px 24px;padding:14px 20px;border-radius:8px;
                        box-shadow:0 2px 6px rgba(0,0,0,.06);display:flex;gap:28px;flex-wrap:wrap;
                        font-size:13px}

                h2{font-size:19px;font-weight:600;padding:0 40px 14px;margin-top:6px}
                h3{font-size:14px;font-weight:600;margin-bottom:10px;color:#374151}

                /* Overview table */
                .ov-tbl{width:calc(100% - 80px);margin:0 40px 28px;border-collapse:collapse;
                        background:#fff;border-radius:8px;overflow:hidden;
                        box-shadow:0 2px 8px rgba(0,0,0,.06)}
                .ov-tbl th{background:#1a1a2e;color:#fff;padding:11px 16px;text-align:left;
                           font-size:11px;text-transform:uppercase;letter-spacing:.5px}
                .ov-tbl td{padding:11px 16px;border-bottom:1px solid #f0f0f0;font-size:13px}
                .ov-tbl tr:last-child td{border-bottom:none}
                .ov-tbl .row-fail td{background:#fff5f5}
                .ov-tbl .row-pass td{background:#f0fdf4}
                .ov-tbl a{color:#3b82f6;text-decoration:none;font-weight:600}
                .ov-tbl a:hover{text-decoration:underline}

                /* Badges */
                .badge,.badge-sm{display:inline-block;border-radius:10px;font-weight:700;
                                 text-transform:uppercase;letter-spacing:.4px}
                .badge{padding:4px 12px;font-size:12px}
                .badge-sm{padding:2px 8px;font-size:10px}
                .badge.pass,.badge-sm.pass{background:#dcfce7;color:#166534}
                .badge.fail,.badge-sm.fail{background:#fee2e2;color:#991b1b}
                .badge.skip,.badge-sm.skip{background:#fef9c3;color:#854d0e}
                .badge.running,.badge-sm.running{background:#dbeafe;color:#1e40af}

                /* Test card (collapsible) */
                .tc{margin:0 40px 14px;border-radius:10px;overflow:hidden;
                    box-shadow:0 2px 8px rgba(0,0,0,.08)}
                .tc.pass{border-left:5px solid #22c55e}
                .tc.fail{border-left:5px solid #ef4444}
                .tc.skip{border-left:5px solid #f59e0b}
                .tc-hdr{background:#fff;padding:14px 18px;cursor:pointer;
                        display:flex;align-items:center;gap:12px;user-select:none;
                        list-style:none;font-size:14px}
                .tc-hdr::-webkit-details-marker{display:none}
                .tc-hdr::before{content:'\\25B6';font-size:10px;transition:transform .2s;color:#6b7280}
                details[open]>.tc-hdr::before{transform:rotate(90deg)}
                .tc-id{font-size:16px;font-weight:700;flex:1}
                .tc-dur{font-size:12px;color:#6b7280}

                .tc-body{background:#fff;padding:18px 20px;border-top:1px solid #f0f0f0}

                .meta-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(260px,1fr));
                           gap:10px;margin-bottom:18px;background:#f8fafc;border-radius:8px;padding:14px;
                           font-size:13px}

                /* Steps table */
                .steps-tbl{width:100%;border-collapse:collapse;margin-bottom:18px;font-size:13px}
                .steps-tbl th{background:#f8fafc;padding:9px 12px;text-align:left;
                              font-size:11px;text-transform:uppercase;color:#6b7280;
                              border-bottom:2px solid #e5e7eb}
                .steps-tbl td{padding:9px 12px;border-bottom:1px solid #f3f4f6}
                .steps-tbl tr:hover td{background:#fafafa}
                .s-fail td{background:#fff8f8}
                .s-fail-detail td{background:#fff1f1 !important;padding:6px 12px 14px}

                .no-steps{color:#9ca3af;font-style:italic;padding:12px}

                .fail-box{padding:8px 12px;font-size:13px}
                .fail-box p{margin-bottom:6px}
                code{font-family:'Courier New',monospace;font-size:12px;background:#f1f5f9;
                     padding:2px 6px;border-radius:4px;word-break:break-all}
                .err{color:#dc2626 !important;background:#fef2f2 !important}

                /* Failure summary */
                .fail-summary{background:#fff8f8;border:1px solid #fecaca;border-radius:8px;
                              padding:14px;margin-top:14px}
                .fail-summary h3{color:#dc2626;margin-bottom:10px}
                .fsm-tbl{width:100%;border-collapse:collapse;margin-bottom:10px;font-size:13px}
                .fsm-tbl th{background:#fef2f2;padding:8px 12px;text-align:left;width:140px;
                            color:#991b1b;border:1px solid #fecaca;font-size:12px}
                .fsm-tbl td{padding:8px 12px;border:1px solid #fecaca;word-break:break-word}

                /* Screenshot */
                .ss-section{margin-top:14px}
                .ss{max-width:100%;border:2px solid #e5e7eb;border-radius:8px;margin-top:8px;
                    box-shadow:0 4px 12px rgba(0,0,0,.12);display:block}

                .footer{text-align:center;padding:28px;color:#9ca3af;font-size:12px}
              </style>
            </head>
            <body>
            """;
}
