import { randomBytes, createHash } from 'node:crypto';
import { mkdirSync, writeFileSync } from 'node:fs';
import { isAbsolute, resolve } from 'node:path';
import { validateCodeManifest, CODE_CAMPAIGN, LAUNCH_OFFER_ID } from '../../outputs/PlayVerifier/src/access-codes.js';

const destination = process.argv[2];
if (!destination || !isAbsolute(destination)) throw new Error('Provide an absolute PRIVATE directory outside Git');
const repository = resolve(import.meta.dirname, '../..');
if (resolve(destination).toLowerCase().startsWith(repository.toLowerCase())) throw new Error('Codes must remain outside the repository');
mkdirSync(destination, { recursive: true });
const family = 'AGATTOK-FRIENDS-' + randomBytes(24).toString('base64url');
const discounts = Array.from({ length: 100 }, () => 'AGATTOK-LAUNCH-' + randomBytes(24).toString('base64url'));
const hash = value => createHash('sha256').update(value).digest('hex');
const manifest = validateCodeManifest({ schemaVersion: 1, campaignId: CODE_CAMPAIGN, offerId: LAUNCH_OFFER_ID,
  familyCodeSha256: hash(family), discountCodeSha256: discounts.map(hash) });
const write = (name, text) => writeFileSync(resolve(destination, name), text, { flag: 'wx', mode: 0o600 });
write('manifest.json', JSON.stringify(manifest, null, 2) + '\n');
write('friends-private.txt', `아가톡 관리자·가족·지인 공통 무료 이용 코드\n${family}\n\n개인적으로만 전달하세요. 공개 홍보에 넣지 마세요. 앱의 이용 코드 입력란에서 사용합니다.\n자동 결제 없음 / 이용 기간 제한 없음 / 최대24시간마다 또는 기기 재시작 후 인터넷 확인 필요.\n아직 기존0.5.27(41)에서는 사용할 수 없습니다. 새 코드 지원 앱 제공 확인 후 전달하세요.\n`);
write('launch-100-private.csv', '\uFEFFnumber,code,status,recipient_note\r\n' + discounts.map((code, index) => `${index + 1},${code},unissued,`).join('\r\n') + '\r\n');
console.log(JSON.stringify({ created: true, invitationCodes: 1, discountCodes: 100, destination,
  rawCodesPrinted: false, manifestSha256: hash(JSON.stringify(manifest, null, 2) + '\n') }));
