<template>
  <section class="script-workspace">
    <div class="panel script-assets-panel">
      <div class="panel-header">
        <div>
          <span class="eyebrow">Module 03</span>
          <h2>平台脚本资产</h2>
          <p>{{ currentProject?.name }} 下可用于任务计划的脚本，导入仅作为追加或更新资产的入口。</p>
        </div>
        <div class="script-assets-actions">
          <a-input v-model:value="scriptKeyword" class="compact-search" allow-clear placeholder="搜索脚本、接口、变量" />
          <a-button :disabled="selectedRows.length === 0" danger @click="deleteSelectedScripts">批量删除</a-button>
          <a-button type="primary" @click="openScriptImportDialog">导入 JMX</a-button>
        </div>
      </div>

      <div class="script-asset-list">
        <div v-if="filteredScriptAssets.length" class="script-asset-head">
          <span>
            <a-checkbox
              :checked="allVisibleSelected"
              :indeterminate="partVisibleSelected"
              @update:checked="toggleAllVisible"
            />
          </span>
          <span>状态</span>
          <span>脚本</span>
          <span>更新时间</span>
          <span>操作</span>
        </div>
        <div
          v-for="script in filteredScriptAssets"
          :key="script.id"
          class="script-asset-row"
          :class="{ active: selectedScriptAsset?.id === script.id, checked: selectedScriptIds.includes(script.id) }"
          role="button"
          tabindex="0"
          @click="selectScript(script)"
          @keydown.enter="selectScript(script)"
        >
          <span @click.stop>
            <a-checkbox
              :checked="selectedScriptIds.includes(script.id)"
              @update:checked="toggleScriptSelection(script.id)"
            />
          </span>
          <span class="asset-status" :class="scriptStatus(script).tone">{{ scriptStatus(script).label }}</span>
          <div>
            <strong>{{ script.name }}</strong>
            <small>{{ getThreadGroupCount(script) }} 线程组 · {{ script.apis.length }} API · {{ scriptStatus(script).reason }} · v{{ script.latestVersion }}</small>
          </div>
          <span>{{ formatDate(script.updatedAt) }}</span>
          <div class="asset-row-actions">
            <a
              class="asset-link-button"
              :href="editor.scriptEditorUrl(script)"
              target="_blank"
              rel="noopener"
              @click.stop="editor.ensureScriptSteps(script)"
            >编辑</a>
            <a-button
              size="small"
              type="primary"

              :disabled="!scriptStatus(script).executable"
              @click.stop="runScriptAsset(script)"
            >执行</a-button>
            <a-button size="small" danger @click.stop="deleteScriptAsset(script)">删除</a-button>
          </div>
        </div>
        <div v-if="filteredScriptAssets.length === 0" class="empty-inline">
          <strong>暂无匹配脚本</strong>
          <span>调整搜索条件，或导入新的 JMX 脚本资产。</span>
        </div>
      </div>
    </div>

    <aside class="panel parsed-detail-panel">
      <template v-if="selectedScriptAsset">
        <div class="detail-heading">
          <div>
            <span class="eyebrow">Parsed Script</span>
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
  </section>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { formatDate } from '../../utils/format';
import { useScriptEditor } from '../../composables/useScriptEditor';
import { useScriptImport } from '../../composables/useScriptImport';
import { useScriptParams } from '../../composables/useScriptParams';
import { useTaskSchedule } from '../../composables/useTaskSchedule';
import { useThreadGroups } from '../../composables/useThreadGroups';
import { useWorkspace } from '../../composables/useWorkspace';
import type { ScriptAsset, ThreadGroup } from '../../types';
import { scriptExecutableStatus } from '../../utils/script-status';

const editor = useScriptEditor();
const { openScriptImportDialog } = useScriptImport();
const { openParamDrawer } = useScriptParams();
const { runScriptAsset } = useTaskSchedule();
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
const allVisibleSelected = computed(() =>
  filteredScriptAssets.value.length > 0 && filteredScriptAssets.value.every((script) => selectedScriptIds.value.includes(script.id)),
);
const partVisibleSelected = computed(() =>
  filteredScriptAssets.value.some((script) => selectedScriptIds.value.includes(script.id)) && !allVisibleSelected.value,
);

watch(filteredScriptAssets, (items) => {
  const visibleIds = new Set(items.map((script) => script.id));
  selectedScriptIds.value = selectedScriptIds.value.filter((id) => visibleIds.has(id));
});

function selectScript(script: ScriptAsset) {
  selectedScriptId.value = script.id;
}

function toggleAllVisible(value: string | number | boolean) {
  selectedScriptIds.value = value ? filteredScriptAssets.value.map((script) => script.id) : [];
}

function toggleScriptSelection(id: number) {
  selectedScriptIds.value = selectedScriptIds.value.includes(id)
    ? selectedScriptIds.value.filter((item) => item !== id)
    : [...selectedScriptIds.value, id];
}

async function deleteSelectedScripts() {
  if (await deleteScriptAssets(selectedRows.value)) {
    selectedScriptIds.value = [];
  }
}

function scriptStatus(script: ScriptAsset) {
  return scriptExecutableStatus(script);
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
