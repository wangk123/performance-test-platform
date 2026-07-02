<template>
  <div class="report-page">
    <a-spin v-if="loading" size="large" tip="加载报告数据..." class="report-loading" />
    <a-result v-else-if="error" status="error" :title="error">
      <template #extra><a-button type="primary" @click="loadReport">重试</a-button></template>
    </a-result>

    <div v-else-if="data" class="report-body">
      <!-- Header -->
      <div class="report-topbar">
        <div>
          <h1>性能测试报告</h1>
          <p class="topbar-meta">{{ data.plan.planName }} · {{ data.scenarios.length }} 个场景</p>
        </div>
        <div class="topbar-actions">
          <a-button @click="$router.back()">返回</a-button>
          <a-button type="primary" :loading="exporting" @click="handleExportWord">
            <DownloadOutlined /> 下载 Word
          </a-button>
        </div>
      </div>

      <div class="report-container">
        <!-- ===== 1. 测试目标 ===== -->
        <section class="section">
          <div class="section-hd">
            <div><span class="eyebrow">Test Objectives</span><h2>测试目标</h2></div>
          </div>
          <div class="section-bd">
            <div class="obj-grid">
              <div>
                <h3>🎯 测试目的</h3>
                <ul>
                  <li>验证系统在本次任务计划配置下的性能表现</li>
                  <li>通过多场景多梯度执行，定位系统吞吐量上限和性能拐点</li>
                  <li>评估各接口在高负载下的响应时间和错误率</li>
                  <li>为后续容量规划和优化提供数据依据</li>
                </ul>
              </div>
              <div>
                <h3>📊 测试范围</h3>
                <table class="info-table">
                  <tr><td>任务计划</td><td><strong>{{ data.plan.planName }}</strong></td></tr>
                  <tr><td>测试场景</td><td><strong>{{ data.scenarios.length }} 个</strong></td></tr>
                  <tr><td>执行总轮数</td><td><strong>{{ totalRounds }} 次</strong></td></tr>
                  <tr><td>并发梯度</td><td><strong>{{ concurrencyLevels }}</strong></td></tr>
                </table>
              </div>
            </div>
          </div>
        </section>

        <!-- ===== 2. 测试环境配置 ===== -->
        <section class="section">
          <div class="section-hd">
            <div><span class="eyebrow">Test Environment</span><h2>测试环境配置</h2></div>
          </div>
          <div class="section-bd">
            <div class="env-grid">
              <div class="env-card">
                <div class="env-card-hd">🖥️ 被测服务器（业务服务）</div>
                <div class="env-card-bd">
                  <table class="info-table">
                    <tr><td>实例数量</td><td><span class="placeholder">待补充</span></td></tr>
                    <tr><td>实例规格</td><td><span class="placeholder">待补充</span></td></tr>
                    <tr><td>JVM 配置</td><td><span class="placeholder">待补充</span></td></tr>
                    <tr><td>框架</td><td><span class="placeholder">待补充</span></td></tr>
                    <tr><td>数据库连接池</td><td><span class="placeholder">待补充</span></td></tr>
                  </table>
                </div>
              </div>
              <div class="env-card">
                <div class="env-card-hd">🗄️ 中间件</div>
                <div class="env-card-bd">
                  <table class="info-table">
                    <tr><td>MySQL</td><td><span class="placeholder">待补充</span></td></tr>
                    <tr><td>Redis</td><td><span class="placeholder">待补充</span></td></tr>
                    <tr><td>其他</td><td><span class="placeholder">待补充</span></td></tr>
                  </table>
                </div>
              </div>
              <div class="env-card">
                <div class="env-card-hd">🖥️ 压测集群（JMeter 执行节点）</div>
                <div class="env-card-bd">
                  <table class="info-table">
                    <tr><td>节点数量</td><td><span class="placeholder">待补充</span></td></tr>
                    <tr><td>节点规格</td><td><span class="placeholder">待补充</span></td></tr>
                    <tr><td>JMeter 版本</td><td><span class="placeholder">待补充</span></td></tr>
                  </table>
                </div>
              </div>
              <div class="env-card">
                <div class="env-card-hd">🌐 网络拓扑</div>
                <div class="env-card-bd">
                  <table class="info-table">
                    <tr><td>环境</td><td><span class="placeholder">待补充</span></td></tr>
                    <tr><td>流量路径</td><td><span class="placeholder">待补充</span></td></tr>
                  </table>
                </div>
              </div>
            </div>
          </div>
        </section>

        <!-- ===== 3. 场景多梯度结果 ===== -->
        <section class="section">
          <div class="section-hd">
            <div style="flex:1">
              <span class="eyebrow">Scenario Details</span>
              <h2>各场景多梯度结果</h2>
            </div>
            <div class="sc-tabs">
              <button v-for="(sc,i) in data.scenarios" :key="sc.scenarioId"
                :class="['sc-tab', { active: activeScenario === i }]"
                @click="activeScenario = i"
              >{{ sc.scenarioName }}</button>
            </div>
          </div>
          <div class="section-bd" v-if="currentScenario">
            <!-- Gradient comparison table -->
            <h4 class="sub-heading">梯度对比总览</h4>
            <div class="cmp-table-wrap">
              <table class="cmp-table">
                <thead><tr><th>梯度</th><th>线程数</th><th>采样数</th><th>TPS</th><th>Avg RT</th><th>P95 RT</th><th>错误率</th></tr></thead>
                <tbody>
                  <tr v-for="r in currentScenario.rounds" :key="r.executionId">
                    <td><strong>{{ roundReportLabel(r) }}</strong></td>
                    <td>{{ r.threads }}</td>
                    <td>{{ fmtNum(r.summary.samples) }}</td>
                    <td>{{ fmt1(r.summary.throughput) }}/s</td>
                    <td>{{ r.summary.avgRt }}ms</td>
                    <td>{{ r.summary.p95 }}ms</td>
                    <td :style="{ color: r.summary.errorRate > 2 ? '#dc2626' : r.summary.errorRate > 1 ? '#ea580c' : '#16a34a' }">{{ fmt1(r.summary.errorRate) }}%</td>
                  </tr>
                </tbody>
              </table>
            </div>

            <!-- Collapsible rounds -->
            <h4 class="sub-heading mt-4">各梯度详细结果</h4>
            <div v-for="r in currentScenario.rounds" :key="r.executionId" class="round-section">
              <button :class="['round-toggle', { expanded: expandedRounds.has(r.executionId) }]"
                @click="toggleRound(r.executionId)">
                <span class="arrow">▶</span>
                <span class="round-badge">{{ roundReportLabel(r) }}</span>
                线程: {{ r.threads }} · Ramp: {{ r.rampUp }}s · 持续: {{ r.duration }}s · 吞吐: {{ fmt1(r.summary.throughput) }}/s · P95: {{ r.summary.p95 }}ms
                <span class="round-sub">{{ expandedRounds.has(r.executionId) ? '已展开' : '点击展开' }}</span>
              </button>
              <div v-if="expandedRounds.has(r.executionId)" class="round-body">
                <!-- Config cards -->
                <div class="rd-cfg-row">
                  <div v-if="r.stepName" class="rd-cfg-item"><span class="rd-label">Thread Group</span><span class="rd-value">{{ r.stepName }}</span></div>
                  <div class="rd-cfg-item"><span class="rd-label">线程数</span><span class="rd-value">{{ r.threads }}</span></div>
                  <div class="rd-cfg-item"><span class="rd-label">Ramp-Up</span><span class="rd-value">{{ r.rampUp }}s</span></div>
                  <div class="rd-cfg-item"><span class="rd-label">时长</span><span class="rd-value">{{ r.duration }}s</span></div>
                  <div class="rd-cfg-item"><span class="rd-label">采样数</span><span class="rd-value">{{ fmtNum(r.summary.samples) }}</span></div>
                  <div class="rd-cfg-item"><span class="rd-label">TPS</span><span class="rd-value">{{ fmt1(r.summary.throughput) }}/s</span></div>
                  <div class="rd-cfg-item"><span class="rd-label">Avg RT</span><span class="rd-value">{{ r.summary.avgRt }}ms</span></div>
                  <div class="rd-cfg-item"><span class="rd-label">错误率</span><span class="rd-value" :style="{ color: r.summary.errorRate > 2 ? '#dc2626' : '#16a34a' }">{{ fmt1(r.summary.errorRate) }}%</span></div>
                </div>

                <!-- Aggregate table -->
                <h4 class="sub-heading">接口聚合统计</h4>
                <div class="agg-table-wrap">
                  <table class="agg-table">
                    <thead><tr><th>Label</th><th>Avg</th><th>Median</th><th>P95</th><th>P99</th><th>Error%</th><th>TPS</th></tr></thead>
                    <tbody>
                      <tr v-for="row in r.aggregateRows" :key="row.label">
                        <td>{{ row.label }}</td>
                        <td>{{ row.average }}ms</td>
                        <td>{{ row.median }}ms</td>
                        <td>{{ row.p95 }}ms</td>
                        <td>{{ row.p99 }}ms</td>
                        <td :class="{ 'err-high': row.errorRate > 2 }">{{ fmt1(row.errorRate) }}%</td>
                        <td>{{ fmt1(row.throughput) }}/s</td>
                      </tr>
                    </tbody>
                  </table>
                </div>

                <!-- Charts -->
                <div class="charts-row">
                  <div class="chart-box">
                    <h4>响应时间 & TPS</h4>
                    <div :ref="el => setChartRef(r.executionId, 'rtTps', el)" class="report-chart"></div>
                  </div>
                  <div class="chart-box">
                    <h4>错误率</h4>
                    <div :ref="el => setChartRef(r.executionId, 'error', el)" class="report-chart"></div>
                  </div>
                </div>

                <!-- Errors -->
                <h4 class="sub-heading">错误样本 ({{ r.failures.errorCount }})</h4>
                <div v-if="r.failures.samples.length" class="err-list">
                  <div v-for="s in r.failures.samples" :key="s.id" class="err-item">
                    <span class="err-dot"></span>
                    <span class="err-label">{{ s.label }}</span>
                    <span class="err-code">{{ s.statusCode }}</span>
                    <span class="err-msg">{{ s.message }}</span>
                    <span class="err-time">{{ s.time }}</span>
                  </div>
                </div>
                <a-empty v-else description="无错误" />
              </div>
            </div>
            <a-empty v-if="!currentScenario.rounds.length" description="暂无执行记录" />
          </div>
          <div class="section-bd" v-else><a-empty description="暂无场景数据" /></div>
        </section>

        <!-- ===== 4. 测试结论 ===== -->
        <section class="section">
          <div class="section-hd">
            <div><span class="eyebrow">Test Conclusion</span><h2>测试结论与分析</h2></div>
          </div>
          <div class="editor-toolbar">
            <button class="et-btn" title="标题" @click="execCmd('formatBlock','h2')">H</button>
            <button class="et-btn" title="加粗" @click="execCmd('bold')"><b>B</b></button>
            <button class="et-btn" title="列表" @click="execCmd('insertUnorderedList')">•</button>
          </div>
          <div ref="editorRef" class="editor-content" contenteditable="true" @input="onEditorInput"></div>
        </section>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref, reactive, watch } from 'vue';
import { useRoute } from 'vue-router';
import { DownloadOutlined } from '@ant-design/icons-vue';
import { message } from 'ant-design-vue';
import * as echarts from 'echarts/core';
import { CanvasRenderer } from 'echarts/renderers';
import { LineChart } from 'echarts/charts';
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components';
import { fetchPlanReport, exportWordReport, type PlanReportResponse, type RoundReport } from '../api/reports';
import { roundReportLabel } from '../utils/scenario-thread-group';

echarts.use([CanvasRenderer, LineChart, GridComponent, LegendComponent, TooltipComponent]);

const route = useRoute();
const loading = ref(true);
const error = ref('');
const exporting = ref(false);
const data = ref<PlanReportResponse | null>(null);
const editorRef = ref<HTMLElement | null>(null);
const editorContent = ref('');
const activeScenario = ref(0);
const expandedRounds = reactive(new Set<number>());
const chartRefs: Record<string, any> = reactive({});
const chartInstances: Record<string, any> = {};

const planId = computed(() => Number(route.params.planId));
const currentScenario = computed(() => data.value?.scenarios[activeScenario.value] || null);

// Compute total rounds and concurrency levels for 测试目标 section
const totalRounds = computed(() => data.value?.scenarios.reduce((s, sc) => s + sc.rounds.length, 0) || 0);
const concurrencyLevels = computed(() => {
  const labels = new Set<string>();
  data.value?.scenarios.forEach((sc) => sc.rounds.forEach((r) => labels.add(roundReportLabel(r))));
  return [...labels].join(' / ');
});

onMounted(() => loadReport());

async function loadReport() {
  loading.value = true; error.value = '';
  try {
    data.value = await fetchPlanReport(planId.value);
    if (data.value.scenarios.length > 0) {
      const first = data.value.scenarios[0].rounds;
      if (first.length > 0) {
        expandedRounds.add(first[0].executionId);
        await nextTick();
        renderChartsForRound(first[0]);
      }
    }
  } catch (e: any) { error.value = e.message || '加载报告失败'; }
  finally { loading.value = false; }
}

function toggleRound(executionId: number) {
  if (expandedRounds.has(executionId)) {
    expandedRounds.delete(executionId);
    disposeCharts(executionId);
  } else {
    expandedRounds.add(executionId);
    const round = data.value?.scenarios.flatMap(s => s.rounds).find(r => r.executionId === executionId);
    if (round) nextTick(() => renderChartsForRound(round));
  }
}

function setChartRef(eid: number, key: string, el: any) { if (el) chartRefs[`${eid}-${key}`] = el; }

function disposeCharts(eid: number) {
  ['rtTps', 'error'].forEach(k => {
    const key = `${eid}-${k}`;
    if (chartInstances[key]) { chartInstances[key].dispose(); delete chartInstances[key]; }
  });
}

// ---- Charts ----

function renderChartsForRound(r: RoundReport) {
  const ticks = r.metricSeries?.ticks || [];
  if (!ticks.length) return;
  const xData = ticks.map(t => formatSecond(t.bucketTimeMs / 1000));
  const C = { blue: '#3b6df0', orange: '#ea580c', teal: '#0d9488', red: '#dc2626' };

  const rtEl = chartRefs[`${r.executionId}-rtTps`];
  if (rtEl) {
    if (chartInstances[`${r.executionId}-rtTps`]) chartInstances[`${r.executionId}-rtTps`].dispose();
    const c = echarts.init(rtEl);
    c.setOption({
      tooltip: { trigger: 'axis' },
      legend: { data: ['Avg RT', 'P95 RT', 'TPS'], bottom: 0, textStyle: { fontSize: 10 } },
      grid: { left: 50, right: 50, top: 16, bottom: 36 },
      xAxis: { type: 'category', data: xData, boundaryGap: false, axisLabel: { fontSize: 9 } },
      yAxis: [{ type: 'value', name: 'ms' }, { type: 'value', name: 'req/s' }],
      series: [
        { name: 'Avg RT', type: 'line', data: ticks.map(t => t.overall?.avgRtMs || 0), smooth: true, symbol: 'none', lineStyle: { color: C.blue, width: 2 } },
        { name: 'P95 RT', type: 'line', data: ticks.map(t => t.overall?.p95RtMs || 0), smooth: true, symbol: 'none', lineStyle: { color: C.orange, width: 2 } },
        { name: 'TPS', type: 'line', yAxisIndex: 1, data: ticks.map(t => t.overall?.throughput || 0), smooth: true, symbol: 'none', lineStyle: { color: C.teal, width: 2 } },
      ],
    });
    chartInstances[`${r.executionId}-rtTps`] = c;
  }

  const errEl = chartRefs[`${r.executionId}-error`];
  if (errEl) {
    if (chartInstances[`${r.executionId}-error`]) chartInstances[`${r.executionId}-error`].dispose();
    const c = echarts.init(errEl);
    c.setOption({
      tooltip: { trigger: 'axis', valueFormatter: (v: any) => v + '%' },
      legend: { data: ['错误率'], bottom: 0, textStyle: { fontSize: 10 } },
      grid: { left: 50, right: 20, top: 16, bottom: 36 },
      xAxis: { type: 'category', data: xData, boundaryGap: false, axisLabel: { fontSize: 9 } },
      yAxis: { type: 'value', name: '%' },
      series: [{
        name: '错误率', type: 'line',
        data: ticks.map(t => { const o = t.overall; return o && o.samples > 0 ? +((o.errorSamples / o.samples) * 100).toFixed(2) : 0; }),
        smooth: true, symbol: 'none', lineStyle: { color: C.red, width: 2 }, areaStyle: { color: 'rgba(220,38,38,0.10)' },
      }],
    });
    chartInstances[`${r.executionId}-error`] = c;
  }
}

// ---- Export ----

async function handleExportWord() {
  if (!data.value) return;
  exporting.value = true;
  try {
    const chartImages: Record<string, string> = {};
    for (const [key, inst] of Object.entries(chartInstances)) {
      try { chartImages[key] = (inst as any).getDataURL({ type: 'png', pixelRatio: 2, backgroundColor: '#fff' }); } catch (_) {}
    }
    const blob = await exportWordReport(planId.value, { chartImages, editorContent: editorContent.value });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a'); a.href = url;
    a.download = `performance-report-${data.value.plan.planName}.docx`;
    a.click(); URL.revokeObjectURL(url);
    message.success('报告下载成功');
  } catch (e: any) { message.error(e.message || '导出失败'); }
  finally { exporting.value = false; }
}

// ---- Editor ----

function execCmd(cmd: string, val?: string) { document.execCommand(cmd, false, val); editorRef.value?.focus(); }
function onEditorInput() { editorContent.value = editorRef.value?.innerHTML || ''; }

// ---- Watch scenario change ----

watch(activeScenario, async () => {
  for (const [k, inst] of Object.entries(chartInstances)) { try { inst.dispose(); } catch (_) {} delete chartInstances[k]; }
  expandedRounds.clear();
  await nextTick();
  const sc = currentScenario.value;
  if (sc && sc.rounds.length > 0) {
    expandedRounds.add(sc.rounds[0].executionId);
    await nextTick();
    renderChartsForRound(sc.rounds[0]);
  }
});

// ---- Helpers ----

function formatSecond(s: number) { const m = Math.floor(s / 60); return m + ':' + (Math.floor(s % 60) < 10 ? '0' : '') + Math.floor(s % 60); }
function fmtNum(n: number) { return n?.toLocaleString?.() || '0'; }
function fmt1(n: number) { return n != null ? Number(n.toFixed(1)) : 0; }
</script>

<style scoped>
.report-page { min-height: 100vh; background: #f2f3f7; }
.report-loading { display: flex; justify-content: center; padding-top: 120px; }

.report-topbar {
  background: #fff; border-bottom: 1px solid #e5e7eb; padding: 14px 32px;
  display: flex; align-items: center; justify-content: space-between; position: sticky; top: 0; z-index: 100;
}
.report-topbar h1 { font-size: 20px; font-weight: 700; }
.topbar-meta { font-size: 13px; color: #6b7280; margin-top: 2px; }
.topbar-actions { display: flex; gap: 10px; align-items: center; }

.report-container { max-width: 1200px; margin: 0 auto; padding: 24px 32px; }

/* Section */
.section { background: #fff; border-radius: 10px; box-shadow: 0 1px 3px rgba(0,0,0,.05); margin-bottom: 20px; overflow: hidden; }
.section-hd { padding: 16px 24px; border-bottom: 1px solid #e5e7eb; display: flex; align-items: center; flex-wrap: wrap; gap: 16px; }
.section-hd .eyebrow { font-size: 11px; text-transform: uppercase; letter-spacing: .5px; color: #6b7280; width: 100%; }
.section-hd h2 { font-size: 16px; font-weight: 600; }
.section-bd { padding: 24px; }

.sub-heading { font-size: 12px; font-weight: 600; color: #6b7280; margin-bottom: 10px; text-transform: uppercase; }
.mt-4 { margin-top: 20px; }

/* Objectives */
.obj-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 24px; }
.obj-grid h3 { font-size: 14px; font-weight: 600; margin-bottom: 10px; }
.obj-grid ul { font-size: 13px; line-height: 2; padding-left: 18px; }

.info-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.info-table td { padding: 6px 0; border-bottom: 1px solid #f3f4f6; }
.info-table td:first-child { color: #6b7280; width: 100px; }
.placeholder { color: #cbd5e1; font-style: italic; }

/* Environment */
.env-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
.env-card { border: 1px solid #e5e7eb; border-radius: 8px; overflow: hidden; }
.env-card-hd { padding: 10px 16px; background: #f8fafc; border-bottom: 1px solid #e5e7eb; font-size: 13px; font-weight: 600; }
.env-card-bd { padding: 0 16px; }
.env-card-bd .info-table td { padding: 8px 0; }

/* Scenario Tabs */
.sc-tabs { display: flex; gap: 0; }
.sc-tab { padding: 8px 14px; font-size: 13px; font-weight: 500; cursor: pointer; border: none; background: none; color: #6b7280; border-bottom: 2px solid transparent; }
.sc-tab:hover { color: #1a1a2e; }
.sc-tab.active { color: #3b6df0; border-bottom-color: #3b6df0; }

/* Comparison Table */
.cmp-table-wrap { overflow-x: auto; }
.cmp-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.cmp-table thead th { background: #f0f4ff; padding: 8px 12px; text-align: center; font-weight: 600; border-bottom: 2px solid #c7d2fe; font-size: 12px; white-space: nowrap; }
.cmp-table thead th:first-child { text-align: left; }
.cmp-table tbody td { padding: 8px 12px; text-align: center; border-bottom: 1px solid #f3f4f6; }
.cmp-table tbody td:first-child { text-align: left; font-weight: 600; }
.cmp-table tbody tr:hover { background: #f8faff; }

/* Round Sections */
.round-section { border: 1px solid #e5e7eb; border-radius: 8px; margin-bottom: 12px; overflow: hidden; }
.round-toggle {
  width: 100%; padding: 11px 18px; background: #fafbfc; border: none; cursor: pointer;
  display: flex; align-items: center; gap: 10px; font-size: 13px; font-weight: 600; text-align: left;
}
.round-toggle:hover { background: #f0f4ff; }
.round-toggle .arrow { font-size: 9px; color: #6b7280; transition: transform .2s; width: 14px; }
.round-toggle.expanded .arrow { transform: rotate(90deg); }
.round-badge { font-size: 11px; padding: 2px 10px; border-radius: 12px; font-weight: 500; background: #e0e7ff; color: #3730a3; }
.round-sub { font-size: 11px; color: #6b7280; font-weight: 400; margin-left: auto; }
.round-body { padding: 20px; border-top: 1px solid #e5e7eb; }

.rd-cfg-row { display: grid; grid-template-columns: repeat(6,1fr); gap: 8px; margin-bottom: 18px; }
.rd-cfg-item { padding: 8px 12px; background: #f8fafc; border-radius: 6px; }
.rd-label { font-size: 9px; color: #6b7280; text-transform: uppercase; display: block; }
.rd-value { font-size: 13px; font-weight: 600; }

/* Aggregate Table */
.agg-table-wrap { overflow-x: auto; margin-bottom: 16px; }
.agg-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.agg-table thead th { background: #f8fafc; padding: 9px 10px; text-align: right; font-weight: 600; color: #6b7280; border-bottom: 2px solid #e5e7eb; font-size: 12px; white-space: nowrap; }
.agg-table thead th:first-child { text-align: left; }
.agg-table tbody td { padding: 9px 10px; text-align: right; border-bottom: 1px solid #f3f4f6; }
.agg-table tbody td:first-child { text-align: left; }
.agg-table tbody tr:nth-child(even) { background: #fafbfc; }
.agg-table tbody tr:hover { background: #f0f4ff; }
.err-high { color: #dc2626; font-weight: 700; }

/* Charts */
.charts-row { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin: 16px 0; }
.chart-box { border: 1px solid #e5e7eb; border-radius: 8px; padding: 12px; }
.chart-box h4 { font-size: 11px; font-weight: 600; color: #6b7280; margin-bottom: 8px; text-transform: uppercase; }
.report-chart { width: 100%; height: 260px; }

/* Error List */
.err-list { max-height: 260px; overflow-y: auto; }
.err-item { display: flex; align-items: center; gap: 12px; padding: 9px 16px; border-bottom: 1px solid #e5e7eb; font-size: 12px; }
.err-item:hover { background: #fafbfc; }
.err-dot { width: 7px; height: 7px; border-radius: 50%; background: #dc2626; flex-shrink: 0; }
.err-label { font-weight: 600; min-width: 140px; }
.err-code { font-family: monospace; color: #dc2626; min-width: 50px; }
.err-msg { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.err-time { color: #6b7280; white-space: nowrap; }

/* Editor */
.editor-toolbar { display: flex; gap: 2px; padding: 8px 12px; border-bottom: 1px solid #e5e7eb; background: #fafbfc; }
.et-btn { width: 30px; height: 26px; border: 1px solid transparent; border-radius: 4px; background: transparent; cursor: pointer; font-size: 13px; color: #6b7280; display: flex; align-items: center; justify-content: center; }
.et-btn:hover { background: #e5e7eb; color: #1a1a2e; }
.editor-content { min-height: 180px; padding: 18px 22px; outline: none; font-size: 14px; line-height: 1.9; }
.editor-content:empty::before { content: '请在此编写测试结论、问题分析和优化建议...'; color: #cbd5e1; }

@media (max-width: 960px) {
  .env-grid, .obj-grid, .charts-row { grid-template-columns: 1fr; }
  .rd-cfg-row { grid-template-columns: repeat(3,1fr); }
  .sc-tabs { flex-wrap: wrap; }
}
</style>
