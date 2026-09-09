'use client';

import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { apiBaseUrl, apiFetch, ApiRequestError, ApiUnavailableError } from '../lib/api-client';

export type AuthUser = {
  id: string;
  display_name: string;
  primary_email: string | null;
  avatar_url: string | null;
};

export type WorkspaceMembership = {
  workspace_id: string;
  workspace_name: string;
  workspace_type: 'family' | 'professional' | 'organization';
  role: 'owner' | 'admin' | 'manager' | 'member' | 'worker' | 'viewer';
};

type SessionPayload = {
  user: AuthUser;
  workspaces: WorkspaceMembership[];
};

type LoginPayload = SessionPayload & {
  created: boolean;
  selected_workspace_id: string | null;
  session_expires_at: string;
};

type AuthStatus = 'loading' | 'anonymous' | 'authenticated';

type AuthContextValue = {
  status: AuthStatus;
  user: AuthUser | null;
  workspaces: WorkspaceMembership[];
  selectedWorkspaceId: string | null;
  apiConfigured: boolean;
  selectWorkspace: (workspaceId: string) => void;
  signInWithGoogleCredential: (credential: string) => Promise<void>;
  logout: () => Promise<void>;
  refreshSession: () => Promise<void>;
};

const AuthContext = createContext<AuthContextValue | null>(null);
const WORKSPACE_STORAGE_KEY = 'magina:selected-workspace-id';

function chooseWorkspace(workspaces: WorkspaceMembership[], preferred?: string | null) {
  if (preferred && workspaces.some((workspace) => workspace.workspace_id === preferred)) return preferred;
  return workspaces[0]?.workspace_id ?? null;
}

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [status, setStatus] = useState<AuthStatus>('loading');
  const [user, setUser] = useState<AuthUser | null>(null);
  const [workspaces, setWorkspaces] = useState<WorkspaceMembership[]>([]);
  const [selectedWorkspaceId, setSelectedWorkspaceId] = useState<string | null>(null);

  const applySession = useCallback((payload: SessionPayload, preferred?: string | null) => {
    const stored = typeof window !== 'undefined' ? window.localStorage.getItem(WORKSPACE_STORAGE_KEY) : null;
    const selected = chooseWorkspace(payload.workspaces, preferred ?? stored);
    setUser(payload.user);
    setWorkspaces(payload.workspaces);
    setSelectedWorkspaceId(selected);
    if (selected && typeof window !== 'undefined') window.localStorage.setItem(WORKSPACE_STORAGE_KEY, selected);
    setStatus('authenticated');
  }, []);

  const becomeAnonymous = useCallback(() => {
    setUser(null);
    setWorkspaces([]);
    setSelectedWorkspaceId(null);
    setStatus('anonymous');
  }, []);

  const refreshSession = useCallback(async () => {
    if (!apiBaseUrl) {
      becomeAnonymous();
      return;
    }

    try {
      const session = await apiFetch<SessionPayload>('/api/v1/auth/session');
      applySession(session);
    } catch (error) {
      if (error instanceof ApiUnavailableError || (error instanceof ApiRequestError && error.status === 401)) {
        becomeAnonymous();
        return;
      }
      console.error('Unable to refresh Mágina session', error);
      becomeAnonymous();
    }
  }, [applySession, becomeAnonymous]);

  useEffect(() => {
    void refreshSession();
  }, [refreshSession]);

  const selectWorkspace = useCallback((workspaceId: string) => {
    if (!workspaces.some((workspace) => workspace.workspace_id === workspaceId)) return;
    setSelectedWorkspaceId(workspaceId);
    window.localStorage.setItem(WORKSPACE_STORAGE_KEY, workspaceId);
  }, [workspaces]);

  const signInWithGoogleCredential = useCallback(async (credential: string) => {
    const payload = await apiFetch<LoginPayload>('/api/v1/auth/google', {
      method: 'POST',
      body: JSON.stringify({ credential }),
    });
    applySession(payload, payload.selected_workspace_id);
  }, [applySession]);

  const logout = useCallback(async () => {
    if (apiBaseUrl) {
      try {
        await apiFetch<void>('/api/v1/auth/logout', { method: 'POST' });
      } catch (error) {
        console.error('Unable to revoke Mágina session', error);
      }
    }
    window.localStorage.removeItem(WORKSPACE_STORAGE_KEY);
    becomeAnonymous();
  }, [becomeAnonymous]);

  const value = useMemo<AuthContextValue>(() => ({
    status,
    user,
    workspaces,
    selectedWorkspaceId,
    apiConfigured: Boolean(apiBaseUrl),
    selectWorkspace,
    signInWithGoogleCredential,
    logout,
    refreshSession,
  }), [logout, refreshSession, selectWorkspace, selectedWorkspaceId, signInWithGoogleCredential, status, user, workspaces]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used inside AuthProvider');
  return context;
}
