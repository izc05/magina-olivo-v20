import { miReadAdapter } from "@/data/mock/mi-demo";
import { CampaignDetailPage } from "@/features/mi/MiPages";

export function generateStaticParams() {
  return miReadAdapter.getCampaigns().map(({ id }) => ({ id }));
}

export const dynamicParams = false;
export const metadata = { title: "Detalle de campaña · Demo" };

export default async function Page({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  return <CampaignDetailPage id={id} />;
}
