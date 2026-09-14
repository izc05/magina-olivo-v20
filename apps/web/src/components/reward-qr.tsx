type RewardQrProps = {
  code: string;
  size?: number;
};

const VERSION = 3;
const MATRIX_SIZE = 29;
const DATA_CODEWORDS = 55;
const EC_CODEWORDS = 15;
const QUIET_ZONE = 4;

function gfTables() {
  const exp = new Array<number>(512).fill(0);
  const log = new Array<number>(256).fill(0);
  let value = 1;
  for (let i = 0; i < 255; i += 1) {
    exp[i] = value;
    log[value] = i;
    value <<= 1;
    if (value & 0x100) value ^= 0x11d;
  }
  for (let i = 255; i < 512; i += 1) exp[i] = exp[i - 255];
  return { exp, log };
}

const GF = gfTables();

function gfMultiply(a: number, b: number) {
  if (a === 0 || b === 0) return 0;
  return GF.exp[GF.log[a] + GF.log[b]];
}

function reedSolomonGenerator(degree: number) {
  let generator = [1];
  for (let i = 0; i < degree; i += 1) {
    const next = new Array<number>(generator.length + 1).fill(0);
    generator.forEach((coefficient, index) => {
      next[index] ^= coefficient;
      next[index + 1] ^= gfMultiply(coefficient, GF.exp[i]);
    });
    generator = next;
  }
  return generator;
}

function appendBits(target: number[], value: number, length: number) {
  for (let bit = length - 1; bit >= 0; bit -= 1) target.push((value >>> bit) & 1);
}

function encodeCodewords(value: string) {
  const bytes = new TextEncoder().encode(value);
  if (bytes.length > 53) throw new Error('reward_qr_payload_too_long');

  const bits: number[] = [];
  appendBits(bits, 0b0100, 4);
  appendBits(bits, bytes.length, VERSION < 10 ? 8 : 16);
  bytes.forEach((byte) => appendBits(bits, byte, 8));

  const capacity = DATA_CODEWORDS * 8;
  const terminator = Math.min(4, capacity - bits.length);
  for (let i = 0; i < terminator; i += 1) bits.push(0);
  while (bits.length % 8 !== 0) bits.push(0);

  const data: number[] = [];
  for (let offset = 0; offset < bits.length; offset += 8) {
    let byte = 0;
    for (let bit = 0; bit < 8; bit += 1) byte = (byte << 1) | bits[offset + bit];
    data.push(byte);
  }

  const pads = [0xec, 0x11];
  let padIndex = 0;
  while (data.length < DATA_CODEWORDS) {
    data.push(pads[padIndex % pads.length]);
    padIndex += 1;
  }

  const generator = reedSolomonGenerator(EC_CODEWORDS);
  const message = [...data, ...new Array<number>(EC_CODEWORDS).fill(0)];
  for (let index = 0; index < data.length; index += 1) {
    const factor = message[index];
    if (factor === 0) continue;
    generator.forEach((coefficient, generatorIndex) => {
      message[index + generatorIndex] ^= gfMultiply(coefficient, factor);
    });
  }
  return [...data, ...message.slice(-EC_CODEWORDS)];
}

function encodeFormatBits() {
  const formatData = 1 << 3;
  let remainder = formatData;
  for (let i = 0; i < 10; i += 1) remainder = (remainder << 1) ^ (((remainder >>> 9) & 1) * 0x537);
  return ((formatData << 10) | remainder) ^ 0x5412;
}

function createMatrix(value: string) {
  const matrix = Array.from({ length: MATRIX_SIZE }, () => new Array<boolean>(MATRIX_SIZE).fill(false));
  const reserved = Array.from({ length: MATRIX_SIZE }, () => new Array<boolean>(MATRIX_SIZE).fill(false));

  function setModule(x: number, y: number, dark: boolean, reserve = true) {
    if (x < 0 || y < 0 || x >= MATRIX_SIZE || y >= MATRIX_SIZE) return;
    matrix[y][x] = dark;
    if (reserve) reserved[y][x] = true;
  }

  function finder(centerX: number, centerY: number) {
    for (let dy = -4; dy <= 4; dy += 1) {
      for (let dx = -4; dx <= 4; dx += 1) {
        const distance = Math.max(Math.abs(dx), Math.abs(dy));
        setModule(centerX + dx, centerY + dy, distance !== 2 && distance !== 4);
      }
    }
  }

  function alignment(centerX: number, centerY: number) {
    for (let dy = -2; dy <= 2; dy += 1) {
      for (let dx = -2; dx <= 2; dx += 1) {
        setModule(centerX + dx, centerY + dy, Math.max(Math.abs(dx), Math.abs(dy)) !== 1);
      }
    }
  }

  finder(3, 3);
  finder(MATRIX_SIZE - 4, 3);
  finder(3, MATRIX_SIZE - 4);
  for (let index = 8; index < MATRIX_SIZE - 8; index += 1) {
    setModule(6, index, index % 2 === 0);
    setModule(index, 6, index % 2 === 0);
  }
  alignment(22, 22);

  const formatPositions: Array<[number, number]> = [];
  for (let i = 0; i < 6; i += 1) formatPositions.push([8, i]);
  formatPositions.push([8, 7], [8, 8], [7, 8]);
  for (let i = 9; i < 15; i += 1) formatPositions.push([14 - i, 8]);
  for (let i = 0; i < 8; i += 1) formatPositions.push([MATRIX_SIZE - 1 - i, 8]);
  for (let i = 8; i < 15; i += 1) formatPositions.push([8, MATRIX_SIZE - 15 + i]);
  formatPositions.push([8, MATRIX_SIZE - 8]);
  formatPositions.forEach(([x, y]) => { reserved[y][x] = true; });

  const dataBits: number[] = [];
  encodeCodewords(value).forEach((codeword) => appendBits(dataBits, codeword, 8));
  let bitIndex = 0;
  let right = MATRIX_SIZE - 1;
  while (right >= 1) {
    if (right === 6) right -= 1;
    for (let vertical = 0; vertical < MATRIX_SIZE; vertical += 1) {
      const upward = ((right + 1) & 2) === 0;
      const y = upward ? MATRIX_SIZE - 1 - vertical : vertical;
      for (let column = 0; column < 2; column += 1) {
        const x = right - column;
        if (reserved[y][x]) continue;
        let dark = bitIndex < dataBits.length ? dataBits[bitIndex] === 1 : false;
        bitIndex += 1;
        if ((x + y) % 2 === 0) dark = !dark;
        matrix[y][x] = dark;
      }
    }
    right -= 2;
  }

  const format = encodeFormatBits();
  const formatBit = (index: number) => ((format >>> index) & 1) === 1;
  for (let i = 0; i < 6; i += 1) setModule(8, i, formatBit(i));
  setModule(8, 7, formatBit(6));
  setModule(8, 8, formatBit(7));
  setModule(7, 8, formatBit(8));
  for (let i = 9; i < 15; i += 1) setModule(14 - i, 8, formatBit(i));
  for (let i = 0; i < 8; i += 1) setModule(MATRIX_SIZE - 1 - i, 8, formatBit(i));
  for (let i = 8; i < 15; i += 1) setModule(8, MATRIX_SIZE - 15 + i, formatBit(i));
  setModule(8, MATRIX_SIZE - 8, true);

  return matrix;
}

export function RewardQr({ code, size = 220 }: RewardQrProps) {
  const normalized = code.trim();
  if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\.[A-Za-z0-9_-]{16}$/i.test(normalized)) return null;

  const matrix = createMatrix(normalized);
  const viewSize = MATRIX_SIZE + QUIET_ZONE * 2;
  const path = matrix.flatMap((row, y) => row.flatMap((dark, x) => dark ? [`M${x + QUIET_ZONE} ${y + QUIET_ZONE}h1v1h-1z`] : [])).join('');

  return <svg
    role="img"
    aria-label="Código QR firmado de recogida"
    width={size}
    height={size}
    viewBox={`0 0 ${viewSize} ${viewSize}`}
    shapeRendering="crispEdges"
    style={{ maxWidth: '100%', height: 'auto', background: '#fff', color: '#000' }}
  >
    <rect width={viewSize} height={viewSize} fill="#fff" />
    <path d={path} fill="currentColor" />
  </svg>;
}
