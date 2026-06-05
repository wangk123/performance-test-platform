<template>
  <el-dialog v-model="visible" :title="editingTask ? '编辑任务' : '新建任务'" width="720px" destroy-on-close>
    <div class="task-config-dialog">
      <el-form label-position="top">
        <el-form-item label="任务名称">
          <el-input v-model="form.name" placeholder="请输入任务名称" />
        </el-form-item>

        <el-form-item label="选择脚本">
          <div class="script-choice-list">
            <button
              v-for="script in currentProjectScripts"
              :key="script.id"
              class="script-choice"
              :class="{ selected: form.scriptId === script.id }"
              type="button"
              @click="selectScript(script)"
            >
              <span>
                <strong>{{ script.name }}</strong>
                <small>{{ script.sourceFile }} · v{{ script.latestVersion }} · {{ script.threadGroups.length }} 线程组 / {{ script.apis.length }} API / {{ script.monitors.length }} 监控</small>
              </span>
              <span class="asset-status">{{ form.scriptId === script.id ? '已选择' : '解析成功' }}</span>
            </button>
          </div>
        </el-form-item>

        <div class="task-form-grid">
          <el-form-item label="线程数">
            <el-input-number v-model="form.threads" :min="1" :max="10000" controls-position="right" />
          </el-form-item>
          <el-form-item label="Ramp-Up">
            <el-input-number v-model="form.rampUp" :min="0" :max="3600" controls-position="right" />
          </el-form-item>
          <el-form-item label="持续时间">
            <el-input-number v-model="form.duration" :min="1" :max="86400" controls-position="right" />
          </el-form-item>
          <el-form-item label="循环次数">
            <el-input-number v-model="form.loops" :min="1" :max="100000" controls-position="right" />
          </el-form-item>
        </div>

        <div class="task-form-grid">
          <el-form-item label="目标环境">
            <el-select v-model="form.environment">
              <el-option label="SIT / 127.0.0.1" value="SIT / 127.0.0.1" />
              <el-option label="UAT / 10.12.4.18" value="UAT / 10.12.4.18" />
            </el-select>
          </el-form-item>
          <el-form-item label="任务优先级">
            <el-select v-model="form.priority">
              <el-option label="普通" value="普通" />
              <el-option label="高" value="高" />
            </el-select>
          </el-form-item>
        </div>

        <el-form-item label="备注">
          <el-input v-model="form.remark" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
    </div>

    <template #footer>
      <div class="task-dialog-actions">
        <el-button class="task-dialog-button" @click="visible = false">取消</el-button>
        <el-button class="task-dialog-button" type="primary" @click="onSave">保存任务</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, reactive, watch } from 'vue';
import type { ScriptAsset, TestTask } from '../../types';
import { useWorkspace } from '../../composables/useWorkspace';
import { useTaskSchedule } from '../../composables/useTaskSchedule';

const props = defineProps<{
  modelValue: boolean;
  editingTask: TestTask | null;
}>();

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void;
}>();

const { currentProjectScripts } = useWorkspace();
const { saveTask } = useTaskSchedule();

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value),
});

const form = reactive({
  id: undefined as number | undefined,
  scriptId: null as number | null,
  name: '',
  environment: 'SIT / 127.0.0.1',
  threads: 60,
  rampUp: 60,
  duration: 600,
  loops: 1,
  priority: '普通',
  remark: '',
});

watch(
  () => [props.modelValue, props.editingTask, currentProjectScripts.value] as const,
  () => {
    if (!props.modelValue) {
      return;
    }
    const script = props.editingTask
      ? currentProjectScripts.value.find((item) => item.id === props.editingTask?.scriptId)
      : currentProjectScripts.value[0];
    form.id = props.editingTask?.id;
    form.scriptId = props.editingTask?.scriptId ?? script?.id ?? null;
    form.name = props.editingTask?.name ?? (script ? `${script.name} / 回归验证` : '');
    form.environment = props.editingTask?.environment ?? 'SIT / 127.0.0.1';
    form.threads = props.editingTask?.threads ?? script?.threadGroups[0]?.threads ?? 60;
    form.rampUp = props.editingTask?.rampUp ?? script?.threadGroups[0]?.rampUp ?? 60;
    form.duration = props.editingTask?.duration ?? script?.threadGroups[0]?.duration ?? 600;
    form.loops = props.editingTask?.loops ?? script?.threadGroups[0]?.loops ?? 1;
    form.priority = props.editingTask?.priority ?? '普通';
    form.remark = props.editingTask?.remark ?? '用于回归验证链路稳定性';
  },
  { immediate: true },
);

function selectScript(script: ScriptAsset) {
  form.scriptId = script.id;
  if (!props.editingTask) {
    form.name = `${script.name} / 回归验证`;
    form.threads = script.threadGroups[0]?.threads ?? form.threads;
    form.rampUp = script.threadGroups[0]?.rampUp ?? form.rampUp;
    form.duration = script.threadGroups[0]?.duration ?? form.duration;
    form.loops = script.threadGroups[0]?.loops ?? form.loops;
  }
}

function onSave() {
  if (saveTask({ ...form })) {
    visible.value = false;
  }
}
</script>
