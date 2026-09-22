package com.yr.perftest.platform.execution.failure;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class TraceIdHeaderExtractorTest {
    @Test
    void extractsFromSw8ThirdSegment() {
        var headers = "Content-Type: application/json\r\nsw8: 1-AMVnR2VudA==-e9f3a2b7c1d84f05-a1b2-3-0-1-\r\n";
        assertThat(TraceIdHeaderExtractor.extract(headers)).isEqualTo("e9f3a2b7c1d84f05");
    }

    @Test
    void extractsFromXTraceIdFallback() {
        var headers = "Content-Type: application/json\r\nX-Trace-Id: abc123\r\n";
        assertThat(TraceIdHeaderExtractor.extract(headers)).isEqualTo("abc123");
    }

    @Test
    void headerNameCaseInsensitive() {
        var sw8Upper = "Content-Type: application/json\r\nSW8: 1-x-e9f3a2b7c1d84f05-a-1-0-1-\r\n";
        assertThat(TraceIdHeaderExtractor.extract(sw8Upper)).isEqualTo("e9f3a2b7c1d84f05");

        var xTraceLower = "content-type: application/json\r\nx-trace-id: abc123\r\n";
        assertThat(TraceIdHeaderExtractor.extract(xTraceLower)).isEqualTo("abc123");
    }

    @Test
    void malformedSw8FallsBackThenNull() {
        var headers = "Content-Type: application/json\r\nsw8: 1-only\r\nServer: nginx\r\n";
        assertThat(TraceIdHeaderExtractor.extract(headers)).isNull();
        assertThatCode(() -> TraceIdHeaderExtractor.extract(headers)).doesNotThrowAnyException();
    }

    @Test
    void emptySegmentYieldsNull() {
        var headers = "sw8: 1--\r\n";
        assertThat(TraceIdHeaderExtractor.extract(headers)).isNull();
    }

    @Test
    void nullSafe() {
        assertThat(TraceIdHeaderExtractor.extract(null)).isNull();
    }

    @Test
    void multipleHeaderLinesTakesFirstNonEmpty() {
        var headers = """
                Content-Type: application/json
                sw8: 1-
                x-trace-id:
                sw8: 1-x-firstgood-a-1-0-1-
                x-trace-id: second
                """;
        assertThat(TraceIdHeaderExtractor.extract(headers)).isEqualTo("firstgood");
    }
}
