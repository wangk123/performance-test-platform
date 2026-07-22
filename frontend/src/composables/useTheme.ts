import { computed, ref, watch } from 'vue';
import { theme as antdTheme } from 'ant-design-vue';
import {
  ACCENT_DARK,
  ACCENT_LIGHT,
  ACCENT_SOFT_DARK,
  ACCENT_SOFT_LIGHT,
  CANVAS_DARK,
  CANVAS_LIGHT,
  INK_DARK,
  INK_LIGHT,
  LINE_DARK,
  LINE_LIGHT,
  SURFACE_DARK,
  SURFACE_LIGHT,
} from '../constants/design-tokens';

export type ThemeMode = 'default' | 'dark';

const STORAGE_KEY = 'performance-platform-theme';
const themeMode = ref<ThemeMode>(readThemeMode());
const themeModeOptions = [
  { label: '默认风格', value: 'default' },
  { label: '暗黑风格', value: 'dark' },
];

const resolvedTheme = computed<ThemeMode>(() => themeMode.value);

const antTheme = computed(() => {
  const dark = themeMode.value === 'dark';
  return {
    algorithm: dark ? antdTheme.darkAlgorithm : antdTheme.defaultAlgorithm,
    token: {
      colorPrimary: dark ? ACCENT_DARK : ACCENT_LIGHT,
      colorInfo: dark ? ACCENT_DARK : ACCENT_LIGHT,
      colorBgBase: dark ? CANVAS_DARK : CANVAS_LIGHT,
      colorBgContainer: dark ? SURFACE_DARK : SURFACE_LIGHT,
      colorText: dark ? INK_DARK : INK_LIGHT,
      colorBorder: dark ? LINE_DARK : LINE_LIGHT,
      colorPrimaryBg: dark ? ACCENT_SOFT_DARK : ACCENT_SOFT_LIGHT,
      borderRadius: 6,
      fontFamily:
        '"IBM Plex Sans", "PingFang SC", "Microsoft YaHei", -apple-system, BlinkMacSystemFont, sans-serif',
    },
  };
});

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
