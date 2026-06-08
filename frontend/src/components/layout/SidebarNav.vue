<template>
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
        <button
          class="side-entry"
          :class="{ active: activeMainNav === 'home' }"
          type="button"
          @click="selectMainNav('home')"
        >
          <span class="nav-index">01</span>
          <span>首页</span>
        </button>
        <button
          class="side-entry"
          :class="{ active: activeMainNav === 'projects' }"
          type="button"
          @click="selectMainNav('projects')"
        >
          <span class="nav-index">02</span>
          <span>项目管理</span>
        </button>
        <button
          class="side-entry"
          :class="{ active: activeMainNav === 'settings' }"
          type="button"
          @click="selectMainNav('settings')"
        >
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
        @click="enterProjectTab(option.value)"
      >
        <span>{{ moduleIndex(option.value) }}</span>
        <strong>{{ option.label }}</strong>
      </button>
    </div>

    <div class="sidebar-note">
      <span>后端接口模式</span>
      <strong>JMeter 原生执行</strong>
    </div>
  </el-aside>
</template>

<script setup lang="ts">
import { projectTabOptions } from '../../constants';
import { moduleIndex } from '../../utils/format';
import { useAuth } from '../../composables/useAuth';
import { useNavigation } from '../../composables/useNavigation';
import { useWorkspace } from '../../composables/useWorkspace';

const { currentUser } = useAuth();
const { activeMainNav, activeProjectTab, selectMainNav, backToProjects, enterProjectTab } =
  useNavigation();
const { activeProjects, currentProject, selectedProject, selectProject, enterProject } = useWorkspace();
</script>
