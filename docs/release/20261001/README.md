# 등록 준비 실행 근거 — 2026-10-01

범위: **Play 앱 항목 생성, 업로드 키·서명 내부 부트스트랩 빌드, 스토어 파일과 실제 화면, 사실에 맞는 Console 설정 준비**. 공개 출시·실제 유료 서비스 완료를 뜻하지 않는다.

## 기준과 실제 결과

- 기준 main `8e7a6aa9e9af3f3864249e71da5c9fbb6eec5dc5`. Android 입력은 이전 검증 source `9cef337db42a3ed038383dc87b84a1ffbde39474`와 같다.
- 0.4.4/code11, compile/target36/min26. 무료·전체 이용권 정책과 Android 서비스 코드는 바꾸지 않았다.
- 새 업로드 키 RSA3072 / SHA384withRSA / 만료2054-02-16. 저장소 밖 Windows 사용자 전용 DPAPI/ACL 보관. 공개 [업로드 인증서](upload-certificate.pem)만 Git에 포함한다.
- 인증서 SHA256 `f1ed4457b926b985b0355d3e9d2bba285b7b6fc6142c395423c383ee92ea961d`. 실제 signed APK의 signer와 일치한다. Play에서 발급할 앱 서명 인증서와 구분한다.
- Gradle `bundleRelease + assembleRelease` 성공. 최종 AAB는 JDK jarsigner의 공식 경로로 서명하고 `jar verified` 확인. self-signed/trust-chain/no timestamp/POSIX metadata 안내는 보존한다. 앱의 Lint11경고와는 별개다.
- 서명 AAB의 **signature META-INF를 제외한607항목이 기존 unsigned0.4.4와 byte-identical**. 중복 ZIP 엔트리 없음, manifest가 첫 엔트리여서 최초 AGP archive의 JarInputStream 경고를 해소했다.
- 서명 release APK·서명 AAB 패키지 검사 통과: 백업/기기이전9도메인제외,FileProvider제한,INTERNET/cleartext/권한구성,APK v2서명,ZIP16KB/ELF정렬. 모델/native18파일이 검증본과 같다. 실제 ARM/16KB 기기 실행과 Play재서명 설치는 미검증.
- 기존 debug APK에도 수정한 패키지 검사기를 실행해 통과했다. 구형 APK/AAB/패키지ZIP은 덮어쓰지 않았다.
- 전용 API35 x86_64 에뮬레이터에 실제 upload-key-signed release를 설치하고 직접입력→문장→저장→기록→앨범과 결제미설정잠금을 확인했다. 실제 화면4장(1080×2160)을 촬영하고 표시크기를 원복했다. 합성문장만 사용했다.
- 제출용 화면RGB24bit,아이콘RGBA32bit512×512/1MiB이하,피처RGB1024×500와 문구길이3항목을 실제검사했다. 포맷변환 전후 보이는 RGB픽셀은 같다. [파일별 근거](../../../outputs/StoreListing/registration-20261001/assets-manifest.json).
- 서명도구 guard3: 기존키재생성거절 / 기존산출물덮어쓰기거절 / 결제미설정paid-candidate거절. 모두 실제 exit1·예상메시지,기존키byte불변을 확인했다.
- 상세 결과/해시는 [artifacts.json](artifacts.json), [package-verdict.json](package-verdict.json), [store-capture-result.json](store-capture-result.json), [signing-guards.json](signing-guards.json).
- 촬영 후 합성 기록을 삭제하고 release를 제거한 뒤 원래 debug0.4.4를 복구했다. ADB 명령 성공과 owned 에뮬레이터 종료, 이후 `adb devices` 빈 목록을 확인했다. 원래 기록이0개인 전용 QA 기기만 사용했다.

## 준비 중 발견한 도구 문제와 범위

1. 기존 검사기는 debug의 `res/xml/data_extraction_rules.xml` 고정경로를 사용했다. release 최적화가 실제 XML을 `res/4j.xml` 등에 배치해 검사 실패했다. 리소스 테이블의 이름으로 실제 경로를 찾도록 수정하고 release/debug 둘 다 재검증했다. 백업 규칙이나 앱 코드를 바꾼 것은 아니다.
2. PowerShell은 따옴표 없는 `-J-Duser.language=en`을 두 인자로 분리했다. JVM 인자를 인용한 뒤 실제 jarsigner 서명/검증 성공을 확인했다.
3. 초기 촬영은 구매복원 버튼이 첫 화면 아래에 있어 표시대기에서 실패했다. 미설정 상태의 실제 안내를 검증하도록 바꾸고 새 capture를 완료했다. 해상도2400 원본·실패로그는 local work에 별도 보존했다.
4. 기존 아이콘은24bitRGB여서32bit 제출 검사에 실패했다. 이미지내용을 바꾸지 않고 불투명alpha를 추가해32bit로 변환하고 픽셀불변을 확인했다.

이는 **등록 도구와 촬영 실행의 수정**이며 과거0.4.4초안timeout의 원인을 밝혔다는 뜻이 아니다. 과거 QA123/43/UI5종은 당시 근거를 그대로 따른다. 이 실행에서 전체 Android123을 다시 돌렸다고 하지 않는다.

## 운영 미완료

사용자가 새 전용 Cloud 프로젝트를 승인했으나 Google project quota에 거절됐다. 결제 URL/공개 키·서버·실제 Play상품·실제 거래는 아직 연결하지 않았다. 서명 내부AAB도 무료기능용이며 유료기능은 잠긴다. 정책HTTPS/시행일/제공자/접속로그정책과실제ARM/Play시험·비공개12명14일은 별도다. [현재 등록 순서](../../PLAY-REGISTRATION.md), [운영 계획](OPERATIONS.md).

Console 변경은 실제 저장/업로드 결과를 확인해 별도로 기록하며, 준비한 문서만으로 11설정을 완료 표시하지 않는다. 내부트랙업로드/배포와 공개출시 심사 제출을 구분한다.

10월1일 후속: 카테고리·이메일/광고/광고ID/정부/금융/건강 선언을 저장했고 승인된 IARC설문은완료상태를 확인했다. Google추가프로젝트1개한도요청은접수성공,승인대기다. 심사자 구매 없는 전체 접근 경로는미구현으로발견해등록 gate에추가했다. 실제 전체접근안내/AAB업로드/내부배포/정책HTTPS는미완료다. 외부 프로젝트 점검 목록과 결제계정ID는이앱저장소에커밋하지 않는다.
