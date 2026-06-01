<template>
  <section v-if="!currentUser" class="auth-screen">
    <div class="auth-atmosphere">
      <div class="auth-grid"></div>
      <div class="auth-signal signal-a"></div>
      <div class="auth-signal signal-b"></div>
    </div>

    <div class="auth-brand">
      <span class="brand-mark">PT</span>
      <div>
        <strong>性能测试平台</strong>
        <small>Performance Intelligence Console</small>
      </div>
    </div>

    <div class="auth-stage">
      <div class="auth-copy">
        <span class="auth-kicker">Load, Observe, Decide</span>
        <h1>把压测现场变成可控的指挥舱</h1>
        <p>统一管理项目、脚本解析、执行编排、监控指标和报告沉淀。当前原型使用 Mock 数据，便于先确认产品结构和操作路径。</p>
        <div class="auth-metrics">
          <div>
            <span>资产归属</span>
            <strong>Project First</strong>
          </div>
          <div>
            <span>脚本解析</span>
            <strong>JMX Native</strong>
          </div>
          <div>
            <span>监控闭环</span>
            <strong>Live Signals</strong>
          </div>
        </div>
      </div>

      <div class="auth-card">
        <div class="auth-card-header">
          <span class="auth-kicker">Secure Access</span>
          <h2>登录控制台</h2>
          <p>使用演示账号进入平台首页。</p>
        </div>
        <el-form class="auth-form" label-position="top" @submit.prevent>
          <el-form-item label="账号">
            <el-input v-model="loginForm.username" autocomplete="username" size="large" />
          </el-form-item>
          <el-form-item label="密码">
            <el-input v-model="loginForm.password" type="password" autocomplete="current-password" show-password size="large" />
          </el-form-item>
          <el-button type="primary" size="large" :loading="loginLoading" @click="login">进入平台</el-button>
        </el-form>
        <div class="auth-demo">
          <span>演示账号</span>
          <strong>admin / admin123</strong>
        </div>
      </div>
    </div>
  </section>

  <el-container v-else class="app-shell">
    <el-aside width="260px" class="sidebar">
      <div class="brand">
        <span class="brand-mark">PT</span>
        <div>
          <strong>性能测试平台</strong>
          <small>Project Workspace</small>
        </div>
      </div>

      <template v-if="currentUser && !currentProject">
        <div class="main-nav">
          <button class="side-entry" :class="{ active: activeMainNav === 'home' }" type="button" @click="selectMainNav('home')">
            <span class="nav-index">01</span>
            <span>首页</span>
          </button>
          <button class="side-entry" :class="{ active: activeMainNav === 'projects' }" type="button" @click="selectMainNav('projects')">
            <span class="nav-index">02</span>
            <span>项目管理</span>
          </button>
          <button class="side-entry" :class="{ active: activeMainNav === 'settings' }" type="button" @click="selectMainNav('settings')">
            <span class="nav-index">03</span>
            <span>系统配置</span>
          </button>
        </div>

        <div v-if="activeMainNav === 'projects'" class="side-section">
          <div class="side-title">项目列表</div>
          <button
            v-for="project in activeProjects"
            :key="project.id"
            class="project-link"
            :class="{ active: selectedProject?.id === project.id }"
            type="button"
            @click="selectProject(project)"
            @dblclick="enterProject(project)"
          >
            <strong>{{ project.name }}</strong>
            <span>{{ project.code }}</span>
          </button>
        </div>
      </template>

      <div v-else-if="currentUser && currentProject" class="side-section project-detail-nav">
        <button class="side-back" type="button" @click="backToProjects">返回项目列表</button>
        <div class="side-title">项目列表</div>
        <div class="current-project-card">
          <strong>{{ currentProject.name }}</strong>
          <span>{{ currentProject.code }}</span>
        </div>

        <div class="side-title module-title">项目详情导航</div>
        <button
          v-for="option in projectTabOptions"
          :key="option.value"
          class="module-link"
          :class="{ active: activeProjectTab === option.value }"
          type="button"
          @click="activeProjectTab = option.value"
        >
          <span>{{ moduleIndex(option.value) }}</span>
          <strong>{{ option.label }}</strong>
        </button>
      </div>

      <div class="sidebar-note">
        <span>Mock 数据模式</span>
        <strong>先验证项目内工作流</strong>
      </div>
    </el-aside>

    <el-container>
      <el-header class="topbar">
        <div>
          <div class="eyebrow">Phase 2 Prototype</div>
          <div class="page-title">{{ pageTitle }}</div>
        </div>
        <div class="topbar-actions">
          <el-tag type="warning" effect="light">Mock API</el-tag>
          <el-tag v-if="currentProject" type="success" effect="light">{{ currentProject.code }}</el-tag>
          <el-tag v-if="currentUser" type="success" effect="light">{{ currentUser.displayName }}</el-tag>
          <el-button v-if="currentUser" @click="logout">退出</el-button>
        </div>
      </el-header>

      <el-main class="content">
        <template v-if="activeMainNav === 'home'">
          <section class="home-hero">
            <div>
              <span class="eyebrow">Performance Control Center</span>
              <h1>性能测试平台首页</h1>
              <p>把项目资产、脚本解析、执行状态、监控告警和报告沉淀放在一个运营视角下观察。这里先按同类测试平台的工作台形态做 Mock 首页。</p>
            </div>
            <div class="home-hero-card">
              <span>今日重点</span>
              <strong>信贷核心压测复测窗口</strong>
              <small>2 个脚本待配置执行参数，3 个监控目标已就绪</small>
            </div>
          </section>

          <section class="metrics-grid">
            <div class="metric">
              <span>活跃项目</span>
              <strong>{{ activeProjectCount }}</strong>
            </div>
            <div class="metric">
              <span>脚本资产</span>
              <strong>{{ scriptAssetTotal }}</strong>
            </div>
            <div class="metric">
              <span>待执行任务</span>
              <strong>{{ pendingTaskCount }}</strong>
            </div>
            <div class="metric">
              <span>监控目标</span>
              <strong>{{ monitorTargetTotal }}</strong>
            </div>
          </section>

          <section class="home-grid">
            <div class="panel">
              <div class="panel-header">
                <div>
                  <h2>工作流入口</h2>
                  <p>首页不直接替代业务模块，只放高频入口和当前风险状态。</p>
                </div>
              </div>
              <div class="quick-actions">
                <button type="button" @click="selectMainNav('projects')">
                  <span>01</span>
                  <strong>进入项目管理</strong>
                  <small>维护项目、成员、脚本、执行和报告归属</small>
                </button>
                <button type="button" @click="selectMainNav('settings')">
                  <span>02</span>
                  <strong>系统配置</strong>
                  <small>管理用户、角色、权限和平台访问边界</small>
                </button>
                <button type="button" @click="openScript(activeProjectScripts[0])" :disabled="!activeProjectScripts[0]">
                  <span>03</span>
                  <strong>继续脚本解析</strong>
                  <small>{{ activeProjectScripts[0]?.name ?? '暂无可继续脚本' }}</small>
                </button>
              </div>
            </div>

            <div class="panel">
              <div class="panel-header">
                <div>
                  <h2>近期项目</h2>
                  <p>按更新时间展示，点击进入项目详情页。</p>
                </div>
              </div>
              <div class="mini-list">
                <button v-for="project in recentProjects" :key="project.id" type="button" @click="enterProject(project)">
                  <strong>{{ project.name }}</strong>
                  <span>{{ project.code }} · {{ scriptsByProject(project.id).length }} 个脚本 · {{ project.ownerUsername }}</span>
                </button>
              </div>
            </div>

            <div class="panel home-wide-panel">
              <div class="panel-header">
                <div>
                  <h2>平台运行概览</h2>
                  <p>用一屏展示资产准备、执行计划、监控覆盖和报告闭环。</p>
                </div>
              </div>
              <div class="operation-board">
                <div>
                  <span>资产准备</span>
                  <strong>{{ scriptAssetTotal }} 个脚本已解析</strong>
                  <small>线程组、API、监控配置已抽取为平台字段</small>
                </div>
                <div>
                  <span>执行计划</span>
                  <strong>{{ pendingTaskCount }} 个待执行</strong>
                  <small>执行配置将从脚本默认参数复制快照</small>
                </div>
                <div>
                  <span>监控覆盖</span>
                  <strong>{{ monitorTargetTotal }} 个目标</strong>
                  <small>应用、JVM、数据库、中间件统一归属项目</small>
                </div>
                <div>
                  <span>报告沉淀</span>
                  <strong>{{ reportMocks.length }} 份近期报告</strong>
                  <small>后续按项目、脚本和执行批次追溯</small>
                </div>
              </div>
            </div>
          </section>
        </template>

        <template v-else-if="activeMainNav === 'settings'">
          <section class="hero-band">
            <div>
              <span class="eyebrow">System Configuration</span>
              <h1>系统配置</h1>
              <p>平台级配置不属于具体项目，先覆盖用户、角色、权限三类基础能力，后续可扩展组织、环境、审计策略。</p>
            </div>
          </section>

          <section class="settings-layout">
            <aside class="panel settings-nav">
              <button
                v-for="option in configTabOptions"
                :key="option.value"
                class="module-link"
                :class="{ active: activeConfigTab === option.value }"
                type="button"
                @click="activeConfigTab = option.value"
              >
                <span>{{ configIndex(option.value) }}</span>
                <strong>{{ option.label }}</strong>
              </button>
            </aside>

            <div class="panel settings-panel">
              <template v-if="activeConfigTab === 'users'">
                <div class="panel-header">
                  <div>
                    <h2>用户管理</h2>
                    <p>维护平台登录账号、系统角色和账号状态。</p>
                  </div>
                  <el-button type="primary">新建用户</el-button>
                </div>
                <el-table :data="systemUsers" border stripe>
                  <el-table-column prop="username" label="账号" />
                  <el-table-column prop="displayName" label="姓名" />
                  <el-table-column prop="role" label="系统角色" />
                  <el-table-column prop="status" label="状态">
                    <template #default="{ row }">
                      <el-tag :type="row.status === '启用' ? 'success' : 'info'">{{ row.status }}</el-tag>
                    </template>
                  </el-table-column>
                  <el-table-column prop="lastLogin" label="最近登录" />
                </el-table>
              </template>

              <template v-else-if="activeConfigTab === 'roles'">
                <div class="panel-header">
                  <div>
                    <h2>角色管理</h2>
                    <p>角色聚合权限，项目内负责人/成员权限仍由项目成员关系控制。</p>
                  </div>
                  <el-button type="primary">新建角色</el-button>
                </div>
                <div class="role-grid">
                  <div v-for="role in systemRoles" :key="role.name">
                    <strong>{{ role.name }}</strong>
                    <span>{{ role.description }}</span>
                    <small>{{ role.permissions.join(' / ') }}</small>
                  </div>
                </div>
              </template>

              <template v-else>
                <div class="panel-header">
                  <div>
                    <h2>权限配置</h2>
                    <p>按平台模块定义权限点，供系统角色授权使用。</p>
                  </div>
                </div>
                <div class="permission-list">
                  <div v-for="permission in systemPermissions" :key="permission.code">
                    <span>{{ permission.module }}</span>
                    <strong>{{ permission.name }}</strong>
                    <small>{{ permission.code }}</small>
                  </div>
                </div>
              </template>
            </div>
          </section>
        </template>

        <template v-else-if="activeMainNav === 'projects' && !currentProject">
          <section class="hero-band">
            <div>
              <span class="eyebrow">Module 02</span>
              <h1>项目是所有压测资产的入口</h1>
              <p>脚本、测试执行、监控配置、报告都归属于项目。先选择或创建项目，进入后再维护项目内资产。</p>
            </div>
            <div class="hero-actions">
              <el-button @click="resetWorkspace">重置 Mock</el-button>
              <el-button type="primary" size="large" @click="openCreateDialog">新建项目</el-button>
            </div>
          </section>

          <section class="metrics-grid">
            <div class="metric">
              <span>活跃项目</span>
              <strong>{{ activeProjectCount }}</strong>
            </div>
            <div class="metric">
              <span>归档项目</span>
              <strong>{{ archivedProjectCount }}</strong>
            </div>
            <div class="metric">
              <span>脚本资产</span>
              <strong>{{ scriptAssetTotal }}</strong>
            </div>
            <div class="metric">
              <span>项目成员</span>
              <strong>{{ memberTotal }}</strong>
            </div>
          </section>

          <section class="project-home-grid">
            <div class="panel">
              <div class="panel-header">
                <div>
                  <h2>项目列表</h2>
                  <p>项目只做归档，不做物理删除；已归档项目保留历史资产查看。</p>
                </div>
              </div>

              <div class="filters">
                <el-input v-model="projectKeyword" clearable placeholder="搜索项目名称、编码、负责人" />
                <el-segmented v-model="projectStatusFilter" :options="projectStatusOptions" />
              </div>

              <el-table :data="filteredProjects" border stripe highlight-current-row @row-click="selectProject">
                <el-table-column prop="code" label="项目编码" min-width="150" />
                <el-table-column prop="name" label="项目名称" min-width="220" />
                <el-table-column prop="ownerUsername" label="负责人" width="120" />
                <el-table-column label="资产" width="150">
                  <template #default="{ row }">
                    {{ scriptsByProject(row.id).length }} 个脚本
                  </template>
                </el-table-column>
                <el-table-column prop="status" label="状态" width="110">
                  <template #default="{ row }">
                    <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">{{ projectStatusText(row.status) }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="updatedAt" label="更新时间" width="168">
                  <template #default="{ row }">{{ formatDate(row.updatedAt) }}</template>
                </el-table-column>
                <el-table-column label="操作" width="280" fixed="right">
                  <template #default="{ row }">
                    <el-button link type="primary" @click.stop="enterProject(row)">进入项目</el-button>
                    <el-button link type="primary" @click.stop="openEditDialog(row)">编辑</el-button>
                    <el-button link type="primary" @click.stop="openMemberDialog(row)">成员</el-button>
                    <el-button v-if="row.status === 'ACTIVE'" link type="warning" @click.stop="archiveProject(row)">归档</el-button>
                    <el-button v-else link type="success" @click.stop="restoreProject(row)">恢复</el-button>
                  </template>
                </el-table-column>
                <template #empty>
                  <div class="empty-state">没有匹配项目，调整筛选条件或新建项目。</div>
                </template>
              </el-table>
            </div>

            <aside class="panel detail-panel">
              <template v-if="selectedProject">
                <div class="detail-heading">
                  <div>
                    <span class="eyebrow">{{ selectedProject.code }}</span>
                    <h2>{{ selectedProject.name }}</h2>
                  </div>
                  <el-tag :type="selectedProject.status === 'ACTIVE' ? 'success' : 'info'">{{ projectStatusText(selectedProject.status) }}</el-tag>
                </div>
                <p class="detail-description">{{ selectedProject.description || '暂未填写项目说明。' }}</p>
                <div class="info-list">
                  <div>
                    <span>负责人</span>
                    <strong>{{ selectedProject.ownerUsername }}</strong>
                  </div>
                  <div>
                    <span>成员数</span>
                    <strong>{{ membersByProject(selectedProject.id).length }}</strong>
                  </div>
                  <div>
                    <span>脚本资产</span>
                    <strong>{{ scriptsByProject(selectedProject.id).length }}</strong>
                  </div>
                  <div>
                    <span>创建时间</span>
                    <strong>{{ formatDate(selectedProject.createdAt) }}</strong>
                  </div>
                </div>
                <div class="detail-actions">
                  <el-button type="primary" @click="enterProject(selectedProject)">进入项目工作区</el-button>
                  <el-button @click="openMemberDialog(selectedProject)">维护成员</el-button>
                </div>
              </template>
            </aside>
          </section>
        </template>

        <template v-else-if="currentProject">
          <section class="project-hero">
            <div>
              <button class="breadcrumb-button" type="button" @click="backToProjects">项目列表 /</button>
              <span class="eyebrow">{{ currentProject.code }}</span>
              <h1>{{ currentProject.name }}</h1>
              <p>{{ currentProject.description }}</p>
            </div>
            <div class="project-hero-actions">
              <el-tag :type="currentProject.status === 'ACTIVE' ? 'success' : 'info'" effect="light">
                {{ projectStatusText(currentProject.status) }}
              </el-tag>
              <el-button @click="openEditDialog(currentProject)">编辑项目</el-button>
              <el-button @click="openMemberDialog(currentProject)">成员权限</el-button>
            </div>
          </section>

          <section v-if="activeProjectTab === 'overview'" class="project-dashboard">
            <div class="metrics-grid compact">
              <div class="metric">
                <span>脚本资产</span>
                <strong>{{ currentProjectScripts.length }}</strong>
              </div>
              <div class="metric">
                <span>待执行场景</span>
                <strong>{{ currentProjectScripts.length + 2 }}</strong>
              </div>
              <div class="metric">
                <span>监控目标</span>
                <strong>{{ currentProjectMonitorCount }}</strong>
              </div>
              <div class="metric">
                <span>最新报告</span>
                <strong>{{ reportMocks.length }}</strong>
              </div>
            </div>

            <div class="dashboard-grid">
              <div class="panel">
                <div class="panel-header">
                  <div>
                    <h2>项目内资产</h2>
                    <p>所有资产都在项目内维护，后续接口也应以项目 ID 作为前置上下文。</p>
                  </div>
                </div>
                <div class="asset-flow">
                  <div>
                    <span>01</span>
                    <strong>解析脚本</strong>
                    <small>JMX 转平台脚本资产</small>
                  </div>
                  <div>
                    <span>02</span>
                    <strong>配置场景</strong>
                    <small>从脚本默认参数生成执行配置</small>
                  </div>
                  <div>
                    <span>03</span>
                    <strong>绑定监控</strong>
                    <small>应用、主机、中间件指标</small>
                  </div>
                  <div>
                    <span>04</span>
                    <strong>沉淀报告</strong>
                    <small>报告只归属于当前项目</small>
                  </div>
                </div>
              </div>

              <div class="panel">
                <div class="panel-header">
                  <div>
                    <h2>最近脚本</h2>
                    <p>进入脚本页查看线程组、接口和监控解析结果。</p>
                  </div>
                  <el-button type="primary" @click="activeProjectTab = 'scripts'">管理脚本</el-button>
                </div>
                <div class="mini-list">
                  <button v-for="script in currentProjectScripts" :key="script.id" type="button" @click="openScript(script)">
                    <strong>{{ script.name }}</strong>
                    <span>{{ script.threadGroups.length }} 个线程组 · {{ script.apis.length }} 个接口 · v{{ script.latestVersion }}</span>
                  </button>
                </div>
              </div>
            </div>
          </section>

          <section v-else-if="activeProjectTab === 'scripts'" class="script-workspace">
            <div class="panel import-panel">
              <div class="panel-header">
                <div>
                  <span class="eyebrow">Module 03</span>
                  <h2>JMeter 脚本解析导入</h2>
                  <p>上传 JMX 后解析为平台脚本资产，提取线程组、API、变量、监控配置和默认执行参数。</p>
                </div>
              </div>
              <label class="drop-zone" :class="{ 'has-file': scriptFile }">
                <input type="file" accept=".jmx" @change="handleScriptFileChange" />
                <span>{{ scriptFile ? scriptFile.name : '选择 .jmx 文件解析导入' }}</span>
                <small>{{ scriptFile ? formatFileSize(scriptFile.size) : '不会展示上传记录，会直接生成或更新平台脚本' }}</small>
              </label>
              <el-form class="upload-form" label-position="top" @submit.prevent>
                <el-form-item label="平台脚本名称">
                  <el-input v-model="scriptForm.name" placeholder="默认使用 JMX 文件名" />
                </el-form-item>
                <el-form-item label="导入备注">
                  <el-input v-model="scriptForm.remark" type="textarea" :rows="3" placeholder="例如 调整登录链路线程组" />
                </el-form-item>
                <el-button type="primary" :loading="scriptUploading" :disabled="!scriptFile" @click="importScriptAsset">
                  解析并导入脚本
                </el-button>
              </el-form>
            </div>

            <div class="panel script-assets-panel">
              <div class="panel-header">
                <div>
                  <h2>平台脚本资产</h2>
                  <p>{{ currentProject.name }} 下可用于测试执行的脚本，不展示单独上传列表。</p>
                </div>
                <el-input v-model="scriptKeyword" class="compact-search" clearable placeholder="搜索脚本、接口、变量" />
              </div>

              <div class="script-asset-list">
                <button
                  v-for="script in filteredScriptAssets"
                  :key="script.id"
                  class="script-asset-row"
                  :class="{ active: selectedScriptAsset?.id === script.id }"
                  type="button"
                  @click="selectScript(script)"
                >
                  <span class="asset-status" :class="script.parseStatus.toLowerCase()">{{ parseStatusText(script.parseStatus) }}</span>
                  <div>
                    <strong>{{ script.name }}</strong>
                    <small>{{ script.threadGroups.length }} 线程组 · {{ script.apis.length }} API · {{ script.monitors.length }} 监控 · v{{ script.latestVersion }}</small>
                  </div>
                  <span>{{ formatDate(script.updatedAt) }}</span>
                </button>
              </div>
            </div>

            <aside class="panel parsed-detail-panel">
              <template v-if="selectedScriptAsset">
                <div class="detail-heading">
                  <div>
                    <span class="eyebrow">Parsed Script</span>
                    <h2>{{ selectedScriptAsset.name }}</h2>
                  </div>
                  <el-button type="primary" @click="openParamDrawer(selectedScriptAsset)">默认参数</el-button>
                </div>
                <p class="detail-description">
                  来源 {{ selectedScriptAsset.sourceFile }}，当前 v{{ selectedScriptAsset.latestVersion }}，{{ selectedScriptAsset.remark || '暂无备注' }}。
                </p>

                <div class="parsed-section">
                  <h3>线程组</h3>
                  <div class="parsed-table">
                    <div v-for="group in selectedScriptAsset.threadGroups" :key="group.name">
                      <strong>{{ group.name }}</strong>
                      <span>{{ group.threads }} 线程 / Ramp-Up {{ group.rampUp }}s / 循环 {{ group.loops }} / {{ group.duration }}s</span>
                    </div>
                  </div>
                </div>

                <div class="parsed-section">
                  <h3>API 配置</h3>
                  <div class="api-list">
                    <span v-for="api in selectedScriptAsset.apis" :key="`${api.method}-${api.path}`">
                      {{ api.method }} {{ api.path }}
                    </span>
                  </div>
                </div>

                <div class="parsed-section">
                  <h3>监控配置</h3>
                  <div class="api-list">
                    <span v-for="monitor in selectedScriptAsset.monitors" :key="monitor.target">
                      {{ monitor.target }} · {{ monitor.metrics.join('/') }}
                    </span>
                  </div>
                </div>

                <div class="parsed-section">
                  <h3>变量与默认参数</h3>
                  <div class="param-chips">
                    <span v-for="variable in selectedScriptAsset.variables" :key="variable.key">{{ variable.key }}={{ variable.value }}</span>
                    <span v-for="param in selectedScriptAsset.params" :key="param.key">{{ param.label }}：{{ param.value }}</span>
                  </div>
                </div>

                <div class="parsed-section">
                  <h3>版本记录</h3>
                  <div class="version-timeline">
                    <div v-for="version in selectedScriptAsset.versions" :key="version.versionNo">
                      <strong>v{{ version.versionNo }}</strong>
                      <span>{{ version.fileName }} · {{ formatDate(version.importedAt) }}</span>
                    </div>
                  </div>
                </div>
              </template>
              <div v-else class="empty-detail">
                <h2>选择脚本资产</h2>
                <p>右侧展示解析后的线程组、接口、监控和参数，而不是上传文件列表。</p>
              </div>
            </aside>
          </section>

          <section v-else-if="activeProjectTab === 'tasks'" class="placeholder-grid">
            <div class="panel">
              <h2>测试执行</h2>
              <p class="detail-description">这里后续从当前项目脚本资产复制默认参数，生成本次执行配置快照。当前为 Mock 交互占位。</p>
              <div class="mock-kanban">
                <div v-for="script in currentProjectScripts" :key="script.id">
                  <strong>{{ script.name }}</strong>
                  <span>待创建执行任务 · v{{ script.latestVersion }}</span>
                </div>
              </div>
            </div>
          </section>

          <section v-else-if="activeProjectTab === 'monitoring'" class="placeholder-grid">
            <div class="panel">
              <h2>监控配置</h2>
              <p class="detail-description">监控目标从脚本解析结果和项目环境中汇总，执行任务可选择绑定。</p>
              <div class="monitor-grid">
                <div v-for="monitor in currentProjectMonitors" :key="monitor.target">
                  <strong>{{ monitor.target }}</strong>
                  <span>{{ monitor.metrics.join(' / ') }}</span>
                </div>
              </div>
            </div>
          </section>

          <section v-else-if="activeProjectTab === 'reports'" class="placeholder-grid">
            <div class="panel">
              <h2>报告管理</h2>
              <p class="detail-description">报告属于当前项目，后续展示执行结论、趋势和瓶颈定位。</p>
              <div class="report-list">
                <div v-for="report in reportMocks" :key="report.name">
                  <strong>{{ report.name }}</strong>
                  <span>{{ report.time }} · {{ report.result }}</span>
                </div>
              </div>
            </div>
          </section>

          <section v-else class="placeholder-grid">
            <div class="panel">
              <h2>成员权限</h2>
              <p class="detail-description">成员关系仍是项目级权限前置校验，脚本、执行、报告不重复维护成员。</p>
              <el-table :data="membersByProject(currentProject.id)" border stripe>
                <el-table-column prop="username" label="账号" />
                <el-table-column prop="displayName" label="姓名" />
                <el-table-column prop="role" label="项目角色">
                  <template #default="{ row }">{{ projectRoleText(row.role) }}</template>
                </el-table-column>
              </el-table>
            </div>
          </section>
        </template>
      </el-main>
    </el-container>
  </el-container>

  <el-dialog v-model="projectDialogVisible" :title="editingProject ? '编辑项目' : '新建项目'" width="560px">
    <el-form label-position="top" @submit.prevent>
      <el-form-item label="项目编码">
        <el-input v-model.trim="projectForm.code" :disabled="Boolean(editingProject)" placeholder="例如 loan-core" />
      </el-form-item>
      <el-form-item label="项目名称">
        <el-input v-model.trim="projectForm.name" placeholder="例如 信贷核心压测" />
      </el-form-item>
      <el-form-item label="项目负责人">
        <el-input v-model.trim="projectForm.ownerUsername" placeholder="例如 tester" />
      </el-form-item>
      <el-form-item label="项目说明">
        <el-input v-model="projectForm.description" type="textarea" :rows="3" placeholder="填写主要压测范围、环境或边界" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="projectDialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="savingProject" @click="saveProject">保存</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="memberDialogVisible" title="项目成员" width="720px">
    <div v-if="memberProject" class="member-dialog">
      <div class="member-context">
        <div>
          <strong>{{ memberProject.name }}</strong>
          <span>{{ memberProject.code }}</span>
        </div>
        <el-tag :type="memberProject.status === 'ACTIVE' ? 'success' : 'info'">{{ projectStatusText(memberProject.status) }}</el-tag>
      </div>

      <el-table :data="membersByProject(memberProject.id)" border stripe>
        <el-table-column prop="username" label="账号" />
        <el-table-column prop="displayName" label="姓名" />
        <el-table-column prop="role" label="项目角色" width="150">
          <template #default="{ row }">
            <el-tag :type="row.role === 'OWNER' ? 'success' : 'info'">{{ projectRoleText(row.role) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="108">
          <template #default="{ row }">
            <el-button link type="danger" :disabled="row.role === 'OWNER'" @click="removeMember(memberProject.id, row.username)">移除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-form class="member-form" inline @submit.prevent>
        <el-form-item label="成员账号">
          <el-input v-model.trim="memberForm.username" :disabled="memberProject.status === 'ARCHIVED'" placeholder="例如 tester" />
        </el-form-item>
        <el-form-item label="姓名">
          <el-input v-model.trim="memberForm.displayName" :disabled="memberProject.status === 'ARCHIVED'" placeholder="例如 测试同学" />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="memberForm.role" :disabled="memberProject.status === 'ARCHIVED'" class="role-select">
            <el-option label="项目成员" value="MEMBER" />
            <el-option label="项目负责人" value="OWNER" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :disabled="memberProject.status === 'ARCHIVED'" @click="addMember">添加成员</el-button>
        </el-form-item>
      </el-form>
      <p v-if="memberProject.status === 'ARCHIVED'" class="form-hint">已归档项目不允许新增成员，先恢复项目后再维护。</p>
    </div>
  </el-dialog>

  <el-drawer v-model="paramDrawerVisible" title="脚本默认执行参数" size="460px">
    <div v-if="paramScriptAsset" class="param-drawer">
      <div class="drawer-title">
        <span class="eyebrow">{{ currentProject?.code }}</span>
        <h2>{{ paramScriptAsset.name }} v{{ paramScriptAsset.latestVersion }}</h2>
      </div>

      <el-form label-position="top" @submit.prevent>
        <el-form-item label="线程数">
          <el-input-number v-model="paramForm.threads" :min="0" :step="10" controls-position="right" />
        </el-form-item>
        <el-form-item label="循环次数">
          <el-input-number v-model="paramForm.loops" :min="0" controls-position="right" />
        </el-form-item>
        <el-form-item label="持续时间（秒）">
          <el-input-number v-model="paramForm.duration" :min="0" :step="60" controls-position="right" />
        </el-form-item>
        <el-form-item label="Ramp-Up（秒）">
          <el-input-number v-model="paramForm.rampUp" :min="0" :step="10" controls-position="right" />
        </el-form-item>
        <el-form-item label="目标环境">
          <el-select v-model="paramForm.environment" placeholder="选择环境">
            <el-option label="SIT" value="SIT" />
            <el-option label="UAT" value="UAT" />
            <el-option label="PRE" value="PRE" />
            <el-option label="PROD" value="PROD" />
          </el-select>
        </el-form-item>
        <el-form-item label="JMeter 属性扩展项">
          <el-input v-model="paramForm.extraProperties" type="textarea" :rows="5" placeholder="host=127.0.0.1&#10;protocol=https" />
        </el-form-item>
      </el-form>

      <div class="drawer-actions">
        <el-button @click="paramDrawerVisible = false">取消</el-button>
        <el-button type="primary" @click="saveParams">保存参数</el-button>
      </div>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';

type User = {
  username: string;
  displayName: string;
  roles: string[];
};

type ProjectStatus = 'ACTIVE' | 'ARCHIVED';
type ProjectRole = 'OWNER' | 'MEMBER';
type ParseStatus = 'PARSED' | 'PARSE_FAILED';
type StatusFilter = 'ALL' | ProjectStatus;
type ProjectTab = 'overview' | 'scripts' | 'tasks' | 'monitoring' | 'reports' | 'members';
type MainNav = 'home' | 'projects' | 'settings';
type ConfigTab = 'users' | 'roles' | 'permissions';

type Project = {
  id: number;
  code: string;
  name: string;
  description: string;
  ownerUsername: string;
  status: ProjectStatus;
  createdAt: string;
  updatedAt: string;
};

type ProjectMember = {
  id: number;
  projectId: number;
  username: string;
  displayName: string;
  role: ProjectRole;
};

type ThreadGroup = {
  name: string;
  threads: number;
  rampUp: number;
  loops: number;
  duration: number;
};

type ApiConfig = {
  method: string;
  path: string;
  domain: string;
};

type MonitorConfig = {
  target: string;
  metrics: string[];
};

type KeyValue = {
  key: string;
  value: string;
};

type ScriptParam = {
  key: string;
  label: string;
  value: string | number;
};

type ScriptVersionRecord = {
  versionNo: number;
  fileName: string;
  fileSize: number;
  fileHash: string;
  importedAt: string;
  importedBy: string;
  remark: string;
};

type ScriptAsset = {
  id: number;
  projectId: number;
  name: string;
  sourceFile: string;
  latestVersion: number;
  parseStatus: ParseStatus;
  remark: string;
  updatedAt: string;
  threadGroups: ThreadGroup[];
  apis: ApiConfig[];
  monitors: MonitorConfig[];
  variables: KeyValue[];
  params: ScriptParam[];
  versions: ScriptVersionRecord[];
};

const STORAGE_KEY = 'perftest.frontend.prototype.v3';

const loginLoading = ref(false);
const savingProject = ref(false);
const scriptUploading = ref(false);
const projectDialogVisible = ref(false);
const memberDialogVisible = ref(false);
const paramDrawerVisible = ref(false);
const currentUser = ref<User | null>(readStoredUser());
const projects = ref<Project[]>([]);
const members = ref<ProjectMember[]>([]);
const scriptAssets = ref<ScriptAsset[]>([]);
const activeMainNav = ref<MainNav>('home');
const selectedProjectId = ref<number | null>(null);
const workspaceProjectId = ref<number | null>(null);
const selectedScriptId = ref<number | null>(null);
const activeProjectTab = ref<ProjectTab>('overview');
const activeConfigTab = ref<ConfigTab>('users');
const editingProject = ref<Project | null>(null);
const memberProject = ref<Project | null>(null);
const paramScriptAsset = ref<ScriptAsset | null>(null);
const scriptFile = ref<File | null>(null);
const projectKeyword = ref('');
const scriptKeyword = ref('');
const projectStatusFilter = ref<StatusFilter>('ACTIVE');

const loginForm = reactive({
  username: 'admin',
  password: 'admin123',
});

const projectForm = reactive({
  code: '',
  name: '',
  ownerUsername: '',
  description: '',
});

const memberForm = reactive<{
  username: string;
  displayName: string;
  role: ProjectRole;
}>({
  username: '',
  displayName: '',
  role: 'MEMBER',
});

const scriptForm = reactive({
  name: '',
  remark: '',
});

const paramForm = reactive({
  threads: 100,
  loops: 1,
  duration: 600,
  rampUp: 60,
  environment: 'SIT',
  extraProperties: 'host=127.0.0.1\nprotocol=https',
});

const projectStatusOptions = [
  { label: '活跃', value: 'ACTIVE' },
  { label: '归档', value: 'ARCHIVED' },
  { label: '全部', value: 'ALL' },
];

const projectTabOptions: Array<{ label: string; value: ProjectTab }> = [
  { label: '项目概览', value: 'overview' },
  { label: '脚本管理', value: 'scripts' },
  { label: '测试执行', value: 'tasks' },
  { label: '监控配置', value: 'monitoring' },
  { label: '报告管理', value: 'reports' },
  { label: '成员权限', value: 'members' },
];

const configTabOptions: Array<{ label: string; value: ConfigTab }> = [
  { label: '用户管理', value: 'users' },
  { label: '角色管理', value: 'roles' },
  { label: '权限配置', value: 'permissions' },
];

const systemUsers = [
  { username: 'admin', displayName: '平台管理员', role: '系统管理员', status: '启用', lastLogin: '06/01 09:12' },
  { username: 'tester', displayName: '性能测试工程师', role: '测试负责人', status: '启用', lastLogin: '05/31 18:42' },
  { username: 'devops', displayName: '运维同学', role: '监控维护', status: '启用', lastLogin: '05/30 21:06' },
  { username: 'auditor', displayName: '审计查看人', role: '只读审计', status: '停用', lastLogin: '05/18 10:24' },
];

const systemRoles = [
  { name: '系统管理员', description: '管理全部平台配置和所有项目资产。', permissions: ['用户管理', '角色授权', '项目维护', '报告查看'] },
  { name: '测试负责人', description: '创建项目，维护项目成员和压测资产。', permissions: ['项目维护', '脚本导入', '任务执行', '报告查看'] },
  { name: '监控维护', description: '维护监控目标、指标模板和执行绑定。', permissions: ['监控配置', '报告查看'] },
  { name: '只读审计', description: '查看项目、执行记录和报告，不允许修改。', permissions: ['只读查看'] },
];

const systemPermissions = [
  { module: '项目管理', name: '创建和编辑项目', code: 'project:write' },
  { module: '项目管理', name: '归档和恢复项目', code: 'project:archive' },
  { module: '脚本管理', name: '导入和解析 JMX', code: 'script:import' },
  { module: '测试执行', name: '创建执行任务', code: 'task:create' },
  { module: '监控配置', name: '维护监控目标', code: 'monitor:write' },
  { module: '报告管理', name: '查看和导出报告', code: 'report:read' },
  { module: '系统配置', name: '维护用户角色权限', code: 'system:admin' },
];

const pageTitle = computed(() => {
  if (currentProject.value) {
    return `${currentProject.value.name} · ${tabLabel(activeProjectTab.value)}`;
  }
  if (activeMainNav.value === 'settings') {
    return `系统配置 · ${configLabel(activeConfigTab.value)}`;
  }
  return activeMainNav.value === 'projects' ? '项目管理' : '首页';
});
const activeProjects = computed(() => projects.value.filter((project) => project.status === 'ACTIVE'));
const activeProjectCount = computed(() => activeProjects.value.length);
const archivedProjectCount = computed(() => projects.value.filter((project) => project.status === 'ARCHIVED').length);
const memberTotal = computed(() => members.value.length);
const scriptAssetTotal = computed(() => scriptAssets.value.length);
const pendingTaskCount = computed(() => activeProjectCount.value + currentProjectScripts.value.length + 2);
const monitorTargetTotal = computed(() => new Set(scriptAssets.value.flatMap((script) => script.monitors.map((monitor) => `${script.projectId}:${monitor.target}`))).size);
const recentProjects = computed(() => [...projects.value].sort((a, b) => b.updatedAt.localeCompare(a.updatedAt)).slice(0, 4));
const activeProjectScripts = computed(() => scriptAssets.value.filter((script) => projects.value.some((project) => project.id === script.projectId && project.status === 'ACTIVE')));
const selectedProject = computed(() => projects.value.find((project) => project.id === selectedProjectId.value) ?? null);
const currentProject = computed(() => projects.value.find((project) => project.id === workspaceProjectId.value) ?? null);
const currentProjectScripts = computed(() => {
  if (!currentProject.value) {
    return [];
  }
  return scriptAssets.value.filter((script) => script.projectId === currentProject.value?.id);
});
const selectedScriptAsset = computed(() => currentProjectScripts.value.find((script) => script.id === selectedScriptId.value) ?? currentProjectScripts.value[0] ?? null);
const filteredProjects = computed(() => {
  const keyword = projectKeyword.value.trim().toLowerCase();
  return projects.value.filter((project) => {
    const matchesStatus = projectStatusFilter.value === 'ALL' || project.status === projectStatusFilter.value;
    const source = `${project.code} ${project.name} ${project.ownerUsername} ${project.description}`.toLowerCase();
    return matchesStatus && (!keyword || source.includes(keyword));
  });
});
const filteredScriptAssets = computed(() => {
  const keyword = scriptKeyword.value.trim().toLowerCase();
  return currentProjectScripts.value.filter((script) => {
    const source = `${script.name} ${script.sourceFile} ${script.apis.map((api) => api.path).join(' ')} ${script.variables
      .map((item) => item.key)
      .join(' ')}`.toLowerCase();
    return !keyword || source.includes(keyword);
  });
});
const currentProjectMonitors = computed(() => {
  const monitors = currentProjectScripts.value.flatMap((script) => script.monitors);
  const unique = new Map(monitors.map((monitor) => [monitor.target, monitor]));
  return [...unique.values()];
});
const currentProjectMonitorCount = computed(() => currentProjectMonitors.value.length);
const reportMocks = computed(() => [
  { name: `${currentProject.value?.name ?? '项目'} 容量基线报告`, time: '05/30 18:20', result: '通过' },
  { name: `${currentProject.value?.name ?? '项目'} 瓶颈复测报告`, time: '05/28 21:05', result: '待复核' },
]);

watch(currentUser, (user) => {
  if (user) {
    localStorage.setItem('perftest.currentUser', JSON.stringify(user));
  } else {
    localStorage.removeItem('perftest.currentUser');
  }
});

watch([projects, members, scriptAssets], persistWorkspace, { deep: true });

watch(currentProjectScripts, (scripts) => {
  if (!scripts.some((script) => script.id === selectedScriptId.value)) {
    selectedScriptId.value = scripts[0]?.id ?? null;
  }
});

onMounted(() => {
  loadWorkspace();
  selectedProjectId.value = filteredProjects.value[0]?.id ?? projects.value[0]?.id ?? null;
});

async function login() {
  loginLoading.value = true;
  await delay(220);
  loginLoading.value = false;
  if (!loginForm.username.trim() || !loginForm.password.trim()) {
    ElMessage.error('请输入账号和密码');
    return;
  }
  currentUser.value = {
    username: loginForm.username.trim(),
    displayName: loginForm.username.trim() === 'admin' ? '平台管理员' : loginForm.username.trim(),
    roles: ['ADMIN'],
  };
  activeMainNav.value = 'home';
  workspaceProjectId.value = null;
  ElMessage.success('已进入 Mock 工作台');
}

function logout() {
  currentUser.value = null;
  workspaceProjectId.value = null;
}

function backToProjects() {
  activeMainNav.value = 'projects';
  workspaceProjectId.value = null;
  activeProjectTab.value = 'overview';
}

function selectMainNav(nav: MainNav) {
  activeMainNav.value = nav;
  workspaceProjectId.value = null;
  activeProjectTab.value = 'overview';
}

function selectProject(project: Project) {
  selectedProjectId.value = project.id;
}

function enterProject(project: Project) {
  activeMainNav.value = 'projects';
  selectedProjectId.value = project.id;
  workspaceProjectId.value = project.id;
  activeProjectTab.value = 'overview';
  selectedScriptId.value = scriptsByProject(project.id)[0]?.id ?? null;
}

function openScript(script: ScriptAsset) {
  const project = projects.value.find((item) => item.id === script.projectId);
  if (project) {
    activeMainNav.value = 'projects';
    workspaceProjectId.value = project.id;
    selectedProjectId.value = project.id;
  }
  activeProjectTab.value = 'scripts';
  selectedScriptId.value = script.id;
}

function selectScript(script: ScriptAsset) {
  selectedScriptId.value = script.id;
}

function openCreateDialog() {
  editingProject.value = null;
  Object.assign(projectForm, {
    code: '',
    name: '',
    ownerUsername: currentUser.value?.username ?? 'admin',
    description: '',
  });
  projectDialogVisible.value = true;
}

function openEditDialog(project: Project) {
  editingProject.value = project;
  Object.assign(projectForm, {
    code: project.code,
    name: project.name,
    ownerUsername: project.ownerUsername,
    description: project.description,
  });
  projectDialogVisible.value = true;
}

async function saveProject() {
  if (!projectForm.code || !projectForm.name || !projectForm.ownerUsername) {
    ElMessage.error('项目编码、项目名称和负责人不能为空');
    return;
  }
  const duplicate = projects.value.some((project) => project.code === projectForm.code && project.id !== editingProject.value?.id);
  if (duplicate) {
    ElMessage.error('项目编码已存在，不能重复保存');
    return;
  }

  savingProject.value = true;
  await delay(160);
  const now = new Date().toISOString();
  if (editingProject.value) {
    const target = projects.value.find((project) => project.id === editingProject.value?.id);
    if (target) {
      target.name = projectForm.name;
      target.ownerUsername = projectForm.ownerUsername;
      target.description = projectForm.description;
      target.updatedAt = now;
      ensureOwnerMember(target);
      selectedProjectId.value = target.id;
    }
    ElMessage.success('项目已更新');
  } else {
    const project: Project = {
      id: nextId(projects.value),
      code: projectForm.code,
      name: projectForm.name,
      description: projectForm.description,
      ownerUsername: projectForm.ownerUsername,
      status: 'ACTIVE',
      createdAt: now,
      updatedAt: now,
    };
    projects.value.unshift(project);
    ensureOwnerMember(project);
    selectedProjectId.value = project.id;
    ElMessage.success('项目已创建');
  }
  savingProject.value = false;
  projectDialogVisible.value = false;
}

async function archiveProject(project: Project) {
  try {
    await ElMessageBox.confirm(`归档后，${project.name} 不会出现在新建任务入口，但历史资产仍可查看。`, '确认归档项目', {
      confirmButtonText: '归档',
      cancelButtonText: '取消',
      type: 'warning',
    });
  } catch {
    return;
  }
  project.status = 'ARCHIVED';
  project.updatedAt = new Date().toISOString();
  if (workspaceProjectId.value === project.id) {
    backToProjects();
  }
  ElMessage.success('项目已归档');
}

function restoreProject(project: Project) {
  project.status = 'ACTIVE';
  project.updatedAt = new Date().toISOString();
  ElMessage.success('项目已恢复');
}

function openMemberDialog(project: Project) {
  memberProject.value = project;
  Object.assign(memberForm, {
    username: '',
    displayName: '',
    role: 'MEMBER' as ProjectRole,
  });
  memberDialogVisible.value = true;
}

function addMember() {
  if (!memberProject.value) {
    return;
  }
  if (!memberForm.username) {
    ElMessage.error('请输入成员账号');
    return;
  }
  const exists = members.value.some((member) => member.projectId === memberProject.value?.id && member.username === memberForm.username);
  if (exists) {
    ElMessage.error('该成员已在项目中');
    return;
  }
  if (memberForm.role === 'OWNER') {
    members.value
      .filter((member) => member.projectId === memberProject.value?.id && member.role === 'OWNER')
      .forEach((member) => {
        member.role = 'MEMBER';
      });
    memberProject.value.ownerUsername = memberForm.username;
  }
  members.value.push({
    id: nextId(members.value),
    projectId: memberProject.value.id,
    username: memberForm.username,
    displayName: memberForm.displayName || memberForm.username,
    role: memberForm.role,
  });
  Object.assign(memberForm, {
    username: '',
    displayName: '',
    role: 'MEMBER' as ProjectRole,
  });
  ElMessage.success('成员已添加');
}

function removeMember(projectId: number, username: string) {
  members.value = members.value.filter((member) => !(member.projectId === projectId && member.username === username));
  ElMessage.success('成员已移除');
}

function handleScriptFileChange(event: Event) {
  const input = event.target as HTMLInputElement;
  scriptFile.value = input.files?.[0] ?? null;
}

async function importScriptAsset() {
  if (!currentProject.value || !scriptFile.value) {
    return;
  }
  if (!scriptFile.value.name.toLowerCase().endsWith('.jmx')) {
    ElMessage.error('文件类型不支持，请上传 .jmx 文件');
    return;
  }

  scriptUploading.value = true;
  const cleanFileName = sanitizeFileName(scriptFile.value.name);
  const scriptName = scriptForm.name.trim() || cleanFileName.replace(/\.jmx$/i, '');
  const parsed = await parseJmeterFile(scriptFile.value, scriptName);
  await delay(260);

  const now = new Date().toISOString();
  const existing = currentProjectScripts.value.find((script) => script.name === scriptName);
  if (existing) {
    existing.latestVersion += 1;
    existing.sourceFile = cleanFileName;
    existing.parseStatus = parsed.parseStatus;
    existing.threadGroups = parsed.threadGroups;
    existing.apis = parsed.apis;
    existing.monitors = parsed.monitors;
    existing.variables = parsed.variables;
    existing.params = parsed.params;
    existing.remark = scriptForm.remark;
    existing.updatedAt = now;
    existing.versions.unshift(createVersionRecord(existing.latestVersion, cleanFileName, scriptFile.value, now, scriptForm.remark));
    selectedScriptId.value = existing.id;
    ElMessage.success(`已解析并更新 ${scriptName} v${existing.latestVersion}`);
  } else {
    const asset: ScriptAsset = {
      id: nextId(scriptAssets.value),
      projectId: currentProject.value.id,
      name: scriptName,
      sourceFile: cleanFileName,
      latestVersion: 1,
      parseStatus: parsed.parseStatus,
      remark: scriptForm.remark,
      updatedAt: now,
      threadGroups: parsed.threadGroups,
      apis: parsed.apis,
      monitors: parsed.monitors,
      variables: parsed.variables,
      params: parsed.params,
      versions: [createVersionRecord(1, cleanFileName, scriptFile.value, now, scriptForm.remark)],
    };
    scriptAssets.value.unshift(asset);
    selectedScriptId.value = asset.id;
    ElMessage.success(`已解析并导入 ${scriptName}`);
  }

  scriptUploading.value = false;
  scriptFile.value = null;
  Object.assign(scriptForm, { name: '', remark: '' });
}

function openParamDrawer(script: ScriptAsset) {
  paramScriptAsset.value = script;
  const valueMap = Object.fromEntries(script.params.map((param) => [param.key, param.value]));
  Object.assign(paramForm, {
    threads: parseParamNumber(valueMap.threads, 100),
    loops: parseParamNumber(valueMap.loops, 1),
    duration: parseParamNumber(valueMap.duration, 600),
    rampUp: parseParamNumber(valueMap.rampUp, 60),
    environment: String(valueMap.environment ?? 'SIT'),
    extraProperties: String(valueMap.extraProperties ?? ''),
  });
  paramDrawerVisible.value = true;
}

function saveParams() {
  if (!paramScriptAsset.value) {
    return;
  }
  paramScriptAsset.value.params = [
    { key: 'threads', label: '线程数', value: paramForm.threads },
    { key: 'loops', label: '循环次数', value: paramForm.loops },
    { key: 'duration', label: '持续时间', value: `${paramForm.duration}s` },
    { key: 'rampUp', label: 'Ramp-Up', value: `${paramForm.rampUp}s` },
    { key: 'environment', label: '目标环境', value: paramForm.environment },
    { key: 'extraProperties', label: '扩展属性', value: paramForm.extraProperties || '未配置' },
  ];
  paramDrawerVisible.value = false;
  ElMessage.success('默认执行参数已保存');
}

function resetWorkspace() {
  const seed = createSeedData();
  projects.value = seed.projects;
  members.value = seed.members;
  scriptAssets.value = seed.scriptAssets;
  selectedProjectId.value = projects.value[0]?.id ?? null;
  workspaceProjectId.value = null;
  selectedScriptId.value = null;
  ElMessage.success('Mock 数据已重置');
}

function membersByProject(projectId: number) {
  return members.value.filter((member) => member.projectId === projectId);
}

function scriptsByProject(projectId: number) {
  return scriptAssets.value.filter((script) => script.projectId === projectId);
}

function ensureOwnerMember(project: Project) {
  members.value
    .filter((member) => member.projectId === project.id && member.role === 'OWNER')
    .forEach((member) => {
      member.role = 'MEMBER';
    });
  const owner = members.value.find((member) => member.projectId === project.id && member.username === project.ownerUsername);
  if (owner) {
    owner.role = 'OWNER';
  } else {
    members.value.push({
      id: nextId(members.value),
      projectId: project.id,
      username: project.ownerUsername,
      displayName: project.ownerUsername,
      role: 'OWNER',
    });
  }
}

function loadWorkspace() {
  const stored = localStorage.getItem(STORAGE_KEY);
  if (stored) {
    try {
      const parsed = JSON.parse(stored) as {
        projects: Project[];
        members: ProjectMember[];
        scriptAssets: ScriptAsset[];
      };
      projects.value = parsed.projects;
      members.value = parsed.members;
      scriptAssets.value = parsed.scriptAssets;
      return;
    } catch {
      localStorage.removeItem(STORAGE_KEY);
    }
  }
  const seed = createSeedData();
  projects.value = seed.projects;
  members.value = seed.members;
  scriptAssets.value = seed.scriptAssets;
}

function persistWorkspace() {
  localStorage.setItem(
    STORAGE_KEY,
    JSON.stringify({
      projects: projects.value,
      members: members.value,
      scriptAssets: scriptAssets.value,
    }),
  );
}

async function parseJmeterFile(file: File, scriptName: string) {
  const text = await file.text().catch(() => '');
  const threadCount = clamp(countMatches(text, /<ThreadGroup\b/g) || (scriptName.includes('查询') ? 1 : 2), 1, 4);
  const apiCount = clamp(countMatches(text, /HTTPSamplerProxy/g) || (scriptName.includes('支付') ? 5 : 8), 2, 12);
  const hasMonitor = /BackendListener|ResultCollector|PerfMon|kg\.apc/i.test(text);

  return {
    parseStatus: 'PARSED' as ParseStatus,
    threadGroups: Array.from({ length: threadCount }, (_, index) => ({
      name: index === 0 ? '主业务线程组' : `辅助链路线程组 ${index}`,
      threads: index === 0 ? 100 : 40 + index * 20,
      rampUp: index === 0 ? 60 : 30,
      loops: 1,
      duration: index === 0 ? 600 : 300,
    })),
    apis: Array.from({ length: apiCount }, (_, index) => ({
      method: index % 3 === 0 ? 'POST' : 'GET',
      path: mockApiPath(scriptName, index),
      domain: '${host}',
    })),
    monitors: [
      { target: '应用服务', metrics: ['TPS', 'RT', '错误率'] },
      { target: 'JVM', metrics: ['Heap', 'GC', 'Thread'] },
      ...(hasMonitor ? [{ target: '主机资源', metrics: ['CPU', 'Memory', 'Disk IO'] }] : [{ target: '数据库', metrics: ['连接数', '慢 SQL'] }]),
    ],
    variables: [
      { key: 'host', value: '127.0.0.1' },
      { key: 'protocol', value: 'https' },
      { key: 'env', value: 'SIT' },
    ],
    params: defaultParams(),
  };
}

function createSeedData() {
  const projectsSeed: Project[] = [
    {
      id: 1,
      code: 'loan-core',
      name: '信贷核心压测',
      description: '覆盖授信申请、额度查询、合同签署链路，当前重点验证高峰并发下游依赖稳定性。',
      ownerUsername: 'admin',
      status: 'ACTIVE',
      createdAt: '2026-05-02T09:24:00.000Z',
      updatedAt: '2026-05-28T16:10:00.000Z',
    },
    {
      id: 2,
      code: 'payment-gateway',
      name: '支付网关容量验证',
      description: '面向支付路由和回调通知的容量摸底，后续接入分布式执行节点。',
      ownerUsername: 'tester',
      status: 'ACTIVE',
      createdAt: '2026-05-08T11:20:00.000Z',
      updatedAt: '2026-05-30T10:18:00.000Z',
    },
    {
      id: 3,
      code: 'crm-archive',
      name: 'CRM 历史压测项目',
      description: '已完成验收，仅保留历史脚本、任务和报告查看入口。',
      ownerUsername: 'qa-lead',
      status: 'ARCHIVED',
      createdAt: '2026-04-12T08:00:00.000Z',
      updatedAt: '2026-05-16T18:42:00.000Z',
    },
  ];

  const membersSeed: ProjectMember[] = [
    { id: 1, projectId: 1, username: 'admin', displayName: '平台管理员', role: 'OWNER' },
    { id: 2, projectId: 1, username: 'tester', displayName: '性能测试工程师', role: 'MEMBER' },
    { id: 3, projectId: 2, username: 'tester', displayName: '性能测试工程师', role: 'OWNER' },
    { id: 4, projectId: 2, username: 'devops', displayName: '运维同学', role: 'MEMBER' },
    { id: 5, projectId: 3, username: 'qa-lead', displayName: '测试负责人', role: 'OWNER' },
  ];

  const scriptSeed: ScriptAsset[] = [
    createMockAsset(1, 1, '授信申请主链路', 3, 'credit-apply-main.jmx'),
    createMockAsset(2, 1, '额度查询基准脚本', 1, 'quota-query.jmx'),
    createMockAsset(3, 2, '支付回调通知', 1, 'payment-callback.jmx'),
  ];

  return {
    projects: projectsSeed,
    members: membersSeed,
    scriptAssets: scriptSeed,
  };
}

function createMockAsset(id: number, projectId: number, name: string, latestVersion: number, sourceFile: string): ScriptAsset {
  const now = `2026-05-${22 + id}T10:32:00.000Z`;
  return {
    id,
    projectId,
    name,
    sourceFile,
    latestVersion,
    parseStatus: 'PARSED',
    remark: 'Mock 解析结果，可按需求继续调整字段。',
    updatedAt: now,
    threadGroups: [
      { name: '主业务线程组', threads: name.includes('查询') ? 60 : 120, rampUp: 60, loops: 1, duration: 600 },
      ...(name.includes('查询') ? [] : [{ name: '登录前置线程组', threads: 30, rampUp: 20, loops: 1, duration: 300 }]),
    ],
    apis: [
      { method: 'POST', path: mockApiPath(name, 0), domain: '${host}' },
      { method: 'GET', path: mockApiPath(name, 1), domain: '${host}' },
      { method: 'POST', path: mockApiPath(name, 2), domain: '${host}' },
    ],
    monitors: [
      { target: '应用服务', metrics: ['TPS', 'RT', '错误率'] },
      { target: 'JVM', metrics: ['Heap', 'GC', 'Thread'] },
      { target: name.includes('支付') ? 'Redis' : '数据库', metrics: name.includes('支付') ? ['QPS', '命中率'] : ['连接数', '慢 SQL'] },
    ],
    variables: [
      { key: 'host', value: '127.0.0.1' },
      { key: 'protocol', value: 'https' },
      { key: 'env', value: 'SIT' },
    ],
    params: defaultParams(),
    versions: Array.from({ length: latestVersion }, (_, index) =>
      createVersionRecord(latestVersion - index, sourceFile, { size: 142000 + id * 4200 } as File, `2026-05-${22 + id - index}T10:32:00.000Z`, index === 0 ? '当前解析版本' : '历史版本'),
    ),
  };
}

function createVersionRecord(versionNo: number, fileName: string, file: File, importedAt: string, remark: string): ScriptVersionRecord {
  return {
    versionNo,
    fileName,
    fileSize: file.size,
    fileHash: mockHash(`${fileName}-${file.size}-${importedAt}-${versionNo}`),
    importedAt,
    importedBy: currentUser.value?.username ?? 'admin',
    remark,
  };
}

function defaultParams(): ScriptParam[] {
  return [
    { key: 'threads', label: '线程数', value: 100 },
    { key: 'loops', label: '循环次数', value: 1 },
    { key: 'duration', label: '持续时间', value: '600s' },
    { key: 'rampUp', label: 'Ramp-Up', value: '60s' },
    { key: 'environment', label: '目标环境', value: 'SIT' },
  ];
}

function readStoredUser(): User | null {
  const stored = localStorage.getItem('perftest.currentUser');
  if (!stored) {
    return null;
  }
  try {
    return JSON.parse(stored) as User;
  } catch {
    return null;
  }
}

function nextId(items: Array<{ id: number }>) {
  return items.reduce((max, item) => Math.max(max, item.id), 0) + 1;
}

function delay(ms: number) {
  return new Promise((resolve) => {
    window.setTimeout(resolve, ms);
  });
}

function sanitizeFileName(fileName: string) {
  return fileName.replace(/[\\/]/g, '-').replace(/\s+/g, '-');
}

function countMatches(value: string, pattern: RegExp) {
  return value.match(pattern)?.length ?? 0;
}

function clamp(value: number, min: number, max: number) {
  return Math.min(Math.max(value, min), max);
}

function mockApiPath(scriptName: string, index: number) {
  const prefix = scriptName.includes('支付') ? '/api/payment' : scriptName.includes('查询') ? '/api/quota' : '/api/credit';
  const paths = ['/login', '/prepare', '/submit', '/query', '/confirm', '/callback', '/status', '/report'];
  return `${prefix}${paths[index % paths.length]}`;
}

function mockHash(value: string) {
  let hash = 0;
  for (let index = 0; index < value.length; index += 1) {
    hash = (hash << 5) - hash + value.charCodeAt(index);
    hash |= 0;
  }
  return `sha256:${Math.abs(hash).toString(16).padStart(8, '0')}`;
}

function parseParamNumber(value: unknown, fallback: number) {
  const parsed = Number(String(value ?? '').replace(/[^\d.]/g, ''));
  return Number.isFinite(parsed) ? parsed : fallback;
}

function tabLabel(tab: ProjectTab) {
  const option = projectTabOptions.find((item) => item.value === tab);
  return option?.label ?? '项目概览';
}

function configLabel(tab: ConfigTab) {
  const option = configTabOptions.find((item) => item.value === tab);
  return option?.label ?? '用户管理';
}

function moduleIndex(tab: ProjectTab) {
  return String(projectTabOptions.findIndex((item) => item.value === tab) + 1).padStart(2, '0');
}

function configIndex(tab: ConfigTab) {
  return String(configTabOptions.findIndex((item) => item.value === tab) + 1).padStart(2, '0');
}

function projectStatusText(status: ProjectStatus) {
  return status === 'ACTIVE' ? '活跃' : '已归档';
}

function projectRoleText(role: ProjectRole) {
  return role === 'OWNER' ? '项目负责人' : '项目成员';
}

function parseStatusText(status: ParseStatus) {
  return status === 'PARSED' ? '解析成功' : '解析失败';
}

function formatDate(value: string) {
  return new Date(value).toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  });
}

function formatFileSize(size: number) {
  if (size < 1024) {
    return `${size} B`;
  }
  if (size < 1024 * 1024) {
    return `${(size / 1024).toFixed(1)} KB`;
  }
  return `${(size / 1024 / 1024).toFixed(1)} MB`;
}
</script>
