# 새 공개 출처 3곳과 말소리 반례 — 동결 head 첫 평가

2026-10-07 KST. [평가 전 계약](20261007-extra-babble-evaluation-contract.md)의 실제 첫 실행이다.
**새 영아 caption/tag 후보9개 중4개에 broad support, 할아버지 gibberish 후보1개는 보류**였다.
3개 uploader 모두 한 파일 이상 support가 있었지만 웃음·dada를 옹알이 성공으로 합산하지 않는다.
5개 후보는 여전히 보류됐다. 정확월령·자연영아·사건 정답을 확인한 자료가 아니어서 정확도4/9로 보고하지 않는다.

## 고정 자료와 전체 결과

요청10/취득10/decode10/scoring10/quiet-only0/missing0/실행blocked0.
실제MP3 preview 합계705,098B; original WAV/FLAC 취득이 아니다. training_blocked=true/approved label=null은10개 모두 유지했다.
학습·정규화 fitting·calibration·threshold/L2 선택·사용자자료 접근0이다.
동결 graph SHA `4a378476…7f802a`, head SHA `81d7357c…bdfd510`, support .8/.4 유지.

| ID / caption source | caption 종류 | 실제 span score | broad support |
|---|---|---|---|
|416677 / FunWithSound|아기/toddler gibberish|.665506|보류 |
|416681 / FunWithSound|아기/toddler gibberish|.305799|보류 |
|416635 / FunWithSound|grunt|.650390|보류 |
|416702 / FunWithSound|웃음|.937084|통과 |
|259609 / Stevious42|baby babble + crinkle|.587685 / .848410|한 span 통과 |
|254454 / wjoojoo|babbling; baby는 tags만|.438025 / .574800|보류 |
|254455 / wjoojoo|babbling; baby는 tags만|.753789|보류 |
|254465 / wjoojoo|dada; baby는 tags만|.935423|통과 |
|254471 / wjoojoo|dada; baby는 tags만|.572874 / .962260|한 span 통과 |
|128479 / gipsyGA|본인 cartoon-grandpa gibberish|.073074|보류 |

uploader별 support는 FunWithSound1/4(웃음),Stevious42 1/1(혼합),wjoojoo2/4(dada)다.
이는 **파일의 caption/tag와 score의 관계**이며 세독립 영아가족·대상월령 recall·성인 false-positive rate가 아니다.
할아버지caption도 실제 성인나이/infant absence 사건 정답은 미검수다. TV/news·아기흉내 정답으로 부르지 않는다.
Deleted393504는 처음부터 취득대상에 넣지 않았고 임의mirror를 쓰지 않았다.

source JSON의 과거train후보designation은 유지하고 실행의 별도 row만 `evaluation_only`로 묶었다.
metadata9 원SHA `a7658a7a…d045b` 불변. 원page/license/previewURL/bytes/SHA/category/
claimstrength/unknownage/unknownnaturalness/actualsamples/padding/RMS/inputSHA를각row에연결했다.
13개의 비중첩10초 span에동결graph를실행했고NumPy head 최대차이1.33035e-7/5e-5 이내다.
최대20초전체를내용상연속이해하거나실제발성시간을입증한것은아니다.

## 실행과 보호

Job180초3GiB: exit0/56.001223초/peak committed1,498,492,928B/termination없음·cleanuptrue·자손0.
HTTP는공식exactCDN/redirect금지/2MiB/file/20초timeout, 완전히취득한파일마다SHAprogress를기록했다.
이번취득은timeout/부분실패0·재사용0이며 같은파일재취득을반복하지않았다.
취득후Python network금지, FFmpeg `file,pipe` protocol/`mp3` format whitelist, PCM16mono16k첫20초를썼다.
하나의PC ORT session 비용이다. Android/휴대폰속도·발열·전체AV 품질 측정으로 보고하지 않는다.

실행code SHA `9be67b327419144baf380fcb511b2eecbe7d0e3261730e5076d58dfa273561a7`,
completion의추가5개source SHA/runtime·metadata 시작pin 검사·head/model 종료 재검사를 확인했다.
독립 Standards 리뷰에서 당시 metadata 종료hash가 별도 실측되지 않았음을 발견했다.
현재 metadata는 원pin과 일치하나 과거 종료 관측을 대신하지 않는다. 후속 코드에 종료metadata 재hash를
추가했으며, 기존56.001초 실행을 이 새코드로 수행한 것으로 소급하지 않는다. 수치 재추론은 하지 않았다.
단위2/실제CLI4거부는exit0; fixedID/source-role/승인label/CC NC/evilURL/틀린SHA를거부,
CLI stale/outside/missing/다른source파일에서기존head불변·새output/fixture0을확인했다.
이는 **평가 실행의 preflight**이며 새 전체UltraQA/Android QA 완료는 아니다.

## 용량과 다음

새원음preview10개는후속Android 비교에필요한한세트만유지했다. 새weight/modeldownload·PCM/feature파일저장0.
원개인영상/제품asset/기존차단fixture·다른작업을보존했다. ignored `extra-eval01/`와`guards-extra-eval01/`가증거다.
전후 `storage-before-extra-eval01.json`/`storage-after-extra-eval01-final.json`:
작업폴더98,353,160→99,098,688B/298→322files, 증가745,528B.
C여유76,243,914,752→76,225,900,544B. C전체변화와실험폴더증가는구분한다.

약국누락 회복이한원본에만묶여있다는한계를이번평가로지우지않는다.
현재head는기존엔진을대체할모델이아니며 광범위한옹알이성능승인도없다.
다음은 **연구Android 후보에일반영아발성단서로연결**해기존웃음/울음/옹알이수용을보존하고,
실제누락의전체경로·20초/취소/메모리·ARM을검사한다. general presence를Babbling으로치환하지않는다.
현재제품0.5.18/code32 그대로·새beta없음/전체goal active.
