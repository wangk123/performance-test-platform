<template>
  <div class="thread-group-editor">
    <a-form-item label="运行模式">
      <a-radio-group :value="mode" @update:value="switchMode">
        <a-radio-button value="count">按次数</a-radio-button>
        <a-radio-button value="duration">按时间</a-radio-button>
        <a-radio-button value="stepping">阶梯加压</a-radio-button>
      </a-radio-group>
    </a-form-item>

    <section class="thread-config-section">
      <h3>基础并发</h3>
      <div class="step-config-grid">
        <a-form-item label="线程数" :validate-status="errors.threads ? 'error' : undefined" :help="errors.threads || undefined">
          <a-input-number :value="config.threads" :min="1" :step="10" @update:value="updateField('threads', $event)" />
        </a-form-item>
        <a-form-item v-if="mode !== 'stepping'" label="Ramp-Up（秒）" :validate-status="errors.rampUp ? 'error' : undefined" :help="errors.rampUp || undefined">
          <a-input-number :value="config.rampUp" :min="0" :step="10" @update:value="updateField('rampUp', $event)" />
        </a-form-item>
        <a-form-item v-if="mode === 'count'" label="循环次数" :validate-status="errors.loops ? 'error' : undefined" :help="errors.loops || undefined">
          <a-input-number :value="config.loops" :min="1" @update:value="updateField('loops', $event)" />
        </a-form-item>
        <a-form-item v-if="mode === 'duration'" label="持续时间（秒）" :validate-status="errors.duration ? 'error' : undefined" :help="errors.duration || undefined">
          <a-input-number :value="config.duration" :min="1" :step="60" @update:value="updateField('duration', $event)" />
        </a-form-item>
      </div>
    </section>

    <section v-if="mode === 'stepping'" class="thread-config-section">
      <h3>阶梯加压</h3>
      <div class="step-config-grid">
        <a-form-item label="初始延迟（秒）" :validate-status="errors.initialDelay ? 'error' : undefined" :help="errors.initialDelay || undefined">
          <a-input-number :value="stepping.initialDelay" :min="0" :step="5" @update:value="updateStepping('initialDelay', $event)" />
        </a-form-item>
        <a-form-item label="每阶段启动用户数" :validate-status="errors.startUsersCount ? 'error' : undefined" :help="errors.startUsersCount || undefined">
          <a-input-number :value="stepping.startUsersCount" :min="1" :step="10" @update:value="updateStepping('startUsersCount', $event)" />
        </a-form-item>
        <a-form-item label="阶段间隔（秒）" :validate-status="errors.startUsersPeriod ? 'error' : undefined" :help="errors.startUsersPeriod || undefined">
          <a-input-number :value="stepping.startUsersPeriod" :min="1" :step="10" @update:value="updateStepping('startUsersPeriod', $event)" />
        </a-form-item>
        <a-form-item label="单阶段 Ramp-Up（秒）" :validate-status="errors.steppingRampUp ? 'error' : undefined" :help="errors.steppingRampUp || undefined">
          <a-input-number :value="stepping.rampUp" :min="0" :step="5" @update:value="updateStepping('rampUp', $event)" />
        </a-form-item>
        <a-form-item label="保持时间（秒）" :validate-status="errors.flightTime ? 'error' : undefined" :help="errors.flightTime || undefined">
          <a-input-number :value="stepping.flightTime" :min="1" :step="60" @update:value="updateStepping('flightTime', $event)" />
        </a-form-item>
        <a-form-item label="每阶段停止用户数" :validate-status="errors.stopUsersCount ? 'error' : undefined" :help="errors.stopUsersCount || undefined">
          <a-input-number :value="stepping.stopUsersCount" :min="1" :step="10" @update:value="updateStepping('stopUsersCount', $event)" />
        </a-form-item>
        <a-form-item label="停止间隔（秒）" :validate-status="errors.stopUsersPeriod ? 'error' : undefined" :help="errors.stopUsersPeriod || undefined">
          <a-input-number :value="stepping.stopUsersPeriod" :min="1" :step="10" @update:value="updateStepping('stopUsersPeriod', $event)" />
        </a-form-item>
        <a-form-item label="Burst 启动">
          <a-switch :checked="stepping.burst" @update:checked="updateSteppingBurst" />
        </a-form-item>
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
