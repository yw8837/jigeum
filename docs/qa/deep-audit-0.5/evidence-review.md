# 0.5 QA 증거·하네스 독립 감사

2026-10-01. 감사 시작 기준 `4d9894440a2fa36911d7ac46659c3b133dc76144`.
원본을 읽은 뒤 확인한 내용이다. 아래 테스트 공백은 제품 결함을 재현했다는 뜻이 아니다. 이 담당자는 Gradle·ADB를 실행하지 않았다.

## 원시 결과 확인

- `docs/qa/0.5.0/instrumentation.txt`: 시작 상태149, 통과146, assumption skip3. 실패 상태0. 최종 `OK (149 tests)`와 `INSTRUMENTATION_CODE: -1` 확인. 이전 보고서의146통과·3skip 구분은 맞다.
- skip은 `PublicAudioProbeTest#reportPublicSoundEventProbes`, `PurchaseVerifierTest#unconfiguredManagerSurvivesRefreshRestoreAndRepeatedClose`, `PurchaseVerifierTest#missingConfigurationCannotUnlockPremium`이다. 모든 클래스의 패키지는 `com.moa.babylanguage`.
- `font200-tests.txt`: 최종 `OK (3 tests)`. ControlledAnalyzer 기반 UI검사로, 실제 아기 분류 검사는 아니다.
- `server-tests.txt`:56통과,0실패·취소·skip. 실제 Play거래의 증거는 아니다.
- 로컬 `work/ux-050/package-final/aab-signature.txt`: `jar verified.`. self-signed/체인 경고는 일반 Android 업로드 인증서 특성과 구분해야 한다. 기존 AAB가 unsigned였다는 증거는 없다.
- 같은 폴더의 `apk-signature.txt`: APK v2서명 검증 성공. 실제 APK와 AAB SHA256을 다시 계산해 보고서와 일치함을 확인했다: APK `b150b344256b49ff3c10be364f99e10daa7f2e2f5c2d0a97e557539774263f05`, AAB `c57abc48a6f533eef23515b8a2c190336f955467bf635cc170dd9b8865dca6a4`.

## 확인된 하네스 결함

1. **P2: 불완전 계측 결과를 성공으로 인정한다.** `outputs/QA/instrument.py:13`의 기존 parser는 `OK (N tests)`와 exit0만으로 성공 가능하다. N과 상태 수/identity/최종 instrumentation 종료코드 일치를 검사하지 않는다. 중복 통과, 끝나지 않은 테스트, 전체skip, 미예상skip도 성공할 수 있다. 실제149로그가 잘못됐다는 뜻은 아니며 이후 회귀 게이트의 거짓양성이다.
2. **P2: unsigned AAB도 기본 검사를 통과한다.** 기준본 `outputs/QA/check_package.py:78`은 `jarsigner -verify`가 exit0으로 내보내는 `jar is unsigned`를 기록만 한다. `--expect-unsigned-aab`이 없을 때 서명을 필수로 요구하지 않는다. code13 AAB는 원시 로그에서 서명 검증 성공했으므로 과거 산출물 결함과 구분한다.
3. **추적성 공백:** `instrument.py`와 `capture_ui.py`는 실행한 APK/test APK hash·git SHA·설치된 패키지 version을 판정에 연결하지 않는다. `instrument.py --install-app`도 선택 사항이다. 이번 full-run에는 두 install 성공 로그가 있지만 CI 재사용 시 오래된 target APK에 현재 test APK를 돌릴 수 있다. 다음 실행은 app/test hash와 설치한 후보를 기록해야 한다.

## Android 회귀검사의 공백

- `AccessNavigationTests.kt:127,188`: `cancellingAnalysisIgnoresLateCallback`은 cancellable `gate.await()`에서 취소된다. 취소 후 `complete()`해도 늦은 분석 결과를 반환하지 않는다. 취소에 비협조적인 분석기가 실제로 반환한 것을 별도 latch/counter로 확인하고 결과 미게시를 검사해야 한다. expiry검사도 같은 대역을 사용한다.
- `CaptureDeviceTest.kt:42`: `realVideoRecordsAudioPlaysAndPrivateFileCannotBeShared`는 video 메타데이터·파일 크기·UI 재생문구만 확인한다. 녹화의 audio track 존재와 decode 가능성을 검사하지 않는다. 실제 microphone 입력/에너지는 에뮬레이터의 제약과 구분하되, audio track과 PCM sample 수는 검증 가능하다.
- `AccessNavigationTests.kt:135`: 선택구간 재생은 `재생 멈추기 · 0:08` UI를 확인한다. 실제 재생 위치 증가/11초 종료를 확인하는 검사는 별도 필요하다. 실제 decoder8–11초검사는 존재하므로 선택구간 PCM crop에 대한 근거는 있다.
- `VideoAudioTest.kt:64`:20ms timeout은 dispatcher가 decode에 진입하기 전에 걸려도 통과한다. codec 획득 뒤 취소되었다는 관찰 지점이 없으므로 이름의 'codec release'를 확증하지 못한다. timeout 취소 후 다음 decode가 성공했다는 범위까지만 증명한다.
- `CaptureDeviceTest`와 release UI는 시작 전에 CAMERA/RECORD_AUDIO를 모두 grant한다. 0.5의 권한 거부·부분 허용·시작 직전 회수에 관한 현재 UI회귀는 없다.
- 새 CaptureFiles.import의100MB/60.5초 제한, 불완전 stream, 가져오기 중 백그라운드/취소·재진입 검사는 없다. 기존 RecordTransferTests는 기록 JSON이며 영상 가져오기와 다른 경로다.
- `ProtobufRuntimeTest`의 FaceLandmarker검사는 빈 bitmap을 정상 처리하는 smoke다. 실제 얼굴이 있는 영상의 선택시간/회전/관찰 결과에 대한 end-to-end 회귀는 없다.

## 과대 해석하면 안 되는 결과

공개 음성 측정은 울음1건 문장, 웃음1건 보류, 옹알이 미실행이다. `PublicAudioProbeTest`는 측정 결과를 저장할 뿐 분류 성능을 assert하지 않는다. 기존 보고서는 이 제한을 명시했으며,149개 테스트나56개 서버 테스트를 장면 적합성·부모 만족도·유료 품질 증거로 바꿔 말할 수 없다.

## 이 감사에서 실행할 하네스 회귀

수정 전에 합성 원시로그를 입력하는 Python unittest로 incomplete/duplicate/skip/abnormal completion을 실패 재현한다. AAB는 실제 unsigned 파일을 배포하거나 기존 후보를 바꾸지 않고 jarsigner 반환 문자열의 검증계약을 단위검사한다. 이후 parser와 signature검증을 수정하고 재실행 결과를 별도 보존한다. 기존0.5.0 보고서와 원시 증거는 덮어쓰지 않는다.

## 하네스 수정과 검증 결과

- `instrument.py`는 이제 시작/종료 이벤트의 test identity·중복·발견수·최종 summary·정상 runner종료를 함께 확인한다. skip은 기본 실패이며 정확한 `--allow-skip CLASS#METHOD` 목록으로만 허용한다. 전체skip은 허용목록이 있어도 실패한다. 발견수와 통과/skip수는 계속 분리한다.
- `check_package.py`는 명시적인 `jar verified.`를 요구하며 unsigned/부분unsigned를 기본 실패로 판정한다. unsigned 후보는 기존 `--expect-unsigned-aab`을 명시한 경우에만 별도 의도로 허용한다.
- `test_qa_gates.py`15개 호스트 단위검사: 수정 전 기존 parser의 거짓성공7건을 실제실패로 재현했다. 추가 API/helper가 아직 없어 발생한8개 error는 제품/하네스 결함 재현과 구분한다. 수정 후15/15통과. [수정 전](harness-red.txt), [수정 후](harness-green.txt).
- 새 parser에 기존 code13 원시로그와 명시된3개skip목록을 넣어 다시 확인했다.146통과·3skip·validation error0. 기존 AAB서명 로그도 통과. [이전 증거 재판정](historical-reparse.json). **새 기기 실행을 한 결과가 아니다.**
- 설치 APK와 실행 증거의hash연계, 실제 분석기 지연 콜백, 녹화 audio track/재생경계, 권한·영상가져오기 악조건은 이번 하네스수정으로 검증된 것으로 취급하지 않는다.
