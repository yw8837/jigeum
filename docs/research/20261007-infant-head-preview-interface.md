# 작은 자체 head 준비 — 공개 preview·embedding 인터페이스 사전 계약

2026-10-07. [공식 권리·source 조사](20261007-permissive-infant-voice-next.md)에 이어
확인된 공개 preview3개만 소량 취득해 코덱/모델 입력과 embedding을 실제로 검증한다.
이 단계는 학습도, 라벨 검수 완료도, 새 아기 정확도 평가도 아니다.

- 공개 ID377237/113220/18274만 사용한다. source-audit bytes445,428/SHA256
  `592099e29c0f5e15289f8d31df938ca696a06c0031cd12713fd4edbd8078c9c6`를 검사한다.
  공식 페이지의 CDN URL, 개별 CC0/BY license/attribution을 snapshot에 유지한다.
- HTTPS `cdn.freesound.org`의 고정 mp3 URL만 최대2MiB/file 취득한다. 총3개이며 사이트 크롤링·로그인 없음.
  파일 hash/bytes를 새로 기록한다. 이는 원래 FSD PCM의 hash 일치 검증이 아니다.
- 각 파일은 local FFmpeg로 mono16kHz float32에 decode한다. 최대30초/2MiB 출력, timeout20초다.
  원음은 lossy preview이며 새로운 사실 정답으로 바꾸지 않는다. 코드 실행·외부 변환 없음.
- 제품에 이미 들어간 고정 YAMNet SHA `4d8b4a53282dc83ef04e3e7dbc4fbc98082e34e44ed798e16c3a0cdd4c584faf`의
  내부1024 embedding tensor115를 추가 output으로 노출한 TFLite를 **메모리 안에서만** 만든다.
  기존521 score 계산/weight는 유지하고 원래 모델의 동일 창 출력과 모든521열 maxabs<=1e-6를 비교한다.
  별도 모델 다운로드·제품asset 교체·새graph 파일 없음. 내부tensor이름/shape/dtype를 검사한다.
- DC/80Hz 필터를 전체 실제 PCM에 한 번 적용한다. 975ms 기본 창, 마지막 창은 우측zero padding,
  실제sample수·시간을 함께 기록한다. 1초 미만 공개sample도 보존하고 training label을 붙이지 않는다.
- embedding은 ignored 로컬 npz에만 저장한다. 사용자 원본/feature는 이번 단계에 없다.
  source role는 검수 전 provisional이며 raw 라벨로 window별 강한 정답을 만들지 않는다.
- LiteRT runtime/모델·mp3·runner SHA, shape/finite/parity, decode 전후 파일불변,
  Job180초/2GiB/자손0을 기록한다. 취득 경로와 local offline feature worker를 구분한다.

성공하면 기존 encoder에 작은 head를 붙일 **실행 가능한 인터페이스**가 준비되는 것이다.
정확도·속도·발열·Android 모델 탑재/배포로 보고하지 않는다. negative source와
실제 소리/혼합/사건 검수 및 source 분리 학습·평가가 이후 단계다.

## 첫 실행에서 확인한 실제 embedding 형식 — 재실행 전 수정

preview3개(총130,565B)를 취득했지만 첫 interface01은 `Exposed tensor contract invalid`로 실패했다.
기존 모델의 내부embedding이 float32라고 가정한 하네스 오류다. 실제 고정tensor는
**int8/scale0.022328350692987442/zero point-128**이다. 기존521 score output은 float32다.
모델·threshold를 바꾸지 않고 고정 metadata를 검사한 뒤 `(q-zero)*scale`로 float32를 복원한다.
잘못된 dtype/shape/scale/zero를 거부하며, 기존521 score parity gate1e-6는 유지한다.
처음 실패는 아기 소리 인식 실패나 제품 결함이 아니며 원문 로그/exit1/자손0을 보존한다.
2초보다 짧은 공개자료는 실제 길이의 평균으로 필터하고 우측padding은 모델 입력에만 사용한다.

interface02는 다음 공개clip의 MP3 decode/resample full-scale overshoot에서 실패했다.
유한성은 유지됐지만 `abs(pcm)>1` 입력 범위를 벗어났다. 음량 증폭/재정규화는 하지 않는다.
interface03 전에 nominal decoder범위[-1,1] clipping을 명시하고 원래 decode의 SHA/peak와
실제 clamped sample 수·처리 후 SHA를 따로 기록하도록 수정했다. 원본 mp3는 바꾸지 않는다.
입력 변환을 숨기거나 원래 FSD PCM과 같은 자료라고 말하지 않는다. 모델/score parity gate는 그대로다.

## 실제 interface03 — 입력·기존 score 일치 확인

고정 source파일 SHA `baad651df336b91dd2c3e0270d5a5f009cfdcc24351ac0b4e6107eca6d58e845`.
Python3.12.14/NumPy2.5.3/LiteRT2.2.0/flatbuffers25.12.19에서3개publicpreview·총16창을 실행했다.
추가 embedding을 노출한 모든521 score는 원래graph와 **최대차이0**이었다.
embedding은16×1024 float32이며 로컬압축파일은4,166B다. 학습label·아기정확도 정답은 붙이지 않았다.

| public ID | 실제16kHz sample수 | 975ms 창수 | decode clipping | 기존521 score maxdiff |
|---|---:|---:|---:|---:|
| 377237 | 212,536 | 14 | 0 | 0 |
| 113220 | 14,582 | 1 | 0 | 0 |
| 18274 | 9,336 | 1 | 8sample, rawpeak1.21599 | 0 |

마지막 두 clip의우측padding수1,018/6,264는 실제표본과 구분해 보존했다. 가짜1초 정답을 만들지 않는다.
이run은 exit0/.656초/peak728,182,784B/2GiB/자손0이다. graph4,120,480B는 memory에만있고
새encoder weight/model파일을 디스크에 추가하지 않았다. mp3원본과 제품모델 SHA가 유지됐다.
첫dtype/두번째codec실패는원문로그와별도process로보존한다. acquisition의runner SHA는
당시feature수정전source이고 interface03 SHA와구분한다. 취득을최종feature runner의실행증거로소급하지않는다.

순수합성 단위4/exit0: int8극값·zero복원, 잘못된scale/zero/dtype/shape거부,
subsecond실제평균의DC제거·원본보존, invalid짧은PCM거부. model/audio 학습·정확도 검증으로 합산하지 않는다.
시각/욕구모델이나 Android교체는없다. 다음은 잠정train58/출처보류27자료의 실제취득·권리snapshot·
소리/혼합 검수 및 제한된 head 비교다. 독립test라고확정하지않는다.

증거: ignored `work/infant-head-20261007/preview-subset01/acquisition.json`,
`interface01/`, `interface02/`, `interface03/{report.json,process.json,features.log,embeddings-public.npz}`,
`unit-interface.log`. 공개media/특징도Git에올리지않는다.
