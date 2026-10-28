# Android AST 통합 검증 — 실행 검증 완료, 의미 품질·배포 보류

2026-10-04. 후보 모델은 기본 비활성이다. 이 문서는 실행 검증이며 인식 정확도·의도 번역·배포 승인이 아니다.

## 첫 통합 실행

- 빌드/서명 성공: `work/ondevice-av/android-ast-build1.log`, 77초.
- Android API35 x86_64, 기존 QA AVD에서 데이터 삭제 없이 같은 업로드키의 APK로 업데이트.
- 실제 설치 APK/테스트 APK 해시를 검사했다.
- `work/ondevice-av/android-ast-run1/result.json`: **128개 중124통과/4실패/제외0**, 48.67초. runner의 shell exit0을 통과로 오인하지 않고 JUnit 실패와 개수 불일치를 검사했다.
- ORT1.24.3 Android와 호스트1.24.4의 합성8사례 logits 비교, 실제 native 취소/새RunOptions, 취소 후 다음 분석,10/20초 동시 요청 처리는 통과했다. 허용 최대 logits 오차는 사전 고정0.002다. ARM 실기기 증거는 아니다.

## 실패 원인과 수정

1. `AstModelStoreTest`3개가 테스트용 디렉터리 생성에서 실패했다. 계측은 대상앱UID로 실행되므로 test APK의 `context.cacheDir` 대신 `targetContext.cacheDir`를 사용한다. 모델 검증 동작에 도달하지 못한 하네스 설정 실패다.
2. BT709 실디코딩 색상 검사는 예상182/83/29 대신173/76/33을 반환했다. 합성 입력은 matrix만BT709이고 primaries/transfer가 누락됐다. Android의 결합된COLOR_STANDARD를 명시하려고 세 태그를 모두BT709로 보완했다. 원본/수정본 디코딩 YUV61440bytes의SHA256은 동일한 `d3284cc938b4ad1345a1db3308c9264cb5b616cee3dd6cf917d9342b72bf3535`다. 새 테스트는 MediaExtractor가 읽은 표준/범위도 먼저 확인한다. 수정 후 전체 회귀와 기본/연구 빌드 재검사에서 통과했다.

기존 실패 로그와 fixture 원본을 ignored `work/`에 보존했다. 유효 태그 영상의 색상 검사가 통과하기 전에는 허용 오차를 넓히지 않는다.

## 의미 품질 별도 판정

- 추가 월령명시 두공개 source의 사전 고정구간6창에서 frozen v1 옹알이 보조조건 충족0창. 기존 Sage6.5개월 미충족과 함께 독립 회복 근거 부족이다.
- clod11111개월 첫20초 Babbling 최대0.0456659, NoiseCollector9개월 전체15.812초 최대0.0080436. 제목/배포자 설명은 독립 청취 정답이 아니다. 상세출처는 [조사](../../research/20261004-target-age-babble-sources.md).
- 점수를 본 뒤 임계값·crop을 바꾸지 않았다. 아래 실제 Android 평가와 회귀는 실행했지만 독립적인 의미 품질 승격 증거는 확보하지 못했다.
- 현재 새 일반 옹알이 인식·생성 품질을 통과했다고 볼 수 없어 `BabbleSupportPolicy.ENABLED=false`다.

## 전체 회귀 실행 — 패키징 분리 전 기준

`work/ondevice-av/android-ast-full1/instrumentation-result.json`: **322통과/0실패/명시적 제외2**, 총324개,596.70초, 검증 오류0. 별도 입력이 필요한 `PublicAudioProbeTest#reportPublicSoundEventProbes`와 `AstPerceptionProbeTest#evaluateLocalPerceptionManifest`만 제외했다. 결제 설정이 빈 debug 빌드의 구성 누락 검사2개는 실제 실행됐다.

이 결과에는 실제2분 녹음과 영상 촬영, 후반20초 음성 디코딩, 구간 편집, 기록/미디어/앨범, 접근 정책, 소리/시각 처리 회귀가 포함된다. 빌드3의 누락 asset 테스트는 `Resources.getSystem().assets`로 framework 전용 자료를 사용하여 통과했다. 첫 `createPackageContext("android")` 방식은 계측 환경에서 앱 asset을 볼 수 있어 누락 상황을 재현하지 못했다.

앱SHA256 `91c39ee1df3410a5d5e0f6c6c2da8e3cc191fa6bed61211e86d5232985de2b91`, 테스트APK `37483b4f9c3bdbf7882b49df874178a5c510a21e1fcfb7d3251689d79c02a907`의 실제 설치 일치를 확인했다. 테스트 결과를 보존한 뒤 에뮬레이터를 정상 종료했다.

후속 코드 검토로 추가한 v2 세부 감정 보류와 연구 빌드 분리는 이 전체 실행 이후 변경이다. 아래 별도 검사로 검증했으며 같은324개 결과에 소급 포함하지 않는다.

## 검토 수정 후 기본/연구 빌드 재검사

- 기본 APK: 관련 **116통과/0실패/제외0**, 34.83초. 모델 없는 경로에서 v2 충돌 정책, 시각·소리·구간·접근·기록 회귀를 실행했다. `work/ondevice-av/android-default-focused1/result.json`.
- 연구 APK v4: **27통과/0실패/제외0**, 18.68초. 실제 AST 특징/모델 저장/ORT logits/취소/동시 실행/정책/색상 검사를 실행했다. `work/ondevice-av/android-probe-focused4/result.json`.
- 각 계측 실행 전에 실제 설치 앱과 테스트 APK의 SHA를 비교했다. 두 묶음에는 겹치는 검사가 있으므로 서로 더해 고유 테스트 수라고 보고하지 않는다.
- 기본 debug/계측 APK·beta AAB 빌드 성공. beta/release lint 각각 **오류0/경고19**. 경고는 의존성 업데이트7, KTX 제안9, 저장 공간 API 제안2, 의도적인 동기 commit1이다.
- 기본 APK/AAB에 AST 모델·고지·ORT가 없고 YAMNet은 존재한다. 기본 beta AAB는46,670,862bytes다. **code23의 서명 전 QA 파일이며 Play 업로드용 새 버전이 아니다.**
- 패키징 실패 조건 **6/6 통과**: 모델 없이 기본 빌드 가능, 연구 모델 누락/해시 손상 거부, 연구 속성으로 beta/release/preview 빌드 거부. `work/ondevice-av/ast-package-gates1/results.json`.
- 연구 Python 회귀 **43통과**, exit0. 의도적인 타임아웃 테스트의 출력과 실제 하네스 실패를 구분했다.

## v2 원본 평가 — 에뮬레이터 오프라인

Wi-Fi와 모바일 데이터를 끄고, 분석 전후 모두 활성 기본 네트워크 없음·연결된 network agent0을 확인했다. `work/ondevice-av/offline-network-02.json`.

- 소리 트랙 없는 원본 첫10초: 후보도 `NO_AUDIO`, 문장 없음.
- 약국 원본 첫10초/20초: 기존 `INFANT_UNCONFIRMED`에서 **평가용 v2 후보의 옹알이**로 회복. 실제 시각 표본20/40개, 시간순 coverage 유지.
- 각 경로는 에뮬레이터에서 약5.36초/9.96초였다. ARM 휴대폰 속도·발열·배터리 수치로 사용하지 않는다.
- 실행 하네스1개 통과는 **3사례 실행 완료**다. `quality_validated=false`를 유지하며 개별 응답·원본은 ignored `work/`에만 보관한다. 이 원본은 이미 반복 사용한 개발 자료이며 독립 정확도 분모에서 제외한다.

## 정리와 남은 작업

네트워크 설정을 원래 값으로 복원했다. 이번 평가용 원본 사본·manifest·결과5파일과 연구 모델 캐시를 에뮬레이터에서 제거하고 기본 APK/계측 APK로 업데이트 복원했다. 기존 files/databases의 영구 파일6개의 전후 SHA가 같다. 에뮬레이터 정상 종료. 사용자 다운로드 원본은 건드리지 않았다.

이후 기본 경로의0.5.10/code24 서명 패키지·23→24 기록/미디어 보존·정식 재잠금 검증을 추가 완료했다. [서명 후보 QA](SIGNED-CANDIDATE-QA.md). 기본 비활성 후보의 일반화·생성 품질, ARM 실기기, 새 Play 배포는 남았다. 사용자에게 영상 개선만 먼저 배포할지 범위를 확인 중이며 답변 전 배포 범위를 바꾸지 않는다. 마지막 Console 확인 기준은 **0.5.9-beta/code23 제공 중**이다.

집계 증거: [QA-EVIDENCE.json](QA-EVIDENCE.json). 검토 후 116/27개는 전체 앱 회귀를 다시 실행했다는 뜻이 아니다.
