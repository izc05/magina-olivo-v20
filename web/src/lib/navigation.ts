export const publicNavigation = [
  { label: "Inicio", href: "/" },
  { label: "La app", href: "/funciones" },
  { label: "Funcionalidades", href: "/funciones" },
  { label: "Territorio", href: "/#territorio" },
  { label: "Empresas", href: "/anunciate#empresas" },
] as const;

export const miNavigationGroups = [
  {
    label: null,
    items: [{ label: "Inicio", href: "/mi", icon: "home" }],
  },
  {
    label: "Mi Campo",
    items: [
      { label: "Fincas", href: "/mi/fincas", icon: "farm" },
      { label: "Parcelas", href: "/mi/parcelas", icon: "parcel" },
      { label: "Mapa", href: "/mi/mapa", icon: "map" },
    ],
  },
  {
    label: "Gestión",
    items: [
      { label: "Cuaderno", href: "/mi/cuaderno", icon: "notebook" },
      { label: "Campañas", href: "/mi/campanas", icon: "campaign" },
      { label: "Documentos", href: "/mi/documentos", icon: "document" },
      { label: "Informes", href: "/mi/informes", icon: "report" },
    ],
  },
  {
    label: "Consulta",
    items: [
      { label: "Tiempo", href: "/mi/tiempo", icon: "weather" },
      { label: "Radar", href: "/mi/tiempo#radar", icon: "radar" },
      { label: "Mercado", href: "/mi/mercado", icon: "market" },
    ],
  },
  {
    label: "Territorio",
    items: [
      { label: "Cooperativas", href: "/mi/cooperativa", icon: "cooperative" },
      { label: "Noticias", href: null, icon: "news" },
      { label: "Avisos", href: null, icon: "alerts" },
      { label: "Empresas", href: null, icon: "business" },
    ],
  },
  {
    label: "Cuenta",
    items: [
      { label: "Perfil", href: "/mi/perfil", icon: "profile" },
      { label: "Cuenta", href: "/mi/cuenta", icon: "account" },
    ],
  },
] as const;
