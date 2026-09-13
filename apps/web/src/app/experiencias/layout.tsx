import type { ReactNode } from 'react';
import { TerritoryShell } from '@/components/territory-shell';

export default function Layout({ children }: { children: ReactNode }) {
  return <TerritoryShell>{children}</TerritoryShell>;
}