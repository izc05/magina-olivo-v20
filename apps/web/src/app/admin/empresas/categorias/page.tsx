import type { Metadata } from 'next';
import { BusinessCategoryAdmin } from '@/components/business-category-admin';
import '../../admin.css';

export const metadata: Metadata = {
  title: 'Categorías de empresas · Administración · Mágina Olivo',
  description: 'Gestión de la taxonomía del directorio de empresas y servicios.',
  robots: { index: false, follow: false },
};

export default function AdminBusinessCategoriesPage() {
  return <BusinessCategoryAdmin />;
}
