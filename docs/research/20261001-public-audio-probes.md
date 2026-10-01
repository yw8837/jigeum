# 공개 음성의 실제 모델 확인 — 0.5

2026-10-01. YAMNet·현재 EvidenceInterpreter를 API35 x86_64 에뮬레이터에서 실행했다. **성능 평가셋이나 아기 의도 정확도 시험이 아니다.** 배포자의 사건 설명을 참고한 양성 입력 점검 두 건이다. 태어난 지 얼마 안 된 아기와9개월 아기를 같은 대표 표본으로 세지 않는다. 원본 미디어는 로컬 QA 폴더에만 있고 앱/저장소/학습 자료에는 넣지 않았다.

## 출처·권리

| 소리 | 출처/저작자 | 라이선스 | 실제 사용 |
|---|---|---|---|
| 신생아 울음 | [Crying newborn baby](https://freesound.org/people/the_yura/sounds/211528/), the_yura | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) | [공개 MP3 preview](https://cdn.freesound.org/previews/211/211528_2451161-hq.mp3)의 처음15초 |
|9개월 아기 웃음 | [Baby Laugh](https://freesound.org/people/reinsamba/sounds/47370/), reinsamba | [CC BY3.0](https://creativecommons.org/licenses/by/3.0/) | [공개 MP3 preview](https://cdn.freesound.org/previews/47/47370_18799-hq.mp3), 약2.48초 |
|6.5개월 옹알이 | [Infant babbling in crib](https://commons.wikimedia.org/wiki/File:Infant_babbling_in_crib.ogv), Sage Ross | CC BY-SA3.0 선택 가능 | **미실행**. Commons 미디어 요청이 HTTP429여서 반복하지 않음 |

CC 정보는 Commons 저작자/라이선스 원문과 원 배포처로 확인했다. MP3를 FFmpeg로 mono16kHz float32 PCM으로 변환했다. 첫15초 제한 이외 음량 증폭·잡음 제거는 하지 않았다. 알려진 클립에 맞춰 임계값을 조정하지 않았다. [파일 출처·해시](../qa/0.5.0/public-audio-manifest.json), [실제 출력](../qa/0.5.0/public-audio-results.json).

## 관찰된 결과

- **울음:** 영아 울음 모델 점수0.852, 일반 울음0.957. 결과는 “응애, 응애… 토닥토닥 해줄래요?”. 실행984ms. 이는 모델 점수이며 욕구 정확도/보정된 확률이 아니다.
- **웃음:** 아기 웃음0.668·일반 웃음0.984와 함께 화자 미확인 울음0.332가 나왔다. 현재 충돌 규칙 때문에 **한마디 보류**. 실행255ms. 배포자가 웃음으로 제시한 자료에서도 정상적인 결과를 지나치게 보류할 수 있음을 보여 준다.
- **옹알이:** 파일을 확보하지 않아 미실행. 단위시험에서 Babbling exact match를 확인한 사실을 실제 옹알이 분류 성공으로 바꾸지 않는다.

시간은 해당 에뮬레이터의 단발 실행값이다. 다른 기기의 지연이나 ARM 성능 수치로 사용하지 않는다. 앱은 소리 신원·배고픔·졸림을 확인하지 않으며 이 문장을 정답 번역으로 평가하지 않았다. 청취자가 블라인드로 라벨을 매긴 독립 평가도 아니다.

## 제품 판단

입력 없이 나오는 문장·부적절한 놀이 제안 경로를 막는 코드 수정과, 정상 입력에서 유용한 한마디를 제공하는 성능은 별개다. 현재0.5는 **유료 품질 승인 전의 실사용 평가 후보**다. 웃음 보류 사례는 해결해야 할 coverage 문제로 남긴다. 다양한 아기/성인/TV/혼합 입력에서 class별 오류와 보류율을 함께 평가하고 일반 울음과 영아 웃음의 충돌 규칙을 조정해야 한다. 이 두 클립만을 통과시키려고 수치를 바꾸지 않는다.

실행 방법: `PublicAudioProbeTest`는 명시적 `-e public_audio_probe true`일 때만 동작한다. `getExternalFilesDir()/qa-public-audio/{cry,laugh,babble}.f32`를 읽고 같은 폴더의 `probe-results.json`에 결과를 기록한다. 파일이 없는 사건은 `not_run_missing_source`로 남는다. 이 측정용 테스트 성공은 모든 사건이 올바르게 분류됐다는 판정이 아니다.
