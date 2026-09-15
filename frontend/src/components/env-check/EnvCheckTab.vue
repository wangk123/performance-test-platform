<template>
  <div ref="rootEl" class="ec-tab">
    <EnvCheckSettingsCard
      :plan-id="plan.id"
      :precheck-json="precheckJson"
      :items="itemMetas"
      :loading="itemsLoading"
      @saved="docPlan.refresh()"
    />
    <EnvCheckCredentialCard ref="credCard" :project-id="projectId" :plan-id="plan.id" />
    <EnvCheckResultPanel
      :plan-id="plan.id"
      :items="itemMetas"
      :fix-enabled="fixEnabled"
      @goto-credentials="scrollToCredentials"
    />
  </div>
</template>

<script setup lang="ts">
import type { ComponentPublicInstance, Ref } from 'vue';
import { computed, onMounted, ref } from 'vue';
import { message } from 'ant-design-vue';
import type { EnvCheckItemMeta, TaskPlan } from '../../types';
import { useWorkspace } from '../../composables/useWorkspace';
import { fetchEnvCheckItemsApi } from '../../api/env-check';
import EnvCheckSettingsCard from './EnvCheckSettingsCard.vue';
import EnvCheckCredentialCard from './EnvCheckCredentialCard.vue';
import EnvCheckResultPanel from './EnvCheckResultPanel.vue';

/** docPlan 为 usePlanDoc 返回的子集：保存设置后刷新计划（precheckJson 回流）。 */
type PlanDocLike = {
  plan: Ref<TaskPlan | null>;
  refresh: () => Promise<void>;
};

const props = defineProps<{ plan: TaskPlan; docPlan: PlanDocLike }>();

const { workspaceProjectId } = useWorkspace();
const projectId = computed(() => props.plan.projectId ?? workspaceProjectId.value ?? 0);
/** 保存后 doc.plan 已刷新，precheckJson 经此回流设置卡。 */
const precheckJson = computed(() => props.docPlan.plan.value?.precheckJson ?? props.plan.precheckJson ?? null);

const credCard = ref<ComponentPublicInstance | null>(null);
const rootEl = ref<HTMLElement | null>(null);

/** 检查项目录整页拉取一次（含 fixEnabled 总闸），经 props 下发设置卡与结果面板。 */
const itemMetas = ref<EnvCheckItemMeta[]>([]);
const fixEnabled = ref(true);
const itemsLoading = ref(false);

async function loadItems() {
  itemsLoading.value = true;
  try {
    const response = await fetchEnvCheckItemsApi();
    itemMetas.value = response.items;
    fixEnabled.value = response.fixEnabled;
  } catch (error) {
    message.error(error instanceof Error ? error.message : '检查项清单加载失败');
  } finally {
    itemsLoading.value = false;
  }
}

/** 缺凭据弹窗「去凭据」：滚动定位到凭据卡。 */
function scrollToCredentials() {
  const card = credCard.value?.$el as HTMLElement | undefined;
  card?.scrollIntoView({ behavior: 'smooth', block: 'start' });
}

onMounted(loadItems);
</script>

<style scoped>
.ec-tab {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 16px 2px 32px;
}
</style>

<style>
/* 三卡共用外壳（子组件 scoped 样式不共享，收口在 Tab 容器；原型 .card 规格）。 */
.ec-card {
  border: 1px solid var(--line);
  border-radius: 12px;
  background: var(--surface);
  box-shadow: var(--shadow);
  padding: 18px 20px 20px;
}
.ec-card-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}
.ec-card-head h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 700;
}
.ec-card-head p {
  margin: 4px 0 0;
  color: var(--muted);
  font-size: 12px;
}
</style>
