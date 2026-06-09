<template>
  <section class="auth-screen">
    <div class="auth-atmosphere">
      <div class="auth-grid"></div>
      <div class="auth-signal signal-a"></div>
      <div class="auth-signal signal-b"></div>
    </div>

    <div class="auth-brand">
      <span class="brand-mark">PT</span>
      <div>
        <strong>性能测试平台</strong>
        <small>Performance Intelligence Console</small>
      </div>
    </div>

    <div class="auth-stage">
      <div class="auth-copy">
        <span class="auth-kicker">Load, Observe, Decide</span>
        <h1>把压测现场变成可控的指挥舱</h1>
        <p>统一管理项目、脚本解析、执行编排、监控指标和报告沉淀。脚本导入、编辑和执行已接入后端接口。</p>
        <div class="auth-metrics">
          <div>
            <span>资产归属</span>
            <strong>Project First</strong>
          </div>
          <div>
            <span>脚本解析</span>
            <strong>JMX Native</strong>
          </div>
          <div>
            <span>监控闭环</span>
            <strong>Live Signals</strong>
          </div>
        </div>
      </div>

      <div class="auth-card">
        <div class="auth-card-header">
          <span class="auth-kicker">Secure Access</span>
          <h2>登录控制台</h2>
          <p>使用演示账号进入平台首页。</p>
        </div>
        <el-form class="auth-form" label-position="top" @submit.prevent>
          <el-form-item label="账号">
            <el-input v-model="loginForm.username" autocomplete="username" size="large" />
          </el-form-item>
          <el-form-item label="密码">
            <el-input
              v-model="loginForm.password"
              type="password"
              autocomplete="current-password"
              show-password
              size="large"
            />
          </el-form-item>
          <el-button type="primary" size="large" :loading="loginLoading" @click="onLogin">进入平台</el-button>
        </el-form>
        <div class="auth-demo">
          <span>演示账号</span>
          <strong>admin / admin123</strong>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router';
import { useAuth } from '../composables/useAuth';
import { useWorkspace } from '../composables/useWorkspace';

const { loginForm, loginLoading, login } = useAuth();
const route = useRoute();
const router = useRouter();
const { exitProjectWorkspace } = useWorkspace();

async function onLogin() {
  const ok = await login();
  if (ok) {
    exitProjectWorkspace();
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/';
    void router.replace(redirect);
  }
}
</script>
