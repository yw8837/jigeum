# 로컬 음성 연구 도구 — 두 축 코드 리뷰

2026-10-06. 시작점 `d4f3e1874f4aa9d11b6b45cf042277c35121d091`, 최초검토 `3a13755`, 수정검토 `5b47147`.
두 검토자는 GPT-6.1 Sol/high로 분리 실행했다. 요구사항은 현재 사용자 요청과
AST 짧은창/PretrainedSED 실행 계약이다. issue tracker 설정 파일이 없으므로 실제 로컬 spec을 사용했다.
원음 열람·모델 추론·제품 수정·삭제 권한은 검토자에게 부여하지 않았다.

## Standards

문서 규칙 위반0, 비차단 Duplicated Code1이다. AST와 PretrainedSED의 socket 차단/offline env 설정이
각 runner에 중복된다. 공통 함수는 유지보수 제안이며 현재 전송 결함이나 필수 수정으로 판단하지 않았다.
온디바이스·ignored 출력·Job 상한·exit/cleanup 확인·SHA/runtime 고정·safe tensor load·strict head·
입력형식/유한성/경로 검증·이전 output 재사용 거부를 확인했다.
삭제 정책 차단을 QA에서 공개하고, 프로세스 QA와 아기 품질 검증을 구분한 점도 문서 규칙에 맞았다.

## Spec

최초 P2 한 건: AST2초와4초 variant가 각각 자기 창+기존10초 창의 충돌만 검사해,
계약의 **모든 새 짧은 창** 전역보류를 충족하지 못했다. 2초 Babbling=.8/4초 TV=.3이면
2초 양성이 남는 반례였다. 실제 기존3구간의 양성 자체는 없어 초기 연구 결론에는 영향이 없었다.

`5b47147`에서 두 variant 측정 후 `summarize_variants`로 기존 창+모든 짧은창을 각 variant에
공유하도록 수정했다. 반례를 추가해 최종고유7검사 통과했고, 보존full scores의 요약을 실제재생해
3구간/변경0/추가추론0을 확인했다. Spec 재검토는 **P2해소·새 필수지적0**이었다.
검토자는 원본 scores와 재생 기록의 SHA 일치를 확인했고 모델을 다시 실행하지 않았다.

PretrainedSED의 실제 고정source14파일과 weight 크기/SHA도 일치했다. 공식전처리·447-way head·
10초chunk·right padding·실제길이crop을 확인했다. 새로운 정확도/제품완료·정리완료 과장은 찾지 못했다.

## 집계

Standards: 필수0/비차단1. Spec: 최초필수1→수정후미해결0. 축별 결과를 서로 대신하지 않는다.
이는 연구 코드 검토다. 독립 영아 정답·일반 인식률·실폰 속도·새 베타의 품질 승인을 입증하지 않는다.
정리 차단은 [연구 QA](audio-screening-20261006.md)에 별도로 남아 있고 활성 개선 목표는 계속 진행한다.

## 후속 — PANNs固定 비교

별도고정diff `3b0e1ad...e51fde5`를같은두축에분리검토했다. Standards필수0/휴리스틱0,
Spec필수0이었다. source/weight/frontend/runtimepin·byte-identical계수·normwise수치gate·
safe load/527head·반례입력계약·Job정리·36입력무결성/실제3구간구분·제품미채택을확인했다.
Spec은실제validation/retained메타데이터의module digest/출력head/자원·자손0과문서수치일치를확인했고,
원음/개별scores를읽거나추론하지않았다. Standards는고정source와문서계약을검토했다.
새아기인식정답/제품교체/실폰성능승인으로보고하지않는다.
