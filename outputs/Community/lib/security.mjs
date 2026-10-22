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
  const invalid = '미디어 파일이 손상됐거나 지원하지 않는 형식이에요.';
  if (bytes[0] === 255 && bytes[1] === 216) {
    let p=2, frame=false, scan=false, quant=false, huffman=false, entropy=0;
    while(p<bytes.length) {
      need(bytes[p++]===255,invalid);while(bytes[p]===255)p++;
      const marker=bytes[p++];need(marker!==undefined,invalid);
      if(marker===217){need(p===bytes.length&&frame&&scan&&quant&&huffman&&entropy>0,invalid);return {mime:'image/jpeg',duration:0,ext:'jpg'};}
      need(marker!==216&&marker!==0&&!(marker>=208&&marker<=215)&&p+2<=bytes.length,invalid);
      const n=v.getUint16(p);need(n>=2&&p+n<=bytes.length,invalid);
      need(marker!==225,'위치 정보가 없는 사진으로 다시 저장해 주세요.');
      if([192,193,194].includes(marker)){
        need(!frame&&n>=11&&bytes[p+2]===8,invalid);
        const height=v.getUint16(p+3),width=v.getUint16(p+5),components=bytes[p+7];
        need(height>0&&width>0&&height<=8192&&width<=8192&&[1,3,4].includes(components)&&n===8+3*components,invalid);frame=true;
      }
      if(marker===219){need(n>=67,invalid);quant=true;}
      if(marker===196){need(n>=19,invalid);huffman=true;}
      if(marker===218){
        need(frame&&quant&&huffman&&n>=8&&n===6+2*bytes[p+2],invalid);scan=true;p+=n;
        const start=p;
        while(p<bytes.length){
          if(bytes[p]!==255){p++;continue;}
          if(bytes[p+1]===0||(bytes[p+1]>=208&&bytes[p+1]<=215)){p+=2;continue;}
          break;
        }
        entropy+=p-start;continue;
      }
      p+=n;
    }
    need(false,invalid);
  }
  const word=p=>String.fromCharCode(...bytes.subarray(p,p+4));
  let boxBudget=4096;
  function boxes(start,end) {
    const result=[];let p=start;
    while(p<end){need(--boxBudget>=0&&p+8<=end,invalid);const type=word(p+4);let size=v.getUint32(p),header=8;
      // Android MediaMuxer writes mdat with a 64-bit size even for small cards.
      if(size===1){need(type==='mdat'&&p+16<=end,invalid);size=Number(v.getBigUint64(p+8));header=16;}
      need(Number.isSafeInteger(size)&&size>=header&&p+size<=end,invalid);
      result.push({p,size,header,end:p+size,type});p+=size;}
    return result;
  }
  const one=(list,type)=>{const matches=list.filter(b=>b.type===type);need(matches.length===1,invalid);return matches[0];};
  function time(box) {
    const version=bytes[box.p+8];need(version===0||version===1,invalid);
    const offset=version?28:20;need(box.size>=offset+(version?12:8),invalid);
    const scale=v.getUint32(box.p+offset),ticks=version?Number(v.getBigUint64(box.p+offset+4)):v.getUint32(box.p+offset+4);
    need(scale>0&&Number.isSafeInteger(ticks),invalid);const duration=ticks/scale;
    need(duration>0&&duration<=30.1,'영상카드는 30초 이내로 골라 주세요.');return duration;
  }
  const top=boxes(0,bytes.length),ftyp=one(top,'ftyp'),moov=one(top,'moov');
  need(ftyp.p===0&&ftyp.size>=16&&!top.some(b=>b.type==='moof'),invalid);
  const mdats=top.filter(b=>b.type==='mdat'&&b.size>b.header);need(mdats.length>0,invalid);
  const movie=boxes(moov.p+8,moov.end),mvhd=one(movie,'mvhd');
  need(mvhd.size>=(bytes[mvhd.p+8]===1?120:108),invalid);const duration=time(mvhd);
  const tracks=movie.filter(b=>b.type==='trak');need(tracks.length>0&&tracks.length<=8,invalid);
  let video=false;
  for(const track of tracks){
    const tc=boxes(track.p+8,track.end);one(tc,'tkhd');
    const mdia=one(tc,'mdia'),mc=boxes(mdia.p+8,mdia.end),mdhd=one(mc,'mdhd'),trackDuration=time(mdhd);
    const trackScale=v.getUint32(mdhd.p+(bytes[mdhd.p+8]===1?28:20));
    const hdlr=one(mc,'hdlr');need(hdlr.size>=32,invalid);const kind=word(hdlr.p+16);
    need(['vide','soun'].includes(kind),invalid);
    const minf=one(mc,'minf'),stbl=one(boxes(minf.p+8,minf.end),'stbl'),tables=boxes(stbl.p+8,stbl.end);
    const stsd=one(tables,'stsd');need(stsd.size>=16,invalid);const descriptions=boxes(stsd.p+16,stsd.end);
    need(descriptions.length===v.getUint32(stsd.p+12)&&descriptions.length>0,invalid);
    if(kind==='vide'){
      video=true;
      for(const d of descriptions){need(['avc1','avc3','hvc1','hev1','mp4v','av01'].includes(d.type)&&d.size>=86,invalid);
        const width=v.getUint16(d.p+32),height=v.getUint16(d.p+34);need(width>0&&height>0&&width<=8192&&height<=8192,invalid);}
    }
    const stsz=one(tables,'stsz');need(stsz.size>=20,invalid);
    const fixed=v.getUint32(stsz.p+12),count=v.getUint32(stsz.p+16);
    need(count>0&&count<=1000000&&stsz.size===20+(fixed?0:4*count),invalid);
    const stts=one(tables,'stts');need(stts.size>=16,invalid);const timeCount=v.getUint32(stts.p+12);
    need(stts.size===16+timeCount*8&&timeCount>0,invalid);let timed=0,ticks=0;
    for(let i=0;i<timeCount;i++){const samples=v.getUint32(stts.p+16+8*i),delta=v.getUint32(stts.p+20+8*i);timed+=samples;ticks+=samples*delta;need(samples>0&&delta>0&&Number.isSafeInteger(ticks),invalid);}need(timed===count,invalid);
    const sampleDuration=ticks/trackScale;need(sampleDuration<=30.1,'영상카드는 30초 이내로 골라 주세요.');
    need(Math.abs(sampleDuration-trackDuration)<=0.1,invalid);
    const stsc=one(tables,'stsc');need(stsc.size>=16,invalid);const scCount=v.getUint32(stsc.p+12);
    need(scCount>0&&stsc.size===16+12*scCount,invalid);
    const offsets=tables.filter(b=>['stco','co64'].includes(b.type));need(offsets.length===1,invalid);
    const co=offsets[0],wide=co.type==='co64';need(co.size>=16,invalid);const chunks=v.getUint32(co.p+12);
    need(chunks>0&&co.size===16+chunks*(wide?8:4),invalid);
    for(let i=0;i<scCount;i++){const p=stsc.p+16+i*12,first=v.getUint32(p);
      need(first>0&&first<=chunks&&(i?first>v.getUint32(p-12):first===1)&&v.getUint32(p+4)>0&&v.getUint32(p+8)>0&&v.getUint32(p+8)<=descriptions.length,invalid);}
    let sample=0,group=0;
    for(let chunk=1;chunk<=chunks;chunk++){
      if(group+1<scCount&&chunk===v.getUint32(stsc.p+16+(group+1)*12))group++;
      const n=v.getUint32(stsc.p+20+group*12);need(sample+n<=count,invalid);
      const pos=co.p+16+(chunk-1)*(wide?8:4),offset=wide?Number(v.getBigUint64(pos)):v.getUint32(pos);let length=0;
      for(let i=0;i<n;i++){const size=fixed||v.getUint32(stsz.p+20+4*sample++);need(size>0,invalid);length+=size;}
      if(fixed)sample+=n;
      need(Number.isSafeInteger(offset)&&mdats.some(b=>offset>=b.p+b.header&&offset+length<=b.end),invalid);
    }
    need(sample===count,invalid);
  }
  need(video,invalid);return {mime:'video/mp4',duration,ext:'mp4'};
}

/** Replace metadata boxes with equal-sized free boxes so chunk offsets remain valid. */
export function stripMovieMetadata(input) {
  mediaInfo(input);
  const bytes=input.slice(),view=new DataView(bytes.buffer),containers=new Set(['moov','trak','mdia','minf','stbl','edts','dinf']);
  function walk(start,end,depth) {
    need(depth<=8,'MP4 구조를 읽지 못했어요.');let p=start;
    while(p+8<=end){let size=view.getUint32(p);const type=String.fromCharCode(...bytes.slice(p+4,p+8));
      if(size===1){need(type==='mdat'&&p+16<=end,'MP4 파일이 손상됐어요.');size=Number(view.getBigUint64(p+8));}
      need(Number.isSafeInteger(size)&&size>=(view.getUint32(p)===1?16:8)&&p+size<=end,'MP4 파일이 손상됐어요.');
      if(['udta','meta','uuid'].includes(type)){bytes.set(new TextEncoder().encode('free'),p+4);bytes.fill(0,p+8,p+size);}
      else if(['mvhd','tkhd','mdhd'].includes(type)){const length=bytes[p+8]===1?16:8;need(size>=12+length,'MP4 파일이 손상됐어요.');bytes.fill(0,p+12,p+12+length);}
      if(containers.has(type))walk(p+8,p+size,depth+1);
      p+=size;
    }
  }
  walk(0,bytes.length,0);return bytes;
}
