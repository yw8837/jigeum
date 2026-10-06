# UltraQA — VTC1 전처리·무음 제외 연구 경로

2026-10-07. [사전 계약](../research/20261007-vtc1-guarded-contract.md).
목표는 원래 floatgraph 승인과 구별된 전처리/무음 제외 경로의 실행 검증이다.
일반 아기 정확도·Android·상업 사용권·제품 배포 검증으로 합산하지 않는다.
소스/원본 불변, 네트워크 없음, Job180초/3GiB와 모든5열5e-4 유지가 안전 경계다.

## 실행 전 시나리오 행렬

| ID | 의도/입력 | 경로 | 기대 신호 | 실제 결과/증거 | 정리 |
|---|---|---|---|---|---|
| U1 | 길이/타입/shape/NaN/Inf/범위 오류 | 단위 invalid PCM | 추론 전 거부 | 6개 단위 전체 exit0; 상세는 아래 갱신 | 파일 생성 없음 |
| U2 | DC와 긴 무음 속 짧은 소리 | 필터/RMS 불변 검사 | DC 제거/원본 보존/1초 burst 희석 없음 | 위 단위에 포함 | 메모리 fixture |
| U3 | quiet 창과 invalid classifier 출력 | guarded forward | quiet classifier0/zero support, NaN 거부 | 위 단위에 포함 | 메모리 fixture |
| N1 | 고정 합성5조건 | 실제 Torch/ORT | gate 내/quiet skip | 실행 대기 | 새graph/PCM 파일 없음 |
| N2 | 보존3구간/2원본 | 실제 native slide | 원래 좌표/전열 parity, 아이 단서 보존 여부 측정 | 실행 대기 | 원음 보존 |
| N3 | 공개33조건 | 신호 유지 뒤 처음 실행 | 동등성 측정/오인 유지 여부 기록 | N2 이후 조건부 | 원음 보존 |
| H1 | 손상 graph bytes | 실제 CLI | nonzero/성공 report 없음/자손0 | 실행 대기 | 신규 3B file 개별 삭제 |
| H2 | 기존 output 재사용 | 실제 CLI | nonzero/기존 결과 불변 | 실행 대기 | 기존 증거 유지 |
| H3 | 거짓 성공/원래 rawgraph 실패를 감추기 | 메타데이터·독립 검토 | gate/raw_float_graph_accepted=false/품질false 유지 | 실행 대기 | 보고 범위 검토 |

CLI는 새ignored output에만 실행한다. 동작 문제가 발견되면 최대5 cycle,
같은 실패3회 시 그 경로의 반복을 중단하고 근본 원인과 별도 다음 후보를 진행한다.
취소/시간초과/Job자손 정리는 기존 bounded supervisor10개 검사를 근거로 구분한다.
이번 연구의 취소 검사를 Android 앱 취소 검증이라고 표시하지 않는다.

## 현재 단위 실행

`python -B -m unittest discover -s tools/research -p test_vtc1_guarded_probe.py -v`:
고유6/exit0. PCM domain 거부, DC 제거/불변, burst 희석 방지, 실제 모든 offset RMS와 일치,
quiet classifier 미호출, nonquiet NaN 출력 거부를 확인했다. 실모델/원음 검증은 별도다.

현재 상태: **진행 중**. 전체 목표는 active다.
