export const MAX_MEDIA = 25 * 1024 * 1024;
export class BoardError extends Error {
  constructor(status, message) { super(message); this.status = status; }
}
export function need(ok, message, status = 400) { if (!ok) throw new BoardError(status, message); }
export function textField(value, max, min = 1) {
  need(typeof value === 'string', '입력 내용을 확인해 주세요.');
  const text = value.trim();
  need(text.length >= min && text.length <= max && !/[\x00-\x08\x0b\x0c\x0e-\x1f]/.test(text), `${min}~${max}자로 입력해 주세요.`);
  return text;
}
export function passwordField(value) {
  need(typeof value === 'string' && value.length >= 8 && value.length <= 64, '관리 비밀번호는 8~64자로 설정해 주세요.');
  return value;
}
const hex = bytes => Array.from(bytes, b => b.toString(16).padStart(2, '0')).join('');
export const randomToken = () => hex(crypto.getRandomValues(new Uint8Array(32)));
export const digest = async value => hex(new Uint8Array(await crypto.subtle.digest('SHA-256', new TextEncoder().encode(value))));
export async function hashPassword(password, salt = randomToken()) {
  passwordField(password);
  const key = await crypto.subtle.importKey('raw', new TextEncoder().encode(password), 'PBKDF2', false, ['deriveBits']);
  const bits = await crypto.subtle.deriveBits({ name: 'PBKDF2', salt: new TextEncoder().encode(salt), iterations: 100000, hash: 'SHA-256' }, key, 256);
  return { salt, hash: hex(new Uint8Array(bits)) };
}
export async function checkPassword(password, row) {
  const { hash } = await hashPassword(password, row.salt);
  let diff = hash.length ^ row.password_hash.length;
  for (let i = 0; i < hash.length; i++) diff |= hash.charCodeAt(i) ^ (row.password_hash.charCodeAt(i) || 0);
  need(diff === 0, '관리 비밀번호가 맞지 않아요.', 403);
}
export function cookieToken(request) {
  const token = request.headers.get('cookie')?.split(';').map(s => s.trim()).find(s => s.startsWith('agattok_visitor='))?.split('=')[1];
  return /^[a-f0-9]{64}$/.test(token || '') ? token : null;
}
export function sameOrigin(request) {
  need(request.headers.get('origin') === new URL(request.url).origin && request.headers.get('x-board-client') === '1', '이 게시판 화면에서 다시 시도해 주세요.', 403);
}
export function mediaInfo(bytes) {
  need(bytes.length > 16 && bytes.length <= MAX_MEDIA, '사진·영상은 25MB 이하로 골라 주세요.');
  const v = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
  // Only freshly re-encoded JPEG or an MP4 with a bounded movie duration is accepted.
  if (bytes[0] === 255 && bytes[1] === 216 && bytes[bytes.length - 2] === 255 && bytes[bytes.length - 1] === 217) {
    let p = 2;
    while (p + 4 < bytes.length && bytes[p] === 255) {
      const marker = bytes[p + 1]; if (marker === 218) break;
      const n = v.getUint16(p + 2); need(n >= 2 && p + 2 + n <= bytes.length, '사진 파일을 읽을 수 없어요.');
      need(marker !== 225, '위치 정보가 없는 사진으로 다시 저장해 주세요.');
      p += n + 2;
    }
    return { mime: 'image/jpeg', duration: 0, ext: 'jpg' };
  }
  const word = p => String.fromCharCode(...bytes.slice(p, p + 4));
  need(word(4) === 'ftyp', 'JPEG 사진 또는 MP4 영상카드를 골라 주세요.');
  let duration = null;
  function scan(start, end, depth) {
    let p = start;
    while (p + 8 <= end) {
      const size = v.getUint32(p); need(size >= 8 && p + size <= end, 'MP4 파일이 손상됐어요.');
      const type = word(p + 4);
      if (type === 'moov' && depth === 0) scan(p + 8, p + size, 1);
      if (type === 'mvhd' && depth === 1) {
        const version = bytes[p + 8]; need(version === 0 || version === 1, '지원하지 않는 MP4 영상이에요.');
        const offset = version ? 28 : 20;
        need(size >= offset + (version ? 12 : 8), 'MP4 길이 정보를 읽을 수 없어요.');
        const scale = v.getUint32(p + offset);
        const ticks = version ? Number(v.getBigUint64(p + offset + 4)) : v.getUint32(p + offset + 4);
        need(scale > 0 && Number.isSafeInteger(ticks), 'MP4 길이 정보가 올바르지 않아요.');
        duration = ticks / scale;
      }
      p += size;
    }
  }
  scan(0, bytes.length, 0);
  need(duration !== null && duration > 0 && duration <= 30.1, '영상카드는 30초 이내로 골라 주세요.');
  return { mime: 'video/mp4', duration, ext: 'mp4' };
}

/** Replace metadata boxes with equal-sized free boxes so chunk offsets remain valid. */
export function stripMovieMetadata(input) {
  mediaInfo(input);
  const bytes=input.slice(),view=new DataView(bytes.buffer),containers=new Set(['moov','trak','mdia','minf','stbl','edts','dinf']);
  function walk(start,end,depth) {
    need(depth<=8,'MP4 구조를 읽지 못했어요.');let p=start;
    while(p+8<=end){const size=view.getUint32(p);need(size>=8&&p+size<=end,'MP4 파일이 손상됐어요.');const type=String.fromCharCode(...bytes.slice(p+4,p+8));
      if(['udta','meta','uuid'].includes(type)){bytes.set(new TextEncoder().encode('free'),p+4);bytes.fill(0,p+8,p+size);}
      else if(['mvhd','tkhd','mdhd'].includes(type)){const length=bytes[p+8]===1?16:8;need(size>=12+length,'MP4 파일이 손상됐어요.');bytes.fill(0,p+12,p+12+length);}
      if(containers.has(type))walk(p+8,p+size,depth+1);
      p+=size;
    }
  }
  walk(0,bytes.length,0);return bytes;
}
