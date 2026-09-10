# 计划版本发布与修订记录 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为任务计划文档建立「版本号 + 修订记录 + 正文快照」的版本体系：发布版本（可覆盖最新版本）、版本列表、查看全文、回滚，修订记录以独立「版本」Tab 展示。

**Architecture:** 后端新增 `plan_versions` 表（Flyway V3）+ JPA 实体/仓储 + `PlanVersionService`（发布规则/列表/详情），挂到既有 `PlanDocumentController`；前端新增 `PublishVersionModal`、`PlanDetailVersions` 两个组件与第五个「版本」Tab，回滚复用 `usePlanDoc.saveDocument` 既有冲突保护链路。内部 `revision` 乐观锁不动，界面不展示。

**Tech Stack:** Spring Boot 3 / Spring Data JPA / Flyway / MySQL（测试 H2 MODE=MySQL）；Vue 3 + ant-design-vue + md-editor-v3；Gradle；Vite。

**Spec:** `docs/superpowers/specs/2026-09-10-plan-version-publish-design.md`（执行前必读，本计划从 spec 论证）

## Global Constraints

- Java 17：所有 Gradle 命令带 `JAVA_HOME=/Users/wangk/Documents/config/jdk-17.0.17+10/Contents/Home/`，gradlew 在仓库根，用 `-p backend`。
- 提交信息全中文、`类型：摘要——正文` 格式（对齐 git log 既有风格）；**不执行 git push**（用户另行指示）。
- 用户可见文案全中文，技术名词（revision/versionNo/snapshot）保留英文。
- curl 冒烟须加 `--noproxy '*'`（本机 7897 代理会拦 localhost 假 502）。
- 工作树已有未提交改动：「执行设置按钮 + revision 徽标移除」（`frontend/src/components/task-plans/TaskPlanDetail.vue`、`PlanDetailDocument.vue`、`frontend/src/styles/plan-module.css`，含 2 张未跟踪冒烟 PNG——PNG 不入库）。
- 仓库根 `.playwright-mcp/`、`docs/superpowers/screenshots/` 均 gitignore，截图/临时产物不提交。
- MCP 版本工具、版本 diff、审批流为**明确不做**（spec §8），不要顺手实现。
- 发布权限口径（spec §3.1「任意阶段·EDIT 即可」的服务端解释）：`ProjectAccessResolver.resolve(...) == NONE` 拒绝，其余角色一律允许——**不**使用 `PlanAccess.compute` 的阶段相关 EDIT（那会禁止非草稿阶段发布，与 spec 冲突）。
- 错误提示沿用 `PlanValidationException`（默认 400，body code 统一 PLAN_INVALID），具体错误用 message 前缀区分：`PLAN_VERSION_INVALID` / `PLAN_VERSION_NOT_LATEST` / `PLAN_VERSION_DUPLICATE`。

---

### Task 1: 后端数据层——Flyway V3 + 实体 + 仓储

**Files:**
- Create: `backend/src/main/resources/db/migration/V3__plan_versions.sql`
- Create: `backend/src/main/java/com/yr/perftest/platform/task/plandoc/PersistentPlanVersionRecord.java`
- Create: `backend/src/main/java/com/yr/perftest/platform/task/plandoc/PersistentPlanVersionRepository.java`
- Test: `backend/src/test/java/com/yr/perftest/platform/task/plandoc/PlanVersionRepositoryTest.java`

**Interfaces:**
- Consumes: 无（数据层起点）。
- Produces: `PersistentPlanVersionRecord`（JPA 实体，含 `applyOverwrite(String changeNote, String author, String planPhase, int planRevision, Instant updatedAt)`）、`PersistentPlanVersionRepository extends JpaRepository<PersistentPlanVersionRecord, Long>`（`findByPlanIdOrderByCreatedAtDescIdDesc(Long planId)`、`existsByPlanIdAndVersionNo(Long planId, String versionNo)`）。Task 2/3 直接依赖。

- [ ] **Step 0: 预提交工作树存量改动**

工作树里「执行设置按钮 + revision 徽标移除」与本功能无关但同文件交织，先独立入库（PNG 不要 add）：

```bash
cd /Users/wangk/Documents/Git/performance-test-platform
git add frontend/src/components/task-plans/TaskPlanDetail.vue \
        frontend/src/components/task-plans/PlanDetailDocument.vue \
        frontend/src/styles/plan-module.css
git commit -m "refactor：移除文档工具条「执行设置」按钮与 revision 徽标——预检设置抽屉整条死代码链（openPrecheck/savePrecheck/updatePrecheckSettingsApi 接入、相关样式）一并清除，内部 revision 退化为纯乐观锁令牌不再界面展示（版本体系前置改动，spec 2026-09-10）"
```

- [ ] **Step 1: 写 Flyway 迁移**

`backend/src/main/resources/db/migration/V3__plan_versions.sql`：

```sql
CREATE TABLE `plan_versions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `plan_id` bigint NOT NULL,
  `version_no` varchar(32) NOT NULL,
  `change_note` varchar(1000) NOT NULL,
  `created_by` varchar(80) NOT NULL,
  `author` varchar(80) NOT NULL,
  `snapshot_body` ${lob_type} NOT NULL,
  `plan_phase` varchar(20) NOT NULL,
  `plan_revision` int NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE (`plan_id`,`version_no`)
);
```

（`${lob_type}`：MySQL 侧 longtext、H2 测试侧 clob，占位符由 `application.yml` 提供，与 V1 的 `plan_publish_snapshots` 同法。）

- [ ] **Step 2: 写实体**

`backend/src/main/java/com/yr/perftest/platform/task/plandoc/PersistentPlanVersionRecord.java`（仿 `PersistentPlanPublishSnapshotRecord` 的 protected 构造 + 全参构造风格）：

```java
package com.yr.perftest.platform.task.plandoc;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/** 计划版本：发布时冻结的正文快照 + 修订记录（spec 2026-09-10 §2）。 */
@Entity
@Table(name = "plan_versions", uniqueConstraints = @UniqueConstraint(columnNames = {"planId", "versionNo"}))
public class PersistentPlanVersionRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long planId;

    @Column(nullable = false, length = 32)
    private String versionNo;

    @Column(nullable = false, length = 1000)
    private String changeNote;

    @Column(nullable = false, length = 80)
    private String createdBy;

    @Column(nullable = false, length = 80)
    private String author;

    @Lob
    @Column(nullable = false)
    private String snapshotBody;

    @Column(nullable = false, length = 20)
    private String planPhase;

    @Column(nullable = false)
    private int planRevision;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected PersistentPlanVersionRecord() {
    }

    public PersistentPlanVersionRecord(Long planId, String versionNo, String changeNote, String createdBy,
                                       String author, String snapshotBody, String planPhase, int planRevision,
                                       Instant createdAt) {
        this.planId = planId;
        this.versionNo = versionNo;
        this.changeNote = changeNote;
        this.createdBy = createdBy;
        this.author = author;
        this.snapshotBody = snapshotBody;
        this.planPhase = planPhase;
        this.planRevision = planRevision;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    /** 覆盖发布（版本号不变的小修复）：刷新快照关联的修订元数据，createdBy/createdAt 不变。 */
    public void applyOverwrite(String changeNote, String author, String planPhase, int planRevision, Instant updatedAt) {
        this.changeNote = changeNote;
        this.author = author;
        this.planPhase = planPhase;
        this.planRevision = planRevision;
        this.updatedAt = updatedAt;
    }

    public Long getId() { return id; }
    public Long getPlanId() { return planId; }
    public String getVersionNo() { return versionNo; }
    public String getChangeNote() { return changeNote; }
    public String getCreatedBy() { return createdBy; }
    public String getAuthor() { return author; }
    public String getSnapshotBody() { return snapshotBody; }
    public String getPlanPhase() { return planPhase; }
    public int getPlanRevision() { return planRevision; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
```

- [ ] **Step 3: 写仓储**

`backend/src/main/java/com/yr/perftest/platform/task/plandoc/PersistentPlanVersionRepository.java`：

```java
package com.yr.perftest.platform.task.plandoc;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PersistentPlanVersionRepository extends JpaRepository<PersistentPlanVersionRecord, Long> {
    /** 发布时间倒序（版本号为手输字符串，不做字典序排序）；同刻以 id 兜底稳定序。 */
    List<PersistentPlanVersionRecord> findByPlanIdOrderByCreatedAtDescIdDesc(Long planId);

    boolean existsByPlanIdAndVersionNo(Long planId, String versionNo);
}
```

- [ ] **Step 4: 写仓储测试**

`backend/src/test/java/com/yr/perftest/platform/task/plandoc/PlanVersionRepositoryTest.java`：

```java
package com.yr.perftest.platform.task.plandoc;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:plan-version-repo-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.h2.console.enabled=false"
})
class PlanVersionRepositoryTest {

    @Autowired
    private PersistentPlanVersionRepository repository;

    @Test
    void saveAndQueryOrderByCreatedAtDesc() {
        repository.save(new PersistentPlanVersionRecord(1L, "V1.0", "首版", "owner", "owner",
                "正文一", "DRAFT", 1, Instant.parse("2026-09-10T01:00:00Z")));
        repository.save(new PersistentPlanVersionRecord(1L, "V1.1", "补充指标", "owner", "owner",
                "正文二", "DRAFT", 2, Instant.parse("2026-09-10T02:00:00Z")));

        List<PersistentPlanVersionRecord> versions = repository.findByPlanIdOrderByCreatedAtDescIdDesc(1L);
        assertThat(versions).extracting(PersistentPlanVersionRecord::getVersionNo)
                .containsExactly("V1.1", "V1.0");
        assertThat(repository.existsByPlanIdAndVersionNo(1L, "V1.0")).isTrue();
        assertThat(repository.existsByPlanIdAndVersionNo(1L, "V9.9")).isFalse();
    }

    @Test
    void duplicateVersionNoWithinPlanRejected() {
        repository.save(new PersistentPlanVersionRecord(2L, "V1.0", "首版", "owner", "owner",
                "正文", "DRAFT", 1, Instant.now()));
        assertThatThrownBy(() -> repository.saveAndFlush(new PersistentPlanVersionRecord(2L, "V1.0", "重号",
                "owner", "owner", "正文二", "DRAFT", 2, Instant.now())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
```

- [ ] **Step 5: 跑测试验证通过**

```bash
cd /Users/wangk/Documents/Git/performance-test-platform
JAVA_HOME=/Users/wangk/Documents/config/jdk-17.0.17+10/Contents/Home/ ./gradlew -p backend test --tests "com.yr.perftest.platform.task.plandoc.PlanVersionRepositoryTest"
```

Expected: PASS（2 个测试）。若 validate 失败，核对实体字段长度/类型与 DDL 是否一致。

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/resources/db/migration/V3__plan_versions.sql \
        backend/src/main/java/com/yr/perftest/platform/task/plandoc/PersistentPlanVersionRecord.java \
        backend/src/main/java/com/yr/perftest/platform/task/plandoc/PersistentPlanVersionRepository.java \
        backend/src/test/java/com/yr/perftest/platform/task/plandoc/PlanVersionRepositoryTest.java
git commit -m "feat：plan_versions 版本表数据层——Flyway V3（唯一键 plan_id+version_no，snapshot_body 用 lob_type 占位符）+ PersistentPlanVersionRecord 实体（applyOverwrite 支持覆盖发布）+ 仓储（createdAt 倒序查询），配套仓储测试"
```

---

### Task 2: 后端服务——PlanVersionService 发布规则/列表/详情

**Files:**
- Create: `backend/src/main/java/com/yr/perftest/platform/task/plandoc/PlanVersionService.java`
- Test: `backend/src/test/java/com/yr/perftest/platform/task/plandoc/PlanVersionServiceTest.java`

**Interfaces:**
- Consumes: Task 1 的 `PersistentPlanVersionRepository`/`PersistentPlanVersionRecord`；既有 `PersistentTaskPlanRepository.findWithLockingById`、`ProjectAccessResolver.resolve(projectId, principal, createdBy)`（返回 `PlanActorRole`，NONE=非成员）、`HumanPrincipal.username()`、`PlanValidationException`、`PlanAccessDeniedException`。
- Produces（Task 3 控制器直接依赖）：
  - `record PlanVersionView(long id, String versionNo, String changeNote, String createdBy, String author, String planPhase, int planRevision, Instant createdAt, Instant updatedAt)`
  - `record PlanVersionDetail(long id, String versionNo, String changeNote, String createdBy, String author, String planPhase, int planRevision, Instant createdAt, Instant updatedAt, String snapshotBody)`
  - `record PlanVersionListResponse(List<PlanVersionView> versions, boolean bodyDiffersFromLatest)`
  - `PlanVersionView publish(long planId, HumanPrincipal actor, String versionNo, String changeNote)`
  - `PlanVersionListResponse list(long planId, HumanPrincipal actor)`
  - `PlanVersionDetail get(long planId, long versionId, HumanPrincipal actor)`
  - `static Integer compareVersionNumbers(String left, String right)` —— 可比较时返回逐段比较结果（负/零/正），任一侧非 `v?数字[.数字]*` 形态返回 null。

- [ ] **Step 1: 写失败的服务测试**

`backend/src/test/java/com/yr/perftest/platform/task/plandoc/PlanVersionServiceTest.java`（种子搭建仿 `PlanDocumentServiceTest`）：

```java
package com.yr.perftest.platform.task.plandoc;

import com.yr.perftest.platform.identity.HumanPrincipal;
import com.yr.perftest.platform.identity.SystemRole;
import com.yr.perftest.platform.project.PersistentProjectMemberRecord;
import com.yr.perftest.platform.project.PersistentProjectRecord;
import com.yr.perftest.platform.project.PersistentProjectMemberRepository;
import com.yr.perftest.platform.project.PersistentProjectRepository;
import com.yr.perftest.platform.project.ProjectRole;
import com.yr.perftest.platform.task.PersistentTaskPlanRecord;
import com.yr.perftest.platform.task.PersistentTaskPlanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:plan-version-service-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.h2.console.enabled=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class PlanVersionServiceTest {

    private static final HumanPrincipal OWNER = new HumanPrincipal("owner", java.util.Set.of(SystemRole.PROJECT_MEMBER));
    private static final HumanPrincipal COLLEAGUE = new HumanPrincipal("member-b", java.util.Set.of(SystemRole.PROJECT_MEMBER));
    private static final HumanPrincipal OUTSIDER = new HumanPrincipal("stranger", java.util.Set.of(SystemRole.PROJECT_MEMBER));

    @Autowired
    private PlanVersionService versionService;
    @Autowired
    private PersistentTaskPlanRepository planRepository;
    @Autowired
    private PersistentProjectRepository projectRepository;
    @Autowired
    private PersistentProjectMemberRepository memberRepository;

    private long planId;

    @BeforeEach
    void setUp() {
        PersistentProjectRecord project = projectRepository.save(
                new PersistentProjectRecord("P1", "项目一", "", "owner"));
        memberRepository.save(new PersistentProjectMemberRecord(project.getId(), "owner", ProjectRole.OWNER));
        memberRepository.save(new PersistentProjectMemberRecord(project.getId(), "member-b", ProjectRole.MEMBER));
        PersistentTaskPlanRecord plan = planRepository.save(
                new PersistentTaskPlanRecord(project.getId(), "计划一", null, "owner"));
        plan.updateBody("## 一、背景\n\n初始内容\n");
        planId = planRepository.save(plan).getId();
    }

    @Test
    void firstPublishFreezesBodyWithMeta() {
        PlanVersionService.PlanVersionView view = versionService.publish(planId, OWNER, "V1.0", "首版发布");
        assertThat(view.versionNo()).isEqualTo("V1.0");
        assertThat(view.createdBy()).isEqualTo("owner");
        assertThat(view.author()).isEqualTo("owner");
        assertThat(view.planPhase()).isEqualTo("DRAFT");

        PlanVersionService.PlanVersionDetail detail = versionService.get(planId, view.id(), OWNER);
        assertThat(detail.snapshotBody()).contains("初始内容");
    }

    @Test
    void sameVersionNoOverwritesLatestKeepsCreatedBy() {
        PlanVersionService.PlanVersionView first = versionService.publish(planId, OWNER, "V1.0", "首版");
        planRepository.findById(planId).ifPresent(plan -> {
            plan.updateBody("## 一、背景\n\n小修错别字\n");
            planRepository.save(plan);
        });

        PlanVersionService.PlanVersionView overwritten =
                versionService.publish(planId, COLLEAGUE, "V1.0", "错别字小修，不升号");

        assertThat(overwritten.id()).isEqualTo(first.id());
        assertThat(overwritten.author()).isEqualTo("member-b");
        assertThat(overwritten.createdBy()).isEqualTo("owner");
        assertThat(overwritten.changeNote()).isEqualTo("错别字小修，不升号");
        PlanVersionService.PlanVersionListResponse list = versionService.list(planId, OWNER);
        assertThat(list.versions()).hasSize(1);
        assertThat(list.bodyDiffersFromLatest()).isFalse();
    }

    @Test
    void lowerThanLatestRejectedIncludingSegmentCompare() {
        versionService.publish(planId, OWNER, "V1.10", "先发高版本");
        assertThatThrownBy(() -> versionService.publish(planId, OWNER, "V1.9", "倒退"))
                .isInstanceOf(PlanValidationException.class)
                .hasMessageContaining("PLAN_VERSION_NOT_LATEST");
        assertThatThrownBy(() -> versionService.publish(planId, OWNER, "V1.0", "更早"))
                .isInstanceOf(PlanValidationException.class)
                .hasMessageContaining("PLAN_VERSION_NOT_LATEST");
    }

    @Test
    void duplicateNonLatestFreeTextRejected() {
        versionService.publish(planId, OWNER, "final", "首版");
        versionService.publish(planId, OWNER, "V2.0", "升版");
        assertThatThrownBy(() -> versionService.publish(planId, OWNER, "final", "重号"))
                .isInstanceOf(PlanValidationException.class)
                .hasMessageContaining("PLAN_VERSION_DUPLICATE");
    }

    @Test
    void blankVersionNoOrNoteRejected() {
        assertThatThrownBy(() -> versionService.publish(planId, OWNER, " ", "note"))
                .isInstanceOf(PlanValidationException.class)
                .hasMessageContaining("PLAN_VERSION_INVALID");
        assertThatThrownBy(() -> versionService.publish(planId, OWNER, "V1.0", " "))
                .isInstanceOf(PlanValidationException.class)
                .hasMessageContaining("PLAN_VERSION_INVALID");
    }

    @Test
    void listOrdersByCreatedDescAndReportsDirtyFlag() {
        versionService.publish(planId, OWNER, "V1.0", "首版");
        planRepository.findById(planId).ifPresent(plan -> {
            plan.updateBody("## 一、背景\n\n改动未发布\n");
            planRepository.save(plan);
        });
        PlanVersionService.PlanVersionListResponse list = versionService.list(planId, OWNER);
        assertThat(list.versions()).extracting(PlanVersionService.PlanVersionView::versionNo)
                .containsExactly("V1.0");
        assertThat(list.bodyDiffersFromLatest()).isTrue();
    }

    @Test
    void nonProjectMemberDenied() {
        assertThatThrownBy(() -> versionService.list(planId, OUTSIDER))
                .isInstanceOf(PlanAccessDeniedException.class);
    }

    @Test
    void compareVersionNumbersSegmentsAndIncomparable() {
        assertThat(PlanVersionService.compareVersionNumbers("V1.9", "V1.10")).isNegative();
        assertThat(PlanVersionService.compareVersionNumbers("v2.0", "V1.9")).isPositive();
        assertThat(PlanVersionService.compareVersionNumbers("V1", "1.0.0")).isZero();
        assertThat(PlanVersionService.compareVersionNumbers("final", "V1.0")).isNull();
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

```bash
JAVA_HOME=/Users/wangk/Documents/config/jdk-17.0.17+10/Contents/Home/ ./gradlew -p backend test --tests "com.yr.perftest.platform.task.plandoc.PlanVersionServiceTest"
```

Expected: 编译失败（`PlanVersionService` 不存在）。

- [ ] **Step 3: 实现服务**

`backend/src/main/java/com/yr/perftest/platform/task/plandoc/PlanVersionService.java`：

```java
package com.yr.perftest.platform.task.plandoc;

import com.yr.perftest.platform.identity.HumanPrincipal;
import com.yr.perftest.platform.project.ProjectAccessResolver;
import com.yr.perftest.platform.task.PersistentTaskPlanRecord;
import com.yr.perftest.platform.task.PersistentTaskPlanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * 计划版本发布与修订记录（spec 2026-09-10）。
 * 发布规则：版本号=最新 → 覆盖最新（前端已二次确认）；数值低于最新 → 拒绝；
 * 重号但非最新 → 拒绝（覆盖仅限最新版本）；其余 → 新建。
 * 权限：项目成员即可读；发布沿用同口径（spec「任意阶段·EDIT 即可」，不按阶段收敛）。
 */
@Service
public class PlanVersionService {
    private final PersistentTaskPlanRepository planRepository;
    private final PersistentPlanVersionRepository versionRepository;
    private final ProjectAccessResolver accessResolver;

    public PlanVersionService(PersistentTaskPlanRepository planRepository,
                              PersistentPlanVersionRepository versionRepository,
                              ProjectAccessResolver accessResolver) {
        this.planRepository = planRepository;
        this.versionRepository = versionRepository;
        this.accessResolver = accessResolver;
    }

    public record PlanVersionView(long id, String versionNo, String changeNote, String createdBy, String author,
                                  String planPhase, int planRevision, Instant createdAt, Instant updatedAt) {
    }

    public record PlanVersionDetail(long id, String versionNo, String changeNote, String createdBy, String author,
                                    String planPhase, int planRevision, Instant createdAt, Instant updatedAt,
                                    String snapshotBody) {
    }

    public record PlanVersionListResponse(List<PlanVersionView> versions, boolean bodyDiffersFromLatest) {
    }

    @Transactional
    public PlanVersionView publish(long planId, HumanPrincipal actor, String versionNo, String changeNote) {
        PersistentTaskPlanRecord plan = planRepository.findWithLockingById(planId)
                .orElseThrow(() -> new PlanValidationException("PLAN_INVALID：task plan does not exist"));
        requireReadable(plan, actor);
        String no = versionNo == null ? "" : versionNo.trim();
        String note = changeNote == null ? "" : changeNote.trim();
        if (no.isEmpty()) {
            throw new PlanValidationException("PLAN_VERSION_INVALID：版本号不能为空");
        }
        if (note.isEmpty()) {
            throw new PlanValidationException("PLAN_VERSION_INVALID：变更内容不能为空");
        }
        List<PersistentPlanVersionRecord> existing = versionRepository.findByPlanIdOrderByCreatedAtDescIdDesc(planId);
        if (!existing.isEmpty()) {
            PersistentPlanVersionRecord latest = existing.get(0);
            if (latest.getVersionNo().equals(no)) {
                latest.applyOverwrite(note, actor.username(), plan.getPhase().name(), plan.getRevision(), Instant.now());
                return toView(versionRepository.save(latest));
            }
            Integer comparison = compareVersionNumbers(no, latest.getVersionNo());
            if (comparison != null && comparison < 0) {
                throw new PlanValidationException("PLAN_VERSION_NOT_LATEST：版本号低于最新版本 "
                        + latest.getVersionNo() + "，不允许提交");
            }
            if (versionRepository.existsByPlanIdAndVersionNo(planId, no)) {
                throw new PlanValidationException("PLAN_VERSION_DUPLICATE：版本号已被历史版本使用，仅最新版本可覆盖更新");
            }
        }
        PersistentPlanVersionRecord created = new PersistentPlanVersionRecord(planId, no, note,
                actor.username(), actor.username(), plan.getBody() == null ? "" : plan.getBody(),
                plan.getPhase().name(), plan.getRevision(), Instant.now());
        return toView(versionRepository.save(created));
    }

    @Transactional(readOnly = true)
    public PlanVersionListResponse list(long planId, HumanPrincipal actor) {
        PersistentTaskPlanRecord plan = requirePlan(planId);
        requireReadable(plan, actor);
        List<PersistentPlanVersionRecord> versions = versionRepository.findByPlanIdOrderByCreatedAtDescIdDesc(planId);
        boolean dirty = false;
        if (!versions.isEmpty()) {
            String latestBody = versions.get(0).getSnapshotBody();
            dirty = !latestBody.equals(plan.getBody() == null ? "" : plan.getBody());
        }
        return new PlanVersionListResponse(versions.stream().map(this::toView).toList(), dirty);
    }

    @Transactional(readOnly = true)
    public PlanVersionDetail get(long planId, long versionId, HumanPrincipal actor) {
        PersistentTaskPlanRecord plan = requirePlan(planId);
        requireReadable(plan, actor);
        PersistentPlanVersionRecord version = versionRepository.findById(versionId)
                .filter(v -> v.getPlanId() == planId)
                .orElseThrow(() -> new PlanValidationException("PLAN_VERSION_INVALID：版本不存在"));
        return new PlanVersionDetail(version.getId(), version.getVersionNo(), version.getChangeNote(),
                version.getCreatedBy(), version.getAuthor(), version.getPlanPhase(), version.getPlanRevision(),
                version.getCreatedAt(), version.getUpdatedAt(), version.getSnapshotBody());
    }

    /** 数值版本比较：`v?数字[.数字]*` 逐段比较（缺段补 0，V1 == 1.0）；任一侧不合规返回 null（不可比）。 */
    static Integer compareVersionNumbers(String left, String right) {
        int[] a = parseSegments(left);
        int[] b = parseSegments(right);
        if (a == null || b == null) {
            return null;
        }
        int length = Math.max(a.length, b.length);
        for (int i = 0; i < length; i++) {
            int x = i < a.length ? a[i] : 0;
            int y = i < b.length ? b[i] : 0;
            if (x != y) {
                return Integer.compare(x, y);
            }
        }
        return 0;
    }

    private static int[] parseSegments(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() > 1 && (normalized.charAt(0) == 'v' || normalized.charAt(0) == 'V')) {
            normalized = normalized.substring(1);
        }
        if (normalized.isEmpty()) {
            return null;
        }
        String[] parts = normalized.split("\\.");
        int[] segments = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            if (!parts[i].matches("\\d+")) {
                return null;
            }
            segments[i] = Integer.parseInt(parts[i]);
        }
        return segments;
    }

    private PersistentTaskPlanRecord requirePlan(long planId) {
        return planRepository.findById(planId)
                .orElseThrow(() -> new PlanValidationException("PLAN_INVALID：task plan does not exist"));
    }

    private void requireReadable(PersistentTaskPlanRecord plan, HumanPrincipal actor) {
        ProjectAccessResolver.PlanActorRole role =
                accessResolver.resolve(plan.getProjectId(), actor, plan.getCreatedBy());
        if (role == ProjectAccessResolver.PlanActorRole.NONE) {
            throw new PlanAccessDeniedException("PLAN_ACCESS_DENIED：非项目成员");
        }
    }

    private PlanVersionView toView(PersistentPlanVersionRecord version) {
        return new PlanVersionView(version.getId(), version.getVersionNo(), version.getChangeNote(),
                version.getCreatedBy(), version.getAuthor(), version.getPlanPhase(), version.getPlanRevision(),
                version.getCreatedAt(), version.getUpdatedAt());
    }
}
```

- [ ] **Step 4: 跑测试验证通过**

```bash
JAVA_HOME=/Users/wangk/Documents/config/jdk-17.0.17+10/Contents/Home/ ./gradlew -p backend test --tests "com.yr.perftest.platform.task.plandoc.PlanVersionServiceTest"
```

Expected: PASS（8 个测试）。注意 `duplicateVersionNoWithinPlanRejected` 若不抛 `DataIntegrityViolationException`，检查 H2 唯一键与实体 `@UniqueConstraint` 列名映射。

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/yr/perftest/platform/task/plandoc/PlanVersionService.java \
        backend/src/test/java/com/yr/perftest/platform/task/plandoc/PlanVersionServiceTest.java
git commit -m "feat：PlanVersionService 版本发布规则——同号覆盖最新（created_by/created_at 保留、author/快照/变更内容刷新）、数值低于最新拒绝（逐段比较 V1.9<V1.10）、自由文本重号拒绝、必填校验；列表按发布时间倒序并带 bodyDiffersFromLatest 未发布标记；成员可读、非成员拒绝"
```

---

### Task 3: 后端 REST 接入

**Files:**
- Modify: `backend/src/main/java/com/yr/perftest/platform/api/PlanDocumentController.java`（构造器注入 + 3 个端点 + 1 个 record）

**Interfaces:**
- Consumes: Task 2 的 `PlanVersionService`（`publish/list/get` 及三个视图 record）。
- Produces: `GET /api/task-plans/{planId}/versions`、`GET /api/task-plans/{planId}/versions/{versionId}`、`POST /api/task-plans/{planId}/versions`（201）。Task 4 前端按此对接。

- [ ] **Step 1: 控制器注入服务**

在 `PlanDocumentController` 字段区与构造器追加（对齐既有构造器注入风格）：

```java
    private final PlanVersionService versionService;

// 构造器参数追加 PlanVersionService versionService，并赋值 this.versionService = versionService;
```

import 区补：

```java
import com.yr.perftest.platform.task.plandoc.PlanVersionService;
```

- [ ] **Step 2: 加三个端点**

放在既有 `listSnapshots` 端点之后（快照端点旁，语义相邻）：

```java
    @GetMapping("/task-plans/{planId}/versions")
    public PlanVersionService.PlanVersionListResponse listVersions(@PathVariable long planId) {
        return versionService.list(planId, requireHuman());
    }

    @GetMapping("/task-plans/{planId}/versions/{versionId}")
    public PlanVersionService.PlanVersionDetail getVersion(
            @PathVariable long planId, @PathVariable long versionId) {
        return versionService.get(planId, versionId, requireHuman());
    }

    @PostMapping("/task-plans/{planId}/versions")
    @ResponseStatus(HttpStatus.CREATED)
    public PlanVersionService.PlanVersionView publishVersion(
            @PathVariable long planId, @RequestBody PublishVersionRequest request) {
        return versionService.publish(planId, requireHuman(), request.versionNo(), request.changeNote());
    }
```

请求 record 追加到文件尾的 record 区：

```java
    public record PublishVersionRequest(String versionNo, String changeNote) {
    }
```

- [ ] **Step 3: 全量后端测试 + 编译验证**

```bash
JAVA_HOME=/Users/wangk/Documents/config/jdk-17.0.17+10/Contents/Home/ ./gradlew -p backend test
```

Expected: 全绿（既有 459+ 用例无回归，新增 10 个）。

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/yr/perftest/platform/api/PlanDocumentController.java
git commit -m "feat：版本发布/列表/详情 REST 接入——GET/POST /api/task-plans/{id}/versions 与 GET .../versions/{versionId}，鉴权走 requireHuman + 服务层成员门禁，发布返回 201"
```

---

### Task 4: 前端类型 + API

**Files:**
- Modify: `frontend/src/types/index.ts`（`PlanSnapshotView` 附近追加）
- Modify: `frontend/src/api/plan-doc.ts`

**Interfaces:**
- Consumes: Task 3 的三个端点；`request<T>`（`./http`）。
- Produces（Task 5/6 组件依赖）：
  - `interface PlanVersionView { id: number; versionNo: string; changeNote: string; createdBy: string; author: string; planPhase: string; planRevision: number; createdAt: string; updatedAt: string; }`
  - `interface PlanVersionListResponse { versions: PlanVersionView[]; bodyDiffersFromLatest: boolean; }`
  - `interface PlanVersionDetail extends PlanVersionView { snapshotBody: string; }`
  - `listPlanVersionsApi(planId: number): Promise<PlanVersionListResponse>`
  - `getPlanVersionApi(planId: number, versionId: number): Promise<PlanVersionDetail>`
  - `publishPlanVersionApi(planId: number, payload: { versionNo: string; changeNote: string }): Promise<PlanVersionView>`

- [ ] **Step 1: 类型定义**

`frontend/src/types/index.ts` 在 `PlanSnapshotView` 之后追加：

```ts
export interface PlanVersionView {
  id: number;
  versionNo: string;
  changeNote: string;
  createdBy: string;
  author: string;
  planPhase: string;
  planRevision: number;
  createdAt: string;
  updatedAt: string;
}

export interface PlanVersionListResponse {
  versions: PlanVersionView[];
  bodyDiffersFromLatest: boolean;
}

export interface PlanVersionDetail extends PlanVersionView {
  snapshotBody: string;
}
```

- [ ] **Step 2: API 函数**

`frontend/src/api/plan-doc.ts`：import type 区补 `PlanVersionDetail, PlanVersionListResponse, PlanVersionView`，函数区追加：

```ts
export function listPlanVersionsApi(planId: number) {
  return request<PlanVersionListResponse>(`/api/task-plans/${planId}/versions`);
}

export function getPlanVersionApi(planId: number, versionId: number) {
  return request<PlanVersionDetail>(`/api/task-plans/${planId}/versions/${versionId}`);
}

export function publishPlanVersionApi(planId: number, payload: { versionNo: string; changeNote: string }) {
  return request<PlanVersionView>(`/api/task-plans/${planId}/versions`, {
    method: 'POST',
    headers: json,
    body: JSON.stringify(payload),
  });
}
```

- [ ] **Step 3: 构建验证**

```bash
cd frontend && npm run build
```

Expected: vue-tsc + vite 构建绿。

- [ ] **Step 4: Commit**

```bash
git add frontend/src/types/index.ts frontend/src/api/plan-doc.ts
git commit -m "feat：版本体系前端类型与 API——PlanVersionView/ListResponse/Detail 与 list/get/publish 三个接口封装"
```

---

### Task 5: 发布版本弹窗 PublishVersionModal.vue

**Files:**
- Create: `frontend/src/components/task-plans/PublishVersionModal.vue`

**Interfaces:**
- Consumes: Task 4 的 `listPlanVersionsApi`/`publishPlanVersionApi`/`PlanVersionView`。
- Produces: props `{ open: boolean; planId: number }`；emits `update:open`、`published`（发布成功后触发，父级刷新版本列表）。Task 6 在 TaskPlanDetail 挂载。

- [ ] **Step 1: 组件实现**

```vue
<template>
  <a-modal
    :open="open"
    title="发布版本"
    :width="520"
    ok-text="发布"
    cancel-text="取消"
    :confirm-loading="submitting"
    @cancel="close"
    @ok="submit"
  >
    <a-form layout="vertical" class="version-publish-form">
      <a-form-item label="版本号" required>
        <a-input
          v-model:value="versionNo"
          placeholder="如：V1.0"
          :maxlength="32"
          aria-label="版本号"
        />
      </a-form-item>
      <a-form-item label="变更内容" required>
        <a-textarea
          v-model:value="changeNote"
          :rows="4"
          :maxlength="1000"
          show-count
          placeholder="本次发布包含哪些变更"
          aria-label="变更内容"
        />
      </a-form-item>
    </a-form>
    <p class="version-publish-hint">
      发布将冻结当前文档全文作为版本快照，修订人自动记录为当前用户。
      <template v-if="latest">版本号与最新版本相同（{{ latest.versionNo }}）时将覆盖该版本的快照与修订记录。</template>
    </p>
  </a-modal>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue';
import { message, Modal } from 'ant-design-vue';
import { listPlanVersionsApi, publishPlanVersionApi } from '../../api/plan-doc';
import type { PlanVersionView } from '../../types';

/**
 * 发布版本弹窗（spec 2026-09-10 §3.2/§3.3）：
 * 版本号默认显示最新版本号（无版本时留空）；与最新同号 → 确认告警后覆盖；
 * 低于最新/历史重号由服务端拒绝，错误信息直接透出。
 */
const props = defineProps<{ open: boolean; planId: number }>();
const emit = defineEmits<{
  (e: 'update:open', value: boolean): void;
  (e: 'published'): void;
}>();

const versionNo = ref('');
const changeNote = ref('');
const latest = ref<PlanVersionView | null>(null);
const submitting = ref(false);

watch(() => props.open, (open) => {
  if (open) void reset();
});

async function reset() {
  versionNo.value = '';
  changeNote.value = '';
  latest.value = null;
  const response = await listPlanVersionsApi(props.planId).catch(() => null);
  if (response && response.versions.length) {
    latest.value = response.versions[0];
    versionNo.value = latest.value.versionNo;
  }
}

function close() {
  emit('update:open', false);
}

async function submit() {
  const no = versionNo.value.trim();
  const note = changeNote.value.trim();
  if (!no) {
    message.warning('请填写版本号');
    return;
  }
  if (!note) {
    message.warning('请填写变更内容');
    return;
  }
  const doPublish = async () => {
    submitting.value = true;
    try {
      await publishPlanVersionApi(props.planId, { versionNo: no, changeNote: note });
      message.success(latest.value && no === latest.value.versionNo ? `版本 ${no} 已覆盖更新` : `版本 ${no} 已发布`);
      emit('published');
      close();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '发布失败');
    } finally {
      submitting.value = false;
    }
  };
  if (latest.value && no === latest.value.versionNo) {
    Modal.confirm({
      title: `覆盖版本 ${no}？`,
      content: '版本号与最新版本相同，发布将覆盖该版本的快照与修订记录。',
      okText: '覆盖发布',
      cancelText: '再想想',
      onOk: doPublish,
    });
    return;
  }
  await doPublish();
}
</script>

<style scoped>
.version-publish-form {
  margin-bottom: 4px;
}

.version-publish-hint {
  margin: 0;
  font-size: 12px;
  color: var(--muted);
  line-height: 1.7;
}
</style>
```

- [ ] **Step 2: 构建验证**

```bash
cd frontend && npm run build
```

Expected: 绿（组件暂未被引用，vue-tsc 不检查未引用 Vue 文件的模板运行时，但会编译其类型）。

- [ ] **Step 3: Commit**

```bash
git add frontend/src/components/task-plans/PublishVersionModal.vue
git commit -m "feat：发布版本弹窗组件——版本号默认最新号、同号覆盖二次确认告警、必填校验、错误信息透出（低于最新/历史重号由服务端拒绝）"
```

---

### Task 6: 版本 Tab 组件 + Tab 注册 + 工具条入口

**Files:**
- Create: `frontend/src/components/task-plans/PlanDetailVersions.vue`
- Modify: `frontend/src/components/task-plans/TaskPlanDetail.vue`（第五个 Tab + 工具条「发布版本」按钮 + 弹窗挂载）
- Modify: `frontend/src/styles/plan-module.css`（补回 `.doc-toolbar-right` 并新增 `.plan-versions` 样式）

**Interfaces:**
- Consumes: Task 4 全部 API 与类型；Task 5 的 `PublishVersionModal`；`usePlanDoc`（`saveDocument`/`plan`/`permissions`）；`formatDate`（`utils/format.ts`）；`statusLabel`（`composables/usePlanDoc.ts`，展示发版阶段时只传 phase 的映射在组件内做）。
- Produces: `PlanDetailVersions` props `{ doc: ReturnType<typeof usePlanDoc>; refreshTick: number }`，emits `request-publish`；TaskPlanDetail 的 fifth Tab 与工具条按钮。

- [ ] **Step 1: PlanDetailVersions.vue**

```vue
<template>
  <div class="plan-versions">
    <div class="versions-toolbar">
      <span v-if="dirty" class="versions-dirty">有未发布的变更：当前文档与最新版本快照不一致</span>
      <span v-else-if="versions.length" class="versions-clean">文档与最新版本快照一致</span>
      <a-button v-if="canEdit" size="small" type="primary" @click="emit('request-publish')">发布版本</a-button>
    </div>

    <div v-if="versions.length === 0" class="plan-empty">（暂无版本，发布第一个版本以建立修订记录）</div>

    <div v-for="version in versions" :key="version.id" class="version-row">
      <div class="version-main">
        <span class="version-no">{{ version.versionNo }}</span>
        <span class="version-note">{{ version.changeNote }}</span>
      </div>
      <div class="version-meta">
        <span>修订人 {{ version.author }}</span>
        <span>{{ timeLabel(version) }}</span>
        <span>{{ phaseLabel(version.planPhase) }}</span>
      </div>
      <div class="version-actions">
        <a-button size="small" type="text" @click="openView(version)">查看全文</a-button>
        <a-button v-if="canEdit" size="small" type="text" @click="rollback(version)">回滚到此版本</a-button>
      </div>
    </div>

    <a-modal v-model:open="viewOpen" :title="`版本 ${viewing?.versionNo ?? ''} 全文`" :width="880" :footer="null">
      <MdPreview v-if="viewBody" class="plan-md" :model-value="viewBody" :theme="mdTheme" language="zh-CN" />
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch, onMounted } from 'vue';
import { message, Modal } from 'ant-design-vue';
import { MdPreview } from 'md-editor-v3';
import 'md-editor-v3/lib/style.css';
import type { usePlanDoc } from '../../composables/usePlanDoc';
import { useTheme } from '../../composables/useTheme';
import { formatDate } from '../../utils/format';
import { getPlanVersionApi, listPlanVersionsApi } from '../../api/plan-doc';
import type { PlanVersionView } from '../../types';

/**
 * 修订记录展示（spec 2026-09-10 §4）：发布时间倒序列表 + 未发布变更提示 +
 * 查看全文（只读快照预览）+ 回滚（快照内容走 saveDocument 既有链路，不产生版本记录）。
 */
const props = defineProps<{
  doc: ReturnType<typeof usePlanDoc>;
  refreshTick: number;
}>();
const emit = defineEmits<{ (e: 'request-publish'): void }>();

const { themeMode } = useTheme();
const mdTheme = computed(() => (themeMode.value === 'dark' ? 'dark' : 'light'));

const versions = ref<PlanVersionView[]>([]);
const dirty = ref(false);
const viewOpen = ref(false);
const viewing = ref<PlanVersionView | null>(null);
const viewBody = ref('');

const canEdit = computed(() => Boolean(props.doc.permissions.value.EDIT));

onMounted(() => void reload());
watch(() => props.refreshTick, () => void reload());

async function reload() {
  const planId = props.doc.plan.value?.id;
  if (!planId) return;
  const response = await listPlanVersionsApi(planId).catch(() => null);
  versions.value = response?.versions ?? [];
  dirty.value = response?.bodyDiffersFromLatest ?? false;
}

function timeLabel(version: PlanVersionView): string {
  return version.updatedAt !== version.createdAt
    ? `修订于 ${formatDate(version.updatedAt)}`
    : formatDate(version.createdAt);
}

function phaseLabel(phase: string): string {
  return ({ DRAFT: '草稿', REVIEW: '评审', EXECUTION: '执行', REPORT: '报告', PUBLISH: '已发布' })[phase] ?? phase;
}

async function openView(version: PlanVersionView) {
  const planId = props.doc.plan.value?.id;
  if (!planId) return;
  const detail = await getPlanVersionApi(planId, version.id).catch(() => null);
  if (!detail) {
    message.error('版本详情加载失败');
    return;
  }
  viewing.value = version;
  viewBody.value = detail.snapshotBody;
  viewOpen.value = true;
}

function rollback(version: PlanVersionView) {
  const planId = props.doc.plan.value?.id;
  if (!planId) return;
  Modal.confirm({
    title: `回滚到版本 ${version.versionNo}？`,
    content: '将以该版本快照覆盖当前文档（保留保存冲突保护）；回滚本身不产生版本记录。',
    okText: '回滚',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      const detail = await getPlanVersionApi(planId, version.id).catch(() => null);
      if (!detail) {
        message.error('版本详情加载失败');
        return;
      }
      const outcome = await props.doc.saveDocument(detail.snapshotBody);
      if (outcome === 'ok') {
        message.success(`已回滚到版本 ${version.versionNo}`);
        await reload();
      }
    },
  });
}
</script>

<style scoped>
.plan-versions {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.versions-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.versions-dirty {
  color: var(--plan-warn-text, var(--ink));
  font-size: 12.5px;
  font-weight: 600;
}

.versions-clean {
  color: var(--muted);
  font-size: 12px;
}

.version-row {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 12px 14px;
  border: 1px solid var(--line);
  border-radius: 10px;
  background: var(--surface);
}

.version-main {
  display: flex;
  align-items: baseline;
  gap: 10px;
  min-width: 0;
}

.version-no {
  flex: none;
  color: var(--ink);
  font: 650 13.5px var(--font-ui);
}

.version-note {
  min-width: 0;
  color: var(--ink);
  font-size: 12.5px;
  overflow-wrap: anywhere;
}

.version-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 14px;
  color: var(--muted);
  font-size: 12px;
}

.version-actions {
  display: flex;
  gap: 4px;
  margin-left: -8px;
}
</style>
```

（若 `--plan-warn-text` token 不存在会回退到 `var(--ink)`，不需新建 token。）

- [ ] **Step 2: TaskPlanDetail 注册 Tab 与工具条按钮**

`frontend/src/components/task-plans/TaskPlanDetail.vue`：

a) rightExtra 工具条，把空的 `<div class="doc-toolbar-right" />`（Task 1 Step 0 移除按钮后的残留）替换为：

```html
          <div class="doc-toolbar-right">
            <a-button size="small" type="primary" ghost @click="publishOpen = true">发布版本</a-button>
          </div>
```

b) 「发布」Tab 之后追加第五个 Tab：

```html
      <a-tab-pane key="versions" tab="版本">
        <div class="plan-tab-scroll"><PlanDetailVersions :doc="doc" :refresh-tick="versionRefreshTick" @request-publish="publishOpen = true" /></div>
      </a-tab-pane>
```

c) 模板尾部（`<ScenarioDialog .../>` 之后）挂弹窗：

```html
    <PublishVersionModal
      v-model:open="publishOpen"
      :plan-id="(doc.plan.value ?? plan).id"
      @published="onVersionPublished"
    />
```

d) script：import 两个组件、状态与回调：

```ts
import PlanDetailVersions from './PlanDetailVersions.vue';
import PublishVersionModal from './PublishVersionModal.vue';

const publishOpen = ref(false);
const versionRefreshTick = ref(0);

function onVersionPublished() {
  versionRefreshTick.value += 1; // 发布后刷新版本 Tab（未发布变更提示/列表）
}
```

- [ ] **Step 3: plan-module.css 样式**

在 `.plan-doc-toolbar` 规则后补回右侧容器样式，并在文件「版本 Tab」段新增（放 `.plan-document` 段之后任意清晰位置）：

```css
.plan-doc-toolbar .doc-toolbar-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

/* 版本 Tab：发布时间倒序的修订记录行卡 */
.plan-versions .version-row + .version-row {
  margin-top: 2px;
}

@media (max-width: 1100px) {
  .plan-versions .version-row {
    padding: 10px 12px;
  }
}
```

- [ ] **Step 4: 构建验证**

```bash
cd frontend && npm run build
```

Expected: vue-tsc + vite 绿。

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/task-plans/PlanDetailVersions.vue \
        frontend/src/components/task-plans/TaskPlanDetail.vue \
        frontend/src/components/task-plans/PublishVersionModal.vue \
        frontend/src/styles/plan-module.css
git commit -m "feat：「版本」Tab 修订记录展示——发布时间倒序行卡（版本号/变更内容/修订人/时间/阶段）、未发布变更提示条、查看全文只读预览、回滚走 saveDocument 冲突保护链路；文档工具条补「发布版本」入口挂 PublishVersionModal，published 信号刷新 Tab"
```

---

### Task 7: 全量验证 + 运行时冒烟

**Files:**
- 无新文件（验证任务；冒烟截图落 `docs/superpowers/screenshots/`，**不提交**）。

**Interfaces:**
- Consumes: Task 1–6 全部产物；本机运行拓扑：后端 jar@8080、vite@5173（代理 /api）。

- [ ] **Step 1: 后端全量测试**

```bash
cd /Users/wangk/Documents/Git/performance-test-platform
JAVA_HOME=/Users/wangk/Documents/config/jdk-17.0.17+10/Contents/Home/ ./gradlew -p backend test
```

Expected: 全绿。

- [ ] **Step 2: 前端构建**

```bash
cd frontend && npm run build
```

Expected: 绿。

- [ ] **Step 3: 重启后端（Flyway V3 生效）**

```bash
cd /Users/wangk/Documents/Git/performance-test-platform
JAVA_HOME=/Users/wangk/Documents/config/jdk-17.0.17+10/Contents/Home/ ./gradlew -p backend bootJar
pkill -f 'backend-0.1.0-SNAPSHOT.jar' || true
sleep 2
nohup /Users/wangk/Documents/config/jdk-17.0.17+10/Contents/Home/bin/java -jar backend/build/libs/backend-0.1.0-SNAPSHOT.jar > /tmp/backend.log 2>&1 &
disown
sleep 8
curl -s --noproxy '*' -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8080/api/auth/login -X POST -H 'Content-Type: application/json' -d '{"username":"x","password":"x"}'
```

Expected: 启动日志无 Flyway/validate 报错；curl 返回 401（服务活着即可）。启动失败先看 `/tmp/backend.log`（常见：实体与 DDL 不一致 → validate 报错）。

- [ ] **Step 4: API 冒烟（admin/admin123）**

```bash
TOKEN=$(curl -s --noproxy '*' -X POST http://127.0.0.1:8080/api/auth/login -H 'Content-Type: application/json' -d '{"username":"admin","password":"admin123"}' | python3 -c "import json,sys;print(json.load(sys.stdin)['token'])")
# 首版发布（plan 4 当前正文）
curl -s --noproxy '*' -X POST http://127.0.0.1:8080/api/task-plans/4/versions -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"versionNo":"V1.0","changeNote":"冒烟：首版发布"}'
# 同号覆盖（应 200 且返回同 id 语义——覆盖最新）
curl -s --noproxy '*' -X POST http://127.0.0.1:8080/api/task-plans/4/versions -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"versionNo":"V1.0","changeNote":"冒烟：小修覆盖"}'
# 低于最新（应 400，message 含 PLAN_VERSION_NOT_LATEST）
curl -s --noproxy '*' -X POST http://127.0.0.1:8080/api/task-plans/4/versions -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"versionNo":"V0.9","changeNote":"倒退"}'
# 列表
curl -s --noproxy '*' http://127.0.0.1:8080/api/task-plans/4/versions -H "Authorization: Bearer $TOKEN"
```

Expected: 首版 201；覆盖 201 且仍 1 条记录（changeNote 变「冒烟：小修覆盖」）；倒退 400；列表 `versions` 1 条 + `bodyDiffersFromLatest` 为 true（若期间正文未再保存）。

- [ ] **Step 5: 浏览器冒烟（vite@5173，/projects/1/task-plans/4）**

用 playwright MCP（`page.bringToFront()` 防 RAF 节流）验证：

1. 工具条「发布版本」→ 弹窗版本号预填 V1.0（最新号）→ 改成 V2.0 + 变更内容 → 发布成功 toast。
2. 「版本」Tab 出现两行（V2.0 在上），未发布变更提示条显示（若正文≠最新快照）。
3. 工具条再次发布：默认 V2.0、直接点发布 → 覆盖确认告警出现 → 覆盖发布 → 列表仍 2 行，V2.0 显示「修订于 …」。
4. 「查看全文」弹窗可读快照；「回滚到此版本」（选 V1.0）→ 确认 → 保存成功 toast + 提示条变为「有未发布的变更」→ 切到文档 Tab 看正文已变回 V1.0 内容。
5. 截图存 `docs/superpowers/screenshots/plan-version-*.png`（不入库）。

- [ ] **Step 6: Commit（如有零散修正）**

```bash
git status --short   # 确认无遗漏源码改动；截图保持未跟踪
git add -A ':!docs/superpowers/screenshots' ':!.playwright-mcp'
git commit -m "test：版本体系运行时冒烟修正——（按实际修正内容填写；若无改动则跳过本步）"
```

---

## Self-Review 记录

- **Spec 覆盖**：§3.1 入口（Task 6 工具条+Tab 双入口）、§3.2/§3.3 弹窗与四条提交规则（Task 5 + Task 2 服务端兜底）、§4 版本 Tab（Task 6）、§5 数据模型/接口/权限（Task 1–3）、§6 前端（Task 4–6）、§7 测试（各任务 + Task 7）、§8 不做项未实现。✓
- **占位符扫描**：无 TBD/「适当处理」；所有代码块完整可落地。✓
- **类型一致性**：`PlanVersionView/Detail/ListResponse` 字段在 Task 2（Java record）→ Task 3（端点）→ Task 4（TS interface）→ Task 5/6（组件）逐字一致；`refreshTick`/`request-publish`/`published` 事件名前后一致。✓
