package com.yr.perftest.platform.execution.failure;

import com.yr.perftest.platform.execution.TaskSamplePage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

class FailureSampleStoreTraceIdUpgradeTest {
    @Test
    void legacyDbWithoutTraceIdColumnUpgradesOnInitialize(@TempDir Path dir) throws Exception {
        Path db = dir.resolve("failure-samples.db");
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db); Statement s = c.createStatement()) {
            s.execute("CREATE TABLE samples (id INTEGER PRIMARY KEY AUTOINCREMENT, external_id INTEGER NOT NULL, " +
                    "host TEXT NOT NULL, ts INTEGER NOT NULL, label TEXT NOT NULL, code TEXT NOT NULL, success INTEGER NOT NULL, " +
                    "elapsed INTEGER NOT NULL, message TEXT, thread_name TEXT, url TEXT, request_headers TEXT, " +
                    "request_body TEXT, response_headers TEXT, response_body TEXT, failure_message TEXT, " +
                    "UNIQUE(host, external_id))");
            s.execute("INSERT INTO samples(external_id, host, ts, label, code, success, elapsed) VALUES(1,'h1',1,'L','500',0,10)");
        }
        var store = new FailureSampleStore();
        store.initialize(db);
        Long newId = store.insertReturningId(db, new FailureSampleRecord(2L, 2L, "L", "500", false, 10L,
                "", "", "h1", "", "", "", "sw8: 1-x-e9f3a2b7c1d84f05-a-1-0-1-", "", "", ""));
        assertThat(newId).isNotNull();
        assertThat(store.querySummaries(db, new FailureSampleQuery(null, null, null), 1, 10).samples()
                .stream().map(s -> s.traceId())).containsExactly("e9f3a2b7c1d84f05", null);
    }

    @Test
    void detailAndDetailsAfterCarryTraceId(@TempDir Path dir) throws Exception {
        Path db = dir.resolve("failure-samples.db");
        var store = new FailureSampleStore();
        store.initialize(db);
        Long newId = store.insertReturningId(db, new FailureSampleRecord(1L, 1L, "L", "500", false, 10L,
                "", "", "h1", "", "", "", "X-Trace-Id: abc123\r\n", "", "", ""));

        assertThat(store.findDetail(db, newId).orElseThrow().traceId()).isEqualTo("abc123");
        assertThat(store.listDetailsAfter(db, 0L, 10).get(0).traceId()).isEqualTo("abc123");
    }
}
