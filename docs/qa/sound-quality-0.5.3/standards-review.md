# Standards 독립 검토 — 소리 품질 0.5.3

2026-10-02 KST · 기준 `5362ae3...e540f8a` (`feat: interpret temporal sound evidence with symmetric ambiguity and rumble filtering`). `AGENTS.md`, `HANDOFF.md`, `RUNBOOK.md`와 제품·androidTest diff를 읽었다. 이번 담당은 문서만 작성했으며 Gradle/ADB·동적 테스트는 실행하지 않았다.

## Hard findings

**P2 — 공개 음성 QA의 minSdk 호환 위반, 후속 소스 수정 확인.** 커밋의 `PublicAudioProbeTest.kt:80`은 `Optional.orElseThrow()` 무인자 overload를 사용한다. 이 overload는 Android API33부터 제공돼 `RUNBOOK.md`의 min26 계약 아래 API26–32에서 opt-in probe를 실행하면 `NoSuchMethodError`가 발생한다. 제품의 `SoundAnalyzer.kt:64`는 supplier overload를 사용하므로 release 분석 경로의 결함은 아니다. 메인에 즉시 전달했고 현재 작업 트리에서 테스트도 supplier overload로 바뀐 것을 읽어 확인했다. 구형 기기에서의 실제 실행을 완료했다고 주장하지 않는다.

그 외 변경 범위에서 추가 보안·자원·수명주기 hard violation은 발견하지 못했다.

## Smells — 판단 사항

**가능한 Primitive Obsession:** `SoundTimeline.kt:13–14`에서 사건 enum을 표시용 한국어 문자열로 바꾼 뒤 `ClipEvidence.kt:74–94`가 그 문자열로 대사 의미군을 다시 분기한다. 표시명을 바꾸면 분기 불일치가 생길 여지가 있다. 향후 문구 정비 때 의미 결정은 `VocalEvent`/혼합 상태로 유지하고 한국어 label은 화면에서만 사용하는 편이 명확하다. 현재 관찰된 오출력이나 승인 차단 사유는 아니다.

## 확인한 경계

- `SoundAnalyzer.kt:40–49,76–82`: PCM 길이·유한값·진폭 제한, 모델 실행 직렬화, 취소 재전파, `use` 자원 해제, 필터 배열 `finally` 초기화가 있다. 필터는 최대15초·16kHz 입력을 Double 상태로 계산하고 결과를 제한하며 원본을 변경하지 않는다.
- `SoundTimeline.kt:25–85`: 미확인 일반 소리만으로 아기 사건을 허용하지 않고 잘못된 점수를 제외한다. 웃음/울음 충돌은 양방향이며 미해결 사건을 지우지 않는다. 프레임 시각은 SDK 값을 쓴다.
- 아동 말소리3창 허용은 명시된 정책 확장이다. 아기 신원·욕구 정확도로 표현하지 않는다는 AGENTS 규칙과 조사 문서의 한계를 유지한다. 일반 아동/TV와 현장 아기를 구분하는 검증이 추가된 것은 아니다.
- 메인이 진행한 후속 `AcousticShape.from` 변경도 읽었다. 리듬·세기 계산에 같은 필터를 적용하고 사본을 초기화해 제거한 DC를 아기 발성의 연속성으로 설명하는 불일치를 해소한다. 원본 기반 wordingKey는 여전히 의미 결정과 분리된다.

총 hard finding **1개(소스 수정 확인), 미해결0개**, smell **1개**. 소스 검토는 승인하며 최종 계측·패키지·실음원 판정은 메인의 실행 증거를 따른다. 이 검토는 분류 정확도·실제 아기 의도 검증 승인이 아니다.
