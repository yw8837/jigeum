# 실행 명령 사본

이 폴더의 Python/Gradle 파일은 원래 `work/saved-media-20261012/`에서 실행한 작은 드라이버 사본이다.
현재 폴더에서 바로 실행하지 않는다. 재현 시 해당 work 위치에 두어 repository-root 계산을 유지한다.
Android SDK/JDK/OMX 경로는 이 Windows 작업환경 기준이다. 새 모델/AVD는 다운로드하지 않는다.
APK는 재사용한 `work/interval-evidence-20261009/build/outputs/`에 있으며, 설치 hash는 provenance를 따른다.

`baseline02`/`baseline03`는 이전 하네스의 실패 이력이다. 현재 드라이버/테스트로 이 하네스 오류를
다시 재현한다고 주장하지 않는다. 최종 Android suite는47통과/1실패이며 실패를 감춘 녹색 결과가 없다.

host는 Android 실행부를 제거한 파생 파일과 제품의 순수 판정 코드를 컴파일한다.
원래 result.json의 source_sha256.ClipEvidence.kt는 당시 작업트리 원본의 hash다.
baseline에서 실제 컴파일한 파생파일 hash는 별도 derived-inputs.json에 보존했다.
baseline driver가6499256의 원본을 읽은 뒤 Android 부분을 제거하는 경로는 host.py를 따른다.

커밋한 로그는 Windows/ADB의 CRCRLF 줄끝과 줄말단 공백만 정규화한 .txt 사본이다.
실패/stack/count 내용은 유지했다. 원시 로그는 work에 보존하며 두 hash는 log-normalization.json에 있다.
