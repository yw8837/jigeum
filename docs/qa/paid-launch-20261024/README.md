# 정식 월 구독·보상형 광고 준비 — 2026-10-24

## 사용자 확정 및 실제 외부 상태

- 공개 출시 전에 구독과 실제 구매 검증을 준비한다.
- 한국 월4,900원 자동 갱신, 별도 무료 체험 없음. 3개월 선불은 등록하지 않는다.
- Play 상품 `jigeum_album`, 표시명 `아가톡 전체 이용권`, `monthly`가 **활성**이다.
- 매월 자동갱신, 대한민국만 KRW4,900, 유예7일/계정보류 자동계산53일, 재신청 허용.
- 화면 세금은 `VAT 없음`. 별도 세금 설정은 변경하지 않았다.
- `monthly-active.txt/png`, `monthly-price.png`, `subscription-draft.txt/png`에 증거 보존.
- 프로덕션 신규 업로드/제출/공개는 아직 없고 Alpha40 제공 상태다.

## AdMob

- 계정 승인/광고 게재 사용 설정 상태를 확인했다. 기존 다른 앱은 변경하지 않았다.
- 아가톡은 공개 검색에서 패키지로 찾을 수 없어 미게시 Android 앱으로 생성했다.
- 앱 ID `ca-app-pub-2269464728885792~3259646120`.
- 보상형 단위 `premium_operation_rewarded`, `ca-app-pub-2269464728885792/4692147296`.
- 보상 수량1 / `유료 기능 1회`, 파트너 입찰 사용 안 함.
- `admob-created.txt/png`에 생성 결과를 보존했다.
- **아직 SDK/1회 권한 구현·사용자 동의·테스트 광고·실광고 제공·공개 스토어 연결/검토를 완료하지 않았다.**
- SDK 공식 예제 참조: Google Mobile Ads25.5.0 / UMP4.0.0. 실제 의존성은 아직 미추가.
- 앱 원본·녹음·기록을 광고 SDK에 전달하지 않는다. 데이터보안/정책 갱신은 실제 SDK 흐름 확인 후 필요.

## 완료된 로컬/서버 준비

- 기존 Cloud Run verifier revision `jigeum-verifier-00005-ph7`, Ready/traffic100% 확인. 배포 변경 없음.
- 서버 단위 검사56/56 (`verifier-tests.txt`), HTTPS smoke9/9 (`https-smoke.json`).
- 실제 Play 구매/ACK/복원/취소/환불 검증은 아니다.
- 광고 요청 전 만든 `work/paid-launch-20261024/final/Agattok-0.5.27-release-code41.aab`:
  SHA256 `5a1140fb27d4a4550d6c916476a40eae03c320aec5d2d5a1e6179adbad1cb159`, 160294042bytes.
- APK SHA256 `6bdb5d2f7b098006c3f1c9eebf78bef8fea7a306873608bb6b8e530a59104902`.
- package 검사통과, 두 테스트flags false, 모델/native26개 beta40과 동일. `package/` 참조.
- **광고 없는 중간 후보이며 최종 업로드 금지.** 새 요구를 반영한 최종 빌드/런타임/업데이트 검증 필요.
- `outputs/QA/signed_media_upgrade.py`에 `--new-distribution release` 기대값을 추가했다.
  beta→정식 무료 범위 재잠금 확인을 위한 준비이며, 이번에 최종41 업데이트 시험은 아직 실행하지 않았다.

## 이어갈 작업

직접 녹음 내보내기 제보를 먼저 재현한다. 그 뒤 보상형 광고1회/분석1회(확장 촬영·가져오기·20초·시각 포함),
PDF별도1회, 실패/취소 보존, 실제 구독 entitlement 분리, SDK 동의/정책, 최종 패키지 및 거래 검증을 진행한다.
상품 활성화·광고 ID 생성은 앱 출시 완료가 아니다. 새 예약/자동화 없음.
