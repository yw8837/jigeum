# VTC1 상업 앱 배포권 문의 초안 — 미발송

대상: 공식 `MarvinLvn/voice-type-classifier` GitHub repository의 새 issue.
사용자 승인 전 게시하지 않는다. 아기 원음·개별 점수·사용자 정보·계정·토큰을 포함하지 않는다.
공개 source/weight 식별자와 사용 목적만 전달한다.

## 제목

License clarification for VTC1 checkpoint in a commercial on-device Android app

## 게시 본문

Hello, thank you for publishing VTC1.

We are evaluating VTC1 for an Android app for parents. Audio analysis would run entirely on the device; infant audio would not be sent to an external server.

Could you clarify the license for this exact checkpoint?

- Repository revision: `9731a9aad21a85d7bc660193386de94f2fb19f76`
- Checkpoint: `model/train/X.SpeakerDiarization.BBT2_LeaveOneDomainOut_paido.train/weights/0100.pt`
- SHA-256: `8514e96c8e049abd9ffc75c816b6aff51df09507b3ec7ac1c800fd366408286c`

The pyannote dependency has an MIT license, but we could not find a license covering the task checkpoint itself. Is commercial use allowed, including distribution with an APK/AAB or a separately downloaded model, conversion to ONNX/TFLite, and quantization? What attribution or other obligations apply?

We would describe its output as voice-type evidence, rather than a diagnosis or proof of an infant's needs. We will keep it out of our released app until the distribution terms are clear.

If a different small VTC checkpoint is recommended for this use and has explicit distribution terms, a link would also be appreciated.

Thank you.
