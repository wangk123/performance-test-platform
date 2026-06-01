<template>
  <AuthScreen v-if="!currentUser" />
  <ScriptEditorPage v-else-if="scriptEditorVisible" />
  <MainLayout v-else />
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted } from 'vue';
import { useAuth } from './composables/useAuth';
import { useScriptEditor } from './composables/useScriptEditor';
import AuthScreen from './views/AuthScreen.vue';
import MainLayout from './views/MainLayout.vue';
import ScriptEditorPage from './views/ScriptEditorPage.vue';

const { currentUser } = useAuth();
const { scriptEditorVisible, syncEditorRoute } = useScriptEditor();

onMounted(() => {
  syncEditorRoute();
  window.addEventListener('hashchange', syncEditorRoute);
});

onBeforeUnmount(() => {
  window.removeEventListener('hashchange', syncEditorRoute);
});
</script>
