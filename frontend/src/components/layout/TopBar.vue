<template>
  <el-header class="topbar">
    <div>
      <div class="eyebrow">Phase 2 Prototype</div>
      <div class="page-title">{{ pageTitle }}</div>
    </div>
    <div class="topbar-actions">
      <el-tag type="warning" effect="light">Mock API</el-tag>
      <el-tag v-if="currentProject" type="success" effect="light">{{ currentProject.code }}</el-tag>
      <el-tag v-if="currentUser" type="success" effect="light">{{ currentUser.displayName }}</el-tag>
      <el-button v-if="currentUser" @click="fullLogout">退出</el-button>
    </div>
  </el-header>
</template>

<script setup lang="ts">
import { useAuth } from '../../composables/useAuth';
import { useNavigation } from '../../composables/useNavigation';
import { useWorkspace } from '../../composables/useWorkspace';

const { currentUser } = useAuth();
const { currentProject, fullLogout } = useWorkspace();
const { pageTitle } = useNavigation();
</script>
