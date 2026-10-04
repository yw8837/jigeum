# Gemma 생성 경로의 기술문의 준비

2026-10-04 시작 · 2026-10-05 준비 검증 완료 · AST beta25 이후의 후속 진단

## 현재 요구와 미완료 범위

사용자의 목표는 최대 20초의 소리·영상 흐름을 온디바이스에서 함께 해석하고,
장면에 어울리는 감정·욕구 추측을 포함한 새로운 한국어 아기말을 만드는 것이다.
AST beta25는 알려진 옹알이 누락을 보완했지만 이 생성 요구를 완료하지 않았다.
현재 제품의 시각 정보는 얼굴·입 관찰이고, 문장은 작성된 후보에서 고른다.

## 이번에 확인한 다음 경계

Astra의 후속 읽기 전용 검토와 현재 소스를 대조했다.

- `VisualTimeline.kt`는 구간을 덮는 시점·입 움직임·초중후 관찰을 만든다.
- `ClipEvidence.kt`의 `VisualExpression`은 한 종류의 승인된 소리와 얼굴/입 관찰을 사용해
  작성된 요청형 표현을 더한다. 행동·사물·상호작용 맥락을 이해하는 생성 모델이 아니다.
- 기존 text-only Gemma/Qwen 대조에서는 정확한 소리 사건을 제공해도 사건 변경과 부자연스러운
  문장이 남았다. 음성 라벨을 텍스트 생성기에 붙이는 것으로 해결됐다고 볼 수 없다.
- S25 Gemma의 합성 소리 누락·장면 순서 오류·발열·취소 지연은
  [실측 보고서](../qa/ondevice-av-generation/S25-GEMMA-20261004.md)에 남아 있다.
- 현재 근거에서 재추론을 정당화할 새 호출 오류나 바로 채택할 대체 구조를 찾지 못했다.
  이는 모든 온디바이스 모델이 불가능하다는 결론은 아니다.

관련 검토는 [호출 형식 대조](20261004-generation-failure-review.md),
[관찰과 마음 추측의 구분](20261004-grounded-intent-recheck.md),
[런타임 수정 조사](20261004-gemma-runtime-triage.md)를 따른다.

## 준비한 재현 자료와 문의 내용

이미 실행한 S25의 **합성 10초·20초 입력**을 사용한다. 새 아기 자료를 요구하거나
같은 모델 추론을 반복하지 않는다. 원본 앱의 기능·배포 상태도 바꾸지 않는다.

자료에 포함한 항목:

- 고정 모델의 공개 주소·revision·SHA와 SDK 0.17.1 정보. 모델 바이너리는 제외.
- 기존 연구 Android 호출 코드와 독립 빌드 설정.
- 합성 WAV와 단색 PNG, 실제 순서·시간 manifest 및 파일 검증 목록.
- 허용한 형식의 실행 설정·시간·PSS 표본·thermal 수치와 합성 응답.
- 외부 발송 전 검토할 영문 문의 초안. 기기 serial·계정·원본 경로·비밀키·제품 기록은 제외.

공유 가능한 입력인지 파일 이름만으로 판단하지 않는다. 기존 입력의 고정 해시,
decoded PCM, 단색 픽셀과 메타데이터를 검사한다. WAV는 손실 압축을 거친 합성 톤이므로
수학적으로 완벽한 정현파라고 기술하지 않는다. 응답도 검증된 합성 입력과 연결되고
기존 결과의 byte 수·SHA가 일치하는 것만 준비한다. 원시 ADB/logcat 로그는 포함하지 않는다.
WAV는 고정된 ffmpeg ISFT 태그만 들어 있음을 검사한 뒤 **원래 파일 바이트 그대로** 보존한다.
메타데이터를 지우거나 WAV를 다시 인코딩하지 않았다.

문의에서는 두 가지를 구분한다.

1. `audio=true`, `vision=true`, **`video=false`**인 이 artifact에서 AudioFile과 시간 표기된
   이미지 목록을 함께 보내는 현재 경로가 의도된 사용법인지 확인한다.
2. 동일 artifact·입력의 공식 정상 reference와 Android 사이에 오디오 전처리/encoder 입력이나
   이미지 순서 처리의 차이가 있는지, 이를 확인할 진단 방법 또는 적용 가능한 수정이 있는지 묻는다.

관측된 의미 실패를 이미 입증된 SDK 결함이라고 부르지 않는다. 권장 sampler/입력 순서와의
차이 및 통제되지 않은 단회 성능 측정이라는 한계도 초안에 포함한다.

## 실제 준비 검증 — 2026-10-05

- `tools/research/export_gemma_reproducer.py`는 기본 실행 시 검증·목록만 반환한다.
  명시적인 새 `work/` 출력에서만 allowlist 57파일을 내보낸다. 전체 검증 후 생성하며 기존 출력은 덮어쓰지 않는다.
- 소스 15파일, 고정 합성 사례 2개와 inventory만 수집한다. 임의 폴더 재귀 수집을 하지 않는다.
  WAV/PCM/응답의 고정 SHA와 byte 수, 단색 PNG 픽셀·CRC, 매초 440Hz 에너지 비중을 검사한다.
  metrics는 수치/Boolean/고정 enum만 허용한다. source와 output 조상의 symlink/junction도 거부한다.
- `python -m unittest discover -s tools/research -p test_export_gemma_reproducer.py -v`:
  **11개 중 10개 통과 / 1개 skip, 실패 0 / 3.221초**.
  skip은 호스트가 symlink 생성을 허용하지 않기 때문이다. 별도의 실제 Windows junction 검사는
  source·output 조상 거부와 링크 제거 뒤 target sentinel 보존까지 통과했다.
- 내보낸 폴더에서 `prepare_replay.py 10/20`과 Android runner의 `--prepare-only`를 실행했다.
  준비된 WAV/PNG 원래 바이트, prompt, 시간·순서가 기존 합성 입력과 동일했고 `android_executed=false`였다.
- 같은 폴더의 독립 Android 프로젝트를 JDK21/SDK36으로 `assembleDebug --offline --max-workers=1
  --console=plain --no-daemon` 빌드했다. **exit0 / 27.629초 / 37 tasks 실행**.
  180초·전체 자손 committed memory 12GiB·출력 8MiB 한도 중 시간/메모리/출력 한도에 걸리지 않았다.
  root 정상 종료 후 남은 자손을 정리했고 `cleanup_complete=true`, remaining0이다.
  고정 Gradle8.14.3의 Gradle9 비호환 deprecation 안내는 남아 있다. 기존 호출을 재현하는 연구 프로젝트이며
  이 안내를 제품 앱 Lint 경고 수에 합산하거나 원래 재현 코드를 바꾸지는 않았다.
- 공개용 텍스트에서 알려진 로컬 사용자 경로·계정·비밀키 패턴을 검사했다. binary는 고정 fixture/Gradle wrapper뿐이다.
  ZIP에는 allowlist 57개와 README/문의 초안/helper/ignore 4개, 추가 inventory 1개만 넣었다.
  `work/`, 빌드 캐시, APK, 모델, raw 로그는 넣지 않았다. ZIP의 62개 이름·내용·CRC를 다시 대조했다.

고정 로컬 산출물(모두 Git ignored):

| 항목 | 위치/값 |
|---|---|
| 전달 폴더 | `work/gemma-upstream-support-01/` |
| 영문 문의 초안 | `work/gemma-upstream-support-01/ISSUE-DRAFT.md` |
| 준비·빌드·검증 로그 | `work/gemma-upstream-support-qa-01/` |
| ZIP | `work/gemma-upstream-support-20261005.zip` |
| ZIP 크기 | 684,537 bytes (62파일, 비압축 1,180,257 bytes) |
| ZIP SHA256 | `bb18a809347670f58bbec9e9a386aa7ced4daaf47624abe3adf5f90ce72710e6` |

이 단계에서는 **ADB·모델 추론·새 다운로드·휴대폰 앱 변경을 하지 않았다.**
기존 S25 결과를 재현 자료로 준비했으며 새 모델 품질 검사로 세지 않는다.
소스·문서는 커밋하지만 실제 응답/미디어/ZIP은 커밋하지 않는다. exporter의 고정 원천도 ignored work에 있어
새 clone만으로 이 두 과거 실행 자료를 복원할 수는 없다. 외부 검토자는 완성된 ZIP을 받아 사용할 수 있다.

## 외부 발송 경계

기술문의 자료는 로컬에서 검증·보관한다. Google AI Edge/LiteRT-LM 저장소에 issue를 작성하는 것은
외부 메시지 발송이므로 사용자의 명시적인 발송 승인이 필요하다. 일반 조사·앱 배포 승인을
외부 기술문의 발송 승인으로 확대하지 않는다. 준비된 초안·첨부 목록을 보여준 뒤 최종 확인한다.

후속 실행을 뒷받침할 근거는 같은 고정 입력에 적용 가능한 처리 차이·수정 또는 정상 reference다.
단순 버전 번호나 다른 prompt의 우연한 성공을 제품 품질 통과로 간주하지 않는다.
지원 요청의 발송·응답도 기능 구현 완료나 새 모델 채택을 의미하지 않는다.
