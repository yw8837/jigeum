# 소형 분류·잡음 제거 실행 비교와 제품 개선 결정

2026-10-05. 조사에서 끝내지 말고 실제 개선을 적용하라는 사용자 지시에 따른 후속 실행이다.
기준은 게시된 0.5.12-beta/code26이다. 로컬 개별 점수·아기 자료는 `work/model-specialization-20261005/`에만 있다.

## EfficientAT: 실행 성공, 대체 모델은 보류

[고정 계약](20261005-efficientat-execution-contract.md)의 mn04(가중치 약4.07MB, 983,599 parameters)를
strict/weights_only로 읽어 공개·합성33개와 기존 약국10/20초를 실제 추론했다.
원래16kHz PCM 전체를32kHz로 resample하고10초창/5초hop/끝정렬을 사용했다.
이 변환이 이미 없는 고주파를 복원한 것은 아니다.

- 공개33개 worker4.434초, 모델 추론 합계 약1.022초, 최대 Job committed 약1.04GiB.
- 일부 공개 옹알이의 점수는 높았지만 다른 사례는 낮았으며, **현재 AST가 회복한 약국 옹알이를 강하게 구분하지 못했다.**
- PC 속도·tag 점수를 Android 속도나 독립 정확도로 해석하지 않는다. 제품 교체 근거가 부족하다.
- 첫 실행은 manifest의 ceil(ms)와 runner의 floor(ms) 불일치로 모델 전에 실패했다. 입력 계약을 수정하고 관련 host5개를 통과한 뒤 위 결과를 얻었다.

## GTCRN: 주변 소리와 함께 아기 발성 단서도 약해졌다

[고정 실행 계약](20261005-gtcrn-execution-contract.md)의 raw ONNX352,084bytes를 내려받아 SHA256
`f648b02f2d7ff96ebcb0eec2219688a08ed12fe7e3d50f248605a90eba8cad17`을 확인했다.
ORT1.24.3, frame cache3개,16kHz FFT512/hop256/sqrt-periodic-Hann의 분석·합성 경로를 구현했다.
cache는 clip마다 초기화하고 frame 사이에 전달한다. 출력 길이·원점은 유지하며 원본은 수정하지 않는다.

- 공개·합성33개(366.299초)의 잡음 제거20.404초, AST 원본/처리본 비교56.434초.
- 전체 worker78.579초, 최대 Job committed 약1.33GiB, 정상 종료/자손 정리 확인.
- 약국10/20초2조건의 잡음 제거1.719초, AST쌍 비교3.867초. 독립 원본2개가 아니다.
- 약국의 기존 AST 옹알이 양성 단서가 처리 후 양성 기준 아래로 낮아졌다. 일부 공개 옹알이·울음도 크게 약해졌다.
- 처리 소리의 에너지가 낮아진 사실을 잡음 제거 품질 향상으로 세지 않는다. **기본 전처리로 채택하지 않는다.**

host5개는 STFT 공식 torch 설정 대조, pass-through 양끝·부분hop 복원, 원본 불변,
cache 초기화/전달, 비유한 입력·출력 거부를 검사했다. 초기 복원기는 마지막 부분hop에
내부 COLA=1 조건을 잘못 요구했다. 실제 NOLA와 window² 정규화로 수정했다.
float32 spectrum의 끝부분 반올림 오차 최대 약3e-6에 대해 복원 한계를 PCM16 한 양자(1/32768)로 명시했다.
이는 실제 denoiser의 지각 품질 기준이 아니다.

이번 실행은 **AST쌍**이다. YAMNet·Android 처리음을 통합한 제품 품질 통과가 아니다.
이미 기존 양성을 약화시키는 반례가 있어 제품 기본 교체를 위한 추가 Android 시험은 진행하지 않았다.

## 제품에서 찾은 별도 DSP 결함과 수정

위 모델 후보를 검증하며 `VideoAudioDecoder`의 기존 선형 resample에 anti-alias 저역통과 필터가
없음을 확인했다.48kHz→16kHz에서12kHz 성분이4kHz로 접힌다.44.1kHz Android RED의
동일 합성 실험도 원래 진폭 대비 약.788의 잘못된 대역 성분을 남겼다.
이것은 실제 변환 결함이며 약국 누락 전체의 원인을 입증한 것은 아니다.

새 `BoundedPcmResampler`는 bounded streaming ring과 Blackman windowed-sinc로 줄이기 전 대역을
제한한다.16kHz PCM은 그대로 전달하고, 선택 구간 원점·EOS ceil 길이·stereo 평균을 보존한다.
별도 신경망·음성 gate·gain 증폭·모델 threshold 변경은 없다. Infinity가 clamp로 정상값이 되던
검증 순서도 수정했다. [제품 QA](../qa/audio-resampling-0.5.13/README.md)에서 실제 결과·패키징·배포 상태를 확인한다.

## Gemma 실행량 개선은 별도

[구성 감사](20261005-gemma-component-feasibility.md)를 따라 별도 Android 실행기에 visual budget
0/70/140/280 인자를 추가했다. 원음·장면 수·PTS는 유지한다. host17개와 debug build는 통과했다.
실제 S25 연결이 없어 새 budget의 속도·발열·의미 품질은 미측정이며 Gemma는 앱에 넣지 않는다.
이 연결 문제 때문에 독립적인 앱 음성 변환 수정을 멈추지 않는다.
