# VTC1 앱 전처리·무음 제외 경로 — 실행 전 계약

2026-10-07. 현재 제품code32는 변경하지 않는다. 원래 floatgraph의 무음 수치 실패를 그대로 남긴다.
아래는 원래 rawgraph와 별개인 **전처리와 무음 제외를 포함한 연구 경로**다.
원래 무음의 임의 점수를 제품에서 쓰거나 기존 수치 gate를 완화하지 않는다.
weight 상업 포함·변형 재배포권은 여전히 미확정이다.

## 왜 이 비교를 하는가

첫 비교의 원음 VTC 아이 단서가 앱의 DC 제거/80Hz 필터 뒤에도 유지되는지 아직 모른다.
원래 모델의 InstanceNorm은 상수 입력의 극소 부동소수점 차이를 증폭한다.
앱의 기존 무음 조건인 strongest one-second RMS `<.003`은 전체 입력에서 적용되지만,
이 연구 경로에서는 **각 1~2초 classifier 창**에도 적용한다. 창 위치·길이는 바꾸지 않는다.
이는 소리 크기의 검사이며 아기 목소리 검사나 화자 분리가 아니다.

## 미리 고정한 계산

- source/weight/runtime/native slide/2초/.5hop/end-aligned/Binarize/onset/min_on/off는 기존 pin 그대로다.
- 전체 선택 구간에 한 번 DC/80Hz Butterworth 필터를 적용한다. gain/noise gate/발성별 필터/음성 파일 출력 없음.
  `AudioCleanup.kt`의 식을 Python으로 이식한다. 이번 새 이식의 Android byte parity는 별도 미검증이다.
- 원래 forward에 들어가는 실제 창의 strongest one-second RMS `<.003`이면 Torch와 ORT 모두
  그 창의 `(1,(samples-325)//27+1,5)`를 zero support로 반환하고 classifier를 호출하지 않는다.
  판별이 안 된 입력을 원래 모델의 긍정으로 대체하지 않는다. silent subwindow에도 적용한다.
- RMS 조건을 통과한 창만 기존 graphSHA/float32/CPU4threads로 비교한다.
  원래 namespace의 slide/crop/균등평균/coverage를 유지하고 모든5열 **maxabs<=5e-4**를 유지한다.
  원래 무음 rawgraph 결과가 일치했다고 보고하지 않는다.
- invalid length/dtype/nonfinite/out-of-range를 거부하고 graph/원음 SHA를 전후 확인한다.
  Python 네트워크 차단, ignored 새 출력, Job180초/3GiB/자손0을 요구한다.

## 최초 실행 순서·표본 제한

1. 합성 무음/미세잡음/문턱 아래 tone/문턱 위 tone/DC를 먼저 비교한다.
   필터·RMS·skip 확인을 위해 입력을 고정하며 아기 정확도 자료로 세지 않는다.
2. 기존 retained3구간/2원본을 한 번 비교한다. 누락2구간은 같은 원본의 약2.83초 overlap이다.
   원음 vs 새 필터/guard의 아이 active duration은 기존 기록 사건과 비교한다.
   새 독립 정답이나 heldout으로 바꾸지 않는다. 결과가 사라지면 이 전처리 후보를 보류한다.
3. 유효 신호와 수치 계약이 유지되면 기존 공개33조건에 같은 경로를 처음 실행한다.
   음악·성인·무음의 기존 오인이 사라진다고 가정하지 않는다. 이번에는 음악 veto 문턱을 새로 고르지 않는다.

이 비교의 성공은 **이식 가능성 및 전처리 후 단서 보존**이다. 피아노 오인·새 영아/TV 평가·weight권리·ARM/취소·
Android 통합은 별도다. 사용자 원음을 학습에 쓰거나 외부로 전송하지 않는다.
실행 후 결과·실패와 source 변경을 구분해 기록한다. 제품 탑재·새 beta·일반 정확도 향상으로 확대하지 않는다.
