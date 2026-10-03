import type { ReactNode } from "react";
import { MiLayout } from "@/components/layouts/MiLayout";

export default function MiGroupLayout({
  children,
}: Readonly<{ children: ReactNode }>) {
  return <MiLayout>{children}</MiLayout>;
}
