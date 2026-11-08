import { request } from './http';

export interface AdministrationStatus {
  role: 'ADMIN' | 'USER';
  administrator: boolean;
}

export function getAdministrationStatus(): Promise<AdministrationStatus> {
  return request({ url: '/administration/status', method: 'GET' });
}
