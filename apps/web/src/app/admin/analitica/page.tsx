import type { Metadata } from 'next';
import { AdminAnalyticsConsole } from '../../../components/admin-analytics-console';
import '../admin.css';

export const metadata: Metadata = {
  title: 'Analítica | Mágina Olivo Admin',
  description: 'Series históricas y exportación agregada de la plataforma Mágina Olivo V20.',
  robots: { index: false, follow: false },
};

export default function AdminAnalyticsPage() {
  return <AdminAnalyticsConsole />;
}
