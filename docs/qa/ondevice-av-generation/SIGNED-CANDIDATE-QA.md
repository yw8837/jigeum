# 서명 후보 패키지·업데이트 QA

2026-10-04. 기존 `ea1e3aa`의 구현에 후보 버전0.5.10/code24를 부여한다. **로컬 검증용이며 배포 승인이 아니다.** 기존 생성·인식 품질 조건과 사용자의 배포 범위 답변 대기를 유지한다. Play 기준본은 마지막 확인상0.5.9-beta/code23이다.

앞선 goal 턴은 코드 수정·실행 증거·원격 커밋을 만든 **progress**였다. PR #5의 현재 head 일치와 main 상태를 재확인했으며 실행 중인 CI는 없다. 배포 범위 답변 없이도 준비 가능한 서명·실제 업데이트 검증을 수행한다.

## 승인 기준과 시나리오

| ID | 목적/실패 상황 | 실행 | 기대 신호 | 결과·근거 | 정리 |
|---|---|---|---|---|---|
| PK24 | 실험 모델 혼입·서명·배포종류 혼동 | beta/release 빌드, check_package.py, ZIP 검사 | code24·서명·배포표시 일치, AST/ORT 없음, 기존 모델 유지 | **통과**, 기존18개 native/model 파일 동일·같은 업로드 인증서 | 이전code23 파일 보존 |
| UP24 | 기존 설치 업데이트 중 기록 유실 | beta23→beta24, signed_media_upgrade.py 초기본 + 실제 JSON 백업 후속 감사 | 데이터 삭제 없이 기존 문장/LEGACY·MP4 보존 | **통과**, 정확한23→24 metadata·같은MP4 SHA·같은seed ID/내용/LEGACY 확인. 아래 검사 보완 한계 참조 | 본인 소유 외부 합성 백업/영상 제거 |
| UI24 | beta 전체 이용·촬영·구간·실제 분석/저장/PDF | closed_test_ui.py | 120초/20초/200MB, 무음 보류, 저장·내보내기 실행 | **통과**, 실제119.8초 촬영,20초 분석,200MiB 허용/1byte 초과 거부, 실제저장/PDF | 외부fixture/대용량 임시파일 제거 |
| LOCK24 | release 전환 때 기존 결과도 잠김 | 같은키 release24, 실제 미디어 내보내기 | 무료60/10/100·시각/PDF 잠금, 기존 미디어SHA 동일 | **통과**, 새 유료작업 재잠금·기존MP4 이름/내용 동일 | beta24 복원·전체 접근 안내 확인 후 에뮬레이터 종료 |

원본 사용자 영상·파생파일은 이 서명 UI에 쓰지 않는다. 권리 확인된 기존 CC0 음원과 단색 영상 fixture를 로컬에서 재사용한다. Android35 x86_64 에뮬레이터 결과이며 ARM·실제 Play 설치/자동업데이트·의도 정확도는 증명하지 않는다. 기존 앱 데이터는 지우지 않고 Gradle/ADB는 부모만 실행한다.

실제 실행 상한은 패키지 검사 개별 명령120초(묶음360초), 미디어를 추가한 업데이트 UI700초, 전체 서명 UI1400초다. 반복 실패3회/최대5cycle에서 원인을 재검토한다. 서명 비밀은 저장소 밖 DPAPI에서 프로세스 환경으로만 읽었다. 키·토큰·원본을 로그/커밋에 넣지 않았다.

## 실행과 증거

로컬 산출물은 `work/ondevice-av/signed-candidate24/`에 있다. **아직 Play에 올리지 않은 후보**이며 기존 배포파일명을 덮어쓰지 않았다.

| 항목 | 결과 | 로컬 근거 |
|---|---|---|
| 서명 beta APK/AAB |75,387,753 /46,713,022bytes, code24·beta 표시·실험 모델 제외 통과|`signed-package-summary.json`, `beta-package/`|
| 서명 release 비교본 |정식 표시·실제 공개 결제설정, 테스트 접근 표시 모두false|`release-package/`|
| lint |beta/release 각각 오류0·경고19|`lint.log` 및 원본XML|
|23→24 실제 업데이트 |10초 MP4 해시 `230e51e21b5198c723a188d1001c794d176ed95665048c331e9eca926d5014d4` 보존|`signed-upgrade/`|
| 서명 행동 UI |7시나리오 모두 통과. 앞선 버전 결과를 재사용하지 않음|`signed-ui/closed-test-ui-result.json`|
| beta→release의 같은 미디어 |해시 `5d1363c113c1d610d497327e20e1243b4c9c7488ca68b871207b92afc62c98c3` 동일|같은 UI 결과|
| 최초 seed 고유ID 추가 감사 |공식 백업에서 같은ID 1개, 모든7필드와LEGACY 일치|`upgrade-id-audit/result.json`|
| 하네스 적대적 회귀 |8통과/0실패, exit0|`verifier-unit.log`|

현재 재현 명령은 기대 버전을 명시해야 한다.

```powershell
python outputs/QA/signed_media_upgrade.py --old-apk <beta23.apk> --new-apk <beta24.apk> --cry-video <권리확인-QA.mp4> --old-version-code 23 --old-version-name 0.5.9-beta --new-version-code 24 --new-version-name 0.5.10-beta
python outputs/QA/closed_test_ui.py --beta-apk <beta24.apk> --release-apk <release24.apk> --cry-video <권리확인-QA.mp4>
python -m unittest discover -s outputs/QA -p test_signed_media_upgrade.py -v
```

기존에code24가 설치된 기기를 이 명령을 위해 지우거나 다운그레이드하지 않는다. 새 전용 QA 기기 또는 아직code23인 검증 기기에서 재현한다.

## 검토에서 찾은 하네스 결함과 보완

Standards1건과Spec3건 중 같은 지적을 합치면3건이다. 앱 기능 결함으로 보고하지 않는다.

1. 초기 하네스는 버전이 증가하는지만 검사했다. 현재는 기대code/name을 정확히 비교한다. 이번 실제 설치 로그를 별도로 검증해 `23/0.5.9-beta →24/0.5.10-beta`를 확인했다.
2. 초기 문장이 고정돼 이전 QA 기록으로 통과할 수 있었고 LEGACY 라벨도 같은 기록에 연결되지 않았다. 수정 하네스는 run_id 문장과 고유ID를 사용하고, 앱이 내보낸 JSON의 동일ID·전체필드·LEGACY를 업데이트 전후 검사한다. **이번 최초 실행에는 수정된 전후 JSON 검사가 없었다.** 원래 실행의 미디어/버전 증거를 보존하고 현재code24에서 공식 JSON을 새로 내보내 **최초 seed의 고유ID·내용·LEGACY를 후속 확인**했다. 수정된 전체 하네스의 새 완주로 소급하지 않는다.
3. 정리 실패 시 pass JSON이 남을 수 있었다. 정리 실패를 fail로 기록하도록 수정하고 합성 실패 주입 회귀를 통과했다. 이번 실제 실행은 외부fixture 정리까지 성공했다.

두 검토자가 수정 소스를 다시 읽어 해당 지적 해소를 확인했다. 신규 백업 UI는 후속 ID 감사에서 실제 동작했다. 공개 집계는 [SIGNED-CANDIDATE-EVIDENCE.json](SIGNED-CANDIDATE-EVIDENCE.json)을 따른다.

## 정리·남은 조건

외부 QA 입력/내려받기/백업, 로컬200MiB fixture, 내려받은 raw 미디어를 제거했다. 화면·글자크기를 복원했고, 기존 앱 데이터와 합성 QA 기록은 지우지 않았다. beta24로 복원한 실제 설치 SHA와 전체 기능 안내를 확인했다. 에뮬레이터를 정상 종료했고 ADB 기기/이번 시작 PID가 남아 있지 않다.

**이 로컬 패키지 QA는 통과했다. 전체 제품 목표는 미완료다.** 새로운 독립 옹알이 회복·새 문장 생성·ARM 실기기·배포 범위 결정·Play 제출/게시가 남았다. 기본 AST 정책은 여전히 비활성이다. 품질 미통과를 패키지 통과로 대체하지 않는다.
