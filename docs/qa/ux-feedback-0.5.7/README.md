# UltraQA Report · 아가톡 0.5.7/code21

현재 상태: **0.5.7/code21 변경 범위 QA 통과·서명 패키징 완료**. 관련8개 클래스62통과/0실패/0제외, 최종 서명 UI7시나리오 통과다. 기존0.5.6의246통과를 새 버전의 전체 계측 결과로 사용하지 않는다. 이번 빌드에서 전체 테스트 클래스를 다시 실행한 것은 아니다.

## Goal and success criteria

한 손 촬영·구간 편집·이용권 진입·돌봄 관리·표지2×2·분석 근거 안내를 실제 Compose와 최종 서명 APK에서 검사한다. 정상·권한 철회·취소·기록 보존·큰 글자·작은 화면에서 동작하고 잘못된 유료 상태를 표시하지 않아야 한다. 별도의 소유 QA 에뮬레이터만 사용한다. 원본 아기 자료·비밀키·실제 거래·외부 DB는 범위 밖이다. 최대5개 QA cycle, 동일 실패3회면 진단 후 중단한다.

## Scenario matrix

| ID | 의도 / 사용자 모델 | 준비 | 명령·하네스 | 기대 신호 | 실제 / 수정 / 증거 / 정리 |
|---|---|---|---|---|---|
| U1 | 한 손 홈·촬영 / 일반 부모 | 무료와 preview | UiFeedbackTest + 서명 UI | 하단 버튼 가시성·60/120초 구분 | 통과. 스크롤 뒤 버튼 위치 유지, 최종 APK 화면 확인. [홈](small-home.png) |
| U2 | 구간 편집 / 정밀 드래그 어려운 부모 |10/20초 상한·1초/120초 원본 | ClipRangeEditorTest | 탭 이동·드래그·0.5초·TalkBack가 같은 제한을 준수 | 통과. 경계 비활성 색 수정, 수직 스크롤은 선택을 유지하며 부모를 실제 스크롤. [편집](fine-editor.png) |
| U3 | 결제 찾기 / 상품 없는 Alpha 사용자 | 상품 없음·권한 없음 | UiFeedbackTest + AccessNavigationTests | 홈/돌봄 진입, 결제 준비 중 안내, 무료로 복귀 | 통과. 복원 호출·원래 탭 복귀·preview 안내와 상품 미등록 안내 구분. 실제 거래는 미수행 |
| U4 | 테스트 혼동 / Play와 sideload 사용자 | 실제 권한 false·preview true | CaptureAccessLimitsTest + FullCaptureDeviceTest + 서명 UI | preview120초, release60초,권한 철회시 보호 | 통과. 실제60/120초 영상·녹음, 최종 서명 preview2분 녹화. release교체 뒤 무료 한도 복구. [촬영](release-free-capture.png) |
| U5 | 표지 고르기 / 큰 글자·반복 이동 | 네 가지 표지 | UiFeedbackTest + MemoryArtifactsTests |2×2·선택 유지·실제 PDF 출력 | 통과. 상태 재생성 뒤 네 번째 표지 유지·4종 실제PDF. [표지](small-covers.png) |
| U6 | 근거 확인 / 상담 앱으로 오해한 사용자 | 홈/돌봄 진입 | UiFeedbackTest + 서명 UI | 실제 도구/연구 후보/한계가 구분되고 원래 화면으로 복귀 | 통과. 홈/돌봄 복귀 및 서명 APK 안내 렌더 확인. [근거](about.png) |
| U7 | 취소·배경전환·stale권한·저장 | 취소 가능한 모델·권한 철회 | 기존 접근/촬영 회귀 | 분석 취소/종료·오래된 결과 차단·기존 기록 무료 | 관련 회귀 통과. 자료는 합성 fixture만 사용하고 테스트 DB/클립 정리 |
| U8 | 접근성 / 작은폰·200%글자·태블릿 | 소유 API35 x86_64 | 서명 UI + 형상검사 | 버튼·구간·표지·정책에 도달 가능, 잘림 없음 | 통과. [200%글자](font200-home.png), [태블릿2×2](tablet-covers.png), 기존 화면 설정 복구 |
| U9 | QA의 거짓 성공 방지 / 하네스 | 설치 해시·per-test event | instrument.parse_result | 종료코드·완료수·skip·해시를 함께 확인 | 통과.1차 exit0의 실제 실패를 검출,2차62개 완료·요약·설치 해시 일치. 실패 증거 보존 |
| U10 | 기존 작업 보호 / 공동 작업자 | 기존 testers 증거 untracked | git diff/status + package baseline | 기존 증거·기존APK·서명키 보존 | 이전APK·증거 보존. 모델/네이티브18개 파일 byte일치. 전달 폴더에 최신APK 하나만 준비 |

## Commands run / Failures found / Fixes applied

1차 계측은 61개 중 60통과/1실패/제외0이었다. 실패는 `UiFeedbackTest.previewShowsFullLimitsWithoutPretendingThereWasAPurchase`의 문자열 비교였다. 실제 화면에는 `전체 기능 테스트 · 2분 촬영 · 20초 분석`이 표시됐지만 테스트가 부분 문자열을 완전 일치로 비교했다. 조건을 전체 문구의 `assertTextEquals`로 수정했다. 제품의 촬영 한도가 잘못된 사례로 계산하지 않는다.

62개 2차 계측은 **62통과/0실패/0제외**, 실행532.89초·종료코드0으로 완료됐다. 구간 위의 세로 스크롤을 탭 이동으로 처리하지 않는 회귀도 통과했다. 첫 실패 로그와 결과는 별도로 보존한다. ADB 종료코드0에서도 테스트 실패를 검출했고, 설치 APK 해시·각 테스트 시작/완료·요약 수·명시제외를 함께 확인했다. [최종 계측 결과](instrumentation-result.json) / [최종 로그](instrumentation.txt) / [1차 실패](cycle1-instrumentation-result.json).

debug APK/androidTest APK 및 `lintRelease` 최종 빌드는 종료코드0으로 완료됐다. lint 오류0/경고20(AGP1, 의존성6, 새 버전1, SharedPreferences1, 용량검사2, KTX9)이다. 이전0.5.6도20경고이며 이번UI 변경으로 새 경고가 늘어나지는 않았다. 기존 명시적 권한 거절을 영속하는 동기 commit과 검증된 의존성은 유지했다.

최종 서명 preview APK, release APK·AAB 생성과 패키지 검사는 모두 exit0이다. 실제 컴파일된 preview/release 표시·버전, APK/AAB 서명, 16KiB 네이티브 정렬, 백업 제외·FileProvider 범위를 확인했다. 각 APK의 모델/네이티브18개 파일은0.5.6과 byte일치한다. [preview 검사](preview-package-verdict.json) / [release 검사](release-package-verdict.json).

최종 서명 UI는7시나리오 통과했다. preview 실녹화 시작부터 자동 종료 후 편집 화면 확인까지123.78초였으며 이는 finalize/UI 관찰 지연을 포함한 측정치다. 실제 모델은 합성 무음/순음에 아기말을 만들지 않았다. 같은 업로드키 release21로 수동 교체하자 테스트 표시가 사라지고60초/10초 정책으로 돌아왔다. [서명 UI 결과와 설치 해시](signed-ui-result.json). 이 수동 교체를 Play 자동 업데이트 검증으로 보고하지 않는다.

| 명령 | 종료·제한 | 근거 |
|---|---|---|
| `assembleDebug assembleDebugAndroidTest lintRelease` | exit0 | [debug 빌드](build-debug.txt) |
| 선택8개 클래스 계측 | cycle1 exit0이나 검증 fail, cycle2 exit0/pass; timeout900초 |532.89초·62통과/0실패/0제외 |
| 기존 업로드키 `assemblePreview`, `bundleRelease assembleRelease` | exit0 | [preview 빌드](build-preview.txt) / [release 빌드·AAB 서명](build-release.txt) |
| `check_package.py --distribution preview/release --baseline ...` | 각각 exit0, 도구별120초 제한 | 실제 산출물·서명·패키징 확인 |
| `ux_feedback_ui.py` | exit0, UI대기·탭·ADB별 유한 제한 | 실촬영2분·3개 화면 크기·합성 보류·release복구7시나리오 |

### Standards

정적 검토에서 문서 규칙의 확정 위반은 발견되지 않았다. 실행 시점 권한 검사, `androidTest`에만 있는 결제 대역, 공개 고정 참고 URL, 모델 설명의 관찰 범위를 확인했다. 판단에 따른 중복 문구 smell1건은 `captureAllowanceText()`가 `CaptureLimits`에서 값을 가져오도록 수정했다. 실제 수직 스크롤은 별도 계측으로 확인했다.

### Spec

비활성 이동 화살표가 활성처럼 보이는 P3 1건을 수정했다. 그 밖의 확정 요구 누락·의도하지 않은 기능 추가는 보고되지 않았다. 홈·촬영·분석 하단 버튼, 원래 탭 복귀,4종2×2·PDF 테마 전달, preview 안내·release 실행 권한 검사는 실제 코드에 연결돼 있다. 스펙 근거는 사용자 피드백과 `docs/UX-0.5.7.md`다.

## Cleanup and rollback / Residual risks / Evidence

서명 UI의 합성 `/sdcard/Download/agattok-ux057-synthetic.mp4`를 삭제하고 앱을 종료했으며 글자 크기·화면 해상도/밀도를 원래 값(1.0/1080×2400/420)으로 복구했다. 테스트 DB·촬영 fixture는 각 계측 cleanup에서 정리했다. 작업이 시작한 에뮬레이터 PID42756을 종료하고 임시CSV와 UI 덤프를 정리했다. 작업 전용 OMX 상태만 complete/clear했고 [상태 부재 확인](cleanup-state.txt)이 exit0으로 완료됐다. 다른 작업의 상태·기존 증거·APK는 보존했다. 재실행 하네스는 `outputs/QA/ux_feedback_ui.py`, 로그·화면은 이 디렉터리에 보존한다.

정리 CLI의 정상 `cleared:true` 응답을 임시 하네스가 `ok/success`만 검사해 exit1로 오판했다. 상태는 실제로 이미 제거돼 있었다. 동작별 응답 검사로 수정하고 `exists:false`를 읽어 상태 부재를 확인했다. 제품 결함이나 추가 QA cycle로 계산하지 않는다.

**ULTRAQA COMPLETE: Goal met after 2 cycles.** 이 완료는 위 변경 범위 검증과 정리에 한정한다.

Git 보관용 텍스트 로그는 줄끝/후행 공백만 정규화했다. 원래 실행 로그는 작업 디렉터리에 보존하며 결과·실패·제외 내용을 변경하지 않았다.

모델 변경·아기 의도 정확도·ARM/OEM 실제 기기·실제Play구매·12명14일을 이번UI검증으로 대신하지 않는다. 사용자 새 수음 피드백은 원음 크기와 분석 보류 중 어느 쪽인지 확인되지 않았다. 소리녹음은 `MIC`·AAC44.1kHz/96kbps, 영상은 CameraX의 실제 audio경로다. 분석의DC/80Hz필터는 원음 재생에 적용하지 않는다. 이 피드백만으로 자동증폭/임계값을 바꾸거나 수음 개선이 입증됐다고 보고하지 않는다.

### 산출물

| 파일 | SHA256 |
|---|---|
| `Agattok-0.5.7-test-code21.apk` | `2db0646f9cfc8528aa5cb84748e4f95b9b25e0a54c7e3ce790a4fda328d8cedb` |
| `Agattok-0.5.7-release-code21.apk` | `71367dbb5551aa2767f8ec384f9efdd4165bad4d8a2f46c830563c05890e1bef` |
| `Agattok-0.5.7-release-code21.aab` | `aacd06c12ac7db947de3a1799650393f1bd778c6f170aa6e808957bf7b902b16` |

다운로드 전달본 `아가톡 테스트/아가톡-전체기능테스트.apk`는 preview와 SHA일치한다. Play 제출용은 release AAB만 사용한다. preview21 사용 후 정식 Play자동전환은code22 이상과 실제 Play서명/설치 경로 확인이 필요하다.
