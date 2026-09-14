import type { Metadata } from 'next';
import type { ReactNode } from 'react';
import { AdminRouteGate } from '../../components/admin-route-gate';
import './admin.css';
import './admin-polish.css';

export const metadata: Metadata = {
  robots: {
    index: false,
    follow: false,
    nocache: true,
    googleBot: {
      index: false,
      follow: false,
      noimageindex: true,
    },
  },
};

export default function AdminLayout({ children }: Readonly<{ children: ReactNode }>) {
  return <AdminRouteGate>{children}</AdminRouteGate>;
}
