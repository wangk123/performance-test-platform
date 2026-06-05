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
          <el-input v-model="scriptKeyword" class="compact-search" clearable placeholder="搜索脚本、接口、变量" />
          <el-button type="primary" @click="openScriptImportDialog">导入 JMX</el-button>
        </div>
      </div>

      <div class="script-asset-list">
        <div
          v-for="script in filteredScriptAssets"
          :key="script.id"
          class="script-asset-row"
          :class="{ active: selectedScriptAsset?.id === script.id }"
          role="button"
          tabindex="0"
          @click="selectScript(script)"
          @keydown.enter="selectScript(script)"
        >
          <span class="asset-status" :class="script.parseStatus.toLowerCase()">{{ parseStatusText(script.parseStatus) }}</span>
          <div>
            <strong>{{ script.name }}</strong>
            <small>{{ script.threadGroups.length }} 线程组 · {{ script.apis.length }} API · {{ script.monitors.length }} 监控 · v{{ script.latestVersion }}</small>
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
            <el-button size="small" type="danger" plain @click.stop="deleteScriptAsset(script)">删除</el-button>
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
          <el-button type="primary" @click="openParamDrawer(selectedScriptAsset)">默认参数</el-button>
        </div>
        <p class="detail-description">
          来源 {{ selectedScriptAsset.sourceFile }}，当前 v{{ selectedScriptAsset.latestVersion }}，{{ selectedScriptAsset.remark || '暂无备注' }}。
        </p>

        <div class="parsed-section">
          <h3>线程组</h3>
          <div class="parsed-table">
            <div v-for="group in selectedScriptAsset.threadGroups" :key="group.name">
              <strong>{{ group.name }}</strong>
              <span>{{ group.threads }} 线程 / Ramp-Up {{ group.rampUp }}s / 循环 {{ group.loops }} / {{ group.duration }}s</span>
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
import { formatDate, parseStatusText } from '../../utils/format';
import { useScriptEditor } from '../../composables/useScriptEditor';
import { useScriptImport } from '../../composables/useScriptImport';
import { useScriptParams } from '../../composables/useScriptParams';
import { useWorkspace } from '../../composables/useWorkspace';
import type { ScriptAsset } from '../../types';

const editor = useScriptEditor();
const { openScriptImportDialog } = useScriptImport();
const { openParamDrawer } = useScriptParams();
const {
  scriptKeyword,
  filteredScriptAssets,
  selectedScriptAsset,
  selectedScriptId,
  currentProject,
  deleteScriptAsset,
} = useWorkspace();

function selectScript(script: ScriptAsset) {
  selectedScriptId.value = script.id;
}
</script>
