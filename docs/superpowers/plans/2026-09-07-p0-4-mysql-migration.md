# P0-4 主库 MySQL 迁移与 schema 版本化 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** MySQL（192.168.17.216 Docker 部署）成为运行时唯一数据源；Flyway V1 双方言基线（H2 测试 + MySQL 运行时）+ `ddl-auto: validate`；一次性 H2→MySQL 表驱动迁移工具；Testcontainers `testMysql` 任务经 SSH 隧道驱动远程 Docker。

**Architecture:** 依赖与 compose 先行（T1）→ 远程 MySQL 上以 `ddl-auto: update` 一次性启动生成全量表、`mysqldump --no-data` 导出并清洗为 `V1__baseline.sql`（T2）→ 主配置切 MySQL+Flyway+validate、55 个测试类 `create-drop→validate` 清洗、新增 test resources 兜 @DataJpaTest（T3，H2 验证网在全量测试中生效）→ 删 MonitoringSchemaInitializer 与 mysql-schema.sql（T4）→ `datamigration` 包迁移工具 + H2→H2 单测（T5）→ `testMysql` task + 2 个 Testcontainers 用例（V1 真库验证 / 迁移工具 E2E）经远程 Docker 跑绿（T6）→ README 部署章节重写与文档收尾（T7）。

**Tech Stack:** Spring Boot 3.5.14 / Java 17 / Gradle；Flyway 10（Boot BOM）；Testcontainers（Boot BOM）；远程 Docker 29（Ubuntu 22.04 @192.168.17.216，经 SSH unix-socket 隧道）。

**Spec:** `docs/superpowers/specs/2026-09-07-p0-4-mysql-migration-design.md`（本计划一切取舍以 spec 为准）

## Global Constraints

- **服务器操作授权与边界**：用户已明确"测试与迁移工作全部在远程服务器完成"。允许：SSH 到 216、compose 起 MySQL 容器、`docker exec`/`docker pull`。**禁止**：触碰 216 上既有 3 个监控容器（perftest-prometheus/grafana/influxdb）、修改 Docker daemon 配置、操作 217/218。
- **远程 Docker 访问方式（定死）**：SSH unix-socket 隧道 `sshpass -p 'Yr@09876' ssh -nNT -L /tmp/p024-docker.sock:/var/run/docker.sock root@192.168.17.216`，Testcontainers 经 `DOCKER_HOST=unix:///tmp/p024-docker.sock` + `TESTCONTAINERS_HOST_OVERRIDE=192.168.17.216` 驱动。容器映射端口绑定在 216 的 0.0.0.0，本机网络可达（已验证）。**不做** daemon.json 修改、不开 2375。
- **M1**：引入 flyway-core + flyway-mysql，V1 全量基线，`ddl-auto` 全局 `validate`；删除 `docs/database/mysql-schema.sql`；`MonitoringSchemaInitializer` 收编删除。
- **M3**：`application.yml` 默认 MySQL（192.168.17.216:3306/perftest，账号 perftest）；无 local-h2 profile、无 H2 console；不考虑断网兜底。`SPRING_DATASOURCE_URL` 等环境变量可覆盖（README 写明）。
- **M4/M5**：H2 依赖保留 runtimeOnly（迁移工具要读旧库，不挪 test scope）；现有测试跑 H2 内存库；新增 Testcontainers 用例 `@Tag("mysql")`，默认 `test` 不含，`testMysql` 专属。
- **M6 双方言**：V1 不写 `DEFAULT CHARSET=`/`ENGINE=` 等 MySQL 专有子句进表 DDL（utf8mb4 由 compose server 级保证）；AUTO_INCREMENT 列属性两者通用。
- **依赖变化仅限**：`flyway-core`、`flyway-mysql`（implementation）、`org.testcontainers:mysql`（test）。`com.mysql:mysql-connector-j` 已存在（runtimeOnly）不重复加。
- Gradle 一律 `JAVA_HOME=/Users/wangk/Documents/config/jdk-17.0.17+10/Contents/Home/`（AGENTS.md）。
- 提交信息风格：`feat：P0-4 …——细节`（全角冒号+破折号）。
- 执行明细留文件不迁库（D14/M2）：`execution/failure/` 全部代码、`storage/` 结构不动。
- 本地无 `./storage/perftest` H2 存量文件——真实数据迁移验收项记 N/A，迁移工具以 H2→H2 单测 + Testcontainers E2E 覆盖语义。

## 现状锚点（实施者必读）

- `backend/build.gradle`：Boot 3.5.14；`runtimeOnly 'com.h2database:h2'`、`runtimeOnly 'com.mysql:mysql-connector-j'` 已在；`test` task 已有 `useJUnitPlatform()` + `maxHeapSize = '2g'`。
- `backend/src/main/resources/application.yml:4-14`：H2 file url（`jdbc:h2:file:./storage/perftest;MODE=MySQL;DATABASE_TO_LOWER=TRUE;AUTO_SERVER=TRUE`）+ `ddl-auto: update` + `h2.console.enabled: true`——T3 改写处。
- 测试面：55 个 `@SpringBootTest` **全部**内联 `spring.datasource.url`（H2 mem MODE=MySQL）+ `ddl-auto=create-drop`（各 55 处，无遗漏类）；12 个 `@DataJpaTest` 纯切片无内联 properties（默认 embedded H2 替换 + create-drop）；无 `src/test/resources` 目录。
- `MonitoringSchemaInitializer`（46 行）：ApplicationRunner 裸 DDL 给 monitor_target 补列（H2/MySQL 双分支）——V1 由实体生成天然包含其结果，直接删。
- `docs/database/mysql-schema.sql`（565 行，已落后实体）：删除。引用处：`README.md:197,322`（T7 重写）、`docs/requirements-spec.md` 与 `docs/architecture-and-roadmap.md`（历史提及，T7 清理措辞）、`openspec/changes/archive/...`（历史归档**不动**）。
- 216 服务器：Ubuntu 22.04、Docker 29.1.3（API 1.52）、6.5G 磁盘可用、监控栈 3 容器运行中、无 JDK（因此 Gradle 永远在本地跑）。
- 表数量口径：50 个 JPA 实体 ≈ 50 张表（V1 dump 后以实际数为准，含 flyway_schema_history 前共 N 张）。

---

### Task 1: 依赖 + docker-compose + 216 起 MySQL

**Files:**
- Modify: `backend/build.gradle`（dependencies 块）
- Create: `deploy/mysql/docker-compose.yml`

**Interfaces:**
- Produces: V1 生成与运行时共用的 MySQL 实例（216:3306/perftest，root/perftest-root，应用账号 perftest/perftest）；`flyway-core`/`flyway-mysql`/`testcontainers:mysql` 依赖就位。

- [ ] **Step 1: build.gradle 加依赖**（Boot BOM 管版本，无显式版本号）

在 `dependencies {` 块 `runtimeOnly 'com.mysql:mysql-connector-j'` 行后加：

```groovy
    implementation 'org.flywaydb:flyway-core'
    implementation 'org.flywaydb:flyway-mysql'
    testImplementation 'org.testcontainers:mysql'
```

（`org.testcontainers:mysql` 传递依赖 junit-jupiter testcontainers；Boot 3.5 BOM 管理两者版本。）

- [ ] **Step 2: 写 deploy/mysql/docker-compose.yml**（spec §6）

```yaml
services:
  mysql:
    image: mysql:8.0
    container_name: perftest-mysql
    restart: unless-stopped
    ports:
      - "3306:3306"
    environment:
      MYSQL_ROOT_PASSWORD: perftest-root
      MYSQL_DATABASE: perftest
      MYSQL_USER: perftest
      MYSQL_PASSWORD: perftest
    command: --character-set-server=utf8mb4 --collation-server=utf8mb4_unicode_ci
    volumes:
      - mysql-data:/var/lib/mysql
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost", "-uroot", "-pperftest-root"]
      interval: 10s
      timeout: 5s
      retries: 10
      start_period: 30s

volumes:
  mysql-data:
```

（内网测试默认口令写 compose，spec §6 明示允许；正式化再改。）

- [ ] **Step 3: 216 上起 MySQL**

```bash
# 预拉镜像（避免 compose 超时）
sshpass -p 'Yr@09876' ssh root@192.168.17.216 'docker pull mysql:8.0'
# 传 compose 文件并启动（放 /app/perftest/ 与监控栈组织方式一致）
sshpass -p 'Yr@09876' ssh root@192.168.17.216 'mkdir -p /app/perftest'
sshpass -p 'Yr@09876' scp deploy/mysql/docker-compose.yml root@192.168.17.216:/app/perftest/docker-compose.yml
sshpass -p 'Yr@09876' ssh root@192.168.17.216 'cd /app/perftest && docker compose up -d'
```

- [ ] **Step 4: 验证就绪**

```bash
sshpass -p 'Yr@09876' ssh root@192.168.17.216 'docker ps --filter name=perftest-mysql --format "{{.Status}}"'
# 期望 healthy；然后本机验证连通+字符集：
# mysql 客户端本机可能没有——用容器内验证：
sshpass -p 'Yr@09876' ssh root@192.168.17.216 'docker exec perftest-mysql mysql -uroot -pperftest-root -e "show variables like \"character_set_server\"; show databases;"' | grep -E "utf8mb4|perftest"
# 监控栈 3 容器仍 Up：
sshpass -p 'Yr@09876' ssh root@192.168.17.216 'docker ps --format "{{.Names}}" | sort'
```

Expected: `character_set_server utf8mb4`、databases 含 `perftest`、容器列表含原有 3 个监控容器 + perftest-mysql。

- [ ] **Step 5: 提交**

```bash
git add backend/build.gradle deploy/mysql/docker-compose.yml
git commit -m "feat：P0-4 依赖与 MySQL 部署件——flyway-core/flyway-mysql/testcontainers-mysql 引入（Boot BOM 版本），deploy/mysql compose（utf8mb4 server 级、perftest 应用账号、healthcheck）落 216 运行"
```

---

### Task 2: V1 基线生成（远程 MySQL + 本地启动）

**Files:**
- Create: `backend/src/main/resources/db/migration/V1__baseline.sql`（生成产物，经清洗）

**Interfaces:**
- Produces: 双方言 V1 基线（后续 T3/T5/T6 全部依赖）；216 库在生成后重置为空库（最终由 Flyway 管理）。

- [ ] **Step 1: 打包并远程建表（ddl-auto=update 一次性启动）**

```bash
JAVA_HOME=/Users/wangk/Documents/config/jdk-17.0.17+10/Contents/Home/ ./gradlew :backend:bootJar
SPRING_DATASOURCE_URL='jdbc:mysql://192.168.17.216:3306/perftest?characterEncoding=utf8mb4&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai' \
SPRING_DATASOURCE_USERNAME=root SPRING_DATASOURCE_PASSWORD=perftest-root \
SPRING_JPA_HIBERNATE_DDL_AUTO=update SPRING_H2_CONSOLE_ENABLED=false \
JAVA_HOME=/Users/wangk/Documents/config/jdk-17.0.17+10/Contents/Home/ \
java -jar backend/build/libs/backend-0.1.0-SNAPSHOT.jar > /tmp/p024-bootgen.log 2>&1 &
echo $! > /tmp/p024-bootgen.pid
# 轮询至 Started（最多 120s），随后 kill
for i in $(seq 1 60); do grep -q "Started PerformanceTestPlatformApplication\|APPLICATION FAILED" /tmp/p024-bootgen.log && break; sleep 2; done
grep -c "Started" /tmp/p024-bootgen.log && kill $(cat /tmp/p024-bootgen.pid)
# 断言表数：
sshpass -p 'Yr@09876' ssh root@192.168.17.216 'docker exec perftest-mysql mysql -uroot -pperftest-root -N -e "select count(*) from information_schema.tables where table_schema=\"perftest\";"'
```

Expected: 表数 ≈50（记录实际值 N，写入报告）。

注意：若应用启动因非数据源原因失败（如依赖 storage 目录），按日志最小修复（如 `mkdir -p storage`）重试；**不得**修改任何提交的配置来迁就——本任务用环境变量覆盖，仓库仍是 H2 配置。

- [ ] **Step 2: mysqldump 导出 + 清洗**

```bash
sshpass -p 'Yr@09876' ssh root@192.168.17.216 'docker exec perftest-mysql sh -c "mysqldump -uroot -pperftest-root --no-data --skip-dump-date --compact --routines=false --triggers=false perftest"' > /tmp/p024-raw.sql 2>/dev/null
grep -c "CREATE TABLE" /tmp/p024-raw.sql   # 期望 = N
```

清洗规则（sed 脚本，逐条可核）：

```bash
sed -E \
  -e '/^\/\*![0-9]+ SET/d' -e '/^SET /d' \
  -e 's/\) ENGINE=InnoDB [^;]*;/);/' \
  -e '/^LOCK TABLES/d' -e '/^UNLOCK TABLES/d' \
  /tmp/p024-raw.sql > /tmp/p024-clean.sql
grep -c "CREATE TABLE" /tmp/p024-clean.sql   # 仍 = N
grep -ci "ENGINE=\|CHARSET" /tmp/p024-clean.sql   # 期望 0
```

要点：`--compact` 已去 /*!40101 SET*/ 大部分；上面再兜底。列级 `AUTO_INCREMENT`、`datetime(6)`、`longtext`、`tinyint(1)`、`bit(1)` 保留（双方言通用性由 T3 的 H2 验证网实测裁决）。外键 CONSTRAINT 行保留（mysqldump 已按依赖排序）。文件头加注释行 `-- P0-4 V1 baseline: generated from Hibernate ddl-auto=update on MySQL 8.0 via deploy/mysql compose; dual-dialect (H2 MODE=MySQL / MySQL)。禁止手改，变更走 V2+。`

- [ ] **Step 3: 落盘 V1 + 216 库重置**

```bash
mkdir -p backend/src/main/resources/db/migration
cp /tmp/p024-clean.sql backend/src/main/resources/db/migration/V1__baseline.sql
# 重置 216 库（生成库作废，最终态由 Flyway 从零建）：
sshpass -p 'Yr@09876' ssh root@192.168.17.216 'docker exec perftest-mysql mysql -uroot -pperftest-root -e "drop database perftest; create database perftest; grant all privileges on perftest.* to \"perftest\"@\"%\";"'
```

- [ ] **Step 4: 提交（此时尚未接入运行链路，测试仍走旧配置全绿）**

```bash
JAVA_HOME=... ./gradlew :backend:test   # 期望全绿（V1 未生效，防回归确认）
git add backend/src/main/resources/db/migration/V1__baseline.sql
git commit -m "feat：P0-4 V1 双方言基线——216 MySQL 以 ddl-auto=update 生成全量 N 表、mysqldump --no-data 导出并清洗（去 ENGINE/CHARSET/会话语句），库已重置待 Flyway 接管"
```

---

### Task 3: 配置切换 + 测试清洗（H2 验证网接入）

**Files:**
- Modify: `backend/src/main/resources/application.yml:4-14`
- Modify: 55 个测试类的 `ddl-auto=create-drop` → `validate`（sed sweep）
- Create: `backend/src/test/resources/application.yml`（@DataJpaTest 兜底）

**Interfaces:**
- Produces: 运行时默认 MySQL+Flyway+validate；测试 H2 内存库+Flyway V1+validate；全量 `:backend:test` 绿 = V1 的 H2 方言可执行性验证网。

- [ ] **Step 1: 主配置切换**（改写 datasource/jpa 段，删 h2.console）

```yaml
spring:
  application:
    name: performance-test-platform
  datasource:
    url: jdbc:mysql://192.168.17.216:3306/perftest?characterEncoding=utf8mb4&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai
    username: perftest
    password: perftest
  flyway:
    enabled: true
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
```

（H2 url、`ddl-auto: update`、`h2.console` 三处删除；其余段原样。）

- [ ] **Step 2: 测试类 sweep**

```bash
cd backend/src/test/java
grep -rl 'ddl-auto=create-drop' . | while read f; do sed -i '' 's/ddl-auto=create-drop/ddl-auto=validate/' "$f"; done
grep -rc 'ddl-auto=validate' . | grep -c ':1'   # 期望 55
```

- [ ] **Step 3: 新增 backend/src/test/resources/application.yml**（兜 12 个 @DataJpaTest：embedded 替换不走（url 已是 embedded H2），Flyway 切片生效，ddl-auto 覆盖默认 create-drop）

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:datatest;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1
    username: sa
    password:
  jpa:
    hibernate:
      ddl-auto: validate
```

（55 个 @SpringBootTest 内联 url 优先级高于本文件，不受影响。@DataJpaTest 事务回滚保证跨类共享库无脏数据；Flyway 首跑 V1 后后续上下文跳过。）

- [ ] **Step 4: 全量测试 = H2 验证网首跑**

```bash
JAVA_HOME=/Users/wangk/Documents/config/jdk-17.0.17+10/Contents/Home/ ./gradlew :backend:test
```

Expected: 全绿。**若红**：多为 V1 在 H2 MODE=MySQL 的方言残渣（如某列类型不识别、FK 顺序）——回 T2 修清洗规则重生成 V1，不改测试语义、不给实体加配置迁就。逐条修复记录进报告。

- [ ] **Step 5: 提交**

```bash
git add -A backend/src
git commit -m "feat：P0-4 配置切换与测试清洗——application.yml 默认连 216 MySQL+Flyway+validate（删 H2 console/url），55 测试类 create-drop→validate 清洗，新增 test resources 兜 @DataJpaTest 走 V1+validate，全量测试即 H2 方言验证网"
```

---

### Task 4: 删 MonitoringSchemaInitializer 与 mysql-schema.sql

**Files:**
- Delete: `backend/src/main/java/com/yr/perftest/platform/monitoring/MonitoringSchemaInitializer.java`
- Delete: `docs/database/mysql-schema.sql`（若 docs/database/ 目录因此空则一并删）
- Test: 既有全量测试（Initializer 无专测；monitoring 域有其它测试覆盖）

- [ ] **Step 1: 删两文件**

```bash
git rm backend/src/main/java/com/yr/perftest/platform/monitoring/MonitoringSchemaInitializer.java docs/database/mysql-schema.sql
```

- [ ] **Step 2: 全仓残留引用核对（代码零残留；文档引用留 T7 处理 README，requirements/roadmap 措辞一并清）**

```bash
grep -rn "MonitoringSchemaInitializer\|mysql-schema.sql" --include="*.java" --include="*.gradle" --include="*.yml" backend/ && echo FOUND || echo CLEAN
```

Expected: CLEAN（openspec 历史归档与 .md 文档由 T7 处理/保留）。

- [ ] **Step 3: 全量回归 + 提交**

```bash
JAVA_HOME=... ./gradlew :backend:test   # 期望全绿
git add -A && git commit -m "feat：P0-4 收编删除 MonitoringSchemaInitializer 与废弃 docs/database/mysql-schema.sql——补列结果已含于 V1 基线，手工脚本时代终结（M1）"
```

---

### Task 5: H2→MySQL 数据迁移工具（datamigration 包）

**Files:**
- Create: `backend/src/main/java/com/yr/perftest/platform/datamigration/DataMigrationProperties.java`
- Create: `backend/src/main/java/com/yr/perftest/platform/datamigration/DataMigrationRunner.java`
- Test: `backend/src/test/java/com/yr/perftest/platform/datamigration/DataMigrationRunnerTest.java`

**Interfaces:**
- Consumes: V1 基线（表清单从 V1 的 CREATE TABLE 顺序固化）；注入 DataSource（目标库，Flyway 已建表——ApplicationRunner 在 Flyway 之后天然满足）。
- Produces:
  - `@ConditionalOnProperty("app.data-migration.h2-source")` 装配；`DataMigrationRunner implements ApplicationRunner`
  - `DataMigrationProperties(String h2Source, boolean overwrite)`（`app.data-migration.*`）
  - 核心方法 `MigrationSummary migrate()`（也供测试直调）：`record MigrationSummary(int tablesCopied, long rowsCopied, List<String> skippedEmpty)`；失败抛 `IllegalStateException`（消息含表名+行号上下文）
  - 静态有序表清单 `MIGRATION_TABLES`（从 V1 固化，逻辑父表在前）

- [ ] **Step 1: 写失败测试**（H2→H2，目标=测试上下文库〔Flyway V1 已建〕，源=独立 H2 mem 库用 ResourceDatabasePopulator 跑同一 V1 再造数）

```java
package com.yr.perftest.platform.datamigration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.annotation.DirtiesContext;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:data-migration-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.h2.console.enabled=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class DataMigrationRunnerTest {

    @Autowired
    private DataSource targetDataSource;
    @Autowired
    private DataMigrationRunner runner;

    /** 造一个带数据的独立 H2 源库（跑同一 V1 建表）。 */
    private String seedSourceDb() throws Exception {
        String url = "jdbc:h2:mem:migsrc" + System.nanoTime() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE";
        try (Connection c = DriverManager.getConnection(url, "sa", "")) {
            new ResourceDatabasePopulator(
                    new ClassPathResource("db/migration/V1__baseline.sql")).execute(c);
            // projects 父表 + users：显式主键 + 时间戳 + 长文本列（projects.remark）
            try (Statement s = c.createStatement()) {
                s.execute("insert into projects (id, name, description, created_by, created_at, updated_at) "
                        + "values (7, '迁移项目', repeat('x', 10000), 'admin', now(), now())");
                s.execute("insert into users (id, username, password_hash, display_name, enabled, created_at, updated_at) "
                        + "values (9, 'migrated-user', 'h', '迁移用户', true, now(), now())");
            }
        }
        return url;
    }

    @Test
    void copiesRowsWithExplicitPrimaryKeysIntoFlywaySchema() throws Exception {
        String sourceUrl = seedSourceDb();
        DataMigrationRunner.MigrationSummary summary = runner.migrate(sourceUrl, false);
        assertThat(summary.rowsCopied()).isGreaterThanOrEqualTo(2);
        try (Connection c = targetDataSource.getConnection(); Statement s = c.createStatement()) {
            ResultSet rs = s.executeQuery("select count(*) from projects where id = 7");
            rs.next();
            assertThat(rs.getInt(1)).isEqualTo(1);
            ResultSet rs2 = s.executeQuery("select length(description) from projects where id = 7");
            rs2.next();
            assertThat(rs2.getInt(1)).isEqualTo(10000);
        }
    }

    @Test
    void refusesWhenTargetTableNotEmpty() throws Exception {
        String sourceUrl = seedSourceDb();
        runner.migrate(sourceUrl, false);
        assertThatThrownBy(() -> runner.migrate(seedSourceDb(), false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("非空");
    }

    @Test
    void overwriteClearsThenCopies() throws Exception {
        runner.migrate(seedSourceDb(), false);
        DataMigrationRunner.MigrationSummary second = runner.migrate(seedSourceDb(), true);
        assertThat(second.rowsCopied()).isGreaterThanOrEqualTo(2);
    }
}
```

（注：测试用列名以 V1 实际列为准——`projects`/`users` 若列名不同，按 V1 调整 insert 语句，语义不变：显式主键、长文本、时间戳三要素必须有。）

- [ ] **Step 2: 跑测试确认失败**（类不存在编译失败即 RED）

- [ ] **Step 3: 实现**（要点定死，细节按仓库风格）

```java
package com.yr.perftest.platform.datamigration;

/** 一次性迁移开关；h2Source = H2 文件库路径（如 ./storage/perftest）。 */
record DataMigrationProperties(String h2Source, boolean overwrite) {
}
```

```java
package com.yr.perftest.platform.datamigration;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
// ...

/**
 * H2→主库表驱动一次性拷贝（spec §5）：固定有序表清单、按原主键显式插入、
 * 批 500 提交、目标非空拒跑（overwrite 显式覆盖）、旧库只读。无 ORM 参与。
 */
@Configuration
@ConditionalOnProperty("app.data-migration.h2-source")
public class DataMigrationRunner {

    /** 有序表清单：逻辑父表在前（生成顺序 = V1 依赖序）。 */
    static final List<String> MIGRATION_TABLES = List.of(/* 从 V1 固化 ~50 张 */);

    public record MigrationSummary(int tablesCopied, long rowsCopied) {
    }

    // ApplicationRunner：Flyway 已在 bean 初始化阶段完成建表，runner 在其后执行（spec §5 时序）
    // migrate(String sourceUrl, boolean overwrite)：
    //   1) DriverManager 连源（H2 文件路径拼 jdbc:h2:file:{h2Source};MODE=MySQL;DATABASE_TO_LOWER=TRUE;IFEXISTS=TRUE;ACCESS_MODE_DATA=r）
    //   2) 每表：目标 count>0 && !overwrite → IllegalStateException("目标表 X 非空…")；overwrite → DELETE FROM X
    //   3) 源 SELECT *，按两库列交集 INSERT（单表一事务，批 500，异常带表名+行号上下文）
    //   4) 返回汇总
}
```

（表清单固化：Task 2 已得 N 张表名；实现时以 V1 文件 CREATE TABLE 顺序为清单顺序，`flyway_schema_history` 排除。）

- [ ] **Step 4: 全量回归 + 提交**

```bash
JAVA_HOME=... ./gradlew :backend:test   # 全绿（ConditionalOnProperty 未触发时零影响）
git add backend/src/main/java/com/yr/perftest/platform/datamigration backend/src/test/java/com/yr/perftest/platform/datamigration
git commit -m "feat：P0-4 H2→MySQL 一次性迁移工具——app.data-migration.h2-source 属性开关、有序表清单按原主键分批拷贝、目标非空拒跑/显式覆盖、旧库只读，H2→H2 单测覆盖显式主键/长文本/时间戳往返"
```

---

### Task 6: testMysql task + Testcontainers 双用例（远程 Docker）

**Files:**
- Modify: `backend/build.gradle`（新增 testMysql task）
- Create: `backend/src/test/java/com/yr/perftest/platform/datamigration/MysqlV1SchemaIT.java`（`@Tag("mysql")`）
- Create: `backend/src/test/java/com/yr/perftest/platform/datamigration/DataMigrationMysqlIT.java`（`@Tag("mysql")`）

**Interfaces:**
- Consumes: T5 `DataMigrationRunner.migrate(String, boolean)`；T1 隧道 + 环境变量。
- Produces: `./gradlew :backend:testMysql`（仅 `@Tag("mysql")`，复用 test 源集，2g 堆）；远程 Docker 执行方式文档化（README T7 引用）。

- [ ] **Step 1: build.gradle 加 task**

```groovy
tasks.register('testMysql', Test) {
    description = 'Testcontainers 真 MySQL 集成测试（需 Docker，经 DOCKER_HOST 可指向远程）'
    testClassesDirs = sourceSets.test.output.classesDirs
    classpath = sourceSets.test.runtimeClasspath
    useJUnitPlatform {
        includeTags 'mysql'
    }
    maxHeapSize = '2g'
    shouldRunAfter tasks.named('test')
}
```

- [ ] **Step 2: 用例 1 —— V1 真库验证 + 冒烟读写**

```java
package com.yr.perftest.platform.datamigration;

import com.yr.perftest.platform.project.PersistentProjectRecord;
import com.yr.perftest.platform.project.PersistentProjectRepository;
import com.yr.perftest.platform.task.PersistentTaskPlanRecord;
import com.yr.perftest.platform.task.PersistentTaskPlanRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistrar; // 若 Boot 3.5 API 为 ServiceConnection 则不需要
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("mysql")
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {"spring.jpa.hibernate.ddl-auto=validate"})
class MysqlV1SchemaIT {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci");

    @Autowired
    private PersistentProjectRepository projectRepository;
    @Autowired
    private PersistentTaskPlanRepository planRepository;

    @Test
    void contextBootsViaFlywayV1AndRoundTripsLobAndTimestamps() {
        // 上下文就绪本身 = V1 在真 MySQL 执行成功 + validate 通过（spec §7 用例 1）
        PersistentProjectRecord project = projectRepository.save(
                new PersistentProjectRecord("P0-4", "真库冒烟", "", "admin"));
        PersistentTaskPlanRecord plan = planRepository.save(
                new PersistentTaskPlanRecord(project.getId(), "长文本计划", null, "admin"));
        plan.updateBody("正文".repeat(20000)); // @Lob → LONGTEXT 往返
        planRepository.save(plan);
        PersistentTaskPlanRecord reloaded = planRepository.findById(plan.getId()).orElseThrow();
        assertThat(reloaded.getBody().length()).isEqualTo(40000);
        assertThat(reloaded.getCreatedAt()).isNotNull(); // datetime(6) 往返
    }
}
```

（`@ServiceConnection` 覆盖主配置的 216 地址指向容器——本用例验证的是"V1+实体在干净真 MySQL 上成立"，与指向哪台无关。）

- [ ] **Step 3: 用例 2 —— 迁移工具 E2E**

```java
package com.yr.perftest.platform.datamigration;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("mysql")
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {"spring.jpa.hibernate.ddl-auto=validate"})
class DataMigrationMysqlIT {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci");

    @Autowired
    private DataMigrationRunner runner;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void migratesExplicitKeysAndAutoIncrementContinues() throws Exception {
        String sourceUrl = DataMigrationRunnerTestSeeder.seedPopulatedH2Source(); // 与 T5 同款造数逻辑抽出的测试夹具
        DataMigrationRunner.MigrationSummary summary = runner.migrate(sourceUrl, false);
        assertThat(summary.rowsCopied()).isGreaterThanOrEqualTo(2);
        Integer copied = jdbcTemplate.queryForObject("select count(*) from projects where id = 7", Integer.class);
        assertThat(copied).isEqualTo(1);
        // 迁移后再插入不撞键（MySQL 显式插主键后自增计数器自动顶上，spec §7 用例 2）
        jdbcTemplate.update("insert into projects (name, description, created_by, created_at, updated_at) "
                + "values ('新增', '', 'admin', now(6), now(6))");
        Integer newId = jdbcTemplate.queryForObject("select max(id) from projects", Integer.class);
        assertThat(newId).isGreaterThan(7);
    }
}
```

（`DataMigrationRunnerTestSeeder` 为两 IT/单测共用的静态夹具：造 H2 源库（跑 V1 + 显式主键 7/9 + 长文本 + 时间戳）。）

- [ ] **Step 4: 经隧道跑 testMysql**

```bash
# 隧道（若无则建；有则复用 /tmp/p024-docker.sock）
[ -S /tmp/p024-docker.sock ] || (sshpass -p 'Yr@09876' ssh -nNT -L /tmp/p024-docker.sock:/var/run/docker.sock -fN root@192.168.17.216)
export DOCKER_HOST=unix:///tmp/p024-docker.sock
export TESTCONTAINERS_HOST_OVERRIDE=192.168.17.216
JAVA_HOME=/Users/wangk/Documents/config/jdk-17.0.17+10/Contents/Home/ ./gradlew :backend:testMysql
```

Expected: 2 用例 PASS。排障预案：ryuk 起不来 → `export TESTCONTAINERS_RYUK_DISABLED=true`；镜像拉取慢 → 先 `ssh … 'docker pull mysql:8.0'`。**同时验证默认 `test` 不含 mysql 用例**：`JAVA_HOME=… ./gradlew :backend:test`（green，且无 IT 执行）。

- [ ] **Step 5: 提交**

```bash
git add backend/build.gradle backend/src/test/java/com/yr/perftest/platform/datamigration/
git commit -m "feat：P0-4 testMysql 任务与 Testcontainers 双用例——V1 真库上下文+LONGTEXT/datetime(6) 冒烟、迁移工具 E2E（显式主键+自增续号），经 SSH 隧道 DOCKER_HOST 驱动 216 远程 Docker 跑绿"
```

---

### Task 7: README 部署章节重写 + 文档收尾 + 验收

**Files:**
- Modify: `README.md`（部署章节）
- Modify: `docs/environment.md`（216 登记实况）
- Modify: `docs/architecture-and-roadmap.md`（P0-4 行 ⬜→🟨；清理 mysql-schema.sql 措辞）
- Modify: `docs/requirements-spec.md`（mysql-schema.sql 引用清理）
- Modify: `docs/implementation-log.md`（追加）

- [ ] **Step 1: README 部署章节重写**（要点）
  - 方案 A（本地开发）：MySQL 可达（216 已部署；或本机 `docker compose -f deploy/mysql/docker-compose.yml up -d` + `SPRING_DATASOURCE_URL` 覆盖指向 localhost）→ `./gradlew :backend:bootRun`（默认连 216，Flyway 自动建表+种子账号）；环境变量覆盖数据源一行说明；Testcontainers 远程跑法（DOCKER_HOST 隧道 + TESTCONTAINERS_HOST_OVERRIDE）。
  - 「已有 H2 数据迁往 MySQL」一节：`SPRING_DATASOURCE_URL=…mysql… app.data-migration.h2-source=./storage/perftest java -jar …` 一次，完成后人工归档旧 `perftest.mv.db`。
  - 删除 `mysql-schema.sql` 引用与旧方案 B 中不存在的 `application-prod.yml` 指引。
- [ ] **Step 2: environment.md** 216 行补部署实况（perftest-mysql 容器、/app/perftest/compose、账号端口）。
- [ ] **Step 3: roadmap** P0-4 行状态 ⬜→🟨（代码完成、待人工验收）；requirements-spec/roadmap 内 schema.sql 措辞清理（历史归档 openspec/ 不动）。
- [ ] **Step 4: 验收执行（spec §10 映射）**
  1. `JAVA_HOME=… ./gradlew :backend:test` 全绿；
  2. 隧道 + `./gradlew :backend:testMysql` 全绿；
  3. 默认配置启动冒烟：`./gradlew :backend:bootRun`（连 216，Flyway 建表+种子账号）→ curl 登录 admin → 建项目/建计划冒烟通过 → 停止；
  4. 存量数据迁移：**本机无 ./storage/perftest——记 N/A**（工具语义已被单测+IT 覆盖）；如有用户提供旧库再执行；
  5. 旧 H2 文件保护：N/A（无文件）；
  6. `grep -rn "MonitoringSchemaInitializer\|mysql-schema.sql"` 代码零残留。
- [ ] **Step 5: implementation-log 追加 + 提交 + push**

```bash
git add README.md docs/
git commit -m "docs：P0-4 收尾——README 部署章节重写（216 默认/本机 compose 覆盖/H2 数据迁移节/远程 Testcontainers 跑法），environment 登记 MySQL 实况，roadmap P0-4 🟨，实现日志与验收记录"
git push origin main
```

---

## Self-Review 记录

- **Spec 覆盖**：§3 配置形态（T1/T3）、§4.1 生成方式（T2，服务器=216 即 spec 规划落点）、§4.2 双方言（T2 清洗+T3 H2 网+T6 MySQL 网）、§4.4 规约 validate/无 baseline-on-migrate（T3）、§5 迁移工具（T5+T6 用例 2）、§6 compose（T1）、§7 测试策略（T3/T6）、§8 交付物清单逐项映射（含删除项 T4、README T7）。
- **裁量**（依 spec 最优解，最终报告复核）：
  1. spec §7"测试零改动"与 M1"validate 全局"冲突——以 M1（§2 定死口径）为准：55 处 create-drop→validate 是 sweep 不是语义改动，属 M1 落地的必要动作。
  2. Testcontainers 经 SSH unix-socket 隧道驱动远程 Docker：spec 未预见"本机无 Docker"场景，此为满足 M5"需 Docker"验收且不装本地 Docker 的唯一无损方案；服务器零配置改动。
  3. `com.mysql:mysql-connector-j` 已在依赖中，无需新增（spec §8 清单的隐含项）。
  4. 本机无存量 H2 文件 → 验收 #4/#5 记 N/A，工具语义由测试覆盖；真实迁移在用户提供旧库时一次执行。
- **类型一致性**：`DataMigrationRunner.migrate(String, boolean)` 在 T5/T6 两用例一致；V1 路径 `db/migration/V1__baseline.sql` 全链一致；表清单唯一来源 = V1 文件。
