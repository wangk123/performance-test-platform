<template>
  <div class="thread-group-editor">
    <el-form-item label="运行模式">
      <el-radio-group :model-value="mode" @update:model-value="switchMode">
        <el-radio-button value="count">按次数</el-radio-button>
        <el-radio-button value="duration">按时间</el-radio-button>
        <el-radio-button value="stepping">阶梯加压</el-radio-button>
      </el-radio-group>
    </el-form-item>

    <section class="thread-config-section">
      <h3>基础并发</h3>
      <div class="step-config-grid">
        <el-form-item label="线程数" :error="errors.threads">
          <el-input-number :model-value="config.threads" :min="1" :step="10" controls-position="right" @update:model-value="updateField('threads', $event)" />
        </el-form-item>
        <el-form-item v-if="mode !== 'stepping'" label="Ramp-Up（秒）" :error="errors.rampUp">
          <el-input-number :model-value="config.rampUp" :min="0" :step="10" controls-position="right" @update:model-value="updateField('rampUp', $event)" />
        </el-form-item>
        <el-form-item v-if="mode === 'count'" label="循环次数" :error="errors.loops">
          <el-input-number :model-value="config.loops" :min="1" controls-position="right" @update:model-value="updateField('loops', $event)" />
        </el-form-item>
        <el-form-item v-if="mode === 'duration'" label="持续时间（秒）" :error="errors.duration">
          <el-input-number :model-value="config.duration" :min="1" :step="60" controls-position="right" @update:model-value="updateField('duration', $event)" />
        </el-form-item>
      </div>
    </section>

    <section v-if="mode === 'stepping'" class="thread-config-section">
      <h3>阶梯加压</h3>
      <div class="step-config-grid">
        <el-form-item label="初始延迟（秒）" :error="errors.initialDelay">
          <el-input-number :model-value="stepping.initialDelay" :min="0" :step="5" controls-position="right" @update:model-value="updateStepping('initialDelay', $event)" />
        </el-form-item>
        <el-form-item label="每阶段启动用户数" :error="errors.startUsersCount">
          <el-input-number :model-value="stepping.startUsersCount" :min="1" :step="10" controls-position="right" @update:model-value="updateStepping('startUsersCount', $event)" />
        </el-form-item>
        <el-form-item label="阶段间隔（秒）" :error="errors.startUsersPeriod">
          <el-input-number :model-value="stepping.startUsersPeriod" :min="1" :step="10" controls-position="right" @update:model-value="updateStepping('startUsersPeriod', $event)" />
        </el-form-item>
        <el-form-item label="单阶段 Ramp-Up（秒）" :error="errors.steppingRampUp">
          <el-input-number :model-value="stepping.rampUp" :min="0" :step="5" controls-position="right" @update:model-value="updateStepping('rampUp', $event)" />
        </el-form-item>
        <el-form-item label="保持时间（秒）" :error="errors.flightTime">
          <el-input-number :model-value="stepping.flightTime" :min="1" :step="60" controls-position="right" @update:model-value="updateStepping('flightTime', $event)" />
        </el-form-item>
        <el-form-item label="每阶段停止用户数" :error="errors.stopUsersCount">
          <el-input-number :model-value="stepping.stopUsersCount" :min="1" :step="10" controls-position="right" @update:model-value="updateStepping('stopUsersCount', $event)" />
        </el-form-item>
        <el-form-item label="停止间隔（秒）" :error="errors.stopUsersPeriod">
          <el-input-number :model-value="stepping.stopUsersPeriod" :min="1" :step="10" controls-position="right" @update:model-value="updateStepping('stopUsersPeriod', $event)" />
        </el-form-item>
        <el-form-item label="Burst 启动">
          <el-switch :model-value="stepping.burst" @update:model-value="updateSteppingBurst" />
        </el-form-item>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive } from 'vue';
import type { ThreadGroup, ThreadGroupMode, ThreadGroupSteppingConfig } from '../../types';

const props = defineProps<{
  config: ThreadGroup;
}>();

const emit = defineEmits<{
  (e: 'update:config', value: ThreadGroup): void;
}>();

const defaultStepping: ThreadGroupSteppingConfig = {
  initialDelay: 0,
  startUsersCount: 10,
  startUsersPeriod: 30,
  rampUp: 0,
  flightTime: 60,
  stopUsersCount: 10,
  stopUsersPeriod: 30,
  burst: false,
};

const mode = computed<ThreadGroupMode>(() => props.config.mode ?? (props.config.scheduler ? 'duration' : 'count'));
const stepping = computed<ThreadGroupSteppingConfig>(() => ({ ...defaultStepping, ...props.config.stepping }));

const errors = reactive<Record<string, string>>({
  threads: '',
  rampUp: '',
  loops: '',
  duration: '',
  initialDelay: '',
  startUsersCount: '',
  startUsersPeriod: '',
  steppingRampUp: '',
  flightTime: '',
  stopUsersCount: '',
  stopUsersPeriod: '',
});

function switchMode(value: string | number | boolean) {
  const nextMode = String(value) as ThreadGroupMode;
  if (nextMode === mode.value) {
    return;
  }
  if (nextMode === 'count') {
    emit('update:config', { ...props.config, mode: nextMode, scheduler: false, loops: Math.max(1, props.config.loops || 1), duration: 0 });
  } else if (nextMode === 'duration') {
    emit('update:config', { ...props.config, mode: nextMode, scheduler: true, loops: -1, duration: Math.max(1, props.config.duration || 60) });
  } else {
    emit('update:config', { ...props.config, mode: nextMode, scheduler: false, rampUp: 0, loops: 1, duration: stepping.value.flightTime, stepping: stepping.value });
  }
}

function updateField(field: keyof ThreadGroup, value: number | null | undefined) {
  if (value === null || value === undefined) {
    return;
  }
  const validation = validateNumber(String(field), value, field === 'threads' || field === 'loops' || field === 'duration' ? 1 : 0);
  errors[String(field)] = validation;
  if (!validation) {
    emit('update:config', { ...props.config, [field]: value });
  }
}

function updateStepping(field: keyof ThreadGroupSteppingConfig, value: number | null | undefined) {
  if (value === null || value === undefined) {
    return;
  }
  const min = field === 'initialDelay' || field === 'rampUp' ? 0 : 1;
  const key = field === 'rampUp' ? 'steppingRampUp' : field;
  const validation = validateNumber(key, value, min);
  errors[key] = validation;
  if (!validation) {
    const nextStepping = { ...stepping.value, [field]: value };
    emit('update:config', { ...props.config, mode: 'stepping', scheduler: false, duration: nextStepping.flightTime, stepping: nextStepping });
  }
}

function updateSteppingBurst(value: string | number | boolean) {
  emit('update:config', { ...props.config, mode: 'stepping', stepping: { ...stepping.value, burst: Boolean(value) } });
}

function validateNumber(field: string, value: number, min: number): string {
  return value < min ? `${field} 必须 >= ${min}` : '';
}
</script>
