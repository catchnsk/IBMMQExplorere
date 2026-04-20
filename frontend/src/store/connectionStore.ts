import { create } from 'zustand';
import type { MqConnectionResponse } from '../types';

interface ConnectionState {
  activeConfigId: number | null;
  activeConfig: MqConnectionResponse | null;
  setConnection: (config: MqConnectionResponse) => void;
  clearConnection: () => void;
}

export const useConnectionStore = create<ConnectionState>()((set) => ({
  activeConfigId: null,
  activeConfig: null,
  setConnection: (config) => set({ activeConfigId: config.id, activeConfig: config }),
  clearConnection: () => set({ activeConfigId: null, activeConfig: null }),
}));
