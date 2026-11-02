# 독립 두 축 검토 — 공개 음성 후속 평가

2026-10-07. 원 고정 비교 `6511377…2f3b252`, 수정 비교 `2f3b252…24be5be`.
Spec/Standards는 독립 읽기 전용 agent가 각각 source와 실행 evidence를 확인했다.
issue tracker 설정 파일은 없어 [로컬 계약](../research/20261007-public-infant-pair-evaluation-contract.md)을 사용했다.
아래 판단은 두 축을 합쳐 재순위화하지 않는다.

## Spec

원 검토의 **필수 P2 1개 / 범위 확장 0개**:
계약 P1의 완전 결과/P4의 partial·fakecomplete 거부와 달리, 완료 gate가 좌표만 있는
window를 허용했다. rawRMS/quiet/real_samples/score/support/parity/originalAST score의
존재·유한성·정합을 최종 검증하지 않았고 정상 test fixture도 점수 없는 window였다.
이는 실제 run02 수치가 잘못됐다는 증거와 구분한다.

수정 고정 `24be5be`의 `validate_case_results`는 해당 스칼라와 case 요약을 검사하며
완료 gate에서 호출한다. 정상 fixture/누락·NaN·모순·quiet-null 테스트를 보완했다.
독립 reviewer는 scalar-review02의 unit15·실제3case14창 재감사·7필수field누락거부,
원 evaluation SHA 불변·현재 validator SHA의 고정 blob 일치를 확인했다.
기존 P2 해소, **새 필수 수정0 / 범위 확장0**이다.

## Standards

원 검토의 **필수 P2 1개 / 비차단 Fowler 제안1개**:
결과 문서의 ‘publicMP3 한세트 보존’이 실제 두세트/삭제차단 상태와 달랐다.
RUNBOOK의 실제 파일·용량 확인 및 정리 기록 요구에 따라 현재 수량을 정정해야 했다.
삭제 실행이나 자동 차단 우회는 필요하지 않았다.
선택적 Duplicated Code 제안은 provenance pin 구성이 여러 곳에 반복된 점이었다.

수정 고정 `24be5be`는 현재2세트/6파일/4,380,624B·향후한세트 목표로 보고를 정정했고,
`capture_pins`로 구성을 통합했다. 새 스칼라 검증은 원 고정 SHA 검사도 유지한다.
독립 reviewer는 unit15·재감사·7누락거부·현재 validator/역사적 output SHA·전후storage를 확인했다.
기존 P2 해소, **새 필수0 / 새 Fowler 제안0**이다.

## 실제 증거와 한계

- 원 run02의 helper9개 code SHA는 고정2f3b252 blob과 일치했다. labels SHA도 고정Kotlin blob과 일치했다.
- host3case/14창/9지원창, 최대 head/NumPy 차이1.4614082e-7, runtime·process·전후storage가 일치했다.
- Android8case 모두 measured·prior_phase==phase였다. 기존승인3·대조3 보존·새public회복0이다.
- source의CC0/caption-only/evaluation-only/blocked/null 및 training/calibration/private/product/quality false를 유지했다.
- 새 코드로 과거 원음을 재추론했다고 보고하지 않는다. 후속은 기존 결과 스칼라의 읽기 전용 감사다.
- 연구앱/runtime 정리·공용AVD실행 종료/폴더보존·OMX부재를 확인했다. 중복MP3삭제는 자동정책 차단으로 남는다.
- 사건 groundtruth·실폰ARM·새beta패키징/Play배포는 미완료다. 로컬 도구 검토를 앱 일반 품질 승인으로 쓰지 않는다.

전체goal은 active이며 [QA](public-infant-followup-20261007.md)는 cleanup BLOCKED를 유지한다.
