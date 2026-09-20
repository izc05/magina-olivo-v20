import type { Metadata } from "next";
import { LegalPage } from "@/components/LegalPage";

export const metadata: Metadata = {
  title: "Términos de uso",
  description: "Términos de uso de la web Mágina Olivo.",
};

export default function TerminosPage() {
  return (
    <LegalPage eyebrow="Información legal" title="Términos de uso">
      <h2>1. Uso de la web</h2>
      <p>
        La web presenta el proyecto Mágina Olivo y sus funciones previstas.
        Mientras el producto continúe en desarrollo, determinadas pantallas,
        datos y ejemplos mostrados deben entenderse como demostraciones de
        producto y no como un servicio ya disponible.
      </p>
      <h2>2. Información agrícola</h2>
      <p>
        El contenido de la web no sustituye asesoramiento técnico, agronómico,
        administrativo o legal específico para una explotación.
      </p>
      <h2>3. Disponibilidad</h2>
      <p>
        Las funciones anunciadas como futuras, en desarrollo o próximas pueden
        modificarse antes de la publicación definitiva de la aplicación.
      </p>
      <h2>4. Propiedad del contenido</h2>
      <p>
        La identidad visual, textos, diseños y materiales propios de Mágina
        Olivo no deben reutilizarse de forma que implique relación oficial con
        el proyecto sin autorización.
      </p>
    </LegalPage>
  );
}
