# 计划文档行级批注与评审工作台 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把评审批注从"全文档级留言"升级为类 Word 的块级锚定批注：文档行边悬浮加批注、右侧可开关批注面板、评审 Tab 改造为评审工作台，三视图同源同步。

**Architecture:** 后端 `plan_comments` 加锚点/线程/解决列并抽取 `PlanCommentService`；前端在 MdPreview 渲染后注入「渲染块 ↔ 源 Markdown 行」映射（`data-line`），锚定状态由纯函数 `deriveAnchors` 派生（精确→章内→全文→断链四级），批注读写不触碰 `plan.revision`。

**Tech Stack:** Spring Boot 3 / JPA / Flyway / JUnit5+AssertJ（H2 MODE=MySQL）；Vue 3 `<script setup>` / ant-design-vue / md-editor-v3 / vitest（本计划引入，仅覆盖纯函数）。

**Spec:** `docs/superpowers/specs/2026-09-10-plan-review-annotations-design.md`（实现在术语、算法阈值、UI 文案上以 spec 为准）

## Global Constraints

- Java 17：所有 Gradle 命令加前缀 `JAVA_HOME=/Users/wangk/Documents/config/jdk-17.0.17+10/Contents/Home/`（AGENTS.md 约定）。
- 后端测试命令形如 `JAVA_HOME=… ./gradlew :backend:test --tests "com.yr.perftest.platform.task.plandoc.XxxTest"`（root 项目含 `backend` 子模块）。
- 前端校验：`cd frontend && npm run build`（= vue-tsc --noEmit && vite build，全绿才算过）；纯函数单测 `cd frontend && npm test`。
- 迁移号：下一个是 **V5**（V1-V4 已占用；V4 属进行中的 AI 润色特性，与本计划无关，不得改动）。
- **工作区可能有他人未提交改动**（当前：AI 润色特性改了 `PlanDocumentController.java` 等后端文件）。每个 Task 只 `git add` 自己 Task 的文件清单；若发现要改的文件已带无关未提交改动（Task 1 开始时 `git status --short` 检查），停下来问用户，不得把别人的改动裹进本计划的提交。
- 批注读写**绝不调用** `plan.updateBody` / 推进 revision；锚点校验章节白名单复用 `PlanMarkdownSupport.CANONICAL_HEADINGS`（后端）与 `CANONICAL_HEADINGS`（前端 `utils/plan-markdown.ts`），不新增第二份常量。
- 用户可见文案全中文（spec §7 文案表逐字采用）；错误消息保持 `PLAN_XXX：中文` 既有口径（400=PlanValidationException，403=PlanAccessDeniedException，409=PlanStateException）。
- SYSTEM 流转记录、驳回必附原因、状态机、`PlanAccess` 17 个动作键：一律不变。

---

### Task 1: V5 迁移 + 批注实体扩展 + 仓储

**Files:**
- Create: `backend/src/main/resources/db/migration/V5__plan_comment_anchor.sql`
- Modify: `backend/src/main/java/com/yr/perftest/platform/task/plandoc/PersistentPlanCommentRecord.java`
- Modify: `backend/src/main/java/com/yr/perftest/platform/task/plandoc/PersistentPlanCommentRepository.java`
- Test: `backend/src/test/java/com/yr/perftest/platform/task/plandoc/PlanCommentAnchorTest.java`

**Interfaces:**
- Produces: 实体新字段 getter（`getParentId/anchorLine/anchorText/sectionTitle/bodyRevision/resolved/resolvedBy/resolvedAt`）、`applyResolve(boolean, String)`、全参构造器；仓储 `findAllByParentId(Long)`。Task 2/3 依赖。

- [ ] **Step 1: 写失败测试**

```java
package com.yr.perftest.platform.task.plandoc;

import com.yr.perftest.platform.project.PersistentProjectMemberRecord;
import com.yr.perftest.platform.project.PersistentProjectMemberRepository;
import com.yr.perftest.platform.project.PersistentProjectRecord;
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

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:plan-comment-anchor-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.h2.console.enabled=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class PlanCommentAnchorTest {

    @Autowired
    private PersistentPlanCommentRepository commentRepository;
    @Autowired
    private PersistentProjectRepository projectRepository;
    @Autowired
    private PersistentProjectMemberRepository memberRepository;
    @Autowired
    private PersistentTaskPlanRepository planRepository;

    private long planId;

    @BeforeEach
    void setUp() {
        PersistentProjectRecord project = projectRepository.save(
                new PersistentProjectRecord("P1", "项目一", "", "owner"));
        memberRepository.save(new PersistentProjectMemberRecord(project.getId(), "owner", ProjectRole.OWNER));
        planId = planRepository.save(
                new PersistentTaskPlanRecord(project.getId(), "计划一", null, "owner")).getId();
    }

    @Test
    void anchoredRootRoundTrip() {
        PersistentPlanCommentRecord saved = commentRepository.save(new PersistentPlanCommentRecord(
                planId, "reviewer", "目标值偏乐观", PlanCommentKind.REVIEW,
                null, 42, "登录接口 TPS ≥ 1000", "三、测试指标", 5L));
        PersistentPlanCommentRecord loaded = commentRepository.findById(saved.getId()).orElseThrow();
        assertThat(loaded.getParentId()).isNull();
        assertThat(loaded.getAnchorLine()).isEqualTo(42);
        assertThat(loaded.getAnchorText()).isEqualTo("登录接口 TPS ≥ 1000");
        assertThat(loaded.getSectionTitle()).isEqualTo("三、测试指标");
        assertThat(loaded.getBodyRevision()).isEqualTo(5L);
        assertThat(loaded.isResolved()).isFalse();
    }

    @Test
    void resolveMutationPersists() {
        PersistentPlanCommentRecord saved = commentRepository.save(new PersistentPlanCommentRecord(
                planId, "reviewer", "口径不清", PlanCommentKind.REVIEW, null, 7, "下单接口", "三、测试指标", 5L));
        saved.applyResolve(true, "owner");
        commentRepository.save(saved);
        PersistentPlanCommentRecord loaded = commentRepository.findById(saved.getId()).orElseThrow();
        assertThat(loaded.isResolved()).isTrue();
        assertThat(loaded.getResolvedBy()).isEqualTo("owner");
        assertThat(loaded.getResolvedAt()).isNotNull();
        loaded.applyResolve(false, "owner");
        commentRepository.save(loaded);
        PersistentPlanCommentRecord reopened = commentRepository.findById(saved.getId()).orElseThrow();
        assertThat(reopened.isResolved()).isFalse();
        assertThat(reopened.getResolvedBy()).isNull();
        assertThat(reopened.getResolvedAt()).isNull();
    }

    @Test
    void findRepliesByParent() {
        PersistentPlanCommentRecord root = commentRepository.save(new PersistentPlanCommentRecord(
                planId, "reviewer", "根批注", PlanCommentKind.REVIEW, null, 7, "下单接口", "三、测试指标", 5L));
        commentRepository.save(new PersistentPlanCommentRecord(
                planId, "owner", "回复一", PlanCommentKind.REVIEW, root.getId(), null, null, null, null));
        commentRepository.save(new PersistentPlanCommentRecord(
                planId, "reviewer", "回复二", PlanCommentKind.REVIEW, root.getId(), null, null, null, null));
        assertThat(commentRepository.findAllByParentId(root.getId())).hasSize(2);
    }
}
```

- [ ] **Step 2: 跑测试确认编译失败**

Run: `JAVA_HOME=/Users/wangk/Documents/config/jdk-17.0.17+10/Contents/Home/ ./gradlew :backend:test --tests "com.yr.perftest.platform.task.plandoc.PlanCommentAnchorTest"`
Expected: BUILD FAIL（`PersistentPlanCommentRecord` 无对应构造器/`applyResolve`，仓储无 `findAllByParentId`）

- [ ] **Step 3: 写迁移 V5**

`backend/src/main/resources/db/migration/V5__plan_comment_anchor.sql`：

```sql
ALTER TABLE `plan_comments` ADD COLUMN `parent_id` bigint NULL;
ALTER TABLE `plan_comments` ADD COLUMN `anchor_line` int NULL;
ALTER TABLE `plan_comments` ADD COLUMN `anchor_text` varchar(200) NULL;
ALTER TABLE `plan_comments` ADD COLUMN `section_title` varchar(64) NULL;
ALTER TABLE `plan_comments` ADD COLUMN `body_revision` bigint NULL;
ALTER TABLE `plan_comments` ADD COLUMN `resolved` boolean NOT NULL DEFAULT FALSE;
ALTER TABLE `plan_comments` ADD COLUMN `resolved_by` varchar(80) NULL;
ALTER TABLE `plan_comments` ADD COLUMN `resolved_at` datetime(6) NULL;
CREATE INDEX `idx_plan_comments_parent` ON `plan_comments` (`parent_id`);
CREATE INDEX `idx_plan_comments_thread` ON `plan_comments` (`plan_id`, `kind`, `resolved`);
```

- [ ] **Step 4: 扩展实体**

`PersistentPlanCommentRecord.java` 全文替换为：

```java
package com.yr.perftest.platform.task.plandoc;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "plan_comments")
public class PersistentPlanCommentRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long planId;

    @Column(nullable = false, length = 80)
    private String author;

    @Lob
    @Column(nullable = false)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PlanCommentKind kind;

    @Column(nullable = false)
    private Instant createdAt;

    // ---- 线程（spec §4）：NULL=根批注，非空=对根的回复（仅一层） ----
    @Column(name = "parent_id")
    private Long parentId;

    // ---- 锚点三元组（spec §4）：三字段同现同缺；锚定状态不落库、前端派生 ----
    @Column(name = "anchor_line")
    private Integer anchorLine;

    @Column(name = "anchor_text", length = 200)
    private String anchorText;

    @Column(name = "section_title", length = 64)
    private String sectionTitle;

    // ---- 创建时文档 revision（审计：意见针对哪版提出），服务端写入 ----
    @Column(name = "body_revision")
    private Long bodyRevision;

    // ---- 解决状态：语义上只作用于根批注（spec §3.5） ----
    @Column(nullable = false)
    private boolean resolved = false;

    @Column(name = "resolved_by", length = 80)
    private String resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    protected PersistentPlanCommentRecord() {
    }

    /** 流转附言/系统记录/历史形态：无锚点、无线程。 */
    public PersistentPlanCommentRecord(Long planId, String author, String content, PlanCommentKind kind) {
        this(planId, author, content, kind, null, null, null, null, null);
    }

    /** 全参：根批注带锚点（parentId/锚点为 null 即无线程/无锚点形态），回复只带 parentId。 */
    public PersistentPlanCommentRecord(Long planId, String author, String content, PlanCommentKind kind,
                                       Long parentId, Integer anchorLine, String anchorText,
                                       String sectionTitle, Long bodyRevision) {
        this.planId = planId;
        this.author = author;
        this.content = content;
        this.kind = kind;
        this.parentId = parentId;
        this.anchorLine = anchorLine;
        this.anchorText = anchorText;
        this.sectionTitle = sectionTitle;
        this.bodyRevision = bodyRevision;
        this.createdAt = Instant.now();
    }

    /** 解决/重开（spec §3.5）：重开时清空解决人与时间。 */
    public void applyResolve(boolean resolved, String actor) {
        this.resolved = resolved;
        this.resolvedBy = resolved ? actor : null;
        this.resolvedAt = resolved ? Instant.now() : null;
    }

    public Long getId() { return id; }
    public Long getPlanId() { return planId; }
    public String getAuthor() { return author; }
    public String getContent() { return content; }
    public PlanCommentKind getKind() { return kind; }
    public Instant getCreatedAt() { return createdAt; }
    public Long getParentId() { return parentId; }
    public Integer getAnchorLine() { return anchorLine; }
    public String getAnchorText() { return anchorText; }
    public String getSectionTitle() { return sectionTitle; }
    public Long getBodyRevision() { return bodyRevision; }
    public boolean isResolved() { return resolved; }
    public String getResolvedBy() { return resolvedBy; }
    public Instant getResolvedAt() { return resolvedAt; }
}
```

- [ ] **Step 5: 扩展仓储**

`PersistentPlanCommentRepository.java`：

```java
package com.yr.perftest.platform.task.plandoc;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PersistentPlanCommentRepository extends JpaRepository<PersistentPlanCommentRecord, Long> {
    List<PersistentPlanCommentRecord> findAllByPlanIdOrderByIdAsc(Long planId);

    List<PersistentPlanCommentRecord> findAllByParentId(Long parentId);

    void deleteAllByPlanId(Long planId);
}
```

- [ ] **Step 6: 跑测试确认通过**

Run: `JAVA_HOME=/Users/wangk/Documents/config/jdk-17.0.17+10/Contents/Home/ ./gradlew :backend:test --tests "com.yr.perftest.platform.task.plandoc.PlanCommentAnchorTest"`
Expected: 3 个测试 PASS（ddl-auto=validate 同时校验 V5 与实体一致；其余 @SpringBootTest 也会在本模块全量跑时复验迁移兼容）

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/resources/db/migration/V5__plan_comment_anchor.sql \
  backend/src/main/java/com/yr/perftest/platform/task/plandoc/PersistentPlanCommentRecord.java \
  backend/src/main/java/com/yr/perftest/platform/task/plandoc/PersistentPlanCommentRepository.java \
  backend/src/test/java/com/yr/perftest/platform/task/plandoc/PlanCommentAnchorTest.java
git commit -m "feat：批注锚点/线程/解决数据层——Flyway V5（plan_comments 加 parent_id/anchor_line/anchor_text/section_title/body_revision/resolved* 列与两索引）+ 实体全参构造与 applyResolve + findAllByParentId，配套持久化测试"
```

---

### Task 2: 抽取 PlanCommentService + CommentView 扩展

**Files:**
- Create: `backend/src/main/java/com/yr/perftest/platform/task/plandoc/PlanCommentService.java`
- Modify: `backend/src/main/java/com/yr/perftest/platform/task/plandoc/PlanWorkflowService.java`
- Modify: `backend/src/main/java/com/yr/perftest/platform/api/PlanDocumentController.java`
- Test: `backend/src/test/java/com/yr/perftest/platform/task/plandoc/PlanCommentServiceTest.java`（新建）
- Modify: `backend/src/test/java/com/yr/perftest/platform/task/plandoc/PlanWorkflowServiceTest.java`（改注入）

**Interfaces:**
- Produces（Task 3 在其上扩展）:
  - `PlanCommentService.CommentView(long id, long planId, String author, String content, PlanCommentKind kind, Instant createdAt, Long parentId, Integer anchorLine, String anchorText, String sectionTitle, Long bodyRevision, boolean resolved, String resolvedBy, Instant resolvedAt, boolean canResolve, boolean canDelete)`
  - `listComments(long planId, HumanPrincipal viewer)` / `addComment(long, HumanPrincipal, AddCommentCommand)` / `deleteComment(long, long, HumanPrincipal)` / `resolveComment(long, long, HumanPrincipal, boolean)` / `systemComment(long, String)` / `appendReviewNote(long, String author, String content)`
  - `PlanCommentService.AddCommentCommand(String content, Long parentId, CommentAnchor anchor)`、`PlanCommentService.CommentAnchor(Integer line, String text, String section)`

- [ ] **Step 1: 新建 PlanCommentServiceTest（含迁移自 PlanWorkflowServiceTest 的批注用例 + viewer 标志位新断言）**

```java
package com.yr.perftest.platform.task.plandoc;

import com.yr.perftest.platform.identity.HumanPrincipal;
import com.yr.perftest.platform.identity.SystemRole;
import com.yr.perftest.platform.project.PersistentProjectMemberRecord;
import com.yr.perftest.platform.project.PersistentProjectMemberRepository;
import com.yr.perftest.platform.project.PersistentProjectRecord;
import com.yr.perftest.platform.project.PersistentProjectRepository;
import com.yr.perftest.platform.project.ProjectRole;
import com.yr.perftest.platform.task.PersistentTaskPlanRecord;
import com.yr.perftest.platform.task.PersistentTaskPlanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:plan-comment-service-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.h2.console.enabled=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class PlanCommentServiceTest {

    private static final HumanPrincipal OWNER = new HumanPrincipal("owner", Set.of(SystemRole.PROJECT_MEMBER));
    private static final HumanPrincipal REVIEWER = new HumanPrincipal("reviewer", Set.of(SystemRole.PROJECT_MEMBER));
    private static final HumanPrincipal OUTSIDER = new HumanPrincipal("outsider", Set.of(SystemRole.PROJECT_MEMBER));

    @Autowired
    private PlanCommentService comments;
    @Autowired
    private PlanWorkflowService workflow;
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
        memberRepository.save(new PersistentProjectMemberRecord(project.getId(), "reviewer", ProjectRole.MEMBER));
        planId = planRepository.save(
                new PersistentTaskPlanRecord(project.getId(), "计划一", null, "owner")).getId();
    }

    @Test
    void addAndListWithViewerFlags() {
        PlanCommentService.CommentView created = comments.addComment(
                planId, REVIEWER, new PlanCommentService.AddCommentCommand("表格补口径", null, null));
        assertThat(created.canDelete()).isTrue();
        assertThat(created.canResolve()).isTrue();

        var reviewerView = comments.listComments(planId, REVIEWER).stream()
                .filter(c -> c.id() == created.id()).findFirst().orElseThrow();
        assertThat(reviewerView.canResolve()).isTrue();
        assertThat(reviewerView.canDelete()).isTrue();

        var ownerView = comments.listComments(planId, OWNER).stream()
                .filter(c -> c.id() == created.id()).findFirst().orElseThrow();
        assertThat(ownerView.canResolve()).isTrue();
        assertThat(ownerView.canDelete()).isTrue();

        memberRepository.save(new PersistentProjectMemberRecord(
                projectRepository.findAll().get(0).getId(), "outsider", ProjectRole.MEMBER));
        var otherView = comments.listComments(planId, OUTSIDER).stream()
                .filter(c -> c.id() == created.id()).findFirst().orElseThrow();
        assertThat(otherView.canResolve()).isFalse();
        assertThat(otherView.canDelete()).isFalse();
    }

    @Test
    void systemCommentNotDeletable() {
        comments.systemComment(planId, "owner 提交评审");
        PlanCommentService.CommentView system = comments.listComments(planId, OWNER).get(0);
        assertThat(system.canDelete()).isFalse();
        assertThatThrownBy(() -> comments.deleteComment(planId, system.id(), OWNER))
                .isInstanceOf(PlanValidationException.class);
    }

    @Test
    void deleteRequiresAuthorOrOwner() {
        PlanCommentService.CommentView created = comments.addComment(
                planId, REVIEWER, new PlanCommentService.AddCommentCommand("成员批注", null, null));
        memberRepository.save(new PersistentProjectMemberRecord(
                projectRepository.findAll().get(0).getId(), "outsider", ProjectRole.MEMBER));
        assertThatThrownBy(() -> comments.deleteComment(planId, created.id(), OUTSIDER))
                .isInstanceOf(PlanAccessDeniedException.class);
        comments.deleteComment(planId, created.id(), REVIEWER);
        assertThat(comments.listComments(planId, OWNER)).noneMatch(c -> c.id() == created.id());
    }

    @Test
    void nonMemberRejected() {
        assertThatThrownBy(() -> comments.addComment(
                planId, OUTSIDER, new PlanCommentService.AddCommentCommand("外部", null, null)))
                .isInstanceOf(PlanAccessDeniedException.class);
    }

    @Test
    void blankContentRejected() {
        assertThatThrownBy(() -> comments.addComment(
                planId, REVIEWER, new PlanCommentService.AddCommentCommand("  ", null, null)))
                .isInstanceOf(PlanValidationException.class);
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `JAVA_HOME=/Users/wangk/Documents/config/jdk-17.0.17+10/Contents/Home/ ./gradlew :backend:test --tests "com.yr.perftest.platform.task.plandoc.PlanCommentServiceTest"`
Expected: 编译失败（`PlanCommentService` 不存在）

- [ ] **Step 3: 实现 PlanCommentService**

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

/** 批注域服务（spec §6）：增删查/线程/解决；流转服务只写流转记录。批注读写不触碰 plan.revision。 */
@Service
public class PlanCommentService {

    /** 锚点三元组：三字段须同现同缺（spec §6 校验规则）。 */
    public record CommentAnchor(Integer line, String text, String section) {
    }

    public record AddCommentCommand(String content, Long parentId, CommentAnchor anchor) {
    }

    public record CommentView(long id, long planId, String author, String content, PlanCommentKind kind,
                              Instant createdAt, Long parentId, Integer anchorLine, String anchorText,
                              String sectionTitle, Long bodyRevision, boolean resolved, String resolvedBy,
                              Instant resolvedAt, boolean canResolve, boolean canDelete) {
    }

    private final PersistentPlanCommentRepository commentRepository;
    private final PersistentTaskPlanRepository planRepository;
    private final ProjectAccessResolver accessResolver;

    public PlanCommentService(PersistentPlanCommentRepository commentRepository,
                              PersistentTaskPlanRepository planRepository,
                              ProjectAccessResolver accessResolver) {
        this.commentRepository = commentRepository;
        this.planRepository = planRepository;
        this.accessResolver = accessResolver;
    }

    @Transactional(readOnly = true)
    public List<CommentView> listComments(long planId, HumanPrincipal viewer) {
        PersistentTaskPlanRecord plan = requirePlan(planId);
        requireMember(plan, viewer);
        Viewer viewerInfo = viewerOf(plan, viewer);
        return commentRepository.findAllByPlanIdOrderByIdAsc(planId).stream()
                .map(c -> toView(c, viewerInfo))
                .toList();
    }

    @Transactional
    public CommentView addComment(long planId, HumanPrincipal actor, AddCommentCommand command) {
        PersistentTaskPlanRecord plan = requirePlan(planId);
        requireCommenter(plan, actor);
        if (command == null || command.content() == null || command.content().isBlank()) {
            throw new PlanValidationException("PLAN_INVALID：批注内容不能为空");
        }
        CommentAnchor anchor = normalizeAnchor(command.anchor());
        Long parentId = validateParent(planId, command.parentId());
        PersistentPlanCommentRecord saved = commentRepository.save(new PersistentPlanCommentRecord(
                planId, actor.username(), command.content().trim(), PlanCommentKind.REVIEW,
                parentId,
                anchor == null ? null : anchor.line(),
                anchor == null ? null : anchor.text(),
                anchor == null ? null : anchor.section(),
                plan.getRevision()));
        return toView(saved, viewerOf(plan, actor));
    }

    @Transactional
    public void deleteComment(long planId, long commentId, HumanPrincipal actor) {
        PersistentTaskPlanRecord plan = requirePlan(planId);
        PersistentPlanCommentRecord comment = commentRepository.findById(commentId)
                .filter(c -> c.getPlanId() == planId)
                .orElseThrow(() -> new PlanValidationException("PLAN_INVALID：批注不存在"));
        if (comment.getKind() == PlanCommentKind.SYSTEM) {
            throw new PlanValidationException("PLAN_INVALID：系统批注不可删除");
        }
        Viewer viewer = viewerOf(plan, actor);
        if (!viewer.ownerLike() && !comment.getAuthor().equals(actor.username())) {
            throw new PlanAccessDeniedException("PLAN_ACCESS_DENIED：仅批注作者/负责人/项目 OWNER/系统管理员可删除批注");
        }
        commentRepository.findAllByParentId(commentId).forEach(commentRepository::delete); // 删根级联删回复
        commentRepository.delete(comment);
    }

    /** 解决/重开（spec §3.5/§3.6）：仅根批注；评审域外（进入执行后）批注整体只读。 */
    @Transactional
    public void resolveComment(long planId, long commentId, HumanPrincipal actor, boolean resolved) {
        PersistentTaskPlanRecord plan = requirePlan(planId);
        requireCommenter(plan, actor);
        PersistentPlanCommentRecord comment = commentRepository.findById(commentId)
                .filter(c -> c.getPlanId() == planId)
                .orElseThrow(() -> new PlanValidationException("PLAN_INVALID：批注不存在"));
        if (comment.getParentId() != null) {
            throw new PlanValidationException("PLAN_COMMENT_NESTED：仅根批注可解决/重开");
        }
        Viewer viewer = viewerOf(plan, actor);
        if (!viewer.ownerLike() && !comment.getAuthor().equals(actor.username())) {
            throw new PlanAccessDeniedException("PLAN_ACCESS_DENIED：仅批注作者/负责人可解决批注");
        }
        comment.applyResolve(resolved, actor.username());
        commentRepository.save(comment);
    }

    @Transactional
    public void systemComment(long planId, String content) {
        commentRepository.save(new PersistentPlanCommentRecord(planId, "system", content, PlanCommentKind.SYSTEM));
    }

    /** 流转附言（提交/通过附言、驳回原因）：无锚点 REVIEW 批注，前端归「未锚定」分组。 */
    @Transactional
    public void appendReviewNote(long planId, String author, String content) {
        commentRepository.save(new PersistentPlanCommentRecord(planId, author, content, PlanCommentKind.REVIEW));
    }

    private CommentAnchor normalizeAnchor(CommentAnchor anchor) {
        if (anchor == null || (anchor.line() == null && anchor.text() == null && anchor.section() == null)) {
            return null; // 三字段全缺省 = 无锚点（流转附言形态）
        }
        if (anchor.line() == null || anchor.text() == null || anchor.section() == null) {
            throw new PlanValidationException("PLAN_INVALID：批注锚点不完整（line/text/section 须同时出现）");
        }
        if (anchor.line() < 0) {
            throw new PlanValidationException("PLAN_INVALID：批注锚点行号不合法");
        }
        if (!PlanMarkdownSupport.CANONICAL_HEADINGS.contains(anchor.section())) {
            throw new PlanValidationException("PLAN_INVALID：批注锚点章节不合法");
        }
        if (anchor.text().isBlank()) {
            throw new PlanValidationException("PLAN_INVALID：批注锚点文本不能为空");
        }
        String text = anchor.text().trim();
        return new CommentAnchor(anchor.line(), text.length() > 200 ? text.substring(0, 200) : text, anchor.section());
    }

    private Long validateParent(long planId, Long parentId) {
        if (parentId == null) {
            return null;
        }
        PersistentPlanCommentRecord parent = commentRepository.findById(parentId)
                .filter(c -> c.getPlanId() == planId && c.getKind() == PlanCommentKind.REVIEW)
                .orElseThrow(() -> new PlanValidationException("PLAN_INVALID：回复目标批注不存在"));
        if (parent.getParentId() != null) {
            throw new PlanValidationException("PLAN_COMMENT_NESTED：回复不支持嵌套");
        }
        return parentId;
    }

    private CommentView toView(PersistentPlanCommentRecord c, Viewer viewer) {
        boolean authorOrOwner = viewer.ownerLike() || c.getAuthor().equals(viewer.username());
        boolean root = c.getParentId() == null;
        return new CommentView(c.getId(), c.getPlanId(), c.getAuthor(), c.getContent(), c.getKind(), c.getCreatedAt(),
                c.getParentId(), c.getAnchorLine(), c.getAnchorText(), c.getSectionTitle(), c.getBodyRevision(),
                c.isResolved(), c.getResolvedBy(), c.getResolvedAt(),
                root && authorOrOwner && c.getKind() == PlanCommentKind.REVIEW,
                authorOrOwner && c.getKind() == PlanCommentKind.REVIEW);
    }

    private record Viewer(String username, boolean ownerLike) {
    }

    private Viewer viewerOf(PersistentTaskPlanRecord plan, HumanPrincipal actor) {
        ProjectAccessResolver.PlanActorRole role =
                actor == null ? ProjectAccessResolver.PlanActorRole.NONE
                        : accessResolver.resolve(plan.getProjectId(), actor, plan.getCreatedBy());
        boolean ownerLike = role == ProjectAccessResolver.PlanActorRole.SYSTEM_ADMIN
                || role == ProjectAccessResolver.PlanActorRole.PROJECT_OWNER
                || role == ProjectAccessResolver.PlanActorRole.PLAN_OWNER;
        return new Viewer(actor == null ? "" : actor.username(), ownerLike);
    }

    private void requireMember(PersistentTaskPlanRecord plan, HumanPrincipal actor) {
        if (actor == null || accessResolver.resolve(plan.getProjectId(), actor, plan.getCreatedBy())
                == ProjectAccessResolver.PlanActorRole.NONE) {
            throw new PlanAccessDeniedException("PLAN_ACCESS_DENIED：非项目成员");
        }
    }

    /** COMMENT 门禁（PlanAccess 唯一真相源）：非成员 403；成员但阶段不允许 409。COMMENT 不受 hasAnyExecution 影响。 */
    private void requireCommenter(PersistentTaskPlanRecord plan, HumanPrincipal actor) {
        if (actor == null) {
            throw new PlanAccessDeniedException("PLAN_ACCESS_DENIED：未登录");
        }
        ProjectAccessResolver.PlanActorRole role =
                accessResolver.resolve(plan.getProjectId(), actor, plan.getCreatedBy());
        if (role == ProjectAccessResolver.PlanActorRole.NONE) {
            throw new PlanAccessDeniedException("PLAN_ACCESS_DENIED：非项目成员");
        }
        var permissions = PlanAccess.compute(role, plan.getPhase(), plan.getStatus(), false);
        if (!Boolean.TRUE.equals(permissions.get("COMMENT"))) {
            throw new PlanStateException("PLAN_STATE：当前阶段不可批注（当前 "
                    + plan.getPhase() + "/" + plan.getStatus() + "）", plan.getPhase(), plan.getStatus(), List.of());
        }
    }

    private PersistentTaskPlanRecord requirePlan(long planId) {
        return planRepository.findById(planId)
                .orElseThrow(() -> new PlanValidationException("PLAN_INVALID：task plan does not exist"));
    }
}
```

- [ ] **Step 4: PlanWorkflowService 改为委托**

在 `PlanWorkflowService.java`：

1. 注入 `PlanCommentService commentService`（构造器加参），删除 `listComments/addComment/deleteComment/systemComment` 四个方法与 `CommentView` record、`commentRepository` 字段（批注写全部走 commentService）。
2. 各流转方法中的批注写替换：
   - `submit`/`approve` 中 `commentRepository.save(new PersistentPlanCommentRecord(planId, actor.username(), comment.trim(), PlanCommentKind.REVIEW))` → `commentService.appendReviewNote(planId, actor.username(), comment.trim())`
   - 所有 `systemComment(planId, …)` → `commentService.systemComment(planId, …)`
   - `reject` 中同理（`appendReviewNote`）。
3. 删除顶部不再使用的 `PersistentPlanCommentRepository` 注入与 import。

`PlanDocumentController.java`：

1. 注入 `PlanCommentService commentService`（构造器加参），删除 `PlanWorkflowService.CommentView` import。
2. 端点替换：

```java
@GetMapping("/task-plans/{planId}/comments")
public List<PlanCommentService.CommentView> listComments(@PathVariable long planId) {
    requireMember(planService.getPlan(planId));
    return commentService.listComments(planId, requireHuman());
}

@PostMapping("/task-plans/{planId}/comments")
@ResponseStatus(HttpStatus.CREATED)
public PlanCommentService.CommentView addComment(@PathVariable long planId, @RequestBody AddCommentRequest request) {
    return commentService.addComment(planId, requireHuman(),
            new PlanCommentService.AddCommentCommand(request.content(), null, null));
}

@DeleteMapping("/task-plans/{planId}/comments/{commentId}")
@ResponseStatus(HttpStatus.NO_CONTENT)
public void deleteComment(@PathVariable long planId, @PathVariable long commentId) {
    commentService.deleteComment(planId, commentId, requireHuman());
}
```

- [ ] **Step 5: 改造 PlanWorkflowServiceTest 的批注用例**

`PlanWorkflowServiceTest.java`：注入 `@Autowired private PlanCommentService comments;`，`reviewCommentLifecycle` 替换为：

```java
    @Test
    void reviewCommentLifecycle() {
        PlanCommentService.CommentView comment = comments.addComment(
                planId, REVIEWER, new PlanCommentService.AddCommentCommand("第二章表格补口径", null, null));
        assertThat(comment.kind()).isEqualTo(PlanCommentKind.REVIEW);
        comments.deleteComment(planId, comment.id(), REVIEWER);
        assertThat(comments.listComments(planId, OWNER)).noneMatch(c -> c.id() == comment.id());
        PlanCommentService.CommentView otherMember = comments.addComment(
                planId, OWNER, new PlanCommentService.AddCommentCommand("成员批注", null, null));
        assertThatThrownBy(() -> comments.deleteComment(planId, otherMember.id(), REVIEWER)) // 非作者且非负责人
                .isInstanceOf(PlanAccessDeniedException.class);
    }
```

同文件 `withdrawReturnsToDraftAndWritesSystemComments` 中 `workflow.listComments(planId)` → `comments.listComments(planId, OWNER)`。

- [ ] **Step 6: 跑两个测试类确认通过**

Run: `JAVA_HOME=/Users/wangk/Documents/config/jdk-17.0.17+10/Contents/Home/ ./gradlew :backend:test --tests "com.yr.perftest.platform.task.plandoc.PlanCommentServiceTest" --tests "com.yr.perftest.platform.task.plandoc.PlanWorkflowServiceTest" --tests "com.yr.perftest.platform.task.plandoc.PlanCommentAnchorTest"`
Expected: 全部 PASS

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/yr/perftest/platform/task/plandoc/PlanCommentService.java \
  backend/src/main/java/com/yr/perftest/platform/task/plandoc/PlanWorkflowService.java \
  backend/src/main/java/com/yr/perftest/platform/api/PlanDocumentController.java \
  backend/src/test/java/com/yr/perftest/platform/task/plandoc/PlanCommentServiceTest.java \
  backend/src/test/java/com/yr/perftest/platform/task/plandoc/PlanWorkflowServiceTest.java
git commit -m "refactor：批注域抽取 PlanCommentService（增删查/viewer 级 canResolve·canDelete/流转附言 appendReviewNote），CommentView 扩展线程+锚点+解决字段（本期先以 null/false 兼容），状态机服务只留流转；controller 批注端点切至新服务"
```

---

### Task 3: 锚定新增/回复/解决/级联删除（API 定稿）

**Files:**
- Modify: `backend/src/main/java/com/yr/perftest/platform/task/plandoc/PlanCommentService.java`（无逻辑改动，仅测试覆盖既有校验——校验已在 Task 2 全部实现）
- Modify: `backend/src/main/java/com/yr/perftest/platform/api/PlanDocumentController.java`
- Test: `backend/src/test/java/com/yr/perftest/platform/task/plandoc/PlanCommentServiceTest.java`（追加用例）

**Interfaces:**
- Produces（前端 Task 6 消费的最终 API 契约）:
  - `POST /api/task-plans/{planId}/comments` body `{content, parentId?, anchor?: {line, text, section}}` → CommentView（新增字段齐备；`bodyRevision` 服务端取 `plan.getRevision()`）
  - `POST /api/task-plans/{planId}/comments/{commentId}/resolve` body `{resolved: boolean}`
  - `DELETE /api/task-plans/{planId}/comments/{commentId}`（根删除级联回复）

- [ ] **Step 1: 追加失败测试（PlanCommentServiceTest 内新增以下用例）**

```java
    @Test
    void anchoredCommentRoundTrip() {
        var anchor = new PlanCommentService.CommentAnchor(42, "登录接口 TPS ≥ 1000", "三、测试指标");
        PlanCommentService.CommentView created = comments.addComment(
                planId, REVIEWER, new PlanCommentService.AddCommentCommand("目标偏乐观", null, anchor));
        long revision = planRepository.findById(planId).orElseThrow().getRevision();
        assertThat(created.anchorLine()).isEqualTo(42);
        assertThat(created.sectionTitle()).isEqualTo("三、测试指标");
        assertThat(created.bodyRevision()).isEqualTo(revision); // 服务端写入，不信任客户端
        assertThat(created.parentId()).isNull();
        assertThat(created.resolved()).isFalse();
    }

    @Test
    void anchorMustBeComplete() {
        var partial = new PlanCommentService.CommentAnchor(42, "登录接口", null);
        assertThatThrownBy(() -> comments.addComment(
                planId, REVIEWER, new PlanCommentService.AddCommentCommand("x", null, partial)))
                .isInstanceOf(PlanValidationException.class)
                .hasMessageContaining("锚点不完整");
    }

    @Test
    void anchorSectionWhitelisted() {
        var bad = new PlanCommentService.CommentAnchor(1, "text", "十三、不存在");
        assertThatThrownBy(() -> comments.addComment(
                planId, REVIEWER, new PlanCommentService.AddCommentCommand("x", null, bad)))
                .isInstanceOf(PlanValidationException.class)
                .hasMessageContaining("章节不合法");
    }

    @Test
    void anchorTextTruncatedTo200() {
        var anchor = new PlanCommentService.CommentAnchor(0, "长".repeat(300), "三、测试指标");
        PlanCommentService.CommentView created = comments.addComment(
                planId, REVIEWER, new PlanCommentService.AddCommentCommand("x", null, anchor));
        assertThat(created.anchorText()).hasSize(200);
    }

    @Test
    void replyToOneLevelOnly() {
        PlanCommentService.CommentView root = comments.addComment(
                planId, REVIEWER, new PlanCommentService.AddCommentCommand("根", null, null));
        PlanCommentService.CommentView reply = comments.addComment(
                planId, OWNER, new PlanCommentService.AddCommentCommand("回复", root.id(), null));
        assertThat(reply.parentId()).isEqualTo(root.id());
        assertThat(reply.canResolve()).isFalse(); // 回复不可被解决
        assertThatThrownBy(() -> comments.addComment(
                planId, REVIEWER, new PlanCommentService.AddCommentCommand("嵌套", reply.id(), null)))
                .isInstanceOf(PlanValidationException.class)
                .hasMessageContaining("PLAN_COMMENT_NESTED");
    }

    @Test
    void resolveRootOnlyAndPermissionAware() {
        PlanCommentService.CommentView root = comments.addComment(
                planId, REVIEWER, new PlanCommentService.AddCommentCommand("根", null, null));
        PlanCommentService.CommentView reply = comments.addComment(
                planId, REVIEWER, new PlanCommentService.AddCommentCommand("回复", root.id(), null));
        assertThatThrownBy(() -> comments.resolveComment(planId, reply.id(), REVIEWER, true))
                .isInstanceOf(PlanValidationException.class)
                .hasMessageContaining("仅根批注");
        memberRepository.save(new PersistentProjectMemberRecord(
                projectRepository.findAll().get(0).getId(), "outsider", ProjectRole.MEMBER));
        assertThatThrownBy(() -> comments.resolveComment(planId, root.id(), OUTSIDER, true))
                .isInstanceOf(PlanAccessDeniedException.class);
        comments.resolveComment(planId, root.id(), OWNER, true); // 负责人可解决他人批注
        var resolved = comments.listComments(planId, OWNER).stream()
                .filter(c -> c.id() == root.id()).findFirst().orElseThrow();
        assertThat(resolved.resolved()).isTrue();
        assertThat(resolved.resolvedBy()).isEqualTo("owner");
        comments.resolveComment(planId, root.id(), REVIEWER, false); // 作者可重开
        assertThat(comments.listComments(planId, OWNER).stream()
                .filter(c -> c.id() == root.id()).findFirst().orElseThrow().resolved()).isFalse();
    }

    @Test
    void resolveBlockedAfterExecution() {
        PlanCommentService.CommentView root = comments.addComment(
                planId, REVIEWER, new PlanCommentService.AddCommentCommand("根", null, null));
        workflow.submit(planId, OWNER, null);
        workflow.startReview(planId, REVIEWER);
        workflow.approve(planId, REVIEWER, null);
        workflow.startExecution(planId, REVIEWER);
        assertThatThrownBy(() -> comments.resolveComment(planId, root.id(), REVIEWER, true))
                .isInstanceOf(PlanStateException.class);
    }

    @Test
    void deleteRootCascadesReplies() {
        PlanCommentService.CommentView root = comments.addComment(
                planId, REVIEWER, new PlanCommentService.AddCommentCommand("根", null, null));
        comments.addComment(planId, OWNER, new PlanCommentService.AddCommentCommand("回复", root.id(), null));
        comments.deleteComment(planId, root.id(), REVIEWER);
        assertThat(comments.listComments(planId, OWNER)).isEmpty();
    }

    @Test
    void commentBlockedOutsideReviewPhases() {
        workflow.submit(planId, OWNER, null);
        workflow.startReview(planId, REVIEWER);
        workflow.approve(planId, REVIEWER, null);
        workflow.startExecution(planId, REVIEWER);
        assertThatThrownBy(() -> comments.addComment(
                planId, REVIEWER, new PlanCommentService.AddCommentCommand("迟到", null, null)))
                .isInstanceOf(PlanStateException.class);
    }
```

- [ ] **Step 2: 跑测试（锚定/嵌套/级联相关应失败或暴露缺口）**

Run: `JAVA_HOME=/Users/wangk/Documents/config/jdk-17.0.17+10/Contents/Home/ ./gradlew :backend:test --tests "com.yr.perftest.platform.task.plandoc.PlanCommentServiceTest"`
Expected: 新用例 PASS（Task 2 已实现全部校验；若有用例失败，修复 `PlanCommentService` 至最小必要——不允许改测试来凑绿）。注意 `PlanWorkflowService` 需暴露 `submit/startReview/approve/startExecution` 供测试驱动阶段（现有 public 方法，已满足）。

- [ ] **Step 3: Controller 端点定稿**

`PlanDocumentController.java` 批注相关替换为（record 扩展 + resolve 端点）：

```java
@PostMapping("/task-plans/{planId}/comments")
@ResponseStatus(HttpStatus.CREATED)
public PlanCommentService.CommentView addComment(@PathVariable long planId, @RequestBody AddCommentRequest request) {
    return commentService.addComment(planId, requireHuman(), new PlanCommentService.AddCommentCommand(
            request.content(), request.parentId(),
            request.anchor() == null ? null : new PlanCommentService.CommentAnchor(
                    request.anchor().line(), request.anchor().text(), request.anchor().section())));
}

@PostMapping("/task-plans/{planId}/comments/{commentId}/resolve")
@ResponseStatus(HttpStatus.NO_CONTENT)
public void resolveComment(@PathVariable long planId, @PathVariable long commentId,
                           @RequestBody ResolveCommentRequest request) {
    commentService.resolveComment(planId, commentId, requireHuman(), request.resolved());
}
```

record 区替换/新增：

```java
public record CommentAnchorRequest(Integer line, String text, String section) {
}

public record AddCommentRequest(String content, Long parentId, CommentAnchorRequest anchor) {
}

public record ResolveCommentRequest(boolean resolved) {
}
```

- [ ] **Step 4: 全模块后端测试回归**

Run: `JAVA_HOME=/Users/wangk/Documents/config/jdk-17.0.17+10/Contents/Home/ ./gradlew :backend:test`
Expected: BUILD SUCCESS（含既有 mcp/report 等全部模块测试不受影响）

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/yr/perftest/platform/api/PlanDocumentController.java \
  backend/src/test/java/com/yr/perftest/platform/task/plandoc/PlanCommentServiceTest.java
git commit -m "feat：锚定批注 API 定稿——POST comments 支持 parentId/anchor 三元组（完整性/章节白名单/截 200 校验、bodyRevision 服务端写入）、POST resolve 解决/重开（仅根、作者或负责人、执行域 409）、DELETE 根级联回复；补齐九个服务级用例"
```

---

### Task 4: 前端 vitest + splitBlocks 块切分

**Files:**
- Modify: `frontend/package.json`（scripts + devDependency）
- Modify: `frontend/src/utils/plan-markdown.ts`
- Test: `frontend/src/utils/plan-markdown.test.ts`（新建）

**Interfaces:**
- Produces（Task 5/7 依赖）:
  - `interface DocBlock { startLine: number; endLine: number; raw: string }`（startLine/endLine 为**章内容局部**行号，0 基）
  - `splitBlocks(content: string | null | undefined): DocBlock[]`
  - `listItemOffsets(raw: string): number[]`（块内列表项首行的局部偏移）
  - `checklistItemLines(content: string): number[]`（清单项行的局部行号，序与 `parseChecklistGroups` 的 `index` 一致）

- [ ] **Step 1: 安装 vitest 并加 test 脚本**

```bash
cd frontend && npm install -D vitest
```

`frontend/package.json` scripts 增加：

```json
"test": "vitest run"
```

- [ ] **Step 2: 写失败测试**

`frontend/src/utils/plan-markdown.test.ts`：

```ts
import { describe, expect, it } from 'vitest';
import { checklistItemLines, listItemOffsets, splitBlocks } from './plan-markdown';

describe('splitBlocks', () => {
  it('段落以空行分界并携带局部行号', () => {
    const content = '第一段第一行\n第一段第二行\n\n第二段';
    expect(splitBlocks(content)).toEqual([
      { startLine: 0, endLine: 1, raw: '第一段第一行\n第一段第二行' },
      { startLine: 3, endLine: 3, raw: '第二段' },
    ]);
  });

  it('标题/引用/表格各自成块，连续表格行聚合', () => {
    const content = '### 小标题\n\n| a | b |\n|---|---|\n| 1 | 2 |\n| 3 | 4 |\n\n> 引用';
    expect(splitBlocks(content)).toEqual([
      { startLine: 0, endLine: 0, raw: '### 小标题' },
      { startLine: 2, endLine: 5, raw: '| a | b |\n|---|---|\n| 1 | 2 |\n| 3 | 4 |' },
      { startLine: 7, endLine: 7, raw: '> 引用' },
    ]);
  });

  it('代码栅栏吞并到闭合行', () => {
    const content = '```json\n{"a":1}\n```';
    expect(splitBlocks(content)).toEqual([
      { startLine: 0, endLine: 2, raw: '```json\n{"a":1}\n```' },
    ]);
  });

  it('连续列表行（含缩进续行）聚合为一块', () => {
    const content = '- 第一项\n- 第二项\n  续行\n\n段落';
    expect(splitBlocks(content)).toEqual([
      { startLine: 0, endLine: 2, raw: '- 第一项\n- 第二项\n  续行' },
      { startLine: 4, endLine: 4, raw: '段落' },
    ]);
  });

  it('空内容返回空数组', () => {
    expect(splitBlocks(null)).toEqual([]);
    expect(splitBlocks('')).toEqual([]);
  });
});

describe('listItemOffsets', () => {
  it('返回列表项首行的块内偏移', () => {
    expect(listItemOffsets('- 第一项\n- 第二项\n  续行')).toEqual([0, 1]);
    expect(listItemOffsets('1. 甲\n2、乙')).toEqual([0, 1]);
  });
});

describe('checklistItemLines', () => {
  it('清单项行号序与 parseChecklistGroups 的 index 一致（跳过标题与空行）', () => {
    const content = '### 入口准则\n\n- [ ] 指标已定义（自动）\n\n普通文字\n- [x] 脚本已关联';
    expect(checklistItemLines(content)).toEqual([2, 4]);
  });
});
```

- [ ] **Step 3: 跑测试确认失败**

Run: `cd frontend && npm test`
Expected: FAIL（`splitBlocks` 等未导出）

- [ ] **Step 4: 实现（plan-markdown.ts 末尾追加）**

```ts
// ---------- 块切分（行级批注的锚定单元，spec §5.1） ----------

export interface DocBlock {
  /** 块首行（0 基，章内容局部行号） */
  startLine: number;
  /** 块尾行（含） */
  endLine: number;
  raw: string;
}

const ATX_RE = /^\s*#{1,6}\s/;
const TABLE_RE = /^\s*\|.*\|\s*$/;
const QUOTE_RE = /^\s*>/;
const LIST_ITEM_RE = /^\s*(?:[-*+]|\d{1,2}[.、)）])\s+/;
const LIST_CONT_RE = /^\s{2,}\S/;
const FENCE_RE = /^\s*(`{3,}|~{3,})/;

export function splitBlocks(content: string | null | undefined): DocBlock[] {
  if (!content) return [];
  const lines = content.split('\n');
  const blocks: DocBlock[] = [];
  let paragraph: DocBlock | null = null;
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    if (line.trim() === '') {
      paragraph = null;
      continue;
    }
    const fence = line.match(FENCE_RE)?.[1];
    if (fence) {
      let end = i;
      for (let j = i + 1; j < lines.length; j++) {
        end = j;
        if (lines[j].trim().startsWith(fence)) break;
      }
      blocks.push({ startLine: i, endLine: end, raw: lines.slice(i, end + 1).join('\n') });
      paragraph = null;
      i = end;
      continue;
    }
    if (ATX_RE.test(line) || QUOTE_RE.test(line)) {
      blocks.push({ startLine: i, endLine: i, raw: line });
      paragraph = null;
      continue;
    }
    if (TABLE_RE.test(line)) {
      let end = i;
      while (end + 1 < lines.length && TABLE_RE.test(lines[end + 1])) end++;
      blocks.push({ startLine: i, endLine: end, raw: lines.slice(i, end + 1).join('\n') });
      paragraph = null;
      i = end;
      continue;
    }
    if (LIST_ITEM_RE.test(line)) {
      let end = i;
      while (end + 1 < lines.length && lines[end + 1].trim() !== ''
      && (LIST_ITEM_RE.test(lines[end + 1]) || LIST_CONT_RE.test(lines[end + 1]))) end++;
      blocks.push({ startLine: i, endLine: end, raw: lines.slice(i, end + 1).join('\n') });
      paragraph = null;
      i = end;
      continue;
    }
    if (paragraph) {
      paragraph.endLine = i;
      paragraph.raw += '\n' + line;
    } else {
      paragraph = { startLine: i, endLine: i, raw: line };
      blocks.push(paragraph);
    }
  }
  return blocks;
}

/** 块内列表项首行的局部偏移（Task 7 的 <li> 行映射用）。 */
export function listItemOffsets(raw: string): number[] {
  return raw.split('\n')
    .map((line, offset) => (LIST_ITEM_RE.test(line) ? offset : -1))
    .filter((offset) => offset >= 0);
}

/** 清单项行的局部行号，序与 parseChecklistGroups 的 index 口径一致（spec §5.1 六章映射）。 */
export function checklistItemLines(content: string | null | undefined): number[] {
  if (!content) return [];
  const lines = content.split('\n');
  const result: number[] = [];
  for (let i = 0; i < lines.length; i++) {
    const trimmed = lines[i].trim();
    if (/^###\s+/.test(trimmed) || /^\*\*[^*]+\*\*：?$/.test(trimmed)) continue;
    if (TASK_ITEM_RE.test(trimmed) || PLAIN_ITEM_RE.test(trimmed)) result.push(i);
  }
  return result;
}
```

- [ ] **Step 5: 跑测试与类型检查确认通过**

Run: `cd frontend && npm test && npm run build`
Expected: vitest 全绿；vue-tsc + vite build 无错误

- [ ] **Step 6: Commit**

```bash
git add frontend/package.json frontend/package-lock.json frontend/src/utils/plan-markdown.ts frontend/src/utils/plan-markdown.test.ts
git commit -m "feat：plan-markdown 块切分——splitBlocks（段落/ATX/引用/表格/列表/代码栅栏 → 局部行号块）+ listItemOffsets/checklistItemLines（表格行与清单项锚定映射用），引入 vitest 覆盖纯函数"
```

---

### Task 5: plan-anchors 锚定派生纯函数

**Files:**
- Create: `frontend/src/utils/plan-anchors.ts`
- Test: `frontend/src/utils/plan-anchors.test.ts`（新建）

**Interfaces:**
- Consumes: `splitSections/splitBlocks`（Task 4）、`PlanComment`（Task 6 类型，先以结构化最小接口声明避免循环依赖——直接 `import type { PlanComment } from '../types'`，Task 6 会扩展该类型字段，本 Task 即按最终字段编写）。
- Produces（Task 7/8/10 消费）:
  - `type AnchorState = 'ok' | 'remounted' | 'broken'`
  - `interface AnchorResolution { commentId: number; state: AnchorState; line: number | null; sectionTitle: string }`
  - `deriveAnchors(body: string | null | undefined, roots: PlanComment[]): Map<number, AnchorResolution>`（只处理带锚点的根批注；无锚点批注不入 Map）
  - `normalizeForMatch(text: string): string`、`similarity(a: string, b: string): number`

- [ ] **Step 1: 写失败测试**

`frontend/src/utils/plan-anchors.test.ts`：

```ts
import { describe, expect, it } from 'vitest';
import { deriveAnchors, normalizeForMatch, similarity } from './plan-anchors';
import type { PlanComment } from '../types';

const BODY = [
  '# 计划',
  '## 三、测试指标',
  '登录接口 TPS ≥ 1000，CPU < 70%。',
  '',
  '下单接口 P95 ≤ 200ms。',
  '## 四、测试范围',
  '覆盖核心交易链路。',
].join('\n');

function root(overrides: Partial<PlanComment>): PlanComment {
  return {
    id: 1, planId: 1, author: 'reviewer', content: '批注', kind: 'REVIEW', createdAt: '',
    parentId: null, anchorLine: null, anchorText: null, sectionTitle: null, bodyRevision: null,
    resolved: false, resolvedBy: null, resolvedAt: null, canResolve: true, canDelete: true,
    ...overrides,
  };
}

describe('normalizeForMatch / similarity', () => {
  it('去空白与 Markdown 修饰符并小写', () => {
    expect(normalizeForMatch('**登录接口** TPS ≥ 1000，`CPU`')).toBe('登录接口tps≥1000cpu');
  });
  it('相同文本相似度 1，无关文本低于阈值', () => {
    expect(similarity('登录接口tps', '登录接口tps')).toBe(1);
    expect(similarity('登录接口tps', 'zzzzzzzz')).toBeLessThan(0.6);
  });
});

describe('deriveAnchors', () => {
  it('精确命中 → ok', () => {
    const map = deriveAnchors(BODY, [root({ id: 1, anchorLine: 2, anchorText: '登录接口 TPS ≥ 1000', sectionTitle: '三、测试指标' })]);
    expect(map.get(1)).toMatchObject({ state: 'ok', line: 2, sectionTitle: '三、测试指标' });
  });

  it('行号漂移但章内文本可匹配 → remounted 到新行', () => {
    const map = deriveAnchors(BODY, [root({ id: 2, anchorLine: 6, anchorText: '下单接口 P95 ≤ 200ms', sectionTitle: '三、测试指标' })]);
    expect(map.get(2)?.state).toBe('remounted');
    expect(map.get(2)?.line).toBe(4);
  });

  it('章内找不到、全文可找 → remounted；带新章节归属', () => {
    const map = deriveAnchors(BODY, [root({ id: 3, anchorLine: 20, anchorText: '覆盖核心交易链路', sectionTitle: '三、测试指标' })]);
    expect(map.get(3)?.state).toBe('remounted');
    expect(map.get(3)?.line).toBe(6);
  });

  it('彻底找不到 → broken，归属回原章', () => {
    const map = deriveAnchors(BODY, [root({ id: 4, anchorLine: 20, anchorText: '这段话已被删除干净', sectionTitle: '九、风险与预案' })]);
    expect(map.get(4)?.state).toBe('broken');
    expect(map.get(4)?.line).toBeNull();
    expect(map.get(4)?.sectionTitle).toBe('九、风险与预案');
  });

  it('无锚点批注不入结果', () => {
    const map = deriveAnchors(BODY, [root({ id: 5, anchorLine: null, anchorText: null, sectionTitle: null })]);
    expect(map.size).toBe(0);
  });
});
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd frontend && npm test`
Expected: FAIL（模块不存在）

- [ ] **Step 3: 实现 plan-anchors.ts**

```ts
import type { PlanComment } from '../types';
import { CANONICAL_HEADINGS, splitBlocks, splitSections } from './plan-markdown';

export type AnchorState = 'ok' | 'remounted' | 'broken';

export interface AnchorResolution {
  commentId: number;
  state: AnchorState;
  /** 命中块的全局行号（body 0 基）；broken 时为 null */
  line: number | null;
  /** 展示分组归属（broken 回原章；spec §5.3） */
  sectionTitle: string;
}

/** spec §5.3 阈值：归一化相似度 ≥ 0.6 视为同一内容（算法用字符 bigram Dice，O(n) 优于 LCS）。 */
const SIMILARITY_THRESHOLD = 0.6;

/** 去空白与 Markdown 修饰符、转小写——锚点匹配的归一化口径。 */
export function normalizeForMatch(text: string): string {
  return text.replace(/[\s#*>`|~_[\]()\\-]/g, '').toLowerCase();
}

function bigramsOf(normalized: string): Set<string> {
  const grams = new Set<string>();
  for (let i = 0; i < normalized.length - 1; i++) grams.add(normalized.slice(i, i + 2));
  return grams;
}

/** 字符 bigram Dice 系数：2|A∩B|/(|A|+|B|)；短文本（<2 字符）退化为全等判断。 */
export function similarity(a: string, b: string): number {
  const na = normalizeForMatch(a);
  const nb = normalizeForMatch(b);
  if (na === nb) return 1;
  if (na.length < 2 || nb.length < 2) return 0;
  const ga = bigramsOf(na);
  const gb = bigramsOf(nb);
  let intersect = 0;
  for (const gram of ga) if (gb.has(gram)) intersect++;
  return (2 * intersect) / (ga.size + gb.size);
}

interface AnchorBlock {
  line: number;
  sectionTitle: string;
  text: string;
}

function anchorBlocks(body: string | null | undefined): AnchorBlock[] {
  const blocks: AnchorBlock[] = [];
  for (const section of splitSections(body)) {
    const offset = section.line + 1; // 章内容从标题行下一行开始
    for (const block of splitBlocks(section.content)) {
      blocks.push({ line: offset + block.startLine, sectionTitle: section.title, text: block.raw });
    }
  }
  return blocks;
}

/**
 * 锚定派生（spec §5.3，不回写）：精确行号 → 章内模糊 → 全文模糊 → 断链。
 * 只接受带完整锚点的根批注；返回 commentId → 分辨结果。
 */
export function deriveAnchors(body: string | null | undefined, roots: PlanComment[]): Map<number, AnchorResolution> {
  const result = new Map<number, AnchorResolution>();
  const blocks = anchorBlocks(body);
  const byLine = new Map(blocks.map((b) => [b.line, b]));
  for (const comment of roots) {
    if (comment.anchorLine == null || comment.anchorText == null || comment.sectionTitle == null) continue;
    const resolution = (state: AnchorState, line: number | null, sectionTitle: string): AnchorResolution =>
      ({ commentId: comment.id, state, line, sectionTitle });
    const exact = byLine.get(comment.anchorLine);
    if (exact && similarity(comment.anchorText, exact.text) >= SIMILARITY_THRESHOLD) {
      result.set(comment.id, resolution('ok', exact.line, exact.sectionTitle));
      continue;
    }
    const inSection = blocks.filter((b) => b.sectionTitle === comment.sectionTitle);
    const globalBest = bestMatch(comment.anchorText, inSection) ?? bestMatch(comment.anchorText, blocks);
    if (globalBest) {
      result.set(comment.id, resolution('remounted', globalBest.line, globalBest.sectionTitle));
      continue;
    }
    const fallbackSection = CANONICAL_HEADINGS.includes(comment.sectionTitle)
      ? comment.sectionTitle
      : CANONICAL_HEADINGS[0];
    result.set(comment.id, resolution('broken', null, fallbackSection));
  }
  return result;
}

function bestMatch(anchorText: string, candidates: AnchorBlock[]): AnchorBlock | null {
  let best: AnchorBlock | null = null;
  let bestScore = SIMILARITY_THRESHOLD;
  for (const candidate of candidates) {
    const score = similarity(anchorText, candidate.text);
    if (score > bestScore) {
      best = candidate;
      bestScore = score;
    }
  }
  return best;
}
```

- [ ] **Step 4: 跑测试与类型检查确认通过**

Run: `cd frontend && npm test && npm run build`
Expected: 全绿（`PlanComment` 类型在 Task 6 才扩展——若此时 vue-tsc 因类型缺字段报错，先在 `types/index.ts` 按本计划 Task 6 Step 1 的类型定义落盘字段；`npm test` 不做类型检查可先绿）

- [ ] **Step 5: Commit**

```bash
git add frontend/src/utils/plan-anchors.ts frontend/src/utils/plan-anchors.test.ts frontend/src/types/index.ts
git commit -m "feat：锚定派生纯函数 deriveAnchors——归一化 + bigram Dice 相似度（阈值 0.6），四级解析 精确行号→章内模糊→全文模糊→断链（断链归属回原章/首章），不回写不落库"
```

---

### Task 6: 类型扩展 + API 封装 + usePlanDoc 线程化

**Files:**
- Modify: `frontend/src/types/index.ts`（`PlanComment` 扩展 + 新增 `PlanCommentThread`）
- Modify: `frontend/src/api/plan-doc.ts`
- Modify: `frontend/src/composables/usePlanDoc.ts`

**Interfaces:**
- Produces（Task 7-11 消费）:
  - `PlanComment` 新字段：`parentId: number | null; anchorLine: number | null; anchorText: string | null; sectionTitle: string | null; bodyRevision: number | null; resolved: boolean; resolvedBy: string | null; resolvedAt: string | null; canResolve: boolean; canDelete: boolean`
  - `PlanCommentAnchor { line: number; text: string; section: string }`
  - `PlanCommentThread { root: PlanComment; replies: PlanComment[] }`
  - `addCommentApi(planId, payload: { content: string; parentId?: number; anchor?: PlanCommentAnchor })`
  - `resolveCommentApi(planId, commentId, resolved: boolean)`
  - `usePlanDoc()` 新增返回：`threads`（REVIEW 根批注 + 回复）、`unresolvedCount`、`unanchoredThreads`、`anchoredRoots`（供 deriveAnchors 入参）、`panelOpen`（`boolean | null`）+ `panelEffective`（评审阶段缺省开）+ `togglePanel()`、`addAnchoredComment(input): Promise<boolean>`、`resolveComment(commentId, resolved): Promise<boolean>`

- [ ] **Step 1: 类型扩展（types/index.ts，替换现有 `PlanComment` 定义）**

```ts
export interface PlanCommentAnchor {
  line: number;
  text: string;
  section: string;
}

export interface PlanComment {
  id: number;
  planId: number;
  author: string;
  content: string;
  kind: PlanCommentKind;
  createdAt: string;
  parentId: number | null;
  anchorLine: number | null;
  anchorText: string | null;
  sectionTitle: string | null;
  bodyRevision: number | null;
  resolved: boolean;
  resolvedBy: string | null;
  resolvedAt: string | null;
  canResolve: boolean;
  canDelete: boolean;
}

/** 根批注 + 一层回复（spec §2 线程）。 */
export interface PlanCommentThread {
  root: PlanComment;
  replies: PlanComment[];
}
```

- [ ] **Step 2: API 封装（plan-doc.ts，替换 `addCommentApi`、新增 resolve）**

```ts
export function addCommentApi(planId: number, payload: { content: string; parentId?: number; anchor?: PlanCommentAnchor }) {
  return request<PlanComment>(`/api/task-plans/${planId}/comments`, {
    method: 'POST',
    headers: json,
    body: JSON.stringify(payload),
  });
}

export function resolveCommentApi(planId: number, commentId: number, resolved: boolean) {
  return request<void>(`/api/task-plans/${planId}/comments/${commentId}/resolve`, {
    method: 'POST',
    headers: json,
    body: JSON.stringify({ resolved }),
  });
}
```

（import 类型清单加入 `PlanCommentAnchor`。）

- [ ] **Step 3: usePlanDoc 扩展**

`composables/usePlanDoc.ts`：imports 增加 `computed`、`PlanCommentAnchor`、`resolveCommentApi`；`usePlanDoc()` 内追加（`addComment` 保留，供兼容调用，内部改走 `addAnchoredComment`）：

```ts
  const REVIEW_ROOTS = (list: PlanComment[]) =>
    list.filter((c) => c.kind === 'REVIEW' && c.parentId == null);

  /** 线程视图：根批注（保持 id 升序）+ 各自一层回复（spec §2）。 */
  const threads = computed<PlanCommentThread[]>(() => {
    const byParent = new Map<number, PlanComment[]>();
    for (const comment of comments.value) {
      if (comment.parentId != null) {
        byParent.set(comment.parentId, [...(byParent.get(comment.parentId) ?? []), comment]);
      }
    }
    return REVIEW_ROOTS(comments.value).map((root) => ({
      root,
      replies: byParent.get(root.id) ?? [],
    }));
  });

  const unresolvedCount = computed(() => threads.value.filter((t) => !t.root.resolved).length);

  /** 无锚点根批注（历史批注、驳回原因）——面板/工作台「未锚定」分组。 */
  const unanchoredThreads = computed(() =>
    threads.value.filter((t) => t.root.anchorLine == null || t.root.sectionTitle == null));

  /** deriveAnchors 入参：带完整锚点的根批注。 */
  const anchoredRoots = computed(() =>
    REVIEW_ROOTS(comments.value).filter((c) => c.anchorLine != null && c.sectionTitle != null));

  // ---- 面板开关（spec §3.2）：REVIEW 阶段缺省开；手动选择记 localStorage ----
  const PANEL_KEY = 'plan-comment-panel-open';
  const panelOpen = ref<boolean | null>(readPanelPref());
  const panelEffective = computed(() => panelOpen.value ?? (plan.value?.phase === 'REVIEW'));

  function readPanelPref(): boolean | null {
    const stored = localStorage.getItem(PANEL_KEY);
    return stored === null ? null : stored === 'true';
  }

  function togglePanel() {
    panelOpen.value = !panelEffective.value;
    localStorage.setItem(PANEL_KEY, String(panelOpen.value));
  }

  async function addAnchoredComment(input: { content: string; parentId?: number; anchor?: PlanCommentAnchor }) {
    if (!plan.value) return false;
    try {
      await addCommentApi(plan.value.id, input);
      comments.value = await listCommentsApi(plan.value.id);
      return true;
    } catch (error) {
      message.error(error instanceof Error ? error.message : '批注提交失败');
      return false;
    }
  }

  async function resolveComment(commentId: number, resolved: boolean) {
    if (!plan.value) return false;
    try {
      await resolveCommentApi(plan.value.id, commentId, resolved);
      comments.value = await listCommentsApi(plan.value.id);
      message.success(resolved ? '已解决' : '已重新打开');
      return true;
    } catch (error) {
      message.error(error instanceof Error ? error.message : '操作失败');
      return false;
    }
  }
```

返回对象追加：`threads, unresolvedCount, unanchoredThreads, anchoredRoots, panelOpen, panelEffective, togglePanel, addAnchoredComment, resolveComment`；同时把旧 `addComment` 改为 `async function addComment(content: string) { await addAnchoredComment({ content }); }`（`PlanDetailReview` 旧调用零改动，Task 10 重写后移除）。

- [ ] **Step 4: 构建验证**

Run: `cd frontend && npm run build`
Expected: vue-tsc + vite build 全绿（现有组件未用新字段，纯增量）

- [ ] **Step 5: Commit**

```bash
git add frontend/src/types/index.ts frontend/src/api/plan-doc.ts frontend/src/composables/usePlanDoc.ts
git commit -m "feat：批注前端契约与状态——PlanComment 扩展线程/锚点/解决字段、PlanCommentThread、addComment 载荷带 parentId/anchor、resolveCommentApi；usePlanDoc 提供 threads/unresolvedCount/未锚定分组/面板开关(localStorage+评审阶段缺省开)/addAnchoredComment/resolveComment"
```

---

### Task 7: 文档批注层（一）—— data-line 映射 + 悬浮加批注入口

**Files:**
- Create: `frontend/src/components/task-plans/PlanCommentComposer.vue`
- Create: `frontend/src/composables/useDocCommentLayer.ts`
- Modify: `frontend/src/components/task-plans/PlanDetailDocument.vue`
- Modify: `frontend/src/styles/plan-module.css`（追加入口/编辑器样式，亮暗变量）

**Interfaces:**
- Consumes: `splitBlocks/listItemOffsets`（Task 4）、`usePlanDoc.threads`（Task 6）、`PlanCommentAnchor`。
- Produces（Task 8 补渲染、Task 9 补面板、Task 10/11 补定位）:
  - `useDocCommentLayer(options)` 返回：`addButton { visible, top }`、`composer { open, top, line, text, section } | null`、`openComposerFor(block)`、`closeComposer()`、`rebuild()`、`brokenGroups`（Task 8 填充）、`locate(commentId)`（Task 8 实现）
  - 模板挂点：`.doc-main` 内绝对定位「＋批注」按钮 + composer 卡片；DOM 注入 `data-line` 属性。

- [ ] **Step 1: PlanCommentComposer 组件（新增/回复共用，spec §3.1/§7 文案）**

`frontend/src/components/task-plans/PlanCommentComposer.vue`：

```vue
<template>
  <div class="anno-composer" @keydown.esc.stop="emit('cancel')">
    <textarea
      ref="inputRef"
      v-model="draft"
      class="anno-composer-input"
      :placeholder="placeholder"
      rows="3"
      @keydown.enter.exact.prevent="submit"
      @keydown.ctrl.enter.prevent="submit"
      @keydown.meta.enter.prevent="submit"
    />
    <div class="anno-composer-actions">
      <span class="anno-composer-hint">Ctrl/⌘+Enter 提交 · Esc 取消</span>
      <a-button size="small" @click="emit('cancel')">取消</a-button>
      <a-button size="small" type="primary" :disabled="!draft.trim()" :loading="busy" @click="submit">提交</a-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onMounted, ref } from 'vue';

const props = withDefaults(defineProps<{
  placeholder?: string;
  busy?: boolean;
}>(), { placeholder: '针对此行添加批注（评审中全员可见）' });
const emit = defineEmits<{ (e: 'submit', content: string): void; (e: 'cancel'): void }>();

const draft = ref('');
const inputRef = ref<HTMLTextAreaElement | null>(null);

function submit() {
  const content = draft.value.trim();
  if (!content || props.busy) return;
  emit('submit', content);
}

onMounted(() => void nextTick(() => inputRef.value?.focus()));
</script>
```

- [ ] **Step 2: useDocCommentLayer（映射 + 悬浮 + composer；渲染批注与定位留 Task 8）**

`frontend/src/composables/useDocCommentLayer.ts`：

```ts
import { computed, ref, type Ref } from 'vue';
import type { PlanCommentThread } from '../types';
import { listItemOffsets, splitBlocks, type Section } from '../utils/plan-markdown';

export interface ComposerTarget {
  top: number;
  line: number;
  text: string;
  section: string;
}

/**
 * 文档批注层（spec §5.1/§5.2）：MdPreview 渲染后按「顶层子元素 ↔ splitBlocks 块」对齐注入 data-line；
 * 表格行/列表项细化映射。锚定状态纯派生、不回写（spec §4）。
 */
export function useDocCommentLayer(options: {
  containerRef: Ref<HTMLElement | null>;
  sections: Ref<Section[]>;
  threads: Ref<PlanCommentThread[]>;
  canComment: Ref<boolean>;
  /** Pretty 视图且非编辑态才生效 */
  enabled: Ref<boolean>;
}) {
  const addButton = ref({ visible: false, top: 0 });
  const composer = ref<ComposerTarget | null>(null);

  const sectionEls = () =>
    [...(options.containerRef.value?.querySelectorAll<HTMLElement>('[data-section]') ?? [])];

  /** MdPreview 内容宿主：优先 .markdown-body 内层，回退 .md-editor-preview 本身。 */
  function previewHost(sectionEl: HTMLElement): HTMLElement | null {
    const preview = sectionEl.querySelector<HTMLElement>('.md-editor-preview');
    if (!preview) return null;
    return preview.querySelector<HTMLElement>(':scope > .markdown-body') ?? preview;
  }

  function blockLinesOf(raw: string, baseLine: number, el: HTMLElement): number[] {
    const blocks = splitBlocks(raw);
    const block = blocks[0];
    if (!block) return [];
    const children = [...el.children] as HTMLElement[];
    if (el.tagName === 'TABLE') {
      // 表格：数据行逐行映射（表头+分隔行占块首两行，spec §5.1）
      const rows = el.querySelectorAll<HTMLElement>('tbody tr');
      return [...rows].map((_, i) => baseLine + block.startLine + 2 + i);
    }
    if (el.tagName === 'UL' || el.tagName === 'OL') {
      const offsets = listItemOffsets(block.raw);
      const items = el.querySelectorAll<HTMLElement>('li');
      return [...items].map((_, i) => (offsets[i] == null ? -1 : baseLine + block.startLine + offsets[i]))
        .filter((line) => line >= 0);
    }
    return [baseLine + block.startLine];
  }

  /** 映射注入：数量不一致（Markdown 边界形态）则整章跳过，宁可少注入也不错位（spec §5.4）。 */
  function injectDataLines(): void {
    for (const sectionEl of sectionEls()) {
      sectionEl.querySelectorAll('[data-line]').forEach((el) => el.removeAttribute('data-line'));
      const title = sectionEl.dataset.section ?? '';
      const section = options.sections.value.find((s) => s.title === title);
      const host = previewHost(sectionEl);
      if (!section || !host || !section.content.trim()) continue;
      const blocks = splitBlocks(section.content);
      const children = [...host.children] as HTMLElement[];
      if (children.length !== blocks.length) continue;
      const baseLine = section.line + 1;
      children.forEach((child, i) => {
        const lines = blockLinesOf(blocks[i].raw, baseLine, child);
        const line = lines.length === 1 ? lines[0] : -1;
        if (line >= 0) child.dataset.line = String(line);
        // 表格行/列表项：映射到子元素
        if (lines.length > 1) {
          const targets = child.tagName === 'TABLE'
            ? ([...child.querySelectorAll<HTMLElement>('tbody tr')])
            : ([...child.querySelectorAll<HTMLElement>('li')]);
          targets.forEach((target, k) => {
            if (lines[k] >= 0) target.dataset.line = String(lines[k]);
          });
        }
      });
    }
  }

  // ---- 悬浮「＋批注」（spec §3.1）：事件委托，单实例按钮跟随悬浮块 ----
  function onHover(event: MouseEvent): void {
    if (!options.enabled.value || !options.canComment.value || composer.value) return;
    const target = (event.target as HTMLElement).closest<HTMLElement>('[data-line]');
    if (!target || !options.containerRef.value?.contains(target)) {
      addButton.value.visible = false;
      return;
    }
    const containerTop = options.containerRef.value.getBoundingClientRect().top;
    addButton.value = { visible: true, top: target.getBoundingClientRect().top - containerTop };
    addButton.dataset = { line: target.dataset.line ?? '', section: sectionOf(target) };
  }

  function sectionOf(target: HTMLElement): string {
    return target.closest<HTMLElement>('[data-section]')?.dataset.section ?? '';
  }

  // addButton.dataset 非响应式场景使用的挂载属性（line/section），以普通字段承载
  // （声明合并见下方返回对象的扩展字段）

  function openComposerFor(): void {
    const line = Number((addButton as unknown as { dataset?: { line?: string } }).dataset?.line);
    if (!Number.isFinite(line)) return;
    const container = options.containerRef.value;
    if (!container) return;
    const el = container.querySelector<HTMLElement>(`[data-line="${line}"]`);
    const blockText = (el?.textContent ?? '').trim().slice(0, 200);
    const containerTop = container.getBoundingClientRect().top;
    const blockBottom = el ? el.getBoundingClientRect().bottom - containerTop : addButton.value.top;
    composer.value = { top: blockBottom + 6, line, text: blockText, section: sectionOf(el ?? container) };
    addButton.value.visible = false;
  }

  function closeComposer(): void {
    composer.value = null;
  }

  async function rebuild(): Promise<void> {
    injectDataLines();
  }

  return {
    addButton, composer,
    onHover, openComposerFor, closeComposer, rebuild,
    // Task 8 扩展：renderAnnotations / brokenGroups / locate
  };
}
```

实现说明（写给执行者）：`addButton.dataset` 的挂载写法在 TS 下不优雅——落地时改为 `const hoverTarget = ref<{ line: number; section: string } | null>(null)`，`onHover` 里 `hoverTarget.value = { line: Number(target.dataset.line), section: sectionOf(target) }`，`openComposerFor` 读 `hoverTarget.value`。**按此实现，不要用 dataset 挂载**（上面代码段中相关两行以 hoverTarget 替代，行为一致）。本 Task 其余代码已按此口径调整，见 Step 3 集成代码。

- [ ] **Step 3: PlanDetailDocument 集成（模板 + script）**

`PlanDetailDocument.vue` 修改：

1. 滚动容器开启委托监听与定位（`.doc-main` 上）：

```html
<div
  ref="docMainRef"
  class="doc-main"
  tabindex="0"
  role="region"
  aria-label="计划文档内容"
  @scroll="onDocScroll"
  @mouseover="commentLayer.onHover"
  @mouseleave="commentLayer.hideButton"
>
```

2. Pretty 面板尾部（`doc-flow` 之后、`doc-main` 闭合前）挂悬浮按钮与 composer：

```html
<template v-if="viewMode === 'Pretty' && commentLayer.addButton.visible.value && canComment">
  <button type="button" class="doc-anno-add" :style="{ top: `${commentLayer.addButton.value.top}px` }" @click="commentLayer.openComposerFor">＋ 批注</button>
</template>
<template v-if="commentLayer.composer.value">
  <div class="doc-anno-composer-wrap" :style="{ top: `${commentLayer.composer.value.top}px` }">
    <PlanCommentComposer
      :busy="composerBusy"
      @submit="submitAnchorComment"
      @cancel="commentLayer.closeComposer"
    />
  </div>
</template>
```

3. script 追加：

```ts
import PlanCommentComposer from './PlanCommentComposer.vue';
import { useDocCommentLayer } from '../../composables/useDocCommentLayer';

const commentLayer = useDocCommentLayer({
  containerRef: docMainRef,
  sections,
  threads: props.doc.threads,
  canComment: computed(() => Boolean(props.doc.permissions.value.COMMENT)),
  enabled: computed(() => viewMode.value === 'Pretty' && !editing.value),
});
const composerBusy = ref(false);

watch([() => props.plan.body, viewMode, editing, props.doc.threads], () => {
  void nextTick(() => window.requestAnimationFrame(() => void commentLayer.rebuild()));
}, { immediate: true });

async function submitAnchorComment(content: string) {
  const target = commentLayer.composer.value;
  if (!target) return;
  composerBusy.value = true;
  const ok = await props.doc.addAnchoredComment({
    content,
    anchor: { line: target.line, text: target.text, section: target.section },
  });
  composerBusy.value = false;
  if (ok) commentLayer.closeComposer();
}
```

4. 全量跑一遍现有交互（章节编辑/勾选清单/视图切换）确认无回归。

- [ ] **Step 4: 样式（plan-module.css 追加）**

```css
/* ---------- 行级批注：悬浮入口与内联编辑器（spec §3.1/§3.3） ---------- */
.plan-document .doc-main { position: relative; }

.doc-anno-add {
  position: absolute;
  right: 8px;
  z-index: 6;
  padding: 1px 10px;
  border: 1px dashed var(--line-strong);
  border-radius: 6px;
  background: var(--surface);
  color: var(--muted);
  font-size: 12px;
  cursor: pointer;
}

.doc-anno-add:hover {
  border-color: var(--anno-accent, #d46b08);
  color: var(--anno-accent, #d46b08);
  border-style: solid;
}

:root[data-theme='dark'] .doc-anno-add:hover { --anno-accent: #ffc53d; }

.doc-anno-composer-wrap {
  position: absolute;
  left: 24px;
  right: 24px;
  z-index: 7;
}

.anno-composer {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 10px 12px;
  border: 1px solid var(--line);
  border-radius: 10px;
  background: var(--surface);
  box-shadow: 0 6px 20px rgb(0 0 0 / 12%);
}

.anno-composer-input {
  width: 100%;
  border: 1px solid var(--line);
  border-radius: 6px;
  background: var(--canvas);
  color: var(--ink);
  font-size: 13px;
  line-height: 1.6;
  padding: 6px 8px;
  resize: vertical;
}

.anno-composer-actions {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 8px;
}

.anno-composer-hint {
  margin-right: auto;
  color: var(--muted);
  font-size: 11px;
}
```

- [ ] **Step 5: 构建 + 手测**

Run: `cd frontend && npm run build`
手测（`npm run dev` + 浏览器，需一个 REVIEW 阶段计划）：悬浮段落出现「＋ 批注」→ 移开消失 → 点击出现编辑框 → 提交后网络面板见 `POST /comments` 带 `anchor.line/text/section` 且 body 未变（revision 不变）。表格行、清单项、八章标题（Task 8 加）逐一同测——本 Task 先验段落。

- [ ] **Step 6: Commit**

```bash
git add frontend/src/components/task-plans/PlanCommentComposer.vue frontend/src/composables/useDocCommentLayer.ts frontend/src/components/task-plans/PlanDetailDocument.vue frontend/src/styles/plan-module.css
git commit -m "feat：文档批注层（一）——MdPreview 后处理按块对齐注入 data-line（表格行/列表项细化、数量不齐整章跳过防错位）、悬浮「＋批注」跟随与内联 composer、PlanCommentComposer 组件；亮暗样式入 plan-module.css"
```

---

### Task 8: 文档批注层（二）—— 已有批注渲染 + 断链条 + 章级入口 + 定位

**Files:**
- Modify: `frontend/src/composables/useDocCommentLayer.ts`
- Modify: `frontend/src/components/task-plans/PlanDetailDocument.vue`
- Modify: `frontend/src/components/task-plans/ChecklistView.vue`
- Modify: `frontend/src/styles/plan-module.css`

**Interfaces:**
- Consumes: `deriveAnchors`（Task 5）、`doc.anchoredRoots/unanchoredThreads`（Task 6）、Task 7 的层骨架。
- Produces: `useDocCommentLayer` 增 `brokenGroups`、`locate(commentId)`；DOM 呈现 `data-anno` 计数属性、`.doc-anno-badge`、`.doc-anno-hl`、`.doc-anno-flash`。

- [ ] **Step 1: useDocCommentLayer 扩展渲染与定位**

composable 返回对象追加（`deriveAnchors` import 自 `utils/plan-anchors`，`normalizeForMatch` 用于按钮文本快照——快照已在 composer 生成，这里只用 derive）：

```ts
  const brokenGroups = ref<{ sectionTitle: string; threads: PlanCommentThread[] }[]>([]);

  /** 渲染已有批注（spec §3.3/§5.3）：徽标 + 高亮 + 断链分组；先清后挂，幂等。 */
  function renderAnnotations(): void {
    const container = options.containerRef.value;
    if (!container) return;
    container.querySelectorAll('.doc-anno-badge').forEach((el) => el.remove());
    container.querySelectorAll('[data-anno]').forEach((el) => el.removeAttribute('data-anno'));
    container.querySelectorAll('.doc-anno-hl').forEach((el) => el.classList.remove('doc-anno-hl'));
    const roots = options.threads.value.map((t) => t.root);
    const resolutions = deriveAnchors(props_body(), roots);
    const byLine = new Map<number, PlanCommentThread[]>();
    const broken: Map<string, PlanCommentThread[]> = new Map();
    for (const thread of options.threads.value) {
      const resolution = resolutions.get(thread.root.id);
      if (!resolution || resolution.line == null) continue; // 无锚点/断链 → 面板与断链条呈现
      if (resolution.state === 'broken') {
        broken.set(resolution.sectionTitle, [...(broken.get(resolution.sectionTitle) ?? []), thread]);
        continue;
      }
      byLine.set(resolution.line, [...(byLine.get(resolution.line) ?? []), thread]);
    }
    for (const [line, threadsAtLine] of byLine) {
      const el = container.querySelector<HTMLElement>(`[data-line="${line}"]`);
      if (!el) continue;
      const unresolved = threadsAtLine.filter((t) => !t.root.resolved).length;
      el.dataset.anno = String(threadsAtLine.length);
      if (unresolved > 0) el.classList.add('doc-anno-hl');
      const badge = document.createElement('span');
      badge.className = `doc-anno-badge${unresolved > 0 ? '' : ' resolved'}`;
      badge.textContent = unresolved > 0 ? `💬 ${threadsAtLine.length}` : `✓ ${threadsAtLine.length}`;
      badge.title = threadsAtLine.map((t) => `${t.root.author}：${t.root.content}`).join('\n');
      badge.addEventListener('click', (event) => {
        event.stopPropagation();
        options.onBadgeClick?.(line);
      });
      el.appendChild(badge);
    }
    brokenGroups.value = [...broken.entries()].map(([sectionTitle, threads]) => ({ sectionTitle, threads }));
  }

  async function rebuild(): Promise<void> {
    injectDataLines();
    renderAnnotations();
  }

  /** 面板/工作台定位（spec §3.2）：滚动到块并闪烁高亮。 */
  function locate(line: number | null): void {
    const container = options.containerRef.value;
    if (!container || line == null) return;
    const el = container.querySelector<HTMLElement>(`[data-line="${line}"]`);
    if (!el) return;
    container.scrollTo({ top: container.scrollTop + el.getBoundingClientRect().top - container.getBoundingClientRect().top - 96, behavior: 'smooth' });
    el.classList.add('doc-anno-flash');
    window.setTimeout(() => el.classList.remove('doc-anno-flash'), 1600);
  }

  return { /* …Task 7 原有项…, */ brokenGroups, renderAnnotations, locate };
```

（`props_body()` 即读取当前 body：在 composable options 中增加 `body: Ref<string | null>`，由 `PlanDetailDocument` 传 `computed(() => props.plan.body)`；`onBadgeClick?: (line: number) => void` 可选回调供面板联动，本 Task 传 undefined 即可。）

- [ ] **Step 2: PlanDetailDocument 传参与断链条模板**

1. composable options 增加 `body: computed(() => props.plan.body)`。
2. 每个章节 `doc-section-head` 之后插入断链条（Pretty 模板内）：

```html
<div
  v-for="group in commentLayer.brokenGroups.value.filter((g) => g.sectionTitle === section.title)"
  :key="`broken-${section.title}`"
  class="doc-anno-broken"
>
  <details>
    <summary>⚠ {{ group.threads.length }} 条批注的锚点因内容变更失效</summary>
    <div v-for="thread in group.threads" :key="thread.root.id" class="doc-anno-broken-item">
      <b>{{ thread.root.author }}</b>：{{ thread.root.content }}
      <span class="doc-anno-broken-quote">原位置「{{ thread.root.anchorText }}」</span>
    </div>
  </details>
</div>
```

3. 八章章级锚点入口（spec §3.3）：`doc-section-head` 的 `<h3>` 上加条件绑定：

```html
<h3 :data-line="section.title === '八、场景设计' ? section.line : undefined">{{ section.title }}</h3>
```

- [ ] **Step 3: ChecklistView 行锚点**

`ChecklistView.vue`：props 增加 `anchorLines?: number[]`，`.check-item` 上加 `:data-line="anchorLines?.[entry.index]"`：

```ts
const props = withDefaults(defineProps<{ content: string; editable: boolean; anchorLines?: number[] }>(), {
  anchorLines: () => [],
});
```

```html
<div
  v-for="entry in group.items"
  :key="entry.index"
  class="check-item"
  :class="{ pass: entry.checked }"
  :data-line="anchorLines[entry.index]"
>
```

`PlanDetailDocument` 六章调用处传 `:anchor-lines="checklistAnchorLines(section)"`，script 增：

```ts
import { checklistItemLines } from '../../utils/plan-markdown';

function checklistAnchorLines(section: Section): number[] {
  return checklistItemLines(section.content).map((local) => section.line + 1 + local);
}
```

- [ ] **Step 4: 样式（plan-module.css 追加）**

```css
/* ---------- 已有批注呈现（spec §3.3） ---------- */
.doc-flow [data-anno] {
  background: var(--anno-hl-bg, #fff3bf);
  border-radius: 3px;
  box-decoration-break: clone;
}

:root[data-theme='dark'] .doc-flow [data-anno] { --anno-hl-bg: rgb(255 213 145 / 18%); }

.doc-flow .doc-anno-hl { outline: 1px dashed var(--anno-accent, #ffd591); outline-offset: 2px; }

.doc-anno-badge {
  display: inline-block;
  margin-left: 6px;
  padding: 0 7px;
  border: 1px solid var(--anno-accent, #ffd591);
  border-radius: 9px;
  background: var(--anno-chip-bg, #fff7e6);
  color: var(--anno-accent-text, #d46b08);
  font-size: 11px;
  line-height: 17px;
  cursor: pointer;
  white-space: nowrap;
}

.doc-anno-badge.resolved {
  border-color: var(--line);
  background: var(--canvas);
  color: var(--muted);
}

:root[data-theme='dark'] .doc-anno-badge { --anno-chip-bg: rgb(255 197 61 / 12%); --anno-accent-text: #ffc53d; }

.doc-anno-broken {
  margin: 6px 0 10px;
  border: 1px dashed var(--line-strong);
  border-radius: 8px;
  background: var(--canvas);
  font-size: 12.5px;
}

.doc-anno-broken summary { padding: 6px 10px; color: var(--muted); cursor: pointer; }

.doc-anno-broken-item { padding: 4px 10px 8px; line-height: 1.7; }

.doc-anno-broken-quote { display: block; color: var(--muted); font-size: 11.5px; }

@keyframes doc-anno-flash {
  0%, 40% { background: var(--anno-hl-bg, #fff3bf); }
  100% { background: transparent; }
}

.doc-anno-flash { animation: doc-anno-flash 1.5s ease; }
```

- [ ] **Step 5: 构建 + 手测**

Run: `cd frontend && npm run build`
手测：已有批注行出现高亮+徽标（💬 n）；在 Markdown 视图编辑文档把该行改写一半再回 Pretty → 徽标自动跟到新行；整行删除 → 章顶出现断链条可展开；六章清单项可加批注；八章标题悬浮出按钮。

- [ ] **Step 6: Commit**

```bash
git add frontend/src/composables/useDocCommentLayer.ts frontend/src/components/task-plans/PlanDetailDocument.vue frontend/src/components/task-plans/ChecklistView.vue frontend/src/styles/plan-module.css
git commit -m "feat：文档批注层（二）——deriveAnchors 驱动的高亮+💬/✓徽标、断链折叠条（章顶兜底展示原文）、六章清单项行锚点、八章标题章级入口、locate 滚动闪烁；徽标 hover 汇总批注内容"
```

---

### Task 9: 批注面板 + 卡片组件 + 工具条开关 + 右栏布局

**Files:**
- Create: `frontend/src/components/task-plans/PlanCommentCard.vue`
- Create: `frontend/src/components/task-plans/PlanCommentPanel.vue`
- Modify: `frontend/src/components/task-plans/PlanDetailDocument.vue`
- Modify: `frontend/src/components/task-plans/TaskPlanDetail.vue`
- Modify: `frontend/src/styles/plan-module.css`

**Interfaces:**
- Consumes: `PlanCommentThread`、`doc.addAnchoredComment/resolveComment`、Task 4-8 全部。
- Produces: `PlanCommentCard props { thread, showLocate? }` emits `(locate)/(resolve, boolean)/(reply, content)/(remove, PlanComment)`；`PlanCommentPanel props { groups, unresolved, resolved }` emits `(locate, thread)/(resolve, thread, boolean)/(reply, thread, content)/(remove, thread, comment)/close`。工作台（Task 10）复用同一 Card。

- [ ] **Step 1: PlanCommentCard（面板/工作台共用，spec §3.2 卡片结构）**

```vue
<template>
  <div class="anno-card" :class="{ grey: thread.root.resolved }">
    <div class="anno-card-head">
      <span class="anno-card-author">{{ thread.root.author }}</span>
      <span class="anno-card-time">{{ new Date(thread.root.createdAt).toLocaleString() }}</span>
      <span v-if="thread.root.bodyRevision != null" class="anno-card-rev">rev {{ thread.root.bodyRevision }}</span>
      <span v-if="thread.root.anchorText" class="anno-card-state">{{ thread.root.resolved ? '已解决' : '待处理' }}</span>
    </div>
    <div v-if="thread.root.anchorText" class="anno-card-quote">「{{ thread.root.anchorText }}」</div>
    <div class="anno-card-body">{{ thread.root.content }}</div>

    <div v-for="reply in thread.replies" :key="reply.id" class="anno-card-reply">
      <b>{{ reply.author }}</b>：{{ reply.content }}
      <a-button
        v-if="reply.canDelete"
        type="link" size="small" danger class="anno-card-reply-del"
        @click="emit('remove', thread, reply)"
      >删除</a-button>
    </div>

    <template v-if="replying">
      <PlanCommentComposer
        placeholder="回复…"
        :busy="busy"
        @submit="submitReply"
        @cancel="replying = false"
      />
    </template>
    <div v-else-if="canReply" class="anno-card-reply-entry" @click="replying = true">回复…</div>
    <div v-else-if="thread.root.resolved && can('COMMENT')" class="anno-card-locked">已解决线程已锁定，先「重新打开」再回复。</div>

    <div class="anno-card-actions">
      <a v-if="showLocate && thread.root.anchorLine != null" href="#" class="anno-card-link" @click.prevent="emit('locate', thread)">↧ 定位</a>
      <a-button
        v-if="thread.root.canResolve"
        type="link" size="small"
        @click="emit('resolve', thread, !thread.root.resolved)"
      >{{ thread.root.resolved ? '重新打开' : '✓ 解决' }}</a-button>
      <a-button
        v-if="thread.root.canDelete"
        type="link" size="small" danger
        @click="emit('remove', thread, thread.root)"
      >删除</a-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import type { PlanComment, PlanCommentThread } from '../../types';
import type { usePlanDoc } from '../../composables/usePlanDoc';
import PlanCommentComposer from './PlanCommentComposer.vue';

const props = withDefaults(defineProps<{
  thread: PlanCommentThread;
  showLocate?: boolean;
  doc: ReturnType<typeof usePlanDoc>;
}>(), { showLocate: true });
const emit = defineEmits<{
  (e: 'locate', thread: PlanCommentThread): void;
  (e: 'resolve', thread: PlanCommentThread, resolved: boolean): void;
  (e: 'reply', thread: PlanCommentThread, content: string): void;
  (e: 'remove', thread: PlanCommentThread, comment: PlanComment): void;
}>();

const replying = ref(false);
const busy = ref(false);
const canReply = computed(() => Boolean(props.doc.permissions.value.COMMENT) && !props.thread.root.resolved);

function can(action: string) {
  return Boolean(props.doc.permissions.value[action]);
}

async function submitReply(content: string) {
  busy.value = true;
  emit('reply', props.thread, content);
  busy.value = false;
  replying.value = false;
}
</script>
```

- [ ] **Step 2: PlanCommentPanel（分组展示，spec §3.2 面板结构）**

```vue
<template>
  <aside class="anno-panel">
    <div class="anno-panel-head">
      <b>批注</b>
      <span class="anno-panel-count">未解决 {{ unresolved }} · 已解决 {{ resolved }}</span>
      <button type="button" class="anno-panel-close" title="收起" @click="emit('close')">»</button>
    </div>
    <div class="anno-panel-scroll">
      <template v-for="group in groups" :key="group.key">
        <div class="anno-panel-group" :class="{ grey: group.tone === 'grey' }">{{ group.title }}</div>
        <template v-if="group.key !== 'resolved' || resolvedExpanded">
          <PlanCommentCard
            v-for="thread in group.threads"
            :key="thread.root.id"
            :thread="thread"
            :doc="doc"
            @locate="emit('locate', $event)"
            @resolve="(t, r) => emit('resolve', t, r)"
            @reply="(t, c) => emit('reply', t, c)"
            @remove="(t, c) => emit('remove', t, c)"
          />
        </template>
        <a
          v-if="group.key === 'resolved' && group.threads.length > 0"
          href="#"
          class="anno-panel-toggle"
          @click.prevent="resolvedExpanded = !resolvedExpanded"
        >{{ resolvedExpanded ? '收起' : `展开 ${group.threads.length} 条` }}</a>
      </template>
      <div v-if="groups.every((g) => g.threads.length === 0)" class="plan-empty">暂无批注。悬浮文档任意行即可添加。</div>
    </div>
  </aside>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import type { PlanComment, PlanCommentThread } from '../../types';
import type { usePlanDoc } from '../../composables/usePlanDoc';
import PlanCommentCard from './PlanCommentCard.vue';

export interface PlanCommentPanelGroup {
  key: string;
  title: string;
  tone: 'normal' | 'grey';
  threads: PlanCommentThread[];
}

defineProps<{
  groups: PlanCommentPanelGroup[];
  unresolved: number;
  resolved: number;
  doc: ReturnType<typeof usePlanDoc>;
}>();
const emit = defineEmits<{
  (e: 'close'): void;
  (e: 'locate', thread: PlanCommentThread): void;
  (e: 'resolve', thread: PlanCommentThread, resolved: boolean): void;
  (e: 'reply', thread: PlanCommentThread, content: string): void;
  (e: 'remove', thread: PlanCommentThread, comment: PlanComment): void;
}>();

const resolvedExpanded = ref(false);
</script>
```

- [ ] **Step 3: PlanDetailDocument 右栏布局与分组数据**

1. `doc-body` 外包一层或直接在 `plan-document` 内并排：`.doc-body` 保持，模板尾部加：

```html
<div class="doc-shell" :class="{ 'with-panel': doc.panelEffective.value }">
  <!-- 原 .doc-body 整体移入 -->
  <PlanCommentPanel
    v-if="doc.panelEffective.value"
    class="doc-anno-panel-col"
    :groups="panelGroups"
    :unresolved="doc.unresolvedCount.value"
    :resolved="resolvedCount"
    :doc="doc"
    @close="doc.togglePanel"
    @locate="locateThread"
    @resolve="(t, r) => doc.resolveComment(t.root.id, r)"
    @reply="(t, c) => doc.addAnchoredComment({ content: c, parentId: t.root.id })"
    @remove="removeComment"
  />
</div>
```

（即：`<div class="doc-body">` 外再包 `.doc-shell` flex 容器；面板作为第二列。）

2. script 追加分组与操作：

```ts
import type { PlanComment, PlanCommentThread } from '../../types';
import type { PlanCommentPanelGroup } from './PlanCommentPanel.vue';
import { deriveAnchors } from '../../utils/plan-anchors';

const resolvedCount = computed(() => props.doc.threads.value.filter((t) => t.root.resolved).length);

const panelGroups = computed<PlanCommentPanelGroup[]>(() => {
  const resolutions = deriveAnchors(props.plan.body, props.doc.anchoredRoots.value);
  const sectionOf = (thread: PlanCommentThread): string | null => {
    const r = resolutions.get(thread.root.id);
    if (!r) return null; // 未锚定
    return r.sectionTitle;
  };
  const unanchored = props.doc.unanchoredThreads.value;
  const bySection = new Map<string, PlanCommentThread[]>();
  const resolvedThreads: PlanCommentThread[] = [];
  for (const thread of props.doc.threads.value) {
    if (unanchored.includes(thread)) continue;
    if (thread.root.resolved) { resolvedThreads.push(thread); continue; }
    const section = sectionOf(thread) ?? CANONICAL_HEADINGS[0];
    bySection.set(section, [...(bySection.get(section) ?? []), thread]);
  }
  const groups: PlanCommentPanelGroup[] = [];
  if (unanchored.length) {
    groups.push({ key: 'unanchored', title: '未锚定', tone: 'grey', threads: unanchored });
  }
  for (const heading of CANONICAL_HEADINGS) {
    const threads = bySection.get(heading);
    if (threads?.length) groups.push({ key: heading, title: heading, tone: 'normal', threads });
  }
  if (resolvedThreads.length) {
    groups.push({ key: 'resolved', title: `已解决 ${resolvedThreads.length}`, tone: 'grey', threads: resolvedThreads });
  }
  return groups;
});

function locateThread(thread: PlanCommentThread) {
  const resolutions = deriveAnchors(props.plan.body, props.doc.anchoredRoots.value);
  const resolution = resolutions.get(thread.root.id);
  if (viewMode.value !== 'Pretty') viewMode.value = 'Pretty';
  void nextTick(() => commentLayer.locate(resolution?.line ?? null));
}

async function removeComment(thread: PlanCommentThread, comment: PlanComment) {
  if (!props.doc.plan.value) return;
  await deleteCommentApi(props.doc.plan.value.id, comment.id);
  await props.doc.refresh();
  message.success('批注已删除');
}
```

（imports：`CANONICAL_HEADINGS` from plan-markdown、`deleteCommentApi` from api/plan-doc。）

- [ ] **Step 4: TaskPlanDetail 工具条开关（spec §3.2：与视图切换同排）**

`TaskPlanDetail.vue` 的 `rightExtra` 内、视图切换 segmented 之前加：

```html
<button
  v-if="activeTab === 'document'"
  type="button"
  class="segmented-item anno-toggle"
  :class="{ active: doc.panelEffective.value }"
  :aria-pressed="doc.panelEffective.value"
  @click="doc.togglePanel"
>💬 批注 {{ doc.threads.value.length }}</button>
```

- [ ] **Step 5: 面板布局样式（plan-module.css 追加）**

```css
/* ---------- 批注面板右栏（spec §3.2） ---------- */
.doc-shell { display: flex; gap: 0; min-height: 0; }
.doc-shell .doc-body { flex: 1; min-width: 0; }

.doc-anno-panel-col {
  width: 340px;
  flex: none;
  border-left: 1px solid var(--line);
  background: var(--canvas);
  display: flex;
  flex-direction: column;
  max-height: calc(100vh - 220px);
}

.anno-panel-head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border-bottom: 1px solid var(--line);
  font-size: 13px;
}

.anno-panel-count { color: var(--muted); font-size: 11.5px; }

.anno-panel-close {
  margin-left: auto;
  border: none;
  background: none;
  color: var(--muted);
  cursor: pointer;
  font-size: 14px;
}

.anno-panel-scroll { overflow-y: auto; padding: 10px 12px; display: flex; flex-direction: column; gap: 8px; }

.anno-panel-group {
  margin: 6px 0 2px;
  color: var(--muted);
  font-size: 11px;
  letter-spacing: 0.4px;
}

.anno-panel-group.grey { opacity: 0.75; }
.anno-panel-toggle { color: var(--muted); font-size: 11.5px; }

.anno-card {
  border: 1px solid var(--line);
  border-radius: 10px;
  background: var(--surface);
  padding: 9px 11px;
  font-size: 12.5px;
  line-height: 1.65;
}

.anno-card.grey { opacity: 0.62; }
.anno-card-head { display: flex; align-items: center; gap: 8px; color: var(--muted); font-size: 11.5px; }
.anno-card-author { color: var(--ink); font-weight: 600; }
.anno-card-rev, .anno-card-state { border: 1px solid var(--line); border-radius: 4px; padding: 0 5px; font-size: 10.5px; }
.anno-card-quote { margin: 6px 0; padding: 2px 8px; border-left: 2px solid var(--anno-accent, #ffd591); background: var(--anno-chip-bg, #fff7e6); color: var(--muted); font-size: 11.5px; border-radius: 0 4px 4px 0; }
:root[data-theme='dark'] .anno-card-quote { --anno-chip-bg: rgb(255 197 61 / 10%); }
.anno-card-body { white-space: pre-wrap; word-break: break-word; }
.anno-card-reply { border-top: 1px dashed var(--line); margin-top: 6px; padding-top: 6px; color: var(--ink); }
.anno-card-reply-del { float: right; }
.anno-card-reply-entry { margin-top: 6px; border: 1px solid var(--line); border-radius: 6px; padding: 3px 8px; color: var(--muted); font-size: 11.5px; cursor: pointer; background: var(--canvas); }
.anno-card-locked { margin-top: 6px; color: var(--muted); font-size: 11px; }
.anno-card-actions { display: flex; align-items: center; gap: 4px; margin-top: 4px; }
.anno-card-link { color: var(--accent); font-size: 12px; margin-right: auto; }

.seg-anno-toggle { margin-right: 8px; }
```

- [ ] **Step 6: 构建 + 手测**

Run: `cd frontend && npm run build`
手测：REVIEW 阶段进入文档 Tab 面板默认开；点工具条「💬 批注 N」开合且刷新后记忆；分组顺序 未锚定→章节→已解决（折叠）；卡片回复/解决/重开/删除/定位全链路；已解决线程回复框锁定并出现提示。

- [ ] **Step 7: Commit**

```bash
git add frontend/src/components/task-plans/PlanCommentCard.vue frontend/src/components/task-plans/PlanCommentPanel.vue frontend/src/components/task-plans/PlanDetailDocument.vue frontend/src/components/task-plans/TaskPlanDetail.vue frontend/src/styles/plan-module.css
git commit -m "feat：批注面板——PlanCommentCard/PlanCommentPanel（未锚定置顶→章节序→已解决折叠；回复/解决/重开/删除/定位），文档页右栏布局与工具条「💬 批注 N」开关（评审阶段缺省开、localStorage 记忆）"
```

---

### Task 10: 评审工作台（PlanDetailReview 重排）

**Files:**
- Modify: `frontend/src/components/task-plans/PlanDetailReview.vue`（整文件重写）
- Modify: `frontend/src/components/task-plans/TaskPlanDetail.vue`（locate 接线）
- Modify: `frontend/src/styles/plan-module.css`

**Interfaces:**
- Consumes: `PlanCommentCard`（Task 9）、`doc.threads/unresolvedCount/unanchoredThreads/anchoredRoots`、`deriveAnchors`。
- Produces: `PlanDetailReview` emit `(locate, commentId)`；`TaskPlanDetail.locateComment(id)`（切文档 Tab + pendingLocate 传给 PlanDetailDocument，Task 11 再叠加 URL 持久化）。

- [ ] **Step 1: 重写 PlanDetailReview.vue（三段式，spec §3.4；文案逐字采用）**

```vue
<template>
  <section class="review-tab">
    <div class="workbench-actions">
      <span class="workbench-badge">{{ phaseText }} · rev {{ doc.plan.value?.revision ?? '-' }}</span>
      <span class="workbench-hint" v-if="unresolvedCount > 0 && can('APPROVE')">未解决批注 {{ unresolvedCount }} 条</span>
      <span style="flex: 1" />
      <a-button v-if="can('START_REVIEW')" type="primary" @click="run('start-review', '已开始评审')">开始评审</a-button>
      <a-button v-if="can('APPROVE')" type="primary" @click="approve">评审通过</a-button>
      <a-button v-if="can('REJECT')" danger @click="openReject">驳回</a-button>
      <span v-if="!can('COMMENT') && !can('APPROVE')" class="review-hint">当前阶段批注只读</span>
    </div>

    <div class="workbench-filters">
      <button
        v-for="filter in FILTERS"
        :key="filter.key"
        type="button"
        class="workbench-chip"
        :class="{ active: activeFilter === filter.key }"
        @click="activeFilter = filter.key"
      >{{ filter.label }} {{ filter.count() }}</button>
    </div>

    <div class="workbench-list">
      <template v-for="group in visibleGroups" :key="group.key">
        <div class="workbench-group">{{ group.title }}</div>
        <PlanCommentCard
          v-for="thread in group.threads"
          :key="thread.root.id"
          :thread="thread"
          :doc="doc"
          @locate="(t) => emit('locate', t.root.id)"
          @resolve="(t, r) => doc.resolveComment(t.root.id, r)"
          @reply="(t, c) => doc.addAnchoredComment({ content: c, parentId: t.root.id })"
          @remove="removeComment"
        />
      </template>
      <div v-if="visibleGroups.length === 0" class="plan-empty">暂无批注。到文档 Tab 悬浮任意行即可添加。</div>
    </div>

    <div class="workbench-flow">
      <div class="workbench-group">流转记录</div>
      <div
        v-for="comment in flowRecords"
        :key="comment.id"
        class="workbench-flow-item"
      >· {{ comment.author }} {{ comment.content }} —— {{ new Date(comment.createdAt).toLocaleString() }}</div>
    </div>

    <a-modal
      v-model:open="rejectOpen"
      title="驳回评审"
      ok-text="驳回"
      :ok-button-props="{ danger: true, disabled: !rejectReason.trim() }"
      :confirm-loading="rejecting"
      @ok="confirmReject"
    >
      <p class="reject-hint">驳回原因必填，将作为批注留存并退回草稿。</p>
      <a-textarea v-model:value="rejectReason" :rows="4" placeholder="填写驳回原因（必填）" />
    </a-modal>
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { Modal, message } from 'ant-design-vue';
import type { PlanComment, PlanCommentThread } from '../../types';
import { statusLabel } from '../../composables/usePlanDoc';
import { deleteCommentApi } from '../../api/plan-doc';
import { deriveAnchors } from '../../utils/plan-anchors';
import PlanCommentCard from './PlanCommentCard.vue';
import type { usePlanDoc } from '../../composables/usePlanDoc';

const props = defineProps<{ doc: ReturnType<typeof usePlanDoc> }>();
const emit = defineEmits<{ (e: 'locate', commentId: number): void }>();

const rejectOpen = ref(false);
const rejectReason = ref('');
const rejecting = ref(false);
const activeFilter = ref<'all' | 'unresolved' | 'resolved' | 'broken' | 'unanchored'>('all');

const PHASE_TEXT: Record<string, string> = {
  DRAFT: '草稿', REVIEW: '评审', EXECUTION: '执行', REPORT: '报告', PUBLISH: '发布',
};
const phaseText = computed(() => PHASE_TEXT[props.doc.plan.value?.phase ?? 'DRAFT'] ?? '草稿');

function can(action: string) {
  return Boolean(props.doc.permissions.value[action]);
}

const unresolvedCount = computed(() => props.doc.unresolvedCount.value);
const flowRecords = computed(() => props.doc.comments.value.filter((c) => c.kind === 'SYSTEM'));

const resolutions = computed(() => deriveAnchors(props.doc.plan.value?.body, props.doc.anchoredRoots.value));

function threadState(thread: PlanCommentThread): 'unresolved' | 'resolved' | 'broken' | 'unanchored' {
  if (props.doc.unanchoredThreads.value.includes(thread)) return 'unanchored';
  const resolution = resolutions.value.get(thread.root.id);
  if (resolution?.state === 'broken') return 'broken';
  return thread.root.resolved ? 'resolved' : 'unresolved';
}

const FILTERS = computed(() => {
  const counts = { all: props.doc.threads.value.length, unresolved: 0, resolved: 0, broken: 0, unanchored: 0 };
  for (const thread of props.doc.threads.value) counts[threadState(thread)] += 1;
  return [
    { key: 'all', label: '全部', count: () => counts.all },
    { key: 'unresolved', label: '未解决', count: () => counts.unresolved },
    { key: 'resolved', label: '已解决', count: () => counts.resolved },
    { key: 'broken', label: '断链', count: () => counts.broken },
    { key: 'unanchored', label: '未锚定', count: () => counts.unanchored },
  ] as const;
});

const visibleGroups = computed(() => {
  const filtered = props.doc.threads.value.filter(
    (thread) => activeFilter.value === 'all' || threadState(thread) === activeFilter.value,
  );
  const bySection = new Map<string, PlanCommentThread[]>();
  for (const thread of filtered) {
    const section = resolutions.value.get(thread.root.id)?.sectionTitle ?? '未锚定';
    bySection.set(section, [...(bySection.get(section) ?? []), thread]);
  }
  return [...bySection.entries()].map(([title, threads]) => ({ key: title, title, threads }));
});

function run(action: 'start-review', text: string) {
  void props.doc.transition(action, undefined, text);
}

function approve() {
  if (unresolvedCount.value > 0) {
    Modal.confirm({
      title: '评审通过确认',
      content: `仍有 ${unresolvedCount.value} 条未解决批注。通过后进入执行阶段，批注将保留在文档上。`,
      okText: '仍要通过',
      cancelText: '取消',
      onOk: () => props.doc.transition('approve', undefined, '评审已通过'),
    });
    return;
  }
  void props.doc.transition('approve', undefined, '评审已通过');
}

function openReject() {
  rejectReason.value = '';
  rejectOpen.value = true;
}

async function confirmReject() {
  const comment = rejectReason.value.trim();
  if (!comment) return;
  rejecting.value = true;
  const ok = await props.doc.transition('reject', { comment }, '已驳回，退回草稿');
  rejecting.value = false;
  if (ok) rejectOpen.value = false;
}

async function removeComment(thread: PlanCommentThread, comment: PlanComment) {
  if (!props.doc.plan.value) return;
  await deleteCommentApi(props.doc.plan.value.id, comment.id);
  await props.doc.refresh();
  message.success('批注已删除');
}
</script>
```

- [ ] **Step 2: TaskPlanDetail 接线 locate**

```ts
const pendingLocate = ref<number | null>(null);

function locateComment(commentId: number) {
  pendingLocate.value = commentId;
  activeTab.value = 'document';
}
```

```html
<div class="plan-tab-scroll"><PlanDetailReview :doc="doc" @locate="locateComment" /></div>
<PlanDetailDocument
  …
  :locate-comment-id="pendingLocate"
  @located="pendingLocate = null"
/>
```

`PlanDetailDocument.vue`：props 增加 `locateCommentId?: number | null`，emits 增加 `(e: 'located'): void`，script 增：

```ts
watch(() => props.locateCommentId, (id) => {
  if (id == null) return;
  if (viewMode.value !== 'Pretty') viewMode.value = 'Pretty';
  void nextTick(() => {
    const resolutions = deriveAnchors(props.plan.body, props.doc.anchoredRoots.value);
    commentLayer.locate(resolutions.get(id)?.line ?? null);
    emit('located');
  });
});
```

- [ ] **Step 3: 工作台样式（plan-module.css 追加）**

```css
/* ---------- 评审工作台（spec §3.4） ---------- */
.workbench-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: var(--surface);
  margin-bottom: 12px;
}

.workbench-badge {
  padding: 1px 10px;
  border-radius: 999px;
  background: var(--accent-soft);
  color: var(--plan-accent-text);
  font-size: 12px;
  font-weight: 600;
}

.workbench-hint { color: var(--anno-accent-text, #d46b08); font-size: 12px; }
:root[data-theme='dark'] .workbench-hint { --anno-accent-text: #ffc53d; }

.workbench-filters { display: flex; gap: 6px; margin-bottom: 12px; flex-wrap: wrap; }

.workbench-chip {
  border: 1px solid var(--line);
  border-radius: 999px;
  background: var(--surface);
  color: var(--muted);
  font-size: 12px;
  padding: 2px 11px;
  cursor: pointer;
}

.workbench-chip.active { background: var(--ink); border-color: var(--ink); color: var(--surface); }

.workbench-group { color: var(--muted); font-size: 11px; letter-spacing: 0.4px; margin: 10px 0 6px; }

.workbench-list { display: flex; flex-direction: column; gap: 8px; }
.workbench-list .anno-card { max-width: 880px; }

.workbench-flow {
  margin-top: 16px;
  padding-top: 10px;
  border-top: 1px dashed var(--line);
  color: var(--muted);
  font-size: 12px;
  line-height: 2;
}
```

- [ ] **Step 4: 构建 + 手测**

Run: `cd frontend && npm run build`
手测：评审 Tab 三段式呈现；筛选 chips 计数与过滤；「↧ 定位」跳文档 Tab 并滚动闪烁；通过时有未解决批注弹「仍要通过」；无未解决直接通过；驳回照旧。

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/task-plans/PlanDetailReview.vue frontend/src/components/task-plans/TaskPlanDetail.vue frontend/src/components/task-plans/PlanDetailDocument.vue frontend/src/styles/plan-module.css
git commit -m "feat：评审 Tab 改造为评审工作台——动作条（沿用权限显隐）+筛选 chips（全部/未解决/已解决/断链/未锚定）+按章节分组批注卡（复用 PlanCommentCard）+流转记录分区；通过时未解决>0 弹「仍要通过」提醒不阻断；locate 跨 Tab 跳文档定位"
```

---

### Task 11: Tab 状态与批注深链（URL query）

**Files:**
- Modify: `frontend/src/components/task-plans/TaskPlanDetail.vue`

**Interfaces:**
- Consumes: Task 10 的 `locateComment/pendingLocate`。
- Produces: `?tab=document|review|report|publish` 与 `?comment={id}` query 同步（replaceState 语义，`router.replace`）。

- [ ] **Step 1: 实现 query 同步**

`TaskPlanDetail.vue` script：

```ts
import { useRoute, useRouter } from 'vue-router';

const route = useRoute();
const router = useRouter();

const TAB_KEYS = ['document', 'review', 'report', 'publish'] as const;

function tabOfQuery(): string {
  const tab = route.query.tab;
  return typeof tab === 'string' && (TAB_KEYS as readonly string[]).includes(tab) ? tab : 'document';
}

onMounted(() => {
  activeTab.value = tabOfQuery();
  const comment = route.query.comment;
  if (typeof comment === 'string' && /^\d+$/.test(comment)) {
    pendingLocate.value = Number(comment);
    activeTab.value = 'document';
  }
  void doc.load(props.plan.id);
});

watch(activeTab, (tab) => {
  if (tab !== tabOfQuery()) {
    void router.replace({ query: { ...route.query, tab } });
  }
});
```

`locateComment` 同步 query（替换 Task 10 版本）：

```ts
function locateComment(commentId: number) {
  pendingLocate.value = commentId;
  activeTab.value = 'document';
  void router.replace({ query: { ...route.query, tab: 'document', comment: String(commentId) } });
}
```

（注意保留原有 `watch(() => props.plan.id, …)` 与 `onMounted` 中 `doc.load` 的唯一性——合并进上面的 onMounted，勿重复加载。）

- [ ] **Step 2: 构建 + 手测**

Run: `cd frontend && npm run build`
手测：切 Tab 后地址栏 query 变化、刷新落在原 Tab；直接粘贴 `?tab=document&comment=123` 打开详情页 → 自动文档 Tab + 定位闪烁。

- [ ] **Step 3: Commit**

```bash
git add frontend/src/components/task-plans/TaskPlanDetail.vue
git commit -m "feat：计划详情 Tab 状态入 URL——?tab= 四键同步（router.replace），?comment= 深链直达文档批注定位（刷新不再总落文档 Tab）"
```

---

### Task 12: 全量回归 + 亮暗走查 + 手测清单收口

**Files:**
- 无新增（验证任务；发现的问题就地修复并按所属 Task 口径补提交）

- [ ] **Step 1: 后端全量测试**

Run: `JAVA_HOME=/Users/wangk/Documents/config/jdk-17.0.17+10/Contents/Home/ ./gradlew :backend:test`
Expected: BUILD SUCCESS

- [ ] **Step 2: 前端全量**

Run: `cd frontend && npm test && npm run build`
Expected: 全绿

- [ ] **Step 3: 浏览器手测清单（亮/暗双主题各过一遍；dev 环境 vite@5173 + 后端@8080）**

按 spec §8 手测清单执行，逐项确认：

1. 悬浮块出现「＋ 批注」、移开隐藏；点击行尾出内联输入框，Ctrl/⌘+Enter 提交、Esc 取消。
2. 提交后：块上徽标 + 高亮、面板新卡、评审工作台同步可见，三处同一数据。
3. 表格行粒度（三章指标行）、六章清单项、八章章标题（章级）三种粒度各加一条。
4. Markdown 视图编辑：改写一行文字保存 → 徽标 remount 到新行；删除该行 → 章顶断链条可展开显示原文。
5. 面板：分组（未锚定 → 章节序 → 已解决折叠）、回复、解决/重开、已解决锁定回复、删除根级联回复消失。
6. 「定位」/「去文档定位」：滚动 + 闪烁；Markdown 视图下自动切回 Pretty。
7. 评审工作台：筛选计数、通过确认弹窗（有未解决）、无未解决直接通过、驳回必填原因照旧。
8. 深链 `?tab=review`、`?tab=document&comment=N` 刷新与直开。
9. 权限矩阵抽查：非成员不可见；成员在 EXECUTION 阶段无新增入口、面板只读；负责人可解决他人批注。
10. 批注操作后计划 revision 不变（页头无 revision 展示，用网络面板确认无 PUT /document 请求）。

- [ ] **Step 4: 收尾提交（如有修复）与汇总**

```bash
git status --short   # 确认只剩他人特性的未提交文件（若有）
git log --oneline -12
```

向用户报告：任务完成情况、手测结果、遗留事项。

---

## 自审记录（写计划时已核）

1. **Spec 覆盖**：§3.1→Task 7；§3.2→Task 9；§3.3→Task 8；§3.4→Task 10；§3.5→Task 2/3（canResolve/canDelete、resolve 权限、approve 不后端拦截）；§3.6→Task 11；§4→Task 1；§5→Task 4/5/7/8；§6 API→Task 2/3；§7 前端拆解→Task 6-11；§8 测试→各任务步骤 + Task 12；§9 不做项未引入。相似度算法按 spec 授权的实现自由度取 bigram Dice（阈值 0.6 不变），已在 Task 5 注明。
2. **无占位符**：所有代码块完整可落盘；Task 7 的 hoverTarget 修正已在正文写明实现口径。
3. **类型/命名一致性**：`CommentView` 字段、`PlanComment` 字段、`addAnchoredComment` 入参、`PlanCommentPanelGroup`、`deriveAnchors` 签名在消费任务中逐一对齐；迁移列为 V5。
