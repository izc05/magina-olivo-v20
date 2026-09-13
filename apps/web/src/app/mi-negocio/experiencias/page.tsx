import type { Metadata } from 'next';
import { MyBusinessExperiences } from '@/components/my-business-experiences';

export const metadata: Metadata = {
  title: 'Experiencias · Mi negocio · Mágina Olivo',
  description: 'Gestiona experiencias, sesiones y reservas de tu negocio.',
  robots: { index: false, follow: false },
};

export default function MyBusinessExperiencesPage(){return <MyBusinessExperiences/>;}
