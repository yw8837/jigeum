# DEX 경고 해결 검토 — 2026-11-02

## 확인한 사실

- Play 모니터링의 난독화 비율은 0%, 기준은 25%, 해결 기한은 2027년 2월이다. 인식 정확도 지표가 아니다.
- 현재 release는 `isMinifyEnabled=false`, `isShrinkResources=false`다.
- 실제 의존성은 MediaPipe tasks-vision/audio/core 1.0.0, protobuf-javalite 4.36.2, ONNX Runtime Android 1.24.3이다.
- 로컬 Gradle 캐시의 세 MediaPipe AAR와 내부 classes.jar를 검사했다. AAR consumer/proguard 규칙과 JAR의 META-INF proguard/R8 규칙이 모두 없었다. SDK에 필요한 규칙이 자동 포함된다고 가정할 수 없다.
- 보존된 `work/release-20260930/evidence/minified-face-init.log`에는 `Field platform_ ... not found`와 얼굴 모델 초기화 실패가 있다. RELEASE.md에는 protobuf 보존 후 JNI 연결 실패도 기록되어 있다. 이번에 이 오래된 실패를 새 기기에서 재현했다는 뜻은 아니다.

## 권장 구현 순서

1. 별도 진단 빌드에서 R8와 기본 `proguard-android-optimize.txt`를 활성화한다. 우선 MediaPipe/JNI 경계와 protobuf generated message를 보존해 동작 기준을 확보한다. 전체 앱에 `-keep class ** { *; }`를 적용해 수치만 피하지 않는다.
2. 생성된 protobuf 필드 이름과 JNI에서 참조하는 클래스·생성자·메서드를 SDK classes.jar/네이티브 심볼 및 실제 오류로 확인한다. 처음에는 해당 패키지의 넓은 보존으로 시작하되, 통과 후 필요한 경계만 남겨 축소 범위를 넓힌다. 임의 클래스명이나 검증하지 않은 최소 규칙을 정식 빌드에 적용하지 않는다.
3. 음성·얼굴 모델 초기화, 실제 ONNX 10/20초 실행과 취소·회복, 녹음·갤러리 가져오기·영상/이미지 저장, 코드·결제 서명 검증·보상 광고·업데이트 알림·기존 기록 보존을 minified release에서 확인한다. 비축소 debug 성공은 대체 근거가 아니다.
4. `mapping.txt`, R8 configuration/usage 보고서, APK/AAB SHA, 모델/네이티브 파일 동일성, 전후 DEX 크기를 보존한다. Play가 계산하는 난독화 비율 25% 이상은 새 아티팩트의 실제 콘솔 수치로 확인한다. 로컬 이름 변경 수나 크기 감소를 같은 지표로 보고하지 않는다.
5. JNI/반사 호출이 안정적으로 보존되지 않으면 MediaPipe 상위 버전의 consumer rules·protobuf 호환성을 조사한다. 모델 출력·SDK 진단 전송·네이티브 16KB 정렬까지 비교한 뒤 교체한다. 업그레이드 자체를 해결로 간주하지 않는다.

이번 45 코드 기능 변경에는 R8 스위치나 음성/영상 판정 모델·임계값을 바꾸지 않았다. DEX 경고 해결 완료는 아직 아니다.

공식 참고: [코드 최적화 지표](https://developer.android.com/google/play/vitals/code-optimization), [앱 최적화](https://developer.android.com/topic/performance/app-optimization/enable-app-optimization), [보존 규칙](https://developer.android.com/topic/performance/app-optimization/keep-rules-overview).
