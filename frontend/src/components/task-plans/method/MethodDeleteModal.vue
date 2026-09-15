<template>
  <a-modal
    :open="open"
    :title="`删除执行记录 #${row?.executionId ?? ''}`"
    :confirm-loading="busy"
    :ok-text="mode === 'hide' ? '确认移出' : '确认删除'"
    :ok-button-props="{ danger: mode === 'purge' }"
    cancel-text="取消"
    @ok="confirm"
    @cancel="close"
  >
    <p class="del-sub">{{ row ? `${scenarioNo} ${scenarioName} · ${row.threads} 并发 · ${startedAtText} · 选择处理方式：` : '' }}</p>
    <label class="del-opt" :class="{ sel: mode === 'hide' }">
      <input type="radio" name="method-del-opt" :checked="mode === 'hide'" @change="mode = 'hide'">
      <span><span class="t">仅从表格移出</span><br><span class="d">执行数据保留：详情页「历史记录」仍可见，报告聚合不受影响，可随时恢复展示。</span></span>
    </label>
    <label class="del-opt danger-opt" :class="{ sel: mode === 'purge' }">
      <input type="radio" name="method-del-opt" :checked="mode === 'purge'" @change="mode = 'purge'">
      <span><span class="t">彻底删除</span><br><span class="d">物理删除执行记录、聚合数据及挂在该记录名下的补充截图——报告页对应数据将一并消失，不可恢复。</span></span>
    </label>
  </a-modal>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue';
import { message } from 'ant-design-vue';
import type { MethodExecutionRow } from '../../../api/plan-method';
import { deleteExecutionsApi } from '../../../api/task-plans';
import { setExecutionVisibilityApi } from '../../../api/plan-method';

const props = defineProps<{
  open: boolean;
  row: MethodExecutionRow | null;
  scenarioNo: string;
  scenarioName: string;
  /** 后端 yyyy-MM-dd HH:mm，弹窗副标题按原型取 MM-dd HH:mm。 */
  startedAtText: string;
}>();
const emit = defineEmits<{ (e: 'update:open', value: boolean): void; (e: 'done'): void }>();

const NON_TERMINAL = ['QUEUED', 'PENDING', 'RUNNING', 'STOPPING'];
const mode = ref<'hide' | 'purge'>('hide');
const busy = ref(false);

watch(() => props.open, (open) => {
  if (open) mode.value = 'hide';
});

function close() {
  emit('update:open', false);
}

async function confirm() {
  const row = props.row;
  if (!row || busy.value) return;
  if (NON_TERMINAL.includes(row.status)) {
    message.warning('运行中的记录不能删除');
    close();
    return;
  }
  busy.value = true;
  try {
    if (mode.value === 'hide') {
      await setExecutionVisibilityApi(row.executionId, true);
      message.success('已从表格移出');
    } else {
      await deleteExecutionsApi([row.executionId]);
      message.success('记录已彻底删除');
    }
    close();
    emit('done');
  } catch (error) {
    message.error(error instanceof Error ? error.message : '操作失败');
  } finally {
    busy.value = false;
  }
}
</script>

<style scoped>
.del-sub {
  margin: 0 0 12px;
  color: var(--muted);
  font-size: 12.5px;
}

.del-opt {
  border: 1px solid var(--line);
  border-radius: 10px;
  padding: 10px 12px;
  margin-bottom: 9px;
  display: flex;
  gap: 10px;
  cursor: pointer;
  transition: border-color .12s;
}

.del-opt:hover { border-color: var(--line-strong); }
.del-opt.sel { border-color: var(--accent); background: var(--accent-soft); }
.del-opt.danger-opt.sel { border-color: var(--danger); background: var(--danger-soft); }
.del-opt input { margin-top: 3px; accent-color: var(--accent); }
.del-opt.danger-opt input { accent-color: var(--danger); }
.del-opt .t { font-size: 13px; font-weight: 600; }
.del-opt .d { font-size: 11.5px; color: var(--muted); }
.del-opt.danger-opt .t { color: var(--danger); }
</style>
