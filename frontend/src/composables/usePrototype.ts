// 原型开关（spec §13 原型载体约定）：
// URL ?proto=<name> 免重启即时开，'all' 全开。页面加载时解析一次，不做响应式——原型评审场景足够。
// 若需编译期管控（VITE_PROTO 环境变量 + tree-shake），补 src/vite-env.d.ts 声明后扩展此处。
// 功能接上真实 API 后，调用方与 v-if 一并删除，flag 退役。

const urlProto = new URLSearchParams(window.location.search).get('proto');

export function isProto(name: string): boolean {
  return urlProto === name || urlProto === 'all';
}
