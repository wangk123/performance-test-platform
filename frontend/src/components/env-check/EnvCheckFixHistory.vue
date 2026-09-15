<template>
  <div class="ec-fix-history">
    <h4>修复历史</h4>
    <div v-for="fix in fixes" :key="fix.id" class="ec-diff-card">
      <div class="ec-diff-head">
        <span class="ec-fix-name">{{ itemLabel(fix.itemKey) }}</span>
        <span class="ec-host">{{ fix.host }}</span>
        <span class="ec-diff-meta">
          <span>{{ fix.appliedBy }} · {{ formatDate(fix.appliedAt) }}</span>
          <span v-if="fix.rolledBackAt" class="ec-badge na">已回滚</span>
          <span v-else class="ec-badge fixed">已修复</span>
          <a-button v-if="!fix.rolledBackAt" type="link" size="small" danger @click="rollback(fix)">回滚</a-button>
        </span>
      </div>
      <pre v-if="fix.diffText" class="ec-diff"><span
        v-for="(line, index) in diffLines(fix.diffText)"
        :key="index"
        :class="line.cls"
      >{{ line.text }}
</span></pre>
    </div>
  </div>
</template>

<script setup lang="ts">
import { message, Modal } from 'ant-design-vue';
import type { EnvCheckFixRecord } from '../../types';
import { rollbackEnvCheckFixApi } from '../../api/env-check';
import { formatDate } from '../../utils/format';
import { diffLines } from './envCheckResultModel';

const props = defineProps<{ fixes: EnvCheckFixRecord[]; itemLabel: (itemKey: string) => string }>();
const emit = defineEmits<{ (e: 'rolled-back'): void }>();

function rollback(fix: EnvCheckFixRecord) {
  Modal.confirm({
    title: '回滚该修复？',
    content: `「${props.itemLabel(fix.itemKey)} · ${fix.host}」将恢复备份原值，结果行退回「需处理」。`,
    okText: '回滚',
    okType: 'danger',
    cancelText: '取消',
    async onOk() {
      try {
        await rollbackEnvCheckFixApi(fix.id);
        message.success('已回滚');
        emit('rolled-back');
      } catch (error) {
        message.error(error instanceof Error ? error.message : '回滚失败');
        throw error;
      }
    },
  });
}
</script>

<style scoped>
.ec-fix-history {
  margin-top: 16px;
}
.ec-fix-history h4 {
  font-size: 13.5px;
  font-weight: 700;
  margin: 0 0 10px;
}
.ec-diff-card {
  border: 1px solid var(--line);
  border-radius: 10px;
  overflow: hidden;
}
.ec-diff-card + .ec-diff-card {
  margin-top: 10px;
}
.ec-diff-head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 14px;
  background: var(--surface-soft);
  border-bottom: 1px solid var(--line);
  font-size: 12.5px;
}
.ec-fix-name {
  font-size: 13px;
  font-weight: 600;
}
.ec-host {
  font-family: var(--font-data);
  font-size: 12px;
}
.ec-diff-meta {
  margin-left: auto;
  color: var(--muted);
  font-size: 11.5px;
  display: flex;
  gap: 10px;
  align-items: center;
}
.ec-badge.fixed {
  color: var(--accent);
  background: var(--accent-soft);
}
.ec-badge.na {
  color: var(--muted);
  background: transparent;
  border: 1px dashed var(--line-strong);
}
.ec-badge {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  border-radius: 6px;
  font-size: 11.5px;
  font-weight: 600;
  padding: 1px 8px;
  line-height: 20px;
}
.ec-diff {
  font-family: var(--font-data);
  font-size: 11.5px;
  line-height: 1.7;
  padding: 10px 14px;
  overflow-x: auto;
  color: var(--ink);
  margin: 0;
}
.ec-diff .del {
  color: var(--danger);
  background: var(--danger-soft);
  display: block;
  border-radius: 3px;
}
.ec-diff .add {
  color: var(--ok);
  background: var(--ok-soft);
  display: block;
  border-radius: 3px;
}
.ec-diff .ctx {
  color: var(--muted);
  display: block;
}
</style>
