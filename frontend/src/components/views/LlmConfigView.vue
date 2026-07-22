<template>
  <section class="settings-shell">
    <div class="page-head">
      <div>
        <h1>模型配置</h1>
        <p>管理 LLM 提供商、模型与调用记录。</p>
      </div>
    </div>
    <div class="panel">
      <SettingsTabBar :model-value="activeLlmConfigTab" :options="tabOptions" @update:model-value="selectLlmConfigTab" />
      <div class="settings-body">
        <LlmProvidersPanel v-if="activeLlmConfigTab === 'llm-providers'" />
        <LlmModelsPanel v-else-if="activeLlmConfigTab === 'llm-models'" />
        <LlmCallRecordsPanel v-else-if="activeLlmConfigTab === 'llm-call-records'" />
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { llmConfigTabOptions } from '../../constants';
import { useNavigation } from '../../composables/useNavigation';
import type { LlmConfigTab } from '../../types';
import SettingsTabBar from '../settings/SettingsTabBar.vue';
import LlmProvidersPanel from '../settings/LlmProvidersPanel.vue';
import LlmModelsPanel from '../settings/LlmModelsPanel.vue';
import LlmCallRecordsPanel from '../settings/LlmCallRecordsPanel.vue';

const route = useRoute();
const router = useRouter();
const { activeLlmConfigTab } = useNavigation();

const tabOptions = llmConfigTabOptions.map((option) => ({
  label: option.label,
  value: option.value,
}));

const llmConfigPaths: Record<LlmConfigTab, string> = {
  'llm-providers': '/llm-config/providers',
  'llm-models': '/llm-config/models',
  'llm-call-records': '/llm-config/call-records',
};

function selectLlmConfigTab(tab: string) {
  const next = tab as LlmConfigTab;
  activeLlmConfigTab.value = next;
  void router.push(llmConfigPaths[next]);
}

function syncTabFromRoute() {
  if (route.path.endsWith('/providers')) {
    activeLlmConfigTab.value = 'llm-providers';
  } else if (route.path.endsWith('/models')) {
    activeLlmConfigTab.value = 'llm-models';
  } else if (route.path.endsWith('/call-records')) {
    activeLlmConfigTab.value = 'llm-call-records';
  }
}

watch(() => route.path, syncTabFromRoute, { immediate: true });
</script>
