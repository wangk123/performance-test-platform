<template>
  <el-container class="app-shell">
    <SidebarNav />

    <el-container direction="vertical">
      <TopBar />

      <el-main class="content">
        <HomeView v-if="activeMainNav === 'home'" />
        <SettingsView v-else-if="activeMainNav === 'settings'" />
        <ProjectListView
          v-else-if="activeMainNav === 'projects' && !currentProject"
          @create="openCreate"
          @edit="openEdit"
          @members="openMembers"
        />
        <ProjectDetail v-else-if="currentProject" @edit="openEdit" @members="openMembers" />
      </el-main>
    </el-container>
  </el-container>

  <ProjectFormDialog v-model="projectDialogVisible" :editing-project="editingProject" />
  <MemberDialog v-model="memberDialogVisible" :project="memberProject" />
  <ScriptImportDialog />
  <ScriptParamDrawer />
</template>

<script setup lang="ts">
import { ref } from 'vue';
import type { Project } from '../types';
import { useNavigation } from '../composables/useNavigation';
import { useWorkspace } from '../composables/useWorkspace';
import SidebarNav from '../components/layout/SidebarNav.vue';
import TopBar from '../components/layout/TopBar.vue';
import HomeView from '../components/views/HomeView.vue';
import SettingsView from '../components/views/SettingsView.vue';
import ProjectListView from '../components/views/ProjectListView.vue';
import ProjectDetail from '../components/views/ProjectDetail.vue';
import ProjectFormDialog from '../components/dialogs/ProjectFormDialog.vue';
import MemberDialog from '../components/dialogs/MemberDialog.vue';
import ScriptImportDialog from '../components/dialogs/ScriptImportDialog.vue';
import ScriptParamDrawer from '../components/drawers/ScriptParamDrawer.vue';

const { activeMainNav } = useNavigation();
const { currentProject } = useWorkspace();

const projectDialogVisible = ref(false);
const memberDialogVisible = ref(false);
const editingProject = ref<Project | null>(null);
const memberProject = ref<Project | null>(null);

function openCreate() {
  editingProject.value = null;
  projectDialogVisible.value = true;
}
function openEdit(project: Project) {
  editingProject.value = project;
  projectDialogVisible.value = true;
}
function openMembers(project: Project) {
  memberProject.value = project;
  memberDialogVisible.value = true;
}
</script>
