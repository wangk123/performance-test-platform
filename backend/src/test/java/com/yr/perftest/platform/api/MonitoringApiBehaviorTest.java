package com.yr.perftest.platform.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:monitoring-api-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.h2.console.enabled=false",
        "platform.monitoring.prometheus.file-sd-path=build/test-monitoring/jmx-targets.json"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class MonitoringApiBehaviorTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void managesServerMonitorTargetsAndWritesExporterDiscovery() throws Exception {
        mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User", "admin")
                        .content("{\"code\":\"loan-core\",\"name\":\"信贷核心压测\",\"description\":\"授信链路\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/projects/1/monitor-targets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"订单服务器",
                                  "host":"127.0.0.1",
                                  "port":9100,
                                  "metricsPath":"/metrics",
                                  "env":"test",
                                  "labels":{"team":"loan"},
                                  "enabled":true,
                                  "items":[
                                    {
                                      "type":"JAVA_JMX_AGENT",
                                      "name":"订单服务 JVM",
                                      "serviceName":"order-service",
                                      "processKeyword":"order-service.jar",
                                      "port":9404,
                                      "metricsPath":"/metrics"
                                    },
                                    {
                                      "type":"MYSQL_EXPORTER",
                                      "name":"订单 MySQL",
                                      "instanceName":"order-db",
                                      "port":9104,
                                      "metricsPath":"/metrics"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type", is("SERVER")))
                .andExpect(jsonPath("$.enabled", is(true)))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].processKeyword", is("order-service.jar")));

        mockMvc.perform(get("/api/projects/1/monitor-targets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].address", is("127.0.0.1:9100")));

        String discovery = Files.readString(Path.of("build/test-monitoring/jmx-targets.json"));
        org.assertj.core.api.Assertions.assertThat(discovery)
                .contains("127.0.0.1:9100")
                .contains("127.0.0.1:9404")
                .contains("127.0.0.1:9104")
                .contains("\"project_id\" : \"1\"")
                .contains("\"target_kind\" : \"SERVER_RESOURCE\"")
                .contains("\"target_kind\" : \"JAVA_JMX_AGENT\"")
                .contains("\"process_keyword\" : \"order-service.jar\"")
                .contains("\"target_kind\" : \"MYSQL_EXPORTER\"");

        mockMvc.perform(put("/api/monitor-targets/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"订单服务器",
                                  "host":"127.0.0.1",
                                  "port":9200,
                                  "metricsPath":"/metrics",
                                  "env":"test",
                                  "labels":{"team":"loan"},
                                  "enabled":false,
                                  "items":[]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled", is(false)))
                .andExpect(jsonPath("$.address", is("127.0.0.1:9200")))
                .andExpect(jsonPath("$.items", hasSize(0)));

        mockMvc.perform(post("/api/monitor-targets/1/check"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastCheckStatus", is("FAILED")))
                .andExpect(jsonPath("$.lastCheckMessage", containsString("unavailable")));

        mockMvc.perform(delete("/api/monitor-targets/1"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/projects/1/monitor-targets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void preservesRemoteDeployFieldsOnPartialUpdate() throws Exception {
        mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User", "admin")
                        .content("{\"code\":\"loan-core\",\"name\":\"信贷核心压测\",\"description\":\"授信链路\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/projects/1/monitor-targets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"订单服务器",
                                  "host":"127.0.0.1",
                                  "sshUsername":"root",
                                  "sshPassword":"secret",
                                  "sshPort":2222,
                                  "pluginDir":"/opt/monitoring",
                                  "port":9100,
                                  "metricsPath":"/metrics",
                                  "env":"test",
                                  "enabled":true,
                                  "items":[]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sshUsername", is("root")))
                .andExpect(jsonPath("$.sshPort", is(2222)))
                .andExpect(jsonPath("$.pluginDir", is("/opt/monitoring")))
                .andExpect(jsonPath("$.sshPasswordConfigured", is(true)));

        mockMvc.perform(put("/api/monitor-targets/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"订单服务器-更新",
                                  "host":"127.0.0.1",
                                  "port":9100,
                                  "metricsPath":"/metrics",
                                  "env":"test",
                                  "enabled":true,
                                  "items":[]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("订单服务器-更新")))
                .andExpect(jsonPath("$.sshUsername", is("root")))
                .andExpect(jsonPath("$.sshPort", is(2222)))
                .andExpect(jsonPath("$.pluginDir", is("/opt/monitoring")))
                .andExpect(jsonPath("$.sshPasswordConfigured", is(true)));
    }
}
