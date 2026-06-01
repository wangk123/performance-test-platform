import { reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import type { ScriptAsset } from '../types';
import { parseParamNumber } from '../utils/format';

const paramDrawerVisible = ref(false);
const paramScriptAsset = ref<ScriptAsset | null>(null);

const paramForm = reactive({
  threads: 100,
  loops: 1,
  duration: 600,
  rampUp: 60,
  environment: 'SIT',
  extraProperties: 'host=127.0.0.1\nprotocol=https',
});

function openParamDrawer(script: ScriptAsset) {
  paramScriptAsset.value = script;
  const valueMap = Object.fromEntries(script.params.map((param) => [param.key, param.value]));
  Object.assign(paramForm, {
    threads: parseParamNumber(valueMap.threads, 100),
    loops: parseParamNumber(valueMap.loops, 1),
    duration: parseParamNumber(valueMap.duration, 600),
    rampUp: parseParamNumber(valueMap.rampUp, 60),
    environment: String(valueMap.environment ?? 'SIT'),
    extraProperties: String(valueMap.extraProperties ?? ''),
  });
  paramDrawerVisible.value = true;
}

function saveParams() {
  if (!paramScriptAsset.value) {
    return;
  }
  paramScriptAsset.value.params = [
    { key: 'threads', label: '线程数', value: paramForm.threads },
    { key: 'loops', label: '循环次数', value: paramForm.loops },
    { key: 'duration', label: '持续时间', value: `${paramForm.duration}s` },
    { key: 'rampUp', label: 'Ramp-Up', value: `${paramForm.rampUp}s` },
    { key: 'environment', label: '目标环境', value: paramForm.environment },
    { key: 'extraProperties', label: '扩展属性', value: paramForm.extraProperties || '未配置' },
  ];
  paramDrawerVisible.value = false;
  ElMessage.success('默认执行参数已保存');
}

export function useScriptParams() {
  return {
    paramDrawerVisible,
    paramScriptAsset,
    paramForm,
    openParamDrawer,
    saveParams,
  };
}
