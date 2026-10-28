import { test } from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
const fixture=name=>new Uint8Array(readFileSync(new URL('./fixtures/synthetic.'+name,import.meta.url)));
import { hashPassword, checkPassword, mediaInfo, sameOrigin, cookieToken, textField, MAX_MEDIA, stripMovieMetadata } from '../lib/security.mjs';
test('password hashes have random salts, reject a different password', async () => {
  const a = await hashPassword('a-valid-password'), b = await hashPassword('a-valid-password');
  assert.notEqual(a.hash, b.hash); assert.notEqual(a.salt, b.salt);
  await checkPassword('a-valid-password', { salt:a.salt, password_hash:a.hash });
  await assert.rejects(checkPassword('a-wrong-password', { salt:a.salt, password_hash:a.hash }), /맞지/);
  await assert.rejects(hashPassword('1234'), /8~64/);
});
test('cross origin and simple form requests cannot mutate', () => {
  assert.throws(() => sameOrigin(new Request('https://board.test/api', {headers:{origin:'https://evil.test','x-board-client':'1'}})));
  assert.throws(() => sameOrigin(new Request('https://board.test/api', {headers:{origin:'https://board.test'}})));
  sameOrigin(new Request('https://board.test/api', {headers:{origin:'https://board.test','x-board-client':'1'}}));
});
test('cookie parsing rejects malformed tokens; text bounds are enforced', () => {
  assert.equal(cookieToken(new Request('https://b.test', {headers:{cookie:'agattok_visitor=admin'}})),null);
  assert.equal(textField('  익명  ',20),'익명');
  assert.throws(() => textField('x'.repeat(1001),1000));
});
test('reject SVG, PNG disguised as MP4, oversize, and JPEG EXIF', () => {
  assert.throws(() => mediaInfo(new TextEncoder().encode('<svg onload="alert(1)"></svg>')));
  assert.throws(() => mediaInfo(new Uint8Array(MAX_MEDIA+1)));
  const jpeg = new Uint8Array(25); jpeg.set([255,216,255,225,0,6]); jpeg.set([255,217],23);
  assert.throws(() => mediaInfo(jpeg), /위치/);
});
function mp4(seconds) {
  const b = new Uint8Array(64), v = new DataView(b.buffer);
  const box = (p,n,type) => {v.setUint32(p,n);b.set(new TextEncoder().encode(type),p+4);};
  box(0,16,'ftyp'); box(16,48,'moov'); box(24,40,'mvhd'); v.setUint32(44,1000); v.setUint32(48,seconds*1000);
  return b;
}
test('real JPEG and MP4 structure accepted; signature-only files and truncated media rejected',()=>{
  assert.equal(mediaInfo(fixture('jpg')).mime,'image/jpeg');
  assert.equal(mediaInfo(fixture('mp4')).mime,'video/mp4');
  assert.throws(()=>mediaInfo(mp4(20)));
  const jpeg=new Uint8Array(25);jpeg.set([255,216,255,218]);jpeg.set([255,217],23);assert.throws(()=>mediaInfo(jpeg));
  const b=fixture('mp4');assert.throws(()=>mediaInfo(b.subarray(0,b.length-16)));
  const co=Buffer.from(b).indexOf('stco');assert.ok(co>0);
  new DataView(b.buffer).setUint32(co+12,b.length+1);assert.throws(()=>mediaInfo(b));
});
test('MP4 duration is read from movie and track bytes rather than client input',()=>{
  const b=fixture('mp4'),mvhd=Buffer.from(b).indexOf('mvhd'),v=new DataView(b.buffer);
  const scale=v.getUint32(mvhd+16);v.setUint32(mvhd+20,31*scale);assert.throws(()=>mediaInfo(b),/30초/);
  const track=fixture('mp4'),mdhd=Buffer.from(track).indexOf('mdhd'),tv=new DataView(track.buffer);
  tv.setUint32(mdhd+20,tv.getUint32(mdhd+16)*31);assert.throws(()=>mediaInfo(track),/30초/);
});
test('video metadata and creation dates are removed without moving samples',()=>{
  const original=fixture('mp4'),b=new Uint8Array(original.length+32);b.set(original);const v=new DataView(b.buffer);
  v.setUint32(original.length,32);b.set(new TextEncoder().encode('udtaGPS+37.0+127.0'),original.length+4);
  const mvhd=Buffer.from(b).indexOf('mvhd');v.setUint32(mvhd+8,12345);
  const clean=stripMovieMetadata(b);assert.equal(clean.length,b.length);assert.equal(mediaInfo(clean).duration,mediaInfo(original).duration);
  assert.equal(new DataView(clean.buffer).getUint32(mvhd+8),0);
  assert.equal(new TextDecoder().decode(clean.slice(original.length+4,original.length+8)),'free');
  assert.equal(clean.slice(original.length+8).every(x=>x===0),true);
  const mdat=Buffer.from(b).indexOf('mdat'),size=v.getUint32(mdat-4);
  assert.deepEqual(clean.slice(mdat+4,mdat-4+size),b.slice(mdat+4,mdat-4+size));
});

test('short movie headers cannot conceal a long sample timeline',()=>{
  const b=fixture('mp4'),stts=Buffer.from(b).indexOf('stts'),mdhd=Buffer.from(b).indexOf('mdhd'),v=new DataView(b.buffer);
  const samples=v.getUint32(stts+12),scale=v.getUint32(mdhd+16);
  v.setUint32(stts+16,Math.ceil(scale*60/samples));assert.throws(()=>mediaInfo(b),/30초/);
});

function extendedMdatFixture() {
  const original=fixture('mp4'),tag=Buffer.from(original).indexOf('mdat'),p=tag-4;
  assert.ok(p>=0);const old=new DataView(original.buffer),size=old.getUint32(p);
  const b=new Uint8Array(original.length+8);b.set(original.subarray(0,p+8));b.set(original.subarray(p+8),p+16);
  const v=new DataView(b.buffer);v.setUint32(p,1);v.setBigUint64(p+8,BigInt(size+8));
  // This pinned synthetic fixture uses stco. Inserting the extra header moves samples by eight bytes.
  let found=0,from=0;
  while(true){const co=Buffer.from(b).indexOf('stco',from);if(co<0)break;found++;from=co+4;
    for(let i=0;i<v.getUint32(co+8);i++){const at=co+12+4*i,offset=v.getUint32(at);if(offset>=p+8)v.setUint32(at,offset+8);}}
  assert.ok(found>0);return {b,p,size:size+8};
}

test('Android-style extended mdat accepts real samples and preserves them while stripping metadata',()=>{
  const {b,p,size}=extendedMdatFixture(),info=mediaInfo(b),clean=stripMovieMetadata(b);
  assert.equal(info.mime,'video/mp4');assert.deepEqual(mediaInfo(clean),info);
  assert.deepEqual(clean.subarray(p+16,p+size),b.subarray(p+16,p+size));
  assert.equal(new DataView(clean.buffer).getBigUint64(p+8),BigInt(size));
});

test('extended mdat rejects truncated, unsafe and out-of-bounds sizes and sample offsets inside its header',()=>{
  for(const size of [8n,15n,BigInt(MAX_MEDIA+1),BigInt(Number.MAX_SAFE_INTEGER)+1n]){
    const {b,p}=extendedMdatFixture();new DataView(b.buffer).setBigUint64(p+8,size);assert.throws(()=>mediaInfo(b));}
  const {b,p}=extendedMdatFixture();assert.throws(()=>mediaInfo(b.subarray(0,p+12)));
  const co=Buffer.from(b).indexOf('stco');new DataView(b.buffer).setUint32(co+12,p+8);assert.throws(()=>mediaInfo(b));
});

test('too many tiny MP4 boxes are rejected with a bounded allocation budget',()=>{
  const b=new Uint8Array(8*4100),v=new DataView(b.buffer);
  for(let i=0;i<b.length;i+=8){v.setUint32(i,8);b.set(new TextEncoder().encode('free'),i+4);}
  assert.throws(()=>mediaInfo(b));
});
