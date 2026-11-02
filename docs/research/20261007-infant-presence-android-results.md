# Android 발성 후보 — 약국의 두 누락 구간에서 실제 한마디 회복

2026-10-07 KST. 구현 `323c82f`, [사전 계약](20261007-infant-presence-android-contract.md).
**연구 후보에서 첫 구간과 후속 구간 모두 실제 한마디가 나왔다. 제품 beta에는 아직 적용하지 않았다.**
사용자 음성/영상/특징의 외부 전송·학습·calibration·threshold 선택은 0이다.

## 무엇을 바꾸었나

기존 AST 입력/527 logits를 보존한 graph에 공개 자료로 학습하고 동결한 769parameter 발성 head를 연결했다.
원래 graph 대비 추가 **4,474B**이며 큰 LLM이나 새 encoder 다운로드는 없다.
새 score는 보정된 확률이나 의도 번역 정확도가 아니다. [학습 근거](20261007-ast-infant-head-results.md)의
train21/18source·영아7clip/4source/40.7초라는 작은 caption-weak 표본의 한계를 유지한다.

기존 YAMNet/AST 옹알이 및 shifted YAMNet 보완을 먼저 처리하고 새 general presence는 마지막에만 붙인다.
같은 AST 창에서 score≥.80, cleaned strongest1s RMS≥.003을 함께 요구한다. 원래 감정/TV 충돌·실패·무음 보호는 유지한다.
기존 입력 무음 기준 .003을 통과한 단서를 whole-scene activity 기준 .012/.04가 다시 버리던 구조를
**general presence에만** 수정했다. 기존 울음·웃음·옹알이 기준이나 모델 score 임계값을 낮추지 않았다.
[quiet gate 근거](20261007-quiet-presence-gate-evidence.md)를 참고한다.

새 단서는 `InfantVoiceSupportWindow`로 분리했다. `BABBLE`, 울음, 웃음, 욕구, 발성 onset·연속성·횟수,
입술 동기를 만들지 않는다. 8개 general 발성 표현에는 으앙/응애·배고픔·졸림을 붙이지 않는다.
화면 근거는 넓은 범위와 정확 위치·지속 여부 미확인을 표시한다.

## 실제 Android 비교 — 동일 음성36조건

최신 후보 APK SHA `77b20be46ecef6e302515d63bde12f4c378a8eeb50ea829fb455d0271a61624e`.
API35 x86_64 공용MoaQA35에서 actual ORT native run과 원음별 pin 확인으로 비교했다.
`prior_phase`는 기존 AST+shifted YAMNet까지 완료한 기준이며 `phase`는 그 결과에 general presence를 보완한 값이다.

| 기존 누락/대조 | 기존 결과 | 최신 후보 | head score | 같은 창 cleaned RMS |
|---|---|---|---:|---:|
| 약국 첫0–10초 | INFANT_UNCONFIRMED | **아기 발성 / 한마디 출력** | .8198 | .009325 |
| 같은 원본7.173–17.173초 | INFANT_UNCONFIRMED | **아기 발성 / 한마디 출력** | .9400 | .014138 |
| 별도 울음8.790초 | 울음 / 한마디 출력 | 울음 / 한마디 그대로 | .6419 | .162486 |

첫 구간의 whole-scene activity는 .010이었다. head 기준은 이미 통과했으며 새 음량 gate의 구조적 보완으로
실제 표현까지 회복됐다. 예시 문구는 ‘작은 목소리를 건네요. 눈을 맞추고 함께 이야기해요.’다.
약국 두 구간은 한 원본에서 겹치며 **독립 두 아기의 성공이나 인식률2/2가 아니다**.

전체36조건 중 기존 한마디 승인13개의 observation·문장·사건이 exact 보존됐다. 새 general 지원은 위 두 구간뿐이었다.
기존 공개33조건에 general 지원이 새로 붙은 사례는0이다. 성인 대화·음악·청소기·무음 등과 기존 감정 결과를 비교했지만,
공개33은 합성/개발/기존train source가 섞여 있어 일반 오인율 분모로 쓰지 않는다.
측정 실행은112.28초/36측정/계측1pass/실패·skip0, 고유 runtime copy 제거·원본 pin 불변을 확인했다.

## 저음량 수정의 반례

실제 Android에서 추가7조건을 사전 정의해 검사했다. exact silence, DC offset, 2ms click, 낮은440Hz tone,
1초 audible tone+19초 silence, quiet 앞부분+공개 성인 대화 tail, quiet 앞부분+공개 piano tail이다.
20초 조건은 실제0–10/5–15/10–20 중첩3창이 모두 반환됐다. **general 지원0/새 한마디0**, 기존 판정 유지다.
24.88초/계측1pass이며 public source의 변형 대조를 새 독립 source로 세지 않는다. 사용자 음성의 padding/혼합은 하지 않았다.
같은 창의 큰 성인 말·음악과 head 오인이 함께 있으면 여전히 위험하다. 이7조건 통과로 해당 문제가 사라졌다고 주장하지 않는다.

## 실제 앱 경로와 영상 시간 흐름

보존 float PCM3구간을 PCM16 WAV로 format 변환한 다음 `VideoAudioDecoder`→`DeviceClipAnalyzer`를 그대로 실행했다.
약국 두 구간 모두 general 발성 한마디가 나왔고 별도 울음은 기존 결과를 유지했다. 각각3.638/2.227/.149초,
계측1pass/8.21초·원본 float PCM 불변·임시 WAV/runtime copy 정리다. 학습용 augmentation은 하지 않았다.
**약국 원본MP4를 지금 확보하지 못해 이것은 원본영상 회복 검증이 아니다.**

별도 기존 옹알이 원본MP4의 실제10/20초를 전체 AV 경로로 검사했다. 기존 옹알이 결과 유지,
20/40개 actual PTS 시각 관찰·시간 순서·선택 구간 coverage를 보존했다. 각각6.894/12.310초다.
이 원본은 새로 회복한 약국 음성과 다르며 이미 기존 AST가 지원한 개발 사례다.
이 수치는 에뮬레이터 한 번의 실행이고 직전 다른환경/실행과 공정한 속도 비교나 휴대폰 성능 보장은 아니다.

실제 UI의20초 분석 중 취소→다음 분석 완료·입력 보존·자동 저장0·main heartbeat를 확인했다.
구간 편집/접근/취소/미디어·앨범·기록 가져오기/시간축80검사도 통과했다.
[한정 Android QA](../qa/infant-presence-android-20261007.md)의 명령·실패·정리 기록을 따른다.

## 배포·남은 검증

- 제품0.5.18-beta/code32는 유지한다. 새 후보는 오프라인 별도 debug package이며 beta/release/preview/bundle로 만들 수 없다.
- 2026-10-07 **04:33 KST** 실제 Play 게시 개요는 code32 ‘검토 중인 변경사항’이었다. 후보가 게시되거나 테스터에게 제공됐다는 뜻이 아니다.
- S25 USB는 현재 ADB에 연결돼 있지 않다. ARM의 실제10/20초 속도·PSS·발열·취소/재시도 확인이 다음 적용 gate다.
- [추가9파일 평가](20261007-extra-babble-evaluation-results.md)의5누락을 보존한다. 새 검증source와 모델 답을 가린 사건 검수,
  대상월령/자연성·TV·화자 미확인 반례가 필요하다. 배고픔·졸림·놀이 욕구 모델을 갖췄다는 뜻이 아니다.
- ARM·독립 검토·서명/업데이트/권한 gate를 만족한 변경만 code33 이상 beta로 반영한다. 공개production/상품/실거래는 보류다.

전체 goal은 active다. 지금의 유의미한 정보 이득은 **기존 실제 누락의 한마디 회복**이며 일반 번역 정확도 승인과 구분한다.
