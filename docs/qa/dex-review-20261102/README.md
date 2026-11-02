# DEX 코드 최적화 경고 진단 및 코드 전달 — 2026-11-02

## 실제 관찰

Play 제출24 상세에서 정확0.5.30(44)가 출시됨, 2026-10-28 17:44 KST로 표시됐다.
출처: https://play.google.com/console/u/0/developers/8736564730162379651/app/4975993958261665020/publishing/submission-activity/24/details

모니터링 및 개선의 경고는 정확44에 대해 난독화0%, 기준25%, 해결기한2027년2월이다.
기준미달시 Google Play 공개상태와 게시기능에영향가능하다는문구를확인했다.
`play-monitor.txt`와 `play-dex-warning.png`가현재경고의원천증거다.
해당화면의crash/ANR/coldstart는데이터없음이다.오류0또는성능문제없음으로해석하지않는다.
10월28일패키지상세기록의비압축DEX크기는65.2MB이며이번에새로측정한값은아니다.

## 원인과 해결 범위

현재 app/build.gradle.kts의 release는isMinifyEnabled=false/isShrinkResources=false다.
outputs/MoaAndroid/RELEASE.md는이전R8시험에서MediaPipe의SystemInfo.platform_ protobuf필드가제거되어
얼굴모델생성이실패했고,필드보존규칙추가뒤에도JNI연결오류가났다고기록한다.
이번에는당시오류를재실행하지않았다.이설정이현재난독화0%와일치하는직접원인이다.

DEX는Kotlin/Java실행코드이며아기의도번역정확도점수가아니다.필요한엔진코드를보존하면서R8을활성화하고
실제축소release의음성/얼굴모델·미디어·코드·거래/광고·업데이트·이전기록회귀를통과시킨뒤다음버전에반영해야한다.
단순옵션전환이나경고숨김은해결로보지않는다.이번턴은설명/증거보존으로앱소스·설정·상품·배포를변경하지않았다.

공식근거:
- https://developer.android.com/google/play/vitals/code-optimization : DEX정의, 일반앱10MB이상경고대상, R8/대체최적화검사.
- https://developer.android.com/google/play/vitals : 최적화/난독화/축소기준25%, 2027년2월부터스토어노출영향가능.

## 코드 전달

기존D:/Codex/Agattok/private-access-codes-20261027/issued/의friends-private.txt와launch-100-private.csv를읽었다.
CSV는100행이며운영자공통무료코드와첫할인코드,전체목록경로를요청한사용자에게전달한다.
원문/manifest/키는Git에추가하지않고코드발급·등록·소비·제3자발송을하지않았다.
초대는기간제한없는무료전체이용,할인은신규구독자첫2개월매월2,450원→월4,900원자동갱신이다.
코드입력위치는앱전체이용권>이용코드입력.실제Play할인결제는여전히미검증이다.
