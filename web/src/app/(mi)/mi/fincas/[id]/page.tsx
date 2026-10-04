import { miReadAdapter } from "@/data/mock/mi-demo";
import { FarmDetailPage } from "@/features/mi/MiPages";

export function generateStaticParams() {
  return miReadAdapter.getFarms().map(({ id }) => ({ id }));
}

export const dynamicParams = false;
export const metadata = { title: "Detalle de finca · Demo" };

export default async function Page({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  return <FarmDetailPage id={id} />;
}
