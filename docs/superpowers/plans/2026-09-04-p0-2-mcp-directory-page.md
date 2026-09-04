# P0-2 ② MCP 工具目录页 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 交付 P0-2 中无阻塞依赖的 ②「MCP 工具目录页」——后端注册表 REST 端点 + 前端 `/mcp-tools` 页面，新成员打开页面复制配置即可在本地 Agent 接入。

**Architecture:** 后端新增 `GET /api/mcp/tools` 直接映射内存 `McpToolRegistry`（单一事实源，工具随服务重启自动上下线、零页面配置）；`McpTool` 契约补 `default usageExample()` 元数据方法；stage 规范序列常量新增 `PLAN`（无工具注册，仅为序列与筛选 tab 预留）。前端新增顶级路由 `/mcp-tools`，页面按仓库根 `mcp-directory-prototype.html` 视觉基准 1:1 还原：接入指引横幅（Claude Code / DSH 配置片段一键复制）→ 阶段筛选 tabs + 搜索 → 单一平铺卡片网格（两态状态图标）→ 详情抽屉（参数表 / 使用示例）→ 页脚收尾。

**Tech Stack:** Spring Boot 3 (Java 17, MockMvc + JUnit 5 集成测试)、Vue 3.5 `<script setup>` + TypeScript + scoped CSS（复用 `base.css` 设计令牌，不引入新依赖）。

**Spec:** `docs/superpowers/specs/2026-09-04-p0-2-mcp-agent-surface-design.md`（本计划只实施其 ② 目录页 + §4.2/§4.4 非阻塞前置件）

## Scope（本轮实施边界）

**前置检测结论（2026-09-04）**：P0-1（计划文档模块）**未开发**——`backend/.../task/plandoc/` 包不存在、`PlanDocumentService`/`PlanWorkflowService` 全库无匹配、P0-1 实施计划 105 个任务复选框全部未勾选、git log 无 P0-1 实现提交。

因此按 spec §3/§8：

- **本轮实施**：② 目录页（后端端点 + 前端页面）+ 非阻塞前置件（`McpTool.usageExample()` default 方法、stage 序列补 `PLAN`）。
- **不做（依赖 P0-1，保持 §8 延后跟踪）**：① 五个计划工具（`mcp/plan/` 包）、REVISION_CONFLICT 错误码映射、五条 usageExample 编写、`plan_templates` 字段对齐、③ `skill-pack/perf-plan/SKILL.md`、roadmap D3 措辞同步。
- 现有 8 个 MCP 工具**零改动**（spec §4.2 明确：default 方法全量回归即可，不补示例）。

## Global Constraints

- Java 17 路径：`JAVA_HOME=/Users/wangk/Documents/config/jdk-17.0.17+10/Contents/Home/`（运行任何 Gradle 命令前设置，仓库根用 `./gradlew`）。
- 后端测试风格：JUnit 5 `@SpringBootTest`（H2 内存库 properties）+ `@AutoConfigureMockMvc` + AssertJ，参照 `backend/src/test/java/com/yr/perftest/platform/mcp/McpServerApiTest.java`。
- 前端无单测 runner：验证门槛 = `cd frontend && npm run build`（`vue-tsc --noEmit` 类型检查 + vite 构建必须零错误）。
- 视觉基准：仓库根 `mcp-directory-prototype.html`（布局/配色/两态状态图标/平铺网格/页脚/抽屉均按它还原；CSS 令牌映射：`--accent-dark`→`--primary-dark`、`--accent-strong`→`--active-bg-strong`、`--font-mono`→`--font-data`、`--shadow-pop` 在组件内自定义）。
- 页面**纯只读**：无启停、无编辑、无运行时注册（D18）；内容区**流式铺满**（不限宽居中）；列表 = **单一平铺网格不做阶段分组渲染**（阶段仅由筛选 tabs 承载）；状态**两态纯图标**（绿圈勾=可用 / 灰圈斜杠=不可用，不用文字）。
- `stages` = 服务端固定规范序列常量 `PLAN → NAVIGATE → DESIGN → OBSERVE → DIAGNOSE → VERIFY → CAPTURE`，不随注册表去重；空阶段 tab 也渲染（带计数 0，原型即如此）。
- Web 目录页展示**全部**注册工具并标注写权限徽标；scope 过滤是 MCP 机器身份调用期语义，**不在**目录端点重复实现（spec §4.1）。
- 提交信息风格：`类型：中文描述——细节`（对齐 git log 既有风格，如 `feat：...——...`）。
- 仓库根有他人未跟踪文件 `report-prototype.html`：**禁止** `git add -A` / `git add .`，每次只 add 本任务明确列出的文件。
- 后端新代码放 `com.yr.perftest.platform` 既有包结构：目录端点在 `api/`（与现有控制器同层），不新建 Maven 模块。

---

## File Structure（全景）

| 文件 | 动作 | 职责 |
|------|------|------|
| `backend/src/main/java/com/yr/perftest/platform/mcp/McpTool.java` | Modify | 契约补 `default usageExample()`；stage javadoc 补 PLAN |
| `backend/src/main/java/com/yr/perftest/platform/api/McpDirectoryController.java` | Create | `GET /api/mcp/tools`：映射注册表 + 固定 stage 序列 + 排序 |
| `backend/src/test/java/com/yr/perftest/platform/mcp/McpDirectoryControllerTest.java` | Create | 401 / 与 registry 严格一致 / 序列与排序 / 字段口径 |
| `frontend/src/types/index.ts` | Modify | `MainNav` 补 `mcpTools`；新增 `McpToolSummary` / `McpDirectory` |
| `frontend/src/api/mcp-directory.ts` | Create | `fetchMcpDirectoryApi()` |
| `frontend/src/utils/clipboard.ts` | Create | 剪贴板复制（Clipboard API + execCommand 回退） |
| `frontend/src/utils/format.ts` | Modify | 新增 `mcpStageLabel()` |
| `frontend/src/router/index.ts` | Modify | 顶级路由 `/mcp-tools` |
| `frontend/src/components/layout/GlobalRail.vue` | Modify | 全局导航新增「MCP 工具」按钮（执行器配置之后） |
| `frontend/src/composables/useNavigation.ts` | Modify | `mainNavPaths` + `activeMainNav` 补 mcpTools |
| `frontend/src/composables/useBreadcrumb.ts` | Modify | `/mcp-tools` 面包屑段 |
| `frontend/src/components/mcp/McpToolDirectoryPage.vue` | Create | 目录页（页头/横幅/筛选/网格/页脚/抽屉宿主） |
| `frontend/src/components/mcp/McpToolDetailDrawer.vue` | Create | 详情抽屉子组件（参数表/示例/徽标） |
| `docs/implementation-log.md` | Modify | 实现记录条目 |

依赖方向：Task 1（后端契约）→ Task 2（接通+页面骨架+横幅）→ Task 3（列表区）→ Task 4（抽屉）→ Task 5（回归+记录）。Task 2-4 同属一个 Vue 文件的增量，必须按序执行。

---

### Task 1: 后端——`McpTool.usageExample()` + `McpDirectoryController`

**Files:**
- Modify: `backend/src/main/java/com/yr/perftest/platform/mcp/McpTool.java`
- Create: `backend/src/main/java/com/yr/perftest/platform/api/McpDirectoryController.java`
- Test: `backend/src/test/java/com/yr/perftest/platform/mcp/McpDirectoryControllerTest.java`

**Interfaces:**
- Consumes: `McpToolRegistry.all()`（返回 `List<McpTool>`，既有）；`McpTool` 的 `name()/title()/description()/stage()/requiresWriteScope()/inputSchema()`（既有）。
- Produces（Task 2 前端类型镜像此契约）:
  - `McpTool.default String usageExample()`——默认返回 `""`；
  - `GET /api/mcp/tools`（登录用户可读）响应：

```json
{
  "server": { "name": "performance-test-platform", "endpoint": "/mcp", "toolCount": 8 },
  "stages": ["PLAN", "NAVIGATE", "DESIGN", "OBSERVE", "DIAGNOSE", "VERIFY", "CAPTURE"],
  "tools": [
    {
      "name": "list_projects",
      "title": "List Projects",
      "stage": "NAVIGATE",
      "requiresWriteScope": false,
      "status": "ENABLED",
      "description": "...",
      "usageExample": "",
      "inputSchema": { "type": "object", "properties": { "...": "..." } }
    }
  ]
}
```

- [ ] **Step 1: Write the failing test**

创建 `backend/src/test/java/com/yr/perftest/platform/mcp/McpDirectoryControllerTest.java`：

```java
package com.yr.perftest.platform.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MCP 工具目录端点测试（P0-2 ②）：目录与注册表严格一致（单一事实源），
 * 固定规范 stage 序列（含预留 PLAN），未登录 401。
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:mcp-directory-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.h2.console.enabled=false"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class McpDirectoryControllerTest {
    private static final List<String> STAGE_ORDER =
            List.of("PLAN", "NAVIGATE", "DESIGN", "OBSERVE", "DIAGNOSE", "VERIFY", "CAPTURE");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private McpToolRegistry registry;

    @Test
    void rejectsUnauthenticatedAccess() throws Exception {
        mockMvc.perform(get("/api/mcp/tools")).andExpect(status().isUnauthorized());
    }

    @Test
    void directoryMirrorsRegistryWithCanonicalStageOrder() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/mcp/tools")
                        .header("Authorization", "Bearer " + loginToken()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());

        assertThat(body.at("/server/name").asText()).isEqualTo("performance-test-platform");
        assertThat(body.at("/server/endpoint").asText()).isEqualTo("/mcp");
        assertThat(body.at("/server/toolCount").asInt()).isEqualTo(registry.all().size());

        List<String> stages = new ArrayList<>();
        body.at("/stages").forEach(node -> stages.add(node.asText()));
        assertThat(stages).containsExactlyElementsOf(STAGE_ORDER);

        List<String> names = new ArrayList<>();
        body.at("/tools").forEach(node -> names.add(node.get("name").asText()));
        assertThat(names).containsExactlyInAnyOrderElementsOf(
                registry.all().stream().map(McpTool::name).toList());

        // tools 按规范 stage 序列排序（组内顺序不作断言）
        int previous = -1;
        for (JsonNode tool : body.at("/tools")) {
            int index = STAGE_ORDER.indexOf(tool.get("stage").asText());
            assertThat(index).isGreaterThanOrEqualTo(previous);
            previous = index;
        }

        // 字段口径：两态 status 全 ENABLED（注册表 v1 无启停标志）、usageExample 默认空串、schema 透传
        for (JsonNode tool : body.at("/tools")) {
            assertThat(tool.get("status").asText()).isEqualTo("ENABLED");
            assertThat(tool.get("usageExample").asText()).isEmpty();
            assertThat(tool.get("requiresWriteScope").isBoolean()).isTrue();
            assertThat(tool.get("inputSchema").isObject()).isTrue();
        }

        JsonNode listProjects = toolByName(body, "list_projects");
        assertThat(listProjects.at("/inputSchema/properties/includeArchived/type").asText())
                .isEqualTo("boolean");
        assertThat(listProjects.get("requiresWriteScope").asBoolean()).isFalse();
        assertThat(toolByName(body, "start_execution").get("requiresWriteScope").asBoolean()).isTrue();
    }

    private JsonNode toolByName(JsonNode body, String name) {
        for (JsonNode tool : body.at("/tools")) {
            if (name.equals(tool.get("name").asText())) {
                return tool;
            }
        }
        throw new AssertionError("tool not found in directory: " + name);
    }

    private String loginToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd /Users/wangk/Documents/Git/performance-test-platform && JAVA_HOME=/Users/wangk/Documents/config/jdk-17.0.17+10/Contents/Home ./gradlew :backend:test --tests "com.yr.perftest.platform.mcp.McpDirectoryControllerTest"
```

Expected: FAIL——`rejectsUnauthenticatedAccess` 期望 401 实得 404（端点不存在），`directoryMirrorsRegistry...` 同样 404/断言失败。

- [ ] **Step 3: Write minimal implementation**

3a. 修改 `backend/src/main/java/com/yr/perftest/platform/mcp/McpTool.java`——`stage()` 的 javadoc 补 PLAN，`inputSchema()` 之后、`call()` 之前新增 default 方法：

```java
    /** 阶段：PLAN / NAVIGATE / DESIGN / OBSERVE / DIAGNOSE / VERIFY / CAPTURE */
    String stage();

    /** 写操作工具需要非只读 scope 才能调用 */
    boolean requiresWriteScope();

    Map<String, Object> inputSchema();

    /** 使用示例（D18：说明文案与使用示例作为工具元数据随平台发布维护）；新工具必须提供，存量工具默认空串。 */
    default String usageExample() {
        return "";
    }

    Object call(Map<String, Object> args, Principal principal);
```

（仅展示被改动区域；`name()/title()/description()` 保持原样。）

3b. 创建 `backend/src/main/java/com/yr/perftest/platform/api/McpDirectoryController.java`：

```java
package com.yr.perftest.platform.api;

import com.yr.perftest.platform.mcp.McpTool;
import com.yr.perftest.platform.mcp.McpToolRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP 工具目录端点（P0-2 ②）：直接映射内存 {@link McpToolRegistry}——单一事实源，
 * 工具上下线随服务重启自动生效，页面侧零配置。Web 页面展示全部注册工具
 * （写权限以徽标标注）；scope 过滤是 MCP 机器身份的调用期语义，不在此重复实现。
 */
@RestController
public class McpDirectoryController {
    /** 阶段固定规范序列（闭环时序，spec §4.1）：不随注册表去重，PLAN 为计划工具（P0-1 后）预留。 */
    static final List<String> STAGE_ORDER =
            List.of("PLAN", "NAVIGATE", "DESIGN", "OBSERVE", "DIAGNOSE", "VERIFY", "CAPTURE");

    private final McpToolRegistry registry;

    public McpDirectoryController(McpToolRegistry registry) {
        this.registry = registry;
    }

    @GetMapping("/api/mcp/tools")
    public Map<String, Object> directory() {
        List<Map<String, Object>> tools = new ArrayList<>();
        for (McpTool tool : registry.all().stream()
                .sorted(Comparator.comparingInt(item -> stageIndex(item.stage())))
                .toList()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", tool.name());
            item.put("title", tool.title());
            item.put("stage", tool.stage());
            item.put("requiresWriteScope", tool.requiresWriteScope());
            // 注册表 v1 无启停标志，全部 ENABLED；未来引入 enabled 后在此透传，页面自动跟随（spec §4.3）
            item.put("status", "ENABLED");
            item.put("description", tool.description());
            item.put("usageExample", tool.usageExample());
            item.put("inputSchema", tool.inputSchema());
            tools.add(item);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("server", Map.of(
                "name", "performance-test-platform",
                "endpoint", "/mcp",
                "toolCount", tools.size()
        ));
        body.put("stages", STAGE_ORDER);
        body.put("tools", tools);
        return body;
    }

    private int stageIndex(String stage) {
        int index = STAGE_ORDER.indexOf(stage);
        return index < 0 ? STAGE_ORDER.size() : index;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
cd /Users/wangk/Documents/Git/performance-test-platform && JAVA_HOME=/Users/wangk/Documents/config/jdk-17.0.17+10/Contents/Home ./gradlew :backend:test --tests "com.yr.perftest.platform.mcp.McpDirectoryControllerTest"
```

Expected: PASS（2 个用例全绿）。

- [ ] **Step 5: MCP 既有端到端回归（default 方法零影响验证）**

```bash
cd /Users/wangk/Documents/Git/performance-test-platform && JAVA_HOME=/Users/wangk/Documents/config/jdk-17.0.17+10/Contents/Home ./gradlew :backend:test --tests "com.yr.perftest.platform.mcp.*"
```

Expected: PASS（`McpServerApiTest` 5 个用例 + 新目录测试全绿）。

- [ ] **Step 6: Commit**

```bash
cd /Users/wangk/Documents/Git/performance-test-platform
git add backend/src/main/java/com/yr/perftest/platform/mcp/McpTool.java \
        backend/src/main/java/com/yr/perftest/platform/api/McpDirectoryController.java \
        backend/src/test/java/com/yr/perftest/platform/mcp/McpDirectoryControllerTest.java
git commit -m "feat：P0-2② MCP 工具目录端点——GET /api/mcp/tools 直映射注册表单一事实源；McpTool 契约补 default usageExample、stage 规范序列预留 PLAN"
```

---

### Task 2: 前端——类型/API/路由/导航接通 + 页面骨架 + 接入指引横幅

**Files:**
- Modify: `frontend/src/types/index.ts:12`（`MainNav` 行）及文件末尾追加类型
- Create: `frontend/src/api/mcp-directory.ts`
- Create: `frontend/src/utils/clipboard.ts`
- Modify: `frontend/src/utils/format.ts`（文件末尾追加函数）
- Modify: `frontend/src/router/index.ts`
- Modify: `frontend/src/components/layout/GlobalRail.vue`
- Modify: `frontend/src/composables/useNavigation.ts`
- Modify: `frontend/src/composables/useBreadcrumb.ts`
- Create: `frontend/src/components/mcp/McpToolDirectoryPage.vue`

**Interfaces:**
- Consumes: Task 1 的 `GET /api/mcp/tools` 响应契约；`frontend/src/api/http.ts` 的 `request<T>(path, options)`；`base.css` 设计令牌。
- Produces（Task 3/4 依赖）:
  - `types`：`McpToolSummary { name: string; title: string; stage: string; requiresWriteScope: boolean; status: 'ENABLED' | 'DISABLED'; description: string; usageExample: string; inputSchema: Record<string, unknown> }`、`McpDirectory { server: { name: string; endpoint: string; toolCount: number }; stages: string[]; tools: McpToolSummary[] }`；
  - `api/mcp-directory.ts`：`fetchMcpDirectoryApi(): Promise<McpDirectory>`；
  - `utils/clipboard.ts`：`copyToClipboard(text: string): Promise<boolean>`；
  - `utils/format.ts`：`mcpStageLabel(stage: string): string`；
  - `McpToolDirectoryPage.vue` 内部状态：`directory/loading/error` refs、`showToast(message: string)`、`mcpEndpoint` computed、高亮工具函数——Task 3/4 在此文件上增量扩展。

- [ ] **Step 1: 补前端类型（`frontend/src/types/index.ts`）**

第 12 行 `MainNav` 改为：

```ts
export type MainNav = 'home' | 'projects' | 'executionNodes' | 'mcpTools' | 'settings' | 'llmConfig';
```

文件末尾追加：

```ts
export type McpToolStatus = 'ENABLED' | 'DISABLED';

export type McpToolSummary = {
  name: string;
  title: string;
  stage: string;
  requiresWriteScope: boolean;
  status: McpToolStatus;
  description: string;
  usageExample: string;
  inputSchema: Record<string, unknown>;
};

export type McpDirectory = {
  server: { name: string; endpoint: string; toolCount: number };
  stages: string[];
  tools: McpToolSummary[];
};
```

- [ ] **Step 2: 创建 `frontend/src/api/mcp-directory.ts`**

```ts
import { request } from './http';
import type { McpDirectory } from '../types';

export function fetchMcpDirectoryApi() {
  return request<McpDirectory>('/api/mcp/tools');
}
```

- [ ] **Step 3: 创建 `frontend/src/utils/clipboard.ts`**

```ts
export async function copyToClipboard(text: string): Promise<boolean> {
  try {
    if (navigator.clipboard && window.isSecureContext) {
      await navigator.clipboard.writeText(text);
      return true;
    }
  } catch {
    // 剪贴板 API 不可用或被拒绝时回退到隐藏 textarea 方案
  }
  const textarea = document.createElement('textarea');
  textarea.value = text;
  textarea.style.position = 'fixed';
  textarea.style.opacity = '0';
  document.body.appendChild(textarea);
  textarea.select();
  let succeeded = false;
  try {
    succeeded = document.execCommand('copy');
  } catch {
    succeeded = false;
  }
  textarea.remove();
  return succeeded;
}
```

- [ ] **Step 4: `frontend/src/utils/format.ts` 文件末尾追加**

```ts
const MCP_STAGE_LABELS: Record<string, string> = {
  PLAN: '计划',
  NAVIGATE: '导航',
  DESIGN: '设计',
  OBSERVE: '观察',
  DIAGNOSE: '诊断',
  VERIFY: '验证',
  CAPTURE: '取证',
};

export function mcpStageLabel(stage: string): string {
  return MCP_STAGE_LABELS[stage] ?? stage;
}
```

- [ ] **Step 5: 路由（`frontend/src/router/index.ts`）**

顶部 import 区追加（与其它视图 import 并列）：

```ts
import McpToolDirectoryPage from '../components/mcp/McpToolDirectoryPage.vue';
```

`'/'` MainLayout children 中、`execution-nodes` 行之后追加：

```ts
        { path: 'mcp-tools', name: 'mcp-tools', component: McpToolDirectoryPage },
```

- [ ] **Step 6: 全局导航（`frontend/src/components/layout/GlobalRail.vue`）**

「执行器配置」按钮之后、「系统配置」按钮之前插入（图标为终端符号，对齐原型 `#i-terminal`）：

```html
    <button
      class="global-rail-btn"
      :class="{ active: activeMainNav === 'mcpTools' }"
      type="button"
      title="MCP 工具"
      @click="selectMainNav('mcpTools')"
    >
      <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 17l6-5-6-5"/><path d="M12 19h8"/></svg>
    </button>
```

- [ ] **Step 7: 导航状态（`frontend/src/composables/useNavigation.ts`）**

`mainNavPaths` 常量补一行：

```ts
const mainNavPaths: Record<MainNav, string> = {
  home: '/',
  projects: '/projects',
  executionNodes: '/execution-nodes',
  mcpTools: '/mcp-tools',
  settings: '/settings',
  llmConfig: '/llm-config/providers',
};
```

`activeMainNav` computed 中、`settings` 判断**之前**插入：

```ts
    if (route.path.startsWith('/mcp-tools')) {
      return 'mcpTools';
    }
```

（注意顺序：必须在 `/settings` 判断前无所谓，但保持与 `executionNodes` 同风格的独立分支即可。）

- [ ] **Step 8: 面包屑（`frontend/src/composables/useBreadcrumb.ts`）**

`// 执行器配置` 分支之后插入：

```ts
    // MCP 工具目录
    if (route.path.startsWith('/mcp-tools')) {
      segments.push({ label: 'MCP 工具目录' });
      return segments;
    }
```

- [ ] **Step 9: 创建页面骨架 `frontend/src/components/mcp/McpToolDirectoryPage.vue`**

完整初始文件（Task 3/4 在此之上增量；本步交付：页头 + 接入指引横幅 + 加载/错误态 + 页脚 + toast）：

```vue
<template>
  <section class="mcp-directory">
    <div class="page-head">
      <div>
        <h1>MCP 工具目录</h1>
        <p class="sub">
          平台全部已注册 MCP 工具的能力清单——本地 Agent（Claude Code / DSH）接入后即可调用。
          工具随版本发布自动上下线，本页无需配置。
        </p>
      </div>
      <div class="head-chips">
        <span class="chip chip-ok"><span class="dot"></span>MCP 服务在线</span>
        <span v-if="directory" class="chip chip-neutral">
          {{ directory.server.toolCount }} 个工具 · {{ directory.stages.length }} 个阶段
        </span>
      </div>
    </div>

    <section class="access" aria-label="接入指引">
      <div class="access-hd">
        <svg
          class="ic"
          viewBox="0 0 24 24"
          aria-hidden="true"
        ><path d="M9 3v6M15 3v6"/><path d="M7 9h10v3a5 5 0 0 1-10 0z"/><path d="M12 17v4"/></svg>
        <h2>本地 Agent 接入指引</h2>
        <span class="hint">三步完成，全程无需找人</span>
      </div>
      <div class="access-bd">
        <div class="steps">
          <div class="step">
            <span class="step-no">1</span>
            <div>
              <b>申请 Agent API Key</b>
              <p>由管理员在「系统配置 → Agent API Key」签发；只读演练用 <code>readonly</code> scope，含写操作的全流程用普通 scope。</p>
              <RouterLink class="link-btn" to="/settings">
                前往系统配置申请
                <svg
                  class="ic sm"
                  viewBox="0 0 24 24"
                  aria-hidden="true"
                ><path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6"/><path d="M15 3h6v6M10 14 21 3"/></svg>
              </RouterLink>
            </div>
          </div>
          <div class="step">
            <span class="step-no">2</span>
            <div>
              <b>复制 Agent 配置片段</b>
              <p>粘贴到本地 Agent 的 MCP 配置文件，地址与鉴权头已按当前环境填好。</p>
            </div>
          </div>
          <div class="step">
            <span class="step-no">3</span>
            <div>
              <b>开箱即用</b>
              <p>Agent 重启后自动发现下方全部工具；写权限工具需非只读 API Key 调用。</p>
            </div>
          </div>
        </div>
        <div class="config">
          <div class="config-top">
            <div class="seg" role="tablist" aria-label="选择 Agent 类型">
              <button
                type="button"
                role="tab"
                :aria-selected="agentKind === 'cc'"
                :class="{ on: agentKind === 'cc' }"
                @click="agentKind = 'cc'"
              >Claude Code</button>
              <button
                type="button"
                role="tab"
                :aria-selected="agentKind === 'dsh'"
                :class="{ on: agentKind === 'dsh' }"
                @click="agentKind = 'dsh'"
              >DSH</button>
            </div>
            <span class="endpoint"><span class="method">POST</span> {{ mcpEndpoint }}</span>
          </div>
          <div class="codebox">
            <!-- eslint-disable-next-line vue/no-v-html -- 内容为本组件生成的转义高亮文本，无用户输入 -->
            <pre v-html="highlightedConfig"></pre>
            <button class="copy-btn" type="button" :class="{ done: configCopied }" @click="copyConfig">
              <svg
                class="ic xs"
                viewBox="0 0 24 24"
                aria-hidden="true"
              ><rect x="9" y="9" width="12" height="12" rx="2"/><path d="M5 15V5a2 2 0 0 1 2-2h10"/></svg>
              {{ configCopied ? '已复制' : '复制配置' }}
            </button>
          </div>
          <p class="scope-note">
            <svg
              class="ic sm"
              viewBox="0 0 24 24"
              aria-hidden="true"
            ><circle cx="12" cy="12" r="9"/><path d="M12 11v5M12 8h.01"/></svg>
            认证方式为请求头 <code>X-API-Key</code>；平台仅接受机器身份访问 MCP，Web 账号不能直连。
          </p>
        </div>
      </div>
    </section>

    <div v-if="loading" class="state-note">正在加载工具清单…</div>
    <div v-else-if="error" class="state-note error">
      {{ error }}
      <button class="retry-btn" type="button" @click="load">重试</button>
    </div>

    <footer class="page-ft">
      <span class="ft-left">
        <svg
          class="ic sm"
          viewBox="0 0 24 24"
          aria-hidden="true"
        ><circle cx="12" cy="12" r="9"/><path d="M12 11v5M12 8h.01"/></svg>
        工具清单由服务注册表实时生成，随版本发布自动上下线，本页无需任何配置
      </span>
      <span class="ft-right">performance-test-platform · MCP</span>
    </footer>

    <div class="toast" :class="{ show: toastVisible }" role="status">
      <svg
        class="ic sm"
        viewBox="0 0 24 24"
        aria-hidden="true"
      ><path d="m4 12.5 5 5L20 6.5"/></svg>
      <span>{{ toastText }}</span>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { fetchMcpDirectoryApi } from '../../api/mcp-directory';
import type { McpDirectory } from '../../types';
import { copyToClipboard } from '../../utils/clipboard';

const directory = ref<McpDirectory | null>(null);
const loading = ref(false);
const error = ref('');
const agentKind = ref<'cc' | 'dsh'>('cc');
const configCopied = ref(false);
const toastVisible = ref(false);
const toastText = ref('');
let toastTimer: number | undefined;

const mcpEndpoint = computed(() => `${window.location.origin}/mcp`);

const configText = computed(() =>
  agentKind.value === 'cc' ? claudeCodeConfig(mcpEndpoint.value) : dshConfig(mcpEndpoint.value),
);

const highlightedConfig = computed(() =>
  agentKind.value === 'cc' ? highlightClaudeCode(configText.value) : highlightDsh(configText.value),
);

function claudeCodeConfig(endpoint: string): string {
  return [
    '~/.claude.json — mcpServers 配置：',
    '{',
    '  "mcpServers": {',
    '    "perf-platform": {',
    '      "type": "http",',
    `      "url": "${endpoint}",`,
    '      "headers": { "X-API-Key": "<agent-api-key>" }',
    '    }',
    '  }',
    '}',
  ].join('\n');
}

function dshConfig(endpoint: string): string {
  return [
    'DSH MCP 接入配置（Streamable HTTP）：',
    `endpoint: ${endpoint}`,
    'headers:',
    '  X-API-Key: <agent-api-key>',
  ].join('\n');
}

function escapeHtml(text: string): string {
  return text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

function highlightClaudeCode(code: string): string {
  const [firstLine, ...rest] = code.split('\n');
  const body = escapeHtml(rest.join('\n')).replace(
    /"([^"\n]+)"(\s*:)?/g,
    (_match, value: string, colon?: string) =>
      colon
        ? `<span class='tok-key'>"${value}"</span>${colon}`
        : `<span class='tok-str'>"${value}"</span>`,
  );
  return `<span class='tok-cmt'>${escapeHtml(firstLine)}</span>\n${body}`;
}

function highlightDsh(code: string): string {
  return escapeHtml(code)
    .replace(/^(#.*)$/gm, "<span class='tok-cmt'>$1</span>")
    .replace(/^(\s*[\w-]+)(:)(.*)$/gm, "<span class='tok-key'>$1</span>$2<span class='tok-str'>$3</span>");
}

function showToast(message: string) {
  toastText.value = message;
  toastVisible.value = true;
  if (toastTimer !== undefined) clearTimeout(toastTimer);
  toastTimer = window.setTimeout(() => {
    toastVisible.value = false;
  }, 1600);
}

async function copyConfig() {
  if (await copyToClipboard(configText.value)) {
    configCopied.value = true;
    showToast('配置已复制到剪贴板');
    window.setTimeout(() => {
      configCopied.value = false;
    }, 1800);
  }
}

async function load() {
  loading.value = true;
  error.value = '';
  try {
    directory.value = await fetchMcpDirectoryApi();
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载失败';
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  void load();
});

onBeforeUnmount(() => {
  if (toastTimer !== undefined) clearTimeout(toastTimer);
});
</script>

<style scoped>
.mcp-directory {
  --shadow-pop: 0 8px 24px rgba(26, 35, 50, 0.1), 0 2px 6px rgba(26, 35, 50, 0.06);
  font-size: 14px;
}

.mcp-directory .ic {
  width: 16px;
  height: 16px;
  flex: none;
  stroke: currentColor;
  fill: none;
  stroke-width: 1.7;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.mcp-directory .ic.sm { width: 14px; height: 14px; }
.mcp-directory .ic.xs { width: 12px; height: 12px; }

/* ===== 页头 ===== */
.mcp-directory .page-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
  flex-wrap: wrap;
}

.mcp-directory .page-head h1 {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
  letter-spacing: 0.2px;
  color: var(--ink);
}

.mcp-directory .page-head .sub {
  margin: 4px 0 0;
  font-size: 13px;
  color: var(--muted);
}

.mcp-directory .head-chips { display: flex; gap: 8px; }

.mcp-directory .chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 5px 12px;
  border-radius: 16px;
  font-size: 12px;
  font-weight: 600;
  border: 1px solid transparent;
}

.mcp-directory .chip .dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  flex: none;
}

.mcp-directory .chip-ok { background: var(--ok-soft); color: var(--ok); }

.mcp-directory .chip-ok .dot {
  background: var(--ok);
  box-shadow: 0 0 0 3px rgba(47, 155, 106, 0.15);
  animation: mcp-pulse 2.4s infinite;
}

@keyframes mcp-pulse {
  0%, 100% { box-shadow: 0 0 0 3px rgba(47, 155, 106, 0.15); }
  50% { box-shadow: 0 0 0 5px rgba(47, 155, 106, 0.05); }
}

.mcp-directory .chip-neutral { background: var(--surface); color: var(--muted); border-color: var(--line); }

/* ===== 接入指引横幅 ===== */
.mcp-directory .access {
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--radius);
  box-shadow: var(--shadow);
  margin-bottom: 20px;
  overflow: hidden;
}

.mcp-directory .access-hd {
  padding: 14px 20px;
  border-bottom: 1px solid var(--line);
  display: flex;
  align-items: center;
  gap: 10px;
}

.mcp-directory .access-hd .ic { color: var(--primary-dark); }

.mcp-directory .access-hd h2 { margin: 0; font-size: 15px; font-weight: 600; color: var(--ink); }

.mcp-directory .access-hd .hint { margin-left: auto; font-size: 12px; color: var(--muted); }

.mcp-directory .access-bd { display: grid; grid-template-columns: 1fr 1.35fr; }

.mcp-directory .steps {
  padding: 18px 20px;
  border-right: 1px solid var(--line);
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.mcp-directory .step { display: flex; gap: 12px; }

.mcp-directory .step-no {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: var(--accent-soft);
  color: var(--primary-dark);
  font-size: 12px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  flex: none;
  margin-top: 2px;
}

.mcp-directory .step b { font-size: 13px; font-weight: 600; display: block; margin-bottom: 2px; color: var(--ink); }

.mcp-directory .step p { margin: 0; font-size: 12.5px; color: var(--muted); }

.mcp-directory .step .link-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  margin-top: 6px;
  font-size: 12.5px;
  font-weight: 600;
  color: var(--primary-dark);
}

.mcp-directory .step .link-btn:hover { text-decoration: underline; }

.mcp-directory .config { padding: 18px 20px; background: var(--surface-soft); }

.mcp-directory .config-top {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
  flex-wrap: wrap;
}

.mcp-directory .seg {
  display: inline-flex;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  padding: 2px;
}

.mcp-directory .seg button {
  padding: 4px 12px;
  border-radius: 4px;
  font-size: 12.5px;
  font-weight: 500;
  color: var(--muted);
  border: none;
  background: none;
  cursor: pointer;
  transition: all 0.18s;
}

.mcp-directory .seg button.on { background: var(--accent); color: var(--primary-ink); }

.mcp-directory .endpoint {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-family: var(--font-data);
  font-size: 12px;
  color: var(--ink);
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  padding: 4px 10px;
}

.mcp-directory .endpoint .method { color: var(--primary-dark); font-weight: 700; }

.mcp-directory .codebox { position: relative; }

.mcp-directory .codebox pre {
  margin: 0;
  background: #0e1720;
  color: #d7e3ec;
  border-radius: var(--radius-sm);
  padding: 14px 16px;
  overflow: auto;
  font-family: var(--font-data);
  font-size: 12.5px;
  line-height: 1.7;
  max-height: 218px;
  text-align: left;
}

.mcp-directory .codebox :deep(.tok-key) { color: #7fd4dc; }
.mcp-directory .codebox :deep(.tok-str) { color: #a7d8a0; }
.mcp-directory .codebox :deep(.tok-cmt) { color: #5c7080; font-style: italic; }

.mcp-directory .copy-btn {
  position: absolute;
  top: 8px;
  right: 8px;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 5px 10px;
  border-radius: var(--radius-sm);
  background: rgba(255, 255, 255, 0.08);
  color: #c9d7e2;
  font-size: 12px;
  border: none;
  cursor: pointer;
  transition: background 0.18s;
}

.mcp-directory .copy-btn:hover { background: rgba(255, 255, 255, 0.16); }

.mcp-directory .copy-btn.done { color: #a7d8a0; }

.mcp-directory .scope-note {
  margin: 10px 0 0;
  font-size: 12px;
  color: var(--muted);
  display: flex;
  gap: 6px;
  align-items: flex-start;
}

.mcp-directory .scope-note .ic { margin-top: 2px; color: var(--warn); width: 14px; height: 14px; }

/* ===== 状态提示 ===== */
.mcp-directory .state-note { padding: 48px 0; text-align: center; color: var(--muted); font-size: 13.5px; }

.mcp-directory .state-note.error { color: var(--danger); }

.mcp-directory .retry-btn {
  margin-left: 10px;
  font-size: 13px;
  color: var(--primary-dark);
  font-weight: 600;
  background: none;
  border: none;
  cursor: pointer;
}

.mcp-directory .retry-btn:hover { text-decoration: underline; }

/* ===== 页脚 ===== */
.mcp-directory .page-ft {
  margin-top: 34px;
  padding-top: 16px;
  border-top: 1px solid var(--line);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  font-size: 12.5px;
  color: var(--muted);
}

.mcp-directory .page-ft .ft-left { display: inline-flex; align-items: center; gap: 7px; }

.mcp-directory .page-ft .ft-left .ic { color: var(--primary-dark); width: 14px; height: 14px; }

.mcp-directory .page-ft .ft-right { font-family: var(--font-data); font-size: 11.5px; letter-spacing: 0.5px; }

/* ===== Toast ===== */
.mcp-directory .toast {
  position: fixed;
  bottom: 28px;
  left: 50%;
  transform: translate(-50%, 16px);
  background: var(--ink);
  color: #fff;
  border-radius: var(--radius-sm);
  padding: 9px 18px;
  font-size: 13px;
  display: flex;
  align-items: center;
  gap: 8px;
  opacity: 0;
  pointer-events: none;
  transition: all 0.22s;
  z-index: 200;
  box-shadow: var(--shadow-pop);
}

.mcp-directory .toast.show { opacity: 1; transform: translate(-50%, 0); }

.mcp-directory .toast .ic { color: #7fd4dc; width: 15px; height: 15px; }

/* ===== 响应式 & 动效偏好 ===== */
@media (max-width: 1080px) {
  .mcp-directory .access-bd { grid-template-columns: 1fr; }
  .mcp-directory .steps { border-right: none; border-bottom: 1px solid var(--line); }
}

@media (prefers-reduced-motion: reduce) {
  .mcp-directory *,
  .mcp-directory *::before,
  .mcp-directory *::after {
    transition: none !important;
    animation: none !important;
  }
}
</style>
```

- [ ] **Step 10: 构建验证**

```bash
cd /Users/wangk/Documents/Git/performance-test-platform/frontend && npm run build
```

Expected: `vue-tsc --noEmit` 零错误、vite build 成功输出 dist。

- [ ] **Step 11: 手测（dev server，可选但建议）**

```bash
cd /Users/wangk/Documents/Git/performance-test-platform/frontend && npm run dev
```

浏览器登录后：全局导航出现「MCP 工具」图标按钮（执行器配置下方）→ 点击进入 `/mcp-tools`：页头两 chips、三步指引、配置片段 tab 切换、复制按钮变「已复制」+ toast、endpoint 显示当前 origin、页脚渲染。对照 `mcp-directory-prototype.html`。

- [ ] **Step 12: Commit**

```bash
cd /Users/wangk/Documents/Git/performance-test-platform
git add frontend/src/types/index.ts \
        frontend/src/api/mcp-directory.ts \
        frontend/src/utils/clipboard.ts \
        frontend/src/utils/format.ts \
        frontend/src/router/index.ts \
        frontend/src/components/layout/GlobalRail.vue \
        frontend/src/composables/useNavigation.ts \
        frontend/src/composables/useBreadcrumb.ts \
        frontend/src/components/mcp/McpToolDirectoryPage.vue
git commit -m "feat：P0-2② MCP 工具目录页骨架——/mcp-tools 顶级路由与全局导航接通，接入指引横幅（Claude Code/DSH 配置片段一键复制、API Key 申请入口）"
```

---

### Task 3: 前端——工具列表区（阶段筛选 tabs + 搜索 + 平铺卡片网格 + 空状态）

**Files:**
- Modify: `frontend/src/components/mcp/McpToolDirectoryPage.vue`（模板、脚本、样式三处增量）

**Interfaces:**
- Consumes: Task 2 的 `directory` ref、`mcpStageLabel()`（format）、`McpToolSummary` 类型、`.ic` 图标基类。
- Produces（Task 4 依赖）: `tools` computed（`McpToolSummary[]`）、`openTool(tool: McpToolSummary)` / `closeDrawer()` / `selectedTool` ref（本任务先落 script 部分，Task 4 接卡片点击）、stage 徽标配色类 `st-<STAGE>` 与状态图标样式约定。

- [ ] **Step 1: 脚本增量（`<script setup>` 内）**

import 区补：

```ts
import type { McpDirectory, McpToolSummary } from '../../types';
import { mcpStageLabel } from '../../utils/format';
```

（原 `import type { McpDirectory } from '../../types';` 行替换为上面第一行。）

响应式区追加（`const error = ref('');` 之后）：

```ts
const currentStage = ref('全部');
const keyword = ref('');
const selectedTool = ref<McpToolSummary | null>(null);
```

computed 区追加（`mcpEndpoint` 之后）：

```ts
const tools = computed(() => directory.value?.tools ?? []);

const stageTabs = computed(() => {
  const tabs = [{ stage: '全部', label: '全部', count: tools.value.length }];
  for (const stage of directory.value?.stages ?? []) {
    tabs.push({
      stage,
      label: mcpStageLabel(stage),
      count: tools.value.filter((tool) => tool.stage === stage).length,
    });
  }
  return tabs;
});

const filteredTools = computed(() => {
  const kw = keyword.value.trim().toLowerCase();
  return tools.value.filter((tool) => {
    if (currentStage.value !== '全部' && tool.stage !== currentStage.value) {
      return false;
    }
    if (!kw) {
      return true;
    }
    return `${tool.name}\n${tool.title}\n${tool.description}\n${tool.stage}`.toLowerCase().includes(kw);
  });
});
```

方法区追加（`copyConfig` 之后）：

```ts
function clearSearch() {
  keyword.value = '';
}

function openTool(tool: McpToolSummary) {
  selectedTool.value = tool;
}

function closeDrawer() {
  selectedTool.value = null;
}

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    closeDrawer();
  }
}
```

生命周期改为（替换 Task 2 的 `onMounted`/`onBeforeUnmount`）：

```ts
onMounted(() => {
  void load();
  document.addEventListener('keydown', onKeydown);
});

onBeforeUnmount(() => {
  document.removeEventListener('keydown', onKeydown);
  if (toastTimer !== undefined) clearTimeout(toastTimer);
});
```

- [ ] **Step 2: 模板增量**

在 `</section>`（接入指引横幅结束标签）之后、`<div v-if="loading" ...>` 之前插入：

```html
    <div v-if="directory" class="toolbar">
      <div class="tabs" role="tablist" aria-label="按阶段筛选">
        <button
          v-for="tab in stageTabs"
          :key="tab.stage"
          type="button"
          role="tab"
          class="tab"
          :class="{ on: currentStage === tab.stage }"
          :aria-selected="currentStage === tab.stage"
          @click="currentStage = tab.stage"
        >{{ tab.label }}<span class="n">{{ tab.count }}</span></button>
      </div>
      <label class="search">
        <svg
          class="ic"
          viewBox="0 0 24 24"
          aria-hidden="true"
        ><circle cx="11" cy="11" r="7"/><path d="m21 21-4.3-4.3"/></svg>
        <input
          v-model="keyword"
          type="search"
          placeholder="搜索工具名称 / 说明…"
          aria-label="搜索工具"
        />
      </label>
      <span class="result-count">{{ filteredTools.length }} / {{ tools.length }} 个工具</span>
      <span class="legend" aria-label="状态图例">
        <span class="st-ic ok" role="img" aria-label="可用" title="可用">
          <svg
            class="ic"
            viewBox="0 0 24 24"
            aria-hidden="true"
          ><circle cx="12" cy="12" r="9" fill="currentColor" stroke="none"/><path d="m8.3 12.4 2.6 2.6 4.9-5.4" stroke="#fff" stroke-width="2"/></svg>
        </span>
        <span class="st-ic off" role="img" aria-label="不可用" title="不可用">
          <svg
            class="ic"
            viewBox="0 0 24 24"
            aria-hidden="true"
          ><circle cx="12" cy="12" r="8.4"/><path d="M6.4 6.4 17.6 17.6"/></svg>
        </span>
      </span>
    </div>

    <div v-if="directory" class="grid-area">
      <div v-if="filteredTools.length" class="grid">
        <button
          v-for="tool in filteredTools"
          :key="tool.name"
          type="button"
          class="card"
          :class="{ off: tool.status === 'DISABLED' }"
          aria-haspopup="dialog"
          @click="openTool(tool)"
        >
          <div class="card-top">
            <span class="tool-name">{{ tool.name }}</span>
            <span class="badge stage" :class="`st-${tool.stage}`">{{ mcpStageLabel(tool.stage) }}</span>
          </div>
          <div class="tool-title">{{ tool.title }}</div>
          <p class="tool-desc">{{ tool.description }}</p>
          <div class="badges">
            <span v-if="tool.requiresWriteScope" class="badge b-write">
              <svg
                class="ic xs"
                viewBox="0 0 24 24"
                aria-hidden="true"
              ><circle cx="7.5" cy="15.5" r="4"/><path d="m10.5 12.5 9-9M17 4l3 3M14 7l2.5 2.5"/></svg>
              需写权限
            </span>
            <span v-else class="badge b-read">只读</span>
          </div>
          <div class="card-foot">
            <span class="card-stage">
              <span
                class="st-ic"
                :class="tool.status === 'DISABLED' ? 'off' : 'ok'"
                role="img"
                :aria-label="tool.status === 'DISABLED' ? '不可用' : '可用'"
                :title="tool.status === 'DISABLED' ? '不可用' : '可用'"
              >
                <svg
                  v-if="tool.status !== 'DISABLED'"
                  class="ic"
                  viewBox="0 0 24 24"
                  aria-hidden="true"
                ><circle cx="12" cy="12" r="9" fill="currentColor" stroke="none"/><path d="m8.3 12.4 2.6 2.6 4.9-5.4" stroke="#fff" stroke-width="2"/></svg>
                <svg
                  v-else
                  class="ic"
                  viewBox="0 0 24 24"
                  aria-hidden="true"
                ><circle cx="12" cy="12" r="8.4"/><path d="M6.4 6.4 17.6 17.6"/></svg>
              </span>
              阶段：{{ mcpStageLabel(tool.stage) }}
            </span>
            <span class="detail-hint">
              查看详情
              <svg
                class="ic xs"
                viewBox="0 0 24 24"
                aria-hidden="true"
              ><path d="M4 12h15M13 6l6 6-6 6"/></svg>
            </span>
          </div>
        </button>
      </div>
      <div v-else class="empty">
        <svg
          class="ic lg"
          viewBox="0 0 24 24"
          aria-hidden="true"
        ><circle cx="11" cy="11" r="7"/><path d="m21 21-4.3-4.3"/></svg>
        <p>未找到匹配「{{ keyword.trim() }}」的工具</p>
        <button type="button" @click="clearSearch">清除搜索</button>
      </div>
    </div>
```

- [ ] **Step 3: 样式增量（`<style scoped>` 内、`/* ===== 响应式 ===== */` 之前插入）**

```css
/* ===== 工具栏 ===== */
.mcp-directory .toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.mcp-directory .tabs { display: flex; gap: 6px; flex-wrap: wrap; }

.mcp-directory .tab {
  padding: 6px 13px;
  border-radius: 16px;
  border: 1px solid var(--line);
  background: var(--surface);
  font-size: 12.5px;
  font-weight: 500;
  color: var(--muted);
  display: inline-flex;
  align-items: center;
  gap: 6px;
  transition: all 0.18s;
  cursor: pointer;
}

.mcp-directory .tab:hover { border-color: var(--line-strong); color: var(--ink); }

.mcp-directory .tab.on {
  background: var(--accent-soft);
  border-color: var(--active-bg-strong);
  color: var(--primary-dark);
  font-weight: 600;
}

.mcp-directory .tab .n {
  font-family: var(--font-data);
  font-size: 11px;
  background: rgba(26, 35, 50, 0.06);
  border-radius: 9px;
  padding: 0 6px;
  line-height: 17px;
}

.mcp-directory .tab.on .n { background: rgba(11, 127, 138, 0.14); }

.mcp-directory .search {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 8px;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  padding: 7px 12px;
  width: 250px;
  transition: border-color 0.18s;
}

.mcp-directory .search:focus-within { border-color: var(--accent); }

.mcp-directory .search .ic { color: var(--muted); width: 15px; height: 15px; }

.mcp-directory .search input {
  border: none;
  outline: none;
  font: inherit;
  font-size: 13px;
  width: 100%;
  background: transparent;
  color: var(--ink);
}

.mcp-directory .result-count { font-size: 12.5px; color: var(--muted); white-space: nowrap; }

/* ===== 徽标体系 ===== */
.mcp-directory .badge {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 2px 9px;
  border-radius: 11px;
  font-size: 11.5px;
  font-weight: 600;
  letter-spacing: 0.2px;
  white-space: nowrap;
}

.mcp-directory .st-PLAN { background: var(--accent-soft); color: var(--primary-dark); }
.mcp-directory .st-NAVIGATE { background: #e8effd; color: #2563eb; }
.mcp-directory .st-DESIGN { background: #f1eafd; color: #7c3aed; }
.mcp-directory .st-OBSERVE { background: #eef2f6; color: #475569; }
.mcp-directory .st-DIAGNOSE { background: var(--warning-soft); color: var(--warn); }
.mcp-directory .st-VERIFY { background: var(--ok-soft); color: var(--ok); }
.mcp-directory .st-CAPTURE { background: #fdeaf3; color: #be185d; }

.mcp-directory .b-write {
  background: var(--warning-soft);
  border: 1px solid var(--warning-border);
  color: var(--warn);
}

.mcp-directory .b-read { background: var(--surface); border: 1px solid var(--line); color: var(--muted); }

/* 状态图标：两态纯图标（可用 / 不可用） */
.mcp-directory .st-ic { display: inline-flex; align-items: center; justify-content: center; flex: none; }
.mcp-directory .st-ic .ic { width: 15px; height: 15px; }
.mcp-directory .st-ic.ok { color: var(--ok); }
.mcp-directory .st-ic.off { color: #8aa0b0; }
.mcp-directory .legend { display: inline-flex; align-items: center; gap: 8px; padding-left: 2px; }

/* ===== 卡片网格：单一平铺，不做阶段分组 ===== */
.mcp-directory .grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(310px, 1fr));
  gap: 14px;
}

.mcp-directory .card {
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--radius);
  box-shadow: var(--shadow);
  padding: 16px 18px 14px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  text-align: left;
  position: relative;
  transition: box-shadow 0.18s, border-color 0.18s, transform 0.18s;
  cursor: pointer;
  font: inherit;
  color: inherit;
}

.mcp-directory .card:hover {
  box-shadow: var(--shadow-pop);
  border-color: var(--line-strong);
  transform: translateY(-1px);
}

.mcp-directory .card.off { opacity: 0.6; background: var(--surface-soft); }
.mcp-directory .card.off:hover { opacity: 1; }

.mcp-directory .card-top { display: flex; align-items: flex-start; gap: 10px; }

.mcp-directory .tool-name {
  font-family: var(--font-data);
  font-size: 14.5px;
  font-weight: 600;
  word-break: break-all;
  color: var(--ink);
}

.mcp-directory .card-top .stage { margin-left: auto; }

.mcp-directory .tool-title { font-size: 13px; font-weight: 600; color: var(--ink); }

.mcp-directory .tool-desc {
  margin: 0;
  font-size: 12.5px;
  color: var(--muted);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  min-height: 40px;
}

.mcp-directory .badges {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  margin-top: auto;
  padding-top: 8px;
}

.mcp-directory .card-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-top: 1px dashed var(--line);
  padding-top: 9px;
  margin-top: 2px;
  font-size: 12px;
  color: var(--muted);
}

.mcp-directory .card-stage { display: inline-flex; align-items: center; gap: 6px; }

.mcp-directory .detail-hint {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--primary-dark);
  font-weight: 500;
}

.mcp-directory .detail-hint .ic { width: 13px; height: 13px; transition: transform 0.18s; }

.mcp-directory .card:hover .detail-hint .ic { transform: translateX(2px); }

/* ===== 空状态 ===== */
.mcp-directory .empty { text-align: center; padding: 64px 0; color: var(--muted); }

.mcp-directory .empty .ic { width: 34px; height: 34px; margin: 0 auto 12px; color: var(--line-strong); display: block; }

.mcp-directory .empty p { margin: 0; font-size: 13.5px; }

.mcp-directory .empty button {
  margin-top: 12px;
  font-size: 13px;
  color: var(--primary-dark);
  font-weight: 600;
  background: none;
  border: none;
  cursor: pointer;
}

.mcp-directory .empty button:hover { text-decoration: underline; }
```

并在 1080px 媒体查询块内补一行、新增 720px 块（与既有响应式段合并）：

```css
@media (max-width: 720px) {
  .mcp-directory .search { width: 100%; margin-left: 0; }
  .mcp-directory .grid { grid-template-columns: 1fr; }
}
```

- [ ] **Step 4: 构建验证**

```bash
cd /Users/wangk/Documents/Git/performance-test-platform/frontend && npm run build
```

Expected: 零错误通过。

- [ ] **Step 5: 手测清单（dev server）**

阶段 tabs（全部 + 7 阶段含计数，PLAN 为 0 也显示）；搜索名称/描述即时过滤；结果计数 `n / 8`；卡片含等宽名、标题、stage 徽标、写权限/只读徽标、绿圈状态图标、两行截断描述；无匹配时空状态 + 清除搜索；宽屏多列铺满、无分组头、无成片空白；页脚在列表下方。对照原型。

- [ ] **Step 6: Commit**

```bash
cd /Users/wangk/Documents/Git/performance-test-platform
git add frontend/src/components/mcp/McpToolDirectoryPage.vue
git commit -m "feat：P0-2② 工具目录列表区——阶段筛选 tabs（固定序列含 PLAN）+ 本地搜索 + 单一平铺卡片网格 + 两态状态图标 + 空状态与页脚"
```

---

### Task 4: 前端——详情抽屉（接口文档式：参数表 / 使用示例 / 徽标自包含）

**Files:**
- Create: `frontend/src/components/mcp/McpToolDetailDrawer.vue`
- Modify: `frontend/src/components/mcp/McpToolDirectoryPage.vue`（挂抽屉 + backdrop）

**Interfaces:**
- Consumes: `McpToolSummary` 类型、`mcpStageLabel()`、`copyToClipboard()`、Task 3 的 `selectedTool`/`closeDrawer()`、`.badge`/`.st-<STAGE>`/`.b-write`/`.b-read`/`.st-ic` 类名约定（抽屉组件自带同名样式，scoped 隔离）。
- Produces: `McpToolDetailDrawer` 组件——props `{ tool: McpToolSummary }`，emits `close` / `copied(message: string)`；从 `tool.inputSchema.properties` + `required[]` 渲染参数表。

- [ ] **Step 1: 创建 `frontend/src/components/mcp/McpToolDetailDrawer.vue`**

```vue
<template>
  <aside class="drawer" role="dialog" aria-modal="true" :aria-label="`工具详情：${tool.name}`">
    <div class="drawer-hd">
      <div class="titles">
        <div class="tool-name">{{ tool.name }}</div>
        <div class="tool-title">{{ tool.title }}</div>
        <div class="badges">
          <span class="badge" :class="`st-${tool.stage}`">{{ mcpStageLabel(tool.stage) }} · {{ tool.stage }}</span>
          <span v-if="tool.requiresWriteScope" class="badge b-write">
            <svg
              class="ic xs"
              viewBox="0 0 24 24"
              aria-hidden="true"
            ><circle cx="7.5" cy="15.5" r="4"/><path d="m10.5 12.5 9-9M17 4l3 3M14 7l2.5 2.5"/></svg>
            需写权限
          </span>
          <span v-else class="badge b-read">只读</span>
          <span
            class="st-ic"
            :class="enabled ? 'ok' : 'off'"
            role="img"
            :aria-label="enabled ? '可用' : '不可用'"
            :title="enabled ? '可用' : '不可用'"
          >
            <svg
              v-if="enabled"
              class="ic"
              viewBox="0 0 24 24"
              aria-hidden="true"
            ><circle cx="12" cy="12" r="9" fill="currentColor" stroke="none"/><path d="m8.3 12.4 2.6 2.6 4.9-5.4" stroke="#fff" stroke-width="2"/></svg>
            <svg
              v-else
              class="ic"
              viewBox="0 0 24 24"
              aria-hidden="true"
            ><circle cx="12" cy="12" r="8.4"/><path d="M6.4 6.4 17.6 17.6"/></svg>
          </span>
        </div>
      </div>
      <button class="drawer-close" type="button" aria-label="关闭详情" @click="emit('close')">
        <svg
          class="ic"
          viewBox="0 0 24 24"
          aria-hidden="true"
        ><path d="M6 6l12 12M18 6 6 18"/></svg>
      </button>
    </div>

    <div class="drawer-bd">
      <section class="sec">
        <h3>
          <svg
            class="ic"
            viewBox="0 0 24 24"
            aria-hidden="true"
          ><path d="M2 4h6a4 4 0 0 1 4 4v13a3 3 0 0 0-3-3H2z"/><path d="M22 4h-6a4 4 0 0 0-4 4v13a3 3 0 0 1 3-3h7z"/></svg>
          功能说明
        </h3>
        <p class="desc">{{ tool.description }}</p>
      </section>

      <section class="sec">
        <h3>
          <svg
            class="ic"
            viewBox="0 0 24 24"
            aria-hidden="true"
          ><path d="M4 6h9M17 6h3M4 12h3M11 12h9M4 18h9M17 18h3"/><circle cx="15" cy="6" r="2"/><circle cx="9" cy="12" r="2"/><circle cx="15" cy="18" r="2"/></svg>
          入参
        </h3>
        <table class="params">
          <thead>
            <tr>
              <th style="width: 26%">参数</th>
              <th style="width: 14%">类型</th>
              <th style="width: 12%">必填</th>
              <th>说明</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="param in parameters" :key="param.name">
              <td class="p-name">{{ param.name }}</td>
              <td class="p-type">{{ param.type }}</td>
              <td>
                <span v-if="param.required" class="req">必填</span>
                <span v-else class="opt">可选</span>
              </td>
              <td>{{ param.description }}</td>
            </tr>
          </tbody>
        </table>
      </section>

      <section class="sec">
        <h3>
          <svg
            class="ic"
            viewBox="0 0 24 24"
            aria-hidden="true"
          ><path d="M4 17l6-5-6-5"/><path d="M12 19h8"/></svg>
          使用示例
        </h3>
        <div v-if="tool.usageExample" class="codebox">
          <pre>{{ tool.usageExample }}</pre>
          <button class="copy-btn" type="button" :class="{ done: copied }" @click="copyExample">
            <svg
              class="ic xs"
              viewBox="0 0 24 24"
              aria-hidden="true"
            ><rect x="9" y="9" width="12" height="12" rx="2"/><path d="M5 15V5a2 2 0 0 1 2-2h10"/></svg>
            {{ copied ? '已复制' : '复制示例' }}
          </button>
        </div>
        <p v-else class="no-example">该工具暂未提供使用示例。</p>
      </section>
    </div>

    <div class="drawer-ft">
      <svg
        class="ic"
        viewBox="0 0 24 24"
        aria-hidden="true"
      ><circle cx="7.5" cy="15.5" r="4"/><path d="m10.5 12.5 9-9M17 4l3 3M14 7l2.5 2.5"/></svg>
      <span>
        调用本工具需 Agent API Key{{ tool.requiresWriteScope ? '（写权限工具需非只读 scope）' : '' }} ·
        <RouterLink to="/settings">前往系统配置申请</RouterLink>
      </span>
    </div>
  </aside>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import type { McpToolSummary } from '../../types';
import { mcpStageLabel } from '../../utils/format';
import { copyToClipboard } from '../../utils/clipboard';

type ToolParameter = { name: string; type: string; required: boolean; description: string };

const props = defineProps<{ tool: McpToolSummary }>();
const emit = defineEmits<{
  (e: 'close'): void;
  (e: 'copied', message: string): void;
}>();

const enabled = computed(() => props.tool.status !== 'DISABLED');
const copied = ref(false);

const parameters = computed<ToolParameter[]>(() => {
  const schema = props.tool.inputSchema as {
    properties?: Record<string, { type?: string | string[]; description?: string }>;
    required?: string[];
  } | null;
  const properties = schema?.properties ?? {};
  const required = new Set(schema?.required ?? []);
  const entries = Object.entries(properties);
  if (entries.length === 0) {
    return [{ name: '—', type: 'void', required: false, description: '无入参' }];
  }
  return entries.map(([name, property]) => ({
    name,
    type: Array.isArray(property.type) ? property.type.join(' | ') : String(property.type ?? 'any'),
    required: required.has(name),
    description: property.description ?? '',
  }));
});

async function copyExample() {
  if (await copyToClipboard(props.tool.usageExample)) {
    copied.value = true;
    emit('copied', '示例已复制到剪贴板');
    window.setTimeout(() => {
      copied.value = false;
    }, 1800);
  }
}
</script>

<style scoped>
.drawer {
  position: fixed;
  top: 0;
  right: 0;
  bottom: 0;
  width: 520px;
  max-width: 92vw;
  background: var(--surface);
  box-shadow: -12px 0 32px rgba(26, 35, 50, 0.14);
  z-index: 100;
  display: flex;
  flex-direction: column;
  animation: mcp-drawer-in 0.24s cubic-bezier(0.32, 0.72, 0.35, 1);
}

@keyframes mcp-drawer-in {
  from { transform: translateX(102%); }
  to { transform: translateX(0); }
}

.drawer .ic {
  width: 16px;
  height: 16px;
  flex: none;
  stroke: currentColor;
  fill: none;
  stroke-width: 1.7;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.drawer .ic.xs { width: 12px; height: 12px; }

.drawer-hd {
  padding: 18px 22px 14px;
  border-bottom: 1px solid var(--line);
  display: flex;
  gap: 12px;
  align-items: flex-start;
}

.drawer-hd .titles { min-width: 0; }

.drawer-hd .tool-name {
  font-family: var(--font-data);
  font-size: 16px;
  font-weight: 600;
  color: var(--ink);
  word-break: break-all;
}

.drawer-hd .tool-title { margin-top: 2px; font-size: 13px; font-weight: 600; color: var(--ink); }

.drawer-hd .badges { margin-top: 8px; display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }

.drawer-close {
  margin-left: auto;
  width: 30px;
  height: 30px;
  border-radius: var(--radius-sm);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--muted);
  transition: all 0.18s;
  flex: none;
  border: none;
  background: none;
  cursor: pointer;
}

.drawer-close:hover { background: var(--surface-soft); color: var(--ink); }

.drawer-bd {
  overflow-y: auto;
  padding: 20px 22px 28px;
  display: flex;
  flex-direction: column;
  gap: 22px;
}

.drawer .sec h3 {
  margin: 0 0 10px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 1px;
  color: var(--muted);
  text-transform: uppercase;
  display: flex;
  align-items: center;
  gap: 7px;
}

.drawer .sec h3 .ic { width: 14px; height: 14px; color: var(--primary-dark); }

.drawer .sec .desc { margin: 0; font-size: 13.5px; color: var(--ink); }

table.params {
  width: 100%;
  border-collapse: collapse;
  font-size: 12.5px;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  overflow: hidden;
}

.params th {
  background: var(--surface-soft);
  text-align: left;
  font-weight: 600;
  color: var(--muted);
  padding: 8px 12px;
  border-bottom: 1px solid var(--line);
  font-size: 12px;
}

.params td {
  padding: 8px 12px;
  border-bottom: 1px solid var(--line);
  vertical-align: top;
  color: var(--ink);
}

.params tr:last-child td { border-bottom: none; }

.params td.p-name { font-family: var(--font-data); font-size: 12px; white-space: nowrap; }

.params td.p-type { font-family: var(--font-data); font-size: 11.5px; color: var(--muted); white-space: nowrap; }

.params .req { color: var(--danger); font-weight: 700; }

.params .opt { color: var(--muted); }

.drawer .codebox { position: relative; }

.drawer .codebox pre {
  margin: 0;
  background: #0e1720;
  color: #d7e3ec;
  border-radius: var(--radius-sm);
  padding: 14px 16px;
  overflow: auto;
  font-family: var(--font-data);
  font-size: 12.5px;
  line-height: 1.7;
}

.drawer .copy-btn {
  position: absolute;
  top: 8px;
  right: 8px;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 5px 10px;
  border-radius: var(--radius-sm);
  background: rgba(255, 255, 255, 0.08);
  color: #c9d7e2;
  font-size: 12px;
  border: none;
  cursor: pointer;
  transition: background 0.18s;
}

.drawer .copy-btn:hover { background: rgba(255, 255, 255, 0.16); }

.drawer .copy-btn.done { color: #a7d8a0; }

.drawer .no-example { margin: 0; font-size: 12.5px; color: var(--muted); }

.drawer-ft {
  border-top: 1px solid var(--line);
  padding: 14px 24px;
  background: var(--surface-soft);
  font-size: 12.5px;
  color: var(--muted);
  display: flex;
  align-items: center;
  gap: 9px;
}

.drawer-ft .ic { color: var(--primary-dark); width: 15px; height: 15px; }

.drawer-ft a { font-weight: 600; color: var(--primary-dark); }

/* 徽标与状态图标（与页面同名约定，scoped 独立） */
.drawer .badge {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 2px 9px;
  border-radius: 11px;
  font-size: 11.5px;
  font-weight: 600;
  letter-spacing: 0.2px;
  white-space: nowrap;
}

.drawer .st-PLAN { background: var(--accent-soft); color: var(--primary-dark); }
.drawer .st-NAVIGATE { background: #e8effd; color: #2563eb; }
.drawer .st-DESIGN { background: #f1eafd; color: #7c3aed; }
.drawer .st-OBSERVE { background: #eef2f6; color: #475569; }
.drawer .st-DIAGNOSE { background: var(--warning-soft); color: var(--warn); }
.drawer .st-VERIFY { background: var(--ok-soft); color: var(--ok); }
.drawer .st-CAPTURE { background: #fdeaf3; color: #be185d; }

.drawer .b-write {
  background: var(--warning-soft);
  border: 1px solid var(--warning-border);
  color: var(--warn);
}

.drawer .b-read { background: var(--surface); border: 1px solid var(--line); color: var(--muted); }

.drawer .st-ic { display: inline-flex; align-items: center; justify-content: center; flex: none; }
.drawer .st-ic .ic { width: 15px; height: 15px; }
.drawer .st-ic.ok { color: var(--ok); }
.drawer .st-ic.off { color: #8aa0b0; }

@media (max-width: 720px) {
  .drawer { width: 100vw; max-width: 100vw; }
}

@media (prefers-reduced-motion: reduce) {
  .drawer { animation: none !important; }
}
</style>
```

- [ ] **Step 2: 页面挂载抽屉（`McpToolDirectoryPage.vue`）**

import 区补：

```ts
import McpToolDetailDrawer from './McpToolDetailDrawer.vue';
```

模板 `</footer>`（页脚）之后、toast 之前插入：

```html
    <div class="backdrop" :class="{ show: selectedTool }" @click="closeDrawer"></div>
    <McpToolDetailDrawer
      v-if="selectedTool"
      :tool="selectedTool"
      @close="closeDrawer"
      @copied="showToast"
    />
```

样式追加（`/* ===== Toast ===== */` 段之前）：

```css
/* ===== 详情抽屉背板 ===== */
.mcp-directory .backdrop {
  position: fixed;
  inset: 0;
  background: rgba(18, 26, 34, 0.44);
  z-index: 90;
  opacity: 0;
  pointer-events: none;
  transition: opacity 0.2s;
}

.mcp-directory .backdrop.show { opacity: 1; pointer-events: auto; }
```

- [ ] **Step 3: 构建验证**

```bash
cd /Users/wangk/Documents/Git/performance-test-platform/frontend && npm run build
```

Expected: 零错误通过。

- [ ] **Step 4: 手测清单（dev server）**

点卡片开抽屉：名称/标题/stage+英文徽标/写权限/状态图标重复展示；功能说明；参数表（`list_projects` 显示 `includeArchived / boolean / 可选 / include archived projects`；无参工具显示 `— / void`）；使用示例区（存量 8 工具为空 → 显示「该工具暂未提供使用示例。」）；底部 API Key 提示 + 申请链接可达 `/settings`；ESC / 点背板 / 点关闭均关抽屉。

- [ ] **Step 5: Commit**

```bash
cd /Users/wangk/Documents/Git/performance-test-platform
git add frontend/src/components/mcp/McpToolDetailDrawer.vue \
        frontend/src/components/mcp/McpToolDirectoryPage.vue
git commit -m "feat：P0-2② 工具详情抽屉——inputSchema 渲染参数表（名称/类型/必填/说明）、使用示例代码块与复制、徽标自包含展示"
```

---

### Task 5: 全量回归 + 端到端冒烟 + 实现记录

**Files:**
- Modify: `docs/implementation-log.md`

**Interfaces:**
- Consumes: Task 1-4 全部交付物。
- Produces: 全绿测试证据 + 实现记录；无新代码接口。

- [ ] **Step 1: 后端全量测试**

```bash
cd /Users/wangk/Documents/Git/performance-test-platform && JAVA_HOME=/Users/wangk/Documents/config/jdk-17.0.17+10/Contents/Home ./gradlew :backend:test
```

Expected: BUILD SUCCESSFUL，全量用例通过（含新增 `McpDirectoryControllerTest` 与既有 `McpServerApiTest` 回归）。

- [ ] **Step 2: 前端构建**

```bash
cd /Users/wangk/Documents/Git/performance-test-platform/frontend && npm run build
```

Expected: `vue-tsc --noEmit` 零错误 + vite build 成功。

- [ ] **Step 3: 端到端冒烟（真实服务）**

```bash
cd /Users/wangk/Documents/Git/performance-test-platform && JAVA_HOME=/Users/wangk/Documents/config/jdk-17.0.17+10/Contents/Home ./gradlew :backend:bootRun
```

（后台起服务，等待启动完成后：）

```bash
TOKEN=$(curl -s -X POST http://127.0.0.1:8080/api/auth/login -H 'Content-Type: application/json' -d '{"username":"admin","password":"admin123"}' | python3 -c 'import json,sys; print(json.load(sys.stdin)["token"])')
curl -s http://127.0.0.1:8080/api/mcp/tools -H "Authorization: Bearer $TOKEN" | python3 -m json.tool | head -30
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8080/api/mcp/tools
```

Expected: 登录拿到 token；带 token 的 GET 返回 `server.name=performance-test-platform`、`toolCount=8`、`stages` 七阶段序列、`tools` 按 stage 排序且 `status` 全 `ENABLED`；不带 token 返回 `401`。验证完停掉服务。

- [ ] **Step 4: 更新 `docs/implementation-log.md`**

文件末尾追加（对齐既有条目风格）：

```markdown
## 2026-09-04（P0-2 ② MCP 工具目录页）

已完成：

1. 后端 `GET /api/mcp/tools`（`api/McpDirectoryController`）：直接映射内存 `McpToolRegistry` 单一事实源，固定规范 stage 序列（PLAN→NAVIGATE→DESIGN→OBSERVE→DIAGNOSE→VERIFY→CAPTURE）排序，登录可读；`McpTool` 契约补 `default usageExample()`（存量 8 工具零改动）。
2. 前端 `/mcp-tools` 顶级路由 + 全局导航「MCP 工具」入口：接入指引横幅（Claude Code / DSH 配置片段一键复制、API Key 申请入口），阶段筛选 tabs + 本地搜索 + 单一平铺卡片网格（两态状态图标、写权限徽标），接口文档式详情抽屉（inputSchema 参数表 + 使用示例），页脚收尾；对照 `mcp-directory-prototype.html` 视觉基准实现。
3. ① 计划工具 ×5 与 ③ perf-plan skill 依赖 P0-1（未开发），按 spec §8 保持延后跟踪。

验证：

1. `McpDirectoryControllerTest`（401 / 与 registry 严格一致 / 序列排序 / 字段口径）+ `McpServerApiTest` 回归全绿；`gradle :backend:test` 全量通过。
2. `npm run build`（vue-tsc + vite）零错误；bootRun + curl 端到端冒烟（登录读取目录、匿名 401）通过。
```

- [ ] **Step 5: Commit**

```bash
cd /Users/wangk/Documents/Git/performance-test-platform
git add docs/implementation-log.md
git commit -m "docs：P0-2② 实现记录——工具目录页交付（端点+页面），①/③ 依赖 P0-1 延后"
```

---

## Self-Review（spec 覆盖对照，仅本轮范围）

| Spec 条目 | 任务 |
|-----------|------|
| §4.1 REST 端点（响应结构/固定序列/排序/可见性口径） | Task 1 |
| §4.2 `McpTool.usageExample()` default | Task 1 |
| §4.3 状态两态 `ENABLED`（v1 全 ENABLED、纯图标前端呈现） | Task 1（后端常量）+ Task 3/4（图标） |
| §4.4 stage 新增 PLAN（序列常量；`toolSpecification` 拼接无需改动） | Task 1 |
| §5.1 路由/导航入口/流式铺满（.content 已 100%） | Task 2 |
| §5.2 页头横幅（endpoint/认证说明/CC+DSH 片段/申请入口） | Task 2 |
| §5.3 卡片/详情/筛选/搜索/页脚/纯只读 | Task 3 + Task 4 |
| §5.4 工程落点（api 模块/组件/类型/视觉基准） | Task 2/3/4 |
| §10 后端②测试（一致性/401/默认空串回归） | Task 1 + Task 5 |
| §10 前端②手测清单 | Task 2/3/4 手测步骤 + Task 5 冒烟 |
| §6/§7/§8（①③，依赖 P0-1） | 明确不做，Scope 声明 |

类型一致性：`McpToolSummary.status` ↔ 后端 `"status"` 字段（`'ENABLED' | 'DISABLED'`）；`mcpStageLabel` 在 format.ts 定义、页面与抽屉共同消费；`copyToClipboard` 在 clipboard.ts 定义、页面与抽屉共同消费；`selectedTool/openTool/closeDrawer/showToast` 由 Task 3 声明、Task 4 接线——已核对无命名漂移。
