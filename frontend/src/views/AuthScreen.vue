<template>
  <section class="auth-screen">
    <div class="auth-card">
      <div class="auth-card-brand">
        <span class="brand-mark">PT</span>
        <div>
          <strong>性能测试平台</strong>
          <small>冷青工程工作台</small>
        </div>
      </div>
      <div class="auth-card-header">
        <h1>登录</h1>
        <p>使用账号进入项目、脚本与监控工作台。</p>
      </div>
      <a-form class="auth-form" layout="vertical" @submit.prevent>
        <a-form-item label="账号">
          <a-input v-model:value="loginForm.username" autocomplete="username" size="large" />
        </a-form-item>
        <a-form-item label="密码">
          <a-input
            v-model:value="loginForm.password"
            type="password"
            autocomplete="current-password"
            show-password
            size="large"
          />
        </a-form-item>
        <a-button type="primary" size="large" block :loading="loginLoading" @click="onLogin">进入平台</a-button>
      </a-form>
      <div class="auth-demo">
        <span>演示账号</span>
        <strong>admin / admin123</strong>
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
