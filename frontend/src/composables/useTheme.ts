import { computed, ref, watch } from 'vue';
import { theme as antdTheme } from 'ant-design-vue';

export type ThemeMode = 'default' | 'dark';

const STORAGE_KEY = 'performance-platform-theme';
const themeMode = ref<ThemeMode>(readThemeMode());
const themeModeOptions = [
  { label: '默认风格', value: 'default' },
  { label: '暗黑风格', value: 'dark' },
];

const resolvedTheme = computed<ThemeMode>(() => themeMode.value);

const antTheme = computed(() => ({
  algorithm: themeMode.value === 'dark' ? antdTheme.darkAlgorithm : antdTheme.defaultAlgorithm,
}));

let initialized = false;

export function useTheme() {
  if (!initialized) {
    initialized = true;
    bindTheme();
  }

  return {
    themeMode,
    themeModeOptions,
    resolvedTheme,
    antTheme,
  };
}

function bindTheme() {
  watch(themeMode, (mode) => {
    if (typeof window !== 'undefined') {
      window.localStorage.setItem(STORAGE_KEY, mode);
    }
  }, { immediate: true });

  watch(resolvedTheme, (theme) => {
    if (typeof document !== 'undefined') {
      document.documentElement.dataset.theme = theme;
    }
  }, { immediate: true });
}

function readThemeMode(): ThemeMode {
  if (typeof window === 'undefined') {
    return 'default';
  }
  const value = window.localStorage.getItem(STORAGE_KEY);
  return value === 'dark' || value === 'default' ? value : 'default';
}
