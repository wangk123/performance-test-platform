<template>
  <el-container class="app-shell">
    <SidebarNav />

    <el-container direction="vertical">
      <TopBar />

      <el-main class="content">
        <RouterView v-slot="{ Component }">
          <component
            :is="Component"
            @create="openCreate"
            @edit="openEdit"
            @members="openMembers"
          />
        </RouterView>
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
import SidebarNav from '../components/layout/SidebarNav.vue';
import TopBar from '../components/layout/TopBar.vue';
import ProjectFormDialog from '../components/dialogs/ProjectFormDialog.vue';
import MemberDialog from '../components/dialogs/MemberDialog.vue';
import ScriptImportDialog from '../components/dialogs/ScriptImportDialog.vue';
import ScriptParamDrawer from '../components/drawers/ScriptParamDrawer.vue';

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
