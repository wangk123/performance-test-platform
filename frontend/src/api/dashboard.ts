import type { Project } from '../types';
import { request } from './http';

export type DashboardSummary = {
  activeProjectCount: number;
  archivedProjectCount: number;
  scriptAssetTotal: number;
  taskTotal: number;
  recentProjects: Project[];
};

export function getDashboardSummaryApi() {
  return request<DashboardSummary>('/api/dashboard/summary');
}
