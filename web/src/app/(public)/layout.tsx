import type { ReactNode } from "react";
import { PublicLayout } from "@/components/layouts/PublicLayout";

export default function PublicGroupLayout({
  children,
}: Readonly<{ children: ReactNode }>) {
  return <PublicLayout>{children}</PublicLayout>;
}
