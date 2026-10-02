# UltraQA Report · 소리·영상 관찰 0.5.9-beta/code23

2026-10-02 시작 · 기준 `efa5fd04db14f6139c80fd55dc32451ec21f4477` · [변경 계약](../../UX-SOUND-VISUAL-0.5.9.md)

## Goal and success criteria

실제 제공 영상의 보류를 재현하고 후보 모델을 비교한다. 근거 없는 아기말을 늘리지 않으면서 보류 안내·구간 재선택·시각 관찰·출처 저장을 개선한다. 무료60초/10초/100MB·전체120초/20초/200MB·베타개방·정식 entitlement를 유지하고 서명 패키지와 기존 Alpha 업데이트를 검증한다.

**영상 두 개의 아기 발성 누락은 미해결이다.** 모델과 음성 승인 임계값은 유지했다. 이번 QA의 완료 조건은 이 한계를 숨기지 않고 검증된 개선을 배포하는 것이며, 의도 정확도 또는 제공된 모든 영상 인식의 성공이 아니다. [기존 진단](../../research/20261002-private-video-diagnosis.md) / [후보 모델 비교](../../research/20261002-model-059-comparison.md).

전용 `MoaQA35 / emulator-5554`, Android35/x86_64. Gradle/ADB 담당자는 root 한 명. 원본·파생 미디어는 로컬 Git 제외이며 외부로 올리지 않았다. 최대5회·동일 실패3회 중단. 다른 프로젝트·사용자 계정·결제 자원을 변경하지 않는다.

## Scenario matrix

| ID | 의도·오류 모델 | 준비·명령 | 기대 신호 | 실제 결과·근거 | 수정·정리 |
|---|---|---|---|---|---|
| S01 | 제공 영상 인식 누락 | 같은 Android MP44구간·DeviceClipAnalyzer | 판정·보류 이유·실제 시각 결과 분리 | [4구간](evidence/private-probe-summary.json): 3보류·1울음 유지, 모두 시각 관찰,1개 quiet와7–12초 추천 | 임시 diagnostic/test source 및 기기 원본 정리 |
| S02 | 공개 음성/성인·음악·무음 | 실제 모델22개·strict 및 baseline 비교 | 기존 성능 숨기지 않음·새 반대판정 없음 | [이전 대비](evidence/public-regression.json):22개 판정 동일. **strict 라벨은 기존4불일치로 실패** [로그](evidence/public-strict-run.txt) | 임계값 변경 없음, 정답/정확도 주장 없음 |
| V01 | 입 모양·단일/다중 얼굴·가림·모델실패 | VisualFeatures059/VisualEvidenceAudit/SoundVisual059 | 실제 관찰만 제공, face-only 우회 금지 | [집중57통과](evidence/focused-result.json); 실제 noface 모델+로컬 얼굴 표본 |2얼굴 탐색·불명확 특징null·보류에도 시각 전달 |
| V02 | 화면 밖 아기·화면 속 성인 | synthetic sound + 구조화 face | 얼굴 행동을 아기에게 귀속하지 않음 | 코드리뷰에서 지적·요청형 표현으로 보완, 금지표현 회귀 통과 | requestedOffset을 실제PTS/립싱크로 쓰지 않음 |
| P01 | DB1·JSON1·미지출처·편집·회전·PNG/MP4 | ExpressionBasis10개 + 기존 전체 | LEGACY/AUDIO/AUDIO_VISUAL/CAREGIVER 유지 | 집중 테스트 통과·서명 이전 결과는 아래 | source 별도보존·새JSON2/기존v1읽기·oldMP4 미재작성 |
| U01 | 보류→추천→범위변경·권한축소·취소 | UI test와 기존 접근 회귀 | 탭으로 선택만 변경·자동분석/저장 없음 | 집중UI3통과; 전체 결과는 아래 | 취소/선택/옵션/권한에 보류상태 초기화 |
| A01 | 베타 전체·정식 재잠금·무료내보내기 | signed UI/패키지 |120/20·200MB 유지, release60/10·100MB | 최종 실행 결과는 아래 | 제출은 beta AAB만 |
| Q01 | 손상·대용량·취소·허위통과·dirty tree | 전체계측/host/lint | 실패·제외를 누락하지 않음 | 호스트30통과·집중57통과·전체 아래 | media/key/cache는 커밋 제외 |
| R01 | 기존 트랙 업데이트 | Play Alpha beta23 | 증가된code·서명·제출 상태 | [배포 노트](../../release/20261002/RELEASE-NOTES-059.md); 제출 결과 후 확정 | 프로덕션/상품/실거래 미변경 |

## Commands run

| 명령 | 결과·제한 |
|---|---|
| 격리 Windows LiteRT2.2.0, 공식 원본/현모델·동일PCM·975/480ms홉 |27파일 비교, 사용자실패4구간 미회복, 원본float 공개웃음/울음 회귀. 모델 미채택 |
| Gradle assembleDebug/assembleDebugAndroidTest | 성공, 현재 소스57개 집중계측 실행 |
| SoundVisual059/VisualFeatures059/VisualEvidenceAudit/ExpressionBasis/ExpressionBasisUi/SoundTimeline/EvidenceInterpreter |57통과·0실패·0제외, 실제 설치SHA 검증 |
| `python -m unittest discover -s outputs/QA -p test_qa_gates.py -v` |30통과 |
| `sound_probe.py --corpus … --strict` |실행 완료 후 선언라벨4건 불일치로 **exit1**. 동일22파일의 기존판정 대비 변화0. 이 실패를 성공으로 바꾸지 않음 |
| 로컬 MP4 diagnostic |4구간 모두 실제 소리/시각 파이프라인, 1.65–2.52초/구간(에뮬레이터 관찰치). 실제 의도 정확도·ARM 지연 아님 |

## Failures found and fixes applied

1. 음성 보류가 영상 관찰을 생략했다. 보류/무음/읽기 실패도 요청한 영상 관찰과 structured issue를 반환한다. 한마디 저장 가능한 가짜 결과는 만들지 않는다.
2. 출처가 없는 과거 기록과 영상 관찰만 한 결과를 구분할 수 없었다. 실제 문장에 반영한 근거를 독립 basis로 저장한다. 구형 자료는 LEGACY, 보호자 수정은 CAREGIVER다.
3. 일반 아기 클래스 미검출과 작은 소리를 같은 안내로 처리했다. 구조화 issue·측정 RMS·배경 우세를 구분한다. 소리 집중 구간만 선택 제안한다.
4. 초기 시각 추가문이 얼굴 주인을 아기로 단정할 수 있었다. 독립 검토2명 지적으로 요청형 표현으로 수정하고 실제 발성창 동시성 조건을 제거했다. 얼굴·소리 주인을 검증하지 않는다고 결과 근거에 명시한다.
5. 새 금칙어 테스트가 ‘잠깐’의 ‘잠’을 수면 표현으로 오판했다. 실행 전에 실제 수면 의미구로 고쳤다.
6. strict 공개 라벨 검사는 기존부터 보류/복합/어린말로 나오는4개를 실패로 보고한다. 현재모델 baseline22개와 판정이 모두 같으며 수정으로 숨기지 않는다.
7. 전체 첫 실행은279통과/2실패/3제외였다([결과](evidence/full-first-result.json)). 문장수정 테스트2개를 분리해1통과/1실패를 재현했다. [진단](evidence/navigation-diagnostic-logcat.txt)에서 카드 높이가173→1323px로 변하고 클릭 대상이 사라지는 시점을 확보했다. `TranslatedMedia.captionHeight`와 같은 크기의 로딩 카드를 사용해 레이아웃을 안정화했다. 복원 직후 비동기 DB 로딩은 테스트가 editor 존재를 기다리도록 고쳤다. 포인터 클릭·기존 문장 assertion은 유지했다. 수정 후 AccessNavigation/ExpressionBasisUI/GalleryMedia/TranslatedMedia **42통과**([결과](evidence/navigation-green-result.json)).
8. 첫 release lint에서 새 `LocalContextConfigurationRead` 오류1개·기존경고20개가 나왔다([로그](evidence/lint-first-failure.txt)). `LocalConfiguration.current.fontScale`로 바꿔 글자크기 변경에 반응하도록 수정했다. lint 경고/오류를 baseline으로 숨기지 않았다. 수정 전 서명 산출물은 로컬 provisional 폴더로 옮겼으며 Play에 올리지 않았다.
9. 최종 서명 UI의 첫 실행은 앞5시나리오를 통과한 뒤 기록 목록의 seed가 첫 화면에 없어서 실패했다([원래 결과](evidence/signed-ui-first-result.json)). 앞선 schema-upgrade 기록과 새미디어 기록 때문에 seed가 화면 아래로 밀렸으며 **실제 한 번 스크롤해 원문 존재를 확인했다**([관찰](evidence/signed-scroll-diagnosis.jsonl), [화면](evidence/scrolled-existing-record.png)). 사용자 데이터/앱 코드를 바꾸지 않고 하네스에 최대12회 스크롤 확인을 추가했다. 미디어 선택은 홈에서 다시 기록으로 들어가 최신 항목을 선택하고, 기존 파일명·SHA 일치 assertion을 유지한다. 재실행은 동일 APK SHA·120초 타임라인·200MiB 경계의 앞4개 성공 증거만 재사용하며 저장/PDF/재시작/release전환을 모두 다시 실행한다. 최초 진단명령은 SDK 환경변수 누락으로 실행 전 실패해 ANDROID_HOME을 명시한 뒤 성공했다.

## Independent review

- 계획: 조건부 승인 → 보류에도 시각 전달·LEGACY 기본값·다중 얼굴·실제 시각기여·측정 기반 안내 조건 반영.
- 소리/아키텍처: 승인. 기존 임계값 보존, 추천이 음성 gate를 우회하지 않음.
- 보안/자원/취소: 승인. 새 네트워크·원본 업로드·영구 특징 저장 없음; PCM/Bitmap/모델/decoder 자원 해제와 entitlement 유지.
- UI/DB/저장: 독립 코드 검토 승인. 기존import-origin 호환, 새출처 보존, 생성문장 최대90자(160자 한도 이내).

## Residual risks

- 제공 두 영상에서 아기 소리를 놓치는 문제가 남아 있다. 더 큰 float 모델·증폭·홉 변경으로 해결되지 않았다. 권리가 확보된 실제환경 발성 데이터와 독립 라벨/평가로 별도 모델을 검증해야 한다.
- 얼굴 주인·아기 연령·음성 화자·실제 프레임PTS·립싱크를 확인하지 않는다. 입꼬리/눈감김은 감정·졸림 확률이 아니다. 시각 근거는 제한된 응답 요청 문구 선택에 쓰인다.
- 새 JSON2는 이전 앱이 읽을 수 없고, DB2에서 구버전 앱으로 다운그레이드는 지원하지 않는다. 기존JSON1 가져오기는 유지한다.
- ARM/OEM 실제 촬영·코덱·갤러리·실제 Play 자동업데이트 및 거래는 이 에뮬레이터 QA로 입증하지 않는다.

## Final verification and cleanup

최종 검증은 2026-10-03에 수행했다.

| 검증 | 실제 결과 | 근거 |
|---|---|---|
| 전체 Android 계측 | **281통과/0실패/3명시제외**, 총284개·706.06초·exit0 | [JSON](evidence/full-final-result.json), [전체 실행](evidence/full-final-run.txt) |
| beta/release lint | 각각 **오류0/기존경고20**, exit0 | [최종 lint](evidence/lint-final.txt) |
| 서명 APK/AAB | 두 배포종류 모두 통과, 모델/라이선스18파일 보존, AAB종류·서명·backup 제외 검사 | [beta](evidence/beta-package.json), [release](evidence/release-package.json) |
| 실제 beta22→beta23 업데이트 | 앱 데이터 삭제 없이 기존 문장·LEGACY 표시 유지 | [결과](evidence/signed-upgrade-result.json), [화면](evidence/after-upgrade.png) |
| 최종 서명 행동 UI | **7시나리오 통과**, 동일APK의 앞4개+나머지3개 재실행 | [결과](evidence/signed-ui-final-result.json), [실행](evidence/signed-ui-final-run.txt) |

명시 제외3개는 public probe(별도 실제22파일 검사), 결제설정 누락 전용2개다. 실제 공개 결제설정을 포함한 debug에서 누락설정 테스트를 실행하지 않는다. 개별test 완료 수와 실제 설치 APK SHA를 확인했다.

실제 촬영 타임라인은119.6초(관찰 대기122.87초), 선택20초 실제 분석을 확인했다. 정확한200MiB 허용/1바이트초과 거절, 모델/시각 결과와 실제 PDF, 재시작, 같은 업로드키 release 수동 교체를 검증했다. beta에서 내보낸10초 MP4와 release에서 다시 내보낸 **같은 파일의 SHA256이 동일**했다. 정식60/10/100·시각/PDF 재잠금을 확인했다. 같은code23 수동 교체를 실제 Play 자동업데이트로 보고하지 않는다.

### Play

beta23 AAB 업로드·출시 저장·최종 검토 전송을 처리했다. Console에 ‘검토 중인 변경사항’과 Alpha beta23이 표시되며 **빠른 자동 검사 진행 중**이다. 자동 검사 성공 후 검토 전송되므로 심사 승인/게시 완료로 보고하지 않는다. 오류0/경고2·지원 기기 감소0. [정확한 상태와 화면](../../release/20261003/PLAY-STATUS.md).

### Cleanup and rollback

각 signed UI finally에서 외부 QA 파일·로컬 대용량 fixture·내려받은 미디어 삭제, 화면 크기/글자 크기 복원을 확인했다. 개인 MP4의 기기 사본은 앞선 diagnostic finally에서 제거했다. 원본 사용자 영상은 유지했다. 최종 외부 Download 목록은 비어 있었다.

추가 일괄 정리 명령(전용 앱 uninstall·provisional 산출물 제거·프로세스 종료)은 **실행 전 자동 승인 정책에서 차단**됐으며 상세 사유는 반환되지 않았다. 이 명령은 실행되지 않았다. 더 제한된 정상 종료 경로인 `adb -s emulator-5554 emu kill`과 `gradlew --stop`은 성공했다. ADB 기기 목록과 해당 emulator/Gradle PID의 부재를 확인했다. `work/update-059/provisional-packages`와 전용 AVD 내부의 합성 QA 기록은 격리된 상태로 보존한다. 개인 원본이나 서명키를 지우지 않았다. 다음 기기QA는 이 AVD에 release23 합성 기록이 남아 있음을 고려해야 한다.

격리 UltraQA 상태의 완료·clear·부재확인을 [정리 결과](evidence/cleanup-result.json)에 기록했다. 커밋용 text 로그는 줄바꿈과 줄끝 공백만 정규화했으며 원본 출력은 ignored work에 보존한다. **기능·패키지·업데이트 검증은 완료됐지만 임시 산출물 삭제는 보류**다. 사용영상 인식 누락과 공개 strict라벨4건 불일치는 미해결이며 ‘전체 인식 품질 승인’으로 해석하지 않는다.
