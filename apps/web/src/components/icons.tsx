import type { SVGProps } from 'react';

type Props = SVGProps<SVGSVGElement>;
const base = { width: 24, height: 24, viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', strokeWidth: 1.8, strokeLinecap: 'round' as const, strokeLinejoin: 'round' as const };

export function HomeIcon(p: Props){return <svg {...base} {...p}><path d="M3 11.5 12 4l9 7.5"/><path d="M5.5 10.5V20h13v-9.5"/><path d="M9.5 20v-5h5v5"/></svg>}
export function SproutIcon(p: Props){return <svg {...base} {...p}><path d="M12 21V10"/><path d="M12 13c-4.8 0-7-2.7-7-6 4.8 0 7 2.3 7 6Z"/><path d="M12 10c4.5 0 7-2.4 7-6-4.4 0-7 2.4-7 6Z"/></svg>}
export function CompassIcon(p: Props){return <svg {...base} {...p}><circle cx="12" cy="12" r="9"/><path d="m15.5 8.5-2.2 4.8-4.8 2.2 2.2-4.8 4.8-2.2Z"/></svg>}
export function MoreIcon(p: Props){return <svg {...base} {...p}><circle cx="5" cy="12" r="1" fill="currentColor" stroke="none"/><circle cx="12" cy="12" r="1" fill="currentColor" stroke="none"/><circle cx="19" cy="12" r="1" fill="currentColor" stroke="none"/></svg>}
export function BellIcon(p: Props){return <svg {...base} {...p}><path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 8.5h18C21 15 18 15 18 8Z"/><path d="M9.7 20h4.6"/></svg>}
export function MapPinIcon(p: Props){return <svg {...base} {...p}><path d="M20 10c0 5-8 11-8 11S4 15 4 10a8 8 0 1 1 16 0Z"/><circle cx="12" cy="10" r="2.5"/></svg>}
export function ArrowIcon(p: Props){return <svg {...base} {...p}><path d="M5 12h14"/><path d="m14 7 5 5-5 5"/></svg>}
export function PlusIcon(p: Props){return <svg {...base} {...p}><circle cx="12" cy="12" r="9"/><path d="M12 8v8M8 12h8"/></svg>}
export function RainIcon(p: Props){return <svg {...base} {...p}><path d="M7 16.5H6a4 4 0 1 1 1.2-7.8A5.5 5.5 0 0 1 17.8 10a3.5 3.5 0 0 1 .2 7H17"/><path d="m8 19-1 2M12 19l-1 2M16 19l-1 2"/></svg>}
