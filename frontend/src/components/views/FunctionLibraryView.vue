<template>
  <section class="function-library">
    <div class="page-head">
      <div>
        <h1>函数库</h1>
        <p>平台 JMeter 自定义函数只读展示。压测执行走分布式节点；本地执行请导出 JMX 并安装函数包至 JMeter <code>lib/ext/</code>。</p>
      </div>
      <a-button type="primary" :loading="downloading" @click="downloadPackage">下载函数包</a-button>
    </div>

    <div class="panel">
      <a-table
        :columns="columns"
        :data-source="functions"
        :loading="loading"
        :pagination="false"
        :row-key="(record: JmeterFunctionDefinition) => record.key"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'example'">
            <code>{{ record.example }}</code>
          </template>
          <template v-else-if="column.key === 'parameters'">
            <span v-if="record.parameters.length">{{ record.parameters.map((item: { name: string }) => item.name).join(', ') }}</span>
            <span v-else class="monitor-cell-muted">无</span>
          </template>
          <template v-else-if="column.key === 'actions'">
            <a-button size="small" @click="copyExample(record.example)">复制语法</a-button>
          </template>
        </template>
      </a-table>
    </div>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import type { TableColumnsType } from 'ant-design-vue';
import { message } from 'ant-design-vue';
import {
  downloadJmeterFunctionPackage,
  fetchJmeterFunctions,
  type JmeterFunctionDefinition,
} from '../../api/jmeter-functions';

const functions = ref<JmeterFunctionDefinition[]>([]);
const loading = ref(false);
const downloading = ref(false);

const columns: TableColumnsType<JmeterFunctionDefinition> = [
  { title: '名称', dataIndex: 'displayName', key: 'displayName' },
  { title: '分类', dataIndex: 'category', key: 'category', width: 100 },
  { title: '说明', dataIndex: 'description', key: 'description' },
  { title: '参数', key: 'parameters', width: 120 },
  { title: '示例', key: 'example', width: 220 },
  { title: '操作', key: 'actions', width: 100 },
];

onMounted(() => {
  void loadFunctions();
});

async function loadFunctions() {
  loading.value = true;
  try {
    functions.value = await fetchJmeterFunctions();
  } catch (error) {
    message.error(error instanceof Error ? error.message : '加载函数列表失败');
  } finally {
    loading.value = false;
  }
}

async function downloadPackage() {
  downloading.value = true;
  try {
    const blob = await downloadJmeterFunctionPackage();
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = 'perftest-jmeter-functions.jar';
    link.click();
    URL.revokeObjectURL(url);
    message.success('函数包下载已开始');
  } catch (error) {
    message.error(error instanceof Error ? error.message : '下载函数包失败');
  } finally {
    downloading.value = false;
  }
}

async function copyExample(example: string) {
  try {
    await navigator.clipboard.writeText(example);
    message.success('已复制函数语法');
  } catch {
    message.error('复制失败');
  }
}
</script>
