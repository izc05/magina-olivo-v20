import type { ComponentProps } from 'react';
import {
  ArrowRight,
  BellSimple,
  Briefcase,
  Camera,
  CalendarDots,
  ChartLineUp,
  CloudRain,
  CloudArrowDown,
  CompassRose,
  CurrencyEur,
  DotsThreeCircle,
  Drop,
  FileText,
  Factory,
  Flask,
  HouseLine,
  MapTrifold,
  Megaphone,
  Mountains,
  Newspaper,
  MapPinLine,
  Plant,
  PlusCircle,
  Scissors,
  SealCheck,
  Sparkle,
  Storefront,
  SunHorizon,
  Tree,
  WarningCircle,
  Wrench,
} from '@phosphor-icons/react/dist/ssr';

type Props = ComponentProps<typeof HouseLine>;

const defaults = {
  size: 24,
  weight: 'duotone' as const,
  mirrored: false,
};

export function HomeIcon(props: Props) { return <HouseLine {...defaults} {...props} />; }
export function SproutIcon(props: Props) { return <Plant {...defaults} {...props} />; }
export function CompassIcon(props: Props) { return <CompassRose {...defaults} {...props} />; }
export function MoreIcon(props: Props) { return <DotsThreeCircle {...defaults} {...props} />; }
export function BellIcon(props: Props) { return <BellSimple {...defaults} {...props} />; }
export function MapPinIcon(props: Props) { return <MapPinLine {...defaults} {...props} />; }
export function ArrowIcon(props: Props) { return <ArrowRight {...defaults} weight="bold" {...props} />; }
export function PlusIcon(props: Props) { return <PlusCircle {...defaults} {...props} />; }
export function RainIcon(props: Props) { return <CloudRain {...defaults} {...props} />; }

export function OliveTreeIcon(props: Props) { return <Tree {...defaults} {...props} />; }
export function PruningIcon(props: Props) { return <Scissors {...defaults} {...props} />; }
export function VerifiedIcon(props: Props) { return <SealCheck {...defaults} {...props} />; }
export function AlertIcon(props: Props) { return <WarningCircle {...defaults} {...props} />; }
export function WaterIcon(props: Props) { return <Drop {...defaults} {...props} />; }
export function DocumentIcon(props: Props) { return <FileText {...defaults} {...props} />; }
export function TodayIcon(props: Props) { return <SunHorizon {...defaults} {...props} />; }
export function CampaignIcon(props: Props) { return <ChartLineUp {...defaults} {...props} />; }
export function TerritoryMapIcon(props: Props) { return <MapTrifold {...defaults} {...props} />; }
export function ProfessionalIcon(props: Props) { return <Briefcase {...defaults} {...props} />; }
export function CalendarIcon(props: Props) { return <CalendarDots {...defaults} {...props} />; }
export function CostIcon(props: Props) { return <CurrencyEur {...defaults} {...props} />; }
export function TreatmentIcon(props: Props) { return <Flask {...defaults} {...props} />; }
export function NewsIcon(props: Props) { return <Newspaper {...defaults} {...props} />; }
export function EventIcon(props: Props) { return <CalendarDots {...defaults} {...props} />; }
export function MillIcon(props: Props) { return <Factory {...defaults} {...props} />; }
export function ServiceIcon(props: Props) { return <Wrench {...defaults} {...props} />; }
export function PlaceIcon(props: Props) { return <Mountains {...defaults} {...props} />; }
export function DirectoryIcon(props: Props) { return <Storefront {...defaults} {...props} />; }
export function PromotionIcon(props: Props) { return <Sparkle {...defaults} {...props} />; }
export function AnnouncementIcon(props: Props) { return <Megaphone {...defaults} {...props} />; }
export function CameraIcon(props: Props) { return <Camera {...defaults} {...props} />; }
export function OfflineIcon(props: Props) { return <CloudArrowDown {...defaults} {...props} />; }
