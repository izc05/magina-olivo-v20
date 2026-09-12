import type { ProfessionalPrintPayload } from '@/lib/professional-print-source';

type DrawItem =
  | { kind: 'text'; text: string; x: number; size: number; bold?: boolean; gapAfter?: number }
  | { kind: 'rule'; gapAfter?: number };

function clean(value: unknown) {
  return String(value ?? '').replace(/[\r\n\t]+/g, ' ').replace(/\s+/g, ' ').trim();
}

function money(value: number) {
  return `${value.toLocaleString('es-ES', { minimumFractionDigits: 2, maximumFractionDigits: 2 })} EUR`;
}

function dateLabel(value?: string | null) {
  if (!value) return '—';
  const [year, month, day] = value.slice(0, 10).split('-');
  return `${day}/${month}/${year}`;
}

function wrap(value: string, width = 86) {
  const words = clean(value).split(' ').filter(Boolean);
  const lines: string[] = [];
  let current = '';
  for (const word of words) {
    if (!current) current = word;
    else if (`${current} ${word}`.length <= width) current += ` ${word}`;
    else { lines.push(current); current = word; }
  }
  if (current) lines.push(current);
  return lines.length ? lines : [''];
}

function pdfString(value: string) {
  const normalized = value
    .replace(/€/g, 'EUR')
    .replace(/[“”]/g, '"')
    .replace(/[‘’]/g, "'")
    .replace(/—/g, '-')
    .replace(/–/g, '-');
  let out = '';
  for (const char of normalized) {
    const code = char.charCodeAt(0);
    if (char === '\\' || char === '(' || char === ')') out += `\\${char}`;
    else if (code >= 32 && code <= 255) out += char;
    else out += '?';
  }
  return out;
}

function makeRows(data: ProfessionalPrintPayload): DrawItem[] {
  const invoice = data.document_type === 'invoice';
  const issuer = data.issuer;
  const customerName = data.customer.legal_name || data.customer.display_name;
  const rows: DrawItem[] = [
    { kind: 'text', text: 'MAGINA OLIVO', x: 48, size: 9, bold: true, gapAfter: 4 },
    { kind: 'text', text: invoice ? 'FACTURA' : 'PRESUPUESTO', x: 48, size: 20, bold: true, gapAfter: 2 },
    { kind: 'text', text: data.document.number || (invoice ? 'Borrador' : 'Sin numero'), x: 48, size: 13, bold: true, gapAfter: 14 },
    { kind: 'rule', gapAfter: 12 },
    { kind: 'text', text: 'EMISOR', x: 48, size: 8, bold: true, gapAfter: 2 },
    { kind: 'text', text: issuer?.legal_name || issuer?.workspace_name || 'Profesional agricola', x: 48, size: 11, bold: true, gapAfter: 1 },
    { kind: 'text', text: [issuer?.tax_id ? `NIF/CIF ${issuer.tax_id}` : '', issuer?.address, [issuer?.postal_code, issuer?.municipality].filter(Boolean).join(' '), issuer?.province].filter(Boolean).join(' · '), x: 48, size: 9, gapAfter: 2 },
    { kind: 'text', text: [issuer?.phone, issuer?.email].filter(Boolean).join(' · '), x: 48, size: 9, gapAfter: 10 },
    { kind: 'text', text: 'CLIENTE', x: 48, size: 8, bold: true, gapAfter: 2 },
    { kind: 'text', text: customerName, x: 48, size: 11, bold: true, gapAfter: 1 },
    { kind: 'text', text: [data.customer.tax_id ? `NIF/CIF ${data.customer.tax_id}` : '', data.customer.phone, data.customer.email].filter(Boolean).join(' · '), x: 48, size: 9, gapAfter: 10 },
    { kind: 'text', text: invoice ? `Fecha: ${dateLabel(data.document.issued_on)} · Vencimiento: ${dateLabel(data.document.due_on)} · Estado: ${data.document.status}` : `Fecha: ${dateLabel(data.document.issued_on)} · Valido hasta: ${dateLabel(data.document.valid_until)} · Estado: ${data.document.status}`, x: 48, size: 9, gapAfter: 12 },
    { kind: 'rule', gapAfter: 10 },
  ];

  if (!invoice && data.document.title) rows.push({ kind: 'text', text: data.document.title, x: 48, size: 12, bold: true, gapAfter: 8 });

  if (invoice) {
    rows.push({ kind: 'text', text: 'TRABAJOS / CONCEPTOS', x: 48, size: 9, bold: true, gapAfter: 5 });
    for (const line of data.lines) {
      const label = [clean(line.title), line.site_name ? clean(line.site_name) : '', line.occurred_on ? dateLabel(clean(line.occurred_on)) : ''].filter(Boolean).join(' · ');
      for (const [index, wrapped] of wrap(label, 70).entries()) rows.push({ kind: 'text', text: index === 0 ? `${wrapped}    ${money(Number(line.amount_eur ?? 0))}` : wrapped, x: 52, size: 9, gapAfter: index === 0 ? 1 : 0 });
      rows.push({ kind: 'text', text: '', x: 48, size: 4, gapAfter: 3 });
    }
  } else {
    rows.push({ kind: 'text', text: 'CONCEPTOS', x: 48, size: 9, bold: true, gapAfter: 5 });
    for (const line of data.lines) {
      const label = `${clean(line.description)} · ${Number(line.quantity ?? 0).toLocaleString('es-ES')} ${clean(line.unit)} · ${money(Number(line.unit_price_eur ?? 0))}/ud · ${money(Number(line.line_total_eur ?? 0))}`;
      for (const wrapped of wrap(label, 82)) rows.push({ kind: 'text', text: wrapped, x: 52, size: 9, gapAfter: 1 });
      rows.push({ kind: 'text', text: '', x: 48, size: 4, gapAfter: 3 });
    }
  }

  rows.push(
    { kind: 'rule', gapAfter: 8 },
    { kind: 'text', text: `Base: ${money(data.document.subtotal_eur)}`, x: 340, size: 10, gapAfter: 2 },
    { kind: 'text', text: `Impuestos: ${money(data.document.tax_eur)}`, x: 340, size: 10, gapAfter: 2 },
    { kind: 'text', text: `TOTAL: ${money(data.document.total_eur)}`, x: 340, size: 14, bold: true, gapAfter: 14 },
  );

  if (data.document.notes) {
    rows.push({ kind: 'text', text: 'NOTAS', x: 48, size: 8, bold: true, gapAfter: 3 });
    for (const line of wrap(data.document.notes, 92)) rows.push({ kind: 'text', text: line, x: 48, size: 9, gapAfter: 1 });
    rows.push({ kind: 'text', text: '', x: 48, size: 4, gapAfter: 6 });
  }
  if (issuer?.payment_terms) {
    rows.push({ kind: 'text', text: 'CONDICIONES / PAGO', x: 48, size: 8, bold: true, gapAfter: 3 });
    for (const line of wrap(issuer.payment_terms, 92)) rows.push({ kind: 'text', text: line, x: 48, size: 9, gapAfter: 1 });
  }
  rows.push({ kind: 'text', text: issuer?.footer_note || 'Documento generado desde datos estructurados de Magina Olivo.', x: 48, size: 8, gapAfter: 2 });
  return rows;
}

function contentForPage(items: DrawItem[]) {
  let y = 800;
  const commands: string[] = [];
  for (const item of items) {
    if (item.kind === 'rule') {
      commands.push(`0.82 G 48 ${y} m 547 ${y} l S`);
      y -= 4 + (item.gapAfter ?? 0);
      continue;
    }
    const leading = Math.max(item.size + 3, 10);
    commands.push(`BT /${item.bold ? 'F2' : 'F1'} ${item.size} Tf 1 0 0 1 ${item.x} ${y} Tm (${pdfString(item.text)}) Tj ET`);
    y -= leading + (item.gapAfter ?? 0);
  }
  return commands.join('\n');
}

function paginate(items: DrawItem[]) {
  const pages: DrawItem[][] = [];
  let current: DrawItem[] = [];
  let used = 0;
  const capacity = 690;
  for (const item of items) {
    const height = item.kind === 'rule' ? 4 + (item.gapAfter ?? 0) : Math.max(item.size + 3, 10) + (item.gapAfter ?? 0);
    if (current.length && used + height > capacity) {
      pages.push(current);
      current = [{ kind: 'text', text: 'MAGINA OLIVO · continuacion', x: 48, size: 8, bold: true, gapAfter: 10 }];
      used = 24;
    }
    current.push(item);
    used += height;
  }
  if (current.length) pages.push(current);
  return pages;
}

function buildPdf(items: DrawItem[]) {
  const pages = paginate(items);
  const objects: Array<Uint8Array> = [];
  const encoder = (value: string) => Uint8Array.from(Array.from(value, (char) => char.charCodeAt(0) & 0xff));
  const catalogId = 1;
  const pagesId = 2;
  const fontId = 3;
  const boldFontId = 4;
  let nextId = 5;
  const pagePairs = pages.map(() => ({ pageId: nextId++, contentId: nextId++ }));

  const pushObject = (id: number, body: string) => { objects[id] = encoder(`${id} 0 obj\n${body}\nendobj\n`); };
  pushObject(catalogId, `<< /Type /Catalog /Pages ${pagesId} 0 R >>`);
  pushObject(pagesId, `<< /Type /Pages /Kids [${pagePairs.map((pair) => `${pair.pageId} 0 R`).join(' ')}] /Count ${pagePairs.length} >>`);
  pushObject(fontId, '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>');
  pushObject(boldFontId, '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>');

  pages.forEach((pageItems, index) => {
    const pair = pagePairs[index];
    const content = encoder(contentForPage(pageItems));
    pushObject(pair.pageId, `<< /Type /Page /Parent ${pagesId} 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 ${fontId} 0 R /F2 ${boldFontId} 0 R >> >> /Contents ${pair.contentId} 0 R >>`);
    const prefix = encoder(`${pair.contentId} 0 obj\n<< /Length ${content.length} >>\nstream\n`);
    const suffix = encoder('\nendstream\nendobj\n');
    const joined = new Uint8Array(prefix.length + content.length + suffix.length);
    joined.set(prefix, 0); joined.set(content, prefix.length); joined.set(suffix, prefix.length + content.length);
    objects[pair.contentId] = joined;
  });

  const header = encoder('%PDF-1.4\n%\xE2\xE3\xCF\xD3\n');
  const chunks: Uint8Array[] = [header];
  const offsets = new Array(nextId).fill(0);
  let offset = header.length;
  for (let id = 1; id < nextId; id += 1) {
    const object = objects[id];
    if (!object) throw new Error(`missing_pdf_object:${id}`);
    offsets[id] = offset;
    chunks.push(object);
    offset += object.length;
  }
  const xrefOffset = offset;
  let xref = `xref\n0 ${nextId}\n0000000000 65535 f \n`;
  for (let id = 1; id < nextId; id += 1) xref += `${String(offsets[id]).padStart(10, '0')} 00000 n \n`;
  xref += `trailer\n<< /Size ${nextId} /Root ${catalogId} 0 R >>\nstartxref\n${xrefOffset}\n%%EOF\n`;
  chunks.push(encoder(xref));

  const total = chunks.reduce((sum, chunk) => sum + chunk.length, 0);
  const pdf = new Uint8Array(total);
  let cursor = 0;
  for (const chunk of chunks) { pdf.set(chunk, cursor); cursor += chunk.length; }
  return pdf;
}

export function generateProfessionalPdf(data: ProfessionalPrintPayload) {
  return new Blob([buildPdf(makeRows(data))], { type: 'application/pdf' });
}
