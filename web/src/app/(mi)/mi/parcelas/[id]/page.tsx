import { miReadAdapter } from "@/data/mock/mi-demo";
import { ParcelDetailPage } from "@/features/mi/MiPages";

export function generateStaticParams() {
  return miReadAdapter.getParcels().map(({ id }) => ({ id }));
}

export const dynamicParams = false;
export const metadata = { title: "Detalle de parcela · Demo" };

export default async function Page({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  return <ParcelDetailPage id={id} />;
}
