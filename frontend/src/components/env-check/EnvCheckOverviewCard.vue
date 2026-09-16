<template>
  <section class="ec-card">
    <header class="ec-card-head">
      <div>
        <h3>检查总览</h3>
        <p>目标清单来自文档「环境部署信息」（只读）；勾选检查项并核对凭据后执行</p>
      </div>
      <div class="eco-head-actions">
        <a-switch v-model:checked="enabled" :loading="saving" aria-label="启用环境检查" />
        <span class="eco-switch-label">启用环境检查</span>
        <a-button type="primary" size="small" :loading="saving" @click="save">保存设置</a-button>
      </div>
    </header>

    <div class="eco-targets">
      <div class="eco-src-hint">
        检查目标实时解析自计划文档「五、测试资源 → 环境部署信息」（{{ targets.total }} 台），修改部署表后刷新生效，平台侧不单独维护
      </div>
      <a-table
        v-if="targets.targets.length"
        :columns="targetColumns"
        :data-source="targets.targets"
        :pagination="false"
        :loading="targetsLoading"
        row-key="host"
        size="small"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'host'">
            <span class="ec-host">{{ record.host }}</span>
          </template>
          <template v-else-if="column.key === 'credential'">
            <span class="eco-cred-chip" :class="credentialChip(record.credential).level">
              {{ credentialChip(record.credential).text }}
            </span>
          </template>
          <template v-else-if="column.key === 'applicable'">
            {{ record.applicableRemoteItems }} / {{ remoteItemCount }}
          </template>
        </template>
      </a-table>
      <a-empty
        v-else
        :image-style="{ height: '48px' }"
        description="文档「五、测试资源 → 环境部署信息」未识别到目标机，请先在文档中补充部署表"
      />
    </div>

    <a-spin :spinning="loading">
      <div v-for="group in groups" :key="group.category" class="ec-item-group">
        <div class="ec-group-head">
          <span>{{ group.categoryLabel }}</span>
          <span class="ec-kind-tag">{{ group.kind }}</span>
          <span class="ec-cnt">{{ selectedCountText(group.items, selected) }}</span>
        </div>
        <label v-for="entry in group.items" :key="entry.key" class="ec-item-row">
          <a-checkbox
            :checked="selected.includes(entry.key)"
            :aria-label="entry.label"
            @change="toggleItem(entry.key, $event.target.checked)"
          />
          <span class="ec-name">{{ entry.label }}</span>
          <span class="ec-desc">{{ entry.description }}</span>
          <span class="ec-meta">
            <span v-if="riskChip(entry)" class="ec-risk-chip" :class="riskChip(entry)!.level">{{ riskChip(entry)!.text }}</span>
            <span v-else class="ec-kind-chip">{{ kindChip(entry) }}</span>
          </span>
        </label>
      </div>
      <p v-if="!loading && !groups.length" class="ec-empty">检查项清单为空</p>
    </a-spin>
  </section>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { message } from 'ant-design-vue';
import type { TableColumnsType } from 'ant-design-vue';
import type { EnvCheckItemMeta, EnvCheckTargetView, EnvCheckTargetsPreview } from '../../types';
import { updatePrecheckSettingsApi } from '../../api/plan-doc';
import { credentialChip, groupEnvCheckItems, kindChip, parsePrecheckSettings, riskChip, selectedCountText } from './envCheckSettings';

const props = defineProps<{
  planId: number;
  precheckJson: string | null;
  items: EnvCheckItemMeta[];
  loading: boolean;
  targets: EnvCheckTargetsPreview;
  targetsLoading: boolean;
}>();
const emit = defineEmits<{ (e: 'saved'): void }>();

const enabled = ref(false);
const selected = ref<string[]>([]);
const saving = ref(false);

const groups = computed(() => groupEnvCheckItems(props.items));

/** 注册表远程项总数：目标表「适用远程检查项」分母（分子由后端随 targets 重取刷新）。 */
const remoteItemCount = computed(() => props.items.filter((entry) => entry.kind === 'REMOTE').length);

const targetColumns: TableColumnsType<EnvCheckTargetView> = [
  { title: '地址', dataIndex: 'host', key: 'host', width: 140 },
  { title: '模块', dataIndex: 'module', key: 'module' },
  { title: '凭据', key: 'credential', width: 120 },
  { title: '适用远程检查项', key: 'applicable', width: 130 },
];

/** precheckJson 外部刷新（保存后 doc.refresh 回流）或清单首次到达时重放初始勾选态。 */
function applyPrecheck() {
  const parsed = parsePrecheckSettings(
    props.precheckJson,
    props.items.map((entry) => entry.key),
  );
  enabled.value = parsed.enabled;
  selected.value = parsed.items;
}

function toggleItem(key: string, checked: boolean) {
  selected.value = checked ? [...selected.value, key] : selected.value.filter((entry) => entry !== key);
}

async function save() {
  saving.value = true;
  try {
    await updatePrecheckSettingsApi(props.planId, { enabled: enabled.value, items: [...selected.value] });
    message.success('环境检查设置已保存');
    emit('saved');
  } catch (error) {
    message.error(error instanceof Error ? error.message : '保存失败');
  } finally {
    saving.value = false;
  }
}

watch([() => props.precheckJson, () => props.items], applyPrecheck, { immediate: true });
</script>

<style scoped>
.eco-head-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex: none;
}
.eco-switch-label {
  font-size: 13px;
  font-weight: 600;
}
.eco-targets {
  margin-bottom: 14px;
}
.eco-src-hint {
  font-size: 12px;
  color: var(--muted);
  padding: 8px 12px;
  border: 1px dashed var(--line-strong);
  border-radius: 8px;
  margin-bottom: 12px;
}
.ec-host {
  font-family: var(--font-data);
  font-size: 12px;
}
.eco-cred-chip {
  font: 600 10.5px var(--font-ui);
  border-radius: 999px;
  padding: 1px 9px;
  line-height: 18px;
  white-space: nowrap;
}
.eco-cred-chip.ok {
  color: var(--ok);
  background: var(--ok-soft);
}
.eco-cred-chip.warn {
  color: var(--warn);
  background: var(--warning-soft);
}
.eco-cred-chip.danger {
  color: var(--danger);
  background: var(--danger-soft);
}
.ec-item-group {
  border: 1px solid var(--line);
  border-radius: 10px;
  overflow: hidden;
}
.ec-item-group + .ec-item-group {
  margin-top: 10px;
}
.ec-group-head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 9px 14px;
  background: var(--surface-soft);
  border-bottom: 1px solid var(--line);
  font-size: 12.5px;
  font-weight: 600;
  color: var(--muted);
}
.ec-kind-tag {
  font: 600 10.5px var(--font-data);
  color: var(--accent);
  background: var(--accent-soft);
  border-radius: 5px;
  padding: 0 6px;
}
.ec-cnt {
  margin-left: auto;
  font: 500 11.5px var(--font-data);
}
.ec-item-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 14px;
  border-bottom: 1px solid var(--line);
  cursor: pointer;
}
.ec-item-group .ec-item-row:last-child {
  border-bottom: none;
}
.ec-item-row:hover {
  background: var(--surface-soft);
}
.ec-name {
  font-size: 13px;
  font-weight: 500;
}
.ec-desc {
  color: var(--muted);
  font-size: 11.5px;
}
.ec-meta {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 6px;
  flex: none;
}
.ec-risk-chip,
.ec-kind-chip {
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
.ec-kind-chip {
  font: 500 10.5px var(--font-data);
  color: var(--muted);
  border: 1px solid var(--line);
  padding: 0 6px;
  line-height: 17px;
}
.ec-empty {
  color: var(--muted);
  font-size: 12.5px;
  text-align: center;
  padding: 16px 0;
  margin: 0;
}
</style>
