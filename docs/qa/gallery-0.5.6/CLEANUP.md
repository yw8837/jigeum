# 0.5.6 작업 정리

2026-10-02. 사용자 자료가 없는 `MoaQA35`, serial `emulator-5554`와 자체 `work/gallery-056`만 대상으로 했다.

- UI 하네스가 표시 크기·밀도·fontScale을 원래 값으로 복구했고 가져오기용 임시 source를 삭제했다. [후속 UI 정리](ui-record-result.json)의 `source_removed=true`, `display_restored=true`를 확인했다.
- 실제로 이 run에서 pull한 갤러리 경로만 허용목록으로 삼아10개 파일을 정리했다. 초기 삭제 명령의exit0/빈출력만으로 MediaStore 행 삭제를 확정하지 않았다.
- 이후10개 정확한 DATA 경로를 각각 읽기 전용으로 조회했다. 모두exit0 및 `No result found.`였으며 파일도 제거돼 있었다. [파일/행 확인](gallery-cleanup.json). 앱의 원래 항목·사용자 파일·다른 폴더는 정리 대상이 아니다.
- `adb -s emulator-5554 emu avd name`이 `MoaQA35`임을 확인한 뒤 그 에뮬레이터만 종료했다. `emu kill` exit0, 최종 `adb devices`에는 연결 기기가 없었다.
- 해당 Android 작업의 `gradlew.bat --stop` exit0, daemon1개 종료를 확인했다. 장기 빌드/계측/UI 실행은 완료됐다.
- 자체 격리 OMX state를cleanup→complete(iteration3)→read(activefalse)→clear(clearedtrue)→read(existsfalse)로 정리했다. 각CLI exit0·JSON 성공을 확인했다. 저장소 루트의 기존 `.omx`는 이 명령의cwd/대상이 아니다.
- 원본/파생PNG·MP4와 준비 실패 로그는ignored `work/gallery-056`의 로컬 증거로 보존했다. Git에는 소스·테스트·하네스·요약JSON·권한정보 없는 테스트 로그·공개정보 Play 증거만 포함한다. APK/AAB·키·토큰·캐시를 추가하지 않는다.
- Git용 계측/focused 로그는Android shell의CRCRLF/Windows CRLF 줄바꿈을LF로 정규화했다. 테스트 내용·결과는 바꾸지 않았고 원래 바이트의 로그도ignored `work/gallery-056/*-original.log`에 보존했다.
- 기존0.5.5 이하 기준 산출물은 원래 이름으로 보존했다. 다운로드 `아가톡 테스트` 폴더에는 새 `아가톡-전체기능테스트.apk` 하나만 남았다. preview SHA는 [산출물](artifacts.json)과 일치한다.

Play 전화 문자 인증/변경사항 저장은 운영자에게 인계했고 연락처/게시 개요 탭을 유지했다. 이 외부 차단을 로컬 QA 성공으로 해소했다고 표시하지 않는다. 인증 코드·개인 전화번호는 문서·이미지에 보존하지 않는다.
