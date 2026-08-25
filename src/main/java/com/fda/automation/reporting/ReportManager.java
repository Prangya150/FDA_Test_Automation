package com.fda.automation.reporting;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Thread-safe collector of TestRecord objects for the current suite run. */
public final class ReportManager {
    private static final ReportManager INSTANCE = new ReportManager();

    private final List<TestRecord> records = new CopyOnWriteArrayList<>();
    private volatile long suiteStartMs = System.currentTimeMillis();
    private volatile long suiteEndMs;

    private ReportManager() {}

    public static ReportManager getInstance() { return INSTANCE; }

    public void suiteStarted()  { suiteStartMs = System.currentTimeMillis(); }
    public void suiteFinished() { suiteEndMs   = System.currentTimeMillis(); }

    public void          addRecord(TestRecord r) { records.add(r); }
    public List<TestRecord> getRecords()         { return records; }

    public long getSuiteStartMs() { return suiteStartMs; }
    public long getSuiteEndMs()   { return suiteEndMs > 0 ? suiteEndMs : System.currentTimeMillis(); }
}
