# VTC 1 정확 checkpoint의 상업 사용·재배포 조건 조사

조회: **2026-10-07 00:04~00:09 KST**(2026-10-06 15:04~15:09 UTC).
`agent-reach`의 GitHub CLI/API 및 Jina Reader 경로로 공개 1차 자료를 읽었다.
계정 생성·작성자 연락·게시·유료 API·설치·Git 명령·가중치 취득·원음 읽기·추론·제품 변경을 하지 않았다.
공유 작업 중 이 문서 하나만 작성했다. 임시 디스크 파일은 만들지 않았다.

## 판단

**VTC 1의 `0100.pt`를 상업 앱에 포함·재배포할 수 있다는 명시적 grant는 확인하지 못했다.**
공개 저장소에 실제 weight가 있고 저자가 “open-source” 모델이라고 설명하지만,
weight의 복제·변형·상업 사용·앱 동봉·재배포 조건을 정하는 라이선스/모델 카드/보충 grant는 확인되지 않았다.
따라서 내부 성능 비교 결과와 별개로 **제품 배포 권리 gate는 미해결**이다.
“상업 사용 명시 금지”를 발견한 것도 아니며, 숨겨진 별도 허락이 없다고 입증한 것도 아니다.

이 판단은 공개 자료에서 **허락을 확인할 수 있는가**에 관한 것이다.
해당 파라미터의 저작권 성립 여부나 모든 국가의 예외 조항에 대한 법률 판단을 대신하지 않는다.
현재 공개 자료로 제품에 넣을 수 있다고 보고할 근거가 없다.

## 1. 조사 대상 artifact

| 항목 | 값 및 확인 구분 |
|---|---|
| repository/ref | `MarvinLvn/voice-type-classifier`, `new_model`, `9731a9aad21a85d7bc660193386de94f2fb19f76` |
| artifact | `model/train/X.SpeakerDiarization.BBT2_LeaveOneDomainOut_paido.train/weights/0100.pt` |
| 공개 tree의 크기 | **4,532,569B**, 이번 API 조회로 확인 |
| 공개 Git blob SHA-1 | `649293f96345748da846ecbdd46538838f1fae25`, 이번 API 조회로 확인 |
| 실제 payload SHA-256 | `8514e96c8e049abd9ffc75c816b6aff51df09507b3ec7ac1c800fd366408286c` — 부모 작업자가 확인한 값으로 전달받음. 이번 권리 조사에서 binary를 다시 읽거나 취득하지 않음 |

원천: [고정 tree API](https://api.github.com/repos/MarvinLvn/voice-type-classifier/git/trees/9731a9aad21a85d7bc660193386de94f2fb19f76?recursive=1),
[weight blob metadata](https://api.github.com/repos/MarvinLvn/voice-type-classifier/git/blobs/649293f96345748da846ecbdd46538838f1fae25),
[고정 weight 경로](https://github.com/MarvinLvn/voice-type-classifier/blob/9731a9aad21a85d7bc660193386de94f2fb19f76/model/train/X.SpeakerDiarization.BBT2_LeaveOneDomainOut_paido.train/weights/0100.pt).
weight blob contents는 요청하지 않았다.

## 2. 원 repository에서 조사한 모든 공개 license 경로

| 경로/범위 | 조회 결과 | 이 결과가 입증하는 범위 |
|---|---|---|
| [repo API](https://api.github.com/repos/MarvinLvn/voice-type-classifier) | `license:null`, default `new_model` | GitHub가 인식하는 repository license가 없음 |
| [license API](https://api.github.com/repos/MarvinLvn/voice-type-classifier/license) | HTTP404 | 해당 API에서 root license를 반환하지 않음 |
| [branches](https://api.github.com/repos/MarvinLvn/voice-type-classifier/branches) | `new_model` 위 pin, `master`=`72adda544fd3d77f848059a7921dd1e83c61bd5b` | 두 공개 branch를 확인 |
| 두 branch의 recursive tree | 모두 `truncated:false`; LICENSE/LICENCE/NOTICE/COPYING/RIGHTS/TERMS/model-card 경로 없음 | root 외 하위 경로까지 확인 |
| [tags](https://api.github.com/repos/MarvinLvn/voice-type-classifier/tags) / [releases](https://api.github.com/repos/MarvinLvn/voice-type-classifier/releases) | 각각0개 | 별도 tag/release asset의 grant 경로를 발견하지 못함 |
| default branch의 LICENSE / LICENSE.md / LICENSE.txt path commit API | 각각0개 | 이 세 일반 파일명의 공개 default-branch 이력에서 license 추가를 확인하지 못함. 모든 역사적 파일명/삭제된 비공개 자료를 조사한 것은 아님 |
| 두 branch의 README 및 docs 전체 | license/copyright/commercial/redistribute/permission 문구 없음; new_model README의 `open-source`는 논문 제목 | 문서 안의 별도 사용 허락을 발견하지 못함 |
| 두 branch의 .sh/.yml/Dockerfile 전체(훈련 specs 제외) | 동일 권리 keyword grant 없음 | wrapper/container/config header의 별도 허락을 발견하지 못함 |
| [public issues](https://api.github.com/repos/MarvinLvn/voice-type-classifier/issues?state=all&per_page=100) 30개 / [comments](https://api.github.com/repos/MarvinLvn/voice-type-classifier/issues/comments?per_page=100) 77개 | 제목/본문에서 license/commercial/redistribute/copyright/permission keyword matching grant0 | 공개 이슈의 해당 문구로 허락을 찾지 못함; 새 문의를 작성하지 않음 |

문서 원천: [README](https://github.com/MarvinLvn/voice-type-classifier/blob/9731a9aad21a85d7bc660193386de94f2fb19f76/README.md),
[disclaimer](https://github.com/MarvinLvn/voice-type-classifier/blob/9731a9aad21a85d7bc660193386de94f2fb19f76/docs/disclaimer.md),
[evaluations](https://github.com/MarvinLvn/voice-type-classifier/blob/9731a9aad21a85d7bc660193386de94f2fb19f76/docs/evaluations.md),
[master tree](https://api.github.com/repos/MarvinLvn/voice-type-classifier/git/trees/72adda544fd3d77f848059a7921dd1e83c61bd5b?recursive=1).
README는 “code for VTC 1.0 will remain available here for reproducibility” 및 pre-trained model 적용을 설명한다.
재현/적용 안내를 APK/AAB 상업 재배포 라이선스로 확대하지 않는다.

## 3. 저자 공식 페이지·논문·supplement 조사

- [저자 공식 homepage](https://marvinlvn.github.io/), [projects](https://marvinlvn.github.io/projects/),
  [publications](https://marvinlvn.github.io/publications/)를 읽었다. Publications의 VTC2020 설명은
  “open-source neural network” 및 논문 abstract이며 `0100.pt` 권리 범위를 정하는 별도 model card/grant는 찾지 못했다.
- [arXiv2005.12656v3](https://arxiv.org/abs/2005.12656v3)의 license link는 **CC BY4.0**이지만,
  실제 anchor title은 **`Rights to this article`**다. 이는 논문 자체의 권리 표기다.
  이 페이지는 checkpoint 파일을 CC BY4.0 대상으로 지정하지 않는다.
- [공식 논문 HTML](https://arxiv.org/html/2005.12656v3)의 Implementation 문구:
  “Our own code, easy-to-use scripts to apply the pretrained model can be found on our GitHub repository ...”
  이어 “As soon as required agreements will be obtained, we plan to facilitate access to the data by hosting them on Homebank.”
  source/scripts 공개 위치와 training data 접근 agreement를 설명한다.
  weight의 상업 복제/변형/재배포 허락 문구는 찾지 못했다.
- [ISCA 공식 논문 landing](https://www.isca-archive.org/interspeech_2020/lavechin20_interspeech.html)의
  asset link는 PDF 및 citation/bibtex/DOI UI다. 별도 weight supplement/license/model-card 링크를 찾지 못했다.

논문에 기록된 제한된 training data 접근이 **checkpoint 사용을 자동으로 금지한다**고 단정하지 않는다.
반대로 논문의 CC BY/open-source 표현이 **checkpoint 사용을 자동으로 허락한다**고 단정하지 않는다.

## 4. dependency의 실제 MIT 범위

원 repo gitlink의 `MarvinLvn/pyannote-audio` commit은
`85b84bc7ee3f44f1f112397f615873f348517541`다.
[dependency LICENSE](https://github.com/MarvinLvn/pyannote-audio/blob/85b84bc7ee3f44f1f112397f615873f348517541/LICENSE)는
MIT, Copyright(c)2016 CNRS이며 “this software and associated documentation files”를 대상으로 한다.
개별 SincNet/PyanNet source의 MIT header와 함께 **해당 source 사용권**은 확인된다.
이 license가 부모 repository의 `0100.pt` task checkpoint를 포함한다는 문구는 없다.
모델 구조의 source를 재구현하는 것과 학습된 parameter를 제품에 재배포하는 것은 별도 권리 확인 대상이다.

[GitHub 공식 licensing 안내](https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/customizing-your-repository/licensing-a-repository)는
license가 없으면 기본 copyright 규칙이 적용되며 공개 repository의 view/fork 권한은 GitHub Terms에 따른다고 설명한다.
공개 GitHub 다운로드 가능성을 상업 앱 동봉·변형물 재배포 권리로 보고하지 않는다.

## 5. VTC family의 직접 대체 경로

| 직접 후보 | 이번에 확인한 권리/배포 상태 | 작은 상업 대체 checkpoint로 인정 여부 |
|---|---|---|
| 구 VTC1 `master`의 `0265.pt`, `0330.pt`, `0455.pt`, `0495.pt` | 각각2,736,346B; 위 master tree에 존재. 동일 branch에 weight license/grant 없음 | **권리 해결 안 됨**. 작다는 이유로 대체하지 않음 |
| 원 repo [공개 forks](https://api.github.com/repos/MarvinLvn/voice-type-classifier/forks?per_page=100) | API에 나타난20개 모두 `license:null`. 공동저자 `hbredin` fork도 포함 | fork 목록으로 원 weight 허락을 만들어내지 않음; 새 유효 grant 발견 없음 |
| [LAAC-LSCP/VTC](https://github.com/LAAC-LSCP/VTC/tree/a66d76548e2ec9cb042b28b8b3106e77ca0fbc13) / [coml/VTC-2](https://huggingface.co/coml/VTC-2) | source root license 없음/API null. source gitlink weight revision `6b1a955…`; 현재 HF revision `91167f6103c1f211bb9861f32d57e92ed01618b9`도 card에 base_model/citation만, LICENSE/LICENCE404. 1,099,159,455B checkpoint | 크고 정확 weight grant 없음 |
| [VTC-IS-25 Whisper checkpoint](https://github.com/LAAC-LSCP/VTC-IS-25/tree/acebb56ced2454fa92ceb1afbe46dc2ed3683bf4) | source API license null; 전체 tree에 license/notice 없음.378,022,248B weight | 권리 해결 안 됨; 작은 대체 아님 |
| [MarvinLvn/BabyHuBERT 공개 metadata](https://huggingface.co/api/models/MarvinLvn/BabyHuBERT?blobs=true) | revision `3736078db7fc4703222e4bb52da9f59871425243`, gated=auto. 공개 `extra_gated_prompt`가 **commercial use prohibition**, surveillance 금지와 derived-model 조건 상속을 명시 | **상업 대체로 부적합**. gate를 수락/등록하지 않았고 gated card/full license를 우회하지 않음 |

`coml/BabyHuBERT` API는401이었다. BabyHuBERT의 현재 공개 NC 고지를
원래 SincNet VTC1이나 모든 역사적 VTC2 weight에 자동 적용하지 않는다.
그 각각의 정확 revision/weight grant가 확인돼야 한다.
이번 직접 경로 범위에서 **권리가 명확한 작은 상업 VTC checkpoint는 찾지 못했다**.

## 제품 배포 판단을 바꾸는 데 필요한 공개 근거

다음 근거 중 하나가 정확 artifact/ref를 대상으로 명시돼야 한다.

1. 권리자의 공식 LICENSE/model card가 `0100.pt` 또는 해당 checkpoint revision/SHA를 포함하고,
   상업 사용·복제·변형·APK/AAB/다운로드 통한 재배포 조건을 정한다.
2. 동일 task의 새로운 작은 checkpoint가 해당 권리를 명시하고
   attribution/notice/derived-model 의무를 이행할 수 있다.

현재 공개 자료로는 어느 조건도 충족하지 못했다. 작성자에게 연락하지 않았으며 연락을 새로 승인받았다고 가정하지 않는다.
로컬 retained/Public 비교의 품질 결과는 부모 작업자 소유이고, 이 문서는 그 결과를 검증하거나 일반 인식률로 확대하지 않는다.

관련 이전 계약: [정확 VTC1 실행/source 계약](20261006-specialist-actionable-next.md),
[기존 BabyHuBERT/ChildVox 권리 조사](20261005-infant-specialist-followup.md).
