import { computed, type MaybeRefOrGetter, toValue } from 'vue';
import type { ScriptStep, ThreadGroup, ThreadGroupMode, ThreadGroupSteppingConfig } from '../types';

/**
 * Derives thread group data reactively from a steps array.
 * Replaces the redundant `ScriptAsset.threadGroups` field.
 *
 * @param steps - A ref, getter, or plain value of ScriptStep[]
 * @returns computed threadGroups array and threadGroupCount
 */
export function useThreadGroups(steps: MaybeRefOrGetter<ScriptStep[]>) {
  const threadGroups = computed<ThreadGroup[]>(() => {
    const resolved = toValue(steps);
    return resolved
      .filter((step) => step.type === 'THREAD_GROUP')
      .map((step) => ({
        name: step.name,
        threads: Number(step.config.threads ?? 1),
        rampUp: Number(step.config.rampUp ?? 0),
        loops: Number(step.config.loops ?? 1),
        duration: Number(step.config.duration ?? 0),
        scheduler: Boolean(step.config.scheduler ?? false),
        mode: normalizeMode(step),
        stepping: normalizeStepping(step.config.stepping),
      }));
  });

  const threadGroupCount = computed(() => threadGroups.value.length);

  return { threadGroups, threadGroupCount };
}

function normalizeMode(step: ScriptStep): ThreadGroupMode {
  const mode = String(step.config.mode ?? '');
  if (mode === 'stepping') {
    return 'stepping';
  }
  if (mode === 'duration' || Boolean(step.config.scheduler ?? false)) {
    return 'duration';
  }
  return 'count';
}

function normalizeStepping(value: unknown): ThreadGroupSteppingConfig {
  const source = typeof value === 'object' && value !== null ? value as Partial<ThreadGroupSteppingConfig> : {};
  return {
    initialDelay: Number(source.initialDelay ?? 0),
    startUsersCount: Number(source.startUsersCount ?? 10),
    startUsersPeriod: Number(source.startUsersPeriod ?? 30),
    rampUp: Number(source.rampUp ?? 0),
    flightTime: Number(source.flightTime ?? 60),
    stopUsersCount: Number(source.stopUsersCount ?? 10),
    stopUsersPeriod: Number(source.stopUsersPeriod ?? 30),
    burst: Boolean(source.burst ?? false),
  };
}
