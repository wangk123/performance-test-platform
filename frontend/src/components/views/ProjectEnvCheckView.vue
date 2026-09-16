<template>
  <section class="pev-view">
    <header class="pev-head">
      <div>
        <h2>环境检查</h2>
        <p>项目级 SSH 凭据池，按机器地址匹配各计划文档「环境部署信息」解析出的检查目标；计划侧可配置仅本计划生效的覆盖凭据。</p>
      </div>
    </header>
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
.pev-head h2 {
  margin: 0;
  font-size: 17px;
  font-weight: 700;
}
.pev-head p {
  margin: 4px 0 0;
  color: var(--muted);
  font-size: 12.5px;
}
</style>
