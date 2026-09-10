# Mágina Olivo — Admin V20

Estado: **estructura definida; implementación visual pendiente**.

## 1. Principio

Admin gobierna la plataforma y el contenido público. No es Mi Campo con más permisos.

Separación obligatoria:

```text
Cuenta / autenticación
├── identidad de usuario
├── sesiones
└── workspaces privados

Mi Campo
├── fincas
├── trabajos
├── cosechas
└── documentos privados

Admin de plataforma
├── usuarios y soporte
├── contenido público
├── territorio
├── directorio
├── publicidad
├── fuentes/datos
├── moderación
└── configuración
```

Los roles administrativos de plataforma son distintos de los roles agrícolas (`propietario`, `trabajador`, `cliente`, etc.).

## 2. Roles de plataforma

Base prevista:
- `super_admin`: configuración crítica y permisos;
- `admin`: gestión general;
- `editor`: contenidos, noticias, eventos, fichas públicas;
- `moderator`: revisión/moderación;
- `commercial`: negocios, campañas y patrocinio;
- `support`: consulta operativa limitada y soporte;
- `data_manager`: fuentes, importaciones y calidad de datos.

Los permisos deben ser por capacidad, no solo por nombre de rol.

## 3. Panel inicial

Admin debe priorizar trabajo pendiente:

```text
ADMIN
├── pendientes de revisión
├── errores de fuentes/importaciones
├── contenido sin publicar
├── negocios reclamados
├── campañas activas
├── incidencias de usuarios
└── estado de servicios
```

No usar el dashboard como colección decorativa de KPIs.

## 4. Usuarios

Funciones:
- búsqueda de usuario;
- estado de cuenta;
- perfil público;
- memberships/workspaces sin exponer documentos privados salvo capacidad explícita y caso de soporte autorizado;
- bloqueos/suspensiones;
- historial de acciones administrativas;
- solicitudes de ayuda o reclamación.

Privacidad: un administrador editorial no necesita acceso a Mi Campo.

## 5. Territorio

Catálogo maestro:
- municipio oficial;
- pueblo/localidad visible;
- provincia;
- coordenadas/centroide;
- códigos oficiales;
- imagen principal y derechos;
- estado activo;
- alias/búsqueda.

Debe soportar la diferencia entre entidad administrativa y localidad reconocida por el usuario.

## 6. Contenido

Tipos iniciales:
- noticias;
- eventos;
- guías/consejos;
- campañas territoriales;
- contenido de pueblos;
- avisos informativos.

Estados:
- borrador;
- revisión;
- programado;
- publicado;
- archivado;
- rechazado.

Cada publicación conserva autor/editor, timestamps y revisión.

## 7. Almazaras y cooperativas

Admin gestiona:
- ficha;
- ubicación;
- contactos;
- marcas;
- servicios;
- campaña/horarios;
- imágenes;
- fuentes;
- nivel de verificación;
- reclamación por propietario/representante.

Nunca mezclar una ficha pública con los registros privados de entrega de los agricultores.

## 8. Negocios y Cerca de ti

Flujo:

```text
Ficha detectada/importada
→ pendiente de revisión
→ publicada gratuita
→ posible reclamación
→ verificada
→ posible plan destacado/patrocinado
```

Campos comerciales separados de los datos objetivos de la ficha.

## 9. Publicidad y promociones

Entidades:
- anunciante;
- campaña;
- creatividad;
- ubicación/segmentación territorial;
- fechas;
- estado;
- presupuesto/contrato futuro;
- métricas agregadas;
- etiqueta de patrocinio.

Reglas:
1. patrocinio siempre visible como tal;
2. no pagar para alterar avisos agronómicos, seguridad o hechos;
3. no mezclar ranking editorial y ranking comercial sin señalización;
4. segmentación respetuosa con privacidad.

## 10. Fuentes y calidad de datos

Admin debe saber de dónde sale cada dato cambiante.

Registro de fuente:
- proveedor;
- tipo;
- endpoint/adaptador;
- última actualización;
- estado;
- errores recientes;
- licencia/condiciones;
- frecuencia;
- responsable.

Aplicable a:
- AEMET;
- radar;
- Catastro;
- SIGPAC;
- precios;
- RAIF;
- noticias/importadores;
- OSM/Overpass;
- otros futuros.

## 11. Moderación

Cola común para:
- fichas reclamadas;
- correcciones de negocios;
- imágenes/derechos;
- eventos enviados;
- contenido comunitario futuro;
- reportes.

Toda decisión importante debe quedar auditada.

## 12. Configuración

Configuración separada por dominio:
- general;
- territorio;
- meteorología;
- notificaciones;
- contenido;
- publicidad;
- gamificación;
- integraciones;
- límites/feature flags.

Cambios críticos requieren capacidad superior y registro de auditoría.

## 13. Auditoría

`AdminAuditEvent` conceptual:
- actor;
- acción;
- tipo/id de objetivo;
- antes/después resumido cuando proceda;
- timestamp;
- razón opcional;
- request/correlation id.

No borrar silenciosamente decisiones editoriales o comerciales relevantes.

## 14. Seguridad

- mínimo privilegio;
- permisos por capacidad;
- separación plataforma/workspace;
- endpoints admin independientes de endpoints públicos y Mi Campo;
- operaciones sensibles protegidas contra CSRF/replay según arquitectura;
- no exponer secretos o tokens en UI;
- logs sin datos privados innecesarios.

## 15. Navegación Admin

```text
Admin
├── Inicio
├── Contenido
├── Territorio
├── Almazaras
├── Negocios
├── Publicidad
├── Usuarios
├── Moderación
├── Fuentes y datos
└── Configuración
```

Submódulos aparecen dentro de estas superficies, no como decenas de entradas principales.

## 16. Reglas cerradas

1. Roles Admin y roles agrícolas son dominios diferentes.
2. Editor no recibe acceso automático a Mi Campo privado.
3. Todo dato público cambiante tiene fuente/fecha/estado de revisión.
4. Publicidad se separa de contenido objetivo.
5. Acciones administrativas relevantes son auditables.
6. Las integraciones se monitorizan como fuentes/adaptadores.
7. Admin sirve para operar la plataforma, no para duplicar todas las pantallas de usuario.
