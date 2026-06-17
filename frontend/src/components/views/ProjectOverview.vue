<template>
  <template v-if="currentProject">
    <section class="project-hero">
      <div>
        <button class="breadcrumb-button" type="button" @click="backToProjects">项目列表 /</button>
        <span class="eyebrow">{{ currentProject.code }}</span>
        <h1>{{ currentProject.name }}</h1>
        <p>{{ currentProject.description }}</p>
      </div>
      <div class="project-hero-actions">
        <a-tag :color="currentProject.status === 'ACTIVE' ? 'success' : 'default'">
          {{ projectStatusText(currentProject.status) }}
        </a-tag>
        <a-button @click="$emit('edit', currentProject)">编辑项目</a-button>
        <a-button @click="$emit('members', currentProject)">成员权限</a-button>
      </div>
    </section>

    <section class="project-dashboard">
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
            <a-button type="primary" @click="enterProjectTab('scripts')">管理脚本</a-button>
          </div>
          <div class="mini-list">
            <button
              v-for="script in currentProjectScripts"
              :key="script.id"
              type="button"
              @click="openScript(script)"
            >
              <strong>{{ script.name }}</strong>
              <span>{{ getThreadGroupCount(script) }} 个线程组 · {{ script.apis.length }} 个接口 · v{{ script.latestVersion }}</span>
            </button>
          </div>
        </div>
      </div>
    </section>
  </template>
</template>

<script setup lang="ts">
import { projectStatusText } from '../../utils/format';
import { useNavigation } from '../../composables/useNavigation';
import { useThreadGroups } from '../../composables/useThreadGroups';
import { useWorkspace } from '../../composables/useWorkspace';
import type { Project, ScriptAsset } from '../../types';

defineEmits<{
  (e: 'edit', project: Project): void;
  (e: 'members', project: Project): void;
}>();

const { backToProjects, enterProjectTab, openScript } = useNavigation();
const { currentProject, currentProjectScripts, currentProjectMonitorCount, reportMocks } =
  useWorkspace();

function getThreadGroupCount(script: ScriptAsset): number {
  return useThreadGroups(() => script.steps).threadGroupCount.value;
}
</script>
