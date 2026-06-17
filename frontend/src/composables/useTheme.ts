import { computed, ref, watch } from 'vue';
import { theme as antdTheme } from 'ant-design-vue';

export type ThemeMode = 'system' | 'light' | 'dark';
export type ResolvedTheme = 'light' | 'dark';

const STORAGE_KEY = 'performance-platform-theme';
const themeMode = ref<ThemeMode>(readThemeMode());
const systemTheme = ref<ResolvedTheme>(readSystemTheme());
const themeModeOptions = [
  { label: '跟随系统', value: 'system' },
  { label: '浅色', value: 'light' },
  { label: '深色', value: 'dark' },
];

const resolvedTheme = computed<ResolvedTheme>(() => (
  themeMode.value === 'system' ? systemTheme.value : themeMode.value
));

const antTheme = computed(() => ({
  algorithm: resolvedTheme.value === 'dark' ? antdTheme.darkAlgorithm : antdTheme.defaultAlgorithm,
  token: {
    colorPrimary: '#2bbf9f',
    borderRadius: 8,
    fontFamily: '"Avenir Next", "PingFang SC", "Microsoft YaHei", sans-serif',
  },
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
  if (typeof window !== 'undefined') {
    const media = window.matchMedia('(prefers-color-scheme: dark)');
    media.addEventListener('change', () => {
      systemTheme.value = readSystemTheme();
    });
  }

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
    return 'system';
  }
  const value = window.localStorage.getItem(STORAGE_KEY);
  return value === 'light' || value === 'dark' || value === 'system' ? value : 'system';
}

function readSystemTheme(): ResolvedTheme {
  if (typeof window === 'undefined') {
    return 'dark';
  }
  return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
}
