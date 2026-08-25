package com.fda.automation.reporting;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class TestRecord {
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private final String testId;
    private final String methodName;
    private final String description;
    private final String status;        // PASS | FAIL | SKIP
    private final String startTime;
    private final String endTime;
    private final long   durationMs;
    private final List<StepRecord> steps;
    private final String screenshotBase64; // final (pass) or failure screenshot
    private final String screenshotPath;

    public TestRecord(String testId, String methodName, String description, String status,
                      long startMs, long endMs,
                      List<StepRecord> steps,
                      String screenshotBase64, String screenshotPath) {
        this.testId           = testId;
        this.methodName       = methodName;
        this.description      = description;
        this.status           = status;
        this.startTime        = fmt(startMs);
        this.endTime          = fmt(endMs);
        this.durationMs       = endMs - startMs;
        this.steps            = steps;
        this.screenshotBase64 = screenshotBase64;
        this.screenshotPath   = screenshotPath;
    }

    // --- Getters ---------------------------------------------------------------

    public String          getTestId()           { return testId; }
    public String          getMethodName()        { return methodName; }
    public String          getDescription()       { return description; }
    public String          getStatus()            { return status; }
    public String          getStartTime()         { return startTime; }
    public String          getEndTime()           { return endTime; }
    public long            getDurationMs()        { return durationMs; }
    public List<StepRecord> getSteps()            { return steps; }
    public String          getScreenshotBase64()  { return screenshotBase64; }
    public String          getScreenshotPath()    { return screenshotPath; }

    public String getFormattedDuration() {
        long secs = durationMs / 1000;
        return String.format("%02d:%02d", secs / 60, secs % 60);
    }

    /** Returns the first step that failed, or null if none. */
    public StepRecord getFailedStep() {
        return steps.stream()
                .filter(s -> s.getStatus() == StepStatus.FAIL)
                .findFirst()
                .orElse(null);
    }

    // -------------------------------------------------------------------------

    private static String fmt(long epochMs) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMs), ZoneId.systemDefault())
                .format(TIME_FMT);
    }
}
