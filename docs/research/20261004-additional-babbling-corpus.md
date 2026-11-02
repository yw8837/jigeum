# 옹알이 보조 모델 추가 평가 자료

2026-10-04. 원 배포 페이지를 agent-reach/Jina Reader로 읽고, 실제 HTML에서 라이선스/파일 링크를 확인했다. 공개 원본과 변환본은 ignored `work/public-perception/`에만 보존한다. 앱/저장소/광고에 미디어를 넣지 않는다.

| 원본 | 확인한 설명·권리 | 모델 결과 확인 전 선택 | 독립성·라벨 한계 |
|---|---|---|---|
| [baby_voice_11months(11).wav](https://freesound.org/people/reinsamba/sounds/49728/), reinsamba | 딸 Charlotte, 11개월, 새 소리·음절·단어를 시도한다는 저자 설명. [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/). 원본 길이 24.792초, 공개 MP3 preview 확보. | 첫 20초, mono 16kHz PCM16, 잡음 처리 없음. | 기존 웃음 47370과 같은 저자/아기 그룹으로 취급한다. 새로운 독립 아기로 세지 않는다. 배포자 설명은 독립 청취 정답이 아니다. |
| [Infant babbling in crib.ogv](https://commons.wikimedia.org/wiki/File:Infant_babbling_in_crib.ogv), Sage Ross | 6.5개월 아기가 buh/muh 발성한다는 저자 설명. 선택한 권리 [CC BY-SA 3.0](https://creativecommons.org/licenses/by-sa/3.0/). 2010-04-11 촬영. 실제 컨테이너 길이 15.189333초, OGV 확보. | 첫 15초, mono 16kHz PCM16, 잡음 처리 없음. | 기존 11원본에 없던 저자/원본이다. 독립 청취 라벨과 현장 일반화는 아직 확립되지 않았다. 과거 다운로드 실패와 달리 이번에는 실제 파일을 확보했다. |

## 원본 SHA-256

- reinsamba 공개 MP3: `35c227cfc4a6cdec687502fc2171fad7f698db53a2abe4c7880361c640b5c825`
- Sage Ross OGV: `64ed5d4e0181956dc0e4887c131c09c0904537582e31c6546000730fe9fdb2c9`

변환 WAV 해시와 정확한 로컬 입력은 `work/public-perception/attribution.json`, `cases.json`에 고정했다. 이번 문서는 자료 확보·변환 확인이며 AST 인식 성공이나 정확도 수치가 아니다. 한 원본의 겹치는 여러 창을 독립 표본으로 세지 않는다. 사용자 약국 원본은 계속 개발 회귀 사례로만 사용한다.
