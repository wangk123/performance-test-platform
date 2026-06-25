import { onUnmounted, ref, watch, type Ref } from 'vue';
import { getTargetMonitoringSeriesApi } from '../api/target-monitoring';
import type { MetricKind, MetricSeries } from '../types';

export type MetricsRefreshInterval = 1000 | 5000 | 10000 | 30000 | 60000;

export function usePrometheusSeries(options: {
  taskId: Ref<number | null | undefined>;
  executionId?: Ref<number | null | undefined>;
  kind: MetricKind;
  targetIds: Ref<number[]>;
  itemId?: Ref<string | null | undefined>;
  polling: Ref<boolean>;
  refreshIntervalMs: Ref<MetricsRefreshInterval>;
}) {
  const series = ref<MetricSeries[]>([]);
  const unit = ref('');
  const loading = ref(false);
  const error = ref<string | null>(null);
  const initialized = ref(false);
  let timer: ReturnType<typeof setInterval> | null = null;

  function resolvedExecutionId() {
    return options.executionId?.value ?? options.taskId.value;
  }

  async function load(background = false) {
    const executionId = resolvedExecutionId();
    if (!executionId) {
      series.value = [];
      initialized.value = false;
      return;
    }
    if (!background && !initialized.value) {
      loading.value = true;
    }
    try {
      const result = await getTargetMonitoringSeriesApi(executionId, options.kind, {
        targetIds: options.targetIds.value,
        itemId: options.itemId?.value ?? null,
      });
      series.value = result.series;
      unit.value = result.unit;
      error.value = null;
      initialized.value = true;
    } catch {
      if (!initialized.value) {
        error.value = '指标加载失败';
        series.value = [];
      }
    } finally {
      loading.value = false;
    }
  }

  function clearTimer() {
    if (timer) {
      clearInterval(timer);
      timer = null;
    }
  }

  function restartTimer() {
    clearTimer();
    if (!options.polling.value) {
      return;
    }
    timer = setInterval(() => {
      void load(true);
    }, options.refreshIntervalMs.value);
  }

  watch(
    [options.taskId, options.executionId, options.targetIds, () => options.itemId?.value],
    () => {
      initialized.value = false;
      void load(false);
    },
    { immediate: true, deep: true },
  );

  watch(
    [options.polling, options.refreshIntervalMs],
    () => {
      restartTimer();
    },
    { immediate: true },
  );

  onUnmounted(clearTimer);

  return { series, unit, loading, error, reload: load };
}
