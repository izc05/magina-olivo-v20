import { MiPlaceholderPage } from "@/features/scaffold/MiPlaceholderPage";

export function generateStaticParams() {
  return [{ id: "demo" }];
}

export const dynamicParams = false;

export const metadata = { title: "Detalle de finca · Demo" };

export default async function FarmDetailPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  return <MiPlaceholderPage title={`Detalle de finca · ${id}`} />;
}
