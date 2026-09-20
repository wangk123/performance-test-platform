<template>
  <section class="pev-view">
    <EnvCheckCredentialCard :project-id="projectId" />
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useRoute } from 'vue-router';
import EnvCheckCredentialCard from '../env-check/EnvCheckCredentialCard.vue';

const route = useRoute();
const projectId = computed(() => Number(route.params.projectId) || 0);
</script>

<style scoped>
.pev-view {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
</style>
