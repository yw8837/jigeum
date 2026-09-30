# UltraQA Report

## Goal and success criteria

**앱:** 지금 내 기분이 그래 · **버전:** 0.3.0 / code 6 · **검증일:** 2026-09-30

사용자 요청 범위는 통합 빌드, 서비스 품질, UIUX와 오류 검증, 소스 커밋·푸시입니다. 출시·Play 제출·운영 서버 배포·실제 금전 구매는 이후 진행합니다. 판정은 이 로컬 통합 범위에 한정합니다.

승인 기준은 정상 무료 흐름, 기록 보존, 비정상 입력 방어, 취소·프로세스 재생성 복구, 유료 권한 방어, 화면 접근성과 빌드·패키지 검증입니다. 전용 에뮬레이터와 합성 데이터만 사용하며 사용자 원본 데이터·자격증명을 시험에 쓰지 않습니다. 최대 5사이클, 같은 실패 3회 반복 시 원인 진단 후 중단을 원칙으로 했습니다. 현재 결과는 구현 검토 후 수정·재검증한 **2번째 통합 사이클**입니다. 하네스 오류는 제품 오류와 구분합니다.

## 결과 요약

| 검증 | 결과 | 범위 |
|---|---|---|
| 통합 빌드 | PASS | debug APK, AndroidTest APK, release AAB, lintRelease |
| Android 계측 | **40/40 PASS** | 미디어 7, 문장 8, 저장/전송 11, 공유/PDF 8, 결제 6 |
| 결제 검증 서버 | **16/16 PASS** | 실패·취소·skip 0, 실제 localhost HTTP 포함 |
| UI/데이터 시나리오 | **12개 유형 PASS** | 아래 매트릭스, 최신 성공·실패 모두 증거에 보존 |
| Lint | 오류 **0**, 경고 **24**, 힌트 **2** | 스타일/KTX·의도적 동기 저장 확인·의존성 업데이트 알림 포함 |
| 패키지 | PASS | 디버그 서명, ZIP/ELF 16KiB 정렬, 모델/native 기준본 비교 |
| 시각 검토 | PASS | 일반/작은 화면/200% 글자/넓은 화면, 수정 후 후보 문장과 하단 여백 재확인 |

**현재 APK의 실제 구매는 비활성화 상태입니다.** 운영 검증 URL과 공개 키가 비어 있습니다. 가격을 지어내거나 검증 없이 이용권을 열지 않는 상태를 확인했습니다. PDF 생성기는 별도 계측 테스트로 검증했고, 실제 Play 이용권을 구매해 UI에서 PDF를 내보내는 종단 흐름은 검증하지 않았습니다.

환경: Windows, JDK 21.0.12, Gradle 8.14.3, AGP 8.13.2, Kotlin 2.2.10, API 35 x86_64 Google APIs 에뮬레이터. 앱 minSdk 26 / target·compileSdk 36. 계측 실행 13.58초(러너 8.748초), 최종 빌드 2분 18초. 물리 ARM 기기는 사용하지 않았습니다.

## Scenario matrix

증거 경로는 `Jigeum-QA-evidence-0.3.0.zip` 내부의 `evidence/` 기준입니다. 모든 테스트는 전용 설치와 합성 자료로 실행했습니다.

| ID | 의도·사용자/실패 모델 | 설정·명령/하네스 | 기대 신호 | 실제 결과·수정 | 근거 | 정리 |
|---|---|---|---|---|---|---|
| F1 | 무료 사용자가 편집·저장 연타·앱 종료 | `run_qa.py normal`, SQLite 직접 읽기 | 수정 초안 복구, 한 건만 저장, 무료 공유·재시작 기록 유지 | PASS. 실제 프로세스 종료 후 초안 복원, 연타에도 1건 | `results.jsonl`, `ui-steps.jsonl`, `normal-result.png`, `free-share.png` | 앱 내 전체 삭제·공유 캐시 비움 |
| F2 | 같은 상황 반복·리컴포지션·저장 실패 | PhraseEngineTests 8개, normal UI | 최근 9개 회피, 같은 세션 유지, 3개 후보 앞뒤 다양성 | PASS. 연속 인덱스 선택을 조합 비교로 교체, IO에서 저장 | `instrumentation.log`, `normal-result.png` | 문장 이력 prefs 초기화 |
| F3 | 손상·초대형 JSON·충돌·미래 시각·유니코드·선택 중 종료 | RecordTransferTests 11개, migration/transfer/transfer_e2e | 원자 저장, 기존 기록 보존, 안전한 미리보기/병합, 복구 | PASS. 실제 0.2.1→0.3.0 이전, 선택 중 프로세스 종료 뒤 백업 완료, 재가져오기 중복 0, 미래 기록 후 새 기록은 현재 시각 | `instrumentation.log`, `ui-steps.jsonl`, `import-preview.png` | 합성 DB기록·Downloads 9개 제거 |
| F4 | 긴 한글·이모지·페이지/기록 상한·탭 이동 | MemoryArtifactsTests 8개, album/normal UI | 글자 유실 없음, 부분 파일 정리, 상태 유지, 공유 범위 제한 | PASS. 300쪽/300기록 상한, PDF 마지막 페이지 렌더 확인, 표지·메모 선택 유지 | `instrumentation.log`, `album.png`, `paywall.png` | 캐시 비움, 시험 출력 제거 |
| F5 | 이용권 변조·만료·거절·Google 장애·순간 몰림 | PurchaseVerifierTest 6개, `npm test` 16개, album UI | 미검증 잠금, 장애/거절 구분, 제한·시간초과 뒤 회복 | PASS. 60개 동시 요청 중 테스트 슬롯 4개 처리/56개 즉시503, health 정상, 이후 회복; 동일 토큰20개 조회 병합 | `billing-node-tests.log`, `instrumentation.log`, `paywall.png` | localhost 서버/소켓·임시 키 정리 |
| F6 | 권한 거부·취소·후면/전면·손상/무음 영상 | 미디어 계측7개, permission/camera/video/invalid UI | 수동 대체, 10초 정지, 배경 녹음 중지, 재진입, 초과 파일 거절 | PASS. 카메라4회 전환, 오디오/무트랙/무음 영상, 30초·50MB 초과/손상 파일 거절 | `video-audio.png`, `camera.png`, `ui-steps.jsonl` | 전용 설치 권한 초기화·영상 fixture 제거 |
| F7 | 작은폰·큰글자·가로/넓은 화면·반복 진입 | layout/confirm-layout/stress, 화면 직접 검토 | 저장 버튼 접근, 가림 없음, 경로10회 정상 복귀 | PASS. 200% 글자, 360dp 작은 화면,1280×720/160dpi rail, 넓은 화면 안전 여백 수정 | `font200-result.png`, `small-result.png`, `wide-album-confirmed.png`, `stress-meminfo.txt` | 크기/밀도 override 초기화, font 1.0 |
| F8 | 잘못된 성공 판정·비밀 유출·백업/공유 경계 | build/lint/check_package, 로그 종료코드, staged source 검사 | 실제 APK·AAB 검사, 무서명 표시, 키 제외, 앱 비정상 종료 없음 | PASS. 9개 백업 도메인 양쪽 제외, 제한된 FileProvider, INTERNET 확인, baseline native/model 동일 | `build-verified.log`, `package-verdict.json`, `elf-*.txt`, `apk-signature.txt` | 테스트 APK 제거, 에뮬레이터 종료, 실행 상태 정리 |

12개 유형: normal, migration, transfer, layout, permission, camera, video, invalid, transfer-e2e, album, confirm-layout, stress. 시각·레이아웃 기본 시험은 최종 후보 직전 빌드에서 수행했고, UI 수정 영향 영역(normal/album/confirm-layout) 및 영상·비정상 입력·전송·stress는 최종 APK에서 다시 확인했습니다. 모든 유형을 최종 APK에서 새로 처음부터 실행했다는 의미는 아닙니다.

명령/JSON은 제품 데이터로만 처리합니다. 모델에 외부 지시를 전달하는 LLM 도구 실행 경로가 없어 프롬프트 주입을 통한 명령 실행 시나리오는 해당하지 않습니다. 대신 중복 키·잘못된 타입·제어문자·초대형 토큰·CSV 수식 입력을 검증했습니다. 기존 0.2.1 산출물과 작업 폴더의 무관한 파일은 보존했습니다.

## Commands run

| 명령 | 목적·제한 | 최종 결과 |
|---|---|---|
| `gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:bundleRelease :app:lintRelease --max-workers=1 --console=plain` | 실제 설치/시험/릴리스 산출물, 단일 worker | exit 0, BUILD SUCCESSFUL, 2m18s |
| `python work/final-release-20260930/instrument.py` | ADB 계측, 600초 상한 | exit 0, OK (40 tests), INSTRUMENTATION_CODE -1은 러너 정상 완료 코드 |
| `npm test` (`outputs/PlayVerifier`) | node:test, 제한된 loopback 요청·타이머 | exit 0,16 pass/0 fail/0 skip |
| `python work/final-release-20260930/run_qa.py <mode>` | 모드별 UI, UI 대기15초·개별 ADB20~25초 상한 | 각 유형 최신 결과 pass; 과거 실패는 아래 설명 |
| `python work/final-release-20260930/transfer_e2e.py` | 실제 DocumentsUI·프로세스 종료·DB 검사 | exit 0,pass |
| `python work/final-release-20260930/check_package.py` | APK/AAB와 컴파일 manifest 실제 읽기 | exit 0,pass |
| `python work/final-release-20260930/cleanup.py` | 전체 삭제·공유 캐시·권한·콜드 시작·crash buffer | exit 0,pass,콜드 시작3회 정상 |

실행 명령별 종료코드·시간·출력은 `commands.jsonl`, 빌드/계측/Node 독립 로그에 보존했습니다. 단순 exit 0만으로 성공 처리하지 않고 테스트 수, runner 결론, JSON 결과와 UI/DB 상태를 같이 확인했습니다. portable 재현 도구는 `outputs/QA`에 있으며 경로 변경 후 10개 Python 파일 구문 검사를 통과했습니다. 이 portable 복사본 전체를 새 환경에서 다시 실행한 것은 아닙니다.

## Failures found / Fixes applied

| 문제·사용자 영향 | 원인·수정 위치 | 재검증 |
|---|---|---|
| 후보 세 문장의 시작이 비슷해 반복감 발생 | `PhraseEngine.kt`: 연속 조합 인덱스를 앞/뒤 다양성 비교로 변경, 최근9개 회피 유지 | 문장 계측8개·최종 결과 화면 |
| 미래 날짜 import 이후 새 기록도 미래로 저장 | `MainActivity.kt`: 가져온 최대ID 기준 제거, 현재 시각에서 실제 충돌만 회피 | transfer-e2e 실제 import/새 저장 |
| 손상/큰 입력·중복/동시 작업에서 데이터 보존 위험 | `ObservationStore.kt`, `RecordTransfer.kt`: IO·mutex·트랜잭션,10,000건·48MiB·문자토큰8192byte 한도, 충돌/stale preview 재확인 | 동시60쓰기·9,999건 실패 원자성·128KiB 토큰·중복/UTF-8 시험 |
| 화면 이동/프로세스 재생성 중 백업·공유 콜백 위험 | `MainActivity.kt`, `LibraryScreens.kt`: 루트 scope, SAF callback에서 영속 저장소 조회, 연타 guard | 선택 중 프로세스 종료·취소·재가져오기 |
| 긴 문장의 PDF/PNG 잘림·부분 파일·캐시 삭제 경합 | `MemoryArtifacts.kt`: 줄 단위 페이지 나눔, 크기 맞춤, 상한, export/cleanup 직렬화 | 마지막 PDF 페이지 실제 렌더·300쪽 거절·무료 PNG |
| 이용권 만료/늦은 응답·중복 결제 상태 경합 | BillingManager/BillingModels/PurchaseVerifier: generation, 검증 중복 방지, 명시적 만료와 거절/장애 구분 | 계측6개·Node16개; 실제 Play callback은 남음 |
| 검증 서비스가 느린 요청/동시 몰림에 점유될 위험 | PlayVerifier:16KiB 본문·수신5초·검증16초·Google7초, 기본32처리·256연결, 요청 병합 | 60동시 요청·408/413/429/503·회복 |
| 유료 화면 왕복 후 선택/스크롤 유지와 터치 문제 | LibraryScreens/MainActivity: SaveableStateProvider, 메모 전체행 toggle, 하단 navigation inset | album·wide 재확인 |

초기 빌드에는 편집 중 들여쓰기 lint 오류가 있었고 수정 후 통과했습니다. 경고를 숨겨 통과시키지 않았습니다.

### 하네스·환경 오류 구분

- Unicode 기본 AVD 경로에서 에뮬레이터가 멈춰 영어 경로 `C:\\MoaAVD`로 전용 AVD를 실행했습니다.
- DocumentsUI의 가려진 배경 `Downloads` 노드를 눌렀습니다. 열린 drawer의 마지막 일치 노드를 선택하도록 수정했습니다.
- 저장 버튼의 실제 문구는 `SAVE`인데 `Save`를 기다렸습니다. 실제 표기로 수정했습니다.
- album 복귀 시 유지된 스크롤 밖의 제목을 기다렸습니다. 화면 안의 표지 선택 문구로 검사했습니다.
- Compose 선택 항목이 접근성 XML에서 `checked=true`인데 `selected=true`로 검사했습니다. 해당 **라벨의 부모 checked 상태**를 확인하도록 수정해 표지와 메모를 각각 검증했습니다.
- UiAutomator 초기 null root, Python 모듈 이름 충돌, shell quoting 오류는 하네스 설정 오류로 기록했습니다. 수정 뒤 관련 시나리오를 다시 실행했습니다.
- 종료 정리에서 OMX `clear`의 응답 필드가 `success`가 아닌 `cleared`인 점을 잘못 검사했습니다. 필드 검사를 수정했고 재확인에서 `exists:false`와 실제 상태 파일 부재를 확인했습니다. 에뮬레이터·시험 파일 정리는 정상 완료됐습니다.

실패 기록은 `results.jsonl`에서 삭제하지 않았습니다. 동일 실패가 무한히 반복될 때까지 재시도하지 않고 원인을 고친 뒤 관련 시나리오를 실행했습니다.

## Cleanup and rollback

합성 기록은 앱의 ‘모든 기록 삭제’로 제거하고 SQLite 0건, 월령·이전 복구 사본·문장 이력 제거, 공유 캐시 비움을 확인했습니다. Downloads의 정확한 `jigeum-qa-*` 소유 파일9개와 UI dump만 제거하고 이전의 무관한 파일은 보존했습니다. 표시 크기/밀도 override와 글꼴을 복구하고 카메라·마이크 권한을 초기화했습니다. 테스트 패키지를 제거하고 앱의 빈 홈을 다시 확인했습니다. 콜드 시작3회는 모두 정상이며 Activity TotalTime은 2,797/2,437/2,930ms입니다. 에뮬레이터 환경의 참고값이며 실기기 성능 보장이 아닙니다. crash buffer에서 앱 항목은 0건입니다.

실행 증거는 의도적으로 보관합니다. raw SQLite snapshot·이전 prefs 사본·내보낸 JSON·임시 fixture는 Git/증거ZIP에서 제외하고 실행 임시본을 제거했습니다. 비밀키·서비스 계정·앱 사용자 데이터·APK/AAB·캐시는 Git에 넣지 않습니다. 전용 에뮬레이터는 종료했고, OMX ultraqa 상태는 현재 실행 폴더에서만 완료 후 clear하여 부재를 확인했습니다. 다른 작업의 상태는 보존했습니다. 실제 정리 판정은 `cleanup-result.json`과 `lifecycle-result.json`에 기록했습니다.

## Residual risks

1. **실제 Play 구매/보류/취소/복원/환불/계정 전환은 미검증입니다.** 개발자 계정 인증·상품·운영 검증 설정이 남아 있습니다. 현 APK는 실제 결제를 막습니다.
2. **물리 ARM 기기는 미검증입니다.** 마이크 품질, 카메라 수명주기, 발열·배터리, OEM 파일 선택기/기기 이전, 실제16KB ARM 런타임은 확인해야 합니다. minSdk26 실행도 이번 API35 시험으로 대체하지 않습니다.
3. **서버 운영 용량은 측정하지 않았습니다.** 로컬 60요청은 제한·복구 검증입니다. 다중 인스턴스, Google quota, 프록시 뒤 공유IP 제한은 운영 환경에서 설정/측정해야 합니다. 개인정보/아기 데이터 외부 DB는 없습니다.
4. **오프라인 권한 캐시에는 최대24시간 환불 반영 지연이 있을 수 있습니다.** 설정된 유효기간 이후에는 재검증이 필요합니다. 무로그인 방식의 기기시간 조작·변조 클라이언트 위험과 고정 공개 키 교체는 운영 정책에서 관리해야 합니다.
5. **문장 의미 정확도를 입증한 시험은 아닙니다.** 유한한 표현 예시이며 최근 반복 완화는 장기 무반복 보장이 아닙니다. 부모 관찰과 편집이 필요합니다.
6. Lint24경고/2힌트, 코드/리소스 축소 비활성화로 인한 APK 크기, 실제 보호자 대상 사용성은 후속 개선 항목입니다. 공유 기능은 Android chooser 표시와 파일 생성까지 확인했으며 외부 수신 앱의 모든 저장 동작은 시험하지 않았습니다.

## Evidence

- `Jigeum-QA-evidence-0.3.0.zip`: 빌드/계측/서버/패키지 로그, UI 화면과 동작 trace, 정리 결과.
- `Jigeum-Android-0.3.0.sha256.txt`: 최종 APK·AAB·소스ZIP·증거ZIP의 SHA-256.
- 최종 APK SHA-256: `bed16f4e0f3ba5d35ad50e9f35a17884c3885a7b5d1965e698633623cb1c006d`.
- `QA/README.md`: 새 시험 환경에서 재현하는 순서와 전제.
- `PlayVerifier/QA-2026-09-30.md`: HTTP·토큰·타이머 방어 시험 상세.

**ULTRAQA COMPLETE: Goal met after 2 cycles** — 로컬 통합 범위. 종료 근거는 `lifecycle-result.json`입니다. 이 보고서는 출시 승인이나 실제 Play 거래 검증 완료를 뜻하지 않습니다.
