# UltraQA Report — 장면과 함께 저장0.5.5/code19

2026-10-02. 로컬 구현·통합 검증·서명 패키징 완료. **전체 계측231통과/0실패/1명시제외**, 최종 서명 APK의 실제 미디어 저장·재생·내려받기·공유 통과. [전달 파일 해시](artifacts.json). Play 업로드·검토 제출은 이번 결과에 포함하지 않는다.

## 목표와 경계

선택 구간의 영상·실제 소리·편집한 번역 문장을 MP4로 앱에 보관하고, 기록에서 재생·내려받기·공유한다. 녹음만 있는 경우 번역 카드 영상에 실제 소리를 담는다. 이미 생성된 결과는 무료로 보관/내보내기 가능하다. 아기 원본 서버 전송, 상품 등록/실거래, Play 제출을 포함하지 않는다. [계약](../../UX-MEDIA-0.5.5.md), [행렬](MATRIX.md).

## 검증

| 범위 | 결과 | 근거 |
|---|---|---|
| 실제 코덱/선택구간/음량/자막/화면/SQLite/취소·삭제실패 |7/7 통과 | [계측](focused2.log) |
| 관련 접근·공유·가져오기 회귀 |31/31 통과 | [계측](regressions.log) |
| 전체 계측 |232개 중231통과/0실패/1명시제외,580.26초 | [결과](instrumentation-result.json), [로그](instrumentation.log) |
| 서명 앱 실제 저장·재생·SAF·공유·재시작·큰글자·무료 접근 |5개 흐름 통과. 실제 MP4 743,293bytes/10.000초/audio+video | [결과·설치 SHA](media-ui-result.json), [화면 단계](media-ui-steps.jsonl) |
| debug/preview lint, release 패키징 |빌드 통과. release lint0오류/18경고. 최종 preview APK/release APK·AAB 검사 통과 | [preview](package-preview.json), [release](package-release.json), [lint](lint-release.txt) |
| 검증 서버 |56/56 통과 | [Node 테스트](server-tests.log) |
| 공개 정책 | HTTP4/4 및 MP4 안내 확인 | [상태](policy-https.json), [revision](policy-revision.json) |

실제 시험은 전용 API35 x86_64 에뮬레이터 한 대에서 수행했다. 디버그 계측은 합성 색상·순음 fixture, 서명 UI의 실제 모델 경로는 기존 권리 확인 CC0 울음/단색 영상을 사용한다. 결과 문장이 생성됐다는 사실은 상황 적합성·아기 의도 정확도의 증명이 아니다. 새 버전에서 분석 모델/의도 규칙을 바꾸지 않았다.

**검증 순서:** 전체 계측 통과 후, 실제 서명 UI에서 내려받기 완료 Snackbar가 열린 창 뒤에 가려지는 문제를 발견했다. 창 안에 완료 문구를 표시하도록 수정하고 최종 서명 APK/AAB를 다시 만들었다. 이 마지막 표시 변경은 전체 계측 이후이며, 최종 파일의 서명 UI5개 흐름과 패키징을 재검증했다. 전체 계측 APK와 전달 APK의 해시는 각각의 결과에 구분해 남겼다. 명시제외1개는 외부 음원 corpus를 요구하는 `PublicAudioProbeTest#reportPublicSoundEventProbes`다.

## 발견한 문제와 수정

- 초기 빌드: 새 import 누락과 들여쓰기 검사 오류를 수정했다. 테스트를 실행하지 못한 빌드를 통과로 세지 않았다.
- 독립 코드 검토5건: 렌더 성공 직후 취소, DB 잠금 밖 오래된 정리 목록, 파일 삭제 실패 후 상태 불일치, 완료 파일 채택 전 정리 경합, 외부 저장 제공자의 실패 파일 삭제 거절. [Standards / Spec 별 결과와 처리](REVIEW.md).
- 전체 첫 실행232개:226통과/5실패/1명시제외.4개는 삭제 확인 문구를 찾는 낡은 테스트 선택자,1개는 비동기 로딩 뒤 Compose 계측의 IO 복귀에서 Looper 없는 스레드로 화면 갱신이 진행된 경계였다. 문구 선택자를 갱신하고 준비 완료 상태를 명시적으로 Main dispatcher에서 적용했다. 이후 관련31개를 다시 통과했다. 최초 실패 로그는 로컬 `work/media-memory-055/full`에 보존한다.
- 삭제는 SQLite와 함께 `delete_media:*` 정리 의도를 남긴다. 파일 삭제 실패 시 기록은 제거된 실제 상태를 반영하고 경고·재시도를 유지한다. 저장/가져오기가 남은 미디어 ID를 재사용하지 못하게 한다. 새 DB 테이블·외부 DB는 추가하지 않았다.
- 첫 서명 UI 실패는 가려진 완료 안내였다. 표시 수정 후 최종 UI 통과. 중간 탐색에서는 PDF 이용권 버튼이 화면 아래에 있는데 스크롤하지 않는 하네스 오류도 발견해 실제 버튼을 찾아 누르고 이용권 화면을 확인하도록 수정했다. 최초 실패와 중간 탐색을 최종 통과로 세지 않는다. 로그는 로컬 `work/media-memory-055/ui`, `ui-explore.log`에 보존한다.
- Media3는 선언1.8.0, 기존 의존성 정렬로 실제1.9.0 선택. [해결 결과](media3-resolution.log). 렌더 플랫폼 진단은 명시적으로 비활성화했다.

## 명령과 제한 시간

- Gradle `assembleDebug assembleDebugAndroidTest lintRelease`, worker1. 수정 후 같은 대상 빌드.
- 실제 `TranslatedMediaTests`7개 및 관련31개 `am instrument -w -r -e class ...`. test별 완료 상태와 OK 요약을 확인.
- `instrument.py --install-app --timeout 1500 --allow-skip com.moa.babylanguage.PublicAudioProbeTest#reportPublicSoundEventProbes`. 설치 APK·테스트 APK SHA까지 대조. 공개 음원 외부 corpus는 이 릴리스에서 재측정하지 않아 명시 제외.
- `build-test-apk.ps1`, `build-play-bundle.ps1 -Purpose paid-candidate`; 기존 업로드 키 사용. `bundlePreview`는 지정된 sideload-only 오류로 실패해야 한다.
- `check_package.py --distribution preview --skip-aab ...`, `--distribution release --apk ... --aab ...`: 서명, 실제 버전/manifest, 공유·백업 경계, ELF16KiB, zipalign 확인. ARM 기기 실행 시험은 별도다.
- `media_ui.py --preview-apk ... --release-apk ... --source <권리 확인 로컬 QA 영상>`: 실제 Android 저장 선택기와 MP4 확인. source는 Git/APK에 넣지 않는다.

## 한계와 다음 확인

- 실제 ARM/OEM 하드웨어 코덱·카메라, 매우 느린 저장소·실제 디스크 완전 소진, 모든 외부 문서 제공자를 검증하지 않았다. 코덱/공간 오류는 문장만 저장하는 경로로 돌아갈 수 있다. 오류 주입은 손상 입력·취소·비어 있지 않은 디렉터리로 unlink 실패를 재현하는 안전한 방법을 썼다.
- 외부 제공자가 삭제를 거부하면 미완성 파일을 직접 확인·삭제하도록 알린다. 이미 내려받거나 수신 앱에 전달된 파일은 앱의 기록 삭제로 지울 수 없다.
- 저장 전 임시 캡처는 화면/프로세스 재생성으로 복원되지 않을 수 있다. 저장된 MP4의 유지와 미저장 원본의 복원은 다르다. 기존 텍스트 기록의 소리/영상도 복구 불가다.
- 기기별 영구 미디어 총용량은 남은 저장공간에 따라 제한된다.1회 출력64MiB, 렌더 전80MiB 이상 여유 검사,120초 렌더 제한. 공유 캐시는 기존20개/64MiB/24시간 계약을 따른다.
- 표정 단독 의도 모델은 추가하지 않았다. [가능 범위·근거·평가 계획](../../research/20261002-face-only.md).
- Play Alpha는code18 초안·공개 전화 승인 대기다. 새code19 AAB 패키징은 업로드/검토/테스터 참여 가능 상태가 아니다. 실제 Play 결제·12명14일·프로덕션도 미완료다.

## 정리

[정리 결과](CLEANUP.md). 기존0.5.4 산출물은 기준본으로 보존하며 다운로드 전달 폴더에는 최신 테스트 APK 하나만 둔다. 앱 기록·원본/파생 QA 미디어·서명 키·구매 토큰·빌드 캐시를 커밋하지 않는다. 직접 생성한 색상/순음 계측 fixture2개만 테스트 소스로 포함한다.
