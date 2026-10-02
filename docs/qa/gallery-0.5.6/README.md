# UltraQA Report — 결과 카드 앨범 저장0.5.6/code20

2026-10-02. **전체 계측246통과/0실패/1명시제외. 최종 서명 카드·기록 UI 및 패키징을 확인했다. release20은 Play 업로드·검토 제출·비공개 Alpha 게시까지 완료됐다. Alpha 활성과 실제 웹 참여 안내를 확인했으며 허용 목록은1명이다. 12명·14일은 충족되지 않았다.** [전달 파일](artifacts.json) / [Play 실제 상태](../../release/20261002/PLAY-STATUS.md).

## 목표와 경계

결과 화면과 같은 카드 렌더러로 사진 PNG, 선택 장면·실제 원음·최종 문장을 포함한 MP4를 휴대전화 ‘아가톡’ 앨범에 저장한다. 별도의 파일 위치 선택이 필요 없고, 기존 기록에서도 내보내며 이미 만든 결과의 저장은 무료다. 원본 자동 업로드·외부 기록 DB·표정 단독 욕구 번역은 추가하지 않는다. [계약과 행렬](../../UX-GALLERY-0.5.6.md).

정상 저장·재생·실패/취소·복구·큰 글자·서명·무료 접근을 확인한다. 원본/키/토큰 노출, 사용자 기기 삭제, 실제 거래·프로덕션 제출은 검증 범위 밖이다. 명시한 전용 에뮬레이터에서만 Android 작업을 한다. 준비 실패/예외/제외를 숨기지 않으며 같은 실패3회 또는5사이클을 상한으로 한다.

## 현재 실행 증거

- focused3: 갤러리13개·기존 미디어7개 **20통과/0실패**,22.94초.
- 이후 잘못된 저널의 다른 항목/사적 파일 삭제 차단과 완성한 pending 이미지 게시 복구2개를 추가했다. 전체247개 중246통과/0실패/1명시제외, 하네스704.96초/Android runner696.885초. [결과·설치 SHA](instrumentation-result.json), [test별 로그](instrumentation.log). 갤러리15개와 기존 미디어7개가 이 전체 실행에 포함됐다.
- debug/test/release lint 빌드 통과. lint0오류/20경고(기존 의존성 업데이트·용량검사·KTX 권고 등, 새 갤러리 KTX 권고2개 포함).
- 최종 preview/release APK와 서명 AAB 생성·패키지 검사 통과. APK 서명·backup 경계·zipalign·ELF16KiB 및 AAB 서명 확인. 구버전 갤러리 쓰기 권한은compiled manifest의maxSdk28 제한을 확인했다.
- 서명 UI의 결과 저장 앞부분: 실제 모델 입력→결과 카드PNG(720×1016)→편집 문장/원음 포함10.000초MP4(751,977bytes, 표시720×960)→앱 기록 저장을 확인했다. 이 실행은 이후 스크롤 대기에서 중단돼 전체status는fail이며 그대로 보존한다. [앞부분 증거](ui-result-prefix.json) / [단계](ui-result-steps.jsonl).
- 같은 최종 APK로 이어간 기록 중심 서명 UI는3흐름pass: 기록MP4 재생/앨범 저장과 창 안 완료 안내, 작은 화면/200% 글자 저장, signed preview→release 수동 업데이트 뒤 기록 유지·갤러리 저장 무료·새PDF 잠금. [후속 결과](ui-record-result.json) / [단계](ui-record-steps.jsonl). 실제 설치preview/release SHA가전달 파일과 일치한다. 전체 UI를 한 번에 모두 통과한 것으로 계산하지 않는다.
- 공개 정책4개 HTTP200과 갤러리 문구 확인. [배포](../../release/20261002/GALLERY-056-POLICY.md).

## 실패와 진단

- 최초 focused16개에서1실패: 기본 MediaStore 조회가 pending 항목을 포함하지 않아 취소 중 항목 수를 잘못 셌다. 실제 복구 스캔도 pending 포함 조회가 필요해 함께 수정했다.
- 두 번째 focused20개는7실패 상태였다. 이미 삭제한 행을 테스트 종료에서 다시 삭제하면 SecurityException이 났고, 취소 주입이 일반 예외를 전파했다. legacy 대역은 최신 Android의 숨김 `.pending-*` 경로를 그대로 넣어 실제 구버전과 달랐다. 대역을 pending 없는 행으로 만들고, 정리는 아직 존재하는 자체 항목만 대상으로 하며 취소 예외를 구별했다. 최초 실패 로그는 보존했다.
- 서명 UI는4회 하네스 문제로 중단됐다. AAC가 먼저 나오는MP4에서 첫stream을 영상으로 가정한 문제,960×720/rotation−90의 표시 크기720×960을 인코딩 크기와 혼동한 문제, 작은 화면의 아래 버튼을 스크롤 없이 기다린 문제,200% 글자 재생성 뒤 아래 스크롤 위치에서 위 제목을 기다린 문제다. 트랙 종류·회전 메타데이터·스크롤 확인을 고쳤다. 디코딩 이미지에서 편집 문장을 확인했고 앱 소스/최종 APK는 이 하네스 수정 중 바꾸지 않았다. 중단4회를 통과로 세지 않고 앞부분 증거와 기록 중심 후속pass를 연결한다.
- 제품의 취소 저널·legacy 누락 URI·기록 로드 차단, 카드 가독성·완료 안내·정책 문제는 수정했다. [독립 검토](REVIEW.md).

## 명령과 증거

- Gradle `assembleDebug assembleDebugAndroidTest lintRelease`; 기존 업로드키로 preview APK/release APK·AAB 생성.
- bounded focused `am instrument -w -r -e class GalleryMediaTests,TranslatedMediaTests`,180초 제한.
- 전체 `instrument.py --install-app --timeout 1500 --allow-skip com.moa.babylanguage.PublicAudioProbeTest#reportPublicSoundEventProbes`.
- `gallery_ui.py`는 실제 모델·미디어 렌더·MediaStore·서명 앱을 사용한다. 음원은 기존 권리 확인 로컬 fixture이며 Git/APK에 원본·파생 결과를 넣지 않는다.
- `check_package.py --distribution preview --skip-aab` 및 release의 APK/AAB 서명·manifest·zipalign·ELF16KiB 확인. preview를 Play에 업로드하지 않는다.

| 실행 | 종료/제한 | 주요 신호 |
|---|---|---|
| Gradle debug/test/lint 및 서명 preview/release 패키징 | exit0, 각 실행의 제한은로컬로그 보존 | 실제 전달APK/AAB 생성, lint0오류/20경고 |
| focused3 GalleryMediaTests,TranslatedMediaTests | exit0,180초 제한/22.94초 | 20/20통과, 이후 추가2회귀는전체 실행에 포함 |
| 전체instrument.py | exit0,1500초 제한/704.96초 | 247중246통과/0실패/허용한skip1개, 설치해시 확인 |
| gallery_ui.py 전체 경로/기록 중심 경로 | 앞부분 실행exit1 / 후속exit0, UI와인코더 대기별 제한 | 앞부분 결과PNG/MP4 통과 증거 및 후속3흐름pass; 전체 일괄pass 아님 |
| check_package.py preview/release | exit0 | 최종 서명/backup/zipalign/ELF16KiB |
| Node 서버 테스트·HTTPS 정책 | exit0 /4경로HTTP200 | 56/56통과, 갤러리 문구 반영; 실제Play거래 증거 아님 |

## 정리와 종료

두 focused 실패 뒤 제품/테스트 문제를 고치고 focused3·최종 전체·서명 UI로 검증한3개 로컬 QA 사이클이다. UI 준비 실패는 추가 제품 사이클로 부풀리지 않는다. 자체 격리상태만 완료/read/clear/read하고 저장소의 기존.omx는 보존한다. 테스트 파일·기기/프로세스 정리와 정확한 확인 한계는 [정리 기록](CLEANUP.md)을 따른다. 원본·파생미디어·키·토큰·APK/AAB·캐시는 Git에서 제외한다.

**로컬 갤러리 QA: ULTRAQA COMPLETE: Goal met after 3 cycles.** 이 종료는 아래 명시한 기기 한계와 외부 운영 대기까지 해결했다는 뜻이 아니다.

## 잔여 범위

Android26–28 실제 권한·MediaProvider, ARM/OEM 실제 코덱·카메라·사진 앱, 기기 전체 디스크 소진은 별도다. API35의 합성 저널 테스트는 구버전 실행 증거가 아니다. 서버 전체 장애 시에도 소리 분석·카드 생성·앨범 저장은 온디바이스이고 구매 검증은 기존 제한을 따른다. 이번 변경은 분석 엔진·의도 정확도 향상의 증거가 아니다.

**Play: 비공개 Alpha 게시·테스터 참여 링크 준비 완료.** 공개 전화 인증·추가 사업 정보 저장으로 차단을 해결하고 검토 제출을 마쳤다. 이후 Alpha 활성/최신release20과 참여 링크 복사 활성, 등록된 운영자 계정의 실제 웹 ‘Become a tester’ 페이지를 확인했다. 허용 목록은1명이며 외부 테스터 모집·목록 추가·실제 참여/설치는 다음 단계다. 게시 전 참여0명 실측을 현재 참여 수로 재사용하지 않는다. 12명·14일 충족이나 실기기·실제 거래 통과를 의미하지 않으며 상품/실거래/공개 프로덕션은 보류한다. 결제 계정 주의 알림의 원인은 이번 확인에서 조사하지 않았다. [현재 화면 증거](../../release/20261002/PLAY-STATUS.md)를 따른다.
