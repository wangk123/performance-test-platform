<template>
  <div class="app-shell" :class="{ 'has-ctx': Boolean(currentProject) }">
    <GlobalRail />
    <ProjectContextNav v-if="currentProject" />

    <div class="app-main">
      <TopBar />
      <main class="content">
        <RouterView v-slot="{ Component }">
          <component
            :is="Component"
            @create="openCreate"
            @edit="openEdit"
            @members="openMembers"
          />
        </RouterView>
      </main>
    </div>
  </div>

  <ProjectFormDialog v-model="projectDialogVisible" :editing-project="editingProject" />
  <MemberDialog v-model="memberDialogVisible" :project="memberProject" />
  <ScriptImportDialog />
  <ScriptCreateDialog />
  <ScriptParamDrawer />
</template>

<script setup lang="ts">
import { ref } from 'vue';
import type { Project } from '../types';
import GlobalRail from '../components/layout/GlobalRail.vue';
import ProjectContextNav from '../components/layout/ProjectContextNav.vue';
import TopBar from '../components/layout/TopBar.vue';
import ProjectFormDialog from '../components/dialogs/ProjectFormDialog.vue';
import MemberDialog from '../components/dialogs/MemberDialog.vue';
import ScriptImportDialog from '../components/dialogs/ScriptImportDialog.vue';
import ScriptCreateDialog from '../components/dialogs/ScriptCreateDialog.vue';
import ScriptParamDrawer from '../components/drawers/ScriptParamDrawer.vue';
import { useWorkspace } from '../composables/useWorkspace';

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
