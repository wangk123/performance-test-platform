<template>
  <section class="script-workspace-page">
    <div class="page-head">
      <div>
        <h1>脚本管理</h1>
        <p>{{ currentProject?.name }} 下可用于任务计划的脚本，支持新建空白脚本或导入 JMX 资产。</p>
      </div>
      <div class="script-assets-actions">
        <a-input v-model:value="scriptKeyword" class="compact-search" allow-clear placeholder="搜索脚本、接口、变量" />
        <a-button :disabled="selectedRows.length === 0" danger @click="deleteSelectedScripts">批量删除</a-button>
        <a-button @click="openScriptCreateDialog">新建脚本</a-button>
        <a-button type="primary" @click="openScriptImportDialog">导入 JMX</a-button>
      </div>
    </div>

    <div class="script-workspace">
    <div class="panel script-assets-panel">
      <a-table
        class="workspace-table"
        :columns="scriptColumns"
        :data-source="filteredScriptAssets"
        :pagination="false"
        :row-key="(record: ScriptAsset) => record.id"
        :row-selection="scriptRowSelection"
        :custom-row="scriptRowEvents"
        :row-class-name="scriptRowClassName"
        :scroll="{ x: 1120 }"
        :locale="{ emptyText: '暂无匹配脚本，可新建空白脚本或导入 JMX 脚本资产。' }"
      >
        <template #bodyCell="{ column, record, index }">
          <template v-if="column.key === 'index'">{{ index + 1 }}</template>
          <template v-else-if="column.key === 'script'">
            <div class="table-main-cell">
              <strong>{{ record.name }}</strong>
              <small>{{ scriptSummary(record) }}</small>
            </div>
          </template>
          <template v-else-if="column.key === 'type'">{{ scriptType(record) }}</template>
          <template v-else-if="column.key === 'status'">
            <span class="asset-status" :class="scriptStatus(record).tone">{{ scriptStatus(record).label }}</span>
          </template>
          <template v-else-if="column.key === 'updatedAt'">{{ formatDate(record.updatedAt) }}</template>
          <template v-else-if="column.key === 'updatedBy'">{{ latestVersionRecord(record)?.importedBy ?? '-' }}</template>
          <template v-else-if="column.key === 'actions'">
            <div class="asset-row-actions">
              <a-button
                size="small"
                :href="editor.scriptEditorUrl(record)"
                target="_blank"
                rel="noopener"
                @click.stop="editor.ensureScriptSteps(record)"
              >编辑</a-button>
              <a-button
                size="small"
                type="primary"
                :disabled="!scriptStatus(record).executable"
                @click.stop="runScriptAsset(record)"
              >执行</a-button>
              <a-button size="small" danger @click.stop="deleteScriptAsset(record)">删除</a-button>
            </div>
          </template>
        </template>
      </a-table>
    </div>

    <aside class="panel parsed-detail-panel">
      <template v-if="selectedScriptAsset">
        <div class="detail-heading">
          <div>
            <h2>{{ selectedScriptAsset.name }}</h2>
          </div>
          <a-button type="primary" @click="openParamDrawer(selectedScriptAsset)">默认参数</a-button>
        </div>
        <p class="detail-description">
          来源 {{ selectedScriptAsset.sourceFile }}，当前 v{{ selectedScriptAsset.latestVersion }}，{{ selectedScriptAsset.remark || '暂无备注' }}。
        </p>

        <div class="parsed-section">
          <h3>线程组</h3>
          <div class="parsed-table">
            <div v-for="group in getThreadGroups(selectedScriptAsset)" :key="group.name">
              <strong>{{ group.name }}</strong>
              <span>{{ threadGroupSummary(group) }}</span>
            </div>
          </div>
        </div>

        <div class="parsed-section">
          <h3>API 配置</h3>
          <div class="api-list">
            <span v-for="api in selectedScriptAsset.apis" :key="`${api.method}-${api.path}`">
              {{ api.method }} {{ api.path }}
            </span>
          </div>
        </div>

        <div class="parsed-section">
          <h3>监控配置</h3>
          <div class="api-list">
            <span v-for="monitor in selectedScriptAsset.monitors" :key="monitor.target">
              {{ monitor.target }} · {{ monitor.metrics.join('/') }}
            </span>
          </div>
        </div>

        <div class="parsed-section">
          <h3>变量与默认参数</h3>
          <div class="param-chips">
            <span v-for="variable in selectedScriptAsset.variables" :key="variable.key">
              {{ variable.key }}={{ variable.value }}
            </span>
            <span v-for="param in selectedScriptAsset.params" :key="param.key">
              {{ param.label }}：{{ param.value }}
            </span>
          </div>
        </div>

        <div class="parsed-section">
          <h3>版本记录</h3>
          <div class="version-timeline">
            <div v-for="version in selectedScriptAsset.versions" :key="version.versionNo">
              <strong>v{{ version.versionNo }}</strong>
              <span>{{ version.fileName }} · {{ formatDate(version.importedAt) }}</span>
            </div>
          </div>
        </div>
      </template>
      <div v-else class="empty-detail">
        <h2>选择脚本资产</h2>
        <p>右侧展示解析后的线程组、接口、监控和参数，而不是上传文件列表。</p>
      </div>
    </aside>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import type { TableColumnsType } from 'ant-design-vue';
import { formatDate } from '../../utils/format';
import { useScriptEditor } from '../../composables/useScriptEditor';
import { useScriptImport } from '../../composables/useScriptImport';
import { useScriptCreate } from '../../composables/useScriptCreate';
import { useScriptParams } from '../../composables/useScriptParams';
import { useTaskPlans } from '../../composables/useTaskPlans';
import { useThreadGroups } from '../../composables/useThreadGroups';
import { useWorkspace } from '../../composables/useWorkspace';
import type { ScriptAsset, ThreadGroup } from '../../types';
import { scriptExecutableStatus } from '../../utils/script-status';

const editor = useScriptEditor();
const { openScriptImportDialog } = useScriptImport();
const { openScriptCreateDialog } = useScriptCreate();
const { openParamDrawer } = useScriptParams();
const { runScriptAsset } = useTaskPlans();
const selectedScriptIds = ref<number[]>([]);
const {
  scriptKeyword,
  filteredScriptAssets,
  selectedScriptAsset,
  selectedScriptId,
  currentProject,
  deleteScriptAsset,
  deleteScriptAssets,
} = useWorkspace();

const selectedRows = computed(() =>
  filteredScriptAssets.value.filter((script) => selectedScriptIds.value.includes(script.id)),
);
const scriptColumns: TableColumnsType<ScriptAsset> = [
  { title: '序号', key: 'index', width: 72, align: 'center' },
  { title: '脚本', key: 'script', width: 360 },
  { title: '类型', key: 'type', width: 90 },
  { title: '状态', key: 'status', width: 104 },
  { title: '更新时间', dataIndex: 'updatedAt', key: 'updatedAt', width: 132 },
  { title: '更新人', key: 'updatedBy', width: 100 },
  { title: '操作栏', key: 'actions', width: 210 },
];
const scriptRowSelection = computed(() => ({
  selectedRowKeys: selectedScriptIds.value,
  onChange: (keys: (string | number)[]) => {
    selectedScriptIds.value = keys.map(Number);
  },
}));

watch(filteredScriptAssets, (items) => {
  const visibleIds = new Set(items.map((script) => script.id));
  selectedScriptIds.value = selectedScriptIds.value.filter((id) => visibleIds.has(id));
});

function selectScript(script: ScriptAsset) {
  selectedScriptId.value = script.id;
}

async function deleteSelectedScripts() {
  if (await deleteScriptAssets(selectedRows.value)) {
    selectedScriptIds.value = [];
  }
}

function scriptRowEvents(record: ScriptAsset) {
  return {
    onClick: () => selectScript(record),
  };
}

function scriptRowClassName(record: ScriptAsset) {
  return selectedScriptAsset.value?.id === record.id ? 'selected-table-row' : '';
}

function scriptStatus(script: ScriptAsset) {
  return scriptExecutableStatus(script);
}

function scriptType(script: ScriptAsset) {
  return script.sourceFile.toLowerCase().endsWith('.jmx') ? 'JMX' : '脚本';
}

function latestVersionRecord(script: ScriptAsset) {
  return [...script.versions].sort((a, b) => b.versionNo - a.versionNo)[0] ?? null;
}

function scriptSummary(script: ScriptAsset) {
  return `${script.sourceFile} · ${getThreadGroupCount(script)} 线程组 · ${script.apis.length} API · ${scriptStatus(script).reason} · v${script.latestVersion}`;
}

function getThreadGroups(script: ScriptAsset): ThreadGroup[] {
  return useThreadGroups(() => script.steps).threadGroups.value;
}

function getThreadGroupCount(script: ScriptAsset): number {
  return useThreadGroups(() => script.steps).threadGroupCount.value;
}

function threadGroupSummary(group: ThreadGroup) {
  if (group.mode === 'stepping') {
    return `${group.threads} 线程 / 阶梯加压 / 每阶 ${group.stepping?.startUsersCount ?? '-'} 用户 / 保持 ${group.stepping?.flightTime ?? '-'}s`;
  }
  if (group.mode === 'duration' || group.scheduler) {
    return `${group.threads} 线程 / Ramp-Up ${group.rampUp}s / 持续 ${group.duration}s`;
  }
  return `${group.threads} 线程 / Ramp-Up ${group.rampUp}s / 循环 ${group.loops} 次`;
}
</script>
