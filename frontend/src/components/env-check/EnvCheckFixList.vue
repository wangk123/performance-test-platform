<template>
  <div class="ec-fix-panel">
    <div class="ec-fix-head">
      <span class="ec-fix-title">问题清单{{ fixEnabled ? ' · 勾选后批量修复' : ' · 修复已被平台关闭（只读）' }}</span>
      <span v-if="fixEnabled" class="ec-fix-hint">低风险默认勾选 · 中风险手动勾选 · 高风险勾选时单独确认</span>
      <span v-if="fixEnabled" class="ec-fix-ops">
        <a-button size="small" @click="toggleSelectAll">{{ allSelected ? '全不选' : '一键全选' }}</a-button>
        <a-button type="primary" size="small" :disabled="!selectedKeys.length" :loading="fixing" @click="apply">
          {{ selectedKeys.length ? `执行修复（已选 ${selectedKeys.length} 项）` : '执行修复' }}
        </a-button>
      </span>
    </div>
    <label v-for="row in rows" :key="row.rowKey" class="ec-fix-row">
      <a-checkbox
        v-if="fixEnabled"
        :checked="selectedKeys.includes(row.rowKey)"
        :aria-label="`${row.label}-${row.host}`"
        @change="toggleIssue(row, $event.target.checked)"
      />
      <div class="ec-fix-body">
        <div class="ec-fix-l1">
          <span class="ec-fix-name">{{ row.label }}</span>
          <span class="ec-host">{{ row.host }}</span>
          <span class="ec-risk-chip" :class="row.risk.toLowerCase()">{{ RISK_SHORT[row.risk] }}</span>
        </div>
        <div class="ec-fix-why">{{ row.detail || row.suggestion }}</div>
        <div v-if="row.method" class="ec-fix-how">{{ row.method }}</div>
      </div>
    </label>
  </div>
</template>

<script setup lang="ts">
import { computed, h, ref, watch } from 'vue';
import { Modal } from 'ant-design-vue';
import { fixRequestOf, type EnvCheckIssueRow } from './envCheckResultModel';

const props = defineProps<{ rows: EnvCheckIssueRow[]; fixing: boolean; fixEnabled: boolean }>();
const emit = defineEmits<{ (e: 'apply', requests: Array<{ host: string; itemKey: string }>): void }>();

const RISK_SHORT = { LOW: '低', MEDIUM: '中', HIGH: '高' } as const;

const selectedKeys = ref<string[]>([]);
const allSelected = computed(() => props.rows.length > 0 && selectedKeys.value.length === props.rows.length);

/** 勾选态初始化：换 run / 刷新详情后重置，仅低风险默认勾选（spec §4.5）。 */
watch(
  () => props.rows,
  (rows) => {
    selectedKeys.value = rows.filter((row) => row.risk === 'LOW').map((row) => row.rowKey);
  },
  { immediate: true },
);

function toggleIssue(row: EnvCheckIssueRow, checked: boolean) {
  if (!checked) {
    selectedKeys.value = selectedKeys.value.filter((key) => key !== row.rowKey);
    return;
  }
  if (row.risk === 'HIGH') {
    Modal.confirm({
      title: '将该高风险项加入修复？',
      icon: warnIcon('danger'),
      content: `「${row.label} · ${row.host}」该操作影响目标机配置，确认加入本次修复清单？`,
      okText: '确认加入',
      okType: 'danger',
      cancelText: '取消',
      onOk: () => {
        selectedKeys.value = [...selectedKeys.value, row.rowKey];
      },
    });
    return;
  }
  selectedKeys.value = [...selectedKeys.value, row.rowKey];
}

function toggleSelectAll() {
  if (allSelected.value) {
    selectedKeys.value = [];
    return;
  }
  const risky = props.rows.filter((row) => row.risk !== 'LOW');
  const commit = () => {
    selectedKeys.value = props.rows.map((row) => row.rowKey);
  };
  if (!risky.length) {
    commit();
    return;
  }
  const highItems = risky.filter((row) => row.risk === 'HIGH').map((row) => row.label);
  Modal.confirm({
    title: `全选将包含 ${risky.length} 项中/高风险修复${highItems.length ? `，其中高风险 ${highItems.length} 项：${highItems.join('、')}` : ''}`,
    icon: warnIcon('orange'),
    content: '修复前逐项备份、修后自动复查，全部可回滚。确认全选？',
    okText: '确认全选',
    cancelText: '取消',
    onOk: commit,
  });
}

function apply() {
  emit('apply', selectedKeys.value.map(fixRequestOf));
}

/** Modal.confirm 警示图标：危险操作（高风险勾选/缺凭据）红色、批量确认橙色，恢复原型红/橙分级。 */
function warnIcon(tone: 'danger' | 'orange') {
  return h('span', { class: `ec-warn-icon ${tone}`, role: 'img', 'aria-label': '警告' }, '!');
}
</script>

<style scoped>
.ec-fix-panel {
  border: 1px solid var(--line);
  border-radius: 10px;
  margin-top: 16px;
  overflow: hidden;
}
.ec-fix-head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  background: var(--surface-soft);
  border-bottom: 1px solid var(--line);
}
.ec-fix-title {
  font-size: 13px;
  font-weight: 700;
}
.ec-fix-hint {
  color: var(--muted);
  font-size: 11.5px;
}
.ec-fix-ops {
  margin-left: auto;
  display: flex;
  gap: 8px;
  align-items: center;
}
.ec-fix-row {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 10px 14px;
  border-bottom: 1px solid var(--line);
  cursor: pointer;
}
.ec-fix-row:last-child {
  border-bottom: none;
}
.ec-fix-body {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}
.ec-fix-l1 {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.ec-fix-name {
  font-size: 13px;
  font-weight: 600;
}
.ec-host {
  font-family: var(--font-data);
  font-size: 12px;
}
.ec-risk-chip {
  font: 600 10.5px var(--font-ui);
  border-radius: 5px;
  padding: 0 7px;
  line-height: 18px;
}
.ec-risk-chip.low {
  color: var(--warn);
  background: var(--warning-soft);
}
.ec-risk-chip.medium {
  color: var(--orange);
  background: var(--orange-soft);
}
.ec-risk-chip.high {
  color: var(--danger);
  background: var(--danger-soft);
}
.ec-fix-why {
  color: var(--muted);
  font-size: 11.5px;
}
.ec-fix-how {
  font-size: 11.5px;
  color: var(--muted);
}
</style>
