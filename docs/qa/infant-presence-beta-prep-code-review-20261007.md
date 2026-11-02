# 독립 코드 검토 — 영아 발성 보완 beta 적용 준비

검토일 2026-10-07. `code-review` 두 축을 서로 다른 기존 reviewer에 병렬로 배정했다.
고정 범위는 `a4fcf49...a1846fc`, 후속은 `a1846fc...acb0224`다.
요구사항은 [사전 계약](../research/20261007-infant-presence-beta-prep-contract.md),
규칙은 AGENTS/RUNBOOK/HANDOFF를 적용했다. 검토자는 코드·문서·안전 JSON/로그만 읽었으며
기기/원음/특징/키/모델 body·빌드·추론·학습·Git 변경은 수행하지 않았다.

## Standards

첫 범위 필수 지적 **1건(P2)**, Fowler 제안0.
RUNBOOK246이 요구한 이번 실험의 시작 작업폴더/C 여유 관측을 기록하지 못했다.
실행자 확인과 실제 evidence 목록이 일치하며, 과거/중간/종료값으로 시작값을 소급하면 안 된다.

후속의 **보고 정정은 충족**, 새 필수0·새 제안0이다. QA에 누락을 명시하고 상태를 BLOCKED로
정정했으며 실제 종료 snapshot29파일/34,560B/C68,357,656,576B를 보존했다.
다음 ARM runner는 동결 사본 만들기 전과 제거 후의 소유 run 폴더/드라이브 용량을 기록한다.
과거 시작 관측은 여전히 미복원이며 후속 개선으로 해당 과거 요건을 소급 통과했다고 하지 않는다.

beta 옵션 의존성/연구 혼합/variant/code/proof 거부, shared verifier, bounded Job 자손 정리,
기존 제품 설치 보존과 candidate 사본 제거, mock/에뮬레이터/실폰 구분은 준수했다.
실제 Gradle10조건·고유25unit의 집계도 안전 증거와 일치했다.

## Spec

첫 범위 필수 **0**, 단순 제안0. 후속 새 필수0·범위 확장0.

- 최종 beta asset 병합과 공식 builder가 같은 ARM verifier를 사용한다. 캐시된 pass로 건너뛰지 않는다.
- 신규beta만 presence=true, 기존 ASTbeta는false, 정식 release의 AST/테스트 권한은false다.
- exact APK/test/model pin, 실제ARM/runtime metadata 일치·freshness·완전창·시간/PSS/열,
  실제 취소/재개 terminal 로그와 test identity, production snapshot과 정리를 검사한다.
- 연구 package·동결 실행 사본을 별도로 다루고 원 제품/보존 final을 변경하지 않는다.
- 후속 storage 관측은 소유 작업폴더만 읽고 오류를 fail로 보고한다. 실행 부족을 과거 성공으로 꾸미지 않는다.

## 후속 실행 증거 확인

`snapshot-runner-15.log`는 2026-10-06 20:15:09 UTC의 별도 출력이다.
두 reviewer가 직접 **15ok /0fail /0.086초 /OK**를 읽었다. 신규 snapshot assertions가 있는
candidate simulated-route를 포함한다. 이전 `final-runner.log`를 새 실행 증거로 재사용하지 않았다.
현재 runner/test는 줄바꿈 정규화 후 고정 `acb0224` source와 일치한다.
로그 자체에 실행 시점 source SHA는 없어 이를 cryptographic byte attestation으로 보고하지 않는다.

**요약:** Standards 최초P2 보고 정정 충족/과거 시작값 미복원·후속 새 지적0;
Spec 최초0/후속0. 실제ARM·새 서명 패키지·업데이트/Play 배포는 별도 미완료다.
