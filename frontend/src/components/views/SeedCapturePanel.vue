<template>
  <div class="capture-panel">
    <a-tabs v-model:activeKey="activeTab">
      <a-tab-pane key="strategies" tab="录制策略">
        <SeedCaptureStrategyPanel
          :datasources="datasources"
          @changed="onStrategiesChanged"
          @selected="selectedStrategyId = $event"
          @executed="onSampleStarted"
        />
      </a-tab-pane>
      <a-tab-pane key="samples" tab="样本历史">
        <SeedCaptureSamplePanel
          :strategy="selectedStrategy"
          :reload-key="reloadKey"
        />
      </a-tab-pane>
      <a-tab-pane key="analysis" tab="多样本分析">
        <SeedCaptureAnalysisPanel
          :strategy="selectedStrategy"
          :reload-key="reloadKey"
          @open-template="openTemplate"
        />
      </a-tab-pane>
    </a-tabs>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import {
  type SeedCaptureSample,
  type SeedCaptureStrategy,
  type SeedDatasource,
} from '../../api/seed';
import SeedCaptureAnalysisPanel from './SeedCaptureAnalysisPanel.vue';
import SeedCaptureSamplePanel from './SeedCaptureSamplePanel.vue';
import SeedCaptureStrategyPanel from './SeedCaptureStrategyPanel.vue';

defineProps<{ datasources: SeedDatasource[] }>();

const emit = defineEmits<{ openTemplate: [templateId: number] }>();

const activeTab = ref('strategies');
const strategies = ref<SeedCaptureStrategy[]>([]);
const selectedStrategyId = ref<number>();
const reloadKey = ref(0);
const selectedStrategy = computed(() =>
  strategies.value.find((strategy) => strategy.id === selectedStrategyId.value) || null,
);

function onStrategiesChanged(items: SeedCaptureStrategy[]) {
  strategies.value = items;
  if (!selectedStrategyId.value || !items.some((strategy) => strategy.id === selectedStrategyId.value)) {
    selectedStrategyId.value = items[0]?.id;
  }
}

function onSampleStarted(_sample: SeedCaptureSample) {
  reloadKey.value += 1;
  activeTab.value = 'samples';
}

function openTemplate(templateId: number) {
  emit('openTemplate', templateId);
}
</script>
