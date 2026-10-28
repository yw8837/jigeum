# 새 소형 영아 엔진 후속: 실행 후보 0

2026-10-07 KST. [기존 공급 감사](20261005-infant-repos-audit.md), [specialist 후속](20261005-infant-specialist-followup.md), [representation 감사](20261007-next-infant-representation-evidence.md)와 10-04~07 엔진 문서의 이름·artifact를 먼저 대조했다. 기존 후보·실행 결과를 다시 나열하지 않는다. 이 문서만 작성했다.

## 판단

**이번 조회 범위에서 새 실행 후보는 0개다.** 공개된 소형 infant-domain encoder 또는 noncry/babble/laugh detector를 실제 weight·출력·권리까지 연결한 신규 근거를 확보하지 못했다. 전 세계에 후보가 없다는 결론은 아니다. 최대3개를 채우려고 cry-only·텍스트·발성 생성 모델을 detector로 바꾸지 않았다.

### 새로 확인했으나 후보로 추가하지 않은 원천

**[tim-shea/BabbleNN](https://github.com/tim-shea/BabbleNN/tree/3c7eb5073002a8c3b7b06f0bac8061c087cf259d).** [공식 tree API](https://api.github.com/repos/tim-shea/BabbleNN/git/trees/HEAD?recursive=1)는 tree ref `3c7eb5073002a8c3b7b06f0bac8061c087cf259d`, truncated=false다. [고정 README raw](https://raw.githubusercontent.com/tim-shea/BabbleNN/3c7eb5073002a8c3b7b06f0bac8061c087cf259d/README.md)는 articulatory/auditory systems의 **발성 생성·발달 simulation**이며 MATLAB/Praat·Mac/Windows 연구 코드라고 설명한다. 실제 infant noncry/babble/laugh 입력 detector가 아니다. 자연 영아 월령·adult control 평가·trained detector weight·그 weight의 상업 grant·Android export를 확보하지 못했다. tree의 `.mat`563,091B나 생성 `.wav`를 작은 영아 classifier의 크기로 세지 않는다. README1,536B/SHA `038527c4881cd06c2d61c51b2a45621539b9eed870e1a9de925c8278dc430815`는 source text 크기다. 음원·MAT body를 받지 않았다.

**[BABBLE 논문 검색 hit](https://openreview.net/pdf/120dc0ebaffb87bd4e4ccb46555ec73150d3c39d.pdf).** 제목은 “BABBLE: Bridging Structured Labels and Natural Language…”다. 검색 snippet만으로 공개 detector나 작은 infant encoder라고 확정할 수 없다. Reader가 HTTP200/496B를 반환했지만 논문 본문의 관련 근거를 확보하지 못했다. 저자 artifact·immutable release·weight bytes/license·월령·adult controls·Android 모두 미확인이다. 새 실행 후보나 새 모델 성능 근거로 세지 않는다. PDF body를 직접 받지 않았다.

## 새 query와 제외 이유

| 실제 query | 확인 범위·blocker |
|---|---|
|[HF `infant` + audio-classification](https://huggingface.co/api/models?search=infant&filter=audio-classification&limit=50&full=true)|0결과. 이 task/name 조합의 결과이며 모든 HF 모델 부재 결론이 아님 |
|[HF `baby` + audio-classification](https://huggingface.co/api/models?search=baby&filter=audio-classification&limit=30&full=true)|현재 반환은 cry classifier 계열 또는 이미 감사한 배포. 이름·task tag만으로 비울음 출력·영아 사전학습을 입증하지 않음 |
|HF `babble`, `infant`, `babbling`|`babble`은 주로 ASR/tokenizer·소음 이름. compact 재조회에서 `infant`23개는 주로 무관/unknown/cry/기존 후보, `babbling`1개는 robotics였다. 새 영아 음향 artifact 미확보 |
|`"infant vocalization" "pretrained" "github" -LittleBeats -voc2vec -SpeechMaturity -infant-vocalize`|새 exact detector artifact 미확보 |
|`"baby" "babbling" "model" "checkpoint" github huggingface -LittleBeats -VocalBaby -speechbrain -FunWithSound`|텍스트 학습·speaker verification·범용 tagging 등을 infant detector로 올리지 않음 |
|`"babbling" "pretrained" site:github.com -jialuli3 -uiuc-sst -SpeechMaturity -BabbleCor`|군중 babble noise·ASR·denoising 경로를 자연 영아 encoder로 올리지 않음 |
|`"infant babbling" "model weights"`|위 simulation/미확인 paper와 기존 배포 반환 |
|`"infant vocalization" "classifier" "github" -site:github.com/jialuli3 -site:github.com/arunps12 -site:github.com/uiuc-sst`|cry-only 또는 기존 artifact/mirror. mirror를 새 author release로 세지 않음 |
|`infant "non-cry" "model" "weights"`|cry/noncry binary·울음 원인 연구를 비울음 영아 발성 양성 detector로 바꾸지 않음 |

## 다음 정확한 한 단계

이미 확보한 public MP3 **9437/244237**의 전체를 모델 점수를 가린 인간 검수자가 듣고 **start/end·화자·infant noncry/cry/laugh·adult/background/uncertain** 구간표를 작성한다. 월령은 caption 상태를 유지하며 uncertain를 양성으로 채우지 않는다. 현재 source의 blocked/null은 자동 승격하지 않는다. 이 작은 사건 검수 단계가 완료돼야 기존 개발 결과와 분리된 음향 평가를 정의할 수 있다. 이번에는 청취·구간표 라벨링을 수행하지 않았다.

## 실제 작업 경계

외부18조회 = HF 검색7 + Exa 검색8 + author tree/raw2 + paper Reader1. 각 HTTP/CLI/process에20초 timeout을 적용했다. metadata/raw text만 받았으며 2MiB 예산 아래에서 read cap·검색 반환 bytes로 관리했다. 최초 두 묶음 출력 일부가 절단돼4개 compact 재조회로 결과 검토를 보완했다. 최초 반환의 정확한 전체 byte 합계는 재구성하지 않는다. cap을 적용한 보수적 합계는 약1.05MiB 미만이다. 재조회 반환은 HF18,372/744B, Exa17,694/17,922B였다. 이를 성공한 논문 본문 검증으로 쓰지 않는다. 추가 대형 조회 없이 종료했다.

모델/음원 body 취득·훈련·추론·사용자 자료 접근·연락·ADB·Git·삭제·Elice는 모두0이다. caption이나 검색 hit를 사건 정답으로, 조사 수행을 Android 개선이나 메인의 pair 통합 검증으로 보고하지 않는다.
