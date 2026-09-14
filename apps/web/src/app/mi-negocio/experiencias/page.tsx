import type { Metadata } from 'next';
import { MyBusinessExperiences } from '@/components/my-business-experiences';
import { MyBusinessExperienceOperations } from '@/components/my-business-experience-operations';

export const metadata: Metadata = {
  title: 'Experiencias · Mi negocio · Mágina Olivo',
  description: 'Gestiona experiencias, sesiones y reservas de tu negocio.',
  robots: { index: false, follow: false },
};

export default function MyBusinessExperiencesPage(){return <><MyBusinessExperiences/><MyBusinessExperienceOperations/></>;}
