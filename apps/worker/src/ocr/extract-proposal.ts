export type ExtractionProposal = {
  documentType: string;
  data: Record<string, unknown>;
  confidence: Record<string, number>;
};

function parseDecimal(raw: string) {
  const normalized = raw.replace(/\s/g, '').replace(/\.(?=\d{3}(?:\D|$))/g, '').replace(',', '.');
  const value = Number(normalized);
  return Number.isFinite(value) ? value : null;
}

function firstMatch(text: string, patterns: RegExp[]) {
  for (const pattern of patterns) {
    const match = text.match(pattern);
    if (match?.[1]) return match[1].trim();
  }
  return null;
}

function parseDate(text: string) {
  const raw = firstMatch(text, [
    /(?:fecha|date)\s*[:\-]?\s*(\d{1,2}[\/.\-]\d{1,2}[\/.\-]\d{2,4})/i,
    /\b(\d{1,2}[\/.\-]\d{1,2}[\/.\-]\d{4})\b/,
  ]);
  if (!raw) return null;
  const parts = raw.split(/[\/.\-]/).map(Number);
  if (parts.length !== 3) return null;
  const [day, month, rawYear] = parts;
  const year = rawYear < 100 ? 2000 + rawYear : rawYear;
  if (!day || !month || !year || month > 12 || day > 31) return null;
  return `${year.toString().padStart(4, '0')}-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}`;
}

function add(data: Record<string, unknown>, confidence: Record<string, number>, key: string, value: unknown, score: number) {
  if (value === null || value === undefined || value === '') return;
  data[key] = value;
  confidence[key] = score;
}

export function extractDocumentProposal(documentKind: string, rawText: string): ExtractionProposal | null {
  const text = rawText.replace(/\r/g, ' ');
  const data: Record<string, unknown> = {};
  const confidence: Record<string, number> = {};
  add(data, confidence, 'date', parseDate(text), 0.74);

  if (['invoice', 'purchase_receipt', 'quote'].includes(documentKind)) {
    const totalRaw = firstMatch(text, [
      /(?:total\s*(?:factura|a\s*pagar)?|importe\s*total|total)\s*[:€\s]*([0-9][0-9.]*[,.][0-9]{2})/i,
      /([0-9][0-9.]*[,.][0-9]{2})\s*€/i,
    ]);
    add(data, confidence, 'total_eur', totalRaw ? parseDecimal(totalRaw) : null, totalRaw ? 0.84 : 0);
    add(data, confidence, 'document_number', firstMatch(text, [
      /(?:factura|ticket|presupuesto|n[ºo°]\.?|núm(?:ero)?)\s*[:#\-]?\s*([A-Z0-9][A-Z0-9\-\/.]{2,})/i,
    ]), 0.68);
    add(data, confidence, 'supplier_tax_id', firstMatch(text, [
      /(?:cif|nif|vat)\s*[:\-]?\s*([A-Z0-9][A-Z0-9\-]{7,14})/i,
    ]), 0.72);
  }

  if (documentKind === 'delivery_ticket') {
    const kgRaw = firstMatch(text, [
      /(?:peso\s*neto|neto|kilos?|kg)\s*[:\-]?\s*([0-9][0-9.,]*)\s*(?:kg|kgs?)?/i,
      /([0-9][0-9.,]*)\s*kg\b/i,
    ]);
    add(data, confidence, 'total_kg', kgRaw ? parseDecimal(kgRaw) : null, kgRaw ? 0.9 : 0);
    add(data, confidence, 'ticket_number', firstMatch(text, [
      /(?:albar[aá]n|ticket|entrada|n[ºo°]\.?|núm(?:ero)?)\s*[:#\-]?\s*([A-Z0-9][A-Z0-9\-\/.]{2,})/i,
    ]), 0.68);
  }

  if (documentKind === 'yield_result') {
    const yieldRaw = firstMatch(text, [
      /(?:rendimiento(?:\s*graso)?|rdto\.?|yield)\s*[:\-]?\s*([0-9]{1,2}(?:[,.][0-9]{1,3})?)\s*%?/i,
    ]);
    add(data, confidence, 'yield_percent', yieldRaw ? parseDecimal(yieldRaw) : null, yieldRaw ? 0.88 : 0);
    const moistureRaw = firstMatch(text, [/(?:humedad)\s*[:\-]?\s*([0-9]{1,2}(?:[,.][0-9]{1,3})?)\s*%?/i]);
    add(data, confidence, 'moisture_percent', moistureRaw ? parseDecimal(moistureRaw) : null, moistureRaw ? 0.8 : 0);
    const acidityRaw = firstMatch(text, [/(?:acidez)\s*[:\-]?\s*([0-9]{1,2}(?:[,.][0-9]{1,3})?)\s*%?/i]);
    add(data, confidence, 'acidity_percent', acidityRaw ? parseDecimal(acidityRaw) : null, acidityRaw ? 0.8 : 0);
  }

  if (!Object.keys(data).length) return null;
  return { documentType: documentKind, data, confidence };
}
