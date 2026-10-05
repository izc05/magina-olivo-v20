import type { Metadata } from "next";
import type { ReactNode } from "react";
import { MiLayout } from "@/components/layouts/MiLayout";

export const metadata: Metadata = {
  robots: { index: false, follow: false },
};

export default function MiGroupLayout({
  children,
}: Readonly<{ children: ReactNode }>) {
  return <MiLayout>{children}</MiLayout>;
}
