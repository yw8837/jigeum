# Play·광고·게시판 실제 상태 — 2026-10-27

## 앱 업데이트

- 기존 공개 버전0.5.27(41)은 대한민국에 제공 중이다.
- 정확0.5.28(42), 출시명 `0.5.28 (42) - access codes and launch offer`를 기존 내부 트랙 `4701013220785139321`의 출시2에 제공했다. Console 게시일 **10월27일23:07 KST / 내부 테스터에게 제공됨**.
- AAB SHA256 `104a3ba0b90667a00da6f6ee59cfebd94af14c8624be9b64be00fa82ff813714`의 단일 업로드를 재사용한다. Alpha40와 테스터 목록/그룹은 변경하지 않았다.
- 같은42를 기존 프로덕션 트랙 `4697837620700937386` 출시2로 승급했다. **23:16 KST 제출23 / 프로덕션·스토어 등록정보 / 검토 중**을 확인했다. 빠른 검사가 진행 중이며42 공개 제공 완료는 아니다. 관리형 게시 사용 중지로 승인 후 자동 게시 예정이다.
- 프로덕션42와 스토어 간단 설명/전체 설명/화면2장/추천 그래픽 변경의 총5건이다. `play-production42-submitted.txt/png`, `play-submission23-details.txt/png`를 참조한다.
- 내부 제공 증거: `play-internal42-provided.txt/png`.
- 새42 실제 Play 설치·상품 조회·할인 결제·서버ACK·복원·취소/만료 검증은 미실행이다. 서명APK/실제 코드서버의 가상폰 통과와 구분한다.

## 권한·비차단 경고

활성 산출물 중 AD_ID 권한 누락 오류가 표시됐다. 업로드된 정확42 권한15개에
`com.google.android.gms.permission.AD_ID`가 있음을 Console에서 확인했다 (`play-code42-permissions.txt`).
광고가 없는 기존 활성Alpha40를 유지하며, 내부 출시 UI의 `권한 없이 출시`를 통해 이번 출시만 무시 처리했다.
프로덕션 미리보기에서도 같은 경고를 이번 출시에만 처리했다. AD_ID 선언이나 전체 오류 탐지를 끄지 않았고 새 앱 권한을 제거하지 않았다.
R8 매핑과 native debug symbols 미첨부 경고는 남는다. DEX 최적화는 별도 후속 과제다.

## 할인·광고

- `jigeum_album/monthly/launch-2m-half` 활성. 대한민국 신규 구독자(앱의 구독 이력이 없는 사용자), 월2,450원2회 이후 월4,900원 자동 갱신. 별도 무료 체험 없음.
- `play-offer-active.txt/png`, `play-offer-preview.txt/png`는 Console 설정 확인이다. 실제 결제 성공 증거가 아니다.
- 코드100개 발급 완료·실코드 소비0개. 100개 제한은 정상 앱의 발급/등록 제한이며 변조 클라이언트의 Play 거래까지 제한하는 거래 상한은 아니다.
- AdMob 앱은 `검토 필요`이고 Play 앱 검색이 아직 미검출이다 (`admob-recheck.txt`). 스토어 연결·앱 검토·실광고 제공은 미완료다.
- 실제 청구·실광고 클릭·Threads 게시·DM 발송·예약은 하지 않았다.

## 스토어·게시판

- 새 추천 그래픽, 실제42 홈/기록 화면2장, 간단/상세 설명을 저장했다. 새 추천 그래픽은 AI 생성 애셋으로 표시했다. 원본 스크린샷은 실제 앱 캡처다.
- `play-store-ready.txt`는 전송 전 저장 이력이다. 현재 제출23에 포함돼 검토 중이며 실제 공개 적용과 구분한다.
- https://baby.yeolmaeapps.kr/ Sites v7의 배너는 이미 공개 반영됐다. `community-banner.md` 참조.

## IARC

10월27일21:43 KST `IARC Live Rating Notice`는 콘텐츠 등급 적용 통지다. 인증ID
`b917e49f-a80e-86f6-8340-3fb431ccc20e`가 Console과 일치한다. Console의 기존 설문 제출일은10월22일20:24,
현재 상태는 완료됨이다. 메일 본문에 국가별 등급은 나열되어 있지 않아 Console에서 확인했다.

- 대한민국/러시아:3세 이상, 기타 국가:IARC3+, 유럽:PEGI3.
- 북미/브라질:전체이용가, 독일:USK전체이용가.
- 게시판에 따른 사용자 상호작용 표시. 앱의 성인 보호자 대상 설정·아기 월령 범위나 번역 정확도/의료 인증과는 별개다.
- 반려·수정 요청이 없으며 답장/재신청은 필요하지 않다. 향후 설문 답변에 영향을 주는 기능 변경 때 재평가한다.
- `iarc-current.txt` 참조. 메일 원문/사적인 헤더는 저장하지 않았다.

## 카카오 공유 미리보기 조사

사용자가 Play 공유 링크를 카카오톡에 붙였을 때 `play.google.com`/`링크를 확인하세요`만 표시된다고 제보했다.
로그인 브라우저와 익명HTTP에서 기본URL, `hl=ko&gl=KR`, `pcampaignid=web_share` 모두 정상200이며
OG제목/설명/이미지가 있다. 해당 이미지도 image/png/200/236,761bytes다. 공개 페이지는 아직 기존41 설명/그림을 제공한다.
`play-share-anonymous.json`, `play-share-image.json` 참조. 원인을 지역 제한으로 확정하지 않는다.
공식 https://developers.kakao.com/tool/debugger/sharing 에서 로그인이 필요해 사용자에게 요청했다.
현재 캐시 초기화/카카오 실제 수집·채팅 미리보기 복구는 미확인이다. 사용자 메시지 전송은 하지 않는다.

## 정리

소유 가상폰 앱/fixture/XML와 미리보기 서버는 정리/종료했다. 사용자 폰·기록은 건드리지 않았다.
불완전 중간 서명 파일 `D:/Codex/Agattok/release-20261027/delivery/Agattok-0.5.28-release-code42.incomplete`
57,001,222bytes 삭제는 자동 정책 검토에서 차단됐다. 우회하지 않고 보존했다. 이전 차단 대상도 유지한다.
최종42·직전41/40·모델·키·발급 코드는 보존한다. 자동화33 PAUSED/새 예약 없음.
# 추가 확인 — 제작자명·카카오 공유

- 공개 개발자 이름 `Yeolmae_STUDIO` 저장 성공. Google 검토 중, 승인 전 `Yeolmae` 표시 유지.
- `developer-name-review.png/txt`에 이름 검토 안내만 보존했다. 법적/결제/연락처 정보 변경 없음.
- 카카오 공식 공유 디버거에서 기본 Play 링크 OG공란 재현. 캐시 초기화에서 1개 스크랩 삭제 후에도 공란 지속.
- `hl=ko&gl=KR`, `pcampaignid=web_share`도 공란. 익명 일반 HTTP의 정상 OG와 다르며 상세 원인은 미확정.
- `kakao-share-before.*`, `kakao-share-after-reset.*`, `kakao-share-ko.txt`, `kakao-share-web-share.txt` 참조.
- 채팅 미리보기 해결·메시지 발송을 주장하지 않는다.
