# 활성 다음 단계 — 의미 있는 인식·맥락 개선까지 지속

### 최신 — 2026-10-12 영상 저장 수정의 실폰 확인

1. [저장 QA](qa/saved-media-20261012.md)의 수정 소스/연구APK를 유지한다. host44통과,
   Android48중47통과·HDR codec1실패·skip0이다. 전체를 통과로 기록하지 않는다.
2. 사용자 S25 연결 동의 후 USB 디버깅이 `unauthorized`다. 허용 요청을 전달했다.
   승인되면 `work/saved-media-20261012/phone.py <serial> phone01`로 별도 astcandidate 패키지를 설치해
   SavedMediaFlowTest/CaptionHdrCompatibilityTest/TranslatedMediaTests를 최대420초 검사한다.
   기존 Play 설치·기록은 보존한다. 기존 연구 패키지가 있으면 상태를 먼저 검사하며 덮어쓰지 않는다.
3. 실폰 합성 HDR 성공을 확인한 뒤 사용자 오류 영상으로 두 저장 경로를 확인한다.
   원본을 외부로 보내지 않는다. GL gainmap 결함과 실제 사용자 실패 원인을 구분한다.
4. 새 raw 저장은 분석이 없어도 선택 구간+원래 소리를 저장한다. 새 입력 초기화는 검증했지만
   표현이 같은 실제 장면의 구분 정확도는 미입증이다. 짧은 반복 발성의 시간 흐름만 추가했다.
5. 기존code38 첫20초45초timeout gate는 별도로 남아 있다. 기존 서명38에는 이번수정이 없으므로
   그 파일을 새 수정본으로 제출하지 않는다. 새 성능/서명 확인 후 기존Alpha 업데이트만 진행한다.
   현재 마지막 제공확인37/자동화33PAUSED. 에뮬레이터·QA앱·임시미디어 정리는 완료했다.

### 최신 — 2026-10-10 정리 우선 / code38 성능 gate 남음

1. [발성부분관찰 후보](qa/vocal-kind-20261010.md)는구현·고정53조건·서명업데이트보존과
   동일20초후속2회부분결과/저장을통과했다. 최초서명20초45초timeout원인이미확정이므로
   **Play38제출보류**, 자동화33 PAUSED유지, 게시37유지다. timeout증거를삭제하지않는다.
2. 다음성능확인은새빌드/모델다운로드없이현재서명38과동일SHA영상으로범위를제한한다.
   PC부하/메모리·cold/warm상태·UI검사도구간섭을통제하고실제시작–종료를기록한다.
   후속성공을원인해결로간주하지않는다. 재발하면취소/재시도경로까지확인한다.
3. 종료APK/AAB31개삭제·실제3.71GiB회수. [정리보고서](qa/storage-cleanup-20261010.md)와
   [차단파일목록](qa/storage-cleanup-20261010-blocked.csv)을따른다. 원래80패키지·TalkNet2가중치·
   기존3build의삭제는자동정책차단이며대체방법으로우회하지않는다. 원본·키·제품모델보존.
   사용자의정확한대상재승인후표준삭제도실행전차단됐다. 현재사용자승인은충분하며
   Windows관리자권한부족/파일잠금으로오인하지않는다. 82파일/3폴더현존·재시도삭제0.
4. 코드와QA상태기록을보존하고시험앱/미디어/에뮬레이터를종료한다. 종료뒤필요없는생성물을
   다시분류하며빌드캐시를새위치에중복생성하지않는다. 미해결성능때문에38검증파일은유지한다.

아래는 현재 게시판37과 이전 작업 이력이다.

### 최신 — code37 구간 관찰 보완 Alpha 제공 확인

1. [구간 관찰 QA](qa/interval-evidence-20261009.md) 제품 검증 완료. 실제53조건의 기존26출력 유지,
   부분발성2조건 추가·대조 신규출력0, 넓은 음성창의 시간순 영상 연결1조건 확인.
   알려진 개발 자료·동일 원본 변형을 포함하므로 일반 인식률이나 욕구 정답 증가로 환산하지 않는다.
2. 서명36→37 기록/MP4 보존, beta37 실제 분석/저장/내려받기, 정식release37 재잠금 검사를 통과했다.
   검증한beta37 AAB만 기존 Alpha 출시17에 업로드·검토 요청했다(2026-10-09 22:01 KST 확인).
3. **2026-10-09 23:32 KST** Alpha의 정확37 **선택한 테스터에게 제공됩니다**를 확인했다.
   Console 게시일23:28 KST, 게시 개요의 검토 항목 해소. [제공 근거](qa/interval-evidence-20261009/play-review.md).
   제공 근거6c8c7b1 커밋·푸시·SHA/clean 확인 후 자동화33을 **PAUSED**로 갱신했다.
   새 빌드·중복업로드·다른트랙·테스터 설정 변경은 필요 없다.
4. QA기기앱/외부미디어 제거·에뮬레이터 종료 완료. 새build969MB 삭제는 자동정책차단이며
   이전app/build2.38GB·recovery-build280MB도 보존한다. 삭제수단 변경으로 우회하지 않는다.
5. 이후 독립 실패 입력 확대, 넓은 AV 연결의 화자/시간엇갈림 구분과 남은 보류 개선이 필요하다.
   현재 분석범위와 실제 정답을 구분하고 기존 성공/성인·배경 대조를 보존하며 진행한다.

아래는 이번 구현 전의 단계와 이전 배포 이력이다.

### 최신 — S25 측정 완료 후 남은 개선

1. [실폰 QA](qa/visual-cadence-phone-20261009.md): 얼굴10/20초·반복·취소/재개 확인 완료.
   연구app은배포36과동일200ms소스이며폰의Play설치본35는보존했다. 추가QA를위한연결유지불필요.
2. 사용자확정 **정확도우선**. 음성보류와시각연결을먼저검증·보완한다. 같은시간의아기발성단서와
   얼굴/입변화를대조하되성인음성·무음·더빙/시간엇갈림·다중얼굴을대조군으로검사한다.
   얼굴존재만으로아기발성을확정하거나모델임계값을낮추지않는다. 더넓은독립실패입력검증은남는다.
3. 현재20초PCM전체/AST0–10·5–15·10–20초/영상100관찰coverage는실측확인됐지만통합맥락정확도와
   다르다. 얼굴20초시각단계약9.8초의최적화는정확도작업뒤다룬다. 동일관찰/출력/해상도조건과
   전후동일입력측정이기준이며20초를채우려고대기를넣거나관찰수를줄이지않는다.
4. 작은64x64H264의기본하드웨어미지원은제품fallback후속검토사항이다. 이번에는취소시험입력만
   지원되는320x320으로보완했다. 모든비디오호환성해결로보고하지않는다.
5. 새test재빌드280MB정리는사용자승인후에도자동정책차단이다. 이전app/build2.38GB는별도승인대기.
   다른삭제수단으로우회하지않는다. 새배포·반복빌드없이검증APK/작은근거보존,원래앱/자료보호.

### 최신 — code36 기존 Alpha 제공 확인 완료

1. [배포QA](qa/visual-cadence-release-20261009.md)의최종서명/DEX200ms/모델/접근flags,
   얼굴부하·35→36기록/미디어보존·새beta AV/저장·정식재잠금은통과했다.
2. 사용자S25연결불가+개선판업데이트요청에따라실폰미측정을명시한제한된Alpha제출을진행한다.
   기존200ms후보검증을재사용하며60초촬영전체검사/폰속도/일반정확도를새검증으로세지않는다.
3. beta36 AAB만 기존 Alpha 출시 ID16에 올려 **10월9일18:56 KST 검토 요청**했다.
   **19:27 KST** Alpha에서 정확36의 **선택한 테스터에게 제공됩니다**를 확인했다.
   Console 게시일은19:24 KST이며 게시 개요의 검토 항목도 해소됐다.
   [제공 증거](qa/visual-cadence-release-20261009/play-review.md)의 `069cc7b`를 푸시·SHA/clean 확인 후
   자동화33을 **PAUSED**로 갱신했다. 추가 빌드·중복 제출·테스터 설정 변경은 필요 없다.
4. QA기기앱/에뮬레이터는정리했다.소유app/build2.38GB삭제는자동정책차단으로사용자확인대기.
5. 배포 이후 S25 측정은 위 실폰 QA에서 마쳤다. 장시간·다른기종성능, 음성보류와시각연결,
   더넓은실패입력검증은 남는다.

### 최신 — 구간 시각 관찰 후보의 실폰 측정과 AV 판정 보완

1. [시각cadence QA](qa/visual-cadence-20261009.md)의로컬98Android/19host/5Python/통합1검사는완료했다.
   500→200ms 후보에서약국보관10초의입상태변화6→19,실제로놓쳤던두번입열림을확인했다.
   전체발성회복/욕구/통합AV모델성능으로보고하지않는다.
2. S25연결요청에답이오면보존된별도연구APK로얼굴있는10/20초·반복·취소/발열을확인한다.
   현재기기연결없음.같은에뮬레이터검사나대형빌드를반복할이유는없다.
3. 현재불만의원본/앱버전/결과문구를확인해음성보류·시각검출·시간연결의어느단계인지재현한다.
   기존약국원본은없지만250JPEG/PCM을재사용할수있다.파생자료를새독립정답으로세지않는다.
4. 현재음성보류는시각판정전에반환되고AST broad도시각연결에서제외된다.이경계의안전한
   보완이본래인식문제의다음우선순위다.입움직임/얼굴만으로아기발성을확정하지않는다.
5. 실폰측정뒤다음beta의버전증분·최종패키징/회귀·기존Alpha업데이트를진행한다.
   기존35는17:56에게시됐으며,200ms후보는아직미배포다.중복업로드하지않는다.
6. 이번연구APK2개/작은근거를보존하고중간1.02GB정리완료.공용AVD/모델/원본/250JPEG유지.

### 완료 — code35 실제 테스터 제공 확인

1. [현재 QA](qa/corroborated-vocal-20261009.md)의 서명 접근7시나리오·업데이트 보존·소유 QA 정리를 마쳤다.
   2cycle complete/clear/부재, 중간파일3.6GB 제거. 새 빌드나 ADB 반복은 필요하지 않다.
2. 앱 소스 `ed52fae`와 증거 `86cbd14`를 기존 `codex/ondevice-av-probe`에 푸시하고 SHA/clean을 확인했다.
3. 검증된 `Agattok-0.5.21-beta-code35.aab`만 기존 Alpha에 올려 **10월9일17:20 KST 검토 요청**했다.
   AAB SHA256은 `6608e17f827c193df3a82a6b3eaa595467ba880037d40fb343877444fc36cd13`.
4. **10월9일18:13 KST** Alpha의 정확35 `선택한 테스터에게 제공됩니다`를 확인했다.
   Console 게시일17:56 KST, 게시 개요의 검토 항목 해소. [배포 기록](qa/corroborated-vocal-20261009/play-review.md).
   작은 화면/텍스트 증거 `ba80920` 푸시·SHA일치·clean을 확인하고 자동화33을 **PAUSED**로 갱신했다.
   기존 테스터 목록을 변경하지 않았고 실제 계정별 설치/연속14일을 새로 검증하지 않았다.
5. 이번 제한된 추가 출력과 일반 인식·욕구 정확도는 구분한다. 남은 보류·영상 맥락/표현 개선은 후속 과제다.

아래는 이전 실행 이력이다.

### 최신 — code34 게시 확인·설치 완료 소식

1. 2026-10-07 22:14 KST Alpha의 정확34 `선택한 테스터에게 제공됩니다`를 확인했다.
   Console게시일22:06 KST. 기존 세 테스터 목록33/4/2선택은 유지됐다.
2. 배포증거·QA/summary/인수인계 `c83652f`를푸시·SHA대조했고 자동화33은PAUSED다.
   [완료 감사](qa/accepted-vocal-audibility-20261007/completion-audit.md)의 마지막 외부요건도충족됐다.
3. 사용자가 업체의 전체 설치 완료 소식을 전달했다. 계정별실제설치·설치버전·참여시작일과
   연속14일을 직접 검증한 것은 아니다. 최소12명의연속14일은 Play의실제충족상태로 별도판정한다.
4. 배포완료로 일반정확도/욕구해석을 입증하지 않는다. 남은웃음/다른옹알이/작은울음보류는
   감사의원천목록에보존했다. 이번변경으로추가모델·업로드·학습·정식정책변경은없다.
5. 반복빌드/ADB·중복업로드는필요없다. 필요한최종/직전패키지·증거만보존한다.

아래 검토 대기 단계는 게시 전 이력이다.

### 현재 다음 실행 — 검토 요청한 beta34 Alpha 실제 게시 확인

1. [현재QA](qa/accepted-vocal-audibility-20261007.md): 동일28조건에서작은옹알이2조건회복,
   성인/잡음새출력0, 기존53조건모두exact보존·native100통과를확인했다.
2. beta34/release34패키지·33→34실제기록/미디어·전체beta/정식재잠금·실제AV는통과했다.
   큰모델다운로드·임계값튜닝·평가자료학습은하지않는다.
3. 최종독립review필수각0·2cycle lifecycle정리완료. `31ecf70`푸시/SHA대조후
   2026-10-07 21:25 KST 기존Alpha에beta34검토요청을전송했다.
   21:38 KST사전검사종료·정확34의검토중변경사항을확인했다. 현재제공확인은code33까지다.
4. 자동화33이15분간격으로실제게시를확인한다. 34의선택테스터제공확인후작은증거를
   저장하고QA/HANDOFF/NEXT/summary를커밋·푸시·SHA대조, 자동화일시중지및목표감사를한다.
   같은상태에서는조용히종료한다. 중복업로드/제출취소/다른트랙/테스터목록변경을하지않는다.
5. 임시입력·소유QA설치/실행·중복패키지약805MB정리는완료됐다. 최종/직전파일과공용AVD는보존했다.
   현재실기기연결·반복빌드/ADB는필요없다. 실제12명연속14일은업체참여확인부터별도로추적한다.


### 현재 — Zardu 등록까지 게시 완료, 업체 참여 시작 준비 완료

S25 caption ARM·clean 서명빌드·패키지검사·32→33 기록/미디어 보존은 완료됐다.
상태와 실패를 [caption beta QA](qa/caption-beta-20261007.md)에서 확인한다.
기존5cycle은 `ULTRAQA STOPPED: Max cycles`이며 같은 환경에서 반복하지 않는다.
이후 사용자 PC재부팅을 확인해 별도scope에서 남은동일게이트가 exit0/404.67초로 통과했다.
영상/소리10초 MP4 hash보존·PDF·재시작·정식제한까지 확인했고 소유QA설치/실행도 정리했다.

1. 독립 Spec/Standards최종필수각0·새scope complete/clear/부재 완료. 검증을 불필요하게 반복하지 않는다.
2. 소스·QA문서 `7dae68a`푸시·local/remote SHA일치, beta33 AAB업로드·검토전송 완료.
3. Alpha33은2026-10-07 19:08 KST게시·**선택한 테스터에게 제공됩니다**를 확인했다.
   이후 사용자 요청의 Zardu33명+기존그룹4명+기존2명목록 선택을Alpha에 저장/제출했다.
   [테스터 등록 기록](qa/zardu-testers-20261007.md): 제출13은19:28 KST출시됨이며,
   19:38 KST에검토항목해소·세목록선택33/4/2·참여링크를확인했다.
   승인 확인 자동화33은 기록 푸시·SHA 대조 후 일시중지한다.
   업체계정 로그인/실제설치는 업체 확인 대상이며 운영자 페이지접근으로 대신하지 않는다.
   새 업로드/재빌드·다른트랙·프로덕션 변경은 필요하지 않다.
4. 소유 임시파일/실행만 정리하고 공용MoaQA35폴더·원본·기준산출물은 보존한다.
5. 사용자에게 업체 전달용 설치/웹 참여 링크를 전달한다. 다음 외부 진행은 업체의 실제 참여·설치 확인이며,
   모집 사실과 실제 피드백을 바탕으로 12명 연속14일 조건 및 추후 프로덕션 신청을 별도 확인한다.

아래 ARM 대기 단계는 과거 이력이다.

### 최신 다음 실행 — frozen caption pair의 실제 ARM 측정

연구 Android 후보는 고정53조건에서 기존18출력 보존+2추가출력을 확인했다.
[결과/QA](qa/caption-android-20261007.md), [새ARM계약](research/20261007-caption-arm-contract.md)을 읽는다.
실제폰0/새beta0/code32유지이며 기존head APK proof로 이번 caption runtime을 승인하지 않는다.

연결 재점검: ADB 목록·무선 debugging mDNS는 비어 있고, Windows PnP에는 S25의
Bluetooth만 있으며 Samsung USB/Android ADB 인터페이스0이다. 데이터 USB 케이블 연결,
휴대폰 잠금 해제·USB 디버깅 허용 뒤 같은 fresh caption 시험을 진행한다. 드라이버 재설치나
다른 기기 연결을 임의로 실행하지 않는다. 현재 코드 변경 없이 재연결이 필요한 상태다.

1. 물리기기가 연결되면 JDK21/JAVA_HOME과 전용serial을 지정하고 현재
   `work/caption-android-20261007/final/caption-app.apk` 및 `caption-test.apk`를 사용한다.
   `tools/research/ast_device_benchmark.py --infant-caption-candidate`에 explicit
   `--serial --adb --apkanalyzer --apk --test-apk --output`을 전달한다. 새 output만 허용한다.
   사용자 영상/기록 대신 합성10/20/20초, 실제 native 취소/재개·PSS·발열·기존 설치 보존·정리를 확인한다.
2. 통과한 fresh proof를 별도로 재검증·독립 검토한 뒤 caption beta flag와 builder/payload verifier를 연결한다.
   현재 debug caption flag는beta에 쓰면 거부된다. 제품versionCode>32와 서명/기존beta업데이트 보존을 검사한다.
3. 승인된 beta AAB만 실제 Play 비공개 테스트에 새버전으로 제출하고 상태를 확인한다.
   source captions/겹치는 샘플 지원을 독립 일반정확도·배고픔/졸림 판정으로 바꾸지 않는다.
4. 이득이 남아 있어도 성능·오인·기존 승인 보존에 실패하면 수정하고 해당 위험만 재검증한다.
   이전 실패후보 반복·사용자 학습·큰LLM다운로드·과거삭제차단 우회는 하지 않는다.

아래 다음 실행들은 이전 단계의 이력이다. 최신고정 pair와mode는 위 경로를 따른다.

### 다음 실행 — 새 공개 source의 별도 weak bag 가설

- [AST768 MIL](research/20261007-ast-mil-results.md)은실제validation14/retained3/public33 지원변화0으로미채택한다.
  추가동일학습/threshold튜닝을하지않는다. 기존약국회복후보와ARMgate는유지한다.
- [취득완료2source](research/20261007-new-infant-audio-review-acquisition.md)의blocked/null/미검수는그대로다.
  [연구경계](research/20261007-weak-presence-training-boundary.md)에따라exact2ID/권리/역할/실험ID allowlist와
  전체97.518초coverage·tail/padding·bag당noisy caption loss·source분할/반례의
  [별도사전계약](research/20261007-caption-wholebag-contract.md)을작성했다. 다음은그exact실험의구현/실제비교다.
  원caption을실제사건정답으로쓰거나first20초에존재target을상속하지않는다. 사용자자료/held는학습금지다.
- 의미있는새신호가나오면원발성·감정guard/화자미확인·실제Android/ARM을분리해검증한다.
  제품0.5.18/code32·새beta/Playupload0. 실제ARMproof없이패키징gate를우회하지않는다.

### 원인별 후속 — 51조건 점수 재생 뒤

- [결과](research/20261007-presence-context-replay-results.md): 이미기록된출력18보존,
  broad단서부족25, highpresence+감정context보류8이다. 원model추론/신규회복/앱반영0,
  [한정metadata QA](qa/presence-context-replay-20261007.md)만완료다. 전체goal active.
- broad검출25에는학습양성7clip/4source/40.7초라는결손이있다. 새자연영아source/사건검수·성인/TV/호흡반례를
  확보하며기존held/eval을train으로옮기지않는다. [새2source 원문조사](research/20261007-next-infant-training-source.md)는
  18개월9437/12개월244237의현재CC0/preview를고정했다. 현재원음/사건검수/학습승인0·blocked/null이다.
  다음은점수노출전원음검수/권리·생활잡음/파생관계확인이고caption을자동사건정답으로학습하지않는다.
- 감정context8에는발성존재와감정/화자귀속분리가필요하다. 기존neutralOnly에추가generic창을넣거나
  단일tail에맞춰guard를없애지않는다. sidecar는진단이며제품한마디승인이아니다.
- 이미약국의회복을확인한후보는기존동결pair로실제ARM을검증한다. 현재ADB실폰0/베타미반영.
  새대형model/APK를쌓지않고실기기·서명update/권한QA후code>32의closedbeta로진행한다.

### 추가 공개자료 평가 후 — 감정충돌과 실제ARM

- [결과](research/20261007-public-infant-followup-results.md): 동결 head의 public3/14창 반응을 확인했고,
  actual Android public5선택+기존3대조에서 기존승인/대조 exact보존·새public회복0을 확인했다.
  9개월tail은 head/RMS 통과 뒤 기존 YAMNet 울음충돌 guard로 보류된다.
- 이 하나의 caption-only clip에 맞춰 임계값/충돌 보호를 낮추지 않는다. 발성존재 단서와 감정단서를
  분리하는 표현계약·시간정렬/성인감정혼합 반례 검토를 다음 분석으로 삼는다. 사용자자료 학습0 유지.
- 실제ARM proof가 아직 없으므로 beta패키징/업로드 gate는 유지한다. ADB실폰0/새Play제출0/code32 그대로다.
  필요한 frozen research APK/test는 기존 final 한세트를 재사용하며 새대형모델을 받지 않는다.
- [QA](qa/public-infant-followup-20261007.md)는 행동검사 통과/중복publicMP3정리 자동정책차단으로 cleanup BLOCKED다.
  중복3개2.19MB는 남기고 우회하지 않는다. state/연구앱/runtime/이번AVD실행은 정리했다.
- [사건라벨 후속](research/20261007-babble-event-label-next.md)의 ACLEW tutorial/GoldStandard metadata를
  정확 enum/timeunit·권리까지 확인한다. 접근 제한 원음/labels를 허락 없이 취득하지 않는다.

### 현재 실행점 — 준비된 후보의 실제 ARM 검증

베타 적용 옵션/패키징 검사는 준비됐다. [계약](research/20261007-infant-presence-beta-prep-contract.md) /
[QA와 기록 누락](qa/infant-presence-beta-prep-20261007.md).
현재 ADB 물리 기기0, 실제ARM 성공 proof0, 새beta 패키지/Play 업로드0이다.
code32를 다시 올리거나 에뮬레이터/mocked unit을 실폰 증거로 쓰지 않는다.

1. S25가 연결되면 보존 `work/infant-presence-android-20261007/final/`의 연구 APK/test 한세트로
   `tools/research/ast_device_benchmark.py --infant-presence-candidate`를 실행한다.
   serial/adb/apkanalyzer/apk/test-apk/output을 명시한다. 새 output은 기존 디렉터리를 덮어쓰지 않는다.
2. 10/20/20초 실제 속도·sampled PSS·thermal·native 취소/재개·기존 앱 설치 보존·연구앱 정리를 검사한다.
   새 runner는 시작/종료 용량도 기록한다. 실제 실패면 원인 수정 후 해당 위험을 다시 검사한다.
3. 실제통과 후 versionCode>32로 새beta를 만들고 `-InfantPresenceSupport -AstBabbleSupport`
   및 real `-InfantArmProof -ProofPython`을 공식 builder에 전달한다. directGradle도 같은 proof를 요구한다.
4. 서명·최종 archive/compiled flag·기존 beta 업데이트/로컬 기록·저장/취소 회귀·독립 검토 뒤
   Play 비공개 테스트에 새beta만 제출한다. 일반 정확도/욕구 생성의 완료로 해석하지 않는다.

이번 준비 시작 용량은 미기록이며 소급 복원하지 않는다. 전 단계 삭제 차단도 남아 있다.
추가 공개 source는 metadata 검토만 완료했고 사건 청취 정답/훈련 승인으로 바꾸지 않는다.

### 최신 — 연구 Android에서 약국 누락의 한마디 회복, ARM gate로 진행

[실제 Android 결과](research/20261007-infant-presence-android-results.md): 기존 AST에 동결769parameter head를 붙인
연구 후보에서 같은약국원본의 겹친두10초 모두 general발성 한마디가 회복됐다. 공개33 새generic지원0,
기존phase승인13 observation/문장 exact보존·추가quiet/click/adult/music7조건 새지원0이다.
원527logits/threshold 유지·같은창RMS≥.003·shifted승인 후 보완·graph증가4,474B다. 일반정확도/욕구판정으로 보고하지 않는다.
실제 decoder(WAV변환)3조건·별도기존원본MP4 10/20초 AV·UI취소재개·저장/구간/기록 회귀도 검사했다.
약국 원MP4는 현재확보되지 않아 WAV회복을 원본영상회복으로 부르지 않는다.

**다음 실제 실행은 S25 ARM10/20초 속도·PSS·발열·native취소재개 확인**이다. 현재 ADB에는MoaQA35만 있다.
필요한 최신 연구APK/test 한세트를 `work/infant-presence-android-20261007/final/`에보존했다. 연구앱/테스트앱·AVD실행은종료했다.
중간app/build·hostclasses·controlledPCM 일괄삭제는자동검토가차단해남아있고우회하지않았다. 격리state는clear/부재확인했다.
후속 [평가source 조사](research/20261007-next-infant-head-validation.md)는5파일 후보/새exactID1·자연6–18개월검증0,
학습blocked/approvednull을유지하며 새자료와 blind사건검수를 준비한다. 사용자원음/특징 외부전송·학습0이다.

**제품beta에는 아직 미적용/미배포**다. code32를재업로드하지않는다. 2026-10-07 04:33KST Play게시개요는code32 검토중이었다.
ARM·독립review·서명/업데이트 gate후 유효변경만 새code beta로 반영하고 일반정확도나정식출시로 보고하지 않는다.
아래 연구Android 연결 예정·기기0은 선행 시점의 이력이다. 전체goal은active로계속한다.


**현재 다음 실행은 연구Android 후보 연결이다.** [추가source실측](research/20261007-extra-babble-evaluation-results.md)은
10개705KB·4/9weakcaption지원(웃음/dada포함)/grandpa반례지원0·나머지5보류·unknown age/naturalness를보존했다.
새source를학습/threshold선택에넣지않았다. source/domain 일반정확도 승인이아니다.
동결head를범용영아발성단서로연결하고기존YAMNet/AST 승인보존/기존label 그대로,
실제누락전체경로·10/20초중첩/AV·취소·메모리·ARM을검사한다. genericpresence를Babbling으로치환금지.
head/graph모두제품미탑재·code32유지·전체goalactive. ADB마지막readonly기기0이라휴대폰성능미측정.
아래 '다음extra평가'는선행graph시점의계획이며첫평가실행은완료됐다.

**다음 실제 실행:** [추가9clip/3source 평가 계약](research/20261007-extra-babble-evaluation-contract.md)에
따른평가전용취득·동결head비교. blocked를학습승인으로바꾸지않고 unknown age/naturalness·분모를보존한다.
새head의 [ONNX 연결](research/20261007-ast-infant-head-graph-results.md)은10조건/12span첫통과,
원527logits0차이/score≤6.77e-8/추가4,474B·model후보한개만보존했다.
합성20초중첩은Android/실제영아20초가아니다. 새source/반례후실제Android후보전체경로·ARM으로이어간다.
modelcandidate `work/infant-head-20261007/ast-head-graph01/ast-infant-presence-candidate.onnx`,
SHA `4a378476dcbe5f6b13c5f67295797d59376dab3627fe3dbd56bfeeadbd7f802a`.
기존originalasset유지·새beta없음/code32이후미배포/goal active. 아래 '다음ONNX연결'은 선행실측 당시 이력.

현재 첫 정보 이득은 [AST768 head 실측](research/20261007-ast-infant-head-results.md)이다.
약국 누락2구간 broad support를 처음 회복했지만 같은 원본 overlap이며 일반정확도/제품승인 아니다.
negative10/기존33대조 오인0·영아held2/4; 다른울음/짧은babble 미통과를 보존한다.
다음 실행은 동결769param head의 ONNX 연결/원527logits·새score parity, 별도source 평가다.
그 후 Android0–10/5–15/10–20초·기존엔진 보완/수용보존·ARM·취소·메모리·독립review로 진행한다.
연구의20초 비중첩2span을 product 중첩3창 검증으로 치환하지 않는다.
새blocked9개는 임의train승인 금지. 제품code32 유지/새배포없음, 전체goal active.
아래 인터페이스 미학습 설명은 이전 검사 당시 이력이다.

우선 다음실행은 [AST768 인터페이스 첫통과](research/20261007-ast-embedding-interface-results.md)를기반으로
**현재AST+작은768head의공개source분리학습·반례/기존누락비교**다. 새encoder다운로드없음.
원본527logits계약/실제길이·padding/시간span·고정source/학습선택·private평가전용을별도계약후실행한다.
현재head학습없음·Android미검증·새beta없음/code32이후미배포다. 첫6입력은품질시험아니다.
[새9옹알이source 감사](research/20261007-extra-babble-source-audit.md)는페이지/허락/주소만확인됐고
자연발성/월령/변조미검수·trainingblocked이므로원음검수없이승인라벨로바꾸지않는다.
이후신호가있는후보만오인/기존승인보존·모바일전체QA·독립리뷰·비공개업데이트한다.

최신[시간창MIL35](research/20261007-infant-mil-results.md)도첫누락미회복/새성인비명오인잔존으로미탑재다.
다음은[새representation·자료결손](research/20261007-next-infant-representation-evidence.md)의새dev옹알이source검수와
현재앱AST representation에tinyhead를붙일수있는지검토다. 계약·실제출처를고정한후처음비교한다.
대형LLM나만능교체로확대하지않는다. 기존제품/threshold유지·private평가전용·영아held재할당금지.
MILQA26unit/4CLI/3run 종료·이번state부재·전후용량 기록은인식개선입증으로보고하지않는다.

최신은 [binary35 실측](research/20261007-infant-presence-results.md)이다. 前23head/CV도최초누락을회복못했고
성인웃음오인이남았다. binary35는기존웃음오인을줄였지만영아validation4미검출·새비명오인이라미탑재다.
다음은고정공개35의시간창별weak multiple-instance 학습과별도영아source확장/작은representation 권리·입력계약조사다.
같은private평균head를반복하거나threshold를낮추지않는다. private자료는평가전용이다.
연구QA21unit/실제4CLI/worker0/이번stateclear와중복실패MP3 3.13MB정리는제품품질목표와구분한다.
0.5.18/code32이후새앱배포없음/전체goal active. 다음신호가있는후보만오인보존·Android·독립검토·beta에이어간다.
아래단계는선행실험이력으로본다.

가장 최신 실행은 [VTC 필터·quiet창 제외](research/20261007-vtc1-guarded-contract.md)와
[기존encoder의 작은head 인터페이스](research/20261007-infant-head-preview-interface.md)다.
두 누락의 아이 신호는 필터 후에도 유지됐고 모든5열 backend 동등성은36조건에서 통과했다.
rawgraph 무음 실패·피아노 오인·사용권·Android 미검증은 그대로다. 같은 최적화 수준 비교를 다시 돌리지 않는다.
[FSD source 감사](research/20261007-permissive-infant-voice-next.md)의 잠정 train58/보류27은
원음/사건/가족 정답을 검수한 데이터가 아니다. 다음 실행은 public subset의 합법적 취득과
라벨·혼합·원천 연결 확인, 기존encoder+작은head의 source 분리 비교다.
원음/feature는 온디바이스이며 사용자자료 학습 금지, 기존public33은 회귀용이다.
3publicpreview130,565B와 특징4,166B만 진행중 준비로 유지한다. 새encoder weight를 받지 않았다.
새guarded QA의3fixture3,065B 정리는 자동검토차단으로 남겼고 우회하지 않는다. 격리state는 clear됐다.
연구검증을 제품에 반영했다고 보고하지 말고 code32 이후 새 배포는 아직 없다고 명시한다.

0. [VTC1첫실측](research/20261007-vtc1-first-screen-results.md)에서누락원본의두겹치는구간에아이신호가남았다.
   기존후보를반복하지말고VTC의원문시간축/후처리·피아노오인·기존규칙결합·내부graph이식/모바일비용을계속검증한다.
   [weight권리](research/20261007-vtc1-distribution-rights.md)는미확정이고공식문의초안은미발송/승인대기다.
   새로운source/영아정답/TV/원래승인보존·실기기와권리를확보하기전제품에넣지않는다.
   source/pin/결과는보존하고진행중인4.53MBweight만유지한다. 연구QA완료를앱개선목표완료로닫지않는다.
   [ONNX후속](research/20261007-vtc1-graph-contract.md)은실제3구간의수치가일치했으나무음gate실패가남았다.
   활성4.54MBgraph는정규화진단용이다. 원래gate를완화하지않고kernel차이/기존무음guard와새오인을검토한다.
   원weight문의는승인대기/미발송이다. 상업권명확한작은taskhead/공개data대안조사도재개했다.

1. [PANNs Cnn10 실제실행](research/20261006-panns-cnn10-contract.md)도회복근거미확보로종료했다.
   AST2초/4초·PretrainedSED의 [완료한 실행](research/20261006-short-context-and-pretrainedsed-results.md)과함께같은3구간반복을하지않는다.
   다음미검증후보는영아발성task를학습한공개head/다른representation의정확weights·권리·추론계약부터확인한다.
2. 신호가 확인된 새 후보는 영아·성인·TV·재생 반례에서 사전 고정 판정을 비교한다. 점수 완화/사후 창 탐색으로 성공을 만들지 않는다.
   새 source/family 정답과 기존 회귀를 분리하며, 부모가 알려 준 실제 상황은 추론 입력으로 넣지 않고 분석 후 채점한다.
3. 비교 이득과 기존 수용 보존이 확인된 변경만 온디바이스 적용·Android 전체경로·취소/시간/메모리·두 축 리뷰·비공개beta로 이어간다.
   단순 문서·UX·오류 안내 변경을 인식률 향상으로 보고하거나 큰 LLM를 다시 받지 않는다.
4. 종료 모델/source/임시 빌드는 RUNBOOK에 따라 정리한다. 명명된 합성fixture10폴더의 재귀삭제는 정책검토차단으로 남아 있다.
   이 대상은 다른 도구로 우회하지 않는다. 개인 원본/제품assets/키/현재·직전final은 보존한다.
5. code32의 마지막 확인은 아래18:36 검토제출 요청이다. 연구단계에서 새AAB를 업로드하지 않고 같은code32를 재업로드하지 않는다.

# 다음 단계 — 0.5.18 소리·영상 시간 연결/비공개 검토 요청 이후

최신 [QA](qa/visual-alignment-0.5.18/README.md)/[계약](UX-VISUAL-ALIGNMENT-0.5.18.md)을 따른다.
code32는10월6일18:36 KST 검토 제출 요청/Google 빠른 검사 진행 상태다. 같은code32를 재업로드하지 않는다.
code31은18:13 게시/선택테스터제공을 직접 확인한 이전 버전이다. 공개프로덕션/상품은 보류한다.

1. 다음 상태 점검에서 code32의 Google 검토 결과와 실제 테스터 제공을 확인한다.
2. 새 영아·비아기·다중화자 영상에서 시간창별 근거·누락·오연결을 평가한다.
   합성 관찰 회복이나 기존 약국 회귀를 일반 시각/발성 정확도 이득으로 보고하지 않는다.
   사용자 원본·파생미디어·특징의 외부 전송은 추가하지 않는다.
3. 실제 휴대폰/ARM의10/20초 지연·메모리·발열·취소를 평가한다. 에뮬레이터 수치를 기종별 보증으로 쓰지 않는다.
4. 소리 인식 후보는 아래 EfficientSED 계약과 새원음/반례 기준을 따른다. 같은3구간 반복으로 채택 근거를 만들지 않는다.
5. 작은 한국어 표현 후보 Qwen3-0.6B INT4는 [텍스트 후보 조사](research/20261004-local-text-candidates.md)에 있다.
   아직 정확한 Android artifact 실행·한국어 사실 충실도는 미검증이고 원음/영상 인식 대안이 아니다.
   현재 제품은 YAMNet+AST+MediaPipe이며 Gemma/Qwen/EfficientSED는 미탑재다.
6. 배고픔·졸림·놀이에는 별도 행동/맥락 근거와 독립 평가가 필요하다. 얼굴 계수·울음 라벨만으로 임의 욕구를 출력하지 않는다.

아래는 이전 버전 이력이다.

# 이전 — 0.5.17 판정·표현 수정/비공개 검토 요청 이후

최신 [QA](qa/vocal-continuity-0.5.17/README.md)/[계약](UX-VOCAL-CONTINUITY-0.5.17.md)을 따른다.
code31은10월6일17:40 KST 검토 제출 요청/Google 빠른 검사 진행 상태다. 같은code31을 재업로드하지 않는다.
code30은11:08 게시/선택테스터제공을 확인한 이전 버전이다. 공개프로덕션/상품은 보류한다.

1. 다음 상태 점검에서 code31의 Google 검토 결과와 실제 테스터 제공을 확인한다.
2. 새 영아 원음/비아기 반례로 이득을 평가한다. 합성 관찰 회복이나 기존37조건 보존을 일반 인식률로 세지 않는다.
   새 실패는 버전·원음·구간별로 비교하며 원본/특징을 외부 전송하지 않는다.
3. EfficientSED는 아래 연구 계약을 따른다. 같은 누락3구간 반복/점수 완화로 제품 채택 근거를 만들지 않는다.
   현재 제품은 기존 YAMNet+AST다. 새 후보의 ARM/새 원음/반례 검증이 남아 있다.
4. 배고픔·졸림·놀이 표현은 별도 행동/맥락 근거와 독립 평가가 필요하다. 해시/울음 라벨로 임의 출력하지 않는다.
5. 첫 UI startup ANR의 환경 이력을 보존한다. 동일 APK 단독 UI 두 번과 최종 서명 UI는 통과했지만
   일반 휴대폰 성능/ANR 부재를 보장하지 않는다. 다음 실제 기기/새 원음 검증에서 비교한다.

아래는 이전 이력과 연구 후속이다.

# 다음 단계 — 2026-10-06 결과 동선 개선과 Android 이식 이후

후속: **code30은10월6일11:08 게시/선택테스터제공을 직접 확인했다.** 아래 검토제출중 표기는 당시 이력이다.
[새 보존 누락 원음 비교](research/20261006-efficientsed-retained-failure-screen.md)는 제품 채택을 뒷받침하지 못했다.
같은 원음3구간 추론을 반복하거나 관찰용0.5를 제품 임계값으로 쓰지 않는다.
인식 개선은 새 영아 사건/비아기 반례 및 [시간축 평가 계약](research/20261006-efficientsed-temporal-contract.md)부터 잇는다.

[0.5.16 QA](qa/result-navigation-0.5.16/README.md)의 최종 서명·업데이트·제출 상태를 먼저 확인한다.
결과 재진입 UX와 EfficientSED의 Android 전처리 수치 이식은 구현했다. 아래 과거의 이식 예정은 이력이다.

1. beta30은10월6일10:36 KST 검토제출요청완료/Google빠른검사중이다. 최종검토·테스터제공상태를확인한다.
   같은code30을중복업로드하지않는다. 공개프로덕션/상품은보류한다.
2. 새 인식 비교는 알려진 약국 외의 독립 영아 원음과 성인·TV·겹침 반례를 확보해 실시한다.
   사용자 미디어의 외부 전송·자동 수집은 추가하지 않는다. 같은 원음의 파생 창을 새 아이 표본으로 세지 않는다.
3. EfficientSED의 447라벨·strong/weak·시간격자·padding·10/20초창을 정하고 ARM에서 성능·취소를 검증한다.
   host/Android 수치 일치만으로 제품 모델을 바꾸거나 의도 정확도가 올랐다고 말하지 않는다.
4. 욕구 표현은 별도 행동/맥락 라벨과 반례가 필요하다. 문구 hash·울음 라벨로 배고픔/졸림을 임의 출력하지 않는다.
5. 실제 부모가 결과→다음 장면→무료 저장을 찾는지 관찰하고, 반복/인식거절/결제가림의 경쟁 리뷰 가설을 검증한다.
   [공개31리뷰](research/20261006-competitor-review-opportunities.md)의 노출 표본은 불만 빈도 통계가 아니다.
6. 리뷰의 resultStart/resultEnd 묶음 P3는 후속 상태 모델 정리 때 검토한다.
   Activity 파괴/프로세스 복원까지 보장하려면 부모 export·preview reader를 포함한 source lease 작업이 별도로 필요하다.
7. 자동승인검토가이번전용 `C:/MoaAVD/AgattokNavigation30.avd` 및 `.ini` 삭제를차단했다.
   앱제거/실행종료/격리state정리는완료했고폴더와등록파일의수동정리만남았다. 우회삭제하지않는다.

# 이전 — 2026-10-06 심화 조사 이후

[조사와 개발 결정](research/20261006-product-quality-and-growth-decision.md)을 따른다.
순위 목표를 인식·상황 표현·저장/재사용의 실제 품질 개선으로 연결한다.

1. **바로 이어갈 P0:** EfficientSED20.3MB의 Android 호환 전처리를 별도 연구 경로에 구현한다.
   AST와 다른 center/reflection·preemphasis·mel 범위·layout·정규화를 보존해 mel/logits를 원본과 대조한다.
2. 알려진 약국은 회귀용으로 유지하고, 새로운 영아/비아기·겹침·창경계 입력에서
   누락 회복과 새 오인을 함께 측정한다. 자료 부족이1번의 독립 이식 작업을 막지는 않는다.
3. 음식 요청/놀이 요청 등의 관찰 맥락은 별도 행동 라벨·반례·부모 문장 평가로 개발한다.
   대사 hash로 배고픔·졸림을 골라내거나 현재 얼굴 관찰을 욕구 모델로 부르지 않는다.
4. [리뷰 기반 실험](research/20261006-competitor-review-opportunities.md)의 첫 가치→무료 장면 저장→
   자발적 재사용→가격 이해 순서를 시험한다. 제안한 인원/목표는 탐색 계획이며 실행 결과가 아니다.
5. code29는 이전 검토 제출본이다. 실제 게시 확인 전 중복 업로드하지 않는다.
   새 인식 변경은 비교 이득·Android 통합·UltraQA·독립 코드검토 뒤 기존 승인 범위의 비공개 beta에 반영한다.

이번 조사에서 제품 코드/새 모델/배포는 변경하지 않았다. 아래는 code29의 기존 검증·후속 기준이다.

# 다음 단계 — 0.5.15 표현 개선 후 인식 후보의 실제 모바일 검증

[0.5.15 QA](qa/expression-0.5.15/README.md)에 코드·검증·제출 상태를 기록했다.
code29 검토 제출 완료,Google 빠른 검사 진행 중이다. 이전 code28은10월5일16:22에 게시됐다.

1. code29 검토 결과와 실제 테스터 제공 상태를 확인한다. 같은 AAB를 중복 제출하지 않는다.
2. 사용자 최신 실패 입력이 오면 버전/원음/구간별로 기존 YAMNet+AST와 새 후보를 비교한다.
   알려진 약국이나 같은 원음의 여러 창을 새 정확도 표본으로 세지 않는다.
3. [EfficientSED export](research/20261005-efficientsed-mobile-export.md)의20.3MB float ONNX에
   동일한 Android mel 전처리를 연결하고,mel/출력 수치·10/20초 ARM 성능·취소·새 오인을 검증한다.
   현재는 합성 CPU 그래프 이식만 통과했다. 새 모델을 이번 code29에 넣지 않았다.
4. [욕구 표현 근거](research/20261005-infant-needs-expression-evidence.md)에 따라 배고픔/졸림/놀이
   라벨·행동 맥락·독립 아이 평가를 확보한다. 울음이나 옹알이 라벨만으로 욕구를 임의 출력하지 않는다.
5. 잔여 `C:/MoaAVD/AgattokExpression29.avd`는 자동 승인 검토가 삭제를 차단했다.
   실행/등록은 종료·제거돼 있고 수동 정리가 남았다. 동일 삭제를 우회 재시도하지 않는다.

표현의 폭·관찰 순서 개선과 새 인식률/욕구 정확도를 혼동하지 않는다. 아래는 이전 단계 이력이다.

# 다음 단계 — 0.5.14-beta/code28와 새 실패 입력

추가 조사 요청에 따라 [EfficientSED3개 실제 비교](research/20261005-temporal-model-followup.md)와
[영아전문 후속 조사](research/20261005-infant-specialist-followup.md)를 완료했다.
새 비교1순위는20.3MB fmn10+TF advanced-KD다. 알려진약국에서옹알이시간단서가있지만
새실패회복/제품승인/Android검증은아니다. 새피드백이오면현재YAMNet+AST와같은원음으로대조하고,
모바일export/양자화parity와실기기성능을확인한다. 이번조사로제품code28을변경하거나재배포하지않았다.

최신 [0.5.14 QA](qa/audio-window-0.5.14/README.md)의 실제 37조건 비교와 Android53개,
서명 업데이트·기록/영상 보존·짧은 실패 안내 검증을 완료했다. 재현된 짧은 울음 누락2조건이 회복됐고
기존33조건·약국10/20초는 유지됐다. 일반 옹알이 정확도 개선으로 확대하지 않는다.

1. QA의 최신 Play 제출 상태를 확인한다. 이미 올린 code28을 중복 업로드하지 않는다.
   code27은 10월5일04:05에 실제 게시됐다. code28의 검토 제출/게시/테스터 설치를 각각 구분한다.
2. 최신 실패가 약국 원본인지 새 촬영인지 질문을 한 번 보냈으며 답변 대기다. 같은 질문을 반복하지 않는다.
   답변·원본·구간이 들어오면 설치 버전과 같은 입력의 실제 이전/현재 결과로 다음 결함을 진단한다.
3. 같은 울음 원음의 파생 조건과 기존 약국을 독립 정확도 표본으로 다시 세지 않는다.
   추가 개선은 새 실패 재현과 비아기 오인 대조를 함께 통과시킨다.
4. Play의 기존 Fragment1.1.0/edge-to-edge 권고는 유지보수 항목이다. 이번 인식 수정에서는 SDK를 바꾸지 않았다.
   라이브러리 업데이트를 진행할 때 실제 runtime 의존성과 MediaPipe/화면 회귀를 함께 확인한다.

큰 생성 모델/새 데이터 업로드로 이 인식 수정의 범위를 넓히지 않는다. 기존 온디바이스·비공개 beta 개방,
정식 release 이용권 정책을 유지한다. 아래는 이전 계획과 당시 기록이다.

# 이전 — 0.5.13-beta/code27 검토 결과와 발성 누락 개선

[0.5.13 QA](qa/audio-resampling-0.5.13/README.md)의 Android48개와 연구 host27개를 통과했다.
고주파 접힘·EOS·유한성 검증을 수정하고 기존 약국 옹알이 회복을 보존했다.
최종 서명 beta/release 검사, code26→27 기록/MP4 보존, 실제 UI7시나리오와 최종 약국 UI를 통과했다.
2026-10-05 03:27 KST code27을 비공개 Alpha에 검토 제출했다. 03:41 KST 자동 빠른 검사 종료와
`변경사항을 검토 중입니다`를 확인했다. Google 최종 승인·테스터 제공은 미확인이다.
관리형 게시off로 승인 후 자동 게시된다.
이미 제출한 버전을 재업로드하지 않고 현재 Console 상태를 확인한다.
[모델 비교](research/20261005-noise-model-comparison.md)의 EfficientAT/GTCRN은 실행 완료·채택 보류다.
같은 소리의 임계값을 반복 완화하거나 현재 반례가 있는 후보를 배포하지 않는다.
Gemma visual budget 실행기는 구현됐지만 실제 S25가 미연결이므로 새 속도/발열/의미 비교는 남았다.

## 인식 품질을 위한 후속 우선순위

1. 이번 code27의 Google 검토 결과와 테스터 제공 상태를 확인해 QA/HANDOFF를 갱신한다.
2. 다음 누락/오인 사례는 설치 버전·선택한 구간·원본별로 구분한다. 알려진 약국은 회귀 사례로 유지하고
   새로운 독립 정확도 표본으로 다시 세지 않는다.
3. [창 경계 진단](research/20261005-yamnet-window-phase-diagnosis.md)에서 Android 기존4조건과 같은
   현재 모델의 점수를 재현했고, 새32조건으로 위치의 phase 의존성을 확인했다. 다음은 추가 창이 같은
   발성을 중복 집계하지 않도록 후보를 구현·대조하는 단계다. 볼륨/임계값은 유지하고,
   웃음/울음 혼동·성인/TV 새 오인과 기존 AST 회복을 함께 검사한다. 이 진단은 제품에 아직 미반영이다.
4. 후보가 기존 발성을 지우는 반례가 나오면 기본 필터로 넣지 않는다. EfficientAT/GTCRN의 완료된 실행을 반복하지 않는다.
5. S25 재연결 답변이 오면 Gemma의 원음·장수·시간축을 유지한 visual budget 비교를 별도 실행한다.
   미연결 질문을 반복하지 않으며, 이 실험 때문에 독립적인 제품 인식 개선을 멈추지 않는다.

새 제품 변경은 실패 재현 → 수정 → 같은 입력 전후/음성 손실·오인 대조 → Android통합/취소·저장/업데이트
검증 → 독립 리뷰 → 새 code의 비공개 제출 순서로 진행한다. 공개 출시·유료 상품·원본 외부 전송은 포함하지 않는다.

이하 beta26 완료 및 이전 계획은 당시 기록이다. 최신 상태는 HANDOFF와0.5.13 QA를 우선한다.

# 이전 제공 기준 — 0.5.12-beta/code26 비공개 테스트 제공 완료

Play 테스트 전체 기능 개방 유지. 최신 인식 수정·배포 검증은 [0.5.12 QA](qa/audio-presence-0.5.12/README.md), 기본 출처/저장 계약은 [0.5.9 QA](qa/sound-visual-0.5.9/README.md)를 따른다. 정식 정책은 무료60초/10초, 전체120초/20초다.

## 현재 이어갈 단계

**최신 사용자 지시(2026-10-05): 자료 조사·개선·UltraQA·code-review 후 비공개 업데이트 배포까지 승인.**
[0.5.12 QA](qa/audio-presence-0.5.12/README.md)의 실제 무음 희석 결함을 수정하고, 계측38개·공개원음대조·
최종 서명패키지·code23→26 기록/영상 보존·약국0–10초 UI를 검증했다. 조사 결과는
[적용 결정](research/20261005-reference-review-decision.md)을 따른다. 같은 완료된 실험을 반복하지 않는다.
Google Play 비공개Alpha의code26이 **2026-10-05 01:53 KST에 게시**됐다. 02:03 KST 실제 트랙에서
`최신 출시 버전: 0.5.12-beta(26)`과`선택한 테스터에게 제공됩니다`를 확인했다.
이 누락 개선·QA·비공개 배포 작업은 완료됐다. 이전 승인 대기 때문에 다시 제출하거나 같은 검사를 반복하지 않는다.
새 테스터 제보가 있으면 설치 버전·선택 구간과 원본 보존 조건을 확인해 별도 사례로 조사한다.
Gemma/새 문장 생성은 미탑재이며 이번 누락 개선의 완료 조건으로 확대하지 않는다.
일반 영아 인식률, 성인/TV 분리, 욕구 원인 해석은 별도 검증이 필요하다.

다음 모델 비교는 EfficientAT mn04·GTCRN의 **같은 독립 원음 전후 누락/새 오인/발성 보존**부터 설계한다.
문헌상 점수나 모델 파일 존재를 제품 품질로 대신하지 않는다. 공급권리·Android실측·holdout을 먼저 확인한다.

후속 [Gemma 경량화·특화 검토](research/20261005-gemma-specialization-feasibility.md)는 문헌·구현 조사다.
Android 시각 토큰 예산 조절은 새로 확인한 비용 최적화 후보이며, 의미 오류 해결이나 제품 탑재로
보고하지 않는다. PLE/encoder 제거·LoRA·pruning/증류는 서로 다른 작업이고 기존 AV 미통과 판단을 해소하지 않는다.

아래는 이전 단계 기록이다. 선배포 미확정·Play미업로드·기술문의 승인대기 표현은 당시 상태이며
최신 사용자 승인과0.5.12 QA의 실제 배포 기록이 우선한다.

AST beta25의 로컬 패키징·경고 보완·최종 서명 UI와 실제23→25 업데이트 검사를 완료했다.
[최종 QA](qa/ondevice-av-generation/AST-BETA-PACKAGE-QA.md)의 `ast-beta-package-03` 사본만 사용한다.
같은 연구33입력·S25 단독 측정·끝난 코드 검사를 반복하지 않는다.
Gemma 없는 부분 개선본을 먼저 비공개 테스트로 배포할지 범위를 확정한다. 아직 Play 업로드는 없다.
선배포하더라도 AV 공동 이해·새 문장/감정·욕구 생성은 미완료로 유지한다. 공개 출시·상품·실거래 승인은 확대하지 않는다.
배포 진행 시 실제 Console의 현재 versionCode와 서명/업로드/심사 상태부터 확인한다.

2026-10-05 후속: [Gemma 기술문의 자료](research/20261004-gemma-support-handoff.md)의
합성 입력·exact response·독립 프로젝트 ZIP과 초안을 준비하고 입력 보존/빌드를 검증했다.
사용자 승인으로 [공식 이슈 #3851](https://github.com/google-ai-edge/LiteRT-LM/issues/3851)에 게시했다.
직접 ZIP과 공개 Gist를 연결했고, 로그인 없는 다운로드의 원본 SHA 일치를 확인했다. 아직 답변은 없다.
후속 추론은 적용 가능한 처리 차이·수정 등 새 근거가 생길 때만 검토한다. 지원 문의 준비를 품질 완료로 세지 않는다.

아래는 이전 단계의 판단과 실행 기록이다.

## 최우선 — 최대20초 온디바이스 생성 구조

**현재 이어갈 한 단계:** 명시적 제품용 AST beta 후보를 구현하고 새 서명 APK/AAB·업데이트 회귀를 검증한다.
[S25 AST QA](qa/ondevice-av-generation/S25-AST-20261004.md)의 재연결 실행에서 남은 실제 UI와
합성10/20초 `visual=true` 전체45초 예산 검사가2통과했다. 3.780/8.180초·thermal0/0, 후보정리·제품설치동일이다.
AST 단독10/20/반복20초3.122/6.686/5.213초와 native취소/복구, 실제 경로3개는 이미 측정했다. 이를 반복하지 않는다.
첫 UI 실패는 keyguard/SLEEP에서 분석0회인 설정 실패였고 첫 전체AV시도는USB부재로설치전에종료됐다. 재연결로해소됐다.
추가 성능 하네스는 에뮬레이터에서10/20초7.495/11.149초로 통과했다. ARM이나 의미 정확도 근거가 아니다.
기존 [통합 후보 QA](qa/ondevice-av-generation/AST-INTEGRATED-CANDIDATE-QA.md)의 약국 회복·회귀86개와
33입력 전후 보존을 근거로 제한적 AST beta 구현 진행이 타당하다는 Astra 판단을 받았다.
연구debug 차단을 유지하면서 제품용 beta 플래그/모델/runtime을 명시적으로 분리한다.
최종 서명 APK/AAB의 용량·ABI·정렬·고지·버전과 실제 업데이트 기록/미디어/무료내보내기/beta접근을 새로 검증한다.
기본beta/release에는 여전히AST가 없다. 기존 사용자 앱은 설치/중지/삭제하지 않고 점검은 별도패키지·합성입력에 한정한다.
Gemma 없는 선배포 범위는 미확정이다. 전체 AV 이해·새 아기말/감정·욕구 생성 목표와 Play 배포는 미완료로 유지한다.

앞서 [실제 Android 33입력 비교](qa/ondevice-av-generation/AST-PUBLIC-PAIRED-QA.md)를 완료했다.
기존 승인12개/보류21개 유지, 추가승인0·손실0이다. 알려진 약국 회복은 별도 유지하며 일반 정확도로 확대하지 않는다.
기존 AST-only11개의 전후 미확인은 이번 실제 pair로 보완됐다. 같은33개를 반복하거나 임계값을 조정하지 않는다.
이후 **고정 모델·런타임을 포함하는 명시적 통합 후보 빌드와 실제 DeviceClipAnalyzer 호출 경로 준비**를 위 QA로 완료했다.
현재 기본 `src/noAst`와 연구 beta/release 차단 때문에 ENABLED만 true로 바꾸는 것으로는 동작하지 않는다.
후보에서20초 오프라인·UI취소/복구·기록미디어보존을 확인했으며, ARM실측 후 제한적 채택을 결정한다.
TV의 영아 재생음과 독립 일반화 품질은 이번 공개대조로 입증하지 못했다.

[독립 점검 APK](qa/ondevice-av-generation/AST-DEVICE-PROBE-QA.md)의 고정 사본은 이후 S25 실측까지 완료했다.
현재 연결 부재는 추가 UI/전체 AV 검사에만 해당한다. ADB/Gradle은 부모 한 명만 사용한다.
같은 연결 질문이나 완료된 단독 측정을 반복하지 않는다. 고정 사본·고지 보완본의 구분은 S25 QA를 따른다.

후속 완료: [독립 사건/화자 평가 계약](research/20261004-av-evaluation-contract.md)과
[공개 AV corpus 조사](research/20261004-labeled-av-data.md)를 작성했다. 공개 설명까지 확인했으며 정답셋/원본은 미확보다.
기존 QA의15초 제한은 [20초 평가 도구 검증](qa/ondevice-av-generation/PUBLIC-AUDIO-20S-QA.md)으로 수정했다.
host46/Android5/실제20초 전체 구간·실패입력4종/기존44파일보존을 확인했으며 제품·배포 변경은 없다.
다음 인식 판단은 독립 사건·발성주체 자료와 실행 가능한 수정안의 누락 회복/새 오인 비교다.
평가 도구 통과만으로 현재 실패를 해결했다고 하거나 같은 원본의 임계값을 반복 조정하지 않는다.

2026-10-04 S25 실측 추가: [연구 APK QA](qa/ondevice-av-generation/S25-GEMMA-20261004.md).
native GPU 실행은 가능하지만20초 전체43.29초·thermal3·취소6.63초 지연·음성/영상 의미 실패가 남아 탑재 미승인이다.
시험 후 ADB 연결이 끊겼으나 사용자 디버깅 재활성화 후 연구 패키지/모델 정리를 완료하고 제품 버전 불변을 확인했다.
같은 프로세스/제품 UI 검증·32비트 지원·독립 관찰 품질을 실제로 통과하기 전 Gemma를 배포하지 않는다.

후속 원천 조사: [Gemma 파일/SDK 결함](research/20261004-gemma-runtime-triage.md), [대안 모델](research/20261004-compact-av-candidates.md).
현재 실패에 맞는 upstream 수정이나 바로 이식할 대안을 확보하지 못했다. 조사 자체를 새 인식 개선으로 세지 않는다.
같은 실패 prompt/sampler를 반복해 우연한 성공을 찾거나 확인하지 못한 모델을 완성 경로로 약속하지 않는다.

추가 [보관 PCM 비교](research/20261004-retained-pcm-ast-check.md): 중복 원본을 제외한 보류 2구간은 고정 AST/v2로도 회복하지 못했다.
현재 Android 필터 타임라인·독립 정답은 확보되지 않았으며, 이 결과를 근거로 임계값을 낮추거나 후보를 활성화하지 않는다.
새 영상이나 라벨이 더 생기는 것만으로 현재 후보의 품질·Gemma 발열이 해결되지는 않는다. 다음 모델 변경에는 구체적 새 근거가 필요하다.
사용자에게 실측 실패를 알리고 검증된 **Gemma 미포함 code24 영상 개선본만 먼저 베타 업데이트할지** 질문했다.
답변 전에는 기존 beta23을 유지한다. 선배포하더라도 소리·영상 생성의 전체 목표는 미완료로 남는다.

**새 근거가 있는 다음 음성 후보:** [SpeechMaturity artifact](research/20261004-speech-maturity-artifacts.md)의
학습된5-way head와 복수 encoder가 실제 공개돼 있다. [실행 구조 검토](research/20261004-speech-maturity-runtime-review.md)를 따라
고정된 최소 3개 checkpoint의 크기/SHA·제한 로딩·키/shape/유한성 검사를 마쳤다. [실행 증거](research/20261004-speech-maturity-checkpoint-audit.md).
후속 [cfg확보/431키 복원/합성 검증](research/20261004-speech-maturity-reconstruction.md)과
[실제 프로세스 트리 실행기](qa/ondevice-av-generation/BOUNDED-PROCESS-QA.md)를 완료했다.
**18구간425창 원음 비교는 실행됐지만 현재 경로의 제품 채택은 보류한다.** 합성 tone을 발성으로 분류했고
공개 웃음/옹알이에도 혼동이 남았다. [고정 비교 결과·한계](research/20261004-speech-maturity-comparison.md).

[공식 계산 본문 대조](research/20261004-speech-maturity-native-reference.md)는 완료했다. Fairseq/HF/저자 계산 본문을
현재 torch에서 실행한 제한 참조와2입력×8tensor가 사전 허용오차를 통과했고 tone 오분류도 양쪽에서 재현됐다.
저자 전체 환경·18구간 전체 parity는 아니지만 이 반례를 단순 이식 오류로 설명할 근거는 약해졌다.
결과를 보고 임계값·창 길이·입력 gain을 바꾸는 재실험은 하지 않는다. 기존 환경 downgrade도 없다.
TalkNet [후보 조사](research/20261004-active-speaker-candidate.md)와
[소스 검토](research/20261004-talknet-runtime-review.md)를 거쳐487키 strict 복원·1초 합성 feature 추론·입력오류10개 거부를 통과했다.
이어 [실제 AV 비교](research/20261004-talknet-av-comparison.md)까지완료했다. 고정MFCC/112crop/25fps로
원음·무음·±500ms 네조건을시험했으나지연조건이원정렬보다높아제품채택근거는얻지못했다.
**다음은 점수와독립적인 사건·발성주체 라벨 평가 묶음**이다. 아기발성, 보호자만발성,
무음입움직임/무음을구분하며불명확하면unknown으로남긴다. 이미본원본은known case로분리한다.
새사건을포함한고정pipeline1회평가로누락회복과보호자음성오귀속을함께확인한다.
현재임계값/offset/window를이번점수에맞춰반복하지않는다. PC얼굴전처리에약68초가걸렸으며
Android 성능·감정/욕구 생성 검증은별도다. 자손 전체를 제한하는 Windows Job 실행기를 유지한다.
기존 launcher 메모리 값을 실제 torch RAM으로 해석하지 않는다. 두 실행의 raw Job committed 값도 휴대폰 RAM이 아니다.
새 후보는 소리 분류 연구이며 Gemma의 의미/발열·영상 문장 생성 목표는 여전히 미완료다. 상용 배포 조건·Android 실행도 미확정이다.

최신 상태(2026-10-04): [Android AST QA](qa/ondevice-av-generation/ANDROID-AST-QA.md)의 전체322통과/제외2 이후 v2·빌드 분리의 기본116개와 연구27개, Gradle6게이트를 통과했다. [코드 검토](qa/ondevice-av-generation/REVIEW-AST-20261004.md) P2두건도 수정·검증 완료다. 에뮬레이터 오프라인 원본 평가는 회복했지만 독립 일반화·생성 품질은 미통과이므로 후보는 기본 비활성이다. 임시 입력·모델 캐시를 제거하고 환경을 복원했다. 새 비공개 테스트 업로드는 없으며 영상 개선부터 배포할지 범위 확인 답변을 기다린다. 답변 후 확정 범위의 최종 서명 beta/code24이상·업데이트 보존·Play 게시 확인을 진행한다.

후속 준비: 현재 소스의 **0.5.10/code24 서명 beta APK/AAB와 release 비교본**을 로컬에서 만들고 실제23→24의 미디어/기록 보존·서명 UI7시나리오·정식 재잠금까지 확인했다. [서명 후보 QA](qa/ondevice-av-generation/SIGNED-CANDIDATE-QA.md). 후보는 `work/ondevice-av/signed-candidate24/`에 있으며 Play 미제출이다. 배포 범위가 그대로 확정되고 소스가 바뀌지 않으면 이 후보의 SHA를 사용한다. 인식/생성 구현이 추가되면 별도 새 검증과 versionCode 결정을 한다. 연구 빌드는 제출하지 않는다.

2026-10-03 사용자는 미리 작성한 문장 선택을 넘어서 **소리·영상 흐름의 공동 해석과 새 아기말 생성**을 요구했고, **외부 전송 없는 온디바이스를 필수**로 확정했다. [설계](superpowers/specs/2026-10-03-ondevice-av-generation-design.md)에 따라 고정 Gemma4 후보를 실제 내려받아 로컬 소리+시간순 이미지 추론을 실행했다. 초기 의미 품질은 미통과이며 이후 Astra/high가 동일 원음 누락 재현, AST 음성 비교, 전체 구간 관찰 실험·Android 시간순 디코딩 개선을 진행한다. [최신 실측](research/20261003-ondevice-av-probe.md) / [실행 계획](superpowers/plans/2026-10-04-perception-recovery.md) / [QA 행렬](qa/ondevice-av-generation/PERCEPTION-QA.md). 후보 개선→Android/ARM 검증→통합·회귀→검증된 베타 패키징 순서이며, 현재0.5.9 배포 상태를 새 모델 적용 완료로 표현하지 않는다.

## 0. 바로 다음 — 베타 실사용과 모집

1. beta22 게시 완료를 확인했다. beta23 업데이트의 제출/게시 상태는 [Play 상태](release/20261003/PLAY-STATUS.md)를 따른다. 테스트 기간 Play 설치본도 구매 없이 전체 기능을 사용한다.
2. Google 그룹의 공개 가입과 Play 연결은 적용됐다. [초대 안내](release/20261002/TESTER-INVITE.md)의 그룹 가입 → 같은 계정 Play 참여 → Play 설치 순서로 모집한다. 이메일을 매번 수동 등록하지 않는다.
3. 실제 최소12명·연속14일의 참여와 피드백을 확인한다. 그룹 회원 수·직접 설치 APK·링크 생성으로 이 조건을 대신하지 않는다. 이후 프로덕션 액세스 신청·심사는 별도다.
4. ARM 폰에서 현재120초/20초 안내·실제 촬영/원음·구간 선택·저장/재생을 확인한다. 원음이 작은지 분석 보류인지 구분한 뒤 수음 개선을 설계한다.
5. 테스트 종료 후 code24 이상 정식 업데이트의 실제 Play 설치/서명 경로·재잠금·기존 기록 보존을 확인한다. 베타 자동 만료/강제 업데이트를 약속하지 않는다.
6. 상품 등록·실거래·공개 프로덕션은 보류한다. 수익화 전에 지급 계정 주의 알림의 원인·실제 구매/복원/환불을 검증한다.

## 1. 실제 장면 적합성 — 이어서 검증할 품질 과제

- **2026-10-02 실제 제공 영상 진단:** [원인·비교 실험·개선안](research/20261002-private-video-diagnosis.md). 2개 실패 영상의 보류를 재현했다. 산술평균 downmix를 맞춘 5개 기준 구간 + 25개 증폭/시작점 변형에서도 회복되지 않았다. 실제 Android MP4 디코딩에서도 재현됐으며 단순 gain/임계값 완화를 해결책으로 보고하지 않는다. 0.5.9에서 `예상한 아기말`·조건부 시각 표현·근거 표시·보류 안내를 구현했다. [모델 비교](research/20261002-model-059-comparison.md)의 float 후보도 제공실패4구간을 회복하지 못해 현재 모델을 유지했다. 인식 개선은 아직 다음 과제다.
- [최신 연속 사용 피드백과 문장 안내 제안](research/20261002-parent-feedback-phrases.md): 비슷한 울음 대사가 반복되며 일관성도 느껴진다는 관찰. 유한 후보 풀/의미 집중을 개선하되 정확도 증거로 오해하지 않는다. 재미·장면 적합성·영상 보관 중심의 안내를 검토한다.
- 사용자가 진단용 원본 3개를 제공했다. 로컬에서만 처리하며 원본·파생 미디어·개별 기록은 Git 제외다. 독립 라벨을 확보한 아기 클립으로 울음·웃음·옹알이·높은 발성·혼합을 비교한다. 제공된 3개를 독립적인 전체 정확도 평가 자료로 쓰지 않는다. 녹음 업로드 기능을 추가하지 않는다.
- 같은 구간 반복은 같은 의미/문구, 다른 사건은 다른 근거가 나와야 한다. 성인·TV·음악·무음 음성에서도 실제 모델을 시험한다.
- 보호자 최소3명/12클립의 초기 평가를 제안한다. 장면 적합성·아기 말투·저장/공유 의향·의도 확정으로 오해하는지 확인한다. 수치는 성공 결과가 아닌 평가안이다.
- [문장 계약과 승인안](research/20261001-competitors-and-voice.md)의 울음→놀이, 비아기→한마디 모순을 출시 차단 사례로 본다. 전부 보류하는 제품도 통과하지 못한다.
- 현재 유료 시각 관찰은 얼굴/입 관찰과 제한된 응답 요청 문구 선택에 쓰인다. 얼굴 주인·립싱크·욕구는 확인하지 않는다. 그 기능과 PDF에 지불할 이유가 있는지 확인하기 전 유료 가치 검증 완료로 보고하지 않는다.

0.5.3에서 기존 웃음 보류를 실제로 개선했다.11개 공개 source와11개 합성 교란을 비교했으며 일부 옹알이·작은 웃음은 여전히 보류한다. 새 Child speech3창 경로의 TV/큰 어린이/고음 성인 대조군, 실제 부모의 블라인드 장면 적합성 평가를 우선한다. source 제목만으로 정답을 만들지 않는다. 필터는 DC/저주파 완화이며 TV·대화 분리가 아니다.

## 2. 모델 개선

- 검증 가능한 가정·아기·세션 분리 자료를 먼저 설계한다. 임의 caregiver 추정을 원인 정답으로 쓰지 않는다.
- YAMNet 임베딩+소형 head, PANNs/SSAST 비교, voc2vec 교사 모델 순서. 공개 모델을 가져왔다는 이유로 정확도를 주장하지 않는다.
- 라이선스·동의·중복 검사·class별precision/recall/macro-F1·보류율·기기별 지연을 보고한다. [근거와 평가 계획](research/20261001-cry-evidence.md).

## 3. 운영/출시 — 아직 남음

- 현재 통합 소스/전달본은 beta23다. 정식 Play 전환은code24 이상과 실제 서명/설치 경로 확인이 필요하며 같은code23 release 수동 교체는 자동 업데이트 검증이 아니다.
- ARM 폰의 카메라/회전/음질/후반 구간/백그라운드/큰 글씨/TalkBack/분석 지연, Android26–28 앨범 권한·파일 제공자를 실제 환경에서 확인한다.
- 기존 갤러리 정책revision `jigeum-verifier-00005-ph7`과4개 URL의HTTP200/본문·Node56통과는 보존한다. 이번 UI 수정은 저장·시간·결제 정책을 바꾸지 않았다. Chrome 정책 렌더링은 별도 미검증이다.
- 실제 그룹 경로의 참여/Play 설치·최소12명/연속14일을 확인한 뒤 프로덕션 액세스 신청은 별도 승인 범위로 진행한다.
- 상품/실거래 보류 해제 후 지급 계정 알림 원인·Play 구매·PENDING·ACK·복원·환불·계정 변경을 검증한다.

## 지속 계약

기존 기록/백업/서명 키/이용권 검증 유지. `hasCurrentEntitlement()`를 실제 실행 전/중/후 검사. 테스트 대역은 androidTest. Gradle/ADB 한 담당자. 커밋/푸시 후 로컬·원격SHA와 작업 트리 확인. 원본·토큰·키·빌드캐시는 커밋하지 않는다.
# 현재 다음 단계 — frozen pair의 실제 Android 확인

2026-10-07. [실측 결과](research/20261007-caption-wholebag-results.md)와
[연구 계약](research/20261007-caption-android-contract.md)을 먼저 읽는다.
단독 새 head는 약국 회귀로 제외했다. 현재 비교 대상은 기존 graph/원527출력/기존head를 보존하고
기존768vector에3076B frozen supplemental head만 계산하는 debug 연구다.
추가 공개9caption지원4/9→7/9는 일반 정확도나 실제 앱 한마디의 검증이 아니다.

실제 앱 decoder/20초 중첩/native취소/기존약국·기존승인·성인/음악/무음 반례와 새입력부터 확인한다.
ARM은 현재 미연결이며 연구APK/test와 동작 변경의 새 proof가 필요하다. 기존APK proof를 재사용하지 않는다.
통과 후만 새versionCode>32 beta 패키지/서명업데이트/독립검토/실제 비공개 제출로 진행한다.
제품0.5.18-beta/code32·새Play제출0, 전체goal active. 아래는 이전 실험 당시의 다음 단계다.

