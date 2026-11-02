# Android 발성 후보 — 독립 두 축 코드 검토

2026-10-07. 처음 fixedpoint102a326→323c82f, 후속323c82f→3e26806.
검토자는각축의기존전용reviewer를재사용했다. source와허용safeJSON만읽고
원음/배열/modelbody·재추론/파일수정/빌드/ADB/Git는수행하지않았다.
메인이받은실제보고의의미를유지해아래처럼정리한다. cleanup의후속자동검토차단은별도QA에기록한다.

## Standards

**문서규칙 필수지적0, 선택적Fowler smell2.**

- core구현은FLOAT input/logits/head/embedding shape·finite/range, mutex/메모리guard/deadline/nativecancel,
  cleaned/slice/features/directbuffer zeroing을유지한다. 기존승인/shifted후general보완·감정/TV/오류/무음보호를확인했다.
- complete/orderedwindow와같은창head≥.8/RMS≥.003, general한정QUIET 예외·정확onset/리듬/긴발성/입술동기제외가명세와맞는다.
- 후보debug/APK와generated단일graph·notice/CC attribution/원BSD전문을확인했다. evaluator 종료metadata/model/headpin과과거보고정정도확인했다.
- 안전로그build05 exit0/54.248456초·child0/cleanuptrue, native56pass·설치SHA가일치했다.
  Hostpin15는12 rawGitblob일치·3 CRLF/LF동등이었다.
- 후속WAVdecoder test는runID/canonicalparent/metadata크기/3고정ID/format/count/duration/SHA/stale를검사한다.
  실제decoder호출45초·배열finallyzeroing·WAVcleanup을확인했다. 개발사례/원MP4미검증을명시한다.
- benchmark는candidate명시optin/package/flags, 실제head/RMS와coverage·PSS/time/thermal범위를검사한다.
  x86 synthetic측정을ARM승인으로확대하지않는다. safeJSON과QA의측정수가일치한다.
- 비차단DuplicatedCode1:InfantVoiceSupportPolicy의expectedwindow가ASTruntime과반복된다. tail의미를보존한공유helper를향후고려.
- 비차단DuplicatedCode2:AstRetainedPipelineTest의PCM16WAVheader/encoding이기존AstIntegratedCandidateTest와반복된다.
  같은encoding과zeroing책임을유지한testhelper를향후고려. 현재오류증거는없다.

## Spec

**필수지적0, 확인된범위확대0.**

- 같은창head와RMS·전체선택충돌·기존AST/shifted승인후general추가·같은native결과재사용이계약과맞는다.
- 별도genericwindow에events/moments를만들지않고wording/rhythm/긴발성/시간시각연결을제외한다.
  정확위치/지속/화자/욕구미확인을남긴다. beta/release/preview/bundle차단과새다운로드없음을확인했다.
- 종료metadata검사수정과과거56초run에새코드검사를소급하지않는보고가맞는다.
- 후속decoder실제3조건(두general회복/울음보존)·3.638/2.227/.149초가safeJSON과일치한다.
  약국원MP4없음·파생WAV·별도기존MP4 AV를구분한다.
- safeJSON의기존승인13 exact/public33 새generic0·같은원본2span/추가7조건새지원0, flags7/child0·archivepin이보고와일치한다.
- 144실행/고유143ID·반복PCMprobe1과Host중복을구분한다. 공개33trainoverlap·extra9의5누락·일반정확도미검증,
  당시cleanup/ARM/새배포미완료·goalactive를유지한다.

Standards필수0/비차단2, Spec필수0. 전체원로그144개를독립재실행한검토는아니다.
지금cleanup의자동검토거부와ARM/실폰·새beta미완료는[최신QA](infant-presence-android-20261007.md)를따른다.
