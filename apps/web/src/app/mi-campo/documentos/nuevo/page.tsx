import { BottomNav } from '@/components/bottom-nav';
import { FarmDocumentUploadClient } from '@/components/farm-document-upload-client';
import { Topbar } from '@/components/topbar';

export default function NewFarmDocumentPage() {
  return <main className="app-shell"><Topbar/><div className="page mi-campo-page"><FarmDocumentUploadClient/></div><BottomNav active="/mi-campo"/></main>;
}
