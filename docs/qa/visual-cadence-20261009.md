# UltraQA Report — 구간 내 짧은 입 변화 관찰 후보

상태: **로컬 후보의 관찰·Android 회귀 QA 완료, 실폰 성능·패키징·배포 미완료**.
기준cf691aa / beta35 검토 요청과 별도인 로컬 후보.
[수정 계약](../research/20261009-visual-cadence-contract.md).
**ULTRAQA COMPLETE: Goal met after 2 cycles.** 아래 제한된 로컬 QA 범위다.
발성 보류 회복·통합 AV 이해·전체 장기 목표 완료는 아니다.

## 목표와 경계

200ms cadence로 짧은 움직임 누락을 줄이고 실제 Android20초 decode/PTS/모델/취소를 보존한다.
발성 미인식·화자·감정/욕구·일반 정확도 해결을 주장하지 않는다. 음성 판정과 이용권은 그대로다.
최대5cycle/같은 실패3회. main만 Gradle/ADB. 공용MoaQA35와 격리astcandidate앱을 재사용한다.
사용자폰/원본/베타 제출35·테스터목록/프로덕션은 변경하지 않는다.

## 시나리오 행렬

|ID|의도/상황|설정·하네스|기대 신호|실제|수정/근거|정리|
|---|---|---|---|---|---|---|
|C1|짧은 입 열고 닫힘이 sparse 표본 사이에 있음|실제 cadence→timeline→interpreter, VideoCadenceTest|관찰되고 시각표현 보조|baseline2실패→최종19통과|host baseline-red/final-host|host 격리|
|C2|촘촘해진 표본으로 짧은 미소를 과대평가|실제 alignment|500ms 미만 미소 제외|통과|최소 지속시간 명시|fixture 없음|
|C3|실제 약국 영상의 기존10초 JPEG|기존250개 hash/PTS 검증, PC Face1.0.0|간격 변화만 비교|6/19/23 상태 변화, 독립 정답 아님|pharmacy10/summary|개별 특징 ignored|
|C4|얼굴·소리 없는 허용 영상|실제 PC decoder/model|세 cadence 모두 얼굴0|통과|local10/summary|원본 보존|
|C5|구간 시작/끝·회전·crop·실제 모델|VideoTimeline/VideoColor/VisualFeatures/Audit|20초100관찰/실제PTS/누락정직|최종Android98/98, 실패·skip0|native02 설치SHA/실행원천|격리 앱 삭제 완료|
|C6|취소·다중얼굴·시간오류·성인/무음|실제 Android 관련 회귀|취소전파/재실행·오인 방지|동일98검사 내 통과|native02|동일|
|C7|연구 CLI 오동작·무한실행·가짜성공|Python tests/Windows Job bounded150s 2GiB|비정상PTS/출처불일치 거부, 정상exit/자식0|Python5통과, PC최종exit0/자식0|pc-supervision.json/출처변조검사|runtime 삭제 완료|
|C8|기존 제출·다른 작업 보존|git diff/패키지·프로덕션 설치 확인|새버전 업로드 없이 후보만 검증|code35 AAB SHA 보존, 연구앱 별도설치 검증|summary.json|빌드1.02GB 정리|
|C9|제품 AV 전체경로의 합성10/20초|AstIntegratedPerformanceTest, 기존100초 시작 fixture|50/100관찰·AST경로·합성소리보류|2건 측정·검사1통과,2.601/4.552초|performance result, 같은앱APK SHA|합성입력/측정thread/결과복사 정리|

## 실행·실패·수정 근거

- 첫 host2실패:500ms 표본의 반복 입 변화 누락, 촘촘한 관찰에서 최소 미소 지속시간 미명시.
 200ms/공유count·500ms미소/1000ms입변화 run 적용 후 최종host19통과.
- 독립 Spec에서 crop 검사에 남은2장 기대치를 발견해5로 수정했다. 전체 호출 검색으로
 VisualFeatures/Audit와 실제 통합 검사의500ms 기대치도 갱신했다.
- 첫 Android98검사 중97통과/1실패: color fixture의 예전2장 기대치 잔재였다.
 색상 픽셀검사는 통과했고, 새5장 기대치를 수정한 뒤 최종98통과·실패0·skip0이다.
- Gradle build02/03은root exit0/BUILD SUCCESSFUL 뒤 소유 자식이100ms 이상 남아
 `orphan_cleanup`으로 정리됐다. 최초 래퍼는이를무조건실패로세어exit1을반환했다. 로그를보존했다.
 최종 build04는root exit0+성공로그+제한미발동+출력미절단+자식0을 함께 검사하고
 정상root 종료 뒤 소유 자식 정리만 허용한다. timeout·메모리/출력제한은 허용하지 않는다.
 최종48.64초,root0/orphan_cleanup/cleanup완료/남은자식0. 독립 Standards가 원천 검토했다.
- 연구 helper의 duplicate sampler 권고를 반영했고 최종재실행의 세 개별관찰JSON은byte exact다.
- 명령: `host.py baseline-red VideoCadenceTest` exit1 → `host.py final-host VideoCadenceTest,VisualTemporalAlignmentTest` exit0.
 `python -m unittest discover -s tools/research -p test_visual_cadence_probe.py -v` exit0/5검사.
 `build04.py` exit0, `native02.py` exit0/9.05초/98검사, `performance.py` exit0/8.45초/1검사.
 실제 하네스와 원천로그는 ignored `work/visual-cadence-20261009/`, 작은 익명증거는
 [증거 요약](visual-cadence-20261009/summary.json)에 있다.

## Standards

최종 필수0·선택잔여0. 공유sampler/출처변조거부/정상root뒤소유자식정리/원천98통과를 검토했다.
작업자 `/root/visual_cadence_standards` · GPT-6.1 Sol/high · read-only.

## Spec

최종 필수0. crop/color/실제모델/통합 기대치,19host·98Android·50/100관찰근거를 검토했다.
작업자 `/root/visual_cadence_spec` · GPT-6.1 Sol/high · read-only.

## 정리와 남은 승인 기준

- 원본이 없는 실패 영상의250개 파생JPEG가 이전 실험 폴더에 남아 있어 재사용했다.
- 동일100ms track 사후 축소에서도6/19/23으로 달라 표본 간격의 정보 누락을 확인했다.
- 횟수 자체는 검출 정답이 아니고,200ms에서도100ms보다 관찰이 적다.
- 연구PC 측정과 Android **에뮬레이터 합성영상**의2.601/4.552초는 폰 속도/발열 근거가 아니다.
 실제 얼굴 모델 비용을 포함한 S25/저사양폰 성능은 아직 검증하지 않았다.
- APK/AAB 새 제출 없음. 앱 음성 보류의 원인은 아직 해결로 판정하지 않는다.
- 버전 증분 없이 **별도 astcandidate** 앱으로 검사했다. 이 APK를 Play설치용으로 배포하지 않는다.
 실폰 측정/최종서명/새versionCode가 후속요건이며 검토중beta35는원래500ms다.
- 소유 연구앱2개 제거·에뮬레이터종료·공용AVD보존,앱build/새PC런타임2646파일1,018,210,440bytes 삭제.
 정리직후여유80,259,670,016bytes. 최종연구APK2개약267MB와작은근거만새로보존했다.
 기존파생JPEG24MB·모델·원본·직전/현재beta배포본은유지했다. 원본/프레임/개별특징외부전송0.
