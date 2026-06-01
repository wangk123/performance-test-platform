<template>
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
        <button type="button" :disabled="!activeProjectScripts[0]" @click="openScript(activeProjectScripts[0])">
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
        <button
          v-for="project in recentProjects"
          :key="project.id"
          type="button"
          @click="enterProject(project)"
        >
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

<script setup lang="ts">
import { useNavigation } from '../../composables/useNavigation';
import { useWorkspace } from '../../composables/useWorkspace';

const { selectMainNav, openScript } = useNavigation();
const {
  activeProjectCount,
  scriptAssetTotal,
  pendingTaskCount,
  monitorTargetTotal,
  activeProjectScripts,
  recentProjects,
  enterProject,
  scriptsByProject,
  reportMocks,
} = useWorkspace();
</script>
