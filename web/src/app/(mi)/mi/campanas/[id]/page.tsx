import { MiPlaceholderPage } from "@/features/scaffold/MiPlaceholderPage";

export function generateStaticParams() {
  return [{ id: "demo" }];
}

export const dynamicParams = false;

export const metadata = { title: "Detalle de campaña · Demo" };

export default async function CampaignDetailPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  return <MiPlaceholderPage title={`Detalle de campaña · ${id}`} />;
}
