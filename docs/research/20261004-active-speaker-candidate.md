# TalkNet-ASD를 발성 주체 보조 검증 후보로 볼 수 있는가

조사일: 2026-10-04 (KST) · 최초 범위: 저자 공식 TalkNet-ASD repo와 그 repo가 연결하는 arXiv 논문. 이후 별도 실행자가 checkpoint 검사·CPU 합성 추론까지 진행했으며 아래 후속 결과와 연결했다. 최초 조사자는 모델을 실행하거나 원본/데모 영상을 열지 않았다.

## 결론

**TalkNet-ASD는 입 움직임과 소리의 시간 동시성을 점수화하는 active-speaker detector다. 아기 발성 분류기, 감정·욕구 판정기, 문장 생성기는 아니다.** 따라서 얼굴이 보인다는 사실만으로 옹알이 소유자를 확정하는 현재 한계를 보완할 수 있는 연구 아이디어이긴 하지만, 현재 제품에 넣거나 아기 옹알이에 맞는 검증 모델로 부를 근거는 없다.

이 후보를 **원본 미디어를 기기 밖으로 보내지 않는 내부 연구 비교에 검토하는 것은 계속 가능**하다. 기존에 승인된 원본 미디어의 로컬 비교 범위는 유효하며, 새 동의 절차를 추가하지 않는다. 영아 발성에 대한 유효성은 아직 입증되지 않았다. 공개 weight 확보·제한 실행은 별도 후속 연구로 진행했으며, 사용자 원본의 외부 전송 금지는 유지한다.

**상업 제품 채택·재배포는 별도 미해결**이다. repo의 MIT는 코드 권리이며 weight 및 학습자료 권리를 대신하지 않는다. 저자 repo는 pretrained TalkSet checkpoint의 다운로드 경로를 공개하지만 별도 weight license를 기재하지 않는다. TalkSet은 VoxCeleb2·LRS3 기반 파생 자료이며 저자는 원자료를 업로드하지 않는다고 밝힌다. 제품 채택 전에는 weight·학습자료 권리와 배포 조건을 확인해야 한다. Android/ONNX 공식 경로도 없다.

입력·checkpoint·CPU 실행 경로가 검증되면, 기존에 로컬 연구 사용을 승인받은 자료에서 동일 얼굴/동일 시점 음성과 시간 어긋난 음성을 비교해 **얼굴별 active-speaker logit이 동기 차이를 구분하는지** 시험할 수 있다. 영아 도메인과 상용 재배포 미확인을 내부 연구의 자동 금지로 확대하지 않는다. 이 검사는 주체 정합성 가능성을 시험하며 옹알이 종류·마음 해석·Android 통합은 별도다.

## 공식 source에서 확인한 입출력과 학습 범위

- 논문은 ASD를 화면에 보이는 한 명 이상의 사람 중 **누가 말하고 있는지** 찾는 문제로 정의한다. 짧은 특징과 긴 시간 문맥, audio-visual cross-attention을 결합한다. 논문 데이터 평가는 AVA-ActiveSpeaker와 Columbia ASD이며 영아 발성 데이터라는 기술은 없다.
- 저자 demo는 얼굴을224×224로 resize한 뒤 grayscale 중앙112×112를 모델에 넣는다. 오디오는16kHz 정수 PCM에서13차MFCC,25ms window/10ms hop으로 바꾸고 얼굴 영상은25fps다. 시간 길이를 맞춘 뒤1~6초 crop 결과를 평균한다. 선택구간 전체의 의미를 생성하는 모델은 아니다. 정확한 crop·정규화·tail 계약은 [소스 검토](20261004-talknet-runtime-review.md)를 따른다.
- 출력은 얼굴 track마다 시점별 2-class active-speaker head의 **class-1 raw score/logit**이다. demo가 여러 window 결과를 평균하고 얼굴별 시계열로 시각화한다. 대사 transcript, 아기 소리 종류, 같은 아기라는 신원, 욕구·감정은 출력하지 않는다.
- 저자 repo는 TalkSet을 VoxCeleb2와 LRS3를 기반으로 생성했다고 설명하고, TAudio/FAudio 등 음성/입 움직임 일치·불일치 구간을 구성한다. 저자는 원 데이터셋을 직접 만든 것이 아니므로 TalkSet을 업로드하지 않으며 파일 목록과 label만 제공한다고 쓴다. 이 학습 설명만으로 infant babble coverage를 주장할 수 없다.

## checkpoint·권리·기기 경로

| 항목 | 확인 |
|---|---|
| 모델 존재 | `demoTalkNet.py`가 기본 pretrained TalkSet `.model`을 지정하고, 저자 Google Drive ID `1AbN9fCf9IexMxEKXLQY2KYBlb-IhSEea`에서 받도록 한다. 후속 연구에서 이 파일을 받았다. AVA checkpoint는 다운로드하지 않았다. |
| 파일 크기·revision | 후속 다운로드63,227,827bytes, 로컬SHA는 아래 기록. 공개 metadata에 upstream digest/불변 revision은 없어 이를 검증했다고 주장하지 않는다. |
| 권리 | 고정 repo LICENSE는 코드에 MIT를 부여한다. pretrained `.model`의 별도 license는 repo/README에 없다. AVA/TalkSet 학습자료 이용권은 각 원자료/가공자료 terms로 별도 확인해야 하며 이 조사로 상업 weight 사용을 승인할 수 없다. |
| head·CPU 경로 | `lossAV.FC = Linear(256,2)`. **`labels=None`은class1 raw logit이며softmax가 아니다.** 학습 wrapper를 우회한CPU컨테이너에서487개state전체와공식no-label계산을 검증했다. [실측](20261004-talknet-probe.md). |
| 실행 | README는Python3.7.9/CUDA환경을 제시한다. 후속torch2.13 CPU합성1초는통과했으나 원환경수치parity는미확정이다. 공식Android/ONNX배포경로는없다. |
| 변환 가능성 | 네트워크 구성은 PyTorch CNN/TCN/attention이어서 ONNX 변환을 별도 탐색할 여지는 있지만, 고정 input contract·opset·정확도 비교·Android delegate 근거는 공개되어 있지 않다. “ONNX/Android 실행 가능”의 증거로 취급하지 않는다. |

후속 공개 다운로드에서63,227,827bytes와 로컬SHA-256
`d985ddd07dca08864a28337726f5b7d91b6426bebc7327f0e9b21cf9ff1cc937`을 확인했다.
저자가 공개한 digest/불변 revision을 검증한 것은 아니다. 제한된 CPU 검사로487개tensor의finite/구조와
공식 소스 컨테이너 전체strict복원을 확인했다. 1초 합성 feature 추론도 finite `[25,2]`logits를 반환했고
공식class1 raw경로 일치·입력오류10개 거부를 통과했다. forward0.222초는 PC에서의 단회 합성 실측이다.

이 후속 결과에도 영아 도메인 성능은 미확인이다. 다음은 정확한 전처리와 실제 로컬AV의 동기/어긋남 비교다.
상업 제품 채택·재배포 조건·Android 실행·아기말 생성은 별도 미해결이다. 원본 미디어 외부 전송은 하지 않는다.

## 고정 1차 출처

1. 저자 [TalkNet-ASD source commit `6d6821479af485e251c4991487e40573b42181b4`](https://github.com/TaoRuijie/TalkNet-ASD/tree/6d6821479af485e251c4991487e40573b42181b4): [README](https://github.com/TaoRuijie/TalkNet-ASD/blob/6d6821479af485e251c4991487e40573b42181b4/README.md), [demo input/output](https://github.com/TaoRuijie/TalkNet-ASD/blob/6d6821479af485e251c4991487e40573b42181b4/demoTalkNet.py), [model](https://github.com/TaoRuijie/TalkNet-ASD/blob/6d6821479af485e251c4991487e40573b42181b4/model/talkNetModel.py), [training](https://github.com/TaoRuijie/TalkNet-ASD/blob/6d6821479af485e251c4991487e40573b42181b4/trainTalkNet.py), [MIT LICENSE](https://github.com/TaoRuijie/TalkNet-ASD/blob/6d6821479af485e251c4991487e40573b42181b4/LICENSE.md). Public model IDs are present in `demoTalkNet.py` and `utils/tools.py`.
2. 저자 논문 [arXiv v2](https://arxiv.org/abs/2107.06592v2), *Is Someone Speaking? Exploring Long-term Temporal Features for Audio-visual Active Speaker Detection*, ACM Multimedia 2021. Abstract, task definition, architecture family, code link, and AVA/Columbia evaluation.

`work/active-speaker-source-review/metadata.json`는 위 공개 경로의 작은 식별/권리/크기 상태 메모다. 어떤 weight/model/media도 포함하지 않는다.
