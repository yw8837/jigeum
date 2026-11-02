# 0.5.18 독립 코드 리뷰

기준 `737c0a8` → 최종 제품 소스 `a8a3c18`. code-review의 두 축을 병행했다.
읽기 전용 GPT-6.1 Sol/high `/root/visual32_standards`, `/root/visual32_spec`.
Gradle/ADB·산출물·배포 검증은 부모가 담당한다.

## Standards

최초 `525cdb4`: P2 열린 입 인정 상한이 기존 1f에서 빠졌다. 상한을 복원하고
1.2 반례 회귀 검사를 추가했다. `cb2124a` 및 최종 `a8a3c18`에서 해소 확인.
온디바이스/PCM·소리 기준/시각 cadence/권한 계약 추가 위반은 확인하지 않았다.
비차단 possible Duplicated Code1건: 웃음·입 변화 run 분리 조건의 일부 반복을 후속 정리할 수 있다.
현재 두 관찰의 완료 조건이 달라 작은 명시적 루프를 유지한다.

## Spec

최초 `525cdb4`: 지연 오디오와 PTS 공백이 crop된 PCM에 압축되므로 시각 시간창과
잘못 연결될 수 있다. 실제 decoder kept buffer PTS 확인과 제품 전달을 추가했다.
후속 `cb2124a`: 각 버퍼의 실제 PTS로 기대 시각을 재설정하면 한 sample 이하의
작은 공백/겹침이 계속 누적돼도 통과한다. 8kHz/160frames의 ±125us 반복으로
실패를 재현하고 선택 시작+누적 kept source frames 기반 global clock으로 수정했다.
최종 `a8a3c18`: 미해결 지적0/범위 확대0. 실제 모델·Android·Play 상태는 소스 리뷰로 인증하지 않는다.

## 부모의 재현

최초 실제 interpreter5검사 중4실패(시점어긋남/얼굴누락연결/다중단계누락)를 기록했다.
누적 drift는 새4검사 중1실패로 재현했다. 최종 관련 host43검사가 통과했다.
합성 관찰/시간축 검사를 아기 의도 또는 일반 모델 정확도로 보고하지 않는다.

요약: Standards 최초 hard1/최종0, 비차단 heuristic1. Spec 최초1/후속1/최종0.
