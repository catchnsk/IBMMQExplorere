import { create } from 'zustand';
import { persist } from 'zustand/middleware';

interface AuthState {
  username: string | null;
  roles: string[];
  isAuthenticated: boolean;
  setUser: (username: string, roles: string[]) => void;
  clearUser: () => void;
  isAdmin: () => boolean;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set, get) => ({
      username: null,
      roles: [],
      isAuthenticated: false,
      setUser: (username, roles) => set({ username, roles, isAuthenticated: true }),
      clearUser: () => set({ username: null, roles: [], isAuthenticated: false }),
      isAdmin: () => get().roles.includes('ADMIN'),
    }),
    { name: 'mq-auth' }
  )
);
