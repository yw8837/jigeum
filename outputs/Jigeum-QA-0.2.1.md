# UltraQA Report

## 최종 저장 정책 보강 — 2026-09-30

사용자 지시에 따라 외부 DB 없이 온디바이스 저장으로 확정했다. Android 12+의 `data_extraction_rules.xml`에서 `root`만 제외하던 규칙에 실제 기록 영역인 `sharedpref`와 나머지 파일·DB·기기 보호 영역을 명시적으로 추가했다. 클라우드 백업·기기 이전 양쪽에 적용했다. 이는 설정 누락 보강이며 실제 외부 유출을 관측했다는 뜻은 아니다.

- 이 변경 이후 APK/AAB/린트 재빌드 성공. 린트 0 errors / 12 warnings / 2 hints.
- 빌드된 APK의 백업 규칙에서 두 방식 각각 9개 영역 제외, `allowBackup=false`, `fullBackupContent=false`, INTERNET 권한 없음 확인.
- APK 서명, APK ZIP 및 APK/AAB ELF 정렬 재검사 통과. AAB는 계속 미서명이다.
- 새 APK를 에뮬레이터에 업데이트 설치하고 실행 → 기록 생성 → 전체 기록/초안 삭제 → 개인정보 문구 확인 → 재시작 시험 통과. 시험 기록은 다시 비웠다.
- 이전 전체 QA의 Kotlin 소스·네이티브 라이브러리·모델이 이번 빌드에서도 동일함을 비교했다. 아래 7개 계측·6그룹 UI 결과는 백업 XML 보강 전 전체 QA이며, XML 변경 뒤에는 위 설정·설치·기록 smoke 검증만 다시 수행했다.
- 제조사별 실제 기기 이전 도구의 동작은 미시험이다. 자동 백업과 외부 DB를 사용하지 않아 앱 삭제·기기 분실 시 서버 복원은 불가능하다. 파일 내보내기·가져오기는 아직 후속 기능이다.

근거: `build-ondevice.log`, `ondevice-settings-verdict.json`, `ondevice-backup-rules-apk.txt`, `ondevice-manifest-apk.txt`, `ondevice-install.txt`, `ondevice-smoke.log`. 검증 스크립트의 첫 실행은 aapt2의 false 표기 형식 차이를 잘못 가정해 중단됐고, 파서 수정 후 전체 검사를 통과했다. 이 하네스 실패를 제품 실패 또는 제품 통과로 계산하지 않았다.

## 목표와 판정 범위

`지금 내 기분이 그래` 0.2.1, versionCode 5. 설치 뒤 계정·API 키·서버 없이 소리/영상 관찰 → 보호자 확인 → 문장 예시 → 수정·기록을 사용할 수 있는 Android 설치 빌드를 검증한다.

시험 환경: Windows, JDK 21, Gradle 8.14.3, Android SDK 36, API 35 x86_64 에뮬레이터 `emulator-5554`. 실제 아기 자료 대신 단색 영상·합성 소리와 테스트 문장을 사용했다. 생산 서비스·계정·사용자 휴대전화에는 접근하지 않았다. 동일 원인 3회 또는 제품 QA 5사이클을 중단 조건으로 삼았다. 자동화 준비 오류와 제품 결함은 구분했다.

**최종 판정: 설치용 MVP 검증 통과. ULTRAQA COMPLETE: Goal met after 2 cycles.** 최종 APK를 다시 설치한 뒤 계측 7/7과 기록 생성·전체 삭제·개인정보 문구·재실행을 추가 확인했다. ARM 실기기와 Play 스토어 공개 제출은 이 검증 범위에 포함되지 않는다.

## 시나리오 매트릭스

| ID | 의도·사용자/오류 모델 | 설정·명령·예상 신호 | 실제 결과·수정 | 증거 | 정리 |
|---|---|---|---|---|---|
| Q1 | 일반 보호자·반복 탭·프로세스 종료 | 수동 입력→문장 수정→홈 이탈→`am kill`→복원→저장 연타→앱 재시작→삭제 | 통과. 실제 프로세스 종료를 확인했고 편집 문장 유지, 기록 정확히 1개, 삭제 뒤 빈 목록 | `normal.log`, `ui-steps.jsonl`, `saved-record.png` | 생성 기록 삭제 |
| Q2 | 영상 소리·무음·무트랙 | 합성 AAC 영상에서 후보 표시, 소리 확인 후 문장; 무트랙/무음 별도 표시 | 통과. 실제 YAMNet이 합성 효과음에 울음·옹알이 후보 표시. 이 점수는 영아 의도 정확도가 아님 | `video.log`, `video-audio.png`, `model-cues.log` | 합성 QA 파일 제거 완료 |
| Q3 | 손상·과대 입력 | 32초·51MiB·손상 영상 거부, 다시 선택 가능 | 통과. 각각 고유 오류 문구와 재선택 가능 상태를 UI 로그에서 확인 | `invalid.log`, `ui-steps.jsonl` | QA 파일 제거 완료 |
| Q4 | 취소·재진입 | 디코드 20ms 제한 취소 3회 후 정상 재분석, 화면 닫기/재진입 | 통과. 디코더 취소 후 복구, 영상 화면을 닫고 다시 들어오면 이전 결과가 없음 | `instrumentation-final.log`, `invalid.log` | 화면/작업 종료 완료 |
| Q5 | 카메라 반복 사용 | 후면 시작→전후면 6회→문장→닫기→다시 시작→관찰 종료 | 통과. 렌즈 표시와 결과·종료 확인. 단일 렌즈 fallback은 코드 검토만 수행 | `camera.log`, `camera-back.png`, `camera-switched.png` | 카메라 종료 |
| Q6 | 권한 거부·회복·백그라운드 | 마이크 거부→수동 입력, 권한 복구→10초 자동 종료→다시 녹음→홈 이탈 | 통과. 안내와 대체 경로 제공, 이탈 시 녹음 중단 | `permission.log`, `ui-steps.jsonl` | 설정 복원·시험 앱 권한 유지 확인 |
| Q7 | 접근성·화면 구성 | 글자 크기 200%에서 입력/저장/삭제, 가로 회전 시 결과 유지 | 통과. 버튼 접근과 결과 유지 확인, 큰 글씨 스크린샷도 육안 검토 | `largefont.log`, `large-font-result.png`, `landscape-result.png` | 원래 폰트·회전 설정 복원 |
| Q8 | 빌드·패키지·오해를 부르는 성공 로그 | APK/AAB/린트, 실 APK 버전·권한·서명, ELF/ZIP 정렬 검사 | 빌드 성공. 린트 0 errors/12 warnings/2 hints. APK 서명·16KB 정렬 성공, INTERNET 없음. AAB는 미서명 | `build-final.log`, `apk-badging.txt`, `apk-signature.txt`, `elf-*.txt`, `zipalign.txt`, `aab-signature.txt` | 최종 패키지 해시 확인 완료 |

이 앱에는 외부 프롬프트나 명령 실행 기능이 없어 prompt injection 시나리오는 적용하지 않았다. 네트워크·인증·서버가 없으므로 서버 과부하 시험도 적용하지 않았다. 기기 내 작업은 오디오 모델 Mutex, 디코더 Mutex, 최대 10초/160,000 샘플, 영상 30초/50MiB, 인코딩 오디오 12MiB 상한으로 제한한다.

## 실행 명령과 결과

- Gradle `:app:assembleDebug :app:assembleDebugAndroidTest :app:bundleRelease :app:lintRelease`: exit 0. APK/AAB를 실제 생성했다. 최종 빌드 로그는 `build-final.log`.
- `:app:testDebugUnitTest`는 **NO-SOURCE**. JVM 단위 테스트가 통과했다고 계산하지 않았다.
- `adb shell am instrument -w com.moa.babylanguage.test/androidx.test.runner.AndroidJUnitRunner`: **OK (7 tests)**. adb exit 0만 믿지 않고 테스트 러너 본문의 성공·실패를 확인했다.
- 7개 계측 항목: 48kHz stereo→16kHz mono/최대 10초, 44.1kHz 음높이·길이 보존, 무트랙과 무음 구분, 손상 파일 후 복구, 취소 3회 후 복구, 내장 YAMNet 실추론, 1초 미만 녹음 안내.
- 화면 자동화 `run_qa.py normal|permission|camera|video|invalid|largefont`: 각 adb 호출 12~25초 제한, 화면 대기 15~40초 제한, 스크롤 횟수 상한. 원본 명령·종료코드·상태는 `commands.jsonl`, 시나리오 결과는 `results.jsonl`에 남겼다.
- `apksigner verify`, `zipalign -c -P 16 -v 4`, APK/AAB의 ELF PT_LOAD 검사: exit 0. 정적 정렬 통과는 ARM 16KB 실기기 실행을 대신하지 않는다.
- 이전 0.2.0-rc1 디버그 APK와 새 APK의 서명 인증서가 일치한다. 별도 임시 서명으로 설치한 시험 앱과는 다를 수 있다.

## 발견한 결함과 수정

1. **AAC 시작 시각이 음수인 파일을 빈 소리로 처리:** 인코더 priming의 유효 음수 PTS를 파일 끝으로 오인했다. `VideoAudioDecoder`에서 실제 트랙 소진(`sampleTrackIndex < 0`)과 구분했다. 수정 전 계측 7개 중 5개 실패, 수정 후 7개 통과. 길이·주파수·무음 검증이 이 회귀를 포착한다.
2. **영상 프레임 자원 정리 및 실패 표시:** `MPImage.use`로 이미지 컨테이너를 닫고 bitmap 이중 recycle을 방지했다. scaling도 finally 보호 범위로 옮겼다. 얼굴 모델이 생성됐지만 모든 detect가 실패한 경우를 '얼굴 없음'으로 표시하지 않도록 실제 성공 횟수를 집계한다.
3. **입력/결과 수명:** 편집 초안을 `rememberSaveable`로 복원하고, 저장 연타 가드와 비정상 초안 복원 시 홈 이동을 추가했다. 전체 기록 삭제 시 남아 있던 초안도 해제한다. 다시 입력하는 링크의 문구도 동작에 맞게 바꿨다.
4. **녹음·추론 취소:** NonCancellable 경로를 제거하고 화면 이탈·취소 시 늦은 결과가 UI에 반영되지 않게 했다. 모델 동시 실행을 직렬화하고 관리 PCM 버퍼를 초기화한다. 모델 실패와 '후보 없음'을 구별해 수동 입력을 안내한다.
5. **표시/정책:** 버튼의 고정 높이를 최소 높이로 바꿨다. SDK 내부 복사본의 즉시 완전삭제를 보장하지 않도록 개인정보 문구를 '메모리에서 임시 처리, 원본 파일 미보관'으로 정확히 고쳤다.

자동화 준비 오류도 별도 수정했다. 에뮬레이터의 과거 임시 릴리스 서명과 새 디버그 서명이 달라 처음 설치가 거부됐고, 시험 에뮬레이터에 새로 설치했다. JUnit 함수 반환형 오류, 텍스트 필드 전체 선택, 시스템 파일 선택기의 미색인 파일/외부 화면 유지도 하네스에서 바로잡았다. 이 실패들을 앱 기능 통과로 계산하지 않았다.

## 독립 검토

기능·코드·보안 관점의 별도 에이전트 검토를 받았다. MPImage 정리와 얼굴 분석 실패 오표시를 수정한 뒤 직접 설치 후보에 대한 코드 승인을 받았다. 각 검토자는 실행하지 않은 동적 시험을 자신의 검증으로 주장하지 않았다.

## 남은 한계

- 결과는 소리 유형과 보호자가 선택한 상황에 따른 **고정 표현 예시 3개**다. 실제 아기의 욕구·감정·건강을 해석한 정답이 아니다.
- 실제 ARM 휴대전화, Android 8.0 최소 버전, Android 16, 물리 16KB 페이지 기기, OEM별 카메라/코덱, 장시간 발열·배터리는 미시험이다.
- 기록은 SharedPreferences의 전체 JSON을 읽고 쓴다. 매우 많은 기록을 쌓는 장기 사용에서는 저장 지연이 생길 수 있다. 대규모 기록 장기 부하는 이번 시험 범위 밖이다.
- MediaCodec/MediaPipe 등의 네이티브 단일 호출을 coroutine 취소나 루프의 12초 제한으로 강제 중단할 수는 없다. 정상 반환 후 리소스를 해제한다. 녹음 종료 중 reader join 때문에 최악 약 1초 UI 지연이 생길 수 있다.
- 영상은 키프레임을 최대 5회 샘플링하므로 같은 장면을 중복해서 볼 수 있다. 원본의 모든 장면을 연속으로 이해하는 기능이 아니다.
- 릴리스 R8 축소는 기존 MediaPipe 호환 문제 때문에 꺼져 있다. APK는 디버그 설치 빌드이고 AAB는 미서명이다. Play 제출에는 소유자 업로드 서명, 공개 정책 URL·운영자 정보, 콘솔 등록 및 실기기 시험이 남아 있다.

## 정리와 근거

시험 영상 6개와 계측 테스트 앱을 에뮬레이터에서 제거했고 생성 기록은 비웠다. 폰트 1.0·자동회전 1·세로 회전 0으로 원래 설정을 복원했다. 마이크·카메라는 이 시험 앱에 허용된 상태로 남겨 바로 체험할 수 있게 했다. 앱의 홈 화면을 남겼으며 사용자 휴대전화나 계정에는 설치·변경하지 않았다. 이 실행의 UltraQA 상태는 CLI로 완료 후 clear했고 없음까지 확인했다.

구버전 설치/소스 배포 파일과 중복 문서 11개를 경로 검증 후 Windows 휴지통으로 옮겼다. 영구 삭제하지 않았고 현재 프로젝트·홍보 영상·이전 QA의 역사적 증거는 보존했다. 로그와 재현용 하네스는 증거 ZIP에 보관한다. 새 산출물의 체크섬은 `Jigeum-Android-0.2.1.sha256.txt`, 실행 로그·UI 스냅샷·하네스는 `Jigeum-QA-evidence-0.2.1.zip`에 제공한다. 과거 QA 기록은 이번 결과와 혼합하지 않는다.
