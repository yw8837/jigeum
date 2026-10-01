# 심사 접근 구현·검증 범위 — 0.4.5

2026-10-01 작성. 이 문서는 구현 계약과 실행해야 하는 회귀 범위를 설명한다. 실행 결과는 root의 통합 QA 로그를 따른다. 운영 배포·Play 심사 접근 성공을 의미하지 않는다.

## 이용권 계약

- 구매 이용권과 별도인 실제 EC 서명 이용권이다. 화면 Boolean·코드 일치·구매 콜백만으로 기능을 열지 않는다.
- `BillingManager.hasCurrentEntitlement()`가 현재 구매 또는 현재 심사 이용권의 서명·설치 식별자·기한을 검사한다. 출처는 `PLAY_PURCHASE` / `PLAY_REVIEW`로 표시한다.
- `POST /review`: `packageName`, 심사 전용 `installationId` UUID, `reviewCode` 세 필드. 아기 미디어·기록은 보내지 않는다.
- HTTPS 검증 URL의 경로가 정확히 `/verify`이고 사용자 정보·query·fragment가 없을 때만 동일 origin의 `/review`를 만든다. 리다이렉트를 따라가지 않는다.
- 코드 형식은 `[A-Za-z0-9_-]{32,128}`이며 운영 코드는 암호학적 난수로 생성해야 한다. 길이 검사만으로 코드의 난수성을 증명하지 않는다. 코드/서명키는 Git·AAB에 넣지 않는다.
- 서명 envelope는 기존 `payload`·`signature` 구조. claims는 `version=1`, `grantSource=play-review`, `basePlanId=play-review`, `reviewAccessId`, package/product/install/time 필드다. 구매 검증 함수는 이 출처/플랜을 수락하지 않고 심사 검증 함수도 구매 grant를 수락하지 않는다.
- 최대24시간·동일 boot·elapsed anchor 정책을 구매 경로와 공유한다. 재부팅 후 온라인 재확인. 온라인 명시적 거절은 심사 credential과 grant를 삭제한다. 장애는 유효한 기존 서명 시간만 유지한다.
- 재사용 코드는 설치UUID를 AAD에 포함한 기기 Keystore AES256-GCM으로 암호화한다. 복사·암호문 손상·키 유실은 접근을 허용하지 않는다. 해제는 코드와 grant, 기기 키를 삭제하고 구매 권한/초안/기록을 건드리지 않는다.
- 재시작에 저장한 코드를 온라인으로 재확인하며 동시 확인은 하나로 합친다. 자동 재시도 최소간격60초. 늦게 도착한 응답은 활성 generation과 닫힘 상태를 검사한 뒤에만 저장한다.

## 클라이언트 네트워크 제한

구매와 심사 클라이언트의 연결 idle timeout은7초, 읽기 idle timeout은26초다. 서버의 전체 verification deadline23초보다 읽기 대기를 길게 잡아 정상 응답을 클라이언트가 먼저 포기하는 위험을 낮춘다. 요청·응답은16KiB 이하이며 리다이렉트는 거부한다.

`HttpURLConnection.readTimeout`은 각 blocking read의 idle 한도다. 이를 전체33초의 엄밀한 상한으로 표현하지 않는다. 두 클라이언트는 별도 **35초 elapsed deadline watchdog**을 공유하고, 기한이나 코루틴 취소 시작 시 `disconnect()`로 Java 연결을 닫는다. 단일 daemon scheduler와 `removeOnCancelPolicy`를 사용하며 `finally`에서 타이머와 취소 등록을 폐기한다. 응답을 수락하기 직전에 기한·취소 여부도 검사한다. 느린 바이트 응답은 read idle 한도를 반복해서 연장할 수 있어도 전체 기한 뒤 이용권 수락이 불가능하다. 취소·해제 후 늦은 응답은 BillingManager generation 검사로도 버린다. 실제 플랫폼의 socket 종료·스케줄링 지연이 가능하므로35초를 UI의 정확한 밀리초 보장으로 표현하지 않는다.

`HttpRequestDeadlineTest`는 Android 실제 HTTPS loopback에서 느린 drip 응답·대기 중 취소·정상 조기 종료의 cleanup을 시험한다. 합성 TLS fixture `androidTest/assets/transport-deadline-test.p12`와 공개된 테스트 암호는 계측 APK에만 포함하고 release APK/AAB에는 포함하지 않는다. 서버 key manager·테스트 cert trust/localhost 확인 설정도 `androidTest`에만 있다. 프로덕션 endpoint/HTTP/인증서 검증을 우회하지 않는다.

## 추가 회귀

root가 Gradle·ADB를 단독 실행한다. 추가 Android 계측 클래스:

- `ReviewAccessVerifierTest`: 서명·목적·설치·기한·교차 수락 금지, endpoint 입력 방어, 코드 형식, 실제 Keystore 암호화/손상/설치복사/키유실.
- `ReviewAccessLifecycleTest`: 서명 전 잠금, 빈 구매 조회와 분리, 만료·온라인 복구·장애/명시적 철회, 빈 설정, 해제/닫힘의 늦은 응답, 구매 권한 보존, 재시작·재부팅, 중복확인·재시도 제한.
- `ReviewAccessUiTest`: 기본 접기·미설정 비활성, 코드 saved-state 비보존, 심사 출처 표시·해제·구매 미발생.
- `HttpRequestDeadlineTest`: 실제 TLS 느린 응답의 전체 기한, blocked read 취소, 완료 타이머/취소 등록의 정리.

기존123계측·무료/초안/카메라/영상/PDF 회귀도 통합 실행한다. 운영 URL/공개키가 비어 있는 내부0.4.5는 실제 구매와 심사 접근을 사용할 수 없다. 테스트용 키·대역은 `androidTest`에서만 제공한다.
