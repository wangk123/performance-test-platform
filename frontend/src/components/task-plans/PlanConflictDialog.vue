<template>
  <a-modal
    :open="open"
    title="检测到编辑冲突 · 三选一处理"
    width="960px"
    :footer="null"
    @cancel="$emit('update:open', false)"
  >
    <div class="conflict-note">
      <span class="conflict-note-icon">⚠</span>
      <span>你的本地草稿基于较旧 revision，服务器当前已是 revision <b>{{ serverRevision }}</b>。
      平台不做自动覆盖与行级合并，请选择处理方式。</span>
    </div>

    <div class="conflict-columns">
      <div class="diff-col">
        <div class="diff-head">
          <b>平台当前版</b>
          <span class="diff-badge platform">服务器最新</span>
        </div>
        <div class="diff-body">
          <div v-for="(part, index) in serverParts" :key="index" class="diff-line" :class="part.kind">
            {{ part.text || ' ' }}
          </div>
        </div>
      </div>
      <div class="diff-col">
        <div class="diff-head">
          <b>本地草稿</b>
          <span class="diff-badge local">你的修改</span>
        </div>
        <div class="diff-body">
          <div v-for="(part, index) in localParts" :key="index" class="diff-line" :class="part.kind">
            {{ part.text || ' ' }}
          </div>
        </div>
      </div>
    </div>

    <div class="legend">
      <span><span class="swatch del" />平台相对本地的删除</span>
      <span><span class="swatch add" />平台相对本地的新增</span>
      <span><span class="swatch add local" />本地相对平台的新增</span>
    </div>

    <div class="conflict-actions">
      <a-button @click="$emit('resolve', 'keep-server')">保留平台版（放弃本地修改）</a-button>
      <a-button type="primary" @click="$emit('resolve', 'take-local')">采纳本地版（以最新 revision 重放）</a-button>
      <a-button @click="$emit('resolve', 'manual')">手改（以平台版为基底继续编辑）</a-button>
      <a-button type="text" @click="$emit('update:open', false)">取消</a-button>
    </div>
    <p class="conflict-footnote">
      采纳本地版 = 整篇原文覆盖（非行级合并），以服务器最新 revision 重新提交。
    </p>
  </a-modal>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { diffLines } from 'diff';

const props = defineProps<{
  open: boolean;
  serverMarkdown: string;
  localMarkdown: string;
  serverRevision?: number;
}>();
defineEmits<{
  (e: 'update:open', value: boolean): void;
  (e: 'resolve', kind: 'keep-server' | 'take-local' | 'manual'): void;
}>();

type DiffPart = { text: string; kind: 'added' | 'removed' | 'same' };

/** from → to 的差异行（added = to 有 from 无）。 */
function diffParts(from: string, to: string): DiffPart[] {
  return diffLines(from, to).flatMap((part) =>
    part.value
      .replace(/\n$/, '')
      .split('\n')
      .filter((line) => line.trim().length > 0)
      .map((text) => ({ text, kind: part.added ? 'added' : part.removed ? 'removed' : 'same' })),
  );
}

/** 平台栏：本地 → 平台（removed=平台删除 danger，added=平台新增 ok）。 */
const serverParts = computed(() => diffParts(props.localMarkdown, props.serverMarkdown));
/** 本地栏：平台 → 本地（added=本地独有 ok）。 */
const localParts = computed(() => diffParts(props.serverMarkdown, props.localMarkdown));

const serverRevision = computed(() => props.serverRevision ?? '最新');
</script>

<style scoped>
/* 规格：plan-document-prototype.html .conflict-note / .diff-layout / .legend（色走 token） */
.conflict-note {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  margin-bottom: 16px;
  padding: 12px 16px;
  border: 1px solid var(--warn);
  border-radius: 10px;
  background: var(--warning-soft);
  color: var(--warn);
  font-size: 13px;
}

.conflict-note b {
  font-family: var(--font-data);
}

.conflict-note-icon {
  flex: none;
  font-size: 15px;
}

.conflict-columns {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.diff-col {
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: var(--surface);
}

.diff-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px;
  background: var(--canvas);
  border-bottom: 1px solid var(--line);
  font-size: 13px;
}

.diff-badge {
  padding: 1px 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
}

.diff-badge.platform {
  background: var(--ok-soft);
  color: var(--ok);
}

.diff-badge.local {
  background: var(--canvas);
  border: 1px solid var(--line);
  color: var(--muted);
}

.diff-body {
  max-height: 420px;
  overflow-y: auto;
  padding: 12px 14px;
  font-size: 13px;
  line-height: 1.7;
}

.diff-line {
  padding: 1px 6px;
  border-radius: 4px;
  font-family: var(--font-data);
  font-size: 12.5px;
  white-space: pre-wrap;
  word-break: break-all;
}

.diff-line.added {
  background: var(--ok-soft);
  color: var(--ok);
}

.diff-line.removed {
  background: var(--danger-soft);
  color: var(--danger);
  text-decoration: line-through;
}

.legend {
  display: flex;
  justify-content: center;
  gap: 16px;
  margin-top: 12px;
  color: var(--muted);
  font-size: 12px;
}

.legend .swatch {
  display: inline-block;
  width: 10px;
  height: 10px;
  margin-right: 5px;
  border-radius: 2px;
  vertical-align: -1px;
}

.legend .swatch.del {
  border: 1px solid var(--danger);
  background: var(--danger-soft);
}

.legend .swatch.add {
  border: 1px solid var(--ok);
  background: var(--ok-soft);
}

.legend .swatch.add.local {
  opacity: 0.75;
}

.conflict-actions {
  display: flex;
  justify-content: center;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  padding: 16px 0 4px;
}

.conflict-footnote {
  margin: 8px 0 0;
  color: var(--muted);
  font-size: 12px;
  text-align: center;
}
</style>
