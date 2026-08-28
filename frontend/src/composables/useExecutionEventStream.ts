import type { MetricTick } from '../types';

/**
 * 执行事件流：SSE 连接、指数退避重连与回放钩子。
 * 与业务状态解耦——回调由调用方提供，重连条件由 shouldReconnect 判断。
 */
export interface ExecutionStreamHandlers {
  onSample?: (sample: unknown) => void;
  onMetricTick?: (tick: MetricTick) => void;
  shouldReconnect?: () => boolean;
}

export function useExecutionEventStream() {
  let stream: EventSource | null = null;
  let streamExecutionId: number | null = null;
  let retryTimer: number | null = null;
  let backoffStep = 0;

  function disconnect() {
    if (retryTimer !== null) {
      window.clearTimeout(retryTimer);
      retryTimer = null;
    }
    stream?.close();
    stream = null;
    streamExecutionId = null;
    backoffStep = 0;
  }

  function connect(executionId: number, handlers: ExecutionStreamHandlers) {
    if (stream && streamExecutionId === executionId) return;
    disconnect();
    streamExecutionId = executionId;
    stream = new EventSource(`/api/executions/${executionId}/stream`);
    stream.addEventListener('sample', (event) => {
      if (!handlers.onSample) return;
      try {
        handlers.onSample(JSON.parse(event.data));
      } catch {
      }
    });
    stream.addEventListener('metric-tick', (event) => {
      if (!handlers.onMetricTick) return;
      try {
        handlers.onMetricTick(JSON.parse(event.data));
      } catch {
      }
    });
    stream.onerror = () => {
      if (retryTimer !== null) {
        window.clearTimeout(retryTimer);
        retryTimer = null;
      }
      stream?.close();
      stream = null;
      streamExecutionId = null;
      const delay = Math.min(30000, 1000 * Math.pow(2, backoffStep));
      backoffStep = Math.min(5, backoffStep + 1);
      retryTimer = window.setTimeout(() => {
        if (handlers.shouldReconnect && !handlers.shouldReconnect()) return;
        connect(executionId, handlers);
      }, delay);
    };
    stream.onopen = () => {
      backoffStep = 0;
    };
  }

  return { connect, disconnect };
}
