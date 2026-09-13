'use client';

import Link from 'next/link';
import { FormEvent, useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'next/navigation';
import {
  loadExperience,
  loadExperiences,
  requestExperienceBooking,
  type ExperienceDetail,
  type ExperienceListItem,
  type ExperienceType,
} from '@/lib/business-experience-source';
import styles from './business-experiences.module.css';

const typeLabels: Record<ExperienceType,string> = {
  aove_tasting:'Cata de AOVE', mill_visit:'Visita a almazara', guided_tour:'Visita guiada', workshop:'Taller', gastronomy:'Gastronomía',
  nature:'Naturaleza', culture:'Cultura', family:'Familia', wellness:'Bienestar', other:'Experiencia',
};

function money(cents:number|null,currency='EUR'){
  return cents===null?'Consultar':new Intl.NumberFormat('es-ES',{style:'currency',currency}).format(cents/100);
}
function dateTime(value:string){return new Intl.DateTimeFormat('es-ES',{dateStyle:'medium',timeStyle:'short'}).format(new Date(value));}

function BookingForm({experience}:{experience:ExperienceDetail}){
  const [slotId,setSlotId]=useState(''); const [name,setName]=useState(''); const [email,setEmail]=useState(''); const [phone,setPhone]=useState('');
  const [partySize,setPartySize]=useState(String(experience.min_party_size||1)); const [message,setMessage]=useState(''); const [busy,setBusy]=useState(false);
  const [success,setSuccess]=useState<string|null>(null); const [error,setError]=useState<string|null>(null);
  async function submit(event:FormEvent){
    event.preventDefault(); setBusy(true); setError(null);
    try{
      const result=await requestExperienceBooking(experience.business_slug,experience.slug,{slotId:slotId||null,contactName:name,contactEmail:email||null,contactPhone:phone||null,partySize:Number(partySize),message:message||null,consentBusinessContact:true});
      setSuccess(result.message);
    }catch(cause){console.error(cause);setError('No se ha podido enviar la solicitud. Revisa los datos y la disponibilidad.');}
    finally{setBusy(false);}
  }
  if(experience.booking_mode==='external'&&experience.booking_url)return <a className={styles.button} href={experience.booking_url} target="_blank" rel="noopener noreferrer">Reservar en la web del negocio ↗</a>;
  if(experience.booking_mode==='contact')return <div className={styles.notice}>Esta experiencia se gestiona directamente con la empresa. Consulta su ficha para contactar.</div>;
  if(success)return <div className={styles.notice}><strong>Solicitud enviada.</strong><p>{success}</p></div>;
  return <form className={styles.form} onSubmit={submit}>
    {experience.slots.length?<label className={styles.wide}>Fecha / sesión<select value={slotId} onChange={e=>setSlotId(e.target.value)}><option value="">Solicitar sin elegir sesión</option>{experience.slots.filter(s=>s.availablePlaces>0).map(s=><option key={s.id} value={s.id}>{dateTime(s.starts_at)} · {s.availablePlaces} plazas</option>)}</select></label>:null}
    <label>Nombre<input required minLength={2} maxLength={120} value={name} onChange={e=>setName(e.target.value)}/></label>
    <label>Personas<input required type="number" min={experience.min_party_size} max={experience.max_party_size??1000} value={partySize} onChange={e=>setPartySize(e.target.value)}/></label>
    <label>Email<input type="email" value={email} onChange={e=>setEmail(e.target.value)}/></label>
    <label>Teléfono<input inputMode="tel" value={phone} onChange={e=>setPhone(e.target.value)}/></label>
    <label className={styles.wide}>Mensaje<textarea rows={4} maxLength={2000} value={message} onChange={e=>setMessage(e.target.value)}/></label>
    <p className={styles.wide}>Al enviar, autorizas a Mágina Olivo a trasladar estos datos a {experience.business_name} únicamente para gestionar esta solicitud.</p>
    {error?<div className={`${styles.error} ${styles.wide}`}>{error}</div>:null}
    <div className={`${styles.actions} ${styles.wide}`}><button className={styles.button} disabled={busy||(!email&&!phone)}>{busy?'Enviando…':'Solicitar reserva'}</button></div>
  </form>;
}

function ExperienceDetailView({business,slug}:{business:string;slug:string}){
  const [experience,setExperience]=useState<ExperienceDetail|null>(null); const [loading,setLoading]=useState(true); const [error,setError]=useState(false);
  useEffect(()=>{let alive=true;setLoading(true);loadExperience(business,slug).then(v=>{if(alive)setExperience(v)}).catch(()=>{if(alive)setError(true)}).finally(()=>{if(alive)setLoading(false)});return()=>{alive=false}},[business,slug]);
  if(loading)return <main className={styles.page}><div className={styles.state}>Cargando experiencia…</div></main>;
  if(error||!experience)return <main className={styles.page}><Link className={styles.back} href="/experiencias">← Experiencias</Link><div className={styles.state}>Esta experiencia no está disponible.</div></main>;
  return <main className={styles.page}>
    <Link className={styles.back} href="/experiencias">← Todas las experiencias</Link>
    <section className={styles.detail}>
      <article className={styles.panel}>
        {experience.cover_image_url?<img className={styles.detailCover} src={experience.cover_image_url} alt=""/>:null}
        <p className={styles.eyebrow}>{typeLabels[experience.experience_type]} · {experience.business_name}</p><h1>{experience.title}</h1>
        {experience.summary?<p className={styles.lead}>{experience.summary}</p>:null}{experience.description?<p>{experience.description}</p>:null}
        <div className={styles.facts}>
          <div className={styles.fact}><small>Precio</small><strong>{money(experience.price_cents,experience.currency)}{experience.price_cents!==null?' / persona':''}</strong></div>
          <div className={styles.fact}><small>Duración</small><strong>{experience.duration_minutes?`${experience.duration_minutes} min`:'Consultar'}</strong></div>
          <div className={styles.fact}><small>Grupo</small><strong>{experience.min_party_size}{experience.max_party_size?`–${experience.max_party_size}`:'+'} personas</strong></div>
          <div className={styles.fact}><small>Punto de encuentro</small><strong>{experience.meeting_point_text||'Se confirma con la reserva'}</strong></div>
        </div>
        {experience.includes?.length?<><h2>Incluye</h2><ul className={styles.list}>{experience.includes.map((x,i)=><li key={i}>{x}</li>)}</ul></>:null}
        {experience.languages?.length?<p><strong>Idiomas:</strong> {experience.languages.join(', ')}</p>:null}
        {experience.cancellation_policy?<p><strong>Cancelación:</strong> {experience.cancellation_policy}</p>:null}
        <div className={styles.actions}><Link className={styles.secondary} href={`/empresas?slug=${encodeURIComponent(experience.business_slug)}`}>Ver empresa</Link></div>
      </article>
      <aside className={styles.panel}>
        <p className={styles.eyebrow}>RESERVA / SOLICITUD</p><h2>Vive la experiencia</h2>
        {experience.slots.length?<div className={styles.slots}>{experience.slots.slice(0,5).map(slot=><div className={styles.slot} key={slot.id}><span><strong>{dateTime(slot.starts_at)}</strong><br/><small>{slot.availablePlaces} plazas disponibles</small></span><span className={styles.price}>{money(slot.price_override_cents??experience.price_cents,experience.currency)}</span></div>)}</div>:<div className={styles.notice}>No hay sesiones publicadas. Puedes enviar una solicitud y acordar fecha.</div>}
        <BookingForm experience={experience}/>
      </aside>
    </section>
  </main>;
}

export function BusinessExperiencesPage(){
  const params=useSearchParams(); const business=params.get('business')||''; const slug=params.get('slug')||'';
  const [items,setItems]=useState<ExperienceListItem[]>([]); const [q,setQ]=useState(''); const [type,setType]=useState<''|ExperienceType>(''); const [loading,setLoading]=useState(true); const [error,setError]=useState(false);
  useEffect(()=>{if(business&&slug)return;let alive=true;setLoading(true);setError(false);loadExperiences({q:q||undefined,type:type||undefined}).then(v=>{if(alive)setItems(v.experiences)}).catch(()=>{if(alive)setError(true)}).finally(()=>{if(alive)setLoading(false)});return()=>{alive=false}},[business,slug,q,type]);
  const count=useMemo(()=>items.length,[items]);
  if(business&&slug)return <ExperienceDetailView business={business} slug={slug}/>;
  return <main className={styles.page}>
    <section className={styles.hero}><p className={styles.eyebrow}>EXPERIENCIAS DE SIERRA MÁGINA</p><h1>Vive Mágina, no solo la visites.</h1><p>Catas de AOVE, almazaras, gastronomía, naturaleza, cultura y actividades ofrecidas por negocios del territorio. La disponibilidad y la reserva se solicitan directamente desde Mágina Olivo cuando el negocio lo permite.</p></section>
    <div className={styles.filters}><input type="search" placeholder="Buscar cata, visita, taller…" value={q} onChange={e=>setQ(e.target.value)}/><select value={type} onChange={e=>setType(e.target.value as ''|ExperienceType)}><option value="">Todos los tipos</option>{Object.entries(typeLabels).map(([value,label])=><option key={value} value={value}>{label}</option>)}</select></div>
    {loading?<div className={styles.state}>Buscando experiencias…</div>:error?<div className={styles.error}>No se han podido cargar las experiencias.</div>:count===0?<div className={styles.state}>No hay experiencias publicadas con estos filtros.</div>:<section className={styles.grid}>{items.map(item=><Link className={styles.card} href={`/experiencias?business=${encodeURIComponent(item.business_slug)}&slug=${encodeURIComponent(item.slug)}`} key={item.id}>{item.cover_image_url?<img className={styles.cover} src={item.cover_image_url} alt="" loading="lazy"/>:<div className={styles.placeholder}>◉</div>}<div className={styles.body}><p className={styles.eyebrow}>{typeLabels[item.experience_type]} · {item.business_name}</p><h2>{item.title}</h2>{item.summary?<p>{item.summary}</p>:null}<div className={styles.meta}><span className={styles.badge}>{money(item.price_cents,item.currency)}</span>{item.duration_minutes?<span className={styles.badge}>{item.duration_minutes} min</span>:null}{item.next_slot?<span className={styles.badge}>Próxima: {dateTime(item.next_slot)}</span>:null}</div></div></Link>)}</section>}
  </main>;
}
