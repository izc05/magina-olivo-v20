import type { Metadata } from "next";
import { LegalPage } from "@/components/LegalPage";

export const metadata: Metadata = {
  title: "Aviso legal",
  description: "Aviso legal de Mágina Olivo.",
};

export default function AvisoLegalPage() {
  return (
    <LegalPage eyebrow="Información legal" title="Aviso legal">
      <h2>Proyecto</h2>
      <p>
        Mágina Olivo es un proyecto digital orientado a la gestión del olivar.
        La presente web se encuentra en fase de desarrollo.
      </p>
      <h2>Contacto</h2>
      <p>
        Correo de contacto del proyecto:
        {" "}<a href="mailto:hola@maginaolivo.es">hola@maginaolivo.es</a>.
      </p>
      <h2>Contenidos de demostración</h2>
      <p>
        Los nombres de fincas, superficies, producciones, rendimientos, precios,
        previsiones y demás cifras mostradas en prototipos de interfaz pueden
        ser datos de demostración y no deben interpretarse como información
        operativa o actual.
      </p>
      <h2>Publicación definitiva</h2>
      <p>
        Este aviso deberá completarse con los datos jurídicos definitivos del
        responsable antes de la apertura comercial o pública del servicio.
      </p>
    </LegalPage>
  );
}
