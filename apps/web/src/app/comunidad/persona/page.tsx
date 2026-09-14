'use client';

import Link from 'next/link';
import { useEffect, useState } from 'react';
import { BottomNav } from '@/components/bottom-nav';
import { Topbar } from '@/components/topbar';
import { ApiRequestError, apiFetch } from '@/lib/api-client';
import styles from './profile.module.css';

type CommunityMember = {
  id: string;
  display_name: string;
  avatar_url: string | null;
  municipality: string | null;
  bio: string | null;
  public_role: string | null;
  published_posts: number;
  published_comments: number;
  likes_received: number;
};

const roleLabels: Record<string, string> = {
  agricultor: 'Agricultor/a',
  propietario: 'Propietario/a',
  trabajador: 'Trabajador/a',
  profesional_agricola: 'Profesional agrícola',
  tecnico: 'Técnico/a',
  empresa: 'Empresa',
  otro: 'Miembro de Mágina',
};

export default function CommunityMemberPage() {
  const [member, setMember] = useState<CommunityMember | null>(null);
  const [loading, setLoading] = useState(true);
  const [notFound, setNotFound] = useState(false);
  const [invalidReference, setInvalidReference] = useState(false);

  useEffect(() => {
    let cancelled = false;
    const id = new URLSearchParams(window.location.search).get('id')?.trim() ?? '';
    if (!id) {
      setInvalidReference(true);
      setLoading(false);
      return () => { cancelled = true; };
    }

    setLoading(true);
    setNotFound(false);
    setInvalidReference(false);
    apiFetch<{ member: CommunityMember }>(`/api/v1/public/community/members/${encodeURIComponent(id)}`)
      .then((payload) => { if (!cancelled) setMember(payload.member); })
      .catch((cause) => {
        if (cancelled) return;
        setMember(null);
        setNotFound(cause instanceof ApiRequestError && cause.status === 404);
      })
      .finally(() => { if (!cancelled) setLoading(false); });

    return () => { cancelled = true; };
  }, []);

  return <main className="app-shell">
    <Topbar />
    <div className="page">
      <section className={styles.shell}>
        <Link href="/comunidad/descubrir" className={styles.back}>← Volver a descubrir</Link>
        {loading ? <div className={styles.state}>Cargando perfil público…</div> : null}
        {!loading && invalidReference ? <div className={styles.state}><h1>Perfil no indicado</h1><p>Abre esta ficha desde una publicación o desde el buscador de Comunidad Mágina.</p></div> : null}
        {!loading && !invalidReference && !member ? <div className={styles.state}><h1>{notFound ? 'Perfil no público' : 'Perfil no disponible'}</h1><p>Esta ficha solo existe cuando la persona ha elegido expresamente un perfil público en Mágina Olivo.</p></div> : null}
        {member ? <>
          <header className={styles.hero}>
            <div className={styles.avatar} aria-hidden="true">{member.display_name.slice(0, 1).toLocaleUpperCase('es')}</div>
            <div><span className="eyebrow">COMUNIDAD MÁGINA</span><h1>{member.display_name}</h1><p>{roleLabels[member.public_role ?? 'otro'] ?? 'Miembro de Mágina'}{member.municipality ? ` · ${member.municipality}` : ''}</p></div>
          </header>
          {member.bio ? <article className={styles.bio}><h2>Sobre esta persona</h2><p>{member.bio}</p></article> : null}
          <section className={styles.stats} aria-label="Actividad pública en Comunidad Mágina">
            <article><strong>{member.published_posts}</strong><span>Publicaciones</span></article>
            <article><strong>{member.published_comments}</strong><span>Comentarios</span></article>
            <article><strong>{member.likes_received}</strong><span>Me gusta recibidos</span></article>
          </section>
          <p className={styles.privacy}>Esta ficha muestra únicamente información que la persona ha decidido hacer pública. No incluye fincas, parcelas, documentos, email ni datos de Mi Campo.</p>
        </> : null}
      </section>
    </div>
    <BottomNav active="/explorar" />
  </main>;
}
