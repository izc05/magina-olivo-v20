import type { Metadata } from "next";
import { LegalPage } from "@/components/LegalPage";

export const metadata: Metadata = {
  title: "Privacidad",
  description: "Información de privacidad de Mágina Olivo.",
  alternates: { canonical: "/privacidad" },
};

export default function PrivacidadPage() {
  return (
    <LegalPage eyebrow="Información legal" title="Política de privacidad">
      <h2>1. Estado actual del proyecto</h2>
      <p>
        Mágina Olivo está en fase de desarrollo. Esta web tiene actualmente un
        carácter informativo y promocional.
      </p>
      <h2>2. Datos de contacto</h2>
      <p>
        Si contactas por correo electrónico, los datos que incluyas en tu mensaje
        se utilizarán únicamente para responder a tu consulta y mantener la
        conversación relacionada con el proyecto.
      </p>
      <h2>3. Analítica y cookies</h2>
      <p>
        Antes de incorporar herramientas de analítica, seguimiento o marketing,
        esta política y el sistema de consentimiento deberán actualizarse para
        reflejar de forma exacta las tecnologías utilizadas.
      </p>
      <h2>4. Aplicación</h2>
      <p>
        La aplicación Android contará con su propia información de privacidad
        antes de distribuirse públicamente, adaptada a los datos y permisos que
        realmente utilice la versión publicada.
      </p>
      <h2>5. Contacto</h2>
      <p>
        Para cuestiones relacionadas con privacidad puedes escribir a
        {" "}<a href="mailto:hola@maginaolivo.es">hola@maginaolivo.es</a>.
      </p>
    </LegalPage>
  );
}
