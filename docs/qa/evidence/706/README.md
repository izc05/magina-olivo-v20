# #706 — capturas reales de contraste

API 35, AVD `Codex517_API35`, densidad 440 dpi, fuente 1,3x. Anchuras 360/390/430 dp: 990/1072/1182 px. Las imágenes muestran datos agrícolas ficticios conservados para QA.

- `before-*`: 30 capturas del AVD conservado, APK CI DEV 1606, main `fe79efd17c67450d0923868be110a52c33e6d4a3`.
- `after-*`: 30 capturas del AVD desechable emulator-5580, rama `codex/706-surface-contrast` basada en main `45e21fe05624ba68a6020c43258ef75e1351c700`, después del último ajuste de FilterChip. APK local `0.5.0-dev`, código 1, SHA-256 `252189da40b827d15884137b5492b8114dc2f8eff6fe765d68a4d5a170bb4778`.
- `after-expense-keyboard-*`: tres capturas adicionales con el campo Concepto enfocado y teclado realmente visible, comprobado mediante `mInputShown=true` y `mIsInputViewShown=true`. No se escribió ni guardó el formulario.

El AVD desechable recibió una copia verificada del respaldo QA; el AVD conservado no recibió esta APK local ni fixtures. Los formularios se cancelaron. Fotografías, CTA olivo y categorías conservan su identidad.

La APK local no contiene la configuración meteorológica pública suministrada por CI y muestra su estado de fuente no configurada. No es evidencia de conexión meteorológica para B4. Los cambios independientes #710/#712 pueden producir diferencias de contenido; las capturas no son una prueba de identidad píxel a píxel.

El recorte previo de «Mi Campo» a 360 dp y el salto de línea de Avisos con letra grande se conservan para el bloque #705. TalkBack, luz exterior y aceptación física de Gate21 siguen pendientes.

`manifest.json` enumera las 63 imágenes con tamaño y SHA-256. La PR y sus controles por SHA se registran en el dossier [ANDROID-706-SURFACE-CONTRAST-2026-10-09.md](../../ANDROID-706-SURFACE-CONTRAST-2026-10-09.md).

| Superficie | Antes | Después |
| --- | --- | --- |
| Perfil 360 dp | [antes](before-profile-360-font13.png) | [después](after-profile-360-font13.png) |
| Cuaderno 390 dp | [antes](before-notebook-390-font13.png) | [después](after-notebook-390-font13.png) |
| Campaña 430 dp | [antes](before-campaign-detail-430-font13.png) | [después](after-campaign-detail-430-font13.png) |
| Parcela 390 dp | [antes](before-parcel-390-font13.png) | [después](after-parcel-390-font13.png) |
| Tratamiento 360 dp | [antes](before-treatment-360-font13.png) | [después](after-treatment-360-font13.png) |
| Gasto 430 dp | [antes](before-expense-430-font13.png) | [después](after-expense-430-font13.png) |
| Gasto y teclado 390 dp | — | [foco y teclado](after-expense-keyboard-390-font13.png) |
