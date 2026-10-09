# UltraQA Report — 200ms 영상 관찰 S25 실기기 검사

상태: **S25 실기기 검사 통과. 첫 부분 실패와 fixture 수정 후 제한된 재개를 보존했다.**
기존 0.5.22-beta/code36은 Alpha 제공 중이며 이번에 새 제품 코드·Play 업로드는 없다.
`ULTRAQA BLOCKED: 이번 재생성 빌드 폴더의 삭제가 사용자 명시 승인 후에도 정책 차단됨`.
이는 제품 검사 실패가 아니다. lifecycle은2cycle stopped→clear→부재 확인으로 종료했다.

## 목표와 경계

사용자가 S25 울트라를 연결했다. 별도 astcandidate 앱에서 같은200ms 코드의 실제 ARM
10/20초 얼굴 부하, 반복, 합성 통합경로 및 취소 후 재실행을 검사한다.
원래 앱/기록을 설치·수정·초기화하지 않고 아기 자료를 외부로 전송하지 않는다.
처음에는 고정 연구 APK2개를 재사용했다. 이후 fixture 문제를 수정한 **test APK만 재빌드**했고
연구 app APK는 동일 SHA를 유지했다. main만 ADB를 사용했다.
첫 run01은 배터리42°C 또는 Android thermal status3 이상이면 중단하도록1초 간격으로 관찰했다.
run02의1.36초 두 검사에는 온도 표본이 없으므로 아래 열 상태 수치는run01만의 근거다.
각 명령/계측에는 timeout을 두었다.
최대5cycle/동일실패3회. 실제 얼굴 입력은 기존250JPEG/PCM의10초 재구성 및 반복20초다.
원본 MP4/독립20초 정답/일반 정확도 평가로 간주하지 않는다.

## 시나리오 행렬

|ID|의도/실패 조건|설정·하네스|기대|실제|근거·수정|정리|
|---|---|---|---|---|---|---|
|P1|기기/앱 혼동·기존자료 영향|물리ARM 확인, APK SHA, production_snapshot|S25·정확 연구앱·원래 설치정보 유지|통과|SM-S938N/API36/arm64·기존Play35 유지|두차례 소유앱2개 제거|
|P2|실제 얼굴10/20/20반복 부하|AstPerceptionProbeTest·시간순50/100/100관찰|완료·각45초내·저메모리없음|통과|9.87/15.76/15.54초·단계 PSS최대284.8MB|파생12.1MB영상 제거·원본해시 동일|
|P3|실제 통합경로 실패/대기시간|AstIntegratedPerformanceTest·합성10/20초|관찰50/100·정상보류·45초내|통과|3.627/7.398초·관찰50/100·sampled PSS최대553121KiB|합성fixture·계측thread 종료|
|P4|취소 후 native/decoder 점유|AST2·VideoTimeline·VideoAudio 취소재개4검사|skip0·통과4|첫3통과1실패→남은취소+신규capability2통과|64x64하드웨어미지원→같은H264/PTS320x320 fixture|첫실패 보존·앱제거|
|P5|온도·무한실행·정리누락|각계측시간상한·열상태표본·전후앱정보|열상태3미만·정리확인|run01 열상태0·31.0→31.7°C·앱정리통과; PC build삭제차단|USB충전·짧은검사한계|새build280.3MB 승인후에도정책차단|

## 측정 결과

|입력|단계별 합계|시각 처리|관찰 수|단일 얼굴 관찰|
|---|---:|---:|---:|---:|
|얼굴 10초|9.868초|5.853초|50|50|
|얼굴 20초|15.759초|9.789초|100|99|
|얼굴 20초 반복|15.542초|9.615초|100|99|

이는 AstPerceptionProbeTest가 단계마다 별도로 측정한 합계다. Play UI 전체 대기시간이 아니며,
20초는 같은10초를 반복한 부하 입력이다. 반복에서 처리시간 급증이나 메모리 부족은 없었다.
실제 DeviceClipAnalyzer를 실행한 별도 **얼굴 없는 합성** 경로는10초3.627초/20초7.398초,
정상적인 INFANT_UNCONFIRMED 보류와 시간순 관찰50/100을 확인했다.
run01의 얼굴/합성/취소 검사 중 Android thermal status는 모두0, 배터리는31.0→31.7°C였다.
온도는 배터리 센서이며 피부 표면 온도·지속 사용 발열·방열 보장을 뜻하지 않는다.

## 명령·실패·수정

- `python work/visual-cadence-phone-20261009/run.py`: exit1. 얼굴/통합 검사는 통과했지만
  취소 관련4검사 중 VideoTimeline 한 건이 **MediaCodec.configure**에서 실패했다.
  취소 콜백 전의 실패다. 나머지AST2/음원decoder1은 통과했다. 계측timeout240/150/180초.
- 실제 S25 기본 H264 decoder는 `c2.qti.avc.decoder`, `64x64=false; 320x320=true`였다.
  기존64x64 시험 fixture는 보존하고 새320x320 fixture를 추가했다. H264·10fps·200프레임·
  20초·전체PTS가 같으며 색상 내용은 nearest-neighbor로 확대했다.
  생성: `ffmpeg -i temporal-red-blue-20.mp4 -vf scale=320:320:flags=neighbor -c:v libx264 -threads 1 -pix_fmt yuv420p -crf 18 temporal-red-blue-device-20.mp4`.
- 취소 검사의 예외 전파·빌린 bitmap recycle·후속decode5관찰 assertion은 유지했다.
  장치capability 검사1개를 추가했다. 제품 decoder 소스는 변경하지 않았다.
- `build_recovery.py`:106.83초/root exit0, 종료후 소유자식 정리(orphan_cleanup), 자식0,
  메모리상한/출력제한 미발동. 연구 app APK `72fb109e…` 유지, 새test APK `bc59b3b2…`.
- `resume_recovery.py`:exit0/1.36초, 실패했던 취소검사와 새capability검사 **2/2 통과·skip0**.
  첫run01의SHA `e95a0ea5ad76a8afa003a863b9fea94d698e034df2ed78e5b848346ac5098e30`와
  원래app SHA·사용자앱 설치정보를 대조하고 남은 검사항목만 재개했다.
- Standards 검토에서 정리실패를 exit0로 끝낼 수 있는 하네스 결함을 발견했다.
  finally 뒤 최종status assertion을 추가하고 동일 assertion을 process로 검사해
  pass→exit0/failed→exit1을 확인했다. 실제run02는 정리오류0였으므로 폰 검사를 반복하지 않았다.

## Standards

필수 하네스1건 수정(정리실패 exit 전파), 선택2건 반영(새test빌드/열관찰범위 명시).
리뷰어 `/root/visual_cadence_standards`는원천·전체PTS·SHA를읽기검토했다.
최종 재검토에서 필수·선택 잔여0을 확인했다.

## Spec

필수 코드결함0, 문서1건 반영(새test APK 빌드·실패후 제한재개 명시).
리뷰어 `/root/visual_cadence_spec`는 취소검사목적이 약해지지 않았음을 확인했다.
최종 재검토 필수잔여0. 과거 배포시점의 실폰 미측정 문구도 후속 S25 측정과 구분했다.

## 정리·근거

- 연구앱2개는 각run 종료시 제거했고 로컬파생MP4도 제거했다. 사용자 앱은 code35 그대로다.
  production_unchanged는APK hash·버전·설치시각의일치이며 사용자DB를읽거나byte검사하지않았다.
- 새로 만든 `work/visual-cadence-phone-20261009/recovery-build`의1,831파일/280,343,219B 삭제가
  자동 승인 검토에서 차단됐다. 사용자가 정확한 폴더 삭제를 승인했으나 재시도도 차단됐고
  우회하지 않았다. 원래app/build2.38GB는 이번삭제승인대상이 아니며 그대로다.
- 이번 추가 보존물은 새test APK2.24MB, 합성fixture7.8KB와 작은근거다. C:여유 약77.5GB.
- [집계](visual-cadence-phone-20261009/summary.json),
  [첫 부분 결과](visual-cadence-phone-20261009/partial-result.json),
  [제한 재개 결과](visual-cadence-phone-20261009/recovery-result.json),
  [최초 실패](visual-cadence-phone-20261009/recovery-failed.txt),
  [재개 통과](visual-cadence-phone-20261009/recovery-passed.txt),
  [장치 지원 크기](visual-cadence-phone-20261009/codec-capabilities.txt).
  partial-result는원본byte복사라 recovery-result의SHA연결을저장소에서도검증할수있다.

## 남은 한계

별도 연구 빌드의 처리시간과 짧은 반복 검사다. Play 설치본 UI 전체시간·장시간/저사양폰·
일반 발성/화자/감정/욕구 정확도는 입증하지 않는다. 이전 app/build2.38GB 삭제 승인 대기는 별도다.
64x64H264 실제가져오기에서의시각decode미지원은제품수정으로해결한것이아니다.
실제얼굴20초시각단계약9.8초는후속최적화대상이며이실험을성능향상증거로보고하지않는다.
