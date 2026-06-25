package com.yr.perftest.platform.execution;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class JmeterResultParserTest {
    @Test
    void parsesRequestAndResponseBodiesFromFailureJtl(@TempDir Path tempDir) throws Exception {
        Path jtl = tempDir.resolve("failure-result.jtl");
        Files.writeString(jtl, """
                timeStamp,elapsed,label,responseCode,responseMessage,threadName,success,failureMessage,URL,samplerData,requestHeaders,responseData,responseHeaders
                1719300000000,1,测试数据：池伟通,401,Unauthorized,线程组 1-1,false,No results for path: $['responsetype'],http://example.com/api,"POST http://example.com/api\\n\\nPOST data:\\n{""name"":""demo""}","Content-Type: application/json","{""error"":""unauthorized""}","HTTP/1.1 401\\nContent-Type: application/json"
                """);

        TaskSamplePage page = new JmeterResultParser().parseSamplePage(jtl, 1, 10);

        assertTrue(page.samples().size() == 1);
        TaskExecutionResult.Sample sample = page.samples().get(0);
        assertTrue(sample.request().contains("POST http://example.com/api"));
        assertTrue(sample.requestBody().contains("\"name\":\"demo\""));
        assertTrue(sample.requestHeaders().contains("Content-Type: application/json"));
        assertTrue(sample.response().contains("HTTP 401 Unauthorized"));
        assertTrue(sample.responseBody().contains("\"error\":\"unauthorized\""));
        assertTrue(sample.failureMessage().contains("responsetype"));
    }
}
