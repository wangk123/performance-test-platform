<template>
  <div class="page-head">
    <div>
      <h1>工作台</h1>
      <p>项目资产、执行状态与监控告警的入口。从这里进入项目或创建压测任务。</p>
    </div>
    <div class="topbar-actions">
      <a-button @click="selectMainNav('settings')">系统配置</a-button>
      <a-button type="primary" @click="selectMainNav('projects')">进入项目</a-button>
    </div>
  </div>

  <section class="metrics-grid">
    <div class="metric-card">
      <label>活跃项目</label>
      <strong class="font-data">{{ activeProjectCount }}</strong>
    </div>
    <div class="metric-card">
      <label>脚本资产</label>
      <strong class="font-data">{{ scriptAssetTotal }}</strong>
    </div>
    <div class="metric-card">
      <label>待执行任务</label>
      <strong class="font-data">{{ pendingTaskCount }}</strong>
    </div>
    <div class="metric-card">
      <label>监控目标</label>
      <strong class="font-data">{{ monitorTargetTotal }}</strong>
    </div>
  </section>

  <section class="home-grid">
    <a-card class="panel" :bordered="false">
      <div class="panel-header">
        <div>
          <h2>近期项目</h2>
          <p>按更新时间展示，点击进入项目详情。</p>
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
          <span>{{ project.code }} · {{ project.ownerUsername }}</span>
        </button>
        <div v-if="!recentProjects.length" class="home-empty">暂无近期项目</div>
      </div>
    </a-card>

    <a-card class="panel" :bordered="false">
      <div class="panel-header">
        <div>
          <h2>工作流入口</h2>
          <p>高频操作。</p>
        </div>
      </div>
      <div class="quick-actions">
        <button type="button" @click="selectMainNav('projects')">
          <strong>进入项目管理</strong>
          <small>维护项目、成员、脚本、执行和报告归属</small>
        </button>
        <button type="button" :disabled="!recentProjects[0]" @click="recentProjects[0] && enterProject(recentProjects[0])">
          <strong>打开近期项目</strong>
          <small>{{ recentProjects[0]?.name ?? '暂无近期项目' }}</small>
        </button>
        <button type="button" @click="selectMainNav('executionNodes')">
          <strong>执行器配置</strong>
          <small>查看节点负载与可用性</small>
        </button>
      </div>
    </a-card>

    <a-card class="panel home-wide-panel" :bordered="false">
      <div class="panel-header">
        <div>
          <h2>平台运行概览</h2>
          <p>资产 → 执行 → 监控 → 报告</p>
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
          <small>执行配置从脚本默认参数复制快照</small>
        </div>
        <div>
          <span>监控覆盖</span>
          <strong>{{ monitorTargetTotal }} 个目标</strong>
          <small>应用、JVM、数据库、中间件归项目</small>
        </div>
        <div>
          <span>报告沉淀</span>
          <strong>{{ reportMocks.length }} 份近期报告</strong>
          <small>按项目、脚本、执行批次追溯</small>
        </div>
      </div>
    </a-card>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import type { Project } from '../../types';
import { getDashboardSummaryApi } from '../../api/dashboard';
import { useNavigation } from '../../composables/useNavigation';

defineEmits<{
  (e: 'create'): void;
  (e: 'edit'): void;
  (e: 'members'): void;
}>();

const { selectMainNav, enterProject } = useNavigation();
const activeProjectCount = ref(0);
const scriptAssetTotal = ref(0);
const pendingTaskCount = ref(0);
const monitorTargetTotal = ref(0);
const recentProjects = ref<Project[]>([]);
const reportMocks = ref([{ name: '近期报告 Mock', time: '待生成', result: '待接入' }]);

onMounted(async () => {
  try {
    const summary = await getDashboardSummaryApi();
    activeProjectCount.value = summary.activeProjectCount;
    scriptAssetTotal.value = summary.scriptAssetTotal;
    pendingTaskCount.value = summary.taskTotal;
    recentProjects.value = summary.recentProjects;
  } catch {
    recentProjects.value = [];
  }
});
</script>
