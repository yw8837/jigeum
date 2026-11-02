# 짧은 발성을 clip 평균에서 잃는가 — 실행 전 MIL 계약

2026-10-07. [binary35 실패](20261007-infant-presence-results.md)에 이어 같은 고정 공개35 자료의
**학습 pooling 하나**를 바꿔 비교한다. 제품 변경이나 새 threshold 결정은 아니다.

기존 binary head는 한 clip의 nonquiet창 특징을 평균해 학습했다. 짧은 아기 소리가 성인·주변소리
사이에 있으면 평균에서 약해질 수 있다. 이번은 각975ms 특징을 L2 normalize한 bag을 사용한다.
시간별 logits의 actual-sample 가중 log-mean-exp, 온도1.0을 clip logit로 삼고 weak binaryBCE로 학습한다.
positive clip의 모든 창을 아기로 라벨링하지 않는다. negative bag도 같은 집계라 일부 큰 오인에 비용이 든다.
이 가설은 speech separation, 실제화자 식별, 아기 욕구·번역 정답을 의미하지 않는다.

## 사전 고정

- 정확35source snapshot SHA `27966aa9265da239489e7351959c01399ed3a0aed6428149babc2cb127fad4b9`.
- 기존249창 archive SHA `5dabe7acace700d68ae7bcc3db83d40cbe6df4fbb0b26c7a65756cbe61c2b240` 재사용.
  학습용 특징의 새로운 원음 취득/encoder 추론/새representation 없음.
  평가3+33은 기존평가 runner로 원음encoder를 실행한다. 초기 문구의 범위를 이처럼 명확히 한다.
- 훈련21clip/18source, validation14clip/11source 그대로. 영아 train4source, 영아validation1source 한계 유지.
- quiet RMS<.003 제외, 실제 sample로만 가중. silence/NaN/잘못된 순서·shape는 거부한다.
- 1024→1/1025param, zero초기화/Adam .03/300step, bias 무규제, L2 .0001/.001/.01.
  class/source/clip 균등가중, 18train source leave-one-out binaryNLL source평균 최소/동률 강한L2.
  선택 후21train으로 단하나fit·head/selection 저장 후 validation bag에 접근.
- 창 support p≥.8·margin≥.4 그대로. clip bagscore와 단일창·인접2창·시간을 별도로 보고한다.
  score는 calibrated 정답확률이 아니다. 사용자 원음·특징은 평가에만 사용한다.
- Job180초/2GiB/offline. 기존 raw/private/held 데이터 학습·정규화·calibration·augmentation 금지.

## 판정과 QA

첫 누락·희소 발성의 support와 신규성인비명384213/웃음42841, 기존public33의성인/음악/잡음 대조를 함께 본다.
첫 누락의 지속적 회복과 오인 방지가 확인되지 않으면 제품 미탑재다. 같은private원음을 다시학습하거나
선택 threshold/crop을 사후 변경하지 않는다. 자료는개발/weak라벨이며일반정확도로보고하지않는다.

QA는 bag시간/quiet/domain, logmeanexp 극값과 실제sample가중,
analyticgradient의중앙차분비교, 학습source CV분리/NaN, 실제CLI4거부/기존head보존,
각process/completion/runnerSHA·자손0, 별도state lifecycle를 포함한다.
최대5cycle/동일실패3회중단. 연구도구QA와품질실패를분리한다.
종료중복자료정리/독립Spec·Standards검토/Git푸시를이어가고전체goal은active로유지한다.
