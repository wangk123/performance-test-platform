package com.yr.perftest.platform.analysis;

import com.yr.perftest.platform.execution.TaskExecutionResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorClusteringTest {
    private static TaskExecutionResult.Sample sample(int id, String label, String statusCode, boolean success, String failureMessage) {
        return new TaskExecutionResult.Sample(
                id,
                "2026-07-29T01:00:00Z",
                statusCode,
                success,
                label,
                100,
                null,
                "thread-1",
                null,
                null,
                null,
                null,
                null,
                failureMessage
        );
    }

    private static List<TaskExecutionResult.Sample> goldenSamples() {
        return List.of(
                sample(1, "login", "500", false, "timeout after 3012 ms"),
                sample(2, "login", "500", false, "timeout after 998 ms"),
                sample(3, "search", "502", false, "bad gateway"),
                sample(4, "login", "200", true, null)
        );
    }

    @Test
    void goldenDatasetClustersByLabelCodeAndNormalizedMessage() {
        AnalysisFact first = new ErrorClustering().analyze(goldenSamples(), List.of("failure-samples#1-3"));
        AnalysisFact second = new ErrorClustering().analyze(goldenSamples(), List.of("failure-samples#1-3"));

        assertThat(first).isEqualTo(second);
        assertThat(first.algorithmId()).isEqualTo("error-cluster");
        assertThat(first.algorithmVersion()).isEqualTo("1");
        assertThat(first.data()).containsEntry("totalFailures", 3);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> clusters = (List<Map<String, Object>>) first.data().get("clusters");
        assertThat(clusters).hasSize(2);
        assertThat(clusters.get(0))
                .containsEntry("label", "login")
                .containsEntry("statusCode", "500")
                .containsEntry("messagePattern", "timeout after # ms")
                .containsEntry("count", 2)
                .containsEntry("sharePct", 66.6667);
        assertThat(clusters.get(1))
                .containsEntry("label", "search")
                .containsEntry("statusCode", "502")
                .containsEntry("messagePattern", "bad gateway")
                .containsEntry("count", 1)
                .containsEntry("sharePct", 33.3333);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> contribution = (List<Map<String, Object>>) first.data().get("labelContribution");
        assertThat(contribution).hasSize(2);
        assertThat(contribution.get(0))
                .containsEntry("label", "login")
                .containsEntry("count", 2)
                .containsEntry("sharePct", 66.6667);
        assertThat(contribution.get(1))
                .containsEntry("label", "search")
                .containsEntry("count", 1)
                .containsEntry("sharePct", 33.3333);
    }

    @Test
    void fallsBackToMessageWhenFailureMessageIsBlank() {
        TaskExecutionResult.Sample sample = new TaskExecutionResult.Sample(
                1, "t", "500", false, "login", 100, "socket 8080 reset", "thread-1",
                null, null, null, null, null, " ");

        AnalysisFact fact = new ErrorClustering().analyze(List.of(sample), List.of());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> clusters = (List<Map<String, Object>>) fact.data().get("clusters");
        assertThat(clusters).hasSize(1);
        assertThat(clusters.get(0)).containsEntry("messagePattern", "socket # reset");
    }

    @Test
    void multilineFailureMessageIsNormalizedToSingleLine() {
        TaskExecutionResult.Sample sample = new TaskExecutionResult.Sample(
                1, "t", "500", false, "login", 100, null, "thread-1",
                null, null, null, null, null, "timeout after 100 ms\n\tat com.example.Foo.bar(Foo.java:42)");

        AnalysisFact fact = new ErrorClustering().analyze(List.of(sample), List.of());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> clusters = (List<Map<String, Object>>) fact.data().get("clusters");
        assertThat(clusters).hasSize(1);
        assertThat(clusters.get(0)).containsEntry(
                "messagePattern", "timeout after # ms at com.example.Foo.bar(Foo.java:#)");
    }

    @Test
    void emptyInputProducesEmptyClusters() {
        AnalysisFact fact = new ErrorClustering().analyze(List.of(), List.of());

        assertThat(fact.data()).containsEntry("totalFailures", 0);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> clusters = (List<Map<String, Object>>) fact.data().get("clusters");
        assertThat(clusters).isEmpty();
    }
}
