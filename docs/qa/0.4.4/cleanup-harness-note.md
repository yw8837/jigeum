# 정리 도구 출력 형식 교정

첫 cleanup은 기록/메타/공유파일 정리를 수행한 뒤 시험DB 잔여 확인에서 중단했다. Android의 `ls databases`가 여러 파일을 한 줄의 열 형태로 반환해, 파일 하나/줄을 가정한 Python 검사가 실패했다. 원래 출력:

```text
com.google.android.datatransport.events          moa_observations.db
com.google.android.datatransport.events-journal  moa_observations.db-journal
```

이는 허용한 네 파일이 두 줄로 반환된 정리 harness 형식 문제다. `ls -1`로 파일 하나/줄을 명시해 같은 검사를 다시 실행한다. 첫 실행에서 출력한 적 없는 공유파일 개수/이름을 추정해 결과로 만들지 않는다. 최종 cleanup-result는 재실행 시점의 실제 상태를 기록한다. 앱 소스·경계 검증 조건은 바꾸지 않았다.

두 번째 실행에서는 존재하지 않는 실패진단 폴더의 `find` 오류가 ADB exec-out stdout에 실려, 비어 있지 않은 진단 파일 출력으로 오인했다. 직접 확인한 출력은 `find: 'files/access-navigation-failures': No such file or directory`, private files의 실제 목록은 `profileInstalled`뿐이다. 부모 폴더를 `ls -1`로 먼저 확인하고 진단 폴더가 있을 때만 find를 실행하도록 교정했다. 실제 진단 파일이 있을 때는 계속 실패하게 하며 파일을 임의 삭제하지 않는다.
