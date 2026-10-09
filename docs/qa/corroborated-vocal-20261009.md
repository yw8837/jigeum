# UltraQA — 같은 구간에서 보완 확인한 아기 발성

## 목표·경계
code34기준. 미확정/혼합발성에서 충분한같은구간 infant근거가있으면감정없는발성후보만표현.
모델/threshold/필터/gain변경없음. 기존출력 exact, 성인/잡음새출력0, 기록·정식접근보존.
최대5cycle/같은실패3회중단. 대형모델취득/아기자료업로드/상품·프로덕션변경없음.

## 시나리오
|ID|목적·사용자/입력|설정·명령|기대|실제/수정|근거·정리|
|---|---|---|---|---|---|
|P1|작은웃음·혼합단서|host.py red01/green01, baseline.py candidate01|실패회복/모델점수보존|host2실패→63통과; Android대기|work/corroborated-vocal-20261009|
|P2|성인·일반웃음·잡음·무음|frozen28+53 paired|기존승인 exact, 신규대조출력0|대기|UUID입력삭제/원본hash보존|
|P3|NaN/누락/중복시간/다른창음량·head/감정·TV|CorroboratedVocalTest/기존정책native|오인/우회없음|host통과/native대기|실제서명APK hash|
|P4|취소/연속재분석/20초AV|실제화면·계측|시간제한/취소보존·다른구간복귀|대기|소유실행/fixture정리|
|P5|서명업데이트/미디어|34→35저장회귀|기록/MP4 hash보존|대기|사용자설치보존|
|P6|beta/release패키지/접근|기존builder/check_package/UI|beta전체/정식재잠금|대기|현재+직전한세트보존|
|P7|불완전실행/skip/stale보고서|고정run_id/count/hash/terminal실패검사|불완전검사성공처리금지|대기|bounded subprocess로그|
|P8|dirty/tree·저장공간·lifecycle|소유폴더/실행만정리|기존파일/공용AVD보존|대기|정리전후disk·state|
|D1|기존Alpha업데이트|새betaAAB만제출|정확버전 실제상태기록|대기|중복제출없음|

## 현재
사전계약 docs/research/20261009-corroborated-vocal-contract.md.
실패한gain실험은제품에서제외. code35패키지/배포는아직없다.
