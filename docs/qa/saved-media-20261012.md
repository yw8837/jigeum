# UltraQA Report — 갤러리 영상 저장과 분석 독립성

상태: **실폰 두 저장 경로 성공·새38 패키지/업데이트보존 통과, signed20 timeout으로 배포 보류**.
`ULTRAQA STOPPED: Max cycles` (5cycle). 기준6499256. [계약](../research/20261012-saved-media-contract.md).
소유MoaQA35/emulator-5556 및 사용자가 연결 승인한S25, synthetic media, 최대5cycle/같은실패3회 중단.
초기2cycle 뒤 USB 승인으로 실폰3cycle를 실행했다.
후속 사용자 확인으로 PNG는 정상이고 영상카드/장면과 함께 저장만 실패하는 것으로 정정했다.
마지막 Play 제공 확인은code37이며, 기존 서명code38 파일에는 이번 변경이 없다.

## 목표와 범위

사용자는 별도 연구 앱에서 이전에 실패했던 같은 갤러리 영상을 분석한 뒤
‘영상 카드 앨범에 저장’과 ‘장면과 함께 저장하기’를 모두 성공했다고 답했다.
이것은 사용자 직접 확인이며, 원본을 에이전트가 읽거나 원본의 HDR/codec을 확인한 결과는 아니다.
이하 사용자 재확인 대기 문구는 이 응답 전 이력이다. 연구 앱의 사용자 생성 기록/미디어는 보존한다.

실제 갤러리 content URI를 받는 앱 저장 콜백, Transformer와 MediaStore/기록 저장을 실행한다.
결과가 없어도 선택 구간을 원래 소리와 함께 자막 없이 저장하고, 원본·기존 기록을 보존한다.
연속 입력의 이전 결과·초안 오염과 관측된 시간 흐름의 표현을 검사한다.
분석 대역을 쓰는 UI 검사는 인식 모델 정확도를 측정하지 않는다. 아기 자료 외부 전송은 없다.

| ID | 의도 / 사용자 | 설정·명령 | 기대 신호 | 실제 / 수정 | 근거 | 정리 |
|---|---|---|---|---|---|---|
| S1 | 갤러리 영상 번역 후 PNG·MP4·기록 저장 | SavedMediaFlowTest, 실제 app callbacks/content URI | 출력 readable/published, 입력 보존 | SDR 세 저장 경로 통과 | final01 / android-tests | 소유 rows·DB 제거 |
| S2 | 분석 보류·실패 사용자 영상만 보관 | 동일 UI harness + TranslatedMediaTests | 선택구간/오디오 보존, 자막·가짜기록 없음 | 무료 정책·분석 실패·8.123–11.123초·무음 입력 통과 | 동일 | 동일 |
| S3 | 손상·없는 파일·발행 실패 | TranslatedMediaTests/GalleryMediaTests | 부분파일·가짜 성공 없음, 원본 보존 | 손상/취소/실패 롤백/재시작 회복 통과. 실제 외부 provider 권한회수는 미실행 | 동일 | 렌더 파일0·pending journal 비어 있음 |
| S4 | 연속 입력·오래된 결과 | 실제 OpenDocument 콜백에 두 번째 URI 전달, CaptureAccessLimitsTest | 이전 결과/초안 오염 없음 | 새 문장·URI 순서·초안 제거와 기존 권한 경계 통과 | 동일 | 소유 작업취소 |
| S5 | 표현 근거/승인 정책 보존 | host5개 suite, ExpressionVarietyTest | quiet 근거 있을 때만 표현 변화 | host44통과; 기준소스10개 중 새 표현1개 기대실패. 실제 모델 재추론 아님 | host01 / host-baseline | 작은 로그 보존 |
| S6 | 중복탭/오류 출력 | 같은 버튼 action 연속2회, 취소·손상 입력 | 하나만 저장, 오류 후 정리 | 단일 출력·취소 정리 통과; 새120초 timeout 강제실행은 안 함 | final01 | 소유 실행 종료 |
| S7 | HDR 자막 호환성 | 실제 GL shader + 10bit PQ HEVC fixture | 기존 실패 재현, SDR 합성 및 전체 저장 성공 | 기존 gainmap 예외 재현·SDR draw·Composition 모드 통과. 전체 codec는 에뮬레이터 실패, S25 동일APK/fixture 성공 | final01 / phone01 | 사용자 원본 재확인 필요 |

## 명령과 결과

실행 기준 폴더는 이 작업트리다. [재현 드라이버/빌드 로그](saved-media-20261012/commands/)와
[각 Android 테스트 결과](saved-media-20261012/android-tests.json)를 보존한다.

- `python work/saved-media-20261012/build.py build05`: exit0, 133.956초, 600초/5GiB 제한,
  peak2.216GB, 소유 프로세스 정리 확인. debug app/test만 생성했다.
- `python work/saved-media-20261012/host.py host01 ExpressionVarietyTest,LongExpressionTest,EvidenceInterpreterTest,IntervalEvidenceTest,VocalKindEvidenceTest`:
  compile0/test0, **44통과**, 테스트0.837초. 컴파일90초/실행30초 제한.
- `python work/saved-media-20261012/host.py host-baseline ExpressionVarietyTest --baseline`:
  compile0/test1, **10개 중 새 quiet 표현 검사1개 기대실패**. 기존6499256의 표현으로 실패한다.
- `python work/saved-media-20261012/device.py final01 SavedMediaFlowTest,CaptionHdrCompatibilityTest,TranslatedMediaTests,GalleryMediaTests,CaptureAccessLimitsTest,ExpressionVarietyTest`:
  **48개 중47통과·1실패·skip0**, 121.94초, 480초 제한. adb exit0이어도 runner 실패를 감지해 드라이버exit1.
  [전체 로그](saved-media-20261012/final01/instrumentation.txt), [엄격 판정](saved-media-20261012/final01/result.json).
- app/test 설치본 SHA256을 실제 기기에서 비교했다. [provenance](saved-media-20261012/final01/provenance.json).
  이는 연구용 `com.moa.babylanguage.astcandidate`이며 Play 앱 업데이트가 아니다.
- 사용자 USB 허용 후 `python work/saved-media-20261012/phone.py <S25 serial> phone01`:
  동일 app/test APK로 SavedMediaFlowTest/CaptionHdrCompatibilityTest/TranslatedMediaTests **18통과·0실패·skip0**,
  15.78초, 420초 제한, 드라이버exit0. [전체로그](saved-media-20261012/phone01/instrumentation.txt),
  [결과](saved-media-20261012/phone01/result.json), [설치SHA](saved-media-20261012/phone01/provenance.json).
  에뮬레이터47개와 중복된 회귀를 포함하므로65개의 독립 시나리오로 합산하지 않는다.
  실제 Play 패키지의code37/0.5.23-beta·설치시각/경로 metadata가 검사 전후 동일하다. 사용자DB는 읽지 않았다.

## 실패와 수정 이력

### 실제20초 분석 후속 — performance04

`python work/saved-media-20261012/performance.py <S25 serial> performance04`는 **2tests/2pass/0fail/0skip**,
runner23.28초/driverexit0이다. 현재 제품APK SHA는 phone01과 동일하며 test만 재빌드했다.
사용자 자료를 열지 않고 정확한 기존 공개입력 SHA의 파일과 합성 tone만 사용했다.

| 입력 | 실제 DeviceClipAnalyzer 시간 | 영상관찰 | 결과/열 상태 |
|---|---:|---|---|
| 이전20초 공개입력, 프로세스 시작 후 첫 analyzer 호출 | 6.145초 | 1fps 실제20개, PARTIAL/ordered/coverage false | 해석 있음·문제 없음, thermal0→0 |
| 동일 입력 반복 | 5.516초 | 동일20개·부분관찰 | 해석 있음·문제 없음, thermal0→0 |
| 일반fps synthetic tone10초 | 3.127초 | 50개 COMPLETE/ordered/coverage true | INFANT_UNCONFIRMED 보류, thermal0→0 |
| 일반fps synthetic tone20초 | 7.410초 | 100개 COMPLETE/ordered/coverage true | INFANT_UNCONFIRMED 보류, thermal0→0 |

실행 전 research app force-stop 및 runner에서 SavedMediaPerformanceTest→AstIntegratedPerformanceTest 순서를 확인했다.
파일시스템 cold 상태는 아니며 사용자 UI 전체시간·장시간발열·다른기기·일반정확도 측정이 아니다.
실제 입력 SHA와 before/after 원본보존, private 입력/결과만 제거한 근거는
[performance04](saved-media-20261012/performance04/result.json)와 두 device-result를 따른다.

추가 하네스 실패 이력도 보존한다. build06은 arguments API 컴파일 오류, performance01은
shell binary 전달로 입력SHA가 달라진 오류였다. `exec-in ... dd`와 전송 직후SHA 확인으로 보완했다.
performance02는1fps 파일에100관찰을 기대했고, performance03은 PARTIAL이어야 할 상태를
COMPLETE로 기대해 실패했다. 두 실행 모두 실제 첫 분석은 반환됐지만 반복 호출까지 진행하지
못했으므로 2회 통과로 세지 않는다. 정확한1fps 관찰 계약으로 build09/performance04를 실행했다.
일반fps100관찰은 별도 입력으로 확인하며 없는 프레임을 채워 성공시키지 않았다.

기존20261010 서명38의 에뮬레이터 첫45초timeout은 **원인미확정**으로 유지한다.
후속 성공은 그 원인을 고쳤다는 증명이 아니다. 독립Spec 후속 검토는 이실폰 첫/반복 통과와
새서명 beta 패키지·37→38보존·실제 UI20초 분석/저장 근거가 모두 확보되면 제한된Alpha제출에
추가Spec차단 없음으로 판단했다. Standards추가 필수0, 실패행 계측 보완은 선택사항이다.

1. 초기 baseline01은 에뮬레이터 부팅 전 검사로 실행이 시작되지 않았다.
2. baseline02의2실패는 고정 footer에 `performScrollTo`를 호출한 하네스 문제였다. 화면에 보일 때 바로 누르도록 수정했다.
3. baseline03은 SDR 전체 앱 저장1통과, raw 저장2개는 실제 저장 뒤 잘못된 세로 비율 가정으로 실패했다.
   원본의 실제 aspectRatio와 비교하도록 수정했다. HDR1개는 poster 추출에서 실패해 영상 렌더링 전 검사가 중단됐다.
   PNG 성공을 HDR 영상 시험의 선행조건에서 제거했다. 이3개를 제품결함3개로 세지 않는다.
4. build03은 Media3 1.11.1의 package-private `fromSingleItem` 호출로 컴파일 실패했다.
   공개된 track-inference Builder로 수정했고 build04/05가 통과했다.
5. final01의 유일한 실패는 HDR fixture 전체 저장이다. `c2.goldfish.hevc.decoder`에서
   `Invalid to call at Released state`로 실패했다. 에뮬레이터의 이 실패를 skip/통과로 바꾸지 않는다.
   별도 실제 GL 검사에서는 기존 HDR overlay가 gainmap 부재로 실패하며 SDR overlay는 성공했다.
   이것은 확인된 라이브러리 합성 경로 결함이고, 사용자 원본의 원인이 HDR이었다는 확정은 아니다.

## 적용한 수정과 검토

### 새 서명 후보 — 0.5.24-beta / code38

이 작업의 새 betaAAB는 `work/saved-media-20261012/final/Agattok-0.5.24-beta-code38.aab`,
SHA256 `6c3e3eaebb8897c5cf586d3d723432d96c771a227c3ac1009bfe1e20899f9217`이다.
기존 `work/vocal-kind-20261010/final`의 같은버전 산출물은 덮어쓰지 않았고 이번수정본과 SHA가 다르다.
beta/release각 APK+AAB 서명·배포/권한flags·모델 payload·200ms cadence·새 selectedVideo/renderMedia
DEX와 이전37의 보호파일 비교를 통과했다. release는 정책비교용이며 Play제출대상이 아니다.
beta빌드265.10초/peak4.68GB, release214.63초/peak4.20GB; 둘다5GiB/900초제한·exit0·잔여프로세스0.
beta드라이버는 성공뒤CP949경로를UTF-8로읽다실패해 bytes기반검사와기존APK복사만재개했다.
추가DEX검사도추측한메서드명renderVideo때문에실패해실제renderMedia로정정했다.
빌드/기본패키지성공은유지하며제품결함으로집계하지않는다. 하네스실패와재개근거를보존한다.

37→새38 서명업데이트검사는 **exit0/216.90초**로 통과했다. 앱데이터를 초기화하지 않고
기존LEGACY문장필드와10초MP4의 SHA256 `c48cccc11f3bab614d9fd6be89307dc76e09be49c2a8c3b2b503351d34014f01`
일치를 확인했다. 실제 Play 경유 설치검사는 아니며 소유MoaQA35의동일키APK업데이트검사다.

- `TranslatedMedia`: 자막 영상은 GL HDR→SDR 변환, 자막 없는 영상은 HDR 유지 모드;
  공유된 범위/디스크/취소 제한과 원본 트랙 추론. 실패 안내에서 결과 없는 사용자에게 문장 저장을 권하지 않는다.
  자막 출력은 SDR이므로 HDR 원본과 색감·밝기가 달라질 수 있다.
- `CaptureFlow/MainActivity`: 결과 유무에 관계없이 **선택 구간 영상만 저장**. 무료 내보내기,
  중복탭 방지·원본 음성·실제 선택 범위·가짜 Observation 없음.
- `BabyExpression/ClipEvidence`: 짧은 단일 종류의 발성이 실제 PCM 조용한 구간 뒤 다시 나타날 때만
  그 흐름을 표현한다. 앞뒤 각각 인접한 승인창2개를 요구하고 부분/넓은 관찰/시간축 불일치는 제외한다.
  임의 표현 교체나 감정/배고픔/졸림 추정은 추가하지 않았다.
- 독립 Spec/Standards 정적 검토 **필수결함 각0**. Spec의 오류 안내 제안은 반영했다.
  Standards의 렌더/발행/정리 코드 중복 P3는 선택 개선으로 남긴다. 정적 검토를 실폰 성공으로 세지 않는다.

## 정리와 남은 한계

### 최종5cycle — 새 signed beta20 실패 보존

`python work/saved-media-20261012/signed_smoke.py`는 실제20초 선택뒤 **앱의45초timeout 안내**를
관측해exit1로실패했다. 새betaAPK설치SHA는 `106507f17a717cbf2cde5339df7faee9bb94b7669013837a927ca3b496cf5dae`
로검증됐다. [실패원문](saved-media-20261012/release/signed-smoke01/result.json).
장면저장·20초MP4내보내기·release재잠금은이실패뒤실행되지않았으며이전성공으로대체하지않는다.
검사시호스트여유RAM약448MiB를관측했으나시간초과의원인으로확정하지않는다.
연구S25는fileURI의직접호출,새signed는에뮬레이터의가져오기/선택/UI콜백이어서측정범위가다르다.
독립후속Spec분기감사에서AST_INTEGRATED_CANDIDATE/AST_BABBLE_SUPPORT의OR는양쪽true,
동일AST구현/모델/head와YAMNet→support→SoundPhaseRecovery경로를확인했다.
연구용우회가원인이라는증거도없다. 실제배포UI성능gate는**보류**한다.

새38업로드/검토제출은0, Alpha제공37, 자동화33PAUSED다. 5cycle제한에따라추가반복QA를멈추고
실패와미실행항목을보존했다. 소유MoaQA35의테스트앱제거·driver소유외부파일제거·종료확인.
폰은추가검사하지않으며사용자연구앱의직접생성자료는보존했다.
실패run의중복공개입력3개(performance01/02/03/public20.mp4, 각140,042bytes)도경계/SHA검사와
표준Remove-Item명령을준비했으나CreateProcess이전자동정책차단으로실행0/삭제0이다.
다른삭제수단으로우회하지않았다. 최신performance04정확입력과서명후보/증거는다음진단용으로유지한다.
앞선원본확인대기·성능검사전상태는이력이며최신판정은이절과summary.json을따른다.

에뮬레이터의 소유 MediaStore rows0, 렌더 파일0, pending journal 비어 있음을 확인한 뒤
연구 app/test를 제거하고 MoaQA35를 종료했다. 기존 build를 재사용했다.
기존 대용량 삭제의 자동정책차단은 유지하며 우회하지 않는다.
이번 참고 source archive/extraction·host class8대상(6,190,110bytes, 약5.90MiB)의 정확 경계검사+
PowerShell `Remove-Item -LiteralPath`도 자동정책이 CreateProcess 전에 차단했다. 삭제0이며 재시도/우회하지 않았다.
에뮬레이터 정리 확인의 첫 스크립트는 제거된 패키지의 `pm path`가 exit1을 반환해 중단했다.
미설치를 정상 출력으로 검사하는 `pm list packages`로 보완해 나머지 제거·최종 부재를 확인했다.
사용자 S25 연결과 USB 허용 후 별도 시험 app/test만 설치했다. 자동시험은 자기소유 rows/DB/렌더파일을 정리한다.
시험 뒤 MainActivity를 열어 사용자가 실제 오류 영상을 가져와 두 저장 동작을 확인하도록 요청했다.
**현재 연구 앱을 사용자 재확인용으로 유지한다.** 이 시점 뒤 새로 생긴 연구 앱 기록/앨범 출력은
사용자가 만든 자료일 수 있으므로 자동으로 삭제하거나 연구 앱 전체를 제거하지 않는다.

반복 문구의 모든 원인이 해결된 것은 아니다. 소리 종류별8문장 계열의 한계는 남으며,
입력 초기화 검사는 분석 대역을 사용하므로 모델이 두 실제 아기 장면을 구별한다는 입증이 아니다.
약국 옹알이 개선은 사용자 전달이며 이번에 새 측정하지 않았다.
**S25 합성 HDR 변환 검사는 통과**했다. 사용자 원본 재확인, 기존code38 첫20초45초timeout,
새 서명/Play 배포는 미완료다. 이18개 검사는 분석 대역/렌더러 중심이며 실제20초 인식 성능 검사가 아니다.
검사 통과를 일반 인식률·감정/욕구 정확도로 환산하지 않는다.

이전2cycle는USB 승인 대기로stopped→clear→부재 확인했다. 현재3cycle는S25 자동검사를 통과했고
사용자 원본 재확인 대기다. 참고 생성물 정리 정책차단도 유지한다. 전체 목표나 배포 완료가 아니다.
3cycle 실행상태도 사용자 확인 대기를 명시한stopped→clear→부재 확인을 마쳤다([phone01](saved-media-20261012/phone01/)).
