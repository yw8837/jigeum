import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { randomInt, createHash } from 'node:crypto';
import { resolve, join } from 'node:path';
import { validateCodeManifest } from '../../outputs/PlayVerifier/src/access-codes.js';

// Run explicitly with the existing private manifest and a new directory outside Git.
const [source, destination] = process.argv.slice(2);
if (!source || !destination || !resolve(destination).replaceAll('\\', '/').startsWith('D:/Codex/Agattok/private-'))
  throw new Error('Use an explicitly named private directory on D');
const old = validateCodeManifest(JSON.parse(await readFile(source, 'utf8')));
if (old.schemaVersion !== 1) throw new Error('Expected original manifest; never overwrite an issued set');
const alphabet = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
const invitation = Array.from({ length: 10 }, () => alphabet[randomInt(alphabet.length)]).join('');
const shared = 'BABYHALF';
const hash = value => createHash('sha256').update(value).digest('hex');
const manifest = validateCodeManifest({ ...old, schemaVersion: 2, registrationLimit: 100,
  shortFamilyCodeSha256: hash(invitation), sharedDiscountCodeSha256: hash(shared) });
await mkdir(destination, { recursive: false });
await writeFile(join(destination, 'manifest.json'), JSON.stringify(manifest, null, 2) + '\n', { flag: 'wx' });
await writeFile(join(destination, 'codes-private.txt'),
  `아가톡 짧은 코드 · 새 앱 0.5.31(45)부터\n\n관리자·가족·지인 무기한 무료: ${invitation.slice(0, 5)}-${invitation.slice(5)}\n공용 출시 할인: ${shared}\n\n할인: 신규 구독자 첫 2개월 월 2,450원, 이후 월 4,900원 자동 갱신.\n등록 100건 상한 / 운영자 조기 마감 가능. 등록은 결제가 아닙니다.\n무료 코드는 기존 유료 구독을 자동 해지하지 않습니다.\n앱 입력 경로: 전체 이용권 → 이용 코드 입력.\n`, { flag: 'wx' });
console.log(JSON.stringify({ prepared: true, rawCodesPrinted: false, destination, schemaVersion: manifest.schemaVersion }));
