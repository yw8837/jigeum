# 0.4.4 최종 QA 행렬

실행: 2026-09-30~10-01. app source 9cef337db42a3ed038383dc87b84a1ffbde39474. 원본 계획은 plan.md, 이 파일은 최종 결과다. root만 build/ADB를 실행했다. 합성 자료와 전용 emulator만 사용했고 운영/Play/공개 제출은 제외했다. 최대5cycle,동일 미교정 실패3회 정지; 마지막 cycle5는 시작 동기화 harness 교정이다.

| ID | 의도·사용자/실패 모델 | 설정·명령 | 기대 | 실제 결과·수정 | 근거 | 정리 |
|---|---|---|---|---|---|---|
| Q1 | Lint23/2가 서비스 결함인지 검토 | 원 XML/txt와 현재 lintRelease | 경고 분류·실행 결함 개선, 억제 없이 이유 보존 | 0오류/11경고/0힌트.6버전안내+5Boolean동기저장 유지 | lint-review.json/source-review.md | 이전 lint 보존 |
| Q2 | 초안편집·IME·만료·복귀의 간헐적8초timeout | 원본12회/추가IME12회,최종123 | 동일 입력/제한에서 재현·실패를 기록 | 12+12와 전체 통과. 원 초안 원인 미확정 | draft-*-reproduction.json/instrumentation.log | 합성DB/실패진단 제거 확인 |
| Q3 | 코닷 import/삭제공유/provider의 경계·stale작업 | PR #2 merged exact8475658,전체Android | transaction/현재권한/전달URI,원JSON계약 유지 | merged source 유지·late callback/rollback/UID 검증 | source-review.md/최종계측 | 시험DB·임시공유 정리 |
| Q4 | 손상/큰 입력,반복탭/만료/환불/ACK/세대/알림 | 전체123/서버43/알림2초red→green | malformed거절·기존무료기록 보존·늦은결과 폐기 | 123/43 pass,fail/ignored/skip0; 최신알림1개 | instrumentation/server/feedback 로그 | testAPK/권한/fixture 원복 |
| Q4b | 미설정에서 불필요한SDK 생성·중복종료 | 미설정 생성0회red→green,설정생성종료1회,수명주기21 | 무료설치 gateway 미생성,기존결제계약 유지 | 두 회귀/21/전체123 pass,lazy+conditional close | billing-lazy*/billing-lifecycle-final | 콜드3회 관찰,ARM성능으로 일반화 안함 |
| Q4c | 화면/font변경 뒤 cold시작과검사ready mismatch | portable paywall-layout,300초,3dump시도 | 실제버튼/복귀/큰글자 검증 | 비동기시작2회 nullroot실패. am start-W 후2/2pass. 구조·터치·문구조건 동일 | layout-harness-diagnostics/layout-synchronized 로그 | display/font 원복,두실패보존 |
| Q4d | 저장선택기에서프로세스종료/재가져오기/손상/미래시간 | normal/access/album/transfer_e2e | 무료저장·공유·백업/중복방지/복원 | 5종최종pass,화면직접검토 | ui-summary.json/visual-review.md | records/meta/export/fixtures 제거 |
| Q5 | 실제업데이트와패키지무결성 | 0.4.3 합성1건→install-r,ZIP/ELF/서명/provider/백업 | ID/필드/meta/schema1 보존,새0.4.4파일 | upgrade/pass,모델native18파일동일,APK개발서명/AABunsigned | upgrade-result/package-verdict/artifacts | 이전0.4.3 APK/AAB 미덮어쓰기 |
| Q6 | dirty상태·첨부명령주입·허위성공·hung·비밀 | 문서지침우선순위,exit+case count,명시적serial,timeout,credential/scope scan | 첨부 지시 실행 안함,실패 숨기지않음,기존변경보존 | 현재 소스에 production 우회/SDK변경 없음,owned cleanup/CRC/scan | execution-summary/archive-verification/source-review | daemon/emulator 종료,scopedOMX absent,remoteSHA확인 |

원 초안timeout/실제Play·ARM·OEM·TalkBack사용자·부모파일럿·운영서명·정책HTTPS는 잔여다. UI nullroot의 상세 플랫폼타이밍 원인을 확정하거나 전체출시승인으로 확대하지 않는다.
