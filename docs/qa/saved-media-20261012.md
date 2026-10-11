# UltraQA Report — 갤러리 영상 저장과 분석 독립성

상태: **로컬 수정 적용, 실폰 HDR 검사 대기**. 기준6499256. [계약](../research/20261012-saved-media-contract.md).
소유MoaQA35/emulator-5556, synthetic media, 최대5cycle/같은실패3회 중단. 현재2cycle.
후속 사용자 확인으로 PNG는 정상이고 영상카드/장면과 함께 저장만 실패하는 것으로 정정했다.
마지막 Play 제공 확인은code37이며, 기존 서명code38 파일에는 이번 변경이 없다.

## 목표와 범위

실제 갤러리 content URI를 받는 앱 저장 콜백, Transformer와 MediaStore/기록 저장을 실행한다.
결과가 없어도 선택 구간을 원래 소리와 함께 자막 없이 저장하고, 원본·기존 기록을 보존한다.
연속 입력의 이전 결과·초안 오염과 관측된 시간 흐름의 표현을 검사한다.
분석 대역을 쓰는 UI 검사는 인식 모델 정확도를 측정하지 않는다. 아기 자료 외부 전송은 없다.

| ID | 의도 / 사용자 | 설정·명령 | 기대 신호 | 실제 / 수정 | 근거 | 정리 |
|---|---|---|---|---|---|---|
| S1 | 갤러리 영상 번역 후 PNG·MP4·기록 저장 | SavedMediaFlowTest, 실제 app callbacks/content URI | 출력 readable/published, 입력 보존 | SDR 세 저장 경로 통과 | final01 / android-tests | 소유 rows·DB 제거 |
| S2 | 분석 보류·실패 사용자 영상만 보관 | 동일 UI harness + TranslatedMediaTests | 선택구간/오디오 보존, 자막·가짜기록 없음 | 무료 정책·분석 실패·8.123–11.123초·무음 입력 통과 | 동일 | 동일 |
| S3 | 손상·없는 파일·발행 실패 | TranslatedMediaTests/GalleryMediaTests | 부분파일·가짜 성공 없음, 원본 보존 | 손상/취소/실패 롤백/재시작 회복 통과. 실제 외부 provider 권한회수는 미실행 | 동일 | 렌더 파일0·pending journal 비어 있음 |
| S4 | 연속 입력·오래된 결과 | 실제 OpenDocument 콜백에 두 번째 URI 전달, CaptureAccessLimitsTest | 이전 결과/초안 오염 없음 | 새 문장·URI 순서·초안 제거와 기존 권한 경계 통과 | 동일 | 소유 작업취소 |
| S5 | 표현 근거/승인 정책 보존 | host5개 suite, ExpressionVarietyTest | quiet 근거 있을 때만 표현 변화 | host44통과; 기준소스10개 중 새 표현1개 기대실패. 실제 모델 재추론 아님 | host01 / host-baseline | 작은 로그 보존 |
| S6 | 중복탭/오류 출력 | 같은 버튼 action 연속2회, 취소·손상 입력 | 하나만 저장, 오류 후 정리 | 단일 출력·취소 정리 통과; 새120초 timeout 강제실행은 안 함 | final01 | 소유 실행 종료 |
| S7 | HDR 자막 호환성 | 실제 GL shader + 10bit PQ HEVC fixture | 기존 실패 재현, SDR 합성 및 전체 저장 성공 | 기존 gainmap 예외 재현·SDR draw·Composition 모드 통과. 전체 codec는 c2.goldfish.hevc.decoder에서 실패 | final01 | 실폰 검사 필요 |

## 명령과 결과

실행 기준 폴더는 이 작업트리다. [재현 드라이버/빌드 로그](saved-media-20261012/commands/)와
[각 Android 테스트 결과](saved-media-20261012/android-tests.json)를 보존한다.

- `python work/saved-media-20261012/build.py build05`: exit0, 133.956초, 600초/5GiB 제한,
  peak2.216GB, 소유 프로세스 정리 확인. debug app/test만 생성했다.
- `python work/saved-media-20261012/host.py host01 ExpressionVarietyTest,LongExpressionTest,EvidenceInterpreterTest,IntervalEvidenceTest,VocalKindEvidenceTest`:
  compile0/test0, **44통과**, 테스트0.837초. 컴파일90초/실행30초 제한.
- `python work/saved-media-20261012/host.py host-baseline ExpressionVarietyTest --baseline`:
  compile0/test1, **10개 중 새 quiet 표현 검사1개 기대실패**. 기존6499256의 표현으로 실패한다.
- `python work/saved-media-20261012/device.py final01 SavedMediaFlowTest,CaptionHdrCompatibilityTest,TranslatedMediaTests,GalleryMediaTests,CaptureAccessLimitsTest,ExpressionVarietyTest`:
  **48개 중47통과·1실패·skip0**, 121.94초, 480초 제한. adb exit0이어도 runner 실패를 감지해 드라이버exit1.
  [전체 로그](saved-media-20261012/final01/instrumentation.txt), [엄격 판정](saved-media-20261012/final01/result.json).
- app/test 설치본 SHA256을 실제 기기에서 비교했다. [provenance](saved-media-20261012/final01/provenance.json).
  이는 연구용 `com.moa.babylanguage.astcandidate`이며 Play 앱 업데이트가 아니다.

## 실패와 수정 이력

1. 초기 baseline01은 에뮬레이터 부팅 전 검사로 실행이 시작되지 않았다.
2. baseline02의2실패는 고정 footer에 `performScrollTo`를 호출한 하네스 문제였다. 화면에 보일 때 바로 누르도록 수정했다.
3. baseline03은 SDR 전체 앱 저장1통과, raw 저장2개는 실제 저장 뒤 잘못된 세로 비율 가정으로 실패했다.
   원본의 실제 aspectRatio와 비교하도록 수정했다. HDR1개는 poster 추출에서 실패해 영상 렌더링 전 검사가 중단됐다.
   PNG 성공을 HDR 영상 시험의 선행조건에서 제거했다. 이3개를 제품결함3개로 세지 않는다.
4. build03은 Media3 1.11.1의 package-private `fromSingleItem` 호출로 컴파일 실패했다.
   공개된 track-inference Builder로 수정했고 build04/05가 통과했다.
5. final01의 유일한 실패는 HDR fixture 전체 저장이다. `c2.goldfish.hevc.decoder`에서
   `Invalid to call at Released state`로 실패했다. 에뮬레이터의 이 실패를 skip/통과로 바꾸지 않는다.
   별도 실제 GL 검사에서는 기존 HDR overlay가 gainmap 부재로 실패하며 SDR overlay는 성공했다.
   이것은 확인된 라이브러리 합성 경로 결함이고, 사용자 원본의 원인이 HDR이었다는 확정은 아니다.

## 적용한 수정과 검토

- `TranslatedMedia`: 자막 영상은 GL HDR→SDR 변환, 자막 없는 영상은 HDR 유지 모드;
  공유된 범위/디스크/취소 제한과 원본 트랙 추론. 실패 안내에서 결과 없는 사용자에게 문장 저장을 권하지 않는다.
  자막 출력은 SDR이므로 HDR 원본과 색감·밝기가 달라질 수 있다.
- `CaptureFlow/MainActivity`: 결과 유무에 관계없이 **선택 구간 영상만 저장**. 무료 내보내기,
  중복탭 방지·원본 음성·실제 선택 범위·가짜 Observation 없음.
- `BabyExpression/ClipEvidence`: 짧은 단일 종류의 발성이 실제 PCM 조용한 구간 뒤 다시 나타날 때만
  그 흐름을 표현한다. 앞뒤 각각 인접한 승인창2개를 요구하고 부분/넓은 관찰/시간축 불일치는 제외한다.
  임의 표현 교체나 감정/배고픔/졸림 추정은 추가하지 않았다.
- 독립 Spec/Standards 정적 검토 **필수결함 각0**. Spec의 오류 안내 제안은 반영했다.
  Standards의 렌더/발행/정리 코드 중복 P3는 선택 개선으로 남긴다. 정적 검토를 실폰 성공으로 세지 않는다.

## 정리와 남은 한계

에뮬레이터의 소유 MediaStore rows0, 렌더 파일0, pending journal 비어 있음을 확인한 뒤
연구 app/test를 제거하고 MoaQA35를 종료했다. 기존 build를 재사용했다.
기존 대용량 삭제의 자동정책차단은 유지하며 우회하지 않는다.
이번 참고 source archive/extraction·host class8대상(6,190,110bytes, 약5.90MiB)의 정확 경계검사+
PowerShell `Remove-Item -LiteralPath`도 자동정책이 CreateProcess 전에 차단했다. 삭제0이며 재시도/우회하지 않았다.
에뮬레이터 정리 확인의 첫 스크립트는 제거된 패키지의 `pm path`가 exit1을 반환해 중단했다.
미설치를 정상 출력으로 검사하는 `pm list packages`로 보완해 나머지 제거·최종 부재를 확인했다.
사용자 S25 연결 동의를 받았고 USB 디버깅 승인을 기다리는 중이다. 실제 앱/자료를 변경하지 않았다.

반복 문구의 모든 원인이 해결된 것은 아니다. 소리 종류별8문장 계열의 한계는 남으며,
입력 초기화 검사는 분석 대역을 사용하므로 모델이 두 실제 아기 장면을 구별한다는 입증이 아니다.
약국 옹알이 개선은 사용자 전달이며 이번에 새 측정하지 않았다.
**HDR 실제 폰 저장·사용자 원본 재확인, 기존code38 첫20초45초timeout, 새 서명/Play 배포는 미완료**다.
검사 통과를 일반 인식률·감정/욕구 정확도로 환산하지 않는다.

**ULTRAQA BLOCKED: S25 USB 승인/HDR 전체 저장 및 사용자 영상 확인 대기; 참고 생성물 정리 정책 차단.**
이번 실행만2cycle stopped→clear→부재 확인하며, 전체 목표나 배포 완료로 기록하지 않는다.
