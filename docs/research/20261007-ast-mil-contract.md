# AST768의 평균 대신 bag 학습 — 사전 고정 비교

2026-10-07. 기준 commit `989a721aaaa8afcba80ccabe8eaa544025340bcc`.
기존 AST768 presence head는 10초 span representation을 먼저 평균하고 clip BCE로 학습했다.
이번에는 동일 공개35/43span을 사용하되 span별 L2와 actual-sample 가중 log-mean-exp bag BCE로 바꾼다.
이전 YAMNet1024/975ms MIL과 별도 가설이다. AST 10초 span 안의 발성을 분리하거나 정확한 사건 시점을 얻는 것이 아니다.

## 고정 입력과 선택

- source snapshot SHA `27966aa9265da239489e7351959c01399ed3a0aed6428149babc2cb127fad4b9`.
- AST35 archive SHA `359083b64f4db2ac29059be5430e773ea99e8fc423952bbb0e5ecf1940e0c330`.
- 기존21 train/18 uploader source, 14 validation 유지. train의 6clip만 2span이고 그중 영아 양성2clip이다.
  기존 허용 exact public35 caption-weak 연구 범위만 사용한다. 새9437/244237은 blocked/null 그대로이며 사용하지 않는다.
- RMS<.003은 제외한다. span의 real_samples로만 가중하고 padding은 시간으로 세지 않는다.
- head769param/3076B FP32. zero 초기화, Adam .03/300step, 온도1,
  L2 .0001/.001/.01를 train source leave-one-out macro NLL로 선택한다.
  선택/head를 저장한 뒤 validation과 사용자 평가자료에 접근한다. threshold .8/margin .4 유지.
- 새 encoder/원음 취득/LLM/영상 upload 없음. 기존 사용자 PCM은 동결 후 평가전용이며 학습·선택·증강에 쓰지 않는다.
- 현재 평균 head와 frozen new head의 validation/retained3/public33 결과를 비교한다.
  현재 약국 회복2span을 잃거나 성인·음악·무음의 신규 support가 있으면 채택하지 않는다.
  희소성·감정 context·화자 미검증은 support로 해결됐다고 보고하지 않는다.
- provenance/입력SHA/runtime 고정, offline Job180초·fit2GiB/eval3GiB.
  출력은 ignored `work/ast-mil-20261007/`의 새 run만 허용한다.

## QA matrix와 종료

|ID|의도/설정|기대|명령/증거|정리|
|---|---|---|---|---|
|M1|768/1024 domain, shape/NaN/quiet/chronology/padding/actual time|이상 거부; 기본1024 유지|고유 unit log|bytecode 생성 안 함|
|M2|analytic gradient/극값/단일 span BCE 동치|독립 numerical check 통과|unit log|없음|
|M3|source/feature SHA·blocked·역할·pin 변경|학습 전에 거부|unit+CLI|원본 불변|
|M4|전용root 밖/기존output/required누락|실제 CLI exit≠0; 대상 미생성/불변|guard JSON|새 잘못된output0|
|M5|정상fit/eval; success text/exit/partial|head 동결/최종pin 일치/자손0|process/completion/result|head 한세트; 새largeweight0|
|M6|dirty·중단/timeout·회복|타 작업 보존; bounded helper 실제timeout/recovery 확인|QA state/log|scoped state clear|
|M7|보관/드라이브 전후 용량|actual snapshot/이번 임시fixture·process 정리|storage JSON|기존삭제차단 우회0|

최대5 QA cycle/동일실패3회에서 한정 QA를 중단하고 원인을 기록한다.
제품·ARM 검증·Play update는 별도 gate다. 이 실행을 새 아기 정답/일반정확도/유료품질로 계산하지 않는다.
Spec/Standards 두 축 독립 리뷰 뒤 commit/push하며 전체 goal은 계속 active다.
