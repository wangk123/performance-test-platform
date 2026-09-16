# 环境检查信息架构重构 · 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 凭据池 UI 迁至项目级新 tab「环境检查」，计划侧新增「检查总览」（检查目标只读预览 + 检查项勾选同屏），新增 targets 预览 API。

**Architecture:** 后端仅在 `EnvironmentCheckRunner` 加只读 `previewTargets`（复用 `EnvTargetParser` + 凭据 `resolveRecord` + `TargetHost.matches`），经 `EnvCheckRunService` 透出到新 GET 端点；前端凭据表单抽 `CredentialFormModal` 供项目池卡与计划覆盖弹窗共用，`EnvCheckSettingsCard` 重命名扩展为 `EnvCheckOverviewCard`（上半目标表/下半勾选区），新增 `EnvCheckCredBar` 状态条与 `ProjectEnvCheckView` 项目视图。

**Tech Stack:** Spring Boot 3（Java 17）/ Vue 3 + ant-design-vue / vitest / JUnit5 @SpringBootTest。**项目不新增任何依赖**（前端无 @vue/test-utils，组件测试只测抽出的纯逻辑 utils）。

**Spec:** `docs/superpowers/specs/2026-09-16-env-check-restructure-design.md`（决策 R1–R5；本计划不从 spec 妥协任何项）

## Global Constraints

- 后端 Gradle 命令必须带 `JAVA_HOME=/Users/wangk/Documents/config/jdk-17.0.17+10/Contents/Home/`，工作目录 `backend/`。
- 前端命令工作目录 `frontend/`：`npm test`（= vitest run src）、`npx vue-tsc --noEmit`。
- 不改 DB schema、不做破坏性 API 变更；P1-1 的 E1–E9 执行链路行为零变化。
- 前端组件 ≤ 500 行；样式沿用 env-check 既有 token（`var(--line)`、`var(--muted)`、`.ec-card` 外壳等，全局样式收口在 EnvCheckTab.vue 的非 scoped style 块）。
- commit 格式 `<type>：<描述>`（如 `feat：…`、`test：…`），只提交任务内文件。
- 凭据密文任何 API 响应不得返回（沿用 EnvCheckCredentialService 约束）。

---

### Task 1: 后端 targets 预览（服务方法 + REST 端点）

**Files:**
- Modify: `backend/src/main/java/com/yr/perftest/platform/envcheck/EnvCheckCredentialService.java`（resolve 拆出 `resolveRecord`）
- Modify: `backend/src/main/java/com/yr/perftest/platform/envcheck/EnvironmentCheckRunner.java`（新增 `previewTargets` + record）
- Modify: `backend/src/main/java/com/yr/perftest/platform/envcheck/EnvCheckRunService.java`（透传 `preview`）
- Modify: `backend/src/main/java/com/yr/perftest/platform/api/EnvCheckController.java`（GET 端点）
- Test: `backend/src/test/java/com/yr/perftest/platform/envcheck/EnvironmentCheckRunnerTest.java`

**Interfaces:**
- Consumes: 既有 `EnvTargetParser.parse(String)` → `List<TargetHost>`；`EnvCheckCredentialService.resolveRecord(long, Long, String)`（本任务产出）；`PrecheckSettings.migrate` / `settingsOf`（Runner 私有）；`TargetHost.matches(TargetHost, Set<String>)`；`registry.resolve(List<String>)`。
- Produces（后续任务依赖）:
  - `EnvironmentCheckRunner.previewTargets(long planId)` → `TargetsPreview`
  - `public record TargetsPreview(List<TargetView> targets, int total, int ready, List<String> missing)`
  - `public record TargetView(String host, String module, String credential, int applicableRemoteItems)`，`credential ∈ "POOL" | "PLAN_OVERRIDE" | "MISSING"`
  - REST：`GET /api/task-plans/{planId}/env-check/targets` → 上述 TargetsPreview 的 JSON（字段名一一对应）

- [ ] **Step 1: 写失败测试**

在 `EnvironmentCheckRunnerTest` 追加（该类已 `@SpringBootTest @Transactional`，已注入 runner 等组件；`seedPlanWithEnvTable(host, module)` 辅助已存在，返回 planId 并置 `seededProjectId`）。先在类顶部补注入凭据服务（若已有则跳过）：

```java
@Autowired
EnvCheckCredentialService credentials;
```

再追加测试方法（import `com.yr.perftest.platform.envcheck.EnvironmentCheckRunner.TargetsPreview`）：

```java
// previewTargets：三态凭据合成 + applicable 按 appliesTo 匹配（mysql 项只匹配模块列含 mysql 的机器）
@Test
void previewTargetsComposesCredentialStateAndApplicability() {
    long planId = seedPlanWithEnvTable("10.0.0.1", "mysql 主库");
    long projectId = seededProjectId;
    // 项目池凭据：10.0.0.1；计划覆盖：不存在；另一台 10.0.0.2 无凭据
    credentials.save(projectId, null, new EnvCheckCredentialService.CredentialInput(
            "10.0.0.1", 22, "perf", "pw", null, null, null), "owner");

    // 计划文档再补一台无凭据机器（seedPlanWithEnvTable 只写了一台，直接再起一个计划验证缺失态）
    long planNoCred = seedPlanWithEnvTable("10.0.0.2", "redis");

    TargetsPreview preview = runner.previewTargets(planId);
    assertThat(preview.total()).isEqualTo(1);
    assertThat(preview.ready()).isEqualTo(1);
    assertThat(preview.missing()).isEmpty();
    assertThat(preview.targets()).hasSize(1);
    assertThat(preview.targets().get(0).host()).isEqualTo("10.0.0.1");
    assertThat(preview.targets().get(0).credential()).isEqualTo("POOL");
    // 默认设置（未保存过 precheckJson）勾选集为 DEFAULT_ITEMS（全 LOCAL），远程项 0
    assertThat(preview.targets().get(0).applicableRemoteItems()).isZero();

    TargetsPreview noCred = runner.previewTargets(planNoCred);
    assertThat(noCred.total()).isEqualTo(1);
    assertThat(noCred.ready()).isZero();
    assertThat(noCred.missing()).containsExactly("10.0.0.2");
    assertThat(noCred.targets().get(0).credential()).isEqualTo("MISSING");
}

// 计划覆盖优先于项目池：credential = PLAN_OVERRIDE
@Test
void previewTargetsMarksPlanOverride() {
    long planId = seedPlanWithEnvTable("10.0.0.3", "app");
    long projectId = seededProjectId;
    credentials.save(projectId, null, new EnvCheckCredentialService.CredentialInput(
            "10.0.0.3", 22, "perf", "pw", null, null, null), "owner");
    credentials.save(projectId, planId, new EnvCheckCredentialService.CredentialInput(
            "10.0.0.3", 36000, "deploy", null, "FAKE-PEM", null, planId), "owner");

    TargetsPreview preview = runner.previewTargets(planId);
    assertThat(preview.targets().get(0).credential()).isEqualTo("PLAN_OVERRIDE");
}

// 文档无「环境部署信息」表：空清单不报错
@Test
void previewTargetsEmptyWhenDocHasNoEnvTable() {
    PersistentProjectRecord project = projectRepository.save(
            new PersistentProjectRecord("P2", "项目二", "", "owner"));
    memberRepository.save(new PersistentProjectMemberRecord(project.getId(), "owner", ProjectRole.OWNER));
    PersistentTaskPlanRecord plan = planRepository.save(
            new PersistentTaskPlanRecord(project.getId(), "计划二", null, "owner"));
    plan.updateBody("## 五、测试资源\n\n（未写部署表）\n");
    long planId = planRepository.save(plan).getId();

    TargetsPreview preview = runner.previewTargets(planId);
    assertThat(preview.total()).isZero();
    assertThat(preview.targets()).isEmpty();
}

// applicable 计数：勾选含远程项时，带 mysql 标签的项只对模块列含 mysql 的机器计数
@Test
void previewTargetsCountsApplicableRemoteItems() {
    long planId = seedPlanWithEnvTable("10.0.0.4", "mysql 主库");
    // 打开环境检查并勾选两项远程：mysql 项 + ulimit（通用项）
    planRepository.findById(planId).ifPresent(p -> {
        p.updatePrecheck("{\"enabled\":true,\"items\":[\"middleware.mysql-max-connections\",\"os.ulimit\"]}");
        planRepository.save(p);
    });

    TargetsPreview preview = runner.previewTargets(planId);
    // mysql 项 appliesTo={mysql} 匹配「mysql 主库」，ulimit 通用 → 2
    assertThat(preview.targets().get(0).applicableRemoteItems()).isEqualTo(2);
}
```

注：`middleware.mysql-max-connections`、`os.ulimit` 的真实 key 以 `EnvCheckRegistry` 启动收集结果为准（`MiddlewareMysqlItem.key()` / `OsUlimitItem.key()`）。Step 1 执行时先打开这两个类抄准 key 与 `updatePrecheck` 的真实方法名（若计划 record 无该方法，用 plan 实体对应 setter / `setPrecheckJson`）。

- [ ] **Step 2: 跑测试确认失败**

```bash
cd backend && JAVA_HOME=/Users/wangk/Documents/config/jdk-17.0.17+10/Contents/Home/ gradle test --tests "com.yr.perftest.platform.envcheck.EnvironmentCheckRunnerTest"
```

预期：编译失败（`previewTargets` 不存在）。

- [ ] **Step 3: 实现**

`EnvCheckCredentialService`：`resolve` 拆出记录级方法（原 `resolve` 行为不变）：

```java
/** 计划覆盖 &gt; 项目池：返回命中的凭据记录（planId 字段用于区分覆盖来源）；不命中返回 empty。 */
public Optional<PersistentEnvCheckCredentialRecord> resolveRecord(long projectId, Long planId, String host) {
    return planId == null
            ? repository.findByProjectIdAndPlanIdIsNullAndHost(projectId, host)
            : repository.findByProjectIdAndPlanIdAndHost(projectId, planId, host)
                    .or(() -> repository.findByProjectIdAndPlanIdIsNullAndHost(projectId, host));
}

public Optional<ResolvedCredential> resolve(long projectId, Long planId, String host) {
    return resolveRecord(projectId, planId, host).map(this::toResolved);
}
```

`EnvironmentCheckRunner`（字段/依赖全部已存在）：

```java
/** 检查目标预览（spec 2026-09-16 §4）：文档解析 + 凭据三态 + 勾选远程项适用计数；只读，不产生 run。 */
public record TargetView(String host, String module, String credential, int applicableRemoteItems) {
}

public record TargetsPreview(List<TargetView> targets, int total, int ready, List<String> missing) {
}

public TargetsPreview previewTargets(long planId) {
    PersistentTaskPlanRecord plan = planRepository.findById(planId)
            .orElseThrow(() -> new EnvCheckValidationException("ENV_CHECK_INVALID：task plan does not exist"));
    List<TargetHost> hosts = targetParser.parse(plan.getBody() == null ? "" : plan.getBody());
    List<EnvCheckItem> checked = registry.resolve(PrecheckSettings.migrate(settingsOf(plan)).items());
    List<RemoteCheckItem> remote = checked.stream()
            .filter(RemoteCheckItem.class::isInstance).map(RemoteCheckItem.class::cast).toList();
    List<TargetView> targets = new ArrayList<>();
    List<String> missing = new ArrayList<>();
    for (TargetHost host : hosts) {
        String state = credentials.resolveRecord(plan.getProjectId(), plan.getId(), host.host())
                .map(record -> record.getPlanId() != null ? "PLAN_OVERRIDE" : "POOL")
                .orElse("MISSING");
        if ("MISSING".equals(state)) {
            missing.add(host.host());
        }
        int applicable = (int) remote.stream()
                .filter(item -> TargetHost.matches(host, item.appliesTo())).count();
        targets.add(new TargetView(host.host(), host.module(), state, applicable));
    }
    return new TargetsPreview(targets, targets.size(), targets.size() - missing.size(), missing);
}
```

注：`RemoteCheckItem` 若未继承 `EnvCheckItem.appliesTo()` 的可访问路径，改用 `checked`（`EnvCheckItem` 已有 `appliesTo()`）过滤 `item instanceof RemoteCheckItem`，二者等价，取编译通过者。

`EnvCheckRunService` 加透传：

```java
/** 检查目标预览（spec 2026-09-16 §4）：透传 Runner 只读方法。 */
public EnvironmentCheckRunner.TargetsPreview preview(long planId) {
    return runner.previewTargets(planId);
}
```

`EnvCheckController`（runService 已注入）：

```java
/** 检查目标预览（spec 2026-09-16 §4）：文档解析目标 + 凭据三态 + 适用项计数；只读。 */
@GetMapping("/task-plans/{planId}/env-check/targets")
public EnvironmentCheckRunner.TargetsPreview targets(@PathVariable long planId) {
    workflowService.requireActor(planId, requireHuman(), "PRECHECK_RUN");
    return runService.preview(planId);
}
```

- [ ] **Step 4: 跑测试确认通过**

同 Step 2 命令。预期：4 个新测试全 PASS，存量不回归。

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/yr/perftest/platform/envcheck/EnvCheckCredentialService.java \
  backend/src/main/java/com/yr/perftest/platform/envcheck/EnvironmentCheckRunner.java \
  backend/src/main/java/com/yr/perftest/platform/envcheck/EnvCheckRunService.java \
  backend/src/main/java/com/yr/perftest/platform/api/EnvCheckController.java \
  backend/src/test/java/com/yr/perftest/platform/envcheck/EnvironmentCheckRunnerTest.java
git commit -m "feat：环境检查目标预览——文档解析+凭据三态+适用项计数的只读接口 GET /task-plans/{planId}/env-check/targets"
```

---

### Task 2: 抽 CredentialFormModal + 凭据池卡项目化

**Files:**
- Create: `frontend/src/components/env-check/CredentialFormModal.vue`
- Modify: `frontend/src/components/env-check/EnvCheckCredentialCard.vue`
- Test: `npx vue-tsc --noEmit`（编译级验证；表单校验为纯 UI，无纯逻辑可抽测）

**Interfaces:**
- Consumes: `saveCredentialApi(projectId, EnvCheckCredentialInput)`（既有）、`EnvCheckCredential` 类型（既有）。
- Produces（Task 5 依赖）:
  - `<CredentialFormModal v-model:open="..." :project-id="number" :plan-id="number | undefined" :editing="EnvCheckCredential | null" @saved="..." />`
  - `planId` 非 undefined 时表单尾部渲染「仅当前计划生效」勾选，保存时携带 planId（覆盖语义）；undefined 时无该勾选。

- [ ] **Step 1: 新建 CredentialFormModal.vue**

把 `EnvCheckCredentialCard.vue` 里 `a-drawer`（47–83 行）与 `saveCredential`/表单状态整体搬入，组件化为受控弹窗：

```vue
<template>
  <a-drawer :open="open" :title="editing ? '编辑凭据' : '新增凭据'" :width="420" destroy-on-close
            @update:open="(value: boolean) => emit('update:open', value)">
    <a-form layout="vertical">
      <a-form-item label="机器地址（IP）" required>
        <a-input v-model:value="form.host" placeholder="如 10.1.1.10" />
      </a-form-item>
      <a-form-item label="SSH 端口">
        <a-input-number v-model:value="form.sshPort" :min="1" :max="65535" style="width: 100%" />
      </a-form-item>
      <a-form-item label="账号" required>
        <a-input v-model:value="form.username" placeholder="如 perftest" />
      </a-form-item>
      <a-form-item label="认证方式">
        <a-radio-group v-model:value="form.authType">
          <a-radio value="PASSWORD">密码</a-radio>
          <a-radio value="KEY">密钥</a-radio>
        </a-radio-group>
      </a-form-item>
      <a-form-item v-if="form.authType === 'PASSWORD'" label="密码" :extra="editing ? '留空则不修改原密码' : ''">
        <a-input-password v-model:value="form.password" placeholder="只写不回显" autocomplete="new-password" />
      </a-form-item>
      <a-form-item v-else label="私钥内容" :extra="editing ? '留空则不修改原密钥' : ''">
        <a-textarea v-model:value="form.keyMaterial" :rows="5" placeholder="PEM 私钥，只写不回显" />
      </a-form-item>
      <a-form-item label="备注">
        <a-input v-model:value="form.remark" placeholder="如 订单服务 / 非 22 端口" />
      </a-form-item>
      <a-form-item v-if="planId != null">
        <a-checkbox v-model:checked="form.planScoped">仅当前计划生效</a-checkbox>
      </a-form-item>
    </a-form>
    <template #footer>
      <a-space>
        <a-button @click="emit('update:open', false)">取消</a-button>
        <a-button type="primary" :loading="saving" @click="save">保存</a-button>
      </a-space>
    </template>
  </a-drawer>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue';
import { message } from 'ant-design-vue';
import type { EnvCheckCredential } from '../../types';
import { saveCredentialApi } from '../../api/env-check';

const props = defineProps<{
  open: boolean;
  projectId: number;
  /** 计划级覆盖入口传 planId；项目池入口不传（不出现覆盖勾选）。 */
  planId?: number;
  editing: EnvCheckCredential | null;
}>();
const emit = defineEmits<{ (e: 'update:open', value: boolean): void; (e: 'saved'): void }>();

const saving = ref(false);
const form = reactive({
  host: '',
  sshPort: 22,
  username: '',
  authType: 'PASSWORD' as EnvCheckCredential['authType'],
  password: '',
  keyMaterial: '',
  remark: '',
  planScoped: false,
});

/** 每次打开按 editing 重放表单（destroy-on-close 后状态复位，这里兜底首帧）。 */
watch(() => props.open, (open) => {
  if (!open) return;
  const record = props.editing;
  form.host = record?.host ?? '';
  form.sshPort = record?.sshPort ?? 22;
  form.username = record?.username ?? '';
  form.authType = record?.authType ?? 'PASSWORD';
  form.password = '';
  form.keyMaterial = '';
  form.remark = record?.remark ?? '';
  form.planScoped = record?.planId != null;
});

async function save() {
  if (!form.host.trim() || !form.username.trim()) {
    message.warning('请填写机器地址与账号');
    return;
  }
  if (form.authType === 'PASSWORD' && !props.editing && !form.password) {
    message.warning('请填写密码');
    return;
  }
  if (form.authType === 'KEY' && !props.editing && !form.keyMaterial.trim()) {
    message.warning('请填写私钥内容');
    return;
  }
  saving.value = true;
  try {
    await saveCredentialApi(props.projectId, {
      host: form.host.trim(),
      sshPort: form.sshPort,
      username: form.username.trim(),
      password: form.authType === 'PASSWORD' && form.password ? form.password : undefined,
      keyMaterial: form.authType === 'KEY' && form.keyMaterial.trim() ? form.keyMaterial.trim() : undefined,
      remark: form.remark.trim() || undefined,
      planId: props.planId != null && form.planScoped ? props.planId : undefined,
    });
    emit('update:open', false);
    message.success('凭据已保存');
    emit('saved');
  } catch (error) {
    message.error(error instanceof Error ? error.message : '凭据保存失败');
  } finally {
    saving.value = false;
  }
}
</script>
```

- [ ] **Step 2: 改造 EnvCheckCredentialCard 为项目池卡**

- 标题「项目设置 · 环境检查凭据」→「**SSH 凭据池**」，副题改「密码加密存储、只写不回显；按机器地址匹配计划文档解析出的检查目标，项目内所有计划共用」。
- props 改为 `defineProps<{ projectId: number }>()`（删除 `planId?: number`）。
- 删除内嵌 `a-drawer` 与 `saveCredential`/`form`/`editing` 相关代码，改用：

```vue
<CredentialFormModal v-model:open="editorOpen" :project-id="projectId" :editing="editing" @saved="load" />
```

```ts
import CredentialFormModal from './CredentialFormModal.vue';
const editorOpen = ref(false);
const editing = ref<EnvCheckCredential | null>(null);
function openEditor(record: EnvCheckCredential | null) {
  editing.value = record;
  editorOpen.value = true;
}
```

- 「范围」列保留（列数据显示项目凭据/计划覆盖记录，只读）。
- 本卡此时尚无入口渲染（Task 3 接入），`vue-tsc` 验证编译即可。

- [ ] **Step 3: 编译验证**

```bash
cd frontend && npx vue-tsc --noEmit
```

预期：无错误。

- [ ] **Step 4: Commit**

```bash
git add frontend/src/components/env-check/CredentialFormModal.vue frontend/src/components/env-check/EnvCheckCredentialCard.vue
git commit -m "refactor：凭据表单抽 CredentialFormModal 供项目池与计划覆盖共用；凭据卡去 planId 依赖改为项目池卡"
```

---

### Task 3: 项目「环境检查」tab

**Files:**
- Modify: `frontend/src/types/index.ts:11`（ProjectTab 联合类型）
- Modify: `frontend/src/constants/index.ts:12-21`（projectTabOptions）
- Modify: `frontend/src/router/index.ts`（monitoring 路由后加一条）
- Create: `frontend/src/components/views/ProjectEnvCheckView.vue`
- Modify: `frontend/src/components/views/ProjectDetail.vue`（分发分支）

**Interfaces:**
- Consumes: Task 2 的 `EnvCheckCredentialCard`（新 props：仅 `projectId`）。
- Produces: 路由 `/projects/:projectId/env-check` 可达；`ProjectTab` 含 `'env-check'`（Task 5 的跳转按钮依赖此路由）。

- [ ] **Step 1: 类型与常量**

`types/index.ts` 第 11 行改为：

```ts
export type ProjectTab = 'overview' | 'scripts' | 'task-plans' | 'monitoring' | 'env-check' | 'reports' | 'data' | 'functions' | 'members';
```

`constants/index.ts` projectTabOptions 在「监控配置」后插入：

```ts
  { label: '环境检查', value: 'env-check' },
```

（`tabLabel`/`moduleIndex`/侧边导航均由该数组驱动，自动生效。）

- [ ] **Step 2: 路由**

`router/index.ts` 在 `project-monitoring` 行后加：

```ts
        { path: 'projects/:projectId/env-check', name: 'project-env-check', component: ProjectDetail },
```

- [ ] **Step 3: 新建 ProjectEnvCheckView.vue**

```vue
<template>
  <section class="pev-view">
    <header class="pev-head">
      <div>
        <h2>环境检查</h2>
        <p>项目级 SSH 凭据池，按机器地址匹配各计划文档「环境部署信息」解析出的检查目标；计划侧可配置仅本计划生效的覆盖凭据。</p>
      </div>
    </header>
    <EnvCheckCredentialCard :project-id="projectId" />
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useRoute } from 'vue-router';
import EnvCheckCredentialCard from '../env-check/EnvCheckCredentialCard.vue';

const route = useRoute();
const projectId = computed(() => Number(route.params.projectId) || 0);
</script>

<style scoped>
.pev-view {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.pev-head h2 {
  margin: 0;
  font-size: 17px;
  font-weight: 700;
}
.pev-head p {
  margin: 4px 0 0;
  color: var(--muted);
  font-size: 12.5px;
}
</style>
```

- [ ] **Step 4: ProjectDetail 分发**

`ProjectDetail.vue` 在 `ProjectMonitoringView` 分支后加（import 同步）：

```vue
  <ProjectEnvCheckView v-else-if="activeProjectTab === 'env-check'" />
```

```ts
import ProjectEnvCheckView from './ProjectEnvCheckView.vue';
```

- [ ] **Step 5: 编译 + 手动冒烟**

```bash
cd frontend && npx vue-tsc --noEmit && npm run dev
```

浏览器开 `http://localhost:5173/projects/1/env-check`（登录态、项目 1 存在）：侧边栏出现「环境检查」，页面渲染凭据池卡，新增/编辑/删除/测试连接可用，表单无「仅当前计划生效」勾选。

- [ ] **Step 6: Commit**

```bash
git add frontend/src/types/index.ts frontend/src/constants/index.ts frontend/src/router/index.ts \
  frontend/src/components/views/ProjectEnvCheckView.vue frontend/src/components/views/ProjectDetail.vue
git commit -m "feat：项目详情新增「环境检查」tab——SSH 凭据池迁至项目级（/projects/:id/env-check）"
```

---

### Task 4: EnvCheckOverviewCard（检查总览）

**Files:**
- Rename+Modify: `frontend/src/components/env-check/EnvCheckSettingsCard.vue` → `EnvCheckOverviewCard.vue`（git mv 保历史）
- Modify: `frontend/src/types/index.ts`（EnvCheck 目标预览类型）
- Modify: `frontend/src/api/env-check.ts`（fetchEnvCheckTargetsApi）
- Test: `frontend/src/components/env-check/EnvCheckSettingsCard.spec.ts`（随重命名更名，逻辑不动；新增 utils 纯逻辑函数测试进同文件）

**Interfaces:**
- Consumes: Task 1 的 REST 响应类型；既有 `parsePrecheckSettings`/`updatePrecheckSettingsApi`/`groupEnvCheckItems`。
- Produces（Task 5 依赖）:
  - `<EnvCheckOverviewCard :plan-id :project-id :precheck-json :items :loading :targets :targets-loading @saved="..." />`
  - `targets: EnvCheckTargetsPreview`（由父级 EnvCheckTab 拉取传入）
  - 前端类型与 API：

```ts
export type EnvCheckCredentialState = 'POOL' | 'PLAN_OVERRIDE' | 'MISSING';
export interface EnvCheckTargetView {
  host: string;
  module: string;
  credential: EnvCheckCredentialState;
  applicableRemoteItems: number;
}
export interface EnvCheckTargetsPreview {
  targets: EnvCheckTargetView[];
  total: number;
  ready: number;
  missing: string[];
}
```

```ts
export function fetchEnvCheckTargetsApi(planId: number) {
  return request<EnvCheckTargetsPreview>(`/api/task-plans/${planId}/env-check/targets`, { method: 'GET' });
}
```

  - utils 纯逻辑（放 `envCheckSettings.ts`，供渲染与测试共用）：

```ts
export function credentialChip(state: EnvCheckCredentialState): { text: string; level: 'ok' | 'warn' | 'danger' } {
  if (state === 'POOL') return { text: '✓ 已覆盖', level: 'ok' };
  if (state === 'PLAN_OVERRIDE') return { text: '🔒 计划覆盖', level: 'warn' };
  return { text: '✗ 缺失', level: 'danger' };
}
```

- [ ] **Step 1: 写 utils 失败测试**

`EnvCheckSettingsCard.spec.ts` 更名为 `EnvCheckOverviewCard.spec.ts`（同目录），追加：

```ts
import { credentialChip } from './envCheckSettings';

describe('credentialChip（凭据三态展示）', () => {
  it('POOL → 已覆盖/ok，PLAN_OVERRIDE → 计划覆盖/warn，MISSING → 缺失/danger', () => {
    expect(credentialChip('POOL')).toEqual({ text: '✓ 已覆盖', level: 'ok' });
    expect(credentialChip('PLAN_OVERRIDE')).toEqual({ text: '🔒 计划覆盖', level: 'warn' });
    expect(credentialChip('MISSING')).toEqual({ text: '✗ 缺失', level: 'danger' });
  });
});
```

- [ ] **Step 2: 跑测试确认失败**

```bash
cd frontend && npm test -- EnvCheckOverviewCard
```

预期：FAIL（credentialChip 未导出）。

- [ ] **Step 3: 实现类型、API 与 utils**

按上方 Interfaces 代码块落地三处；`envCheckSettings.ts` 补 import `EnvCheckCredentialState` 类型。

- [ ] **Step 4: 跑 utils 测试通过**

同 Step 2。预期 PASS（含既有分组/解析用例）。

- [ ] **Step 5: 重命名并扩展 OverviewCard**

```bash
git mv frontend/src/components/env-check/EnvCheckSettingsCard.vue frontend/src/components/env-check/EnvCheckOverviewCard.vue
git mv frontend/src/components/env-check/EnvCheckSettingsCard.spec.ts frontend/src/components/env-check/EnvCheckOverviewCard.spec.ts
```

模板改为（脚本逻辑 `applyPrecheck`/`toggleItem`/`save` 原样保留，props 增 `projectId`/`targets`/`targetsLoading`）：

```vue
<template>
  <section class="ec-card">
    <header class="ec-card-head">
      <div>
        <h3>检查总览</h3>
        <p>目标清单来自文档「环境部署信息」（只读）；勾选检查项并核对凭据后执行</p>
      </div>
      <div class="eco-head-actions">
        <a-switch v-model:checked="enabled" :loading="saving" aria-label="启用环境检查" />
        <span class="eco-switch-label">启用环境检查</span>
        <a-button type="primary" size="small" :loading="saving" @click="save">保存设置</a-button>
      </div>
    </header>

    <div class="eco-targets">
      <div class="eco-src-hint">
        检查目标实时解析自计划文档「五、测试资源 → 环境部署信息」（{{ targets.total }} 台），修改部署表后刷新生效，平台侧不单独维护
      </div>
      <a-table
        v-if="targets.targets.length"
        :columns="targetColumns"
        :data-source="targets.targets"
        :pagination="false"
        :loading="targetsLoading"
        row-key="host"
        size="small"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'host'">
            <span class="ec-host">{{ record.host }}</span>
          </template>
          <template v-else-if="column.key === 'credential'">
            <span class="eco-cred-chip" :class="credentialChip(record.credential).level">
              {{ credentialChip(record.credential).text }}
            </span>
          </template>
          <template v-else-if="column.key === 'applicable'">
            {{ record.applicableRemoteItems }} / {{ remoteItemCount }}
          </template>
        </template>
      </a-table>
      <a-empty
        v-else
        :image-style="{ height: '48px' }"
        description="文档「五、测试资源 → 环境部署信息」未识别到目标机，请先在文档中补充部署表"
      />
    </div>

    <a-spin :spinning="loading">
      <div v-for="group in groups" :key="group.category" class="ec-item-group">
        <!-- 原 SettingsCard 的 group-head / item-row 结构原样保留 -->
      </div>
    </a-spin>
  </section>
</template>
```

脚本补充：

```ts
const props = defineProps<{
  planId: number;
  projectId: number;
  precheckJson: string | null;
  items: EnvCheckItemMeta[];
  loading: boolean;
  targets: EnvCheckTargetsPreview;
  targetsLoading: boolean;
}>();

const remoteItemCount = computed(() => props.items.filter((entry) => entry.kind === 'REMOTE').length);

const targetColumns: TableColumnsType<EnvCheckTargetView> = [
  { title: '地址', dataIndex: 'host', key: 'host', width: 140 },
  { title: '模块', dataIndex: 'module', key: 'module' },
  { title: '凭据', key: 'credential', width: 120 },
  { title: '适用远程检查项', key: 'applicable', width: 130 },
];
```

样式追加（scoped）：`.eco-head-actions`（flex gap 12）、`.eco-src-hint`（12px muted、1px dashed var(--line-strong) 边框圆角 8、margin-bottom 12）、`.eco-cred-chip.ok/.warn/.danger`（对应 `--ok`/`--warn`/`--danger` 色与 soft 底、10.5px 圆角 chip，对齐原型）。`applicable` 列分母用注册表远程项总数（勾选变化时分子由后端随 targets 重取刷新——父级在 saved 后 refetch）。

- [ ] **Step 6: 编译验证**

```bash
cd frontend && npx vue-tsc --noEmit
```

（此时 EnvCheckTab 仍 import 旧名 `EnvCheckSettingsCard`——编译会报错，属预期失败，Task 5 Step 2 修复。本步骤只确认 OverviewCard 自身无类型错：可临时 `git stash` 外验证，或直接与 Task 5 合并验证。推荐：本任务提交不要求全绿，Task 5 收口。）

- [ ] **Step 7: Commit**

```bash
git add frontend/src/components/env-check/EnvCheckOverviewCard.vue frontend/src/components/env-check/EnvCheckOverviewCard.spec.ts \
  frontend/src/components/env-check/envCheckSettings.ts frontend/src/types/index.ts frontend/src/api/env-check.ts
git commit -m "feat：计划侧新增检查总览卡——检查目标只读预览（凭据三态/适用项数）+ 检查项勾选同屏"
```

---

### Task 5: EnvCheckCredBar + EnvCheckTab 组装

**Files:**
- Create: `frontend/src/components/env-check/EnvCheckCredBar.vue`
- Modify: `frontend/src/components/env-check/EnvCheckTab.vue`

**Interfaces:**
- Consumes: Task 2 `CredentialFormModal`、Task 3 路由 `/projects/:id/env-check`、Task 4 `EnvCheckOverviewCard` 与 `EnvCheckTargetsPreview` 类型、`fetchEnvCheckTargetsApi`。
- Produces: 计划侧「环境检查」标签最终结构（总览卡 → 凭据状态条 → 结果面板）；`EnvCheckResultPanel` 的 `goto-credentials` 事件改由本组件处理为路由跳转。

- [ ] **Step 1: 新建 EnvCheckCredBar.vue**

```vue
<template>
  <section class="ec-cred-bar">
    <span class="stat">
      目标机 <b>{{ preview.total }}</b> 台 · 凭据就绪
      <b :class="preview.ready === preview.total ? 'ok' : 'danger'">{{ preview.ready }}</b> 台
    </span>
    <span v-if="preview.missing.length" class="missing">
      ✗ {{ preview.missing.join('、') }} 未配置凭据，勾选远程检查项将被拦截
    </span>
    <span class="spacer"></span>
    <CredentialFormModal v-model:open="overrideOpen" :project-id="projectId" :plan-id="planId" :editing="null" @saved="emit('saved')" />
    <a-button size="small" @click="overrideOpen = true">🔒 计划级覆盖</a-button>
    <a-button size="small" type="primary" @click="goProject">去项目配置凭据 ↗</a-button>
  </section>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import type { EnvCheckTargetsPreview } from '../../types';
import CredentialFormModal from './CredentialFormModal.vue';

const props = defineProps<{ planId: number; projectId: number; preview: EnvCheckTargetsPreview }>();
const emit = defineEmits<{ (e: 'saved'): void }>();

const overrideOpen = ref(false);
const router = useRouter();

function goProject() {
  void router.push(`/projects/${props.projectId}/env-check`);
}
</script>

<style scoped>
.ec-cred-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 11px 16px;
  border: 1px solid var(--line);
  border-radius: 10px;
  background: var(--surface);
  font-size: 13px;
}
.ec-cred-bar .stat b.ok { color: var(--ok); }
.ec-cred-bar .stat b.danger { color: var(--danger); }
.ec-cred-bar .missing { color: var(--danger); font-size: 12.5px; }
.ec-cred-bar .spacer { flex: 1; }
</style>
```

- [ ] **Step 2: 改造 EnvCheckTab.vue**

- import 改名：`EnvCheckSettingsCard` → `EnvCheckOverviewCard`，新增 `EnvCheckCredBar`、`fetchEnvCheckTargetsApi`、`useRouter`、类型 `EnvCheckTargetsPreview`。
- 删除 `EnvCheckCredentialCard` import 与渲染、`credCard` ref、`scrollToCredentials`。
- 模板：

```vue
<EnvCheckOverviewCard
  :plan-id="plan.id"
  :project-id="projectId"
  :precheck-json="precheckJson"
  :items="itemMetas"
  :loading="itemsLoading"
  :targets="targets"
  :targets-loading="targetsLoading"
  @saved="onSettingsSaved"
/>
<EnvCheckCredBar :plan-id="plan.id" :project-id="projectId" :preview="targets" @saved="loadTargets" />
<EnvCheckResultPanel
  :plan-id="plan.id"
  :items="itemMetas"
  :fix-enabled="fixEnabled"
  @goto-credentials="goProjectCredentials"
/>
```

- 脚本：

```ts
const targets = ref<EnvCheckTargetsPreview>({ targets: [], total: 0, ready: 0, missing: [] });
const targetsLoading = ref(false);
const router = useRouter();

async function loadTargets() {
  targetsLoading.value = true;
  try {
    targets.value = await fetchEnvCheckTargetsApi(props.plan.id);
  } catch (error) {
    message.error(error instanceof Error ? error.message : '检查目标加载失败');
  } finally {
    targetsLoading.value = false;
  }
}

/** 设置保存后：precheckJson 回流 + 勾选变化会使适用项数变化，目标预览同步重取。 */
async function onSettingsSaved() {
  await props.docPlan.refresh();
  await loadTargets();
}

/** 结果面板缺凭据弹窗「去凭据」：凭据池已迁项目级，路由跳转。 */
function goProjectCredentials() {
  void router.push(`/projects/${projectId.value}/env-check`);
}
```

- `onMounted(loadItems)` 改为 `onMounted(() => { void loadItems(); void loadTargets(); });`

- [ ] **Step 3: 全量验证**

```bash
cd frontend && npm test && npx vue-tsc --noEmit
```

预期：vitest 全绿（含 OverviewCard.spec）、类型无错。

- [ ] **Step 4: Commit**

```bash
git add frontend/src/components/env-check/EnvCheckCredBar.vue frontend/src/components/env-check/EnvCheckTab.vue
git commit -m "feat：计划侧凭据卡压缩为状态条（缺失点名+计划级覆盖+跳项目配置），总览/状态条/结果面板三段式组装"
```

---

### Task 6: 回归验证

**Files:** 无新改动（验证任务；发现问题回改对应任务文件后补 fix commit）

- [ ] **Step 1: 后端套件**

```bash
cd backend && JAVA_HOME=/Users/wangk/Documents/config/jdk-17.0.17+10/Contents/Home/ gradle test --tests "com.yr.perftest.platform.envcheck.*" --tests "com.yr.perftest.platform.api.*"
```

- [ ] **Step 2: 前端全量**

```bash
cd frontend && npm test && npx vue-tsc --noEmit
```

- [ ] **Step 3: 手动回归清单（dev 前后端 + 浏览器）**

1. 项目 tab：侧边栏「环境检查」在「监控配置」后；凭据池增删改、测试连接、表单无覆盖勾选。
2. 计划 4「环境检查」标签：总览卡目标表 7 台（10.190.123.x）、来源提示行、空态（切到一个无部署表的计划验证）。
3. 计划级覆盖：状态条「🔒 计划级覆盖」为某台机器配覆盖 → 总览该机变「🔒 计划覆盖」。
4. 状态条缺失点名与「去项目配置凭据 ↗」路由跳转正确。
5. 勾选远程项保存 → 适用项数刷新（mysql 项只对模块含 mysql 机器计数）。
6. E8 拦截不回归：缺凭据时执行 → 弹窗列缺失机器；配齐后执行 → 结果矩阵正常；修复/回滚入口仍在。
7. 深浅主题下新组件样式正常（对照原型 `env-check-restructure-prototype.html`）。

- [ ] **Step 4: 收尾 commit（如有修复）**

```bash
git commit -m "fix：环境检查信息架构重构回归修复"
```

---

## Self-Review 记录

- **Spec 覆盖**：R1（Task 3）、R2（Task 4 目标表只读+空态引导+来源提示）、R3（Task 4/5 三段式）、R4（Task 1 applicable 后端算）、R5（Task 2 Modal 双入口 + Task 5 覆盖弹窗）——全覆盖；§4 API（Task 1）、§6 测试（Task 1/4/6）对应。
- **占位符**：Task 4 Step 5 模板中 `<!-- 原 SettingsCard 的 group-head / item-row 结构原样保留 -->` 是"保留既有代码"的明确指令而非待补内容，执行者打开重命名后文件即见原文，非占位符。
- **类型一致性**：`TargetsPreview(targets, total, ready, missing)` 与前端 `EnvCheckTargetsPreview` 字段一一对应；`credentialChip` 定义于 Task 4 Interfaces 且 Task 4 Step 5 使用；`CredentialFormModal` 的 props 契约在 Task 2 定义、Task 5 消费一致。
