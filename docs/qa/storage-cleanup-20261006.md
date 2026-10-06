# 실험 종료 후 디스크 정리 — 2026-10-06

사용자가 완료된 실험 생성물 정리를 요청했고, 이후 `C:/MoaAVD/AgattokExpression29.avd`와
`C:/MoaAVD/AgattokNavigation30.avd` 삭제를 대상을 명시해 승인했다.
과거 자동 승인 검토의 삭제 차단을 우회하지 않고 새 승인 후 해당 두 디렉터리와 연결된 등록파일만 정리했다.

## 실제 결과

| 대상 | 개수 | 삭제 전 파일 표시 용량(GiB) |
|---|---:|---:|
| 완료된 실험 APK/AAB |145|16.826|
| 다시 받을 수 있는 공개 연구 가중치 |7|5.731|
| 재생성 가능한 Android 빌드 디렉터리 |5|5.355|
| 승인된 임시 QA AVD |2+등록파일1|약34.735|

가중치는 Gemma E2B, Qwen3-1.7B, SpeechMaturity의 공개 encoder/bootstrap3개,
AST 연구용 float ONNX·원본 safetensors다. 제품 AST/YAMNet/MediaPipe assets는 보존했다.
모델의 고정 원천·revision·SHA와 조사/실행 결과는 기존 연구 문서 및 로컬 metadata에 남았다.
다시 사용할 때 고정 원천을 다운로드하고 SHA를 재확인한다.

실험·빌드 정리의 **실제 드라이브 여유 증가24.62GiB**, 임시 AVD 정리의 **14.94GiB**를 확인했다.
합계 **약39.56GiB** 회수, 정리 직후 C 드라이브 여유 **약84.93GiB**, 후속 검증 시 **약86.99GiB**다.
후속 여유 변화까지 이번 삭제 효과로 더하지 않았다.
Windows 탐색기의 GB 표시에 대응하는 이진 단위를 사용했다. sparse 가상 디스크와 압축/공유 파일 때문에
표시 파일 크기의 합을 실제 회수 공간으로 보고하지 않았다. 여유 공간은 다른 프로세스 사용으로 조금 변할 수 있다.

## 보존 및 검증

- 최신code32와 직전code31의 `final/` APK/AAB 및 정책 비교용 release를 보존했다. 모든 최종 파일의 전후SHA·크기가 동일하다.
- 추적 소스·제품모델·서명키·아기 원본·트레일러·0.2.1/0.3.0 기준 산출물은 삭제 대상에서 제외했다.
- QA 로그/JSON/XML/스크린샷·조사/하네스는 보존했다.
- 삭제할 빌드 안의 보고서5묶음/파일21개를 별도 보관하고 원본과 복사 SHA를 확인했다.
- `MoaQA35`의 AVD·두 등록 위치·config는 보존했고 metadata SHA가 동일하다.
- 삭제 전 활성 build/probe/emulator가 없는지 확인했다. 다른 앱·사용자폰·공유 Gradle/HF/SDK 캐시는 변경하지 않았다.
- 전체157대상의 절대경로·허용경계·reparse 부재·Git 추적 부재를 검증한 preview 후 같은 PowerShell에서 삭제했다.
  승인된 AVD는 별도 지정4경로를 사전 검증했고 실제 존재한3대상을 제거했다. 존재 부재까지 확인했다.
- Expression29는 이미 config/등록이 없는 잔여 가상 디스크 폴더였다. Navigation30 등록은 그 AVD 경로를 가리키는지 확인했다.

구 실험 APK/AAB 바이너리는 더 이상 해당 로컬 경로에 없다. 당시 해시·실행 결과는 이력으로 남는다.
과거 버전을 다시 실행할 때는 해당 커밋에서 새 빌드하고 새 해시를 기록한다. 동일 바이너리 재생성을 보장하지 않는다.
이번 정리는 제품 분석 결과나 Play 제출 상태를 변경하지 않는다. 앱을 다시 빌드하면 필요한 빌드 자료가 재생성된다.

## 재실행 방침과 증거

[RUNBOOK](../RUNBOOK.md)의 실험 종료 정리를 다음 작업에도 적용한다.
[정리 도구](../../tools/cleanup_research_artifacts.ps1)는 기본preview이며 `-Apply`에서만 파일을 제거한다.
AVD 삭제는 이 도구의 대상에 포함하지 않는다.

로컬 ignored `work/storage-cleanup-20261006/`에 inventory-before.json, plan.json,
cleanup-result.json, avd-cleanup-result.json 및 preserved-build-reports/를 보존했다.
정리 도구preview/실행과 승인 AVD 정리는 모두exit0/pass다. 새 제품 빌드나 성능 시험은 실행하지 않았다.
