<template>
  <a-config-provider :theme="antTheme">
    <RouterView />
  </a-config-provider>
</template>

<script setup lang="ts">
import { useTheme } from './composables/useTheme';

const { antTheme } = useTheme();
</script>
