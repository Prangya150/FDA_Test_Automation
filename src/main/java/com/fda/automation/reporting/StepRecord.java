package com.fda.automation.reporting;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class StepRecord {
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private final int number;
    private final String description;
    private final String timestamp;
    private final long startMs;

    private StepStatus status = StepStatus.RUNNING;
    private long durationMs;
    private String expected;
    private String actual;
    private String errorMessage;
    private String screenshotBase64;

    public StepRecord(int number, String description) {
        this.number = number;
        this.description = description;
        this.timestamp = LocalDateTime.now().format(TIME_FMT);
        this.startMs = System.currentTimeMillis();
    }

    /** Completes the step with the given status. Only transitions once from RUNNING. */
    public void complete(StepStatus status) {
        if (this.status == StepStatus.RUNNING) {
            this.status = status;
            this.durationMs = System.currentTimeMillis() - startMs;
        }
    }

    public int getNumber()            { return number; }
    public String getDescription()    { return description; }
    public String getTimestamp()      { return timestamp; }
    public StepStatus getStatus()     { return status; }
    public long getDurationMs()       { return durationMs; }
    public String getExpected()       { return expected; }
    public String getActual()         { return actual; }
    public String getErrorMessage()   { return errorMessage; }
    public String getScreenshotBase64() { return screenshotBase64; }

    public void setExpected(String expected)                { this.expected = expected; }
    public void setActual(String actual)                    { this.actual = actual; }
    public void setErrorMessage(String errorMessage)        { this.errorMessage = errorMessage; }
    public void setScreenshotBase64(String screenshotBase64){ this.screenshotBase64 = screenshotBase64; }
}
