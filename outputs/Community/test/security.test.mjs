import { test } from 'node:test';
import assert from 'node:assert/strict';
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
test('MP4 duration is read from bytes rather than client input', () => {
  assert.equal(mediaInfo(mp4(20)).duration,20);
  assert.throws(() => mediaInfo(mp4(31)),/30초/);
  const b=mp4(20); b[16]=255; assert.throws(() => mediaInfo(b),/손상/);
});
test('video location metadata and creation dates are removed without moving sample offsets',()=>{
  const b=new Uint8Array(96);b.set(mp4(20));const v=new DataView(b.buffer);v.setUint32(16,80);
  v.setUint32(36,12345);v.setUint32(40,54321);v.setUint32(64,32);b.set(new TextEncoder().encode('udtaGPS+37.0+127.0'),68);
  const clean=stripMovieMetadata(b);assert.equal(clean.length,b.length);assert.equal(mediaInfo(clean).duration,20);
  assert.equal(new DataView(clean.buffer).getUint32(36),0);assert.equal(new TextDecoder().decode(clean.slice(68,72)),'free');
  assert.equal(clean.slice(72).every(x=>x===0),true);assert.notEqual(b[72],0);
});
