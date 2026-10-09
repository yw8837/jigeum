# UltraQA Report — 선택 구간 관찰 보완 / 0.5.23-beta code37

## Goal and success criteria

[계약](../research/20261009-interval-evidence-contract.md). a3b183d 기준.
혼합 소리 전체 보류에서 검증 가능한 일부 단서를 보존하고, AST 넓은 승인 창의 영상 관찰을 연결한다.
모델/기준값/외부 전송/접근 정책 변경 없음. 일반 정확도 또는 소리 주체 입증은 목표에 포함하지 않는다.
최대 5 cycle, 동일 실패 3회 중단. 구현 source46d7a74, 버전 반영1399f64.
cycle1에서 legacy PTS 우회와 겹친 미확정창을 수정했고 cycle2의 최종 검사를 수행했다.

## Scenario matrix

|ID|의도·모델|준비·명령|기대 신호|실측·수정|근거·정리|
|---|---|---|---|---|---|
|P1|반복된 발성+미확정 다른 소리|IntervalEvidenceTest, device.py paired01|부분 발성만 중립 표현|실제53조건에서2조건 추가 결과|26개 기존 결과 exact 보존|
|P2|성인·무음·배경에 아기 얼굴 더빙|동일 테스트와 보관 모델 출력 재생|새 아기 출력0|대조 신규 출력0|정밀 더빙/화자 검증 모델은 아님|
|P3|넓은 옹알이창+시간순 입 변화|VisualTemporalAlignmentTest, face_probe.py|AUDIO_VISUAL·넓은 범위 명시|재구성10초50관찰 AV 반영, 반복20초100관찰은10초시점2얼굴로 AUDIO 유지|원본해시 보존·파생MP4/연구앱 제거|
|P4|역순/중복/NaN/미정렬/다중얼굴/관찰 공백|IntervalEvidenceTest|연결 거부|첫47개 중1실패 수정, 최종48host 통과|최초 실패 로그 보존|
|P5|기존 성공·음성 오류·정식 권한|회귀·서명 패키지/Android 검사|성공 문장·오류·권한 유지|beta/release37 서명·모델·DEX flags·버전, 실제36→37 기록/MP4, AV 분석·저장·정식 재잠금 통과|제품/연구 패키지 구분|
|P6|취소/재개·숨은 실패·timeout|Android 통합 경로와 종료코드 검사|취소 전달·후속 복구·정상 종료|94·84개 묶음 각0실패/0skip|두 묶음은 테스트 중복 포함|
|P7|작업트리·원본·용량|시작 clean·원천 SHA·전용 작업폴더|타인 변경/원본 보존|시작 clean|기존 삭제 정책 차단 폴더 유지|

## Commands run / Failures found / Fixes applied

첫 host 실행: `work/visual-cadence-20261009/host.py interval-first ...` — 47개 중1실패, exit1.
입력의 모든 PTS가 없을 때 옛 영상 표현 분기로 들어가 broad 결과에 시각 출처를 붙이는 결함이었다.
새 넓은 창 연결에는 legacy fallback을 금지했다. 중간47통과 뒤 독립 Spec 검토에서
겹친 화자 미확인 웃음/약한 울음창도 제외해야 함을 지적받아 반례를 추가하고 최종48통과했다.
첫47통과를 최종48검사의 소스 근거로 재사용하지 않는다.

- `host.py green02 ...`: 최종48통과, [source hash·exit](interval-evidence-20261009/host.json).
- `device.py native01`, `native_extra.py`: 실제 Android94·84개 각통과/0skip. 후자는 일부중복이다.
  기존 SoundTimeline/SoundVisual, AST 취소·실패 복구, 음성/영상 타임스탬프와 디코더 검사를 포함한다.
- `device.py paired01`: 기존53조건 실제 YAMNet/AST 재추론. 원음·모델·frame 관측 exact.
  기존26출력 exact, `laugh_babble`와 `extra_254454`의 새 중립 부분발성2출력, 나머지25보류 동일.
- `replay.py baseline4/candidate4`: 보관84조건 정책 재생36→38출력. 전체81조건 exact,
  추가부분결과2, 기존 face10에 AV 표현1. Android 새 추론이나 독립84원본이 아니다.
- 첫 재생 하네스는 Kotlin 숫자0의Double표기 누락으로 컴파일 실패했다. 이후 직렬화에서 빠진
  kindUncertain을 기존 AST 정책으로 복원하고 모델 입력의 floor ms와 구간 ceil ms를 구분했다.
  잘못된 중간집계는 사용하지 않고 baseline4의36출력이 기존 집계와 일치함을 확인했다.
- `signed_recovery.py01`: 동일15초 CC0 음성의 AAC 재인코딩 MP4에서 기본 선택된 **첫10초**를 서명36/37에서 비교했다.
  **36 보류→37 일부 아기 발성 카드**, 미확정 부분 표시 확인. 파생물/기기 복사 제거·원음 SHA 보존.
  화면18.4/22.6초는 서로 다른 결과 탐색·펼치기를 포함하므로 추론속도 비교가 아니다.
- `face_probe.py`: 보관10초JPEG/PCM 재구성10/반복20초를 연구앱에서 실행,50/100관찰.
 10초9.70초, 반복20초23.88초는 에뮬레이터 단계 합계이며 실폰 속도나 독립20초 정확도가 아니다.
- `build_signed.py beta01/release01`, `verify_final.py`: 서명37 APK/AAB·assets·flags·200ms DEX 검증 통과.
  정식 비교용 release37은 Play 제출 대상이 아니다.
- `run_ui.py upgrade final`: 700초 제한, exit0 / 215.18초. 실제 서명36→37에서 합성 구형 기록과
  저장10초MP4(audio/video 포함)의 SHA256이 동일했다. 앱 데이터 초기화 없음, 베타 전체 접근 유지.
- `run_smoke.py`: 480초 제한, exit0 / 202.59초. 실제 beta37의 영상+소리 분석·저장·MP4 내보내기,
  같은 서명 release37의 시각/PDF 잠금·60/10 안내와 저장MP4 해시 보존을 확인했다.
  Play 실제 업데이트/구매 검증은 아니다. 취소·촬영 정책 전체를 변경하지 않아 이전 긴 촬영 검사를 반복하지 않았다.

## Code review

### Spec

초기 필수1건: 전역 보류를 만든 화자 미확인 반대 소리창과 겹치는 부분창을 승인할 수 있었다.
다른종류/null vote와 겹치는 창을 모두 제외하고 1500ms 반례2개를 추가했다.
46d7a74 재검토 **필수 잔여0**. 모델 임계값·발성 주체·감정/욕구를 새로 확정하지 않는다.

### Standards

초기 필수0·선택1건: 좁은/넓은창의 입 변화 자격조건 중복. `hasSustainedMouthChanges`로 통합했다.
46d7a74 재검토 **필수0·선택0**. 두 검토는 읽기 전용이며 실행 QA와 구분한다.

## Cleanup and rollback / Residual risks / Evidence

이번 QA 앱3개 부재, 소유 외부 미디어 삭제, MoaQA35 에뮬레이터 종료를 확인했다.
공용AVD와 사용자폰/원본은 유지했다. 이번 실행은 새 원본/모델 다운로드가 없다.
검증APK2개를 해시 대조 후 보존하고 새 `work/interval-evidence-20261009/build` 968,764,561바이트
삭제를 시도했으나 **자동 승인 정책이 차단했다**. 삭제나 우회 완료로 기록하지 않는다.
이전 `outputs/MoaAndroid/app/build` 약2.38GB와 `work/visual-cadence-phone-20261009/recovery-build`
약280MB의 삭제 차단도 유지한다. 종료 시C드라이브 여유54,779,625,472바이트다.
이 값에는 다른 작업·공유AVD의 영향이 있어 전후 차이를 이번 작업 단독 사용량으로 주장하지 않는다.
UltraQA cycle2의 제품 게이트는 통과했다. 정리 포함 상태는 **ULTRAQA BLOCKED: 임시 build 삭제 정책 차단**이며
해당 실행만 stopped→clear→부재 확인했다. 승인 정책이 허용하는 시점에 명시된 폴더만 정리할 수 있다.
보관 출력 정책 재생은 실제 모델 재추론이나 새로운 휴대폰 성능 검사가 아니다.
`laugh_babble`은 알려진 개발용 CC0 소스이며, `extra_254454`는 작성자의 babbling 설명과
영아 tag만 확인된 자료다. 이번 두 출력은 정확한 욕구·영아 정답2개나 일반 인식률 증가율이 아니다.
시간 이동된 다른 아기 음성의 주체·정밀 입술 동기를 구분한 것도 아니다.
code36 Alpha 제공 상태는 유지되며 code37은 **2026-10-09 22:01 KST 검토 요청 확인**이다.
게시 개요의 검토 중인 변경사항에 정확37이 있고 사전 빠른 검사가 진행 중이다.
실제37 테스터 제공은 미확인이다. [Play 제출](interval-evidence-20261009/play-review.md).
QA 증거의 텍스트 사본은 CRCRLF/후행 공백을 정규화했다. 원래 실행 로그는 ignored work에 보존한다.

작은 근거: [요약](interval-evidence-20261009/summary.json),
[53조건 비교](interval-evidence-20261009/android-comparison.json),
[서명 업데이트](interval-evidence-20261009/signed-upgrade.json),
[접근·저장](interval-evidence-20261009/signed-smoke.json),
[정리](interval-evidence-20261009/cleanup.json).
