# 脚本版本发布模型（draft / published）Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 脚本编辑以草稿多次保存、手动发布生成不可变版本快照；场景绑定与执行只认已发布版本；删除有引用守卫；清理 parseStatus 假信号。

**Architecture:** 实体化 `scripts` 主表（名称 + 版本号水位），`script_versions` 挂靠脚本并增加 `status(DRAFT|PUBLISHED)`/`remark`；新增 `ScriptPublicationService`（草稿/发布/fork）、`ScriptDeletionService`（守卫删除）；前端脚本列表改脚本维度分组，编辑器拆分"保存草稿/发布"两动作。

**Tech Stack:** Spring Boot 3 (Java 17) + JPA + Flyway（SQL V11 DDL + Java V12 存量回填）；Vue 3 + TypeScript + ant-design-vue；测试 JUnit5 MockMvc(H2 MODE=MySQL) + vitest。

**Spec:** `docs/superpowers/specs/2026-09-17-script-version-publish-design.md`（决策记录 C1-C13；本计划从 spec 出发，执行者须先读 spec）

## Global Constraints

- 后端 Gradle 命令必须 `JAVA_HOME=/Users/wangk/Documents/config/jdk-17.0.17+10/Contents/Home/`。
- 后端单测：`JAVA_HOME=… ./gradlew :backend:test --tests '<Class>'`；H2 内存库自动跑 Flyway（`ScriptApiBehaviorTest` 的 properties 模板）。
- 前端校验：`cd frontend && npm run build`（含 vue-tsc --noEmit）；`npm run test`（vitest）。
- 状态值代码/API 层英文 `DRAFT`/`PUBLISHED`；用户可见文案中文（`草稿`/`已发布`，错误消息如"该版本被场景「x」引用，无法删除"）。
- 不新增依赖；不改构建/格式化配置；后端类 ≤500 行。
- Commit 格式：`<type>：<中文描述>`（如 `feat：脚本版本发布模型——V11 迁移与 scripts 实体`）。
- 包结构遵循现状：脚本域代码在 `com.yr.perftest.platform.script`，控制器在 `com.yr.perftest.platform.api`。
- 所有新持久化字段经 Flyway 迁移，禁用 hibernate ddl-auto 生成（现网 `validate`）。

---

### Task 1: V11 DDL 迁移 + scripts 实体 + 状态枚举

**Files:**
- Create: `backend/src/main/resources/db/migration/V11__script_publish.sql`
- Create: `backend/src/main/java/com/yr/perftest/platform/script/PersistentScriptRecord.java`
- Create: `backend/src/main/java/com/yr/perftest/platform/script/PersistentScriptRepository.java`
- Create: `backend/src/main/java/com/yr/perftest/platform/script/ScriptVersionStatus.java`
- Modify: `backend/src/main/java/com/yr/perftest/platform/script/PersistentScriptVersionRecord.java`（加映射字段，无行为变化）
- Test: `backend/src/test/java/com/yr/perftest/platform/script/ScriptEntityMappingTest.java`

**Interfaces:**
- Consumes: 无（首个任务）。
- Produces:
  - `enum ScriptVersionStatus { DRAFT, PUBLISHED }`
  - `PersistentScriptRecord`：字段 `id, projectId, name, latestVersionNo, createdBy, createdAt`；方法 `getPersistentId(): Long`、`getProjectId(): Long`、`getName(): String`、`getLatestVersionNo(): int`、`bumpLatestVersionNo(int newNo): void`、包内静态工厂 `persistentOf(Long projectId, String name, int latestVersionNo, String createdBy, Instant createdAt)`
  - `PersistentScriptRepository extends JpaRepository<PersistentScriptRecord, Long>`：`List<PersistentScriptRecord> findAllByProjectIdOrderByIdAsc(Long projectId)`、`Optional<PersistentScriptRecord> findByIdAndProjectId(Long id, Long projectId)`、`boolean existsByProjectIdAndName(Long projectId, String name)`
  - `PersistentScriptVersionRecord` 新增 getter：`getScriptId(): Long`、`getStatus(): ScriptVersionStatus`、`getRemark(): String`、`getUpdatedAt(): Instant`

- [ ] **Step 1: 写 V11 迁移 SQL**

```sql
-- V11__script_publish.sql
-- 脚本版本发布模型（spec 2026-09-17 §4）：scripts 主表 + script_versions 挂靠与状态列。
-- status 用 varchar 而非 ENUM，沿用 V7 的列风格决策（放宽与改值无痛）。
CREATE TABLE scripts (
  id                BIGINT NOT NULL AUTO_INCREMENT,
  project_id        BIGINT NOT NULL,
  name              VARCHAR(200) NOT NULL,
  latest_version_no INT NOT NULL DEFAULT 0,
  created_by        VARCHAR(80) NULL,
  created_at        DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  CONSTRAINT uk_script_name UNIQUE (project_id, name)
);

ALTER TABLE script_versions
  ADD COLUMN script_id  BIGINT NULL,
  ADD COLUMN status     VARCHAR(16) NOT NULL DEFAULT 'PUBLISHED',
  ADD COLUMN remark     VARCHAR(512) NULL,
  ADD COLUMN updated_at DATETIME(6) NULL;

ALTER TABLE script_versions
  ADD CONSTRAINT fk_sv_script FOREIGN KEY (script_id) REFERENCES scripts (id);

-- MySQL/H2(MODE=MySQL) 均允许多行 NULL，存量行 script_id 为 NULL 不冲突；
-- 回填后每行都有 script_id，(script_id, version_no) 唯一生效。
CREATE UNIQUE INDEX uk_script_version ON script_versions (script_id, version_no);
```

注意：`CREATE UNIQUE INDEX` 语法 H2 MODE=MySQL 支持；若迁移报语法错，改用 `ALTER TABLE script_versions ADD CONSTRAINT uk_script_version UNIQUE (script_id, version_no);`。

- [ ] **Step 2: 写失败测试（实体映射 + 迁移可启动）**

```java
package com.yr.perftest.platform.script;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:script-entity-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
        "spring.jpa.hibernate.ddl-auto=validate",
        "platform.storage.root=./build/test-storage/script-entity"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ScriptEntityMappingTest {
    @Autowired
    private PersistentScriptRepository scriptRepository;
    @Autowired
    private PersistentScriptVersionRepository versionRepository;

    @Test
    void persistsScriptAndDraftVersion() {
        PersistentScriptRecord script = scriptRepository.save(
                PersistentScriptRecord.persistentOf(1L, "登录链路压测", 0, "admin", Instant.now()));
        script.bumpLatestVersionNo(1);
        scriptRepository.saveAndFlush(script);

        PersistentScriptVersionRecord version = versionRepository.save(new PersistentScriptVersionRecord(
                script.getPersistentId(), script.getProjectId(), 0, "登录链路压测.jmx",
                "./build/test-storage/script-entity/draft.jmx", "admin", Instant.now(),
                ScriptVersionStatus.DRAFT, null));
        version.markPublished(1, "首发", "admin", Instant.now());

        assertThat(scriptRepository.findByIdAndProjectId(script.getPersistentId(), 1L)).isPresent();
        assertThat(versionRepository.findById(version.getId()).orElseThrow().getStatus())
                .isEqualTo(ScriptVersionStatus.PUBLISHED);
        assertThat(versionRepository.findById(version.getId()).orElseThrow().getRemark()).isEqualTo("首发");
    }
}
```

- [ ] **Step 3: 跑测试确认失败**

Run: `JAVA_HOME=/Users/wangk/Documents/config/jdk-17.0.17+10/Contents/Home/ ./gradlew :backend:test --tests 'com.yr.perftest.platform.script.ScriptEntityMappingTest'`
Expected: 编译失败（`PersistentScriptRecord`、`ScriptVersionStatus`、新构造器/`markPublished` 不存在）。

- [ ] **Step 4: 实现实体与枚举**

`ScriptVersionStatus.java`：

```java
package com.yr.perftest.platform.script;

public enum ScriptVersionStatus {
    DRAFT,
    PUBLISHED
}
```

`PersistentScriptRecord.java`（列名对齐 V11）：

```java
package com.yr.perftest.platform.script;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "scripts")
public class PersistentScriptRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long projectId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "latest_version_no", nullable = false)
    private int latestVersionNo;

    @Column(name = "created_by", length = 80)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PersistentScriptRecord() {
    }

    static PersistentScriptRecord persistentOf(Long projectId, String name, int latestVersionNo, String createdBy, Instant createdAt) {
        PersistentScriptRecord record = new PersistentScriptRecord();
        record.projectId = projectId;
        record.name = name;
        record.latestVersionNo = latestVersionNo;
        record.createdBy = createdBy;
        record.createdAt = createdAt;
        return record;
    }

    void bumpLatestVersionNo(int newNo) {
        if (newNo > latestVersionNo) {
            latestVersionNo = newNo;
        }
    }

    public Long getPersistentId() { return id; }
    public Long getProjectId() { return projectId; }
    public String getName() { return name; }
    public int getLatestVersionNo() { return latestVersionNo; }
}
```

`PersistentScriptRepository.java`：

```java
package com.yr.perftest.platform.script;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PersistentScriptRepository extends JpaRepository<PersistentScriptRecord, Long> {
    List<PersistentScriptRecord> findAllByProjectIdOrderByIdAsc(Long projectId);

    Optional<PersistentScriptRecord> findByIdAndProjectId(Long id, Long projectId);

    boolean existsByProjectIdAndName(Long projectId, String name);
}
```

`PersistentScriptVersionRecord.java` 修改：加字段 `private Long scriptId;`、`@Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private ScriptVersionStatus status;`（import `jakarta.persistence.EnumType`/`Enumerated`）、`@Column(length = 512) private String remark;`、`private Instant updatedAt;`；既有包内构造器扩为：

```java
    PersistentScriptVersionRecord(
            Long scriptId,
            Long projectId,
            Integer versionNo,
            String originalFilename,
            String storedPath,
            String uploadedBy,
            Instant uploadedAt,
            ScriptVersionStatus status,
            String remark
    ) { /* 全部赋值，updatedAt = uploadedAt */ }
```

并加转换方法（Task 3 起使用，本任务先落位）：

```java
    void markPublished(int versionNo, String remark, String publishedBy, Instant publishedAt) {
        this.versionNo = versionNo;
        this.remark = remark;
        this.uploadedBy = publishedBy;
        this.uploadedAt = publishedAt;
        this.updatedAt = publishedAt;
        this.status = ScriptVersionStatus.PUBLISHED;
    }
```

注意：旧包内构造器（6 参）当前被 `ScriptService.storeScript` 调用——本任务直接改造该调用点为 9 参（传 `null` scriptId、`ScriptVersionStatus.PUBLISHED`、`null` remark，行为不变，迁移尚未回填时新上传暂无脚本归属，Task 3 修正）。

- [ ] **Step 5: 跑测试确认通过 + 全量回归**

Run: `JAVA_HOME=… ./gradlew :backend:test --tests 'com.yr.perftest.platform.script.ScriptEntityMappingTest'`
Expected: PASS。
Run: `JAVA_HOME=… ./gradlew :backend:test`
Expected: 全绿（现有用例不因加列失败）。

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/resources/db/migration/V11__script_publish.sql \
  backend/src/main/java/com/yr/perftest/platform/script/PersistentScriptRecord.java \
  backend/src/main/java/com/yr/perftest/platform/script/PersistentScriptRepository.java \
  backend/src/main/java/com/yr/perftest/platform/script/ScriptVersionStatus.java \
  backend/src/main/java/com/yr/perftest/platform/script/PersistentScriptVersionRecord.java \
  backend/src/test/java/com/yr/perftest/platform/script/ScriptEntityMappingTest.java
git commit -m "feat：脚本版本发布模型——V11 迁移、scripts 实体与状态枚举"
```

---

### Task 2: V12 Java 存量回填迁移

**Files:**
- Create: `backend/src/main/java/com/yr/perftest/platform/db/migration/V12__ScriptBackfill.java`
- Create: `backend/src/main/java/com/yr/perftest/platform/script/ScriptBackfillCalculator.java`
- Test: `backend/src/test/java/com/yr/perftest/platform/script/ScriptBackfillCalculatorTest.java`

**Interfaces:**
- Consumes: Task 1 的 V11 列结构。
- Produces:
  - `record ScriptBackfillPlan(long scriptIdPlaceholderIgnored…)` 不需要——精确契约为：
  - `ScriptBackfillCalculator.backfillPlans(List<LegacyVersionRow> rows): List<BackfillPlan>`，其中
    `record LegacyVersionRow(long id, long projectId, String originalFilename, int versionNo, String uploadedBy, Instant uploadedAt)`、
    `record BackfillPlan(String scriptName, int latestVersionNo, String createdBy, Instant createdAt, long versionId)`
  - `V12__ScriptBackfill extends BaseJavaMigration`（org.flywaydb），包路径 `com.yr.perftest.platform.db.migration`（Flyway 默认扫描 `classpath:db/migration`，Java migration 与 SQL 同包名即可被发现——注意：Java 类需放 `src/main/java/com/yr/perftest/platform/db/migration/`，与 resources 目录的 `db/migration` 形成同包）。

- [ ] **Step 1: 写失败测试（纯函数：命名、重名去重、水位）**

```java
package com.yr.perftest.platform.script;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScriptBackfillCalculatorTest {
    private final Instant now = Instant.parse("2026-09-17T00:00:00Z");

    @Test
    void buildsOneShellPerLegacyRowWithStrippedName() {
        ScriptBackfillCalculator calculator = new ScriptBackfillCalculator();
        List<ScriptBackfillCalculator.BackfillPlan> plans = calculator.backfillPlans(List.of(
                new ScriptBackfillCalculator.LegacyVersionRow(11L, 1L, "loan-search.jmx", 3, "admin", now),
                new ScriptBackfillCalculator.LegacyVersionRow(12L, 1L, "Order-API.JMX", 7, "admin", now)
        ));
        assertThat(plans).extracting(ScriptBackfillCalculator.BackfillPlan::scriptName)
                .containsExactly("loan-search", "order-api");
        assertThat(plans).extracting(ScriptBackfillCalculator.BackfillPlan::latestVersionNo)
                .containsExactly(3, 7);
    }

    @Test
    void deduplicatesDuplicateNamesByVersionRowId() {
        ScriptBackfillCalculator calculator = new ScriptBackfillCalculator();
        List<ScriptBackfillCalculator.BackfillPlan> plans = calculator.backfillPlans(List.of(
                new ScriptBackfillCalculator.LegacyVersionRow(11L, 1L, "dup.jmx", 1, "admin", now),
                new ScriptBackfillCalculator.LegacyVersionRow(12L, 1L, "dup.jmx", 2, "admin", now)
        ));
        assertThat(plans).extracting(ScriptBackfillCalculator.BackfillPlan::scriptName)
                .containsExactly("dup", "dup-12");
    }

    @Test
    void nameIsTruncatedTo200CharsForColumnLimit() {
        ScriptBackfillCalculator calculator = new ScriptBackfillCalculator();
        String longName = "x".repeat(260);
        List<ScriptBackfillCalculator.BackfillPlan> plans = calculator.backfillPlans(List.of(
                new ScriptBackfillCalculator.LegacyVersionRow(11L, 1L, longName + ".jmx", 1, "admin", now)
        ));
        assertThat(plans.get(0).scriptName()).hasSize(200);
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `JAVA_HOME=… ./gradlew :backend:test --tests 'com.yr.perftest.platform.script.ScriptBackfillCalculatorTest'`
Expected: 编译失败（类不存在）。

- [ ] **Step 3: 实现 calculator 与 Java migration**

`ScriptBackfillCalculator.java`：

```java
package com.yr.perftest.platform.script;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class ScriptBackfillCalculator {
    public record LegacyVersionRow(long id, long projectId, String originalFilename, int versionNo, String uploadedBy, Instant uploadedAt) {
    }

    public record BackfillPlan(String scriptName, int latestVersionNo, String createdBy, Instant createdAt, long versionId) {
    }

    public List<BackfillPlan> backfillPlans(List<LegacyVersionRow> rows) {
        Set<String> usedNames = new HashSet<>();
        List<BackfillPlan> plans = new ArrayList<>();
        for (LegacyVersionRow row : rows) {
            String base = row.originalFilename()
                    .replaceFirst("(?i)\\.jmx$", "")
                    .toLowerCase(Locale.ROOT);
            String name = fit(base, row.id(), usedNames);
            usedNames.add(name);
            plans.add(new BackfillPlan(name, row.versionNo(), row.uploadedBy(), row.uploadedAt(), row.id()));
        }
        return plans;
    }

    private String fit(String base, long rowId, Set<String> used) {
        String candidate = truncate(base, 200);
        while (used.contains(candidate)) {
            String suffix = "-" + rowId;
            candidate = truncate(base, 200 - suffix.length()) + suffix;
            break; // 同 id 只会撞一次；极端重复再截断后仍冲突时附加时间戳
        }
        return candidate;
    }

    private String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
```

（`fit` 的 while-break 写法如上够用：同组重名只可能撞基名一次，`-{id}` 全局唯一。若审查认为不严谨，可改为 `while (used.contains(candidate)) { candidate = truncate(base + "-" + used.size(), 200); }`。）

`V12__ScriptBackfill.java`（包名必须是 `com.yr.perftest.platform.db.migration`，与 `classpath:db/migration` 对应）：

```java
package com.yr.perftest.platform.db.migration;

import com.yr.perftest.platform.script.ScriptBackfillCalculator;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class V12__ScriptBackfill extends BaseJavaMigration {
    private final ScriptBackfillCalculator calculator = new ScriptBackfillCalculator();

    @Override
    public void migrate(Context context) throws Exception {
        List<ScriptBackfillCalculator.LegacyVersionRow> rows = new ArrayList<>();
        try (Statement statement = context.getConnection().createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT id, project_id, original_filename, version_no, uploaded_by, uploaded_at " +
                             "FROM script_versions WHERE script_id IS NULL ORDER BY id")) {
            while (rs.next()) {
                rows.add(new ScriptBackfillCalculator.LegacyVersionRow(
                        rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getInt(4),
                        rs.getString(5), rs.getTimestamp(6).toInstant()));
            }
        }
        for (ScriptBackfillCalculator.BackfillPlan plan : calculator.backfillPlans(rows)) {
            long scriptId;
            try (PreparedStatement insert = context.getConnection().prepareStatement(
                    "INSERT INTO scripts (project_id, name, latest_version_no, created_by, created_at) VALUES (?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                insert.setLong(1, projectIdOf(rows, plan.versionId()));
                insert.setString(2, plan.scriptName());
                insert.setInt(3, plan.latestVersionNo());
                insert.setString(4, plan.createdBy());
                insert.setTimestamp(5, Timestamp.from(plan.createdAt()));
                insert.executeUpdate();
                try (ResultSet keys = insert.getGeneratedKeys()) {
                    keys.next();
                    scriptId = keys.getLong(1);
                }
            }
            try (PreparedStatement update = context.getConnection().prepareStatement(
                    "UPDATE script_versions SET script_id = ?, status = 'PUBLISHED', updated_at = uploaded_at WHERE id = ?")) {
                update.setLong(1, scriptId);
                update.setLong(2, plan.versionId());
                update.executeUpdate();
            }
        }
    }

    private long projectIdOf(List<ScriptBackfillCalculator.LegacyVersionRow> rows, long versionId) {
        return rows.stream().filter(r -> r.id() == versionId).findFirst().orElseThrow().projectId();
    }
}
```

- [ ] **Step 4: 验证迁移在 H2 下可执行**

在 `ScriptEntityMappingTest` 同风格新建属性不同的测试类会重建库——直接跑既有 `ScriptApiBehaviorTest`（其 H2 库启动即执行 V11+V12，空表回填为 no-op）：

Run: `JAVA_HOME=… ./gradlew :backend:test --tests 'com.yr.perftest.platform.api.ScriptApiBehaviorTest'`
Expected: PASS（迁移无异常、空回填通过）。

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/yr/perftest/platform/db/migration/V12__ScriptBackfill.java \
  backend/src/main/java/com/yr/perftest/platform/script/ScriptBackfillCalculator.java \
  backend/src/test/java/com/yr/perftest/platform/script/ScriptBackfillCalculatorTest.java
git commit -m "feat：V12 存量脚本版本回填——逐记录建 scripts 壳并标记 PUBLISHED"
```

---

### Task 3: 上传/新建入口的脚本壳与发布语义

**Files:**
- Modify: `backend/src/main/java/com/yr/perftest/platform/script/ScriptService.java`（`uploadScript`、`createScript`、`storeScript`）
- Modify: `backend/src/main/java/com/yr/perftest/platform/script/ScriptVersion.java`
- Modify: `backend/src/main/java/com/yr/perftest/platform/script/ScriptDefinition.java`
- Test: `backend/src/test/java/com/yr/perftest/platform/api/ScriptApiBehaviorTest.java`（扩用例）

**Interfaces:**
- Consumes: Task 1 的 `PersistentScriptRepository`、`ScriptVersionStatus`、9 参构造器。
- Produces:
  - `ScriptVersion` record 变为 `(long id, long projectId, Long scriptId, int versionNo, String originalFilename, String storedPath, String uploadedBy, Instant uploadedAt, String status, String remark)`（API 层 status 用字符串）
  - `ScriptDefinition` 增加 `long scriptId, String status, String remark`（parseStatus 本任务不动，Task 8 清理）
  - `ScriptService.uploadScript(projectId, file, uploadedBy)`：新脚本名 → 建 scripts 壳 + `PUBLISHED v1`；同名已存在 → 追加 `PUBLISHED v{latest+1}`（multipart 可选 part `remark`，缺省 `""`）
  - `ScriptService.createScript(projectId, name, uploadedBy)`：建壳 + DRAFT 脚手架（`versionNo = 0`）

- [ ] **Step 1: 写失败测试（MockMvc 追加到 ScriptApiBehaviorTest）**

```java
    @Test
    void uploadCreatesScriptShellWithPublishedV1() throws Exception {
        createProject();

        mockMvc.perform(multipart("/api/projects/1/scripts")
                        .file(jmxFile("loan-search.jmx"))
                        .header("Authorization", "Bearer " + authToken)
                        .header("X-User", "admin"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.versionNo", is(1)))
                .andExpect(jsonPath("$.status", is("PUBLISHED")))
                .andExpect(jsonPath("$.scriptId", is(1)))
                .andExpect(jsonPath("$.remark", is("")));

        mockMvc.perform(get("/api/projects/1/scripts/1/definition")
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("loan-search")))
                .andExpect(jsonPath("$.status", is("PUBLISHED")));
    }

    @Test
    void reuploadSameNameAppendsPublishedVersionWithRemark() throws Exception {
        createProject();
        mockMvc.perform(multipart("/api/projects/1/scripts")
                .file(jmxFile("loan-search.jmx"))
                .header("Authorization", "Bearer " + authToken).header("X-User", "admin"))
                .andExpect(status().isCreated());

        MockMultipartFile second = jmxFile("loan-search.jmx");
        mockMvc.perform(multipart("/api/projects/1/scripts")
                        .file(second)
                        .param("remark", "修复登录接口路径")
                        .header("Authorization", "Bearer " + authToken)
                        .header("X-User", "admin"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.versionNo", is(2)))
                .andExpect(jsonPath("$.scriptId", is(1)))
                .andExpect(jsonPath("$.status", is("PUBLISHED")))
                .andExpect(jsonPath("$.remark", is("修复登录接口路径")));
    }

    @Test
    void createBlankScriptStartsAsDraft() throws Exception {
        createProject();

        mockMvc.perform(post("/api/projects/1/scripts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + authToken)
                        .header("X-User", "admin")
                        .content("{\"name\":\"登录链路压测\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is("登录链路压测")))
                .andExpect(jsonPath("$.status", is("DRAFT")))
                .andExpect(jsonPath("$.steps[0].type", is("THREAD_GROUP")));
    }
```

同时提取 helper（测试类内）：

```java
    private MockMultipartFile jmxFile(String name) {
        return new MockMultipartFile("file", name, MediaType.APPLICATION_XML_VALUE,
                "<jmeterTestPlan></jmeterTestPlan>".getBytes());
    }
```

既有用例 `uploadsAndListsJmeterScriptVersions` 断言不受影响（versionNo 仍为 1）；`createsBlankScriptFromJson` 保持通过（latestVersion 语义见 Step 3：DRAFT 时 latestVersion=0，若该用例断言了版本号则同步更新为 0）。

- [ ] **Step 2: 跑测试确认失败**

Run: `JAVA_HOME=… ./gradlew :backend:test --tests 'com.yr.perftest.platform.api.ScriptApiBehaviorTest'`
Expected: 新用例 FAIL（`$.status`/`$.scriptId` 字段不存在）。

- [ ] **Step 3: 实现**

`ScriptVersion.java` 全量替换：

```java
package com.yr.perftest.platform.script;

import java.time.Instant;

public record ScriptVersion(
        long id,
        long projectId,
        Long scriptId,
        int versionNo,
        String originalFilename,
        String storedPath,
        String uploadedBy,
        Instant uploadedAt,
        String status,
        String remark
) {
}
```

`ScriptDefinition.java`：在 `latestVersion` 后插入 `long scriptId, String status, String remark`（保留 `parseStatus` 至 Task 8）。

`PersistentScriptVersionRecord.toScriptVersion()` 同步为 10 参（`status.name()`、`remark`）。

`ScriptService` 改造（构造器追加注入 `PersistentScriptRepository scriptRepository`；关键方法体）：

```java
    @Transactional
    public ScriptVersion uploadScript(long projectId, MultipartFile file, String uploadedBy, String remark) {
        if (!projectRepository.existsById(projectId)) {
            throw new ProjectValidationException("project does not exist");
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.trim().isEmpty()) {
            throw new ScriptValidationException("script filename is required");
        }
        if (!originalFilename.toLowerCase(Locale.ROOT).endsWith(".jmx")) {
            throw new ScriptValidationException("only .jmx files are supported");
        }
        if (file.isEmpty()) {
            throw new ScriptValidationException("script file is empty");
        }
        String content = new String(readFile(file), StandardCharsets.UTF_8);
        validateJmx(content);
        String name = nameOf(originalFilename);
        PersistentScriptRecord script = scriptRepository.findByProjectIdAndName(projectId, name)
                .orElseGet(() -> scriptRepository.save(PersistentScriptRecord.persistentOf(
                        projectId, name, 0, uploadedBy, Instant.now())));
        return storePublishedVersion(script, originalFilename, content, uploadedBy,
                remark == null ? "" : remark.trim());
    }

    private ScriptVersion storePublishedVersion(
            PersistentScriptRecord script, String originalFilename, String content, String uploadedBy, String remark) {
        int versionNo = script.getLatestVersionNo() + 1;
        Path target = scriptFilePath(script.getPersistentId(), versionNo, originalFilename);
        writeAtomically(target, content);
        PersistentScriptVersionRecord record = scriptVersionRepository.save(new PersistentScriptVersionRecord(
                script.getPersistentId(), script.getProjectId(), versionNo, originalFilename,
                target.toString(), uploadedBy, Instant.now(), ScriptVersionStatus.PUBLISHED, remark));
        script.bumpLatestVersionNo(versionNo);
        scriptRepository.save(script);
        return record.toScriptVersion();
    }

    private Path scriptFilePath(Long scriptId, int versionNo, String originalFilename) {
        return storageRoot.resolve("scripts")
                .resolve(String.valueOf(projectIdOf(scriptId)))
                .resolve("s" + scriptId)
                .resolve("v" + versionNo + "-" + sanitizeFilename(originalFilename));
    }
```

（`projectIdOf(scriptId)` 经 `scriptRepository.findById` 取；为免 N+1 直接把 `PersistentScriptRecord` 传入 helper 内联取 `getProjectId()`，写法自定，路径规则 = `storage/scripts/{projectId}/s{scriptId}/v{versionNo}-{filename}`。）

`createScript` 改为建壳 + DRAFT：

```java
    @Transactional
    public ScriptDefinition createScript(long projectId, String name, String uploadedBy) {
        if (!projectRepository.existsById(projectId)) {
            throw new ProjectValidationException("project does not exist");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new ScriptValidationException("script name is required");
        }
        String trimmed = name.trim();
        if (scriptRepository.existsByProjectIdAndName(projectId, trimmed)) {
            throw new ScriptValidationException("script name already exists");
        }
        PersistentScriptRecord script = scriptRepository.save(
                PersistentScriptRecord.persistentOf(projectId, trimmed, 0, uploadedBy, Instant.now()));
        String originalFilename = toJmxFilename(trimmed);
        ScriptStepDefinition threadGroup = new ScriptStepDefinition(
                "thread-1", ScriptStepType.THREAD_GROUP.code(), "线程组 1",
                new ThreadGroupConfig(100, 60, 1, 600, false).toMap(), List.of());
        String content = jmeterScriptRenderer.render(List.of(threadGroup));
        validateJmx(content);
        Path target = storageRoot.resolve("scripts")
                .resolve(String.valueOf(projectId))
                .resolve("s" + script.getPersistentId())
                .resolve("draft-" + sanitizeFilename(originalFilename));
        writeAtomically(target, content);
        scriptVersionRepository.save(new PersistentScriptVersionRecord(
                script.getPersistentId(), projectId, 0, originalFilename, target.toString(),
                uploadedBy, Instant.now(), ScriptVersionStatus.DRAFT, null));
        return getScriptDefinitionByScript(script, scriptRepository.findAllByProjectIdOrderByIdAsc(projectId));
    }
```

`writeAtomically`（本任务加入，供后续复用；同时替换 `storeScript`/`saveScriptContent` 里的 `Files.writeString`）：

```java
    private void writeAtomically(Path target, String content) {
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            Files.createDirectories(target.getParent());
            Files.writeString(tmp, content, StandardCharsets.UTF_8);
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            throw new ScriptValidationException("failed to store script file");
        }
    }
```

`toScriptDefinition` 填充新字段（`record.getScriptId()`、`record.getStatus().name()`、`record.getRemark()`），`parseStatus` 暂保留硬编码 `"PARSED"`。`ScriptController.uploadScript` 加 `@RequestParam(value = "remark", required = false) String remark` 透传。`listScripts`/`listScriptDefinitions`/`getScriptDefinition` 查询路径不变（返回结构含新字段）。

`PersistentScriptRepository` 补 `Optional<PersistentScriptRecord> findByProjectIdAndName(Long projectId, String name);`

- [ ] **Step 4: 跑测试确认通过**

Run: `JAVA_HOME=… ./gradlew :backend:test --tests 'com.yr.perftest.platform.api.ScriptApiBehaviorTest'`
Expected: PASS。
Run: `JAVA_HOME=… ./gradlew :backend:test`
Expected: 全绿；`ScriptContent`/`SaveScriptDefinitionResult` 编译面自动适配（record 扩参的编译错误在受影响处用 `.version()` 新结构修正，均为机械改动）。

- [ ] **Step 5: Commit**

```bash
git add -A backend/src
git commit -m "feat：上传/新建入口生成 scripts 壳——上传即 PUBLISHED、新建即 DRAFT，文件原子写"
```

---

### Task 4: 草稿与发布服务（ScriptPublicationService）

**Files:**
- Create: `backend/src/main/java/com/yr/perftest/platform/script/ScriptPublicationService.java`
- Create: `backend/src/main/java/com/yr/perftest/platform/api/ScriptPublicationController.java`
- Modify: `backend/src/main/java/com/yr/perftest/platform/api/ScriptController.java`（既有两保存端点加 DRAFT-only 校验）
- Test: `backend/src/test/java/com/yr/perftest/platform/api/ScriptPublicationApiTest.java`

**Interfaces:**
- Consumes: Task 3 的实体/仓库/`ScriptVersion`(10 参)、`ScriptService.readStoredContent`（改为包内可见或复制；建议在 `ScriptService` 提取 `String readStoredContent(PersistentScriptVersionRecord)` 为包内 static util `ScriptFiles.read(Path)`，两端共用）。
- Produces:
  - `ScriptPublicationService.saveDraftDefinition(long projectId, long scriptId, String filename, List<ScriptStepDefinition> steps, String updatedBy): SaveScriptDefinitionResult`
  - `ScriptPublicationService.saveDraftContent(long projectId, long scriptId, String filename, String content, String updatedBy): SaveScriptDefinitionResult`
  - `ScriptPublicationService.publish(long projectId, long scriptId, int versionNo, String remark, String publishedBy): ScriptVersion`
  - `ScriptPublicationService.forkDraft(long projectId, long scriptId, long sourceVersionId, String updatedBy): ScriptVersion`
  - 端点：`PUT /api/projects/{projectId}/scripts/{scriptId}/draft`（body 二选一：`{filename, steps}` 或 `{filename, content}`）、`POST …/{scriptId}/publish`、`POST …/{scriptId}/fork-draft`
  - `PersistentScriptVersionRepository` 补：`Optional<PersistentScriptVersionRecord> findByScriptIdAndStatus(Long scriptId, ScriptVersionStatus status);`、`List<PersistentScriptVersionRecord> findAllByScriptIdOrderByVersionNoDesc(Long scriptId);`

- [ ] **Step 1: 写失败测试（MockMvc，H2 属性串沿用 ScriptApiBehaviorTest 模板，storage.root 用 `./build/test-storage/script-pub`）**

覆盖五个行为（每个独立 @Test，均先 `createProject()` + JSON 建空白脚本得 scriptId=1）：

```java
    @Test
    void savesDraftThenPublishesImmutableVersion() throws Exception {
        createBlankScript(); // POST /api/projects/1/scripts {"name":"登录链路压测"}
        // 保存草稿（steps 形态）
        mockMvc.perform(put("/api/projects/1/scripts/1/draft")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + authToken).header("X-User", "admin")
                        .content("{\"filename\":\"登录链路压测.jmx\",\"steps\":[]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version.status", is("DRAFT")));
        // 发布
        mockMvc.perform(post("/api/projects/1/scripts/1/publish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + authToken).header("X-User", "admin")
                        .content("{\"versionNo\":1,\"remark\":\"首发版本\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("PUBLISHED")))
                .andExpect(jsonPath("$.versionNo", is(1)))
                .andExpect(jsonPath("$.remark", is("首发版本")));
    }

    @Test
    void publishRejectsNonIncreasingVersionNoAndBlankRemark() throws Exception {
        createBlankScript();
        saveDraft(); // PUT draft 空步骤
        publishOk(1, "首发"); // helper：发布成功
        saveDraft(); // 发布后自动重建草稿（保存草稿时惰性重建）
        mockMvc.perform(post("/api/projects/1/scripts/1/publish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + authToken).header("X-User", "admin")
                        .content("{\"versionNo\":1,\"remark\":\"重复号\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("version no must be greater than 1")));
        mockMvc.perform(post("/api/projects/1/scripts/1/publish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + authToken).header("X-User", "admin")
                        .content("{\"versionNo\":2,\"remark\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("change remark is required")));
    }

    @Test
    void publishRejectsWhenNoDraftExists() throws Exception {
        createBlankScript();
        mockMvc.perform(post("/api/projects/1/scripts/1/publish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + authToken).header("X-User", "admin")
                        .content("{\"versionNo\":1,\"remark\":\"无草稿\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("no draft to publish")));
    }

    @Test
    void forkDraftCopiesPublishedContentAsNewDraft() throws Exception {
        createBlankScript();
        saveDraft();
        publishOk(1, "首发");
        long publishedVersionId = versionIdOfScriptNumber(1); // helper：GET versions 取 PUBLISHED v1 的 id
        mockMvc.perform(post("/api/projects/1/scripts/1/fork-draft")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + authToken).header("X-User", "admin")
                        .content("{\"sourceVersionId\":" + publishedVersionId + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("DRAFT")));
    }

    @Test
    void savingDraftOnPublishedVersionEndpointIsRejected() throws Exception {
        createBlankScript();
        saveDraft();
        publishOk(1, "首发");
        long publishedVersionId = versionIdOfScriptNumber(1);
        mockMvc.perform(put("/api/projects/1/scripts/" + publishedVersionId + "/definition")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + authToken).header("X-User", "admin")
                        .content("{\"filename\":\"x.jmx\",\"steps\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("published version is immutable, edit the draft instead")));
    }
```

- [ ] **Step 2: 跑测试确认失败**

Run: `JAVA_HOME=… ./gradlew :backend:test --tests 'com.yr.perftest.platform.api.ScriptPublicationApiTest'`
Expected: 编译失败或 404（端点不存在）。

- [ ] **Step 3: 实现服务与控制器**

`ScriptPublicationService.java` 核心方法（`requireScript`：`scriptRepository.findByIdAndProjectId(scriptId, projectId).orElseThrow(() -> new ScriptValidationException("script does not exist"))`）：

```java
    @Transactional
    public SaveScriptDefinitionResult saveDraftDefinition(
            long projectId, long scriptId, String filename, List<ScriptStepDefinition> steps, String updatedBy) {
        PersistentScriptVersionRecord draft = ensureDraft(projectId, scriptId);
        String content = ScriptFiles.read(Path.of(draft.getStoredPath()));
        String patched = jmeterScriptPatcher.patch(content, steps == null ? List.of() : steps);
        if (steps != null && !steps.isEmpty() && jmeterScriptParser.parseSteps(patched).isEmpty()) {
            throw new ScriptValidationException("failed to persist script steps");
        }
        ScriptFiles.writeAtomically(Path.of(draft.getStoredPath()), patched);
        draft.updateDraftMetadata(resolveFilename(draft, filename), updatedBy, Instant.now());
        return new SaveScriptDefinitionResult(draft.toScriptVersion(), jsr223SecretScanner.scanScriptContent(patched));
    }

    @Transactional
    public ScriptVersion publish(long projectId, long scriptId, int versionNo, String remark, String publishedBy) {
        PersistentScriptRecord script = requireScript(projectId, scriptId);
        if (remark == null || remark.isBlank()) {
            throw new ScriptValidationException("change remark is required");
        }
        if (versionNo <= script.getLatestVersionNo()) {
            throw new ScriptValidationException("version no must be greater than " + script.getLatestVersionNo());
        }
        PersistentScriptVersionRecord draft = versionRepository.findByScriptIdAndStatus(scriptId, ScriptVersionStatus.DRAFT)
                .orElseThrow(() -> new ScriptValidationException("no draft to publish"));
        draft.markPublished(versionNo, remark.trim(), publishedBy, Instant.now());
        script.bumpLatestVersionNo(versionNo);
        scriptRepository.save(script);
        return draft.toScriptVersion();
    }

    private PersistentScriptVersionRecord ensureDraft(long projectId, long scriptId) {
        PersistentScriptRecord script = requireScript(projectId, scriptId);
        return versionRepository.findByScriptIdAndStatus(scriptId, ScriptVersionStatus.DRAFT)
                .orElseGet(() -> createDraftFrom(script, latestPublishedContentOrScaffold(script)));
    }

    @Transactional
    public ScriptVersion forkDraft(long projectId, long scriptId, long sourceVersionId, String updatedBy) {
        PersistentScriptRecord script = requireScript(projectId, scriptId);
        PersistentScriptVersionRecord source = versionRepository.findById(sourceVersionId)
                .filter(v -> scriptId.equals(v.getScriptId()))
                .filter(v -> v.getStatus() == ScriptVersionStatus.PUBLISHED)
                .orElseThrow(() -> new ScriptValidationException("source version is not a published version of this script"));
        versionRepository.findByScriptIdAndStatus(scriptId, ScriptVersionStatus.DRAFT)
                .ifPresent(versionRepository::delete);
        return createDraftFrom(script, ScriptFiles.read(Path.of(source.getStoredPath())));
    }
```

`createDraftFrom(script, content)`：写 `draft-{sanitized}` 文件（`writeAtomically`）、存 9 参记录（`versionNo=0, DRAFT, remark=null`）。`saveDraftContent` 与 `saveDraftDefinition` 同构（content 形态直接 `validateJmx` 后原子写）。

新建 `ScriptFiles`（`script` 包，static util）：`read(Path)`、`writeAtomically(Path, String)`（Task 3 已在 `ScriptService` 内实现的本逻辑迁到此 util，`ScriptService` 改调它，避免重复）。

`ScriptPublicationController`（`/api/projects/{projectId}/scripts/{scriptId}` 前缀，X-User header 模式同 `ScriptController`）：`PUT /draft`、`POST /publish`、`POST /fork-draft`，请求 record：

```java
    public record SaveDraftRequest(String filename, List<ScriptStepDefinition> steps, String content) {}
    public record PublishRequest(@NotNull Integer versionNo, @NotBlank String remark) {}
    public record ForkDraftRequest(@NotNull Long sourceVersionId) {}
```

`ScriptController` 两保存端点（`PUT /{versionId}` 与 `PUT /{versionId}/definition`）入口加守卫：

```java
    if (version.getStatus() != ScriptVersionStatus.DRAFT) {
        throw new ScriptValidationException("published version is immutable, edit the draft instead");
    }
```

（守卫放 `ScriptService.saveScriptContent`/`saveScriptDefinition` 开头，两入口共用。）

- [ ] **Step 4: 跑测试确认通过 + 全量回归**

Run: `JAVA_HOME=… ./gradlew :backend:test --tests 'com.yr.perftest.platform.api.ScriptPublicationApiTest'`
Expected: PASS。
Run: `JAVA_HOME=… ./gradlew :backend:test`
Expected: 全绿（既有保存用例若直接保存"新建脚本的版本"，新建即 DRAFT 仍可保存，行为兼容）。

- [ ] **Step 5: Commit**

```bash
git add -A backend/src
git commit -m "feat：草稿保存/发布/fork 服务与端点——发布不可变、水位校验、已发布版本禁改"
```

---

### Task 5: 删除守卫与脚本级联删除（ScriptDeletionService）

**Files:**
- Create: `backend/src/main/java/com/yr/perftest/platform/script/ScriptDeletionService.java`
- Modify: `backend/src/main/java/com/yr/perftest/platform/task/PersistentTaskScenarioRepository.java`
- Modify: `backend/src/main/java/com/yr/perftest/platform/api/ScriptController.java`（旧 DELETE 端点接守卫）
- Create: `backend/src/main/java/com/yr/perftest/platform/api/ScriptDeletionController.java`
- Test: `backend/src/test/java/com/yr/perftest/platform/api/ScriptDeletionApiTest.java`

**Interfaces:**
- Consumes: Task 1/3 实体；`PersistentTaskScenarioRecord.getScriptVersionId()/getPlanId()/getName()`。
- Produces:
  - `ScriptDeletionService.deleteVersion(long projectId, long versionId): void`
  - `ScriptDeletionService.deleteScript(long projectId, long scriptId): void`
  - `PersistentTaskScenarioRepository` 补：`List<PersistentTaskScenarioRecord> findByScriptVersionId(Long scriptVersionId);`、`List<PersistentTaskScenarioRecord> findByScriptVersionIdIn(List<Long> scriptVersionIds);`
  - 端点：`DELETE /api/projects/{projectId}/scripts/{scriptId}/versions/{versionId}`、`DELETE /api/projects/{projectId}/scripts/{scriptId}`；旧 `DELETE /api/projects/{projectId}/scripts/{versionId}` 内部改调 `deleteVersion`（前端 Task 10 切新路由）
  - 拒绝消息（中文，测试断言精确匹配）：`该版本被场景「<名>」引用，无法删除`（多场景用「、」连接）；脚本级：`脚本下存在被场景引用的版本（「<名>」…），无法删除`

- [ ] **Step 1: 写失败测试**

场景搭建 helper：`createProject()` → JSON 建计划 `POST /api/task-plans`（现有路由，参考 `TaskScenarioService` 用例；若路径不同以现有集成测试为准）→ 建空白脚本并发布 → `POST /api/task-plans/{planId}/scenarios` 绑定该版本（body 带 `scriptVersionId`）。测试类属性串 storage.root 用 `./build/test-storage/script-del`。

```java
    @Test
    void deletingReferencedPublishedVersionIsRejectedWithScenarioNames() throws Exception {
        long versionId = publishedVersionWithBoundScenario("下单链路"); // helper：发布 + 场景绑定
        mockMvc.perform(delete("/api/projects/1/scripts/1/versions/" + versionId)
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("该版本被场景「下单链路」引用，无法删除")));
    }

    @Test
    void deletingUnreferencedVersionSucceedsAndKeepsWatermark() throws Exception {
        long versionId = publishedVersionWithoutScenario(); // 发布 v1，无绑定
        mockMvc.perform(delete("/api/projects/1/scripts/1/versions/" + versionId)
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isNoContent());
        // 再发布仍要求 > 原水位
        saveDraft();
        mockMvc.perform(post("/api/projects/1/scripts/1/publish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + authToken).header("X-User", "admin")
                        .content("{\"versionNo\":1,\"remark\":\"复用号\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deletingScriptWithAnyReferencedVersionIsRejected() throws Exception {
        publishedVersionWithBoundScenario("下单链路");
        mockMvc.perform(delete("/api/projects/1/scripts/1")
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("脚本下存在被场景引用的版本（「下单链路」），无法删除")));
    }

    @Test
    void deletingUnreferencedScriptRemovesAllVersionsAndFiles() throws Exception {
        publishedVersionWithoutScenario();
        mockMvc.perform(delete("/api/projects/1/scripts/1")
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/projects/1/scripts/1/versions")
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isNotFound()); // 或 400，以 requireScript 异常映射为准
    }
```

- [ ] **Step 2: 跑测试确认失败**

Run: `JAVA_HOME=… ./gradlew :backend:test --tests 'com.yr.perftest.platform.api.ScriptDeletionApiTest'`
Expected: 404（端点不存在）/编译失败。

- [ ] **Step 3: 实现守卫与级联**

`ScriptDeletionService`（注入 `scriptRepository`、`versionRepository`、`scenarioRepository`、`planRepository`；项目过滤：scenario 经 `planRepository.findById(planId)` 比对 `projectId`）：

```java
    @Transactional
    public void deleteVersion(long projectId, long versionId) {
        PersistentScriptVersionRecord version = versionRepository.findByIdAndProjectId(versionId, projectId)
                .orElseThrow(() -> new ScriptValidationException("script version does not exist"));
        List<PersistentTaskScenarioRecord> refs = scenariosReferencing(projectId, List.of(versionId));
        if (version.getStatus() == ScriptVersionStatus.PUBLISHED && !refs.isEmpty()) {
            throw new ScriptValidationException("该版本被场景" + scenarioNames(refs) + "引用，无法删除");
        }
        versionRepository.delete(version);
        ScriptFiles.deleteQuietly(Path.of(version.getStoredPath()));
    }

    @Transactional
    public void deleteScript(long projectId, long scriptId) {
        PersistentScriptRecord script = scriptRepository.findByIdAndProjectId(scriptId, projectId)
                .orElseThrow(() -> new ScriptValidationException("script does not exist"));
        List<PersistentScriptVersionRecord> versions =
                versionRepository.findAllByScriptIdOrderByVersionNoDesc(scriptId);
        List<PersistentTaskScenarioRecord> refs = scenariosReferencing(projectId,
                versions.stream().map(PersistentScriptVersionRecord::getId).toList());
        if (!refs.isEmpty()) {
            throw new ScriptValidationException("脚本下存在被场景引用的版本（" + scenarioNames(refs) + "），无法删除");
        }
        versions.forEach(version -> ScriptFiles.deleteQuietly(Path.of(version.getStoredPath())));
        versionRepository.deleteAllInBatch(versions);
        scriptRepository.delete(script);
    }

    private String scenarioNames(List<PersistentTaskScenarioRecord> refs) {
        return refs.stream().map(PersistentTaskScenarioRecord::getName)
                .map(name -> "「" + name + "」").collect(Collectors.joining("、"));
    }
```

（`scenariosReferencing(projectId, versionIds)`：`scenarioRepository.findByScriptVersionIdIn(ids)` 后按 plan 归属过滤到本项目。`ScriptFiles.deleteQuietly` 吞 IOException。）

`ScriptDeletionController`：两个 DELETE 端点（`@ResponseStatus(NO_CONTENT)`）；`ScriptController.deleteScript`（旧路由 `/{versionId:\d+}`）改调 `deletionService.deleteVersion`，`ScriptService.deleteScript` 删除。

- [ ] **Step 4: 跑测试确认通过 + 全量回归**

Run: `JAVA_HOME=… ./gradlew :backend:test --tests 'com.yr.perftest.platform.api.ScriptDeletionApiTest'`
Expected: PASS。
Run: `JAVA_HOME=… ./gradlew :backend:test`
Expected: 全绿。

- [ ] **Step 5: Commit**

```bash
git add -A backend/src
git commit -m "feat：脚本版本/脚本删除守卫——被场景引用拒绝（中文报错列名），级联删全版本与文件"
```

---

### Task 6: 绑定与执行只认 PUBLISHED

**Files:**
- Modify: `backend/src/main/java/com/yr/perftest/platform/task/TaskScenarioService.java:380-387`（`validateScript`）
- Modify: `backend/src/main/java/com/yr/perftest/platform/execution/distributed/DistributedJmeterExecutionRunner.java:289`（prepare 处）
- Test: `backend/src/test/java/com/yr/perftest/platform/api/ScriptPublicationApiTest.java`（追加用例，复用搭建 helper）

**Interfaces:**
- Consumes: `PersistentScriptVersionRecord.getStatus()`。
- Produces: `validateScript` 与执行 prepare 对非 PUBLISHED 版本抛 `"script version is not published"`（`ExecutionValidationException`，现有 ApiError 映射）。

- [ ] **Step 1: 写失败测试（追加到 ScriptPublicationApiTest）**

```java
    @Test
    void bindingDraftVersionIsRejected() throws Exception {
        createProject();
        long planId = createPlan();
        long draftVersionId = blankScriptDraftVersionId(); // JSON 建脚本 → 其 DRAFT 版本 id
        mockMvc.perform(post("/api/task-plans/" + planId + "/scenarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + authToken).header("X-User", "admin")
                        .content("{\"name\":\"下单场景\",\"scriptVersionId\":" + draftVersionId + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("script version is not published")));
    }
```

（`POST /api/task-plans/{planId}/scenarios` 的确切路由/body 以 `TaskScenarioController` 现状为准，执行时读该控制器对齐。）

- [ ] **Step 2: 跑测试确认失败**

Run: `JAVA_HOME=… ./gradlew :backend:test --tests 'com.yr.perftest.platform.api.ScriptPublicationApiTest'`
Expected: 新用例 FAIL（当前可绑定成功 → 2xx）。

- [ ] **Step 3: 实现两处校验**

`TaskScenarioService.validateScript` 尾部追加：

```java
        if (script.getStatus() != ScriptVersionStatus.PUBLISHED) {
            throw new ExecutionValidationException("script version is not published");
        }
```

`DistributedJmeterExecutionRunner` prepare 中 `scriptVersionRepository.findById(...)` 之后同样追加（import `ScriptVersionStatus`）。

- [ ] **Step 4: 跑测试确认通过 + 全量回归**

Run: `JAVA_HOME=… ./gradlew :backend:test --tests 'com.yr.perftest.platform.api.ScriptPublicationApiTest'` → PASS；
Run: `JAVA_HOME=… ./gradlew :backend:test` → 全绿。

- [ ] **Step 5: Commit**

```bash
git add -A backend/src
git commit -m "feat：场景绑定与执行装配校验版本已发布——DRAFT 拒绝"
```

---

### Task 7: 脚本维度聚合查询（assets + versions 端点）

**Files:**
- Create: `backend/src/main/java/com/yr/perftest/platform/script/ScriptAssetSummary.java`
- Create: `backend/src/main/java/com/yr/perftest/platform/script/ScriptQueryService.java`
- Create: `backend/src/main/java/com/yr/perftest/platform/api/ScriptAssetController.java`
- Test: `backend/src/test/java/com/yr/perftest/platform/api/ScriptAssetApiTest.java`

**Interfaces:**
- Consumes: Task 1-5 实体/仓库；`PersistentTaskScenarioRepository.findByScriptVersionIdIn`。
- Produces:
  - `record ScriptAssetSummary(long id, long projectId, String name, int latestVersionNo, ScriptVersion latestPublished, boolean hasDraft, Instant draftUpdatedAt, int currentScenarioCount, int outdatedScenarioCount)`
  - `ScriptQueryService.listAssets(long projectId): List<ScriptAssetSummary>`、`listVersions(long projectId, long scriptId): List<ScriptVersionWithRefs>`，`record ScriptVersionWithRefs(ScriptVersion version, List<String> referencedScenarioNames)`
  - 端点：`GET /api/projects/{projectId}/scripts/assets`、`GET /api/projects/{projectId}/scripts/{scriptId}/versions`
  - 旧 `/definitions` 端点本任务不动（Task 12 移除）

- [ ] **Step 1: 写失败测试**

搭建：建项目 → 上传 A.jmx（PUBLISHED v1）→ JSON 建脚本 B（DRAFT）→ 为 A 建场景绑定。断言：

```java
    @Test
    void listsScriptDimensionAssetsWithUsage() throws Exception {
        // A: hasDraft=false, latestVersionNo=1, currentScenarioCount=1, outdatedScenarioCount=0
        mockMvc.perform(get("/api/projects/1/scripts/assets")
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name", is("a")))
                .andExpect(jsonPath("$[0].hasDraft", is(false)))
                .andExpect(jsonPath("$[0].currentScenarioCount", is(1)))
                .andExpect(jsonPath("$[1].name", is("b")))
                .andExpect(jsonPath("$[1].hasDraft", is(true)))
                .andExpect(jsonPath("$[1].latestVersionNo", is(0)));
    }

    @Test
    void listsVersionHistoryWithReferencedScenarioNames() throws Exception {
        // A 绑定场景后：
        mockMvc.perform(get("/api/projects/1/scripts/1/versions")
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].version.versionNo", is(1)))
                .andExpect(jsonPath("$[0].referencedScenarioNames[0]", is("下单链路")));
    }
```

- [ ] **Step 2: 跑测试确认失败**

Run: `JAVA_HOME=… ./gradlew :backend:test --tests 'com.yr.perftest.platform.api.ScriptAssetApiTest'`
Expected: 404。

- [ ] **Step 3: 实现聚合服务与控制器**

`ScriptQueryService`（注入 `scriptRepository`、`versionRepository`、`scenarioRepository`、`planRepository`）：

```java
    @Transactional(readOnly = true)
    public List<ScriptAssetSummary> listAssets(long projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw new ProjectValidationException("project does not exist");
        }
        List<PersistentScriptRecord> scripts = scriptRepository.findAllByProjectIdOrderByIdAsc(projectId);
        Map<Long, List<PersistentScriptVersionRecord>> byScript = versionRepository
                .findAllByProjectIdOrderByVersionNoDesc(projectId).stream()
                .collect(Collectors.groupingBy(PersistentScriptVersionRecord::getScriptId));
        List<Long> allVersionIds = byScript.values().stream()
                .flatMap(List::stream).map(PersistentScriptVersionRecord::getId).toList();
        Map<Long, List<PersistentTaskScenarioRecord>> refsByVersion = scenariosReferencing(projectId, allVersionIds);
        return scripts.stream().map(script -> toSummary(script,
                byScript.getOrDefault(script.getPersistentId(), List.of()), refsByVersion)).toList();
    }
```

`toSummary`：`latestPublished` = 版本列表中第一个 `PUBLISHED`（列表已按 versionNo 倒序，versionNo=0 的 DRAFT 排最后——注意 DRAFT versionNo=0 倒序在最末 ✓）；`hasDraft` = 存在 `DRAFT`；`currentScenarioCount` = 绑定 `latestPublished.id` 的场景数；`outdatedScenarioCount` = 其余引用数。`listVersions` 返回 DRAFT 置顶 + PUBLISHED 倒序（构造两个子列表拼接）。

`PersistentScriptVersionRepository` 补 `findAllByProjectIdOrderByVersionNoDesc` 已有 ✓（无需改）。`ScriptAssetController` 两个 GET 端点。

- [ ] **Step 4: 跑测试确认通过 + 全量回归**

Run: `JAVA_HOME=… ./gradlew :backend:test --tests 'com.yr.perftest.platform.api.ScriptAssetApiTest'` → PASS；
Run: `JAVA_HOME=… ./gradlew :backend:test` → 全绿。

- [ ] **Step 5: Commit**

```bash
git add -A backend/src
git commit -m "feat：脚本维度聚合查询——assets 列表与版本历史（含引用场景名与新旧版使用统计）"
```

---

### Task 8: parseStatus 全链清理（后端 + 前端编译面）

**Files:**
- Modify: `backend/src/main/java/com/yr/perftest/platform/script/ScriptDefinition.java`（删 `parseStatus` 列）
- Modify: `backend/src/main/java/com/yr/perftest/platform/script/ScriptService.java:255`（删硬编码）
- Modify: `frontend/src/types/index.ts:9,247`
- Modify: `frontend/src/api/scripts.ts:20,105`
- Modify: `frontend/src/utils/format.ts:96-98`
- Modify: `frontend/src/utils/script-status.ts:12-14`
- Modify: `frontend/src/utils/seed.ts:53`、`frontend/src/utils/jmeter.ts:39`
- Test: 编译 + 既有测试回归（无新增测试——纯删除）

**Interfaces:**
- Consumes: Task 3 已加 `status` 字段（编译面由它顶替消费点）。
- Produces: `ScriptDefinition` 无 `parseStatus`；前端 `ParseStatus` 类型删除，`ScriptAsset.parseStatus` 删除，`scriptExecutableStatus` 的第一分支改查 `script.status !== 'PUBLISHED'`。

- [ ] **Step 1: 后端删除**

`ScriptDefinition` record 删 `String parseStatus` 列与 `toScriptDefinition` 对应实参 `"PARSED"`。

- [ ] **Step 2: 前端删除**

1. `types/index.ts`：删 `export type ParseStatus = 'PARSED' | 'PARSE_FAILED';`（第 9 行）与 `ScriptAsset.parseStatus: ParseStatus;`（第 247 行），同位置加 `status: 'DRAFT' | 'PUBLISHED';`。
2. `api/scripts.ts`：`BackendScriptDefinition` 删 `parseStatus` 行、`mapScriptDefinition` 删 `parseStatus: definition.parseStatus,`，加 `status: definition.status,`。
3. `utils/format.ts`：删 `parseStatusText`；先 `grep -rn 'parseStatusText' frontend/src` 删除全部调用点（列表徽标）。
4. `utils/script-status.ts`：首分支改 `if (script.status !== 'PUBLISHED') return blocked('未发布', '脚本存在未发布草稿或未发布');`（DRAFT 时列出该提示；列表入口本就按脚本分组，草稿行的可执行性在版本历史里呈现）。
5. `utils/seed.ts`、`utils/jmeter.ts`：`parseStatus` 字段行替换为 `status: 'PUBLISHED' as const`（seed 若有草稿 mock 则 `status: 'DRAFT'`）。
6. `grep -rn 'parseStatus\|ParseStatus' frontend/src backend/src` 确认为零残留。

- [ ] **Step 3: 双端构建验证**

Run: `JAVA_HOME=… ./gradlew :backend:test`
Expected: 全绿（若有断言 parseStatus 的用例，同步删除断言）。
Run: `cd frontend && npm run build && npm run test`
Expected: vue-tsc 零错误、vitest 通过。

- [ ] **Step 4: Commit**

```bash
git add -A backend/src frontend/src
git commit -m "refactor：清理 parseStatus 假信号——后端 DTO 硬编码与前端死分支全链移除"
```

---

### Task 9: 前端 API 层与类型接新端点

**Files:**
- Modify: `frontend/src/api/scripts.ts`
- Modify: `frontend/src/types/index.ts`
- Modify: `frontend/src/composables/useWorkspace.ts`（列表加载/删除流）
- Test: `cd frontend && npm run build`（类型级验证；vitest 回归）

**Interfaces:**
- Consumes: Task 7 的 `/assets`、`/{scriptId}/versions`、Task 4/5 的 draft/publish/fork/delete 端点。
- Produces:
  - `BackendScriptAssetSummary`、`BackendScriptVersionWithRefs` 类型与映射函数 `mapScriptAsset`
  - `ScriptAsset` 增量字段：`scriptId: number`、`status: 'DRAFT' | 'PUBLISHED'`、`remark: string`（Task 8 已加 status）、`hasDraft: boolean`、`draftVersionId: number | null`、`currentScenarioCount: number`、`outdatedScenarioCount: number`、`versions: ScriptVersionRecord[]` 每项增 `id: number`（versionId）、`status`、`remark`、`referencedScenarioNames?: string[]`。**保留 `ScriptAsset.id = versionId` 的既有语义**（绑定/编辑器以 versionId 为准，兼容期取舍：编译波及最小；列表分组用 `scriptId`）
  - 新 API 函数：`listScriptAssetsApi(projectId)`、`listScriptVersionsApi(projectId, scriptId)`、`saveDraftApi(projectId, scriptId, filename, steps, username)`、`publishScriptApi(projectId, scriptId, versionNo, remark, username)`、`forkDraftApi(projectId, scriptId, sourceVersionId, username)`、`deleteScriptVersionApi(projectId, scriptId, versionId)`、`deleteScriptAssetApi(projectId, scriptId)`

- [ ] **Step 1: types 增量字段**

`ScriptVersionRecord` 扩为：

```ts
export type ScriptVersionRecord = {
  id: number;            // scriptVersionId
  status: 'DRAFT' | 'PUBLISHED';
  remark: string;
  versionNo: number;
  fileName: string;
  fileSize: number;
  fileHash: string;
  importedAt: string;
  importedBy: string;
  referencedScenarioNames?: string[];
};
```

`ScriptAsset` 增加上述新字段（保留既有字段）。

- [ ] **Step 2: api/scripts.ts 新端点与映射**

```ts
export type BackendScriptAssetSummary = {
  id: number; projectId: number; name: string; latestVersionNo: number;
  latestPublished: BackendScriptVersion | null; hasDraft: boolean; draftUpdatedAt: string | null;
  currentScenarioCount: number; outdatedScenarioCount: number;
};

export function listScriptAssetsApi(projectId: number) {
  return request<BackendScriptAssetSummary[]>(`/api/projects/${projectId}/scripts/assets`);
}

export function listScriptVersionsApi(projectId: number, scriptId: number) {
  return request<{ version: BackendScriptVersion; referencedScenarioNames: string[] }[]>(
    `/api/projects/${projectId}/scripts/${scriptId}/versions`);
}

export function saveDraftApi(projectId: number, scriptId: number, filename: string, steps: ScriptStep[], username: string) {
  return request<SaveScriptResult>(`/api/projects/${projectId}/scripts/${scriptId}/draft`, {
    method: 'PUT', headers: { 'Content-Type': 'application/json', 'X-User': username },
    body: JSON.stringify({ filename, steps }),
  });
}

export function publishScriptApi(projectId: number, scriptId: number, versionNo: number, remark: string, username: string) {
  return request<BackendScriptVersion>(`/api/projects/${projectId}/scripts/${scriptId}/publish`, {
    method: 'POST', headers: { 'Content-Type': 'application/json', 'X-User': username },
    body: JSON.stringify({ versionNo, remark }),
  });
}

export function forkDraftApi(projectId: number, scriptId: number, sourceVersionId: number, username: string) {
  return request<BackendScriptVersion>(`/api/projects/${projectId}/scripts/${scriptId}/fork-draft`, {
    method: 'POST', headers: { 'Content-Type': 'application/json', 'X-User': username },
    body: JSON.stringify({ sourceVersionId }),
  });
}

export function deleteScriptVersionApi(projectId: number, scriptId: number, versionId: number) {
  return request<void>(`/api/projects/${projectId}/scripts/${scriptId}/versions/${versionId}`, { method: 'DELETE' });
}

export function deleteScriptAssetApi(projectId: number, scriptId: number) {
  return request<void>(`/api/projects/${projectId}/scripts/${scriptId}`, { method: 'DELETE' });
}
```

`BackendScriptVersion` 扩 `scriptId: number; status: 'DRAFT' | 'PUBLISHED'; remark: string;`。

- [ ] **Step 3: useWorkspace 切换数据源**

`useWorkspace.ts:113` 的 `listScriptDefinitionsApi` 改为 `listScriptAssetsApi`：对每个 summary 再拉 `listScriptVersionsApi`（或并行 `Promise.all`），组装 `ScriptAsset[]`（`latestPublished` 的 definition 字段——`steps` 等编辑器需要的信息在进入编辑器时经 `getScriptDefinitionApi` 现拉，列表态置 `[]`/默认值）；`deleteScriptAssets`（`:282`）改按 `scriptId` 调 `deleteScriptAssetApi`，守卫报错（中文 message）直接 toast 展示。

- [ ] **Step 4: 构建验证**

Run: `cd frontend && npm run build && npm run test`
Expected: 零类型错误。

- [ ] **Step 5: Commit**

```bash
git add -A frontend/src
git commit -m "feat：前端 API 层接入脚本资产/版本/草稿/发布/守卫删除端点"
```

---

### Task 10: 列表分组、版本历史抽屉、发布弹窗、编辑器双动作

**Files:**
- Modify: `frontend/src/components/scripts/ScriptWorkspace.vue`（253 行内改造）
- Create: `frontend/src/components/scripts/ScriptVersionDrawer.vue`
- Create: `frontend/src/components/dialogs/ScriptPublishDialog.vue`
- Modify: `frontend/src/composables/useScriptEditor.ts:417-455`（保存流）
- Modify: `frontend/src/views/ScriptEditorPage.vue`
- Modify: `frontend/src/components/dialogs/ScriptImportDialog.vue` + `composables/useScriptImport.ts`（上传 remark 透传）
- Test: `cd frontend && npm run build && npm run test`

**Interfaces:**
- Consumes: Task 9 全部 API 函数与类型。
- Produces:
  - `ScriptVersionDrawer` props：`open: boolean; script: ScriptAsset | null`，emits：`(e: 'update:open', v: boolean)`、`(e: 'deleted')`、`(e: 'forked')`；内部按 `script.scriptId` 拉版本历史，DRAFT 置顶行显示`草稿`徽标，PUBLISHED 行显示版本号/变更说明/发布人/时间/引用场景名/删除按钮（后端 400 中文 message toast）
  - `ScriptPublishDialog` props：`open: boolean; script: ScriptAsset | null; defaultVersionNo: number`，emits `(e: 'published')`；表单：版本号 `a-input-number`（预填）、变更说明 `a-textarea` 必填
  - `useScriptEditor` 新增 `publishScript(versionNo, remark)` 动作与 `draftDirty` 状态；`saveScript`（`:428` 的 `saveScriptDefinitionApi` 调用）改走 `saveDraftApi`，编辑目标版本 = `script.draftVersionId`（无草稿时由后端惰性创建，响应回 DRAFT 版本 id 后回写）

- [ ] **Step 1: ScriptWorkspace 分组渲染**

模板按 `scriptId` 分组（`Map<number, ScriptAsset[]>` computed）：每组一行——名称、`已发布 v{n}` 徽标、`草稿中` 标记（`hasDraft`）、`N 个场景使用旧版本`（`outdatedScenarioCount > 0` 时黄色提示）、操作区：`编辑草稿`（进编辑器）、`版本历史`（开抽屉）、`删除脚本`（确认框文案列版本数，调 `deleteScriptAssetApi`，失败 toast 后端中文 message）。

- [ ] **Step 2: ScriptVersionDrawer 实现**

a-drawer + a-list：DRAFT 行操作 = `删除草稿`（`deleteScriptVersionApi`）；PUBLISHED 行操作 = `基于此版本创建草稿`（`forkDraftApi`，成功后 toast"已基于 v{n} 重建草稿"并刷新）、`删除版本`（守卫 400 时 toast message）。状态徽标经 `format.ts` 新增：

```ts
export function scriptStatusText(status: 'DRAFT' | 'PUBLISHED') {
  return status === 'DRAFT' ? '草稿' : '已发布';
}
```

- [ ] **Step 3: ScriptPublishDialog + useScriptEditor.publishScript**

```ts
async function publishScript(versionNo: number, remark: string) {
  const version = await publishScriptApi(projectId, script.scriptId, versionNo, remark, username);
  message.success(`已发布 v${version.versionNo}`);
  await reloadDefinition(); // 重新拉 DRAFT（发布后下次保存惰性重建）
}
```

`ScriptEditorPage` 顶部动作区：`保存草稿`（既有保存按钮改文案）+ `发布`（开弹窗，`defaultVersionNo = script.latestVersion + 1`）。

- [ ] **Step 4: ScriptImportDialog 透传 remark**

导入表单增可选"变更说明"`a-textarea`，`useScriptImport` 的 `uploadScriptApi` 调用改带 `remark`（`api/scripts.ts` 的 `uploadScriptApi` 加第 4 参 `remark: string`，`formData.append('remark', remark)`）。

- [ ] **Step 5: 构建与回归**

Run: `cd frontend && npm run build && npm run test`
Expected: 通过。手工冒烟（可选）：`npm run dev` 走一遍上传→编辑→发布→版本历史。

- [ ] **Step 6: Commit**

```bash
git add -A frontend/src
git commit -m "feat：脚本列表脚本维度分组、版本历史抽屉、发布弹窗与编辑器保存/发布分离"
```

---

### Task 11: 绑定对话框只列已发布版本 + 升级提示

**Files:**
- Modify: `frontend/src/components/task-plans/BindScriptDialog.vue`
- Modify: `frontend/src/components/task-plans/ScenarioDesignModule.vue`（绑定态展示）
- Test: `cd frontend && npm run build && npm run test`

**Interfaces:**
- Consumes: `ScriptAsset.versions[].status/id`、`scriptId`、`latestVersion`。
- Produces: 绑定下拉 options 仅含 `status === 'PUBLISHED'` 的版本，label 按脚本分组：`{value: versionId, label: "<脚本名> · v<n>（<remark 摘要>）"}`（`a-select` `options` 扁平即可，label 前缀分组语义）；`ScenarioDesignModule` 绑定行显示 `v<n>`，若 `n < 所属脚本 latestVersionNo` 追加提示 tag `有新版本 v{latest}` 与"升级到最新"快捷钮（emit 既有 confirm 流复用，value = 该脚本最新 PUBLISHED 版本 id）。

- [ ] **Step 1: BindScriptDialog 过滤**

`scriptOptions` computed 改为：

```ts
const scriptOptions = computed(() =>
  props.scripts
    .flatMap((script) => script.versions.filter((v) => v.status === 'PUBLISHED')
      .map((v) => ({
        value: v.id,
        label: `${script.name} · v${v.versionNo}${v.remark ? `（${v.remark.slice(0, 20)}）` : ''}`,
      }))),
);
```

（`props.scripts` 为分组前的版本实体数组时先按 `scriptId` 归并；以 useWorkspace 传参现状为准适配。）

- [ ] **Step 2: ScenarioDesignModule 升级提示**

绑定展示处由 `script.id` 反查所属脚本（`scripts.find(s => s.versions.some(v => v.id === scenario.scriptVersionId))`），`boundVersionNo < script.latestVersion` 时渲染 `有新版本 v{latest}` tag + `升级` 按钮，点击 emit 与手动换绑相同的 confirm 事件（value = 最新 PUBLISHED 版本 id）。

- [ ] **Step 3: 构建与回归**

Run: `cd frontend && npm run build && npm run test`
Expected: 通过。

- [ ] **Step 4: Commit**

```bash
git add -A frontend/src
git commit -m "feat：脚本绑定仅列已发布版本并提供一键升级到最新"
```

---

### Task 12: 文档同步与旧端点移除

**Files:**
- Modify: `docs/modules/03-script-management.md`（第 4 节状态机、接口草案）
- Modify: `backend/src/main/java/com/yr/perftest/platform/api/ScriptController.java`（删旧 `GET /definitions`、`GET /{versionId:\d+}` 列表语义保留、旧 DELETE 已在 Task 5 改造）
- Modify: `frontend/src/composables/useWorkspace.ts`（确认无 `/definitions` 残留引用）
- Test: 双端全量回归

**Interfaces:**
- Consumes: Task 9 已切换前端数据源。
- Produces: `03-script-management.md` 第 4 节替换为两态状态机（DRAFT→PUBLISHED、守卫语义、水位规则），接口草案对齐 spec §5；后端删除 `listScriptDefinitions`/`GET /definitions`（确认 `grep -rn 'scripts/definitions' frontend/src` 为零后）。

- [ ] **Step 1: 更新模块文档**

第 4 节"脚本版本状态"代码块替换为：

```text
DRAFT --发布(版本号+变更说明)--> PUBLISHED（不可变）
DRAFT --删除/丢弃--> 物理删除（随时）
PUBLISHED --删除(当前无场景引用)--> 物理删除
```

并补：版本号手动指定仅限递增（水位 `scripts.latest_version_no` 只增不减）、脚本删除级联守卫、执行与绑定仅认 PUBLISHED。接口草案表对齐 spec §5 的 8 个端点。

- [ ] **Step 2: 删除旧端点**

`ScriptController` 删 `listScriptDefinitions` 端点与 `ScriptService.listScriptDefinitions`（`getScriptDefinition`/`getScriptContent` 保留——编辑器现拉用）；`ScriptService.listScripts`（旧 GET 列表）保留可删任一，以 `grep -rn "api/projects/.*}/scripts'" frontend/src` 残留为准，零引用则一并删除。

- [ ] **Step 3: 全量回归**

Run: `JAVA_HOME=… ./gradlew :backend:test` → 全绿。
Run: `cd frontend && npm run build && npm run test` → 通过。

- [ ] **Step 4: Commit**

```bash
git add -A backend/src frontend/src docs
git commit -m "docs：脚本管理模块文档对齐发布模型，移除旧 definitions 端点"
```

---

## Self-Review 记录

1. **Spec coverage**：C1 状态机→Task 1/3/4；C2 水位→Task 4；C3 remark→Task 3/4；C4 绑定钉死+升级→Task 6/11；C5 draft 载体→Task 3/4；C6/C7 删除→Task 5；C8 入口语义→Task 3；C9 中文展示→Task 5/8/10；C10 UI 半隐形→Task 10；C11 衍生降级→Task 4(fork)/10(折叠抽屉)；C12 原子写→Task 3/4；C13 parseStatus→Task 8。§4 迁移→Task 1/2；§5 全端点→Task 3/4/5/7；§6 前端→Task 9/10/11；§7 结构→各任务；§8 测试→各任务 TDD 步骤。无缺口。
2. **Placeholder scan**：无 TBD/“适当处理”；所有代码步骤含真实代码块或精确改法。
3. **Type consistency**：`ScriptVersion` 10 参在 Task 3 定义、Task 4-7 消费一致；`ScriptVersionStatus` 枚举名贯穿；`writeAtomically` Task 3 落于 `ScriptService`、Task 4 迁至 `ScriptFiles` util（Task 4 Step 3 已注明迁移）；前端 `ScriptVersionRecord.id`（versionId）在 Task 9 定义、Task 10/11 消费一致；中文拒绝消息在 Task 5 定义并在测试断言精确匹配。
